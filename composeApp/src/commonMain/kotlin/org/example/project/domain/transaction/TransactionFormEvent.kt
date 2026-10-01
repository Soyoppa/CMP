package org.example.project.domain.transaction

import org.example.project.model.OptionList
import org.example.project.voice.VoiceStatus

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

    /** A new option typed into a picker: saved to the user's list and selected. Handled in the ViewModel. */
    data class OptionCreated(val list: OptionList, val name: String) : TransactionFormEvent

    /** ViewModel-internal: the user's lists changed (loaded, or edited on another screen). */
    data class OptionsLoaded(
        val expenseCategories: List<String>,
        val incomeCategories: List<String>,
        val paymentModes: List<String>,
    ) : TransactionFormEvent

    /** Mic tapped — start listening if idle, stop if already listening. Side effect; handled in the ViewModel. */
    data object VoiceInputToggled : TransactionFormEvent

    /** Pull-to-clear gesture — reset the whole form to defaults. Side effect; handled in the ViewModel. */
    data object ClearForm : TransactionFormEvent

    /** ViewModel-internal: mic lifecycle status for the button UI. */
    data class VoiceStatusChanged(val status: VoiceStatus) : TransactionFormEvent

    /** ViewModel-internal: parsed voice transcript folded into the form fields. */
    data class VoiceResultApplied(
        val amount: String?,
        val description: String,
        val category: String?,
        val isIncome: Boolean?,
    ) : TransactionFormEvent

    /** ViewModel-internal: token cost of the voice add, for the per-add usage readout. */
    data class VoiceAiUsageReported(val usage: VoiceAiUsage) : TransactionFormEvent
}
