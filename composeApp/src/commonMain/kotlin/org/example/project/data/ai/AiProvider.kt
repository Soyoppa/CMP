package org.example.project.data.ai

import org.example.project.data.OllamaMessage

/**
 * A single AI backend the chat feature can talk to.
 *
 * Implementations are intentionally "dumb": the orchestrator ([org.example.project.data.AiRepository])
 * owns budget→prompt construction and passes a ready-made [systemPrompt]. A provider only forwards
 * the conversation to its backend and returns the assistant text.
 */
interface AiProvider {

    /** Stable identity for telemetry/usage attribution. */
    val id: AiProviderId

    /** Human-readable name for diagnostics/logging. */
    val name: String

    /**
     * @param systemPrompt budget-contextualised system instruction (already built).
     * @param history prior turns in chronological order (roles: "user"/"assistant"). System
     *                turns, if any, are ignored — the system instruction is passed separately.
     * @param userMessage the new user turn.
     * @return an [AiResult] with the reply text + token usage. Implementations should throw on
     *         transport/backend failure so the orchestrator can fall back; a blank [AiResult.text]
     *         is treated as "no answer".
     */
    suspend fun chat(
        systemPrompt: String,
        history: List<OllamaMessage>,
        userMessage: String,
    ): AiResult
}
