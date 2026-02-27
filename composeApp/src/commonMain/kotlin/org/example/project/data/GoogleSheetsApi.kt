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

@Serializable
data class SheetsResponse(
    val values: List<List<String>>? = null
)

@Serializable
data class SheetsRequest(
    val values: List<List<String>>
)

class GoogleSheetsApi {
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
    
    suspend fun getTransactions(): List<Transaction> {
        return try {
            val config = ConfigManager.getConfig()
            val response: SheetsResponse = client.get(
                "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/${config.sheetRange}"
            ) {
                parameter("key", config.apiKey)
            }.body()
            
            response.values?.drop(1)?.mapNotNull { row ->
                if (row.size >= 5 && row[0].isNotEmpty() && row[1].isNotEmpty()) {
                    try {
                        // Parse date - handle formats like " January 15" or "January 15"
                        val dateStr = row[0].trim()
                        val parsedDate = if (dateStr.contains(" ")) {
                            // Convert "January 15" to "2026-01-15" format
                            kotlinx.datetime.LocalDate.parse("2026-01-15") // Default for now
                        } else {
                            kotlinx.datetime.LocalDate.parse(dateStr)
                        }
                        
                        // Parse amounts - remove ₱ symbol and commas
                        val inflowStr = if (row.size > 2) row[2].replace("₱", "").replace(",", "").trim() else ""
                        val outflowStr = if (row.size > 3) row[3].replace("₱", "").replace(",", "").trim() else ""
                        
                        Transaction(
                            date = parsedDate.toString(),
                            description = row[1].trim(),
                            inflow = if (inflowStr.isNotEmpty()) inflowStr.toDoubleOrNull() ?: 0.0 else 0.0,
                            outflow = if (outflowStr.isNotEmpty()) outflowStr.toDoubleOrNull() ?: 0.0 else 0.0,
                            category = if (row.size > 4) row[4].trim() else "",
                            modeOfPayment = if (row.size > 5) row[5].trim() else "",
                            isPaid = if (row.size > 6) row[6].equals("TRUE", ignoreCase = true) else false
                        )
                    } catch (e: Exception) {
                        println("Error parsing row: ${row.joinToString(", ")} - ${e.message}")
                        null // Skip invalid rows
                    }
                } else null
            }?.filter { it.description.isNotEmpty() } ?: emptyList()
        } catch (e: Exception) {
            println("Error fetching transactions: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun addTransaction(transaction: Transaction): Boolean {
        return try {
            val config = ConfigManager.getConfig()
            val values = listOf(
                listOf(
                    transaction.date.toString(), // Will be in YYYY-MM-DD format
                    transaction.description,
                    if (transaction.inflow > 0) FormatUtils.formatPeso(transaction.inflow) else "",
                    if (transaction.outflow > 0) FormatUtils.formatPeso(transaction.outflow) else "",
                    transaction.category,
                    transaction.modeOfPayment,
                    if (transaction.isPaid) "TRUE" else "FALSE",
                    "" // Remarks column (empty for now)
                )
            )
            
            val url = "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/${config.sheetRange}:append"
            println("Attempting to write to: $url")
            println("Data to write: $values")
            println("Request body: ${SheetsRequest(values)}")
            
            val response = client.post(url) {
                parameter("key", config.apiKey)
                parameter("valueInputOption", "RAW")
                parameter("insertDataOption", "INSERT_ROWS")
                contentType(ContentType.Application.Json)
                setBody(SheetsRequest(values))
            }
            
            val responseBody = response.body<String>()
            println("API Response Status: ${response.status}")
            println("API Response Headers: ${response.headers}")
            println("API Response Body: $responseBody")
            
            if (!response.status.isSuccess()) {
                println("Write failed with status: ${response.status.value} - ${response.status.description}")
                return false
            }
            
            true
        } catch (e: Exception) {
            println("Detailed error adding transaction: ${e.message}")
            println("Error type: ${e::class.simpleName}")
            println("Stack trace:")
            e.printStackTrace()
            false
        }
    }
}