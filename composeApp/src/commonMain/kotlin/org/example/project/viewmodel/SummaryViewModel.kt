package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.AppContainer
import org.example.project.config.LedgerProfile
import org.example.project.data.ledger.LedgerEntry
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetStatus
import org.example.project.model.BudgetSummaryMapper
import org.example.project.model.CategorySummary
import org.example.project.model.SpendingBuckets
import org.example.project.model.UserConfig
import org.example.project.repository.ConfigRepository
import org.example.project.repository.LedgerRepository
import org.example.project.util.DateUtils
import org.example.project.util.toUserMessage

/** Whether the bar chart plots every category summed, or one category's trend across the year. */
enum class SummaryViewMode { TOTAL, BY_CATEGORY }

data class SummaryUiState(
    /** First load: nothing to show yet. */
    val isLoading: Boolean = true,
    /** Reloading with data already on screen (pull-to-refresh, after a change elsewhere). */
    val isRefreshing: Boolean = false,
    /** The calendar year on screen — the app reads and charts one year at a time. */
    val year: Int = DateUtils.today().year,
    /** Earliest year with any row; bounds how far back the year picker goes. */
    val earliestYear: Int? = null,
    val categories: List<CategorySummary> = emptyList(),
    /** How ledger categories roll up into [categories] (one per user category, or the sheet's buckets). */
    val buckets: SpendingBuckets = SpendingBuckets.of(emptyList()),
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
     * screen shows. Always about today, whichever year is being browsed.
     */
    val thisPeriod: BudgetStatus? = null,
    /**
     * The cut-off the user should budget now: its budgeting window is open (salary has arrived)
     * and it has no budget yet. Can be the *upcoming* cut-off in the last days of the current one.
     */
    val budgetPrompt: BudgetPeriod? = null,
    /**
     * Whether [budgetPrompt] may open the budget sheet by itself. Not for someone who has never
     * set a budget (e.g. on their very first launch): they get the banner, not a pop-up.
     */
    val budgetPromptOpensSheet: Boolean = false,
    val viewMode: SummaryViewMode = SummaryViewMode.TOTAL,
    val selectedCategory: String? = null,
    /** [year]'s expense rows, for the By Category drill-down. */
    val transactions: List<LedgerEntry> = emptyList(),
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
    /** Reload the year (pull-to-refresh, retry). */
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
 * Backs the Summary screen (and the Add screen's "left to spend" banner). Reads exactly one
 * calendar year at a time and recomputes on its own whenever the ledger changes (a transaction
 * added or deleted anywhere) or the user's lists and budgets change.
 */
class SummaryViewModel(
    private val ledger: LedgerRepository = AppContainer.session().ledger,
    private val config: ConfigRepository = AppContainer.session().config,
    private val profile: LedgerProfile = AppContainer.session().profile,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    /** The loaded year's expenses — kept so a budget edit recomputes without re-reading the ledger. */
    private var loaded: LoadedYear? = null

    private class LoadedYear(val year: Int, val expenses: List<LedgerEntry>, val earliestYear: Int?)

    init {
        load(_uiState.value.year)
        viewModelScope.launch { ledger.revision.drop(1).collect { load(_uiState.value.year) } }
        viewModelScope.launch {
            config.state.map { it.config }.distinctUntilChanged().drop(1).collect { recompute() }
        }
    }

    fun onEvent(event: SummaryEvent) {
        when (event) {
            SummaryEvent.Refresh -> load(_uiState.value.year)
            is SummaryEvent.YearSelected ->
                if (event.year != _uiState.value.year) load(event.year)
            is SummaryEvent.MonthSelected -> _uiState.update { it.copy(selectedPeriodId = it.periodIn(event.monthKey)) }
            is SummaryEvent.PeriodSelected -> _uiState.update { it.copy(selectedPeriodId = event.periodId) }
            is SummaryEvent.ViewModeSelected -> _uiState.update { state ->
                state.copy(
                    viewMode = event.mode,
                    selectedCategory = state.selectedCategory ?: state.categories.maxByOrNull { it.totalSpent }?.category,
                )
            }
            is SummaryEvent.CategorySelected -> _uiState.update { it.copy(selectedCategory = event.category) }
        }
    }

    private fun load(year: Int) {
        viewModelScope.launch {
            val switchingYear = year != _uiState.value.year
            _uiState.update {
                // Keep what's on screen while reloading the same year; switching year starts clean.
                val hasData = !it.isLoading && !switchingYear && it.error == null
                it.copy(
                    year = year,
                    isLoading = !hasData,
                    isRefreshing = hasData,
                    error = null,
                    categories = if (switchingYear) emptyList() else it.categories,
                )
            }
            try {
                // Budgets and categories first: the prompt and the breakdown both depend on them.
                config.ensureLoaded()
                val ledgerYear = ledger.readYear(year)
                if (_uiState.value.year != year) return@launch // the user moved on meanwhile
                loaded = LoadedYear(year, ledgerYear.expenses, ledgerYear.earliestYear)
                recompute()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = e.toUserMessage("Couldn't load your summary."))
                }
            }
        }
    }

    /** Rebuilds every figure from the loaded year and the current lists and budgets. */
    private fun recompute() {
        val year = loaded?.takeIf { it.year == _uiState.value.year } ?: return
        val configState = config.state.value
        val userConfig: UserConfig = configState.config
        val plans = userConfig.budgets
        val buckets = profile.bucketsFor(userConfig)
        val currentPeriod = BudgetPeriod.current()
        val periods = BudgetPeriod.allIn(year.year)
        val categories = BudgetSummaryMapper.build(year.expenses, periods, plans, buckets)
        val totalBudgetByPeriod = periods
            .mapNotNull { p -> plans[p.id]?.effectiveTotal?.takeIf { it > 0.0 }?.let { p.id to it } }
            .toMap()
        val viewingCurrentYear = year.year == currentPeriod.year

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isRefreshing = false,
                earliestYear = year.earliestYear?.coerceAtMost(year.year),
                categories = categories,
                buckets = buckets,
                periods = periods,
                totalBudgetByPeriod = totalBudgetByPeriod,
                currentPeriod = currentPeriod,
                transactions = year.expenses,
                // The Add screen's banner is always about today, so only the current year may set it.
                thisPeriod = if (viewingCurrentYear) {
                    BudgetStatus(
                        spent = categories.sumOf { c -> c.spentIn(currentPeriod.id) },
                        budget = totalBudgetByPeriod[currentPeriod.id] ?: 0.0,
                    )
                } else {
                    state.thisPeriod
                },
                // Only ask once the saved budgets are known — never on a failed or partial load.
                budgetPrompt = when {
                    !viewingCurrentYear -> state.budgetPrompt
                    configState.error != null -> null
                    else -> BudgetPeriod.budgetingNow()?.takeIf { p -> plans[p.id]?.isEmpty != false }
                },
                budgetPromptOpensSheet = plans.values.any { !it.isEmpty },
                selectedPeriodId = focusedPeriod(state.selectedPeriodId, periods, categories, currentPeriod),
                // Keep the chosen category on a refresh; otherwise pre-pick the biggest spender so
                // "By Category" has data the instant the user switches to it.
                selectedCategory = state.selectedCategory?.takeIf { c -> categories.any { s -> s.category == c } }
                    ?: categories.maxByOrNull { s -> s.totalSpent }?.category,
            )
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
}
