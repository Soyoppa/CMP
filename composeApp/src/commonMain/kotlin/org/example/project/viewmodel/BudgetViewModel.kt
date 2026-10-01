package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.example.project.AppContainer
import org.example.project.config.LedgerProfile
import org.example.project.model.BudgetCycle
import org.example.project.model.BudgetPeriod
import org.example.project.model.BudgetPlan
import org.example.project.repository.ConfigRepository
import org.example.project.util.DateUtils
import org.example.project.util.toUserMessage

data class BudgetUiState(
    /** The period being budgeted. */
    val period: BudgetPeriod,
    /**
     * Periods the user can switch between without leaving the sheet: the cut-offs of [period]'s
     * month (just the month itself when budgeting monthly).
     */
    val selectablePeriods: List<BudgetPeriod> = period.periodsInMonth(),
    /** Days until [period] ends, counted from today (0 = it ends today; negative once it's over). */
    val daysLeft: Int = 0,
    /** True when [period] hasn't started yet (budgeting ahead). */
    val isUpcoming: Boolean = false,
    val isLoading: Boolean = true,
    /**
     * True when this period has no saved budget yet and the fields were pre-filled from the
     * previous one — the user still has to review and save to set this period's budget.
     */
    val isSuggestion: Boolean = false,
    /** Raw text of the overall budget field (empty = use the category sum). */
    val totalInput: String = "",
    /** The editable buckets, in display order — the user's expense categories. Empty until they add some. */
    val buckets: List<String> = emptyList(),
    /** bucket name -> the raw text in its input field (empty = no budget). */
    val amounts: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    /** "cut-off" or "month", for copy that has to name the period. */
    val periodNoun: String get() = period.cycle.noun

    /** True once the period is over — its budget can still be corrected. */
    val hasEnded: Boolean get() = daysLeft < 0

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
    data class PeriodSelected(val period: BudgetPeriod) : BudgetEvent
    data class TotalChanged(val raw: String) : BudgetEvent
    data class AmountChanged(val bucket: String, val raw: String) : BudgetEvent
    data object SaveClicked : BudgetEvent
}

/**
 * Backs the [org.example.project.ui.BudgetScreen] editor for one period: an overall budget plus
 * optional per-category budgets.
 *
 * It opens on [initialPeriod] — whichever period the caller is showing, so the Summary's "Set a
 * budget" always edits the cut-off on screen — or, with none given (Settings, the payday prompt),
 * on the period being asked for now. Its pills switch between the cut-offs of that month, so a
 * budget can be set ahead or corrected afterwards regardless of today's date.
 *
 * A period with no budget yet is pre-filled from the most recent earlier one so the user reviews
 * and saves instead of retyping. Budgets live with the rest of the user's config (on the phone or
 * in their account), so a save shows up on the Summary straight away.
 */
class BudgetViewModel(
    private val config: ConfigRepository = AppContainer.session().config,
    private val profile: LedgerProfile = AppContainer.session().profile,
    /** The period to budget; null = the one the app would ask for today. */
    private val initialPeriod: BudgetPeriod? = null,
    private val today: LocalDate = DateUtils.today(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState(period = initialPeriod ?: BudgetPeriod.of(today)))
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // The cycle decides which periods exist, so the target is only known once config loads.
            config.ensureLoaded()
            load(targetPeriod(config.config.cycle))
        }
    }

    /** The period to open on, re-cut to the user's cycle in case the caller's was from the other. */
    private fun targetPeriod(cycle: BudgetCycle): BudgetPeriod =
        initialPeriod?.inCycle(cycle)
            ?: BudgetPeriod.budgetingNow(today, cycle)
            ?: BudgetPeriod.of(today, cycle)

    private fun load(period: BudgetPeriod) {
        _uiState.update { state ->
            state.copy(
                period = period,
                selectablePeriods = period.periodsInMonth(),
                daysLeft = period.endDate.toEpochDays().toInt() - today.toEpochDays().toInt(),
                isUpcoming = today < period.startDate,
                isLoading = true,
                isSuggestion = false,
                totalInput = "",
                amounts = emptyMap(),
                saved = false,
                error = null,
            )
        }
        viewModelScope.launch {
            config.ensureLoaded()
            val userConfig = config.config
            val saved = userConfig.planFor(period)
            val plan = saved ?: userConfig.suggestedPlan(period)
            val buckets = profile.bucketsFor(userConfig).names
            _uiState.update { state ->
                if (state.period != period) return@update state // the user switched again meanwhile
                state.copy(
                    isLoading = false,
                    error = config.state.value.error,
                    isSuggestion = saved == null && plan != null,
                    buckets = buckets,
                    totalInput = plan?.total?.takeIf { it > 0.0 }?.let(::formatAmount).orEmpty(),
                    amounts = buckets.associateWith { bucket ->
                        plan?.byBucket?.get(bucket)?.takeIf { it > 0.0 }?.let(::formatAmount).orEmpty()
                    },
                )
            }
        }
    }

    fun onEvent(event: BudgetEvent) {
        when (event) {
            is BudgetEvent.PeriodSelected ->
                if (event.period != _uiState.value.period && event.period in _uiState.value.selectablePeriods) {
                    load(event.period)
                }
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
            val result = config.saveBudget(state.period, plan)
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
