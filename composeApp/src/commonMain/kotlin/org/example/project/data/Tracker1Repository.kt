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
import org.example.project.model.BudgetCategory
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
     * Fetches per-category budget + monthly actual spend from the
     * 'Budget vs Expense' tab.
     *
     * Layout: Category | Budget | January … December. Summary rows
     * (Total Budgeted, Monthly Income, Income - Budget) and blank rows
     * are skipped — only real categories are returned.
     */
    override suspend fun getBudget(): List<BudgetCategory> {
        return try {
            val config = ConfigManager.getConfig()
            val response: SheetsResponse = client.get(
                "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/${config.budgetRange}"
            ) {
                parameter("key", config.apiKey)
            }.body()

            val rows = response.values ?: return emptyList()
            if (rows.isEmpty()) return emptyList()

            // Header: Category, Budget, January, February, … December
            val monthHeaders = rows[0].drop(2).map { it.trim() }

            // The budget block is the contiguous category rows at the top of the
            // tab. It ends at the first blank row or summary row (Monthly Income,
            // Total Budgeted, Income - Budget) — below that the tab holds unrelated
            // tables (credit cards, salary vs savings, fixed/variable lists) that
            // must NOT be read as categories. takeWhile stops at that boundary.
            val stopPrefixes = listOf("total", "income", "monthly income")

            rows.drop(1)
                .takeWhile { row ->
                    val name = row.getOrNull(0)?.trim().orEmpty()
                    name.isNotBlank() &&
                        stopPrefixes.none { name.lowercase().startsWith(it) }
                }
                .map { row ->
                    val category = row[0].trim()
                    val budget = parseAmount(row.getOrNull(1))
                    val monthlyActual = monthHeaders.mapIndexed { index, month ->
                        month to parseAmount(row.getOrNull(index + 2))
                    }.toMap()
                    BudgetCategory(
                        category = category,
                        monthlyBudget = budget,
                        monthlyActual = monthlyActual,
                    )
                }
                .also { println("💰 [Tracker1] Parsed ${it.size} budget categories") }
        } catch (e: Exception) {
            println("💥 [Tracker1] getBudget failed: ${e::class.simpleName} — ${e.message}")
            emptyList()
        }
    }

    private fun parseAmount(raw: String?): Double =
        raw?.replace("₱", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0

    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        scriptRepo.addTransactionDetailed(transaction)

    override suspend fun testWriteConnection(): String = scriptRepo.testConnection()
}
