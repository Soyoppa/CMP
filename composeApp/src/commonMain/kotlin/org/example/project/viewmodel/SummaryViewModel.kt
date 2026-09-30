package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.auth.Session
import org.example.project.config.LedgerProfile
import org.example.project.data.ledger.LedgerEntry
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetStatus
import org.example.project.model.BudgetSummaryMapper
import org.example.project.model.CategorySummary
import org.example.project.repository.BudgetRepository
import org.example.project.repository.LedgerRepository
import org.example.project.util.toUserMessage

/** Whether the bar chart plots every category summed, or one category's trend across cut-offs. */
enum class SummaryViewMode { TOTAL, BY_CATEGORY }

data class SummaryUiState(
    /** First load: nothing to show yet. */
    val isLoading: Boolean = false,
    /** Reloading with data already on screen (pull-to-refresh, after a save). */
    val isRefreshing: Boolean = false,
    val categories: List<CategorySummary> = emptyList(),
    /** The charted cut-offs, oldest first, ending with the current one. */
    val periods: List<BudgetPeriod> = emptyList(),
    /** [BudgetPeriod.id] of the bar the user picked; null = every charted cut-off. */
    val selectedPeriodId: String? = null,
    /** Overall budget per cut-off id: the user's total if set, else the sum of category budgets. */
    val totalBudgetByPeriod: Map<String, Double> = emptyMap(),
    /** The cut-off today falls in. */
    val currentPeriod: BudgetPeriod = BudgetPeriod.current(),
    /**
     * The current cut-off's spending against its budget — the "left to spend" figure the Add
     * screen shows. Kept across refreshes (not cleared while reloading) so it never flickers.
     */
    val thisPeriod: BudgetStatus? = null,
    /** True when the signed-in user hasn't set a budget for the current cut-off yet. */
    val needsBudget: Boolean = false,
    val viewMode: SummaryViewMode = SummaryViewMode.TOTAL,
    val selectedCategory: String? = null,
    val transactions: List<LedgerEntry> = emptyList(),
    val transactionsLoading: Boolean = false,
    val transactionsError: String? = null,
    val error: String? = null,
) {
    val selectedPeriod: BudgetPeriod? get() = periods.firstOrNull { it.id == selectedPeriodId }
}

sealed interface SummaryEvent {
    /** Reload the ledger and budgets (pull-to-refresh, retry, after a save or budget edit). */
    data object Refresh : SummaryEvent
    data class PeriodSelected(val periodId: String) : SummaryEvent
    data class ViewModeSelected(val mode: SummaryViewMode) : SummaryEvent
    data class CategorySelected(val category: String) : SummaryEvent
}

class SummaryViewModel(
    private val repository: LedgerRepository = LedgerRepository(),
    private val budgetRepository: BudgetRepository = BudgetRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    // Drill-down transactions load lazily the first time the user opens "By Category",
    // then refresh whenever the summary itself is reloaded.
    private var transactionsLoaded = false

    init {
        load()
    }

    fun onEvent(event: SummaryEvent) {
        when (event) {
            SummaryEvent.Refresh -> load()
            is SummaryEvent.PeriodSelected -> _uiState.update { it.copy(selectedPeriodId = event.periodId) }
            is SummaryEvent.ViewModeSelected -> selectViewMode(event.mode)
            is SummaryEvent.CategorySelected -> _uiState.update { it.copy(selectedCategory = event.category) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            transactionsLoaded = false
            _uiState.update {
                // Keep what's on screen while reloading; only the very first load shows the spinner.
                val hasData = it.categories.isNotEmpty()
                it.copy(
                    isLoading = !hasData,
                    isRefreshing = hasData,
                    error = null,
                    transactions = emptyList(),
                    transactionsError = null,
                )
            }
            try {
                // coroutineScope (not bare async inside launch): if a read fails, the error is
                // rethrown here for the catch below. A bare async child would instead cancel
                // the whole launch and crash the app with an uncaught exception.
                val (expenses, plans) = coroutineScope {
                    val expensesDeferred = async { repository.getExpenses() }
                    val plansDeferred = async { budgetRepository.getPlans() }
                    expensesDeferred.await() to plansDeferred.await()
                }

                val currentPeriod = BudgetPeriod.current()
                val periods = BudgetPeriod.recent(CHARTED_PERIODS, last = currentPeriod)
                val categories = BudgetSummaryMapper.build(
                    entries = expenses,
                    periods = periods,
                    plans = plans,
                    buckets = LedgerProfile.current().spendingBuckets,
                )
                val totalBudgetByPeriod = periods
                    .mapNotNull { p -> plans[p.id]?.effectiveTotal?.takeIf { it > 0.0 }?.let { p.id to it } }
                    .toMap()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        categories = categories,
                        periods = periods,
                        totalBudgetByPeriod = totalBudgetByPeriod,
                        currentPeriod = currentPeriod,
                        thisPeriod = BudgetStatus(
                            spent = categories.sumOf { c -> c.spentIn(currentPeriod.id) },
                            budget = totalBudgetByPeriod[currentPeriod.id] ?: 0.0,
                        ),
                        // Guests run on demo budgets; everyone else owes each cut-off a budget.
                        needsBudget = !Session.isGuest && plans[currentPeriod.id]?.isEmpty != false,
                        // Keep the user's cut-off on a refresh; otherwise open on the current one.
                        selectedPeriodId = it.selectedPeriodId?.takeIf { id -> periods.any { p -> p.id == id } }
                            ?: currentPeriod.id,
                        // Keep the chosen category on a refresh; otherwise pre-pick the biggest
                        // spender so "By Category" has data the instant the user switches to it.
                        selectedCategory = it.selectedCategory?.takeIf { c -> categories.any { s -> s.category == c } }
                            ?: categories.maxByOrNull { s -> s.totalSpent }?.category,
                    )
                }
                // Refreshing while the drill-down is open should reload its rows too.
                if (_uiState.value.viewMode == SummaryViewMode.BY_CATEGORY) {
                    ensureTransactionsLoaded()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.toUserMessage("Couldn't load your summary."))
                }
            }
        }
    }

    private fun selectViewMode(mode: SummaryViewMode) {
        _uiState.update { state ->
            val category = state.selectedCategory
                ?: state.categories.maxByOrNull { it.totalSpent }?.category
            state.copy(viewMode = mode, selectedCategory = category)
        }
        if (mode == SummaryViewMode.BY_CATEGORY) ensureTransactionsLoaded()
    }

    /** Loads the drill-down transactions once; no-op if already loaded or in flight. */
    private fun ensureTransactionsLoaded() {
        if (transactionsLoaded || _uiState.value.transactionsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(transactionsLoading = true, transactionsError = null) }
            try {
                val txns = repository.getExpenses()
                transactionsLoaded = true
                _uiState.update { it.copy(transactions = txns, transactionsLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(transactionsLoading = false, transactionsError = e.toUserMessage("Couldn't load transactions."))
                }
            }
        }
    }

    private companion object {
        /** Six months of cut-offs — enough trend to read, few enough bars to tap on a phone. */
        const val CHARTED_PERIODS = 12
    }
}
