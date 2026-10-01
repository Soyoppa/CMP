package org.example.project.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.example.project.data.ledger.AddTransactionResult
import org.example.project.data.ledger.LabelField
import org.example.project.data.ledger.LedgerDataSource
import org.example.project.data.ledger.LedgerEntry
import org.example.project.data.ledger.LedgerYear
import org.example.project.model.Transaction

/**
 * The session's ledger — the only way screens read or write transactions. Bound to one
 * [LedgerDataSource] for the whole session (device, cloud, or the developer-only sheet; see
 * [org.example.project.SessionGraph]), so a screen can never read the wrong ledger.
 *
 * [revision] ticks after every change, so every open screen reloads on its own — adding an
 * expense updates the Summary without anyone wiring a refresh call.
 */
class LedgerRepository(private val source: LedgerDataSource) {

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** One calendar year of rows, plus how far back the ledger goes. */
    suspend fun readYear(year: Int): LedgerYear = source.readYear(year)

    /** [year]'s expense rows — the Summary, Paid & Unpaid and AI context all work per year. */
    suspend fun expensesIn(year: Int): List<LedgerEntry> = source.readYear(year).expenses

    suspend fun addTransaction(transaction: Transaction): AddTransactionResult =
        source.addTransaction(transaction).also { if (it.success) changed() }

    /** Permanently deletes [entry]; throws a user-facing error on failure. */
    suspend fun deleteEntry(entry: LedgerEntry) {
        source.deleteEntry(entry)
        changed()
    }

    /** Renames a label on every past row; see [LedgerDataSource.relabel]. */
    suspend fun relabel(field: LabelField, from: String, to: String): Int =
        source.relabel(field, from, to).also { if (it > 0) changed() }

    /** The data may have changed elsewhere (the web, another phone): make open screens reload. */
    fun invalidate() = changed()

    private fun changed() = _revision.update { it + 1 }
}
