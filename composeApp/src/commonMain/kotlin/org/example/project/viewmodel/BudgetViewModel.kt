package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.model.CategoryGroups
import org.example.project.repository.BudgetRepository

data class BudgetUiState(
    val isLoading: Boolean = false,
    /** bucket display name -> the raw text in its input field (empty = no budget). */
    val amounts: Map<String, String> = CategoryGroups.buckets.associateWith { "" },
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** Live sum of the entered budgets — the combined monthly budget shown at the top. */
    val totalMonthly: Double get() = amounts.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
}

/**
 * Backs the [org.example.project.ui.BudgetScreen] editor. Loads the user's saved per-bucket
 * budgets, edits them as text fields, and persists all six buckets on save (explicit zeros for
 * cleared fields, so clearing a budget actually removes it rather than leaving a stale value).
 */
class BudgetViewModel(
    private val budgetRepository: BudgetRepository = BudgetRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, saved = false) }
            val saved = budgetRepository.getBudgets()
            val amounts = CategoryGroups.buckets.associateWith { bucket ->
                saved[bucket]?.takeIf { it > 0.0 }?.let { formatAmount(it) } ?: ""
            }
            _uiState.update { it.copy(isLoading = false, amounts = amounts) }
        }
    }

    fun updateBucket(bucket: String, raw: String) {
        val cleaned = sanitize(raw)
        _uiState.update { it.copy(amounts = it.amounts + (bucket to cleaned), saved = false) }
    }

    fun save() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null, saved = false) }
            // Write every bucket explicitly (0.0 for cleared) so a removed budget is actually cleared.
            val budgets = CategoryGroups.buckets.associateWith { bucket ->
                _uiState.value.amounts[bucket]?.toDoubleOrNull() ?: 0.0
            }
            val result = budgetRepository.saveBudgets(budgets)
            _uiState.update {
                if (result.isSuccess) it.copy(isSaving = false, saved = true)
                else it.copy(
                    isSaving = false,
                    error = result.exceptionOrNull()?.message ?: "Couldn't save budgets.",
                )
            }
        }
    }

    /** Whole numbers render without a trailing ".0"; anything with cents keeps them. */
    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    /** Keep digits and at most one decimal point, so the field can only hold a valid amount. */
    private fun sanitize(raw: String): String {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        val firstDot = filtered.indexOf('.')
        return if (firstDot < 0) filtered
        else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }
}
