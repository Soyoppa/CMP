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
import org.example.project.config.ApiConfig.SCRIPT_URL
import org.example.project.model.Transaction

@Serializable
data class ScriptRequest(
    val date: String,
    val description: String,
    val inflow: String,
    val outflow: String,
    val category: String,
    val modeOfPayment: String,
    val isPaid: String
)

class GoogleAppsScriptApi {
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
    
    // You'll get this URL after deploying the Google Apps Script
    private val scriptUrl = SCRIPT_URL
    
    suspend fun addTransaction(transaction: Transaction): Boolean {
        return try {
            val request = ScriptRequest(
                date = transaction.date.toString(),
                description = transaction.description,
                inflow = if (transaction.inflow > 0) transaction.inflow.toString() else "",
                outflow = if (transaction.outflow > 0) transaction.outflow.toString() else "",
                category = transaction.category,
                modeOfPayment = transaction.modeOfPayment,
                isPaid = if (transaction.isPaid) "TRUE" else "FALSE"
            )
            
            val response = client.post(scriptUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            println("Script Response: ${response.status}")
            response.status.isSuccess()
        } catch (e: Exception) {
            println("Error with Apps Script: ${e.message}")
            false
        }
    }
}