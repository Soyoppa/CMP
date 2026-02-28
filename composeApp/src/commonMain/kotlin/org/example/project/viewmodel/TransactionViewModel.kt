package org.example.project.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.example.project.model.PaymentMode
import org.example.project.model.Transaction
import org.example.project.model.TransactionCategory
import org.example.project.repository.TransactionRepository
import org.example.project.ui.state.TransactionFormState
import org.example.project.ui.state.TransactionUiState
import org.example.project.util.DateUtils

class TransactionViewModel(
    private val repository: TransactionRepository = TransactionRepository()
) : ViewModel() {

    // UI State as StateFlow for better compose integration
    private val _uiState = MutableStateFlow(TransactionUiState())
    val uiState: StateFlow<TransactionUiState> = _uiState.asStateFlow()

    // Form State - kept as mutableStateOf for immediate UI updates
    var formState by mutableStateOf(createInitialFormState())
        private set

    init {
        // Optionally load transactions on init
        // loadTransactions()
    }

    // ========================================
    // Form State Updates - Grouped by functionality
    // ========================================

    fun updateAmount(amount: String) {
        // Only allow valid decimal input
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            formState = formState.copy(amount = amount)
        }
    }

    fun updateDescription(description: String) {
        formState = formState.copy(description = description)
    }

    fun updateDate(date: String) {
        formState = formState.copy(selectedDate = date)
    }

    fun updateCategory(category: String) {
        formState = formState.copy(
            selectedCategory = category,
            showCategoryDropdown = false
        )
    }

    fun updatePaymentMode(paymentMode: String) {
        formState = formState.copy(
            selectedPaymentMode = paymentMode,
            showPaymentDropdown = false
        )
    }

    fun updateIsIncome(isIncome: Boolean) {
        formState = formState.copy(isIncome = isIncome)
    }

    fun updateIsPaid(isPaid: Boolean) {
        formState = formState.copy(isPaid = isPaid)
    }

    fun toggleCategoryDropdown() {
        formState = formState.copy(
            showCategoryDropdown = !formState.showCategoryDropdown,
            showPaymentDropdown = false // Close other dropdown
        )
    }

    fun togglePaymentDropdown() {
        formState = formState.copy(
            showPaymentDropdown = !formState.showPaymentDropdown,
            showCategoryDropdown = false // Close other dropdown
        )
    }

    // ========================================
    // UI State Management
    // ========================================

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update {
            it.copy(showSuccessMessage = false, successMessage = null)
        }
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message
            )
        }
    }

    private fun showSuccess(message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                showSuccessMessage = true,
                successMessage = message
            )
        }
    }

    private fun setLoading(isLoading: Boolean) {
        _uiState.update { it.copy(isLoading = isLoading) }
    }

    // ========================================
    // Business Logic
    // ========================================

    fun addTransaction() {
        // Validate form
        val validationError = validateForm()
        if (validationError != null) {
            showError(validationError)
            return
        }

        viewModelScope.launch {
            setLoading(true)
            clearError()

            try {
                val transaction = buildTransaction()
                val success = repository.addTransaction(transaction)

                if (success) {
                    showSuccess("Transaction saved successfully!")
                    clearForm()
                    // Optional: reload transactions
                    // loadTransactions()
                } else {
                    showError("Failed to save transaction. Please try again.")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message ?: "Unknown error occurred"}")
            }
        }
    }

    fun loadTransactions() {
        viewModelScope.launch {
            setLoading(true)
            clearError()

            try {
                val transactions = repository.getAllTransactions()
                _uiState.update {
                    it.copy(
                        transactions = transactions,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                showError("Failed to load transactions: ${e.message}")
            }
        }
    }

    fun testConnection(): String {
        var result = ""
        viewModelScope.launch {
            setLoading(true)
            try {
                result = repository.testScriptConnection()
            } catch (e: Exception) {
                result = "Connection failed: ${e.message}"
            } finally {
                setLoading(false)
            }
        }
        return result
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    private fun validateForm(): String? {
        return when {
            formState.amount.isEmpty() -> "Please enter an amount"
            formState.amount.toDoubleOrNull() == null -> "Invalid amount format"
            formState.amount.toDouble() <= 0 -> "Amount must be greater than 0"
            formState.description.isBlank() -> "Please enter a description"
            formState.selectedDate.isEmpty() -> "Please select a date"
            else -> null
        }
    }

    private fun buildTransaction(): Transaction {
        val amountValue = formState.amount.toDouble()
        return Transaction(
            date = formState.selectedDate,
            description = formState.description.trim(),
            inflow = if (formState.isIncome) amountValue else 0.0,
            outflow = if (!formState.isIncome) amountValue else 0.0,
            category = formState.selectedCategory,
            modeOfPayment = formState.selectedPaymentMode,
            isPaid = formState.isPaid
        )
    }

    private fun clearForm() {
        formState = createInitialFormState()
    }

    private fun createInitialFormState() = TransactionFormState(
        selectedDate = DateUtils.getCurrentDateFormatted(),
        selectedCategory = TransactionCategory.OTHER.displayName,
        selectedPaymentMode = PaymentMode.OTHER.displayName
    )
}