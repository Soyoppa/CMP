package org.example.project.domain.transaction

import androidx.compose.runtime.Immutable

@Immutable
data class TransactionFormState(
    val description: String = "",
    val amount: String = "",
    val isIncome: Boolean = false,
    val selectedCategory: String = "",
    val selectedPaymentMode: String = "",
    val selectedDate: String = "",
    val isPaid: Boolean = false,
    val showCategoryDropdown: Boolean = false,
    val showPaymentDropdown: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val isValid: Boolean
        get() = amount.isNotBlank() &&
                amount.toDoubleOrNull() != null &&
                amount.toDouble() > 0 &&
                description.isNotBlank() &&
                selectedDate.isNotBlank()
}
