package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.data.ledger.LedgerEntry
import org.example.project.repository.LedgerRepository
import org.example.project.util.DateUtils
import org.example.project.util.toUserMessage

/** Which side of the ledger the list shows. Totals are always reported for both. */
enum class PaymentStatusFilter { ALL, UNPAID, PAID }

/**
 * One entry in the mode-of-payment filter row. [unpaidCount] is scoped to the selected month so
 * the chip doubles as a "what's still outstanding on this card" signal.
 */
data class PaymentModeOption(
    val name: String,
    val unpaidCount: Int,
)

data class PaymentStatusUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Every mode seen in the ledger, most-used first. Stable across month/status changes. */
    val modes: List<PaymentModeOption> = emptyList(),
    /** null = every mode ("All"). */
    val selectedMode: String? = null,
    val months: List<String> = emptyList(),
    /** null = every month ("All"). */
    val selectedMonth: String? = null,
    val statusFilter: PaymentStatusFilter = PaymentStatusFilter.ALL,
    /** Rows matching mode + month + status, unpaid first then high → low. */
    val entries: List<LedgerEntry> = emptyList(),
    val paidTotal: Double = 0.0,
    val paidCount: Int = 0,
    val unpaidTotal: Double = 0.0,
    val unpaidCount: Int = 0,
) {
    val hasEntries: Boolean get() = paidCount + unpaidCount > 0
}

/** Label used for ledger rows whose Mode of Payment cell is blank. */
const val UNASSIGNED_MODE = "Unassigned"

/**
 * Backs the Paid & Unpaid screen.
 *
 * Reads the same 'Data Dump' expense rows as the Summary drill-down ([LedgerRepository.getTransactions])
 * and slices them by the ledger's Paid checkbox, filtered by mode of payment and month. Read-only:
 * flipping a row's Paid state is a sheet write and isn't part of this screen.
 */
class PaymentStatusViewModel(
    private val repository: LedgerRepository = LedgerRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentStatusUiState())
    val uiState: StateFlow<PaymentStatusUiState> = _uiState.asStateFlow()

    /** The unfiltered ledger; every selection change re-derives the visible slice from this. */
    private var allEntries: List<LedgerEntry> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val entries = repository.getExpenses()
                val spelling = canonicalSpellings(entries)
                allEntries = entries.map { txn ->
                    val key = txn.modeOfPayment.trim().lowercase()
                    txn.copy(modeOfPayment = spelling[key] ?: UNASSIGNED_MODE)
                }

                val months = allEntries
                    .map { it.monthNumber }
                    .filter { it in 1..12 }
                    .distinct()
                    .sorted()
                    .map { DateUtils.monthName(it) }

                // Default to the current calendar month when the ledger covers it; otherwise show
                // every month rather than silently landing on an arbitrary one.
                val currentMonth = DateUtils.monthName(DateUtils.currentMonthNumber())
                val selectedMonth = currentMonth.takeIf { it in months }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        months = months,
                        selectedMonth = selectedMonth,
                        selectedMode = null,
                    )
                }
                recompute()
            } catch (e: Exception) {
                allEntries = emptyList()
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load the ledger.")) }
            }
        }
    }

    /** [mode] is null for "All". */
    fun selectMode(mode: String?) {
        _uiState.update { it.copy(selectedMode = mode) }
        recompute()
    }

    /** [month] is null for "All". */
    fun selectMonth(month: String?) {
        _uiState.update { it.copy(selectedMonth = month) }
        recompute()
    }

    fun selectStatus(filter: PaymentStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
        recompute()
    }

    /**
     * Re-derives the mode chips, the two totals and the visible rows from [allEntries].
     *
     * Totals deliberately ignore [PaymentStatusUiState.statusFilter] — both cards stay populated
     * while the list narrows, so switching to "Unpaid" never makes the paid figure disappear.
     */
    private fun recompute() {
        val state = _uiState.value
        val monthNumber = state.selectedMonth?.let { DateUtils.monthNumberFromName(it) } ?: 0

        fun inMonth(txn: LedgerEntry) = monthNumber == 0 || txn.monthNumber == monthNumber

        // Chips list every mode in the ledger (stable ordering by overall usage) but counts only
        // what's outstanding in the month on screen.
        val modes = allEntries
            .groupBy { it.modeOfPayment }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<LedgerEntry>>> { it.value.size }
                .thenBy { it.key })
            .map { (name, rows) ->
                PaymentModeOption(
                    name = name,
                    unpaidCount = rows.count { !it.isPaid && inMonth(it) },
                )
            }

        val scoped = allEntries.filter { txn ->
            inMonth(txn) && (state.selectedMode == null || txn.modeOfPayment == state.selectedMode)
        }
        val (paid, unpaid) = scoped.partition { it.isPaid }

        val visible = when (state.statusFilter) {
            PaymentStatusFilter.ALL -> scoped
            PaymentStatusFilter.UNPAID -> unpaid
            PaymentStatusFilter.PAID -> paid
        }.sortedWith(
            // Unpaid first — what needs action — soonest due at the top; paid history reads the
            // other way round, most recent first. Biggest amount breaks ties within a month.
            compareBy<LedgerEntry> { it.isPaid }
                .thenBy { if (it.isPaid) -it.monthNumber else it.monthNumber }
                .thenByDescending { it.amount }
        )

        _uiState.update {
            it.copy(
                modes = modes,
                entries = visible,
                paidTotal = paid.sumOf { txn -> txn.amount },
                paidCount = paid.size,
                unpaidTotal = unpaid.sumOf { txn -> txn.amount },
                unpaidCount = unpaid.size,
            )
        }
    }

    /**
     * Collapses the ledger's spelling drift ("Rcbc Flex" vs "RCBC Flex") onto one label so a card
     * doesn't show up as two chips, and gives blank cells a name of their own.
     *
     * Returns lower-cased mode -> the spelling to display, picking whichever variant the ledger
     * uses most so the winner is the one the user already recognises.
     */
    private fun canonicalSpellings(entries: List<LedgerEntry>): Map<String, String> =
        entries.map { it.modeOfPayment.trim() }
            .groupBy { it.lowercase() }
            .mapValues { (_, variants) ->
                variants.groupingBy { it }.eachCount()
                    .maxByOrNull { it.value }?.key
                    ?.takeIf { it.isNotEmpty() }
                    ?: UNASSIGNED_MODE
            }
}
