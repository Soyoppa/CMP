package org.example.project.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.request
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.model.CategorySummary
import org.example.project.model.CareofCatagory
import org.example.project.model.Transaction

/**
 * Sheet #2 schema implementation (`tracker_2`).
 *
 * Read tab layout (5 columns, header row + data rows):
 *   A: Date         (YYYY-MM-DD)
 *   B: Description  (free text)
 *   C: Amount       ("₱2,950"; "-₱1,250" for refunds/reversals)
 *   D: Credit Card  (free text)             -> Transaction.modeOfPayment
 *   E: c/o          (FixedCategory.displayName) -> Transaction.category
 *
 * Writes POST to `writeScriptUrl` (Apps Script web app) with these GET parameters:
 *   date, description, amount (signed), creditCard, careOf
 * See apps-script/tracker_2.gs for the matching script template.
 */
class Tracker2Repository : SheetRepository {

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
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
        followRedirects = true
    }

    override suspend fun getSummary(): List<CategorySummary> = emptyList()

    /**
     * Last [limit] rows of the data tab (A:E), newest first.
     * Columns: Date | Description | Amount (signed) | Credit Card | c/o.
     * A negative amount is a refund/reversal, surfaced as an inflow.
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
                val signed = parseAmount(row.getOrNull(2))
                RecentTransaction(
                    description = row.getOrNull(1)?.trim().orEmpty(),
                    amount = kotlin.math.abs(signed),
                    isInflow = signed < 0.0,
                )
            }
            .reversed()
    }

    private fun parseAmount(raw: String?): Double =
        raw?.replace("₱", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0

    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult {
        val scriptUrl = ConfigManager.getConfig().writeScriptUrl
        if (scriptUrl.isBlank()) {
            return AddTransactionResult(
                success = false,
                errorMessage = "Script URL not configured: '$scriptUrl'",
                urlUsed = scriptUrl,
            )
        }

        // Sheet #2 stores a single signed amount: positive = charge, negative = refund.
        val signedAmount = when {
            transaction.outflow > 0.0 -> transaction.outflow
            transaction.inflow > 0.0 -> -transaction.inflow
            else -> 0.0
        }
        // Normalize the c/o field against the enum so the sheet stays clean.
        val careOf = CareofCatagory.fromDisplayName(transaction.category)?.displayName
            ?: transaction.category

        // SECURITY: removed pre-request println that logged the full scriptUrl and transaction
        // payload (description, amount, payment method) to stdout.

        return try {
            val response = client.get(scriptUrl) {
                parameter("date", transaction.date)
                parameter("description", transaction.description)
                parameter("amount", signedAmount.toString())
                parameter("creditCard", transaction.modeOfPayment)
                parameter("careOf", careOf)
            }

            val responseText = response.body<String>()
            val status = response.status.value
            val finalUrl = response.request.url.toString()
            val contentType = response.headers["Content-Type"] ?: "<none>"

            // SECURITY: removed post-response println that logged the final redirect URL
            // (which may contain auth tokens) and full response body to stdout.

            if (status !in 200..399) {
                return AddTransactionResult(
                    success = false,
                    httpStatus = status,
                    responseBody = responseText.take(2000),
                    errorMessage = "HTTP error: ${response.status}",
                    urlUsed = finalUrl,
                )
            }

            try {
                val parsed = Json.decodeFromString<ScriptResponse>(responseText)
                AddTransactionResult(
                    success = parsed.success,
                    httpStatus = status,
                    responseBody = responseText.take(2000),
                    errorMessage = parsed.error,
                    urlUsed = finalUrl,
                )
            } catch (parseError: Exception) {
                AddTransactionResult(
                    success = false,
                    httpStatus = status,
                    responseBody = responseText.take(2000),
                    errorMessage = diagnoseHtmlResponse(responseText, contentType, parseError),
                    exceptionType = parseError::class.simpleName,
                    urlUsed = finalUrl,
                )
            }
        } catch (e: Exception) {
            AddTransactionResult(
                success = false,
                errorMessage = e.message ?: "Unknown exception",
                exceptionType = e::class.simpleName,
                urlUsed = scriptUrl,
            )
        }
    }

    private fun diagnoseHtmlResponse(body: String, contentType: String, parseError: Exception): String {
        val lower = body.lowercase()
        val hint = when {
            "accounts.google.com" in lower || "signin/identifier" in lower ->
                "Apps Script deployment is gated by Google sign-in. Fix: redeploy as Web app with 'Who has access: Anyone'."
            "<title>error</title>" in lower || ("script.google.com" in lower && "error" in lower) ->
                "Apps Script threw a server-side error. Open Apps Script editor → Executions tab for the stack trace."
            body.trimStart().startsWith("<!doctype html") || body.trimStart().startsWith("<html") ->
                "Server returned HTML, not JSON. Likely cause: wrong URL (use /exec, not /edit) or deployment access misconfigured."
            body.isBlank() -> "Empty response body. Apps Script may be undeployed or URL is wrong."
            else -> "Response was not JSON (content-type=$contentType). Parser: ${parseError.message}"
        }
        return "JSON parse failed — $hint"
    }

    override suspend fun testWriteConnection(): String {
        return try {
            val response = client.get(ConfigManager.getConfig().writeScriptUrl)
            val responseBody = response.body<String>()
            "Connection test - Status: ${response.status}, Body: $responseBody"
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }
    }
}
