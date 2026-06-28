package org.example.project.data

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
 * A single expense row reduced to what the per-category drill-down needs: a label, a
 * magnitude, its category, and the month it falls in ([monthNumber] 1..12, 0 if unknown).
 * Income rows are excluded by the producers.
 */
data class CategoryTransaction(
    val description: String,
    val amount: Double,
    val category: String,
    val monthNumber: Int,
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
    /** Per-category budget + monthly spend from the Summary tab. Empty on schemas without this tab. */
    suspend fun getSummary(): List<CategorySummary>

    /** The most recent [limit] entries (newest first), read from the data tab. */
    suspend fun getRecentTransactions(limit: Int): List<RecentTransaction>

    /**
     * Every expense row with its category + month, for the Summary sheet's per-category
     * drill-down. Defaults to empty for schemas without a granular data tab.
     */
    suspend fun getTransactions(): List<CategoryTransaction> = emptyList()

    // Write
    suspend fun addTransaction(transaction: Transaction): AddTransactionResult
    suspend fun testWriteConnection(): String
}
