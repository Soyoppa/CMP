package org.example.project.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.example.project.model.Transaction
import org.example.project.model.TransactionCategory
import org.example.project.model.PaymentMode
import org.example.project.repository.TransactionRepository
import org.example.project.ui.state.TransactionUiState
import org.example.project.ui.state.TransactionFormState
import org.example.project.util.DateUtils

class TransactionViewModel : ViewModel() {
    private val repository = TransactionRepository()
    
    // UI State
    var uiState by mutableStateOf(TransactionUiState())
        private set
    
    var formState by mutableStateOf(
        TransactionFormState(
            selectedDate = DateUtils.getCurrentDateFormatted(),
            selectedCategory = TransactionCategory.OTHER.displayName,
            selectedPaymentMode = PaymentMode.OTHER.displayName
        )
    )
        private set
    
    // Form actions
    fun updateDescription(description: String) {
        formState = formState.copy(description = description)
    }
    
    fun updateAmount(amount: String) {
        formState = formState.copy(amount = amount)
    }
    
    fun updateIsIncome(isIncome: Boolean) {
        formState = formState.copy(isIncome = isIncome)
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
    
    fun updateDate(date: String) {
        formState = formState.copy(selectedDate = date)
    }
    
    fun updateIsPaid(isPaid: Boolean) {
        formState = formState.copy(isPaid = isPaid)
    }
    
    fun toggleCategoryDropdown() {
        formState = formState.copy(showCategoryDropdown = !formState.showCategoryDropdown)
    }
    
    fun togglePaymentDropdown() {
        formState = formState.copy(showPaymentDropdown = !formState.showPaymentDropdown)
    }
    
    fun clearForm() {
        formState = TransactionFormState(
            selectedDate = DateUtils.getCurrentDateFormatted(),
            selectedCategory = TransactionCategory.OTHER.displayName,
            selectedPaymentMode = PaymentMode.OTHER.displayName
        )
    }
    
    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }
    
    fun clearSuccess() {
        uiState = uiState.copy(showSuccessMessage = false, successMessage = null)
    }
    
    // Business logic
    fun loadTransactions() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            try {
                val transactions = repository.getAllTransactions()
                uiState = uiState.copy(
                    transactions = transactions,
                    isLoading = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to load transactions: ${e.message}"
                )
            }
        }
    }
    
    fun addTransaction() {
        if (!formState.isValid) {
            uiState = uiState.copy(errorMessage = "Please fill in all required fields")
            return
        }
        
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, errorMessage = null)
            try {
                val amountValue = formState.amount.toDouble()
                val date = formState.selectedDate
                
                if (date == null) {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Invalid date format. Use M/d/yyyy (e.g., 3/1/2026)"
                    )
                    return@launch
                }
                
                val transaction = Transaction(
                    date = date.toString(),
                    description = formState.description,
                    inflow = if (formState.isIncome) amountValue else 0.0,
                    outflow = if (!formState.isIncome) amountValue else 0.0,
                    category = formState.selectedCategory,
                    modeOfPayment = formState.selectedPaymentMode,
                    isPaid = formState.isPaid
                )
                
                val success = repository.addTransaction(transaction)
                if (success) {
                    uiState = uiState.copy(
                        isLoading = false,
                        showSuccessMessage = true,
                        successMessage = "Transaction saved to Google Sheets!"
                    )
                    clearForm()
                    // Optionally refresh the list
                    loadTransactions()
                } else {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = "Failed to add transaction. Please try again."
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = "Failed to add transaction: ${e.message}"
                )
            }
        }
    }
    
    suspend fun testConnection(): String {
        return try {
            repository.testScriptConnection()
        } catch (e: Exception) {
            "Connection test failed: ${e.message}"
        }
    }
}