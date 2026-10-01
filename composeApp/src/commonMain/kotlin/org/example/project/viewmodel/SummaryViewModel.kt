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
import org.example.project.util.DateUtils
import org.example.project.util.toUserMessage

/** Whether the bar chart plots every category summed, or one category's trend across the year. */
enum class SummaryViewMode { TOTAL, BY_CATEGORY }

data class SummaryUiState(
    /** First load: nothing to show yet. */
    val isLoading: Boolean = false,
    /** Reloading with data already on screen (pull-to-refresh, after a save). */
    val isRefreshing: Boolean = false,
    /** The calendar year on screen — the app reads and charts one year at a time. */
    val year: Int = DateUtils.today().year,
    /** Earliest year with any row; bounds how far back the year picker goes. */
    val earliestYear: Int? = null,
    val categories: List<CategorySummary> = emptyList(),
    /** [year]'s cut-offs, oldest first: Jan 1–15 through Dec 16–31. */
    val periods: List<BudgetPeriod> = emptyList(),
    /** [BudgetPeriod.id] of the cut-off in focus; null = the whole year. */
    val selectedPeriodId: String? = null,
    /** Overall budget per cut-off id: the user's total if set, else the sum of category budgets. */
    val totalBudgetByPeriod: Map<String, Double> = emptyMap(),
    /** The cut-off today falls in. */
    val currentPeriod: BudgetPeriod = BudgetPeriod.current(),
    /**
     * The current cut-off's spending against its budget — the "left to spend" figure the Add
     * screen shows. Always about today, whichever year is being browsed, and kept across
     * refreshes so it never flickers.
     */
    val thisPeriod: BudgetStatus? = null,
    /**
     * The cut-off the user should budget now: its budgeting window is open (salary has arrived)
     * and it has no budget yet. Can be the *upcoming* cut-off in the last days of the current one.
     * Null outside the windows, once a budget is saved, and for guests.
     */
    val budgetPrompt: BudgetPeriod? = null,
    val viewMode: SummaryViewMode = SummaryViewMode.TOTAL,
    val selectedCategory: String? = null,
    val transactions: List<LedgerEntry> = emptyList(),
    val transactionsLoading: Boolean = false,
    val transactionsError: String? = null,
    val error: String? = null,
) {
    val selectedPeriod: BudgetPeriod? get() = periods.firstOrNull { it.id == selectedPeriodId }

    /** True while [year] is the one today falls in — no later year has data to show. */
    val isCurrentYear: Boolean get() = year >= currentPeriod.year

    /** Whether an earlier year can be opened (the ledger reaches back that far). */
    val canGoBack: Boolean get() = earliestYear?.let { year > it } ?: false

    /** [year]'s cut-offs within one calendar month, oldest first. */
    fun periodsIn(monthKey: String): List<BudgetPeriod> = periods.filter { it.monthKey == monthKey }

    /**
     * Which cut-off to focus when the user taps [monthKey]'s bar: the same half they were already
     * looking at, so stepping across months keeps comparing like with like; otherwise the latest.
     */
    fun periodIn(monthKey: String): String? {
        val inMonth = periodsIn(monthKey)
        val half = selectedPeriod?.half
        return (inMonth.firstOrNull { it.half == half } ?: inMonth.lastOrNull())?.id
    }
}

sealed interface SummaryEvent {
    /** Reload the year and its budgets (pull-to-refresh, retry, after a save or budget edit). */
    data object Refresh : SummaryEvent
    /** Open another calendar year; reads are scoped to it. */
    data class YearSelected(val year: Int) : SummaryEvent
    /** A chart bar: selects a cut-off inside that calendar month (see [BudgetPeriod.monthKey]). */
    data class MonthSelected(val monthKey: String) : SummaryEvent
    data class PeriodSelected(val periodId: String) : SummaryEvent
    data class ViewModeSelected(val mode: SummaryViewMode) : SummaryEvent
    data class CategorySelected(val category: String) : SummaryEvent
}

