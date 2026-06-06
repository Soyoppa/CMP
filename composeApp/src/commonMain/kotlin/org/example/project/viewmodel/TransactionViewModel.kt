package org.example.project.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.auth.Session
import org.example.project.config.SchemaFeatures
import org.example.project.data.AiRepository
import org.example.project.domain.transaction.AddTransactionUseCase
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.domain.transaction.TransactionFormEvent
import org.example.project.domain.transaction.TransactionFormReducer
import org.example.project.domain.transaction.TransactionFormState
import org.example.project.model.PaymentMode
import org.example.project.model.Transaction
import org.example.project.model.TransactionCategory
import org.example.project.util.DateUtils
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
 * Voice entry is layered on top: the platform [VoiceInputController] streams recognition state,
 * which the ViewModel translates into form updates — parsing the transcript locally and falling
 * back to [AiRepository.classifyCategory] only when the spoken category is ambiguous.
 */
class TransactionViewModel(
    private val addTransactionUseCase: AddTransactionUseCase = AddTransactionUseCase(),
    private val voiceController: VoiceInputController = provideVoiceInputController(),
    private val aiRepository: AiRepository = AiRepository(),
) : ViewModel() {

    private val _formState = MutableStateFlow(createInitialFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    private val _effects = MutableSharedFlow<TransactionFormEffect>()
    val effects: SharedFlow<TransactionFormEffect> = _effects.asSharedFlow()

    init {
        if (voiceController.isSupported) {
            _formState.update { it.copy(isVoiceSupported = true) }
            observeVoice()
        }
    }

    fun onEvent(event: TransactionFormEvent) {
        when (event) {
            TransactionFormEvent.FormSubmitted -> addTransaction()
            TransactionFormEvent.VoiceInputToggled -> toggleVoice()
            else -> _formState.update { TransactionFormReducer.reduce(it, event) }
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
                    is VoiceState.Idle ->
                        dispatchVoiceStatus(VoiceStatus.Idle)
                    is VoiceState.Listening ->
                        dispatchVoiceStatus(VoiceStatus.Listening)
                    is VoiceState.Result -> {
                        dispatchVoiceStatus(VoiceStatus.Processing)
                        applyTranscript(state.transcript)
                        voiceController.reset()
                    }
                    is VoiceState.Error -> {
                        dispatchVoiceStatus(VoiceStatus.Idle)
                        _effects.emit(TransactionFormEffect.ShowError(state.message))
                        voiceController.reset()
                    }
                }
            }
        }
    }

    private fun dispatchVoiceStatus(status: VoiceStatus) =
        _formState.update { TransactionFormReducer.reduce(it, TransactionFormEvent.VoiceStatusChanged(status)) }

    /**
     * Local-first: parse amount/description/income + a best-effort category from the transcript.
     * Only when the category is still unknown do we spend an AI round-trip to classify it.
     */
    private suspend fun applyTranscript(transcript: String) {
        val features = SchemaFeatures.current()

        // First pass detects the income/expense cue so we can pick the correct category list.
        val probe = VoiceTransactionParser.parse(transcript, emptyList(), features.showIncomeOption)
        val income = probe.isIncome ?: _formState.value.isIncome
        val options =
            if (features.showIncomeOption && income) features.incomeCategoryOptions
            else features.categoryOptions

        val parsed = VoiceTransactionParser.parse(transcript, options, features.showIncomeOption)

        if (parsed.amount == null && parsed.description.isBlank()) {
            dispatchVoiceStatus(VoiceStatus.Idle)
            _effects.emit(TransactionFormEffect.ShowError("Couldn't understand that — try \"250 for groceries\"."))
            return
        }

        _formState.update {
            TransactionFormReducer.reduce(
                it,
                TransactionFormEvent.VoiceResultApplied(
                    amount = parsed.amount,
                    description = parsed.description,
                    category = parsed.category,
                    isIncome = probe.isIncome,
                ),
            )
        }

        // Category fallback: parser couldn't match a known option → let the AI decide.
        if (parsed.category == null) {
            val aiCategory = aiRepository.classifyCategory(transcript, options)
            if (aiCategory != null) {
                _formState.update {
                    TransactionFormReducer.reduce(
                        it,
                        TransactionFormEvent.VoiceResultApplied(
                            amount = null,
                            description = "",
                            category = aiCategory,
                            isIncome = null,
                        ),
                    )
                }
            }
        }

        dispatchVoiceStatus(VoiceStatus.Idle)
    }

    // --- Save ----------------------------------------------------------------

    private fun addTransaction() {
        val state = _formState.value

        validateForm(state)?.let { error ->
            viewModelScope.launch { _effects.emit(TransactionFormEffect.ShowError(error)) }
            return
        }

        // Guest/demo mode: never write to the sheet, but show an honest success.
        if (Session.isGuest) {
            viewModelScope.launch {
                _effects.emit(TransactionFormEffect.ShowSuccess("Demo mode — transaction not saved."))
                resetForm()
            }
            return
        }

        _formState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = addTransactionUseCase.invokeDetailed(buildTransaction(state))
                if (result.success) {
                    _effects.emit(TransactionFormEffect.ShowSuccess("Transaction saved successfully!"))
                    resetForm()
                } else {
                    _effects.emit(TransactionFormEffect.ShowError(result.errorMessage ?: "Failed to save transaction."))
                    _formState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _effects.emit(TransactionFormEffect.ShowError("Error: ${e.message ?: "Unknown error occurred"}"))
                _formState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Clears the form back to defaults and signals the UI to drop focus/keyboard. */
    private suspend fun resetForm() {
        _formState.value = createInitialFormState().copy(isVoiceSupported = voiceController.isSupported)
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

    private fun createInitialFormState() = TransactionFormState(
        selectedDate = DateUtils.getCurrentDateFormatted(),
        selectedCategory = TransactionCategory.OTHER.displayName,
        selectedPaymentMode = PaymentMode.OTHER.displayName,
    )
}
