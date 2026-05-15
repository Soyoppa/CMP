package org.example.project.domain.transaction

object TransactionFormReducer {
    fun reduce(state: TransactionFormState, event: TransactionFormEvent): TransactionFormState =
        when (event) {
            is TransactionFormEvent.AmountChanged -> {
                // Only allow valid decimal input
                if (event.amount.isEmpty() || event.amount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    state.copy(amount = event.amount)
                } else {
                    state
                }
            }
            is TransactionFormEvent.DescriptionChanged ->
                state.copy(description = event.description)
            is TransactionFormEvent.DateChanged ->
                state.copy(selectedDate = event.date)
            is TransactionFormEvent.CategorySelected ->
                state.copy(
                    selectedCategory = event.category,
                    showCategoryDropdown = false
                )
            is TransactionFormEvent.PaymentModeSelected ->
                state.copy(
                    selectedPaymentMode = event.paymentMode,
                    showPaymentDropdown = false
                )
            is TransactionFormEvent.TransactionTypeChanged ->
                state.copy(isIncome = event.isIncome)
            is TransactionFormEvent.IsPaidChanged ->
                state.copy(isPaid = event.isPaid)
            TransactionFormEvent.CategoryDropdownToggled ->
                state.copy(
                    showCategoryDropdown = !state.showCategoryDropdown,
                    showPaymentDropdown = false
                )
            TransactionFormEvent.PaymentDropdownToggled ->
                state.copy(
                    showPaymentDropdown = !state.showPaymentDropdown,
                    showCategoryDropdown = false
                )
            TransactionFormEvent.FormSubmitted ->
                state.copy(isLoading = true, errorMessage = null)
        }
}
