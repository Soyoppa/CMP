package org.example.project.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.request
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.model.Transaction

@Serializable
data class ScriptResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

/**
 * Rich result type — carries diagnostic info up to the ViewModel
 * so logs print from main thread (visible in Logcat regardless of filter).
 */
data class AddTransactionResult(
    val success: Boolean,
    val httpStatus: Int? = null,
    val responseBody: String? = null,
    val errorMessage: String? = null,
    val exceptionType: String? = null,
    val urlUsed: String? = null
)

class GoogleAppsScriptRepository {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    println("[Ktor] $message")
                }
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000  // 30s timeout — prevents infinite loading
            connectTimeoutMillis = 15_000
        }
        followRedirects = true
    }

    suspend fun addTransaction(transaction: Transaction): Boolean {
        return addTransactionDetailed(transaction).success
    }

    /**
     * Detailed version that returns rich diagnostic info.
     * Use this when you need to inspect what actually happened.
     */
    suspend fun addTransactionDetailed(transaction: Transaction): AddTransactionResult {
        val scriptUrl = ConfigManager.getConfig().writeScriptUrl

        // Validate URL is configured
        if (scriptUrl.isBlank() || scriptUrl == "BUILD_TIME_SCRIPT_URL") {
            return AddTransactionResult(
                success = false,
                errorMessage = "Script URL not configured: '$scriptUrl'",
                urlUsed = scriptUrl
            )
        }

        return try {
            val response = client.get(scriptUrl) {
                parameter("date", transaction.date)
                parameter("description", transaction.description)
                parameter("inflow", if (transaction.inflow > 0) transaction.inflow.toString() else "")
                parameter("outflow", if (transaction.outflow > 0) transaction.outflow.toString() else "")
                parameter("category", transaction.category)
                parameter("modeOfPayment", transaction.modeOfPayment)
                parameter("isPaid", if (transaction.isPaid) "TRUE" else "FALSE")
            }

            val responseText = response.body<String>()
            val status = response.status.value
            val finalUrl = response.request.url.toString()

            if (status !in 200..399) {
                return AddTransactionResult(
                    success = false,
                    httpStatus = status,
                    responseBody = responseText.take(2000),
                    errorMessage = "HTTP error: ${response.status}",
                    urlUsed = finalUrl
                )
            }

            // Try to parse as JSON
            try {
                val jsonResponse = Json.decodeFromString<ScriptResponse>(responseText)
                AddTransactionResult(
                    success = jsonResponse.success,
                    httpStatus = status,
                    responseBody = responseText.take(2000),
                    errorMessage = jsonResponse.error,
                    urlUsed = finalUrl
                )
            } catch (parseError: Exception) {
                AddTransactionResult(
                    success = false,
                    httpStatus = status,
                    responseBody = responseText.take(500),
                    errorMessage = "JSON parse failed — response is not JSON (likely HTML auth page). Parser: ${parseError.message}",
                    exceptionType    = parseError::class.simpleName,
                    urlUsed = finalUrl
                )
            }
        } catch (e: Exception) {
            AddTransactionResult(
                success = false,
                errorMessage = e.message ?: "Unknown exception",
                exceptionType = e::class.simpleName,
                urlUsed = scriptUrl
            )
        }
    }

    suspend fun testConnection(): String {
        return try {
            val response = client.get(ConfigManager.getConfig().writeScriptUrl)
            val responseBody = response.body<String>()
            println("🔗 [testConnection] Status: ${response.status}, Body: $responseBody")
            "Connection test - Status: ${response.status}, Body: $responseBody"
        } catch (e: Exception) {
            println("💥 [testConnection] Failed: ${e.message}")
            "Connection failed: ${e.message}"
        }
    }
}