package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.AppContainer
import org.example.project.auth.AppUser
import org.example.project.config.LedgerProfile
import org.example.project.data.ai.AiRepository
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.domain.transaction.TransactionFormEvent
import org.example.project.domain.transaction.TransactionFormReducer
import org.example.project.domain.transaction.TransactionFormState
import org.example.project.domain.transaction.VoiceAiUsage
import org.example.project.model.OptionList
import org.example.project.model.Transaction
import org.example.project.repository.ConfigRepository
import org.example.project.repository.LedgerRepository
import org.example.project.util.DateUtils
import org.example.project.util.toUserMessage
import org.example.project.voice.VoiceInputController
import org.example.project.voice.VoiceState
import org.example.project.voice.VoiceStatus
import org.example.project.voice.VoiceTransactionParser
import org.example.project.voice.provideVoiceInputController

/**
 * Owns the add-transaction form. Unidirectional flow: the UI dispatches [TransactionFormEvent]s,
 * the pure [TransactionFormReducer] folds them into [formState], and one-time outcomes
 * (success / error / clear) are emitted as [TransactionFormEffect]s for the UI to react to.
 *
 * The pickers show the user's own lists from [ConfigRepository] and follow every edit made
 * elsewhere; a name typed into a picker is added to the list on the spot.
 *
 * Voice entry is layered on top: the platform [VoiceInputController] streams recognition state,
 * which the ViewModel translates into form updates — parsing the transcript locally and falling
 * back to [AiRepository.classifyCategory] only when the spoken category is ambiguous (and the
 * session has AI at all).
 */
