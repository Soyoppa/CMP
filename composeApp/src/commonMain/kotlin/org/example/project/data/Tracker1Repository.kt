package org.example.project.data

import org.example.project.config.ConfigManager
import org.example.project.model.CategorySummary
import org.example.project.model.Transaction
import org.example.project.util.DateUtils

/**
 * Sheet #1 schema implementation (`tracker_1`).
 *
 * Backing tab layout:
 *   'Data Dump'!A:H -> Date | Description | Inflow | Outflow | Category | Mode | Paid | Remarks
 *
 * Writes go through a Google Apps Script web app (see GoogleAppsScriptRepository
 * and apps-script/tracker_1.gs).
 */
class Tracker1Repository(
    private val reader: GoogleSheetsReader = GoogleSheetsReader(),
    private val writer: GoogleAppsScriptRepository = GoogleAppsScriptRepository(),
) : SheetRepository {

    /** Data rows of 'Data Dump' (header dropped). Read failures propagate to the caller. */
    private suspend fun ledgerRows(): List<List<String>> =
        reader.readRange(ConfigManager.getConfig().sheetRange).drop(1)

    /** Last [limit] rows of the ledger, newest first. */
    override suspend fun getRecentTransactions(limit: Int): List<RecentTransaction> =
        ledgerRows()
            .filter { it.getOrNull(1)?.isNotBlank() == true }
            .takeLast(limit)
            .map { row ->
                val inflow = parseSheetAmount(row.getOrNull(2))
                val outflow = parseSheetAmount(row.getOrNull(3))
                val isInflow = inflow > 0.0
                RecentTransaction(
                    description = row.getOrNull(1)?.trim().orEmpty(),
                    amount = if (isInflow) inflow else outflow,
                    isInflow = isInflow,
                )
            }
            .reversed()

    /** Every expense row with its category and month. Income (outflow == 0) and blank rows are skipped. */
    override suspend fun getTransactions(): List<CategoryTransaction> =
        ledgerRows().mapNotNull { row ->
            val description = row.getOrNull(1)?.trim().orEmpty()
            val outflow = parseSheetAmount(row.getOrNull(3))
            if (description.isBlank() || outflow <= 0.0) return@mapNotNull null
            CategoryTransaction(
                description = description,
                amount = outflow,
                category = row.getOrNull(4)?.trim().orEmpty(),
                monthNumber = DateUtils.monthNumberFromDate(row.getOrNull(0)),
                date = row.getOrNull(0)?.trim().orEmpty(),
                modeOfPayment = row.getOrNull(5)?.trim().orEmpty(),
                isPaid = parsePaid(row.getOrNull(6)),
            )
        }

    /** The summary is derived from the ledger (see BudgetSummaryMapper), not read from a tab. */
    override suspend fun getSummary(): List<CategorySummary> = emptyList()

    /**
     * The ledger's Paid column (G) is a checkbox, so the API returns "TRUE"/"FALSE" — but the
     * same column has historically been typed by hand, so accept the obvious human spellings too.
     * Anything else (blank included) reads as unpaid, which is the safe default for a bill.
     */
    private fun parsePaid(raw: String?): Boolean =
        raw?.trim()?.lowercase() in setOf("true", "yes", "y", "paid", "1")

    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        writer.addTransaction(transaction)
}
