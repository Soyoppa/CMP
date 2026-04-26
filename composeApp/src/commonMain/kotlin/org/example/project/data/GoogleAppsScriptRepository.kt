package org.example.project.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.model.Transaction
import org.example.project.util.FormatUtils
import org.example.project.util.DateUtils

@Serializable
data class ScriptTransactionRequest(
    val date: String,
    val description: String,
    val inflow: String,
    val outflow: String,
    val category: String,
    val modeOfPayment: String,
    val isPaid: String,
    val remarks: String = ""
)

@Serializable
data class ScriptResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
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
        val scriptUrl = ConfigManager.getConfig().writeScriptUrl
        println("🚀 [addTransaction] Starting request to: $scriptUrl")

        return try {
            // Use GET with query params — avoids the POST redirect issue on web (CORS + 302 drops body)
            val response = client.get(scriptUrl) {
                parameter("date", transaction.date)
                parameter("description", transaction.description)
                parameter("inflow", if (transaction.inflow > 0) FormatUtils.formatPeso(transaction.inflow) else "")
                parameter("outflow", if (transaction.outflow > 0) FormatUtils.formatPeso(transaction.outflow) else "")
                parameter("category", transaction.category)
                parameter("modeOfPayment", transaction.modeOfPayment)
                parameter("isPaid", if (transaction.isPaid) "TRUE" else "FALSE")
            }

            println("📡 [addTransaction] Response status: ${response.status.value}")
            val responseText = response.body<String>()
            println("📄 [addTransaction] Response body (first 300 chars): ${responseText.take(300)}")

            val success = handleResponse(response, responseText)
            if (success) {
                println("✅ [addTransaction] Transaction saved to Google Sheet!")
            } else {
                println("❌ [addTransaction] Script returned failure. Check sheet manually.")
            }
            success

        } catch (e: Exception) {
            println("💥 [addTransaction] Exception: ${e::class.simpleName} — ${e.message}")
            false
        }
    }

    private fun handleResponse(response: io.ktor.client.statement.HttpResponse, responseText: String): Boolean {
        return if (response.status.value in 200..399) {
            try {
                val jsonResponse = Json.decodeFromString<ScriptResponse>(responseText)
                println("🔍 [handleResponse] Parsed JSON: $jsonResponse")
                jsonResponse.success
            } catch (parseError: Exception) {
                println("⚠️ [handleResponse] JSON parse failed (${parseError.message}), treating ${response.status} as success")
                true
            }
        } else {
            println("❌ [handleResponse] HTTP error: ${response.status}")
            false
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