class TransactionFormViewModel(
    private val ledger: LedgerRepository = AppContainer.session().ledger,
    private val config: ConfigRepository = AppContainer.session().config,
    private val profile: LedgerProfile = AppContainer.session().profile,
    private val voiceController: VoiceInputController = provideVoiceInputController(),
    /** Voice category fallback; null without an account (the AI assistant is an account feature). */
    private val aiRepository: AiRepository? =
        AppContainer.aiRepository.takeIf { AppContainer.session().user is AppUser.Account },
) : ViewModel() {

    private val _formState = MutableStateFlow(initialFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    private val _effects = MutableSharedFlow<TransactionFormEffect>()
    val effects: SharedFlow<TransactionFormEffect> = _effects.asSharedFlow()

    init {
        if (voiceController.isSupported) {
            _formState.update { it.copy(isVoiceSupported = true) }
            observeVoice()
        }
        observeOptions()
    }

    fun onEvent(event: TransactionFormEvent) {
        when (event) {
            TransactionFormEvent.FormSubmitted -> addTransaction()
            TransactionFormEvent.VoiceInputToggled -> toggleVoice()
            TransactionFormEvent.ClearForm -> viewModelScope.launch { resetForm() }
            is TransactionFormEvent.OptionCreated -> createOption(event.list, event.name)
            else -> reduce(event)
        }
    }

    private fun reduce(event: TransactionFormEvent) = _formState.update { TransactionFormReducer.reduce(it, event) }

    /** Keeps the pickers in step with the session's lists, wherever they're edited. */
    private fun observeOptions() {
        viewModelScope.launch { config.ensureLoaded() }
        viewModelScope.launch {
            config.state.map { it.config }.distinctUntilChanged().collect { c ->
                reduce(TransactionFormEvent.OptionsLoaded(c.expenseCategories, c.incomeCategories, c.paymentModes))
            }
        }
    }

    private fun createOption(list: OptionList, name: String) {
        viewModelScope.launch {
            config.addOption(list, name)
                .onSuccess { saved ->
                    reduce(
                        if (list == OptionList.PAYMENT_MODES) TransactionFormEvent.PaymentModeSelected(saved)
                        else TransactionFormEvent.CategorySelected(saved)
                    )
                }
                .onFailure { e -> _effects.emit(TransactionFormEffect.ShowError(e.toUserMessage("Couldn't add that."))) }
        }
    }

    // --- Voice entry ---------------------------------------------------------

    private fun toggleVoice() {
        if (!voiceController.isSupported || _formState.value.isLoading) return
        when (_formState.value.voiceStatus) {
            VoiceStatus.Listening -> voiceController.stop()
            VoiceStatus.Processing -> Unit // ignore taps while parsing
            VoiceStatus.Idle -> voiceController.start()
        }
    }

    /** Bridges the controller's [VoiceState] stream into form events + AI category resolution. */
    private fun observeVoice() {
        viewModelScope.launch {
            voiceController.state.collect { state ->
                when (state) {
                    is VoiceState.Idle -> reduce(TransactionFormEvent.VoiceStatusChanged(VoiceStatus.Idle))
                    is VoiceState.Listening -> reduce(TransactionFormEvent.VoiceStatusChanged(VoiceStatus.Listening))
                    is VoiceState.Result -> {
                        reduce(TransactionFormEvent.VoiceStatusChanged(VoiceStatus.Processing))
                        applyTranscript(state.transcript)
                        voiceController.reset()
                    }
                    is VoiceState.Error -> {
                        reduce(TransactionFormEvent.VoiceStatusChanged(VoiceStatus.Idle))
                        _effects.emit(TransactionFormEffect.ShowError(state.message))
                        voiceController.reset()
                    }
                }
            }
        }
    }

    /**
     * Local-first: parse amount/description/income + a best-effort category from the transcript.
     * Only when the category is still unknown do we spend an AI round-trip to classify it.
     */
    private suspend fun applyTranscript(transcript: String) {
        // First pass detects the income/expense cue so we can pick the correct category list.
        val probe = VoiceTransactionParser.parse(transcript, emptyList(), profile.showIncomeOption)
        val income = profile.showIncomeOption && (probe.isIncome ?: _formState.value.isIncome)
        val options = with(_formState.value) { if (income) incomeCategories else expenseCategories }

        val parsed = VoiceTransactionParser.parse(transcript, options, profile.showIncomeOption)

        if (parsed.amount == null && parsed.description.isBlank()) {
            reduce(TransactionFormEvent.VoiceStatusChanged(VoiceStatus.Idle))
            _effects.emit(TransactionFormEffect.ShowError("Couldn't understand that — try \"250 for groceries\"."))
            return
        }

        reduce(
            TransactionFormEvent.VoiceResultApplied(
                amount = parsed.amount,
                description = parsed.description,
                category = parsed.category,
                isIncome = probe.isIncome,
            )
        )

        val ai = aiRepository
        if (parsed.category == null && ai != null && options.isNotEmpty()) {
            // The parser couldn't match one of the user's categories → let the AI pick one.
            val classification = ai.classifyCategory(transcript, options)
            val result = classification.result
            reduce(
                TransactionFormEvent.VoiceAiUsageReported(
                    VoiceAiUsage(
                        aiInvoked = true,
                        provider = result?.provider?.displayName,
                        model = result?.model,
                        promptTokens = result?.promptTokens ?: 0,
                        responseTokens = result?.responseTokens ?: 0,
                    )
                )
            )
            classification.category?.let { category ->
                reduce(TransactionFormEvent.VoiceResultApplied(amount = null, description = "", category = category, isIncome = null))
            }
        } else {
            // Matched locally (or nothing to match against) — no AI round-trip, no tokens spent.
            reduce(TransactionFormEvent.VoiceAiUsageReported(VoiceAiUsage(aiInvoked = false)))
        }

        reduce(TransactionFormEvent.VoiceStatusChanged(VoiceStatus.Idle))
    }

    // --- Save ----------------------------------------------------------------

    private fun addTransaction() {
        val state = _formState.value
        validateForm(state)?.let { error ->
            viewModelScope.launch { _effects.emit(TransactionFormEffect.ShowError(error)) }
            return
        }

        _formState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = ledger.addTransaction(buildTransaction(state))
                if (result.success) {
                    _effects.emit(TransactionFormEffect.ShowSuccess("Transaction saved"))
                    resetForm()
                } else {
                    _effects.emit(TransactionFormEffect.ShowError(result.errorMessage ?: "Failed to save transaction."))
                    _formState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _effects.emit(TransactionFormEffect.ShowError(e.toUserMessage("Couldn't save the transaction.")))
                _formState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Clears the form (keeping the loaded lists) and signals the UI to drop focus/keyboard. */
    private suspend fun resetForm() {
        _formState.update { current ->
            initialFormState().copy(
                isVoiceSupported = current.isVoiceSupported,
                expenseCategories = current.expenseCategories,
                incomeCategories = current.incomeCategories,
                paymentModes = current.paymentModes,
            )
        }
        _effects.emit(TransactionFormEffect.FormCleared)
    }

    private fun validateForm(state: TransactionFormState): String? = when {
        state.amount.isEmpty() -> "Please enter an amount"
        state.amount.toDoubleOrNull() == null -> "Invalid amount format"
        state.amount.toDouble() <= 0 -> "Amount must be greater than 0"
        state.description.isBlank() -> "Please enter a description"
        state.selectedDate.isEmpty() -> "Please select a date"
        else -> null
    }

    private fun buildTransaction(state: TransactionFormState): Transaction {
        val amount = state.amount.toDouble()
        return Transaction(
            date = state.selectedDate,
            description = state.description.trim(),
            inflow = if (state.isIncome) amount else 0.0,
            outflow = if (state.isIncome) 0.0 else amount,
            category = state.selectedCategory,
            modeOfPayment = state.selectedPaymentMode,
            isPaid = state.isPaid,
        )
    }

    private fun initialFormState() = TransactionFormState(selectedDate = DateUtils.getCurrentDateFormatted())
}
