package org.example.project.data.ai

/** Which backend produced a reply. */
enum class AiProviderId(val displayName: String) {
    GEMINI("Gemini"),
}

/**
 * Outcome of one chat turn. Token counts are best-effort: Gemini reports them via `usageMetadata`,
 * Ollama via `prompt_eval_count`/`eval_count`; either may be 0 when unavailable.
 */
data class AiResult(
    val text: String,
    val provider: AiProviderId,
    val model: String,
    val promptTokens: Int = 0,
    val responseTokens: Int = 0,
    /** True when [text] is a failure message rather than a model reply (not counted in usage). */
    val isError: Boolean = false,
) {
    val totalTokens: Int get() = promptTokens + responseTokens
}
