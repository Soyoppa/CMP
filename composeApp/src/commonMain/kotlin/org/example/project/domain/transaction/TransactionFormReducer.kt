package org.example.project.domain.transaction

object TransactionFormReducer {
    // Validates "optional digits, optional decimal point, up to 2 decimal digits".
    // Hoisted so the Regex is compiled once rather than on every AmountChanged/VoiceResultApplied
    // event (i.e. on every keystroke while typing an amount).
    private val decimalAmountRegex = Regex("^\\d*\\.?\\d{0,2}$")

    fun reduce(state: TransactionFormState, event: TransactionFormEvent): TransactionFormState =
        when (event) {
            is TransactionFormEvent.AmountChanged -> {
                // Only allow valid decimal input
                if (event.amount.isEmpty() || event.amount.matches(decimalAmountRegex)) {
                    state.copy(amount = event.amount)
                } else {
                    state
                }
            }
            is TransactionFormEvent.DescriptionChanged ->
                // Cap at 200 chars — prevents unbounded heap growth and limits the token
                // surface area if the description is later injected into an AI prompt.
                state.copy(description = event.description.take(200))
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

            // Side effects owned by the ViewModel; no state change here.
            TransactionFormEvent.VoiceInputToggled -> state
            TransactionFormEvent.ClearForm -> state

            is TransactionFormEvent.VoiceStatusChanged ->
                state.copy(voiceStatus = event.status)

            is TransactionFormEvent.VoiceResultApplied -> {
                // Only overwrite a field when voice produced a confident value; otherwise keep what's there.
                val validAmount = event.amount?.takeIf {
                    it.isNotEmpty() && it.matches(decimalAmountRegex)
                }
                // Apply the same 200-char cap as manual entry so voice-derived text
                // cannot produce an unbounded description (or AI-prompt injection surface).
                val safeDescription = event.description.take(200).ifBlank { state.description }
                state.copy(
                    amount = validAmount ?: state.amount,
                    description = safeDescription,
                    isIncome = event.isIncome ?: state.isIncome,
                    selectedCategory = event.category ?: state.selectedCategory,
                )
            }

            is TransactionFormEvent.VoiceAiUsageReported ->
                state.copy(voiceAiUsage = event.usage)
        }
}
