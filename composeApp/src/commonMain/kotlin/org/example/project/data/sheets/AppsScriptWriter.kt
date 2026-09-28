package org.example.project.data.sheets

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.data.ledger.AddTransactionResult
import org.example.project.model.Transaction

@Serializable
private data class ScriptResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

private val scriptJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Writes to the ledger through the schema's Google Apps Script web app (`WRITE_SCRIPT_URL`).
 * Each schema supplies its own query parameters (see apps-script/tracker_1.gs / tracker_2.gs).
 */
class AppsScriptWriter {
    private val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000  // 30s timeout — prevents infinite loading
            connectTimeoutMillis = 15_000
        }
        followRedirects = true
    }

    /** tracker_1 row: Date | Description | Inflow | Outflow | Category | Mode | Paid. */
    suspend fun addTransaction(transaction: Transaction): AddTransactionResult = append(
        "date" to transaction.date,
        "description" to transaction.description,
        "inflow" to if (transaction.inflow > 0) transaction.inflow.toString() else "",
        "outflow" to if (transaction.outflow > 0) transaction.outflow.toString() else "",
        "category" to transaction.category,
        "modeOfPayment" to transaction.modeOfPayment,
        "isPaid" to if (transaction.isPaid) "TRUE" else "FALSE",
    )

    /**
     * Sends one append request and interprets the script's JSON reply. Transport details (URL,
     * status, body) are deliberately kept out of the result — they can carry the deployment URL.
     */
    suspend fun append(vararg params: Pair<String, String>): AddTransactionResult {
        val scriptUrl = ConfigManager.getConfig().writeScriptUrl
        if (scriptUrl.isBlank()) {
            return AddTransactionResult(success = false, errorMessage = "Saving isn't configured on this build.")
        }

        return try {
            val response = client.get(scriptUrl) {
                params.forEach { (name, value) -> parameter(name, value) }
            }
            if (response.status.value !in 200..399) {
                return AddTransactionResult(
                    success = false,
                    errorMessage = "The server rejected the save (HTTP ${response.status.value}). Please try again.",
                )
            }
            val reply = runCatching {
                scriptJson.decodeFromString<ScriptResponse>(response.bodyAsText())
            }.getOrNull()
                ?: return AddTransactionResult(
                    success = false,
                    // Non-JSON almost always means an HTML sign-in/error page: the deployment is
                    // wrong (/edit instead of /exec, or access not set to "Anyone").
                    errorMessage = "The save service returned an unexpected response. Check the Apps Script deployment.",
                )
            AddTransactionResult(
                success = reply.success,
                errorMessage = if (reply.success) null else "The sheet rejected the save: ${reply.error ?: "unknown error"}",
            )
        } catch (e: Exception) {
            AddTransactionResult(success = false, errorMessage = "Couldn't reach the save service. Check your connection and try again.")
        }
    }
}
