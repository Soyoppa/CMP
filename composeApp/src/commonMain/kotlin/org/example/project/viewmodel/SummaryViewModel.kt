package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.model.CategorySummary
import org.example.project.repository.TransactionRepository

data class SummaryUiState(
    val isLoading: Boolean = false,
    val categories: List<CategorySummary> = emptyList(),
    val months: List<String> = emptyList(),
    val selectedMonth: String? = null,
    /** Sum of all per-category monthly budgets from the Budget tab. Used as chart reference line. */
    val totalMonthlyBudget: Double = 0.0,
    /** Monthly budget per category, keyed by lowercase category name. */
    val budgetByCategory: Map<String, Double> = emptyMap(),
    val error: String? = null,
)

class SummaryViewModel(
    private val repository: TransactionRepository = TransactionRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Fetch summary rows and budget in parallel
                val summaryDeferred = async { repository.getSummary() }
                val budgetDeferred = async { repository.getBudget() }

                val categories = summaryDeferred.await()
                val budget = budgetDeferred.await()

                val months = categories.firstOrNull()?.months ?: emptyList()
                // Total budget per month = sum of every category's monthlyBudget from the Budget tab
                val totalMonthlyBudget = budget.sumOf { it.monthlyBudget }
                val budgetByCategory = budget.associate { it.category.lowercase() to it.monthlyBudget }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categories,
                        months = months,
                        totalMonthlyBudget = totalMonthlyBudget,
                        budgetByCategory = budgetByCategory,
                        selectedMonth = months.lastOrNull { m ->
                            categories.any { c -> c.spentIn(m) > 0.0 }
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectMonth(month: String) {
        _uiState.update { it.copy(selectedMonth = month) }
    }
}
