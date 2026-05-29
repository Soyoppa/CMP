package org.example.project.data

import org.example.project.model.BudgetCategory
import org.example.project.model.CategorySummary
import org.example.project.model.Transaction

/**
 * A single recent entry, reduced to what the read diagnostic needs: a label and a
 * magnitude. [isInflow] distinguishes income/refunds (+) from expenses/charges (−).
 */
data class RecentTransaction(
    val description: String,
    val amount: Double,
    val isInflow: Boolean,
)

/**
 * Schema-agnostic read+write interface over the backing spreadsheet.
 *
 * Each fork picks one implementation via [SheetRepositoryFactory], driven by
 * the build-time `SHEET_SCHEMA` value in `local.properties`.
 *
 * Schema-specific tabs (e.g. budget vs expense) return an empty list on
 * schemas that do not expose them.
 */
interface SheetRepository {

    // Read
    /** Per-category budget + monthly actual spend. Empty on schemas without a budget tab. */
    suspend fun getBudget(): List<BudgetCategory>

    /** Per-category monthly spend from the Summary tab. Empty on schemas without this tab. */
    suspend fun getSummary(): List<CategorySummary>

    /** The most recent [limit] entries (newest first), read from the data tab. */
    suspend fun getRecentTransactions(limit: Int): List<RecentTransaction>

    // Write
    suspend fun addTransaction(transaction: Transaction): AddTransactionResult
    suspend fun testWriteConnection(): String
}
