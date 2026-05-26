package org.example.project.data

import org.example.project.model.BudgetCategory
import org.example.project.model.Transaction

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

    // Write
    suspend fun addTransaction(transaction: Transaction): AddTransactionResult
    suspend fun testWriteConnection(): String
}
