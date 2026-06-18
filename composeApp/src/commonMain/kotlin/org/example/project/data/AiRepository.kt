package org.example.project.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import org.example.project.config.ConfigManager
import org.example.project.data.ai.AiPrefs
import org.example.project.data.ai.AiProvider
import org.example.project.data.ai.AiProviderId
import org.example.project.data.ai.AiProviderMode
import org.example.project.data.ai.AiResult
import org.example.project.data.ai.OllamaProvider
import org.example.project.data.ai.geminiProviderOrNull
import org.example.project.model.CategorySummary
import org.example.project.util.FormatUtils

/**
 * A single chat turn in the shared (provider-agnostic) format. Roles are "system" | "user" |
 * "assistant". Kept in this package for source compatibility with existing callers.
 */
@Serializable
data class OllamaMessage(val role: String, val content: String)

// Characters that could break prompt structure or inject new instructions.
// Compiled once at class-load time; reused by every classifyCategory call.
private val promptInjectionRegex = Regex("""[`"'\\]""")

/**
 * Orchestrates the AI chat feature across providers.
 *
 * **Firebase AI Logic (Gemini) is primary**; Ollama is the fallback. The repository owns
 * budget→prompt construction so every provider receives an identical, already-built system
 * instruction. On any Gemini failure (unconfigured platform, network, quota) it transparently
 * retries through Ollama.
 */
