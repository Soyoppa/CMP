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
                        println("🤖 [AI] Auto: trying ${g.name}")
                        val reply = g.chat(systemPrompt, history, userMessage)
                        if (reply.text.isNotBlank()) return reply
                        println("⚠️ [AI] ${g.name} returned blank — falling back to ${ollama.name}")
                    } catch (e: Exception) {
                        println("⚠️ [AI] ${g.name} failed (${e::class.simpleName}: ${e.message}) — falling back to ${ollama.name}")
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
            println("💥 [AI] Gemini (forced) failed: ${e::class.simpleName}: ${e.message}")
            AiResult(
                text = "Firebase AI error: ${e.message}",
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
            println("💥 [AI] ${ollama.name} failed: ${e::class.simpleName}: ${e.message}")
            AiResult(
                text = "Could not reach Ollama: ${e.message}",
                provider = AiProviderId.OLLAMA,
                model = ConfigManager.getConfig().ollamaModel,
                isError = true,
            )
        }
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
