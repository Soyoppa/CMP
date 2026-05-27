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
    val ollama: ProviderUsage = ProviderUsage(),
    val lastProvider: AiProviderId? = null,
    val lastModel: String? = null,
) {
    val totalRequests: Int get() = gemini.requests + ollama.requests
    val totalPromptTokens: Int get() = gemini.promptTokens + ollama.promptTokens
    val totalResponseTokens: Int get() = gemini.responseTokens + ollama.responseTokens
    val totalTokens: Int get() = totalPromptTokens + totalResponseTokens
    val isEmpty: Boolean get() = totalRequests == 0
}

/**
 * Process-wide session-usage telemetry, observed by both CHAT (provider chip) and DEBUG (usage card).
 *
 * It's a shared holder, but the flow stays unidirectional: only [record]/[reset] mutate it, and the
 * UI only reads [state]. A deliberate, contained exception to the "no shared mutable state" rule —
 * session telemetry is genuinely app-global and outlives any single screen's ViewModel.
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
            when (result.provider) {
                AiProviderId.GEMINI ->
                    s.copy(gemini = s.gemini.bump(), lastProvider = result.provider, lastModel = result.model)
                AiProviderId.OLLAMA ->
                    s.copy(ollama = s.ollama.bump(), lastProvider = result.provider, lastModel = result.model)
            }
        }
    }

    fun reset() {
        _state.value = SessionUsage()
    }
}

/**
 * Which provider the chat should use.
 * - [AUTO]: Gemini primary, Ollama fallback (default).
 * - [GEMINI]/[OLLAMA]: force that provider, no fallback — failures surface so a manual switch is honest.
 */
enum class AiProviderMode(val label: String) {
    AUTO("Auto"),
    GEMINI("Firebase AI"),
    OLLAMA("Ollama"),
}

/** Lightweight, in-memory UI preferences (not persisted across launches). */
object AiPrefs {
    private val _showPerMessageTokens = MutableStateFlow(false)
    val showPerMessageTokens: StateFlow<Boolean> = _showPerMessageTokens.asStateFlow()

    fun setShowPerMessageTokens(value: Boolean) {
        _showPerMessageTokens.value = value
    }

    private val _providerMode = MutableStateFlow(AiProviderMode.AUTO)
    val providerMode: StateFlow<AiProviderMode> = _providerMode.asStateFlow()

    fun setProviderMode(mode: AiProviderMode) {
        _providerMode.value = mode
    }
}
