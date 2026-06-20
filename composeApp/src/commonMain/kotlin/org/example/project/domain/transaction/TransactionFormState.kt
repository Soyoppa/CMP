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
    /**
     * Token cost of the most recent voice add. Null until a voice transcript is processed.
     * Tracked separately from the chat-screen [AiUsageTracker] on purpose — this is a local,
     * per-add readout so you can see whether the mic spent a Gemini round-trip.
     */
    val voiceAiUsage: VoiceAiUsage? = null,
) {
    val isValid: Boolean
        get() {
            val parsed = amount.toDoubleOrNull()
            return parsed != null &&
                    parsed > 0 &&
                    description.isNotBlank() &&
                    selectedDate.isNotBlank()
        }
}

/**
 * Token usage from a single voice add.
 *
 * [aiInvoked] is false when the local parser matched the category on its own — no AI round-trip,
 * zero tokens. When true, [provider]/[model] and the token counts describe the Gemini (or Ollama
 * fallback) call that classified the category.
 */
@Immutable
data class VoiceAiUsage(
    val aiInvoked: Boolean,
    val provider: String? = null,
    val model: String? = null,
    val promptTokens: Int = 0,
    val responseTokens: Int = 0,
) {
    val totalTokens: Int get() = promptTokens + responseTokens
}
