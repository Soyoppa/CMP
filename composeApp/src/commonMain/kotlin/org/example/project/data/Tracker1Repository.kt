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
import org.example.project.model.AiSummaryRecord
import org.example.project.model.BudgetExpenseRecord
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
 *   Summary Trend!A:M      -> per-category monthly totals
 *   'Budget vs Expense'!A:M -> per-category budget + monthly actuals
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

    override suspend fun getFromDataDump(): List<Transaction> {
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

    /**
     * Fetches monthly expense summary records from the AISummaryRecords sheet tab.
     *
     * Each row is a parent category (BILLS, FOOD, etc.) with amounts per month.
     * The TOTAL row is excluded — it can be derived from the records themselves.
     */
    override suspend fun getAiSummaryRecords(): List<AiSummaryRecord> {
        return try {
            val config = ConfigManager.getConfig()
            val range = "Summary Trend!A:M"
            val response: SheetsResponse = client.get(
                "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/$range"
            ) {
                parameter("key", config.apiKey)
            }.body()

            val rows = response.values ?: return emptyList()
            if (rows.isEmpty()) return emptyList()

            // First row is the header: Category, January, February, ... December
            val headers = rows[0]
            val monthHeaders = headers.drop(1) // skip "Category" column

            rows.drop(1)
                .filter { row ->
                    row.isNotEmpty() &&
                    row[0].isNotBlank() &&
                    row[0].trim().uppercase() != "TOTAL" // skip the total row
                }
                .map { row ->
                    val category = row[0].trim().uppercase()
                    val monthlyAmounts = monthHeaders.mapIndexed { index, month ->
                        val amount = row.getOrNull(index + 1)
                            ?.replace(",", "")
                            ?.trim()
                            ?.toDoubleOrNull() ?: 0.0
                        month to amount
                    }.toMap()
                    AiSummaryRecord(category = category, monthlyAmounts = monthlyAmounts)
                }
        } catch (e: Exception) {
            println("Error fetching AI summary records: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches budget vs actual expense records from the "Budget vs Expense" sheet tab.
     *
     * Each row is a sub-category with a fixed monthly budget and actual spend per month.
     * Summary rows (Total Budgeted, Income - Budget) and blank rows are skipped.
     */
    override suspend fun getFromBudgetExpense(): List<BudgetExpenseRecord> {
        return try {
            val config = ConfigManager.getConfig()
            val range = "'Budget vs Expense'!A:M"
            println("💰 [BudgetExpense] Fetching from spreadsheet: ${config.spreadsheetId}, range: $range")

            val response: SheetsResponse = client.get(
                "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/$range"
            ) {
                parameter("key", config.apiKey)
            }.body()

            val rows = response.values ?: run {
                println("💰 [BudgetExpense] No values returned from sheet")
                return emptyList()
            }
            if (rows.isEmpty()) {
                println("💰 [BudgetExpense] Sheet returned empty rows")
                return emptyList()
            }

            println("💰 [BudgetExpense] Total rows (including header): ${rows.size}")

            val headers = rows[0]
            println("💰 [BudgetExpense] Headers: ${headers.joinToString(", ")}")
            val monthHeaders = headers.drop(2)

            val skipPrefixes = listOf("total", "income")

            val records = rows.drop(1)
                .filter { row ->
                    row.isNotEmpty() &&
                    row[0].isNotBlank() &&
                    skipPrefixes.none { row[0].trim().lowercase().startsWith(it) }
                }
                .map { row ->
                    val category = row[0].trim()
                    val budget = row.getOrNull(1)
                        ?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
                    val monthlyActual = monthHeaders.mapIndexed { index, month ->
                        val amount = row.getOrNull(index + 2)
                            ?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
                        month to amount
                    }.toMap()
                    BudgetExpenseRecord(
                        category = category,
                        budget = budget,
                        monthlyActual = monthlyActual
                    )
                }

            println("💰 [BudgetExpense] Parsed ${records.size} category records")
            records.forEach { r ->
                val activeMonths = r.monthlyActual.entries.filter { it.value > 0 }.size
                println("  - ${r.category}: budget=₱${r.budget}, active months=$activeMonths, total=₱${r.totalActual}")
            }

            records
        } catch (e: Exception) {
            println("💥 [BudgetExpense] Error fetching records: ${e::class.simpleName} — ${e.message}")
            emptyList()
        }
    }

    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        scriptRepo.addTransactionDetailed(transaction)

    override suspend fun testWriteConnection(): String = scriptRepo.testConnection()
}
