package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.auth.Session
import org.example.project.data.DemoRepository
import org.example.project.model.CategorySummary
import org.example.project.repository.TransactionRepository

data class SummaryUiState(
    val isLoading: Boolean = false,
    val categories: List<CategorySummary> = emptyList(),
    val months: List<String> = emptyList(),
    val selectedMonth: String? = null,
    val totalMonthlyBudget: Double = 0.0,
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
                val categories = if (Session.isGuest) DemoRepository.getSummary()
                                 else repository.getSummary()
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
