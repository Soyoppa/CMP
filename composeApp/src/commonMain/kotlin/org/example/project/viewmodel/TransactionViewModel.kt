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
import org.example.project.domain.transaction.AddTransactionUseCase
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.domain.transaction.TransactionFormEvent
import org.example.project.domain.transaction.TransactionFormReducer
import org.example.project.domain.transaction.TransactionFormState
import org.example.project.model.PaymentMode
import org.example.project.model.Transaction
import org.example.project.model.TransactionCategory
import org.example.project.util.DateUtils

/**
 * Owns the add-transaction form. Unidirectional flow: the UI dispatches [TransactionFormEvent]s,
 * the pure [TransactionFormReducer] folds them into [formState], and one-time outcomes
 * (success / error / clear) are emitted as [TransactionFormEffect]s for the UI to react to.
 */
class TransactionViewModel(
    private val addTransactionUseCase: AddTransactionUseCase = AddTransactionUseCase(),
) : ViewModel() {

    private val _formState = MutableStateFlow(createInitialFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    private val _effects = MutableSharedFlow<TransactionFormEffect>()
    val effects: SharedFlow<TransactionFormEffect> = _effects.asSharedFlow()

    fun onEvent(event: TransactionFormEvent) {
        when (event) {
            TransactionFormEvent.FormSubmitted -> addTransaction()
            else -> _formState.update { TransactionFormReducer.reduce(it, event) }
        }
    }

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
                    // SECURITY: removed println that logged urlUsed (may contain OAuth redirect
                    // tokens from Apps Script) and responseBody (partial financial data) to stdout.
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
        _formState.value = createInitialFormState()
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
