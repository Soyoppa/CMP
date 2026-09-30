package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.config.LedgerProfile
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetPlan
import org.example.project.repository.BudgetRepository
import org.example.project.util.DateUtils
import org.example.project.util.toUserMessage

data class BudgetUiState(
    /** The cut-off being budgeted — always the one today falls in. */
    val period: BudgetPeriod,
    /** Days remaining in the cut-off after today (0 = it ends today). */
    val daysLeft: Int = 0,
    val isLoading: Boolean = false,
    /**
     * True when this cut-off has no saved budget yet and the fields were pre-filled from the
     * previous one — the user still has to review and save to set this cut-off's budget.
     */
    val isSuggestion: Boolean = false,
    /** Raw text of the overall budget field (empty = use the category sum). */
    val totalInput: String = "",
    /** The editable buckets, in display order. */
    val buckets: List<String> = emptyList(),
    /** bucket name -> the raw text in its input field (empty = no budget). */
    val amounts: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** Live sum of the per-category budgets. */
    val categoryTotal: Double get() = amounts.values.sumOf { it.toDoubleOrNull() ?: 0.0 }

    /** What spending will be measured against: the overall budget if set, else the category sum. */
    val effectiveTotal: Double get() = totalInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: categoryTotal

    /** Category budgets that add up to more than the overall budget — worth a gentle warning. */
    val categoriesExceedTotal: Boolean
        get() = (totalInput.toDoubleOrNull() ?: 0.0).let { total -> total > 0.0 && categoryTotal > total }

    /** Nothing entered yet — saving would set no budget at all. */
    val canSave: Boolean get() = effectiveTotal > 0.0 && !isSaving
}

sealed interface BudgetEvent {
    data class TotalChanged(val raw: String) : BudgetEvent
    data class AmountChanged(val bucket: String, val raw: String) : BudgetEvent
    data object SaveClicked : BudgetEvent
}

/**
 * Backs the [org.example.project.ui.BudgetScreen] editor for the **current cut-off** (1st–15th or
 * 16th–end of month): an overall budget plus optional per-bucket budgets.
 *
 * Every cut-off needs its own budget. When the current one has none, the form is pre-filled from
 * the most recent earlier cut-off (or half the old monthly budget) so the user reviews and saves
 * instead of retyping. Saving writes every field, with explicit zeros for cleared ones.
 */
class BudgetViewModel(
    private val budgetRepository: BudgetRepository = BudgetRepository(),
    buckets: List<String> = LedgerProfile.current().spendingBuckets.names,
    period: BudgetPeriod = BudgetPeriod.current(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BudgetUiState(
            period = period,
            daysLeft = (period.endDay - DateUtils.today().day).coerceAtLeast(0),
            isLoading = true,
            buckets = buckets,
            amounts = buckets.associateWith { "" },
        )
    )
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val plans = budgetRepository.getPlans()
            val saved = plans[period.id]?.takeUnless { it.isEmpty }
            val plan = saved ?: budgetRepository.suggestPlan(period, plans)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isSuggestion = saved == null && plan != null,
                    totalInput = plan?.total?.takeIf { it > 0.0 }?.let(::formatAmount).orEmpty(),
                    amounts = state.buckets.associateWith { bucket ->
                        plan?.byBucket?.get(bucket)?.takeIf { it > 0.0 }?.let(::formatAmount).orEmpty()
                    },
                )
            }
        }
    }

    fun onEvent(event: BudgetEvent) {
        when (event) {
            is BudgetEvent.TotalChanged -> _uiState.update { it.copy(totalInput = sanitize(event.raw), saved = false) }
            is BudgetEvent.AmountChanged -> _uiState.update {
                it.copy(amounts = it.amounts + (event.bucket to sanitize(event.raw)), saved = false)
            }
            BudgetEvent.SaveClicked -> save()
        }
    }

    private fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null, saved = false) }
        viewModelScope.launch {
            val plan = BudgetPlan(
                total = state.totalInput.toDoubleOrNull() ?: 0.0,
                byBucket = state.buckets.associateWith { bucket -> state.amounts[bucket]?.toDoubleOrNull() ?: 0.0 },
            )
            val result = budgetRepository.savePlan(state.period, plan)
            _uiState.update {
                if (result.isSuccess) it.copy(isSaving = false, saved = true, isSuggestion = false)
                else it.copy(
                    isSaving = false,
                    error = result.exceptionOrNull()?.toUserMessage("Couldn't save the budget.") ?: "Couldn't save the budget.",
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
