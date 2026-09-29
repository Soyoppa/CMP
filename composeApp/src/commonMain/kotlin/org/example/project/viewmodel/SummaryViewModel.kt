package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.auth.Session
import org.example.project.config.LedgerProfile
import org.example.project.data.ledger.LedgerEntry
import org.example.project.model.BudgetStatus
import org.example.project.model.BudgetSummaryMapper
import org.example.project.model.CategorySummary
import org.example.project.repository.BudgetRepository
import org.example.project.repository.LedgerRepository
import org.example.project.util.DateUtils
import org.example.project.util.toUserMessage

/** Whether the bar chart plots every category summed, or one category's trend across months. */
enum class SummaryViewMode { TOTAL, BY_CATEGORY }

data class SummaryUiState(
    val isLoading: Boolean = false,
    val categories: List<CategorySummary> = emptyList(),
    val months: List<String> = emptyList(),
    val selectedMonth: String? = null,
    /** Overall monthly budget: the user's total if set, else the sum of category budgets. */
    val totalMonthlyBudget: Double = 0.0,
    val budgetByCategory: Map<String, Double> = emptyMap(),
    /**
     * This calendar month's spending against [totalMonthlyBudget] — the "left to spend" figure the
     * Add screen shows. Kept across refreshes (not cleared while reloading) so it never flickers.
     */
    val thisMonth: BudgetStatus? = null,
    val viewMode: SummaryViewMode = SummaryViewMode.TOTAL,
    val selectedCategory: String? = null,
    val transactions: List<LedgerEntry> = emptyList(),
    val transactionsLoading: Boolean = false,
    val transactionsError: String? = null,
    val error: String? = null,
)

sealed interface SummaryEvent {
    /** Reload the ledger and budgets (pull-to-refresh, retry, after editing budgets). */
    data object Refresh : SummaryEvent
    data class MonthSelected(val month: String) : SummaryEvent
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
            is SummaryEvent.MonthSelected -> _uiState.update { it.copy(selectedMonth = event.month) }
            is SummaryEvent.ViewModeSelected -> selectViewMode(event.mode)
            is SummaryEvent.CategorySelected -> _uiState.update { it.copy(selectedCategory = event.category) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            transactionsLoaded = false
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    transactions = emptyList(),
                    transactionsError = null,
                )
            }
            try {
                // Real users: build the summary from the raw 'Data Dump' ledger (single source of
                // truth, always in sync with the drill-down) with budgets from the cloud store.
                // Guests: the self-contained demo dataset (no ledger, no cloud).
                val categories: List<CategorySummary>
                val totalMonthlyBudget: Double
                if (Session.isGuest) {
                    categories = repository.getDemoSummary()
                    totalMonthlyBudget = categories.sumOf { it.monthlyBudget }
                } else {
                    val txnsDeferred = async { repository.getExpenses() }
                    val planDeferred = async { budgetRepository.getPlan() }
                    val plan = planDeferred.await()
                    categories = BudgetSummaryMapper.build(
                        txnsDeferred.await(),
                        plan.byBucket,
                        LedgerProfile.current().spendingBuckets,
                    )
                    totalMonthlyBudget = plan.effectiveMonthlyTotal
                }
                val months = categories.firstOrNull()?.months ?: emptyList()
                val budgetByCategory = categories.associate { it.category.lowercase() to it.monthlyBudget }
                val currentMonth = DateUtils.monthName(DateUtils.currentMonthNumber())
                val thisMonth = BudgetStatus(
                    spent = categories.sumOf { it.spentIn(currentMonth) },
                    budget = totalMonthlyBudget,
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categories,
                        months = months,
                        totalMonthlyBudget = totalMonthlyBudget,
                        budgetByCategory = budgetByCategory,
                        thisMonth = thisMonth,
                        // Keep the user's month on a refresh; otherwise default to the current
                        // calendar month, falling back to the latest month that has data.
                        selectedMonth = it.selectedMonth?.takeIf { m -> m in months }
                            ?: months.firstOrNull { m -> DateUtils.monthNumberFromName(m) == DateUtils.currentMonthNumber() }
                            ?: months.lastOrNull { m -> categories.any { c -> c.spentIn(m) > 0.0 } },
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
                _uiState.update { it.copy(isLoading = false, error = e.toUserMessage("Couldn't load your summary.")) }
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
}
