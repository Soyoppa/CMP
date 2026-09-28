package org.example.project.repository

import org.example.project.auth.Session
import org.example.project.data.AddTransactionResult
import org.example.project.data.CategoryTransaction
import org.example.project.data.DemoRepository
import org.example.project.data.RecentTransaction
import org.example.project.data.SheetRepository
import org.example.project.data.SheetRepositoryFactory
import org.example.project.model.CategorySummary
import org.example.project.model.Transaction

/**
 * Thin facade the rest of the app talks to. Read+write both flow through
 * the [SheetRepository] picked by [SheetRepositoryFactory] for this fork's
 * `SHEET_SCHEMA` setting.
 *
 * Guests are anonymous, so they must never see the real ledger: every call is routed to the
 * self-contained [DemoRepository] while [Session.isGuest] is true. Keeping the switch here (rather
 * than in each ViewModel) means a new screen can't accidentally leak real rows to a guest.
 */
class TransactionRepository(
    private val sheet: SheetRepository = SheetRepositoryFactory.create(),
) {
    private val source: SheetRepository get() = if (Session.isGuest) DemoRepository else sheet

    /** Per-category budget + monthly spend from the Summary tab. */
    suspend fun getSummary(): List<CategorySummary> = source.getSummary()

    /** The most recent [limit] entries (newest first) for read diagnostics. */
    suspend fun getRecent(limit: Int): List<RecentTransaction> = source.getRecentTransactions(limit)

    /** Every expense row with category + month, for the per-category drill-down. */
    suspend fun getTransactions(): List<CategoryTransaction> = source.getTransactions()

    suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        source.addTransaction(transaction)
}
