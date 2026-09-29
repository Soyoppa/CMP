package org.example.project.repository

import org.example.project.AppContainer
import org.example.project.auth.Session
import org.example.project.data.ledger.AddTransactionResult
import org.example.project.data.ledger.DemoLedgerDataSource
import org.example.project.data.ledger.LedgerDataSource
import org.example.project.data.ledger.LedgerEntry
import org.example.project.data.ledger.LedgerSource
import org.example.project.model.CategorySummary
import org.example.project.model.Transaction

/**
 * The single entry point the app uses for ledger data. Routes every call to the backend that
 * belongs to the current session:
 *  - guests → [DemoLedgerDataSource] (anonymous sessions must never see real rows)
 *  - accounts granted [LedgerSource.SHEETS] → the household Google Sheet (via the gateway)
 *  - everyone else → their own Firestore ledger
 *
 * Deciding here (not in each ViewModel) means a new screen can't accidentally read the wrong ledger.
 */
class LedgerRepository(
    private val cloud: LedgerDataSource = AppContainer.cloudLedger,
    private val sheets: () -> LedgerDataSource = { AppContainer.sheetsLedger },
) {
    private val source: LedgerDataSource
        get() {
            val user = Session.currentUser
            return when {
                user == null || user.isGuest -> DemoLedgerDataSource
                user.ledgerSource == LedgerSource.SHEETS -> sheets()
                else -> cloud
            }
        }

    /** Ready-made summary for guests; signed-in users get theirs built from [getExpenses]. */
    fun getDemoSummary(): List<CategorySummary> = DemoLedgerDataSource.getSummary()

    /** Every income/expense row in ledger order (oldest first). */
    suspend fun getEntries(): List<LedgerEntry> = source.getEntries()

    /** Every expense row with category + month. */
    suspend fun getExpenses(): List<LedgerEntry> = source.getExpenses()

    suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        source.addTransaction(transaction)

    /** Permanently deletes [entry]; guests (demo data) get a user-facing refusal. */
    suspend fun deleteEntry(entry: LedgerEntry) = source.deleteEntry(entry)
}
