package org.example.project.repository

import org.example.project.data.AddTransactionResult
import org.example.project.data.RecentTransaction
import org.example.project.data.SheetRepository
import org.example.project.data.SheetRepositoryFactory
import org.example.project.model.BudgetCategory
import org.example.project.model.Transaction

/**
 * Thin facade the rest of the app talks to. Read+write both flow through
 * the [SheetRepository] picked by [SheetRepositoryFactory] for this fork's
 * `SHEET_SCHEMA` setting.
 */
class TransactionRepository(
    private val sheet: SheetRepository = SheetRepositoryFactory.create(),
) {

    /** Per-category budget + monthly actual spend (for AI budget context). */
    suspend fun getBudget(): List<BudgetCategory> = sheet.getBudget()

    /** The most recent [limit] entries (newest first) for read diagnostics. */
    suspend fun getRecent(limit: Int): List<RecentTransaction> = sheet.getRecentTransactions(limit)

    suspend fun addTransaction(transaction: Transaction): Boolean =
        sheet.addTransaction(transaction).success

    suspend fun addTransactionDetailed(transaction: Transaction): AddTransactionResult =
        sheet.addTransaction(transaction)

    suspend fun testScriptConnection(): String = sheet.testWriteConnection()
}
