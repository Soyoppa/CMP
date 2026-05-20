package org.example.project.data

import org.example.project.model.AiSummaryRecord
import org.example.project.model.BudgetExpenseRecord
import org.example.project.model.Transaction

/**
 * Schema-agnostic read+write interface over the backing spreadsheet.
 *
 * Each fork picks one implementation via [SheetRepositoryFactory], driven by
 * the build-time `SHEET_SCHEMA` value in `local.properties`.
 *
 * Schema-specific tabs (AI summary, budget vs expense) may return empty lists
 * on schemas that do not expose them.
 */
interface SheetRepository {
    // Read
    suspend fun getFromDataDump(): List<Transaction>
    suspend fun getAiSummaryRecords(): List<AiSummaryRecord>
    suspend fun getFromBudgetExpense(): List<BudgetExpenseRecord>

    // Write
    suspend fun addTransaction(transaction: Transaction): AddTransactionResult
    suspend fun testWriteConnection(): String
}