/**
 * Backs the Summary screen. Reads exactly one calendar year at a time (Jan–Dec of [SummaryUiState.year],
 * defaulting to the year today falls in) rather than a rolling window, so the chart's twelve bars
 * are always the real months of a real year and the ledger read stays bounded.
 */
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
        load(_uiState.value.year)
    }

    fun onEvent(event: SummaryEvent) {
        when (event) {
            SummaryEvent.Refresh -> load(_uiState.value.year)
            is SummaryEvent.YearSelected ->
                if (event.year != _uiState.value.year) load(event.year)
            is SummaryEvent.MonthSelected -> _uiState.update { it.copy(selectedPeriodId = it.periodIn(event.monthKey)) }
            is SummaryEvent.PeriodSelected -> _uiState.update { it.copy(selectedPeriodId = event.periodId) }
            is SummaryEvent.ViewModeSelected -> selectViewMode(event.mode)
            is SummaryEvent.CategorySelected -> _uiState.update { it.copy(selectedCategory = event.category) }
        }
    }

    private fun load(year: Int) {
        viewModelScope.launch {
            transactionsLoaded = false
            val switchingYear = year != _uiState.value.year
            _uiState.update {
                // Keep what's on screen while reloading the same year; switching year starts clean.
                val hasData = it.categories.isNotEmpty() && !switchingYear
                it.copy(
                    year = year,
                    isLoading = !hasData,
                    isRefreshing = hasData,
                    error = null,
                    categories = if (switchingYear) emptyList() else it.categories,
                    transactions = emptyList(),
                    transactionsError = null,
                )
            }
            try {
                // coroutineScope (not bare async inside launch): if a read fails, the error is
                // rethrown here for the catch below. A bare async child would instead cancel
                // the whole launch and crash the app with an uncaught exception.
                val (ledgerYear, plans) = coroutineScope {
                    val ledgerDeferred = async { repository.readYear(year) }
                    val plansDeferred = async { budgetRepository.getPlans() }
                    ledgerDeferred.await() to plansDeferred.await()
                }

                val currentPeriod = BudgetPeriod.current()
                val periods = BudgetPeriod.allIn(year)
                val categories = BudgetSummaryMapper.build(
                    entries = ledgerYear.expenses,
                    periods = periods,
                    plans = plans,
                    buckets = LedgerProfile.current().spendingBuckets,
                )
                val totalBudgetByPeriod = periods
                    .mapNotNull { p -> plans[p.id]?.effectiveTotal?.takeIf { it > 0.0 }?.let { p.id to it } }
                    .toMap()
                val viewingCurrentYear = year == currentPeriod.year

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        earliestYear = ledgerYear.earliestYear?.coerceAtMost(year),
                        categories = categories,
                        periods = periods,
                        totalBudgetByPeriod = totalBudgetByPeriod,
                        currentPeriod = currentPeriod,
                        // The Add screen's banner is always about today, so only the current
                        // year's load may set it — browsing 2025 must not change it.
                        thisPeriod = if (viewingCurrentYear) {
                            BudgetStatus(
                                spent = categories.sumOf { c -> c.spentIn(currentPeriod.id) },
                                budget = totalBudgetByPeriod[currentPeriod.id] ?: 0.0,
                            )
                        } else {
                            state.thisPeriod
                        },
                        // Guests run on demo budgets; everyone else is asked once the window opens.
                        budgetPrompt = if (viewingCurrentYear) {
                            BudgetPeriod.budgetingNow()
                                ?.takeIf { p -> !Session.isGuest && plans[p.id]?.isEmpty != false }
                        } else {
                            state.budgetPrompt
                        },
                        selectedPeriodId = focusedPeriod(state.selectedPeriodId, periods, categories, currentPeriod),
                        // Keep the chosen category on a refresh; otherwise pre-pick the biggest
                        // spender so "By Category" has data the instant the user switches to it.
                        selectedCategory = state.selectedCategory?.takeIf { c -> categories.any { s -> s.category == c } }
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

    /**
     * The cut-off to open on: the one already in focus if it's still in range, else today's when
     * this is the current year, else the year's last cut-off that has spending (a past year opens
     * on its most recent activity rather than an empty December).
     */
    private fun focusedPeriod(
        current: String?,
        periods: List<BudgetPeriod>,
        categories: List<CategorySummary>,
        today: BudgetPeriod,
    ): String? {
        current?.let { id -> if (periods.any { it.id == id }) return id }
        periods.firstOrNull { it == today }?.let { return it.id }
        return periods.lastOrNull { p -> categories.any { c -> c.spentIn(p.id) > 0.0 } }?.id
            ?: periods.lastOrNull()?.id
    }

    private fun selectViewMode(mode: SummaryViewMode) {
        _uiState.update { state ->
            val category = state.selectedCategory
                ?: state.categories.maxByOrNull { it.totalSpent }?.category
            state.copy(viewMode = mode, selectedCategory = category)
        }
        if (mode == SummaryViewMode.BY_CATEGORY) ensureTransactionsLoaded()
    }

    /** Loads the drill-down transactions for the charted year once; no-op if already loaded. */
    private fun ensureTransactionsLoaded() {
        if (transactionsLoaded || _uiState.value.transactionsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(transactionsLoading = true, transactionsError = null) }
            val year = _uiState.value.year
            try {
                val txns = repository.expensesIn(year)
                transactionsLoaded = true
                _uiState.update {
                    // Ignore a late result for a year the user has already navigated away from.
                    if (it.year != year) it else it.copy(transactions = txns, transactionsLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(transactionsLoading = false, transactionsError = e.toUserMessage("Couldn't load transactions."))
                }
            }
        }
    }
}
