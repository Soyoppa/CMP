package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.AppContainer
import org.example.project.data.ledger.LedgerEntry
import org.example.project.repository.LedgerRepository
import org.example.project.util.DateUtils
import org.example.project.util.toUserMessage

enum class HistoryFilter { ALL, EXPENSES, INCOME }

data class TransactionHistoryUiState(
    val isLoading: Boolean = true,
    /** The calendar year on screen; the ledger is read one year at a time. */
    val year: Int = DateUtils.today().year,
    /** Earliest year with any row; bounds the year picker. */
    val earliestYear: Int? = null,
    /** Load failure; the screen shows it with a retry. */
    val error: String? = null,
    /** Every entry, newest first. */
    val entries: List<LedgerEntry> = emptyList(),
    val filter: HistoryFilter = HistoryFilter.ALL,
    /** Entry awaiting the user's delete confirmation. */
    val pendingDelete: LedgerEntry? = null,
    /** One delete at a time, so a quiet reload can't resurrect a row that's still being deleted. */
    val isDeleting: Boolean = false,
    /** A delete that failed (the row is restored); shown until dismissed. */
    val deleteError: String? = null,
) {
    val isCurrentYear: Boolean get() = year >= DateUtils.today().year

    val canGoBack: Boolean get() = earliestYear?.let { year > it } ?: false

    val visibleEntries: List<LedgerEntry>
        get() = when (filter) {
            HistoryFilter.ALL -> entries
            HistoryFilter.EXPENSES -> entries.filterNot { it.isIncome }
            HistoryFilter.INCOME -> entries.filter { it.isIncome }
        }
}

sealed interface TransactionHistoryEvent {
    data object Refresh : TransactionHistoryEvent
    data class YearSelected(val year: Int) : TransactionHistoryEvent
    data class FilterSelected(val filter: HistoryFilter) : TransactionHistoryEvent
    data class DeleteClicked(val entry: LedgerEntry) : TransactionHistoryEvent
    data object DeleteConfirmed : TransactionHistoryEvent
    data object DeleteDismissed : TransactionHistoryEvent
    data object DeleteErrorShown : TransactionHistoryEvent
}

/**
 * Backs the Transactions screen: the user's ledger newest-first, with delete.
 *
 * Deletes are optimistic — the row disappears immediately and is put back (with an error) if the
 * backend refuses, e.g. the shared sheet changed underneath us.
 */
class TransactionHistoryViewModel(
    private val ledgerRepository: LedgerRepository = AppContainer.session().ledger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionHistoryUiState())
    val uiState: StateFlow<TransactionHistoryUiState> = _uiState.asStateFlow()

    init {
        load(_uiState.value.year)
        // Any change to the ledger (a delete here, an add on the Add tab) re-reads quietly, which
        // also keeps ids current — sheet rows renumber when one above them is removed.
        viewModelScope.launch { ledgerRepository.revision.drop(1).collect { load(_uiState.value.year, quiet = true) } }
    }

    fun onEvent(event: TransactionHistoryEvent) {
        when (event) {
            TransactionHistoryEvent.Refresh -> load(_uiState.value.year)
            is TransactionHistoryEvent.YearSelected ->
                if (event.year != _uiState.value.year) load(event.year)
            is TransactionHistoryEvent.FilterSelected -> _uiState.update { it.copy(filter = event.filter) }
            is TransactionHistoryEvent.DeleteClicked ->
                if (!_uiState.value.isDeleting) {
                    _uiState.update { it.copy(pendingDelete = event.entry) }
                }
            TransactionHistoryEvent.DeleteConfirmed -> deletePending()
            TransactionHistoryEvent.DeleteDismissed -> _uiState.update { it.copy(pendingDelete = null) }
            TransactionHistoryEvent.DeleteErrorShown -> _uiState.update { it.copy(deleteError = null) }
        }
    }

    /** @param quiet keep the current list on screen (no spinner, errors ignored) — used after a delete. */
    private fun load(year: Int, quiet: Boolean = false) {
        if (!quiet) _uiState.update { it.copy(year = year, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val ledgerYear = ledgerRepository.readYear(year)
                _uiState.update {
                    // Ignore a late result for a year the user has already navigated away from.
                    if (it.year != year) it
                    else it.copy(
                        isLoading = false,
                        earliestYear = ledgerYear.earliestYear?.coerceAtMost(year),
                        entries = ledgerYear.entries.asReversed(),
                    )
                }
            } catch (e: Exception) {
                if (!quiet) {
                    _uiState.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load your transactions.")) }
                }
            }
        }
    }

    private fun deletePending() {
        val entry = _uiState.value.pendingDelete ?: return
        val index = _uiState.value.entries.indexOf(entry)
        _uiState.update {
            it.copy(pendingDelete = null, deleteError = null, isDeleting = true, entries = it.entries - entry)
        }
        viewModelScope.launch {
            try {
                ledgerRepository.deleteEntry(entry)
                _uiState.update { it.copy(isDeleting = false) }
            } catch (e: Exception) {
                _uiState.update { state ->
                    val restored = state.entries.toMutableList().apply { add(index.coerceIn(0, size), entry) }
                    state.copy(
                        isDeleting = false,
                        entries = restored,
                        deleteError = e.toUserMessage("Couldn't delete that transaction. Please try again."),
                    )
                }
            }
        }
    }
}
