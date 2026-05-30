package org.example.project.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.model.CategorySummary
import org.example.project.model.Transaction

@Serializable
data class SheetsResponse(
    val values: List<List<String>>? = null
)

@Serializable
data class SheetsRequest(
    val values: List<List<String>>
)

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
            level = LogLevel.INFO
        }
    }

    /**
     * Last [limit] rows of the 'Data Dump' tab (A:H), newest first.
     * Columns: Date | Description | Inflow | Outflow | Category | Mode | Paid | Remarks.
     * Exceptions propagate so the caller (e.g. the read diagnostic) can surface them.
     */
    override suspend fun getRecentTransactions(limit: Int): List<RecentTransaction> {
        val config = ConfigManager.getConfig()
        val response: SheetsResponse = client.get(
            "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/${config.sheetRange}"
        ) {
            parameter("key", config.apiKey)
        }.body()

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
                .also { println("📊 [Tracker1] Parsed ${it.size} summary categories") }
        } catch (e: Exception) {
            println("💥 [Tracker1] getSummary failed: ${e::class.simpleName} — ${e.message}")
            emptyList()
        }
    }

    private fun parseAmount(raw: String?): Double =
        raw?.replace("₱", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0

    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        scriptRepo.addTransactionDetailed(transaction)

    override suspend fun testWriteConnection(): String = scriptRepo.testConnection()
}
