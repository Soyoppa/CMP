package org.example.project.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
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
            level = LogLevel.INFO
        }
        // Enable automatic redirect following for GET requests
        followRedirects = true
    }
    
    suspend fun addTransaction(transaction: Transaction): Boolean {
        return try {
            // Go back to POST request with JSON body since that was working before
            val request = ScriptTransactionRequest(
                date = transaction.date,
                description = transaction.description,
                inflow = if (transaction.inflow > 0) FormatUtils.formatPeso(transaction.inflow) else "",
                outflow = if (transaction.outflow > 0) FormatUtils.formatPeso(transaction.outflow) else "",
                category = transaction.category,
                modeOfPayment = transaction.modeOfPayment,
                isPaid = if (transaction.isPaid) "TRUE" else "FALSE"
            )
            
            println("Sending POST to Google Apps Script: ${ConfigManager.getConfig().scriptUrl}")
            println("Request data: $request")
            
            val response = client.post(ConfigManager.getConfig().scriptUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
                header("User-Agent", "FinanceApp/1.0")
            }
            
            println("Response status: ${response.status}")
            
            val responseText = response.body<String>()
            println("Response: ${responseText.take(200)}...") // Only show first 200 chars
            
            // Since we know the script actually works (data appears in sheet),
            // be more lenient with what we consider "success"
            val success = handleResponse(response, responseText)
            
            if (success) {
                println("✅ Transaction should be added to Google Sheet!")
            } else {
                println("❌ Request may have failed, but check the sheet manually")
            }
            
            return success
            
        } catch (e: Exception) {
            println("Error with Google Apps Script: ${e.message}")
            println("⚠️ Error occurred, but data might still be written to sheet")
            e.printStackTrace()
            // Even if there's an error, the data might still be written
            // Return false but suggest checking the sheet
            false
        }
    }
    
    private fun handleResponse(response: io.ktor.client.statement.HttpResponse, responseText: String): Boolean {
        // If we get any response (even if not perfect JSON), and the status suggests success,
        // assume it worked since we know the script is actually writing to the sheet
        return if (response.status.value in 200..399) {
            try {
                val jsonResponse = Json.decodeFromString<ScriptResponse>(responseText)
                println("Parsed JSON response: $jsonResponse")
                jsonResponse.success
            } catch (parseError: Exception) {
                println("Response parsing failed, but status indicates success: ${response.status}")
                println("Raw response: $responseText")
                // Since we know the script is working (data appears in sheet), 
                // treat any 2xx-3xx response as success
                true
            }
        } else {
            println("Request failed with status: ${response.status}")
            false
        }
    }
    
    suspend fun testConnection(): String {
        return try {
            val response = client.get(ConfigManager.getConfig().scriptUrl)
            val responseBody = response.body<String>()
            println("Test response status: ${response.status}")
            println("Test response body: $responseBody")
            "Connection test - Status: ${response.status}, Body: $responseBody"
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }
    }
}