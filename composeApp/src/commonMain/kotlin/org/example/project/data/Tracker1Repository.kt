package org.example.project.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.model.CategorySummary
import org.example.project.model.Transaction
import org.example.project.util.DateUtils

@Serializable
data class SheetsResponse(
    val values: List<List<String>>? = null
)

@Serializable
data class SheetsRequest(
    val values: List<List<String>>
)

@Serializable
data class SheetsErrorEnvelope(val error: SheetsApiError? = null)

@Serializable
data class SheetsApiError(val code: Int = 0, val message: String = "", val status: String = "")

private val sheetsErrorJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Turns a non-2xx Sheets API response into an actionable message.
 *
 * The API reports problems like a wrong tab name as
 * `{"error":{"message":"Unable to parse range: 'Tab'!A:E", ...}}` with HTTP 400. Because the client
 * doesn't fail on non-2xx and [SheetsResponse] ignores unknown keys, that error body used to
 * deserialize into `SheetsResponse(values = null)` and surface as an *empty success* ("read
 * succeeded, no transactions") instead of a real error. Callers now check the status and use this.
 */
internal fun sheetsErrorMessage(body: String, httpStatus: Int): String {
    val msg = runCatching {
        sheetsErrorJson.decodeFromString<SheetsErrorEnvelope>(body).error?.message
    }.getOrNull()
    return when {
        !msg.isNullOrBlank() -> "Sheets API error (HTTP $httpStatus): $msg"
        body.isNotBlank() -> "Sheets API returned HTTP $httpStatus with an unexpected response body."
        else -> "Sheets API returned HTTP $httpStatus with an empty body."
    }
}

/**
 * Sheet #1 schema implementation (`tracker_1`).
 *
 * Backing tab layout:
 *   'Data Dump'!A:H        -> Date | Description | Inflow | Outflow | Category | Mode | Paid | Remarks
 *   'Bugdet vs Expense'!A:N -> Category | Budget | January … December (monthly actuals)
 *     (tab name is misspelled in the live sheet — kept as-is intentionally)
 *
 * Writes go through a Google Apps Script web app (see GoogleAppsScriptRepository
 * and apps-script/tracker_1.gs).
 */
class Tracker1Repository(
    private val scriptRepo: GoogleAppsScriptRepository = GoogleAppsScriptRepository(),
) : SheetRepository {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            // SECURITY: INFO logs the full request URL which contains the ?key=<API_KEY>
            // query parameter. Use NONE to prevent the key from appearing in logcat.
            level = LogLevel.NONE
        }
    }

    /**
     * Last [limit] rows of the 'Data Dump' tab (A:H), newest first.
     * Columns: Date | Description | Inflow | Outflow | Category | Mode | Paid | Remarks.
     * Exceptions propagate so the caller (e.g. the read diagnostic) can surface them.
     */
    override suspend fun getRecentTransactions(limit: Int): List<RecentTransaction> {
        val config = ConfigManager.getConfig()
        val resp = client.get(
            "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/${config.sheetRange}"
        ) {
            parameter("key", config.apiKey)
        }
        // The client doesn't fail on non-2xx; surface Sheets API errors (wrong tab/range, bad key,
        // no access) instead of letting the error body parse into an empty, "successful" read.
        if (!resp.status.isSuccess()) {
            throw IllegalStateException(sheetsErrorMessage(resp.bodyAsText(), resp.status.value))
        }
        val response: SheetsResponse = resp.body()

        val rows = response.values ?: return emptyList()
        return rows.drop(1) // header
            .filter { it.getOrNull(1)?.isNotBlank() == true }
            .takeLast(limit)
            .map { row ->
                val inflow = parseAmount(row.getOrNull(2))
                val outflow = parseAmount(row.getOrNull(3))
                val isInflow = inflow > 0.0
                RecentTransaction(
                    description = row.getOrNull(1)?.trim().orEmpty(),
                    amount = if (isInflow) inflow else outflow,
                    isInflow = isInflow,
                )
            }
            .reversed()
    }

    /**
     * Every expense row from the 'Data Dump' tab (A:H) with its category and month.
     * Columns: Date | Description | Inflow | Outflow | Category | …
     * Income rows (outflow == 0) and blank rows are skipped. Exceptions propagate so the
     * caller can surface a real error rather than an empty "success".
     */
    override suspend fun getTransactions(): List<CategoryTransaction> {
        val config = ConfigManager.getConfig()
        val resp = client.get(
            "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/${config.sheetRange}"
        ) {
            parameter("key", config.apiKey)
        }
        if (!resp.status.isSuccess()) {
            throw IllegalStateException(sheetsErrorMessage(resp.bodyAsText(), resp.status.value))
        }
        val response: SheetsResponse = resp.body()

        val rows = response.values ?: return emptyList()
        return rows.drop(1) // header
            .mapNotNull { row ->
                val description = row.getOrNull(1)?.trim().orEmpty()
                val outflow = parseAmount(row.getOrNull(3))
                if (description.isBlank() || outflow <= 0.0) return@mapNotNull null
                CategoryTransaction(
                    description = description,
                    amount = outflow,
                    category = row.getOrNull(4)?.trim().orEmpty(),
                    monthNumber = DateUtils.monthNumberFromDate(row.getOrNull(0)),
                )
            }
    }

    /**
     * Fetches per-category budget + monthly spend from the 'Summary Trend' tab.
     *
     * Layout: Budget | Category | January | February | … | December
     * The Income row and any blank rows are skipped.
     */
    override suspend fun getSummary(): List<CategorySummary> {
        return try {
            val config = ConfigManager.getConfig()
            val response: SheetsResponse = client.get(
                "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/${config.summaryRange}"
            ) {
                parameter("key", config.apiKey)
            }.body()

            val rows = response.values ?: return emptyList()
            if (rows.isEmpty()) return emptyList()

            // Header: Budget | Category | January | February | … | December
            val monthHeaders = rows[0].drop(2).map { it.trim() }

            val skipNames = setOf("income", "expenses", "expense", "savings", "total", "net", "category", "budget")

            rows.drop(1)
                .filter { row ->
                    val name = row.getOrNull(1)?.trim().orEmpty()
                    name.isNotBlank() && !skipNames.contains(name.lowercase())
                }
                .map { row ->
                    val budget = parseAmount(row.getOrNull(0))
                    val category = row[1].trim()
                    val monthlySpend = monthHeaders.mapIndexed { index, month ->
                        month to parseAmount(row.getOrNull(index + 2))
                    }.toMap()
                    CategorySummary(category = category, monthlyBudget = budget, monthlySpend = monthlySpend)
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseAmount(raw: String?): Double =
        raw?.replace("₱", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0

    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        scriptRepo.addTransactionDetailed(transaction)

    override suspend fun testWriteConnection(): String = scriptRepo.testConnection()
}
