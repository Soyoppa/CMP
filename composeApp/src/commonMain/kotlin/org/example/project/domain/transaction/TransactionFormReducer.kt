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
                // Income and expense have different category lists; reset to "Other"
                // (valid in both) so a stale selection can't leak across types.
                state.copy(isIncome = event.isIncome, selectedCategory = "Other")
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

            // Side effect (start/stop mic) — owned by the ViewModel; no state change here.
            TransactionFormEvent.VoiceInputToggled -> state

            is TransactionFormEvent.VoiceStatusChanged ->
                state.copy(voiceStatus = event.status)

            is TransactionFormEvent.VoiceResultApplied -> {
                // Only overwrite a field when voice produced a confident value; otherwise keep what's there.
                val validAmount = event.amount?.takeIf {
                    it.isNotEmpty() && it.matches(Regex("^\\d*\\.?\\d{0,2}$"))
                }
                state.copy(
                    amount = validAmount ?: state.amount,
                    description = event.description.ifBlank { state.description },
                    isIncome = event.isIncome ?: state.isIncome,
                    selectedCategory = event.category ?: state.selectedCategory,
                )
            }
        }
}
