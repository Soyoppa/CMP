package org.example.project.domain.transaction

sealed interface TransactionFormEvent {
    data class AmountChanged(val amount: String) : TransactionFormEvent
    data class DescriptionChanged(val description: String) : TransactionFormEvent
    data class DateChanged(val date: String) : TransactionFormEvent
    data class CategorySelected(val category: String) : TransactionFormEvent
    data class PaymentModeSelected(val paymentMode: String) : TransactionFormEvent
    data class TransactionTypeChanged(val isIncome: Boolean) : TransactionFormEvent
    data class IsPaidChanged(val isPaid: Boolean) : TransactionFormEvent
    data object CategoryDropdownToggled : TransactionFormEvent
    data object PaymentDropdownToggled : TransactionFormEvent
    data object FormSubmitted : TransactionFormEvent
}
