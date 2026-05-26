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
import org.example.project.domain.transaction.AddTransactionUseCase
import org.example.project.domain.transaction.TransactionFormEvent
import org.example.project.domain.transaction.TransactionFormReducer
import org.example.project.domain.transaction.TransactionFormEffect
import org.example.project.domain.transaction.TransactionFormState
import org.example.project.model.PaymentMode
import org.example.project.model.Transaction
import org.example.project.model.TransactionCategory
import org.example.project.repository.TransactionRepository
import org.example.project.ui.state.TransactionUiState
import org.example.project.util.DateUtils

class TransactionViewModel(
    private val repository: TransactionRepository = TransactionRepository(),
    private val addTransactionUseCase: AddTransactionUseCase = AddTransactionUseCase()
) : ViewModel() {

    // Form State — StateFlow for immutable state management
    private val _formState = MutableStateFlow(createInitialFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    // Effects — one-time events for success/error messages
    private val _effects = MutableSharedFlow<TransactionFormEffect>()
    val effects: SharedFlow<TransactionFormEffect> = _effects.asSharedFlow()

    // UI State for transactions list and loading
    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    // Dispatch events to reduce state
    fun onEvent(event: TransactionFormEvent) {
        when (event) {
            TransactionFormEvent.FormSubmitted -> addTransaction()
            else -> _formState.update { state ->
                TransactionFormReducer.reduce(state, event)
            }
        }
    }

    // TODO: rebuild the transaction read path. getFromDataDump() was removed for a
    // fresh start, so the list loads empty until a new data source is wired in.
    fun loadTransactions() {
        _uiState.update { it.copy(transactions = emptyList(), isLoading = false) }
    }

    fun testConnection(): String {
        var result = ""
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                result = repository.testScriptConnection()
            } catch (e: Exception) {
                result = "Connection failed: ${e.message}"
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        return result
    }

    // Private: Business logic for adding transaction
    private fun addTransaction() {
        println("🎯 [ViewModel.addTransaction] Triggered")
        val currentState = _formState.value
        println("🎯 [ViewModel.addTransaction] Current form state: $currentState")

        // Validate form
        val validationError = validateForm(currentState)
        if (validationError != null) {
            println("⚠️ [ViewModel.addTransaction] Validation failed: $validationError")
            viewModelScope.launch {
                _effects.emit(TransactionFormEffect.ShowError(validationError))
            }
            return
        }
        println("✓ [ViewModel.addTransaction] Validation passed")

        // Update state to loading
        _formState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val transaction = buildTransaction(currentState)
                println("📦 [ViewModel.addTransaction] Built transaction: $transaction")
                println("🌐 [ViewModel.addTransaction] Calling AddTransactionUseCase (detailed)...")

                val result = addTransactionUseCase.invokeDetailed(transaction)

                // Log full diagnostic from main thread — guaranteed to appear in Logcat
                println("═══════════════════════════════════════════════════")
                println("📡 [ViewModel.addTransaction] Repository result:")
                println("   success       = ${result.success}")
                println("   httpStatus    = ${result.httpStatus}")
                println("   urlUsed       = ${result.urlUsed}")
                println("   exceptionType = ${result.exceptionType}")
                println("   errorMessage  = ${result.errorMessage}")
                println("   responseBody  = ${result.responseBody}")
                println("═══════════════════════════════════════════════════")

                if (result.success) {
                    println("✅ [ViewModel.addTransaction] SUCCESS")
                    _effects.emit(TransactionFormEffect.ShowSuccess("Transaction saved successfully!"))
                    _formState.value = createInitialFormState()
                    _effects.emit(TransactionFormEffect.FormCleared)
                } else {
                    println("❌ [ViewModel.addTransaction] FAILED — ${result.errorMessage}")
                    val userMessage = result.errorMessage ?: "Failed to save transaction."
                    _effects.emit(TransactionFormEffect.ShowError(userMessage))
                    _formState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                println("💥 [ViewModel.addTransaction] Exception: ${e::class.simpleName} — ${e.message}")
                e.printStackTrace()
                _effects.emit(TransactionFormEffect.ShowError("Error: ${e.message ?: "Unknown error occurred"}"))
                _formState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun validateForm(state: TransactionFormState): String? {
        return when {
            state.amount.isEmpty() -> "Please enter an amount"
            state.amount.toDoubleOrNull() == null -> "Invalid amount format"
            state.amount.toDouble() <= 0 -> "Amount must be greater than 0"
            state.description.isBlank() -> "Please enter a description"
            state.selectedDate.isEmpty() -> "Please select a date"
            else -> null
        }
    }

    private fun buildTransaction(state: TransactionFormState): Transaction {
        val amountValue = state.amount.toDouble()
        return Transaction(
            date = state.selectedDate,
            description = state.description.trim(),
            inflow = if (state.isIncome) amountValue else 0.0,
            outflow = if (!state.isIncome) amountValue else 0.0,
            category = state.selectedCategory,
            modeOfPayment = state.selectedPaymentMode,
            isPaid = state.isPaid
        )
    }

    private fun createInitialFormState() = TransactionFormState(
        selectedDate = DateUtils.getCurrentDateFormatted(),
        selectedCategory = TransactionCategory.OTHER.displayName,
        selectedPaymentMode = PaymentMode.OTHER.displayName
    )
}