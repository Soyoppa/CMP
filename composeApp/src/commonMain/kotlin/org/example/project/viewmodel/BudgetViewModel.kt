package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.config.LedgerProfile
import org.example.project.repository.BudgetRepository
import org.example.project.util.toUserMessage

data class BudgetUiState(
    val isLoading: Boolean = false,
    /** The editable buckets, in display order. */
    val buckets: List<String> = emptyList(),
    /** bucket name -> the raw text in its input field (empty = no budget). */
    val amounts: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** Live sum of the entered budgets — the combined monthly budget shown at the top. */
    val totalMonthly: Double get() = amounts.values.sumOf { it.toDoubleOrNull() ?: 0.0 }
}

sealed interface BudgetEvent {
    data class AmountChanged(val bucket: String, val raw: String) : BudgetEvent
    data object SaveClicked : BudgetEvent
}

/**
 * Backs the [org.example.project.ui.BudgetScreen] editor. Loads the saved per-bucket budgets,
 * edits them as text, and on save writes every bucket (explicit zeros for cleared fields, so
 * clearing a budget actually removes it rather than leaving a stale value).
 */
class BudgetViewModel(
    private val budgetRepository: BudgetRepository = BudgetRepository(),
    buckets: List<String> = LedgerProfile.current().spendingBuckets.names,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BudgetUiState(isLoading = true, buckets = buckets, amounts = buckets.associateWith { "" })
    )
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = budgetRepository.getBudgets()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    amounts = state.buckets.associateWith { bucket ->
                        saved[bucket]?.takeIf { it > 0.0 }?.let(::formatAmount).orEmpty()
                    },
                )
            }
        }
    }

    fun onEvent(event: BudgetEvent) {
        when (event) {
            is BudgetEvent.AmountChanged -> _uiState.update {
                it.copy(amounts = it.amounts + (event.bucket to sanitize(event.raw)), saved = false)
            }
            BudgetEvent.SaveClicked -> save()
        }
    }

    private fun save() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null, saved = false) }
        viewModelScope.launch {
            val state = _uiState.value
            val budgets = state.buckets.associateWith { bucket -> state.amounts[bucket]?.toDoubleOrNull() ?: 0.0 }
            val result = budgetRepository.saveBudgets(budgets)
            _uiState.update {
                if (result.isSuccess) it.copy(isSaving = false, saved = true)
                else it.copy(
                    isSaving = false,
                    error = result.exceptionOrNull()?.toUserMessage("Couldn't save budgets.") ?: "Couldn't save budgets.",
                )
            }
        }
    }

    /** Whole numbers render without a trailing ".0"; anything with cents keeps them. */
    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    /** Keep digits and at most one decimal point (max 12 chars), so the field only holds an amount. */
    private fun sanitize(raw: String): String {
        val filtered = raw.filter { it.isDigit() || it == '.' }.take(12)
        val firstDot = filtered.indexOf('.')
        return if (firstDot < 0) filtered
        else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
    }
}
