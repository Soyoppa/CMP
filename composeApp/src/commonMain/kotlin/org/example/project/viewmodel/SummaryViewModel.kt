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
import org.example.project.data.CategoryTransaction
import org.example.project.data.DemoRepository
import org.example.project.model.BudgetSummaryMapper
import org.example.project.model.CategorySummary
import org.example.project.repository.BudgetRepository
import org.example.project.repository.TransactionRepository
import org.example.project.util.DateUtils

/** Whether the bar chart plots every category summed, or one category's trend across months. */
enum class SummaryViewMode { TOTAL, BY_CATEGORY }

data class SummaryUiState(
    val isLoading: Boolean = false,
    val categories: List<CategorySummary> = emptyList(),
    val months: List<String> = emptyList(),
    val selectedMonth: String? = null,
    val totalMonthlyBudget: Double = 0.0,
    val budgetByCategory: Map<String, Double> = emptyMap(),
    val viewMode: SummaryViewMode = SummaryViewMode.TOTAL,
    val selectedCategory: String? = null,
    val transactions: List<CategoryTransaction> = emptyList(),
    val transactionsLoading: Boolean = false,
    val transactionsError: String? = null,
    val error: String? = null,
)

class SummaryViewModel(
    private val repository: TransactionRepository = TransactionRepository(),
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

    fun load() {
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
                val categories = if (Session.isGuest) {
                    DemoRepository.getSummary()
                } else {
                    val txnsDeferred = async { repository.getTransactions() }
                    val budgetsDeferred = async { budgetRepository.getBudgets() }
                    BudgetSummaryMapper.build(txnsDeferred.await(), budgetsDeferred.await())
                }
                val months = categories.firstOrNull()?.months ?: emptyList()
                val totalMonthlyBudget = categories.sumOf { it.monthlyBudget }
                val budgetByCategory = categories.associate { it.category.lowercase() to it.monthlyBudget }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categories,
                        months = months,
                        totalMonthlyBudget = totalMonthlyBudget,
                        budgetByCategory = budgetByCategory,
                        // Default to the current calendar month; if the sheet has no column
                        // for it, fall back to the latest month that actually has data.
                        selectedMonth = months.firstOrNull { m ->
                            DateUtils.monthNumberFromName(m) == DateUtils.currentMonthNumber()
                        } ?: months.lastOrNull { m ->
                            categories.any { c -> c.spentIn(m) > 0.0 }
                        },
                        // Pre-pick the biggest spender so the "By Category" view has data the
                        // instant the user switches to it — no empty intermediate state.
                        selectedCategory = categories.maxByOrNull { it.totalSpent }?.category,
                    )
                }
                // Refreshing while the drill-down is open should reload its rows too.
                if (_uiState.value.viewMode == SummaryViewMode.BY_CATEGORY) {
                    ensureTransactionsLoaded()
                }
            } catch (e: Exception) {
                // Do not forward e.message — Ktor network exceptions can embed the full
                // request URL including the ?key=<API_KEY> query parameter.
                _uiState.update { it.copy(isLoading = false, error = "Failed to load data. Please try again.") }
            }
        }
    }

    fun selectMonth(month: String) {
        _uiState.update { it.copy(selectedMonth = month) }
    }

    fun selectViewMode(mode: SummaryViewMode) {
        _uiState.update { state ->
            val category = state.selectedCategory
                ?: state.categories.maxByOrNull { it.totalSpent }?.category
            state.copy(viewMode = mode, selectedCategory = category)
        }
        if (mode == SummaryViewMode.BY_CATEGORY) ensureTransactionsLoaded()
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /** Loads the drill-down transactions once; no-op if already loaded or in flight. */
    private fun ensureTransactionsLoaded() {
        if (transactionsLoaded || _uiState.value.transactionsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(transactionsLoading = true, transactionsError = null) }
            try {
                val txns = if (Session.isGuest) DemoRepository.getTransactions()
                           else repository.getTransactions()
                transactionsLoaded = true
                _uiState.update { it.copy(transactions = txns, transactionsLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(transactionsLoading = false, transactionsError = e.message) }
            }
        }
    }
}