class AiRepository(
    private val gemini: AiProvider? = geminiProviderOrNull(),
    private val ollama: AiProvider = OllamaProvider(),
) {

    /** Whether the Gemini (Firebase AI Logic) path can be used on this build + config. */
    val isGeminiAvailable: Boolean
        get() = gemini != null && ConfigManager.getConfig().isFirebaseAiConfigured

    suspend fun chat(
        userMessage: String,
        budget: List<CategorySummary> = emptyList(),
        history: List<OllamaMessage> = emptyList(),
    ): AiResult {
        val systemPrompt = buildSystemPrompt(budget)

        return when (AiPrefs.providerMode.value) {
            // Forced Gemini: attempt only Gemini; surface failure (no silent fallback).
            AiProviderMode.GEMINI -> runGemini(systemPrompt, history, userMessage)
            // Forced Ollama.
            AiProviderMode.OLLAMA -> runOllama(systemPrompt, history, userMessage)
            // Auto: Gemini primary → Ollama fallback.
            AiProviderMode.AUTO -> {
                val g = gemini
                if (g != null && ConfigManager.getConfig().isFirebaseAiConfigured) {
                    try {
                        val reply = g.chat(systemPrompt, history, userMessage)
                        if (reply.text.isNotBlank()) return reply
                        // Gemini returned blank — fall through to Ollama silently
                    } catch (e: Exception) {
                        // Gemini failed — fall through to Ollama silently
                    }
                }
                runOllama(systemPrompt, history, userMessage)
            }
        }
    }

    private suspend fun runGemini(
        systemPrompt: String,
        history: List<OllamaMessage>,
        userMessage: String,
    ): AiResult {
        val model = ConfigManager.getConfig().geminiModel
        if (!isGeminiAvailable) {
            return AiResult(
                text = "Firebase AI isn't available on this build/config.",
                provider = AiProviderId.GEMINI,
                model = model,
                isError = true,
            )
        }
        return try {
            val reply = gemini!!.chat(systemPrompt, history, userMessage)
            if (reply.text.isBlank()) reply.copy(text = "Firebase AI returned an empty response.", isError = true)
            else reply
        } catch (e: Exception) {
            // Do not forward e.message to the UI — it can contain internal URLs, query
            // parameters (including API keys), or stack-frame paths on some runtimes.
            AiResult(
                text = "Firebase AI encountered an error. Please try again.",
                provider = AiProviderId.GEMINI,
                model = model,
                isError = true,
            )
        }
    }

    private suspend fun runOllama(
        systemPrompt: String,
        history: List<OllamaMessage>,
        userMessage: String,
    ): AiResult {
        return try {
            val reply = ollama.chat(systemPrompt, history, userMessage)
            if (reply.text.isBlank()) reply.copy(text = "No response received.", isError = true) else reply
        } catch (e: Exception) {
            // Do not forward e.message to the UI — it can contain the configured Ollama host
            // URL or other internal details that should not be surfaced to end users.
            AiResult(
                text = "Could not reach the AI service. Please check your connection and try again.",
                provider = AiProviderId.OLLAMA,
                model = ConfigManager.getConfig().ollamaModel,
                isError = true,
            )
        }
    }

    /**
     * Picks the single best category for a spoken/free-text expense when the local parser couldn't.
     *
     * Reuses the same provider chain as [chat] (Gemini primary → Ollama fallback) but constrains the
     * model to answer with exactly one of [options]. Returns null if AI is unreachable or replies with
     * something outside the list, so the caller can keep its existing/default category. Never throws.
     */
    suspend fun classifyCategory(spokenText: String, options: List<String>): VoiceCategoryResult {
        if (options.isEmpty() || spokenText.isBlank()) return VoiceCategoryResult(null, null)

        val list = options.joinToString(", ") { it.replace(promptInjectionRegex, " ") }
        // Strip characters that could break prompt structure or inject new instructions
        // (quotes/backslashes that could escape the wrapping quotes, and newlines/control
        // characters that could fake a new instruction line in the multi-line prompt template).
        // The 200-char cap is already enforced in TransactionFormReducer; this is a defence-in-depth
        // sanitisation pass before the text crosses the trust boundary into the model prompt.
        val safeText = spokenText
            .replace(promptInjectionRegex, " ")
            .replace(Regex("[\r\n\t\u0000-\u001f]"), " ")
            .take(200)
        val prompt = """
            You are categorising a personal expense. Choose the SINGLE best category for this
            transaction described in natural language: "$safeText".
            Allowed categories: $list.
            Reply with ONLY the exact category name from the list — no punctuation, no explanation.
        """.trimIndent()

        return try {
            val reply = chat(userMessage = prompt)
            // Hand the raw result back too so the caller can read the token cost of this round-trip.
            if (reply.isError) VoiceCategoryResult(null, reply)
            else VoiceCategoryResult(normalizeToOption(reply.text, options), reply)
        } catch (e: Exception) {
            VoiceCategoryResult(null, null)
        }
    }

    /** Map a noisy model reply back onto one of [options] (exact, then contains, both case-insensitive). */
    private fun normalizeToOption(reply: String, options: List<String>): String? {
        val cleaned = reply.trim().trim('"', '.', '\'', '`').lowercase()
        if (cleaned.isEmpty()) return null
        options.firstOrNull { it.lowercase() == cleaned }?.let { return it }
        return options.firstOrNull { cleaned.contains(it.lowercase()) }
    }

    private fun buildSystemPrompt(budget: List<CategorySummary>): String {
        if (budget.isEmpty()) {
            return """
                You are a personal finance assistant. No budget data is loaded yet —
                answer general finance questions based on your training.
                Format all amounts as PHP (e.g. PHP 1,500 — never use the ₱ symbol).
            """.trimIndent()
        }

        val currentMonth = currentMonthName()

        // Per-category view for THIS month: budget, spent, remaining.
        // This is what powers questions like "can we still spend more on food?".
        val thisMonthTable = budget.joinToString("\n") { c ->
            val spent = c.spentIn(currentMonth)
            val remaining = c.remainingIn(currentMonth)
            val status = when {
                c.monthlyBudget <= 0.0 -> "no budget set"
                remaining < 0 -> "OVER by PHP ${FormatUtils.formatPeso(-remaining)}"
                else -> "PHP ${FormatUtils.formatPeso(remaining)} left"
            }
            "  ${c.category}: budget PHP ${FormatUtils.formatPeso(c.monthlyBudget)}/mo | " +
                "spent PHP ${FormatUtils.formatPeso(spent)} | $status"
        }

        // Year-to-date totals give the model trend context across months.
        val ytdTable = budget.joinToString("\n") { c ->
            "  ${c.category}: PHP ${FormatUtils.formatPeso(c.totalSpent)} spent year-to-date"
        }

        return """
            You are a personal finance assistant for a monthly household budget.
            Use the data below to answer accurately. Format all amounts as PHP (e.g. PHP 1,500 — never use the ₱ symbol).
            The current month is $currentMonth.

            When asked whether there is room to spend more in a category, compare that
            category's monthly budget against what has already been spent THIS month
            ($currentMonth) and report the remaining amount. If already over budget, say so.

            === $currentMonth — BUDGET vs SPENT (per category) ===
            Format: category: monthly budget | spent this month | status
            $thisMonthTable

            === YEAR-TO-DATE SPEND (per category) ===
            $ytdTable
        """.trimIndent()
    }

    /** Full English month name matching the sheet headers (e.g. "May"). */
    @OptIn(ExperimentalTime::class)
    private fun currentMonthName(): String {
        val month = kotlin.time.Clock.System
            .todayIn(TimeZone.currentSystemDefault())
            .month.name
        return month.lowercase().replaceFirstChar { it.uppercase() }
    }
}

/**
 * Outcome of a voice-category classification. [category] is the matched option (or null when the
 * model was unreachable or replied off-list); [result] is the raw AI turn so the caller can read
 * the token cost. [result] is null only when no call happened or it threw before producing a turn.
 */
data class VoiceCategoryResult(
    val category: String?,
    val result: AiResult?,
)
