package org.example.project.data.sheets

import kotlin.math.abs
import org.example.project.data.ledger.AddTransactionResult
import org.example.project.data.ledger.LedgerDataSource
import org.example.project.data.ledger.LedgerEntry
import org.example.project.data.ledger.RecentLedgerEntry
import org.example.project.model.CareOfCategory
import org.example.project.model.Transaction

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

    /** Tracker 2 has no summary/drill-down features, so it exposes no expense breakdown. */
    override suspend fun getExpenses(): List<LedgerEntry> = emptyList()

    /**
     * Last [limit] rows of the data tab, newest first.
     * A negative amount is a refund/reversal, surfaced as an inflow.
     */
    override suspend fun getRecent(limit: Int): List<RecentLedgerEntry> =
        gateway.readRows()
            .drop(1) // header
            .filter { it.getOrNull(1)?.isNotBlank() == true }
            .takeLast(limit)
            .map { row ->
                val signed = parseSheetAmount(row.getOrNull(2))
                RecentLedgerEntry(
                    description = row.getOrNull(1)?.trim().orEmpty(),
                    amount = abs(signed),
                    isInflow = signed < 0.0,
                )
            }
            .reversed()

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
