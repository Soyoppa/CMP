package org.example.project.data.sheets

import kotlin.math.abs
import org.example.project.data.ledger.AddTransactionResult
import org.example.project.data.ledger.LedgerDataSource
import org.example.project.data.ledger.LedgerEntry
import org.example.project.data.ledger.LedgerYear
import org.example.project.model.CareOfCategory
import org.example.project.model.Transaction
import kotlinx.datetime.number
import org.example.project.util.DateUtils

/**
 * Sheet #2 schema implementation (`tracker_2`).
 *
 * Read tab layout (5 columns, header row + data rows):
 *   A: Date         (YYYY-MM-DD)
 *   B: Description  (free text)
 *   C: Amount       ("₱2,950"; "-₱1,250" for refunds/reversals)
 *   D: Credit Card  (free text)             -> Transaction.modeOfPayment
 *   E: c/o          (CareOfCategory.displayName) -> Transaction.category
 *
 * Reads and writes go through the authenticated Sheets gateway (apps-script/sheets-gateway.gs);
 * appends send: date, description, amount (signed), creditCard, careOf.
 */
class Tracker2SheetDataSource(
    private val gateway: SheetsGatewayClient,
) : LedgerDataSource {

    /**
     * One year of rows; the id is the 1-based sheet row number. A negative amount is a
     * refund/reversal, surfaced as income. Rows with an unreadable date are skipped.
     */
    override suspend fun readYear(year: Int): LedgerYear {
        val dated = gateway.readRows().withSheetRowNumbers().mapNotNull { (rowNumber, row) ->
            val description = row.getOrNull(1)?.trim().orEmpty()
            val signed = parseSheetAmount(row.getOrNull(2))
            if (description.isBlank() || signed == 0.0) return@mapNotNull null
            val date = DateUtils.parseLedgerDate(row.getOrNull(0)) ?: return@mapNotNull null
            date to LedgerEntry(
                id = rowNumber.toString(),
                description = description,
                amount = abs(signed),
                category = row.getOrNull(4)?.trim().orEmpty(),
                monthNumber = date.month.number,
                date = row.getOrNull(0)?.trim().orEmpty(),
                modeOfPayment = row.getOrNull(3)?.trim().orEmpty(),
                isIncome = signed < 0.0,
            )
        }
        return LedgerYear(
            year = year,
            entries = dated.filter { (date, _) -> date.year == year }.map { (_, entry) -> entry },
            earliestYear = dated.minOfOrNull { (date, _) -> date.year },
        )
    }

    override suspend fun deleteEntry(entry: LedgerEntry) = gateway.deleteRow(entry)

    override suspend fun addTransaction(transaction: Transaction): AddTransactionResult {
        // Sheet #2 stores a single signed amount: positive = charge, negative = refund.
        val signedAmount = when {
            transaction.outflow > 0.0 -> transaction.outflow
            transaction.inflow > 0.0 -> -transaction.inflow
            else -> 0.0
        }
        // Normalize the c/o field against the enum so the sheet stays clean.
        val careOf = CareOfCategory.fromDisplayName(transaction.category)?.displayName
            ?: transaction.category

        return gateway.append(
            mapOf(
                "date" to transaction.date,
                "description" to transaction.description,
                "amount" to signedAmount,
                "creditCard" to transaction.modeOfPayment,
                "careOf" to careOf,
            )
        )
    }
}
