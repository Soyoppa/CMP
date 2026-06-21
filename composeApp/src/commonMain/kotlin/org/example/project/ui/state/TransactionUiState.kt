package org.example.project.ui.state

import androidx.compose.runtime.Stable
import org.example.project.model.Transaction

/**
 * UI State for transaction-related screens
 * Follows MVVM pattern by separating UI state from business logic
 */
@Stable
data class TransactionUiState(
    val transactions: List<Transaction> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isFormValid: Boolean = false,
    val showSuccessMessage: Boolean = false,
    val successMessage: String? = null
)

/**
 * UI State for transaction input form
 */
@Stable
data class TransactionFormState(
    val description: String = "",
    val amount: String = "",
    val isIncome: Boolean = false,
    val selectedCategory: String = "",
    val selectedPaymentMode: String = "",
    val selectedDate: String = "",
    val isPaid: Boolean = false,
    val showCategoryDropdown: Boolean = false,
    val showPaymentDropdown: Boolean = false
) {
    val isValid: Boolean
        get() = amount.toDoubleOrNull() != null

}