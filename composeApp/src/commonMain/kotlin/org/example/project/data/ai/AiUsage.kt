package org.example.project.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Per-provider tally for the current session. */
data class ProviderUsage(
    val requests: Int = 0,
    val promptTokens: Int = 0,
    val responseTokens: Int = 0,
) {
    val totalTokens: Int get() = promptTokens + responseTokens
}

/** Aggregated session usage, surfaced in the CHAT provider chip and the DEBUG usage card. */
data class SessionUsage(
    val gemini: ProviderUsage = ProviderUsage(),
    val lastProvider: AiProviderId? = null,
    val lastModel: String? = null,
) {
    val totalRequests: Int get() = gemini.requests
    val totalPromptTokens: Int get() = gemini.promptTokens
    val totalResponseTokens: Int get() = gemini.responseTokens
    val totalTokens: Int get() = totalPromptTokens + totalResponseTokens
    val isEmpty: Boolean get() = totalRequests == 0
}

/**
 * Process-wide session-usage telemetry, observed by both CHAT (provider chip) and DEBUG (usage card).
 */
object AiUsageTracker {
    private val _state = MutableStateFlow(SessionUsage())
    val state: StateFlow<SessionUsage> = _state.asStateFlow()

    fun record(result: AiResult) {
        _state.update { s ->
            fun ProviderUsage.bump() = copy(
                requests = requests + 1,
                promptTokens = promptTokens + result.promptTokens,
                responseTokens = responseTokens + result.responseTokens,
            )
            s.copy(gemini = s.gemini.bump(), lastProvider = result.provider, lastModel = result.model)
        }
    }

    fun reset() {
        _state.value = SessionUsage()
    }
}

/** Lightweight, in-memory UI preferences (not persisted across launches). */
object AiPrefs {
    private val _showPerMessageTokens = MutableStateFlow(false)
    val showPerMessageTokens: StateFlow<Boolean> = _showPerMessageTokens.asStateFlow()

    fun setShowPerMessageTokens(value: Boolean) {
        _showPerMessageTokens.value = value
    }
}
