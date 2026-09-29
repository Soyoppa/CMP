package org.example.project.data.sheets

import org.example.project.data.ledger.AddTransactionResult
import org.example.project.data.ledger.LedgerDataSource
import org.example.project.data.ledger.LedgerEntry
import org.example.project.model.Transaction
import org.example.project.util.DateUtils

/**
 * Sheet #1 schema implementation (`tracker_1`).
 *
 * Ledger tab layout ('Data Dump'):
 *   Date | Description | Inflow | Outflow | Category | Mode | Paid | Remarks
 *
 * Reads and writes go through the authenticated Sheets gateway (apps-script/sheets-gateway.gs).
 */
class Tracker1SheetDataSource(
    private val gateway: SheetsGatewayClient,
) : LedgerDataSource {

    /**
     * Every income/expense row. The id is the 1-based sheet row number (row 1 is the header), so
     * a row can be deleted later. Blank rows and rows with no amount are skipped.
     */
    override suspend fun getEntries(): List<LedgerEntry> =
        gateway.readRows().withSheetRowNumbers().mapNotNull { (rowNumber, row) ->
            val description = row.getOrNull(1)?.trim().orEmpty()
            val inflow = parseSheetAmount(row.getOrNull(2))
            val outflow = parseSheetAmount(row.getOrNull(3))
            if (description.isBlank() || (inflow <= 0.0 && outflow <= 0.0)) return@mapNotNull null
            val isIncome = inflow > 0.0
            LedgerEntry(
                id = rowNumber.toString(),
                description = description,
                amount = if (isIncome) inflow else outflow,
                category = row.getOrNull(4)?.trim().orEmpty(),
                monthNumber = DateUtils.monthNumberFromDate(row.getOrNull(0)),
                date = row.getOrNull(0)?.trim().orEmpty(),
                modeOfPayment = row.getOrNull(5)?.trim().orEmpty(),
                isPaid = parsePaid(row.getOrNull(6)),
                isIncome = isIncome,
            )
        }

    override suspend fun deleteEntry(entry: LedgerEntry) = gateway.deleteRow(entry)

    /**
     * The ledger's Paid column (G) is a checkbox, so the API returns "TRUE"/"FALSE" — but the
     * same column has historically been typed by hand, so accept the obvious human spellings too.
     * Anything else (blank included) reads as unpaid, which is the safe default for a bill.
     */
    private fun parsePaid(raw: String?): Boolean =
        raw?.trim()?.lowercase() in setOf("true", "yes", "y", "paid", "1")

    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        gateway.append(
            mapOf(
                "date" to transaction.date,
                "description" to transaction.description,
                // Leave the unused side blank (not 0), exactly like hand-entered rows.
                "inflow" to (transaction.inflow.takeIf { it > 0.0 } ?: ""),
                "outflow" to (transaction.outflow.takeIf { it > 0.0 } ?: ""),
                "category" to transaction.category,
                "modeOfPayment" to transaction.modeOfPayment,
                "isPaid" to transaction.isPaid,
            )
        )
}
