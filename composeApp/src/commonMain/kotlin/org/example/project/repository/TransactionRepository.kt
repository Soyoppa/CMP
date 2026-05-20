package org.example.project.repository

import org.example.project.data.AddTransactionResult
import org.example.project.data.SheetRepository
import org.example.project.data.SheetRepositoryFactory
import org.example.project.model.AiSummaryRecord
import org.example.project.model.BudgetExpenseRecord
import org.example.project.model.Transaction

/**
 * Thin facade the rest of the app talks to. Read+write both flow through
 * the [SheetRepository] picked by [SheetRepositoryFactory] for this fork's
 * `SHEET_SCHEMA` setting.
 */
class TransactionRepository(
    private val sheet: SheetRepository = SheetRepositoryFactory.create(),
) {
    suspend fun getFromDataDump(): List<Transaction> = sheet.getFromDataDump()

    /**
     * Fetches the monthly category summary records (tracker_1 only;
     * other schemas return an empty list).
     */
    suspend fun getAiSummaryRecords(): List<AiSummaryRecord> = sheet.getAiSummaryRecords()

    /**
     * Fetches budget vs actual expense records (tracker_1 only;
     * other schemas return an empty list).
     */
    suspend fun getFromBudgetExpense(): List<BudgetExpenseRecord> = sheet.getFromBudgetExpense()

    suspend fun addTransaction(transaction: Transaction): Boolean =
        sheet.addTransaction(transaction).success

    suspend fun addTransactionDetailed(transaction: Transaction): AddTransactionResult =
        sheet.addTransaction(transaction)

    suspend fun testScriptConnection(): String = sheet.testWriteConnection()
}
