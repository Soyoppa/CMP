package org.example.project.domain.transaction

import androidx.compose.runtime.Immutable
import org.example.project.voice.VoiceStatus

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
    val errorMessage: String? = null,
    /** Whether this platform/browser can capture voice — gates the mic button entirely. */
    val isVoiceSupported: Boolean = false,
    /** Mic button state: idle, listening, or parsing/classifying the transcript. */
    val voiceStatus: VoiceStatus = VoiceStatus.Idle,
) {
    val isValid: Boolean
        get() = amount.isNotBlank() &&
                amount.toDoubleOrNull() != null &&
                amount.toDouble() > 0 &&
                description.isNotBlank() &&
                selectedDate.isNotBlank()
}
