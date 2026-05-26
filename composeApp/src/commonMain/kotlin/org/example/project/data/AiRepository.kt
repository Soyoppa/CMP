package org.example.project.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import org.example.project.config.ConfigManager
import org.example.project.model.BudgetCategory
import org.example.project.util.FormatUtils

@Serializable
data class OllamaMessage(val role: String, val content: String)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false
)

@Serializable
data class OllamaChatResponse(
    val message: OllamaMessage? = null,
    val done: Boolean = false,
    val error: String? = null
)

class AiRepository {

    // No ContentNegotiation — we handle raw string parsing manually
    // because Ollama returns application/x-ndjson regardless of stream:false
    private val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
        }
    }

    suspend fun chat(
        userMessage: String,
        budget: List<BudgetCategory> = emptyList(),
        history: List<OllamaMessage> = emptyList()
    ): String {
        val baseUrl = ConfigManager.getConfig().ollamaUrl
        val model = ConfigManager.getConfig().ollamaModel
        val systemPrompt = buildSystemPrompt(budget)

        val messages = buildList {
            add(OllamaMessage("system", systemPrompt))
            addAll(history.takeLast(10))
            add(OllamaMessage("user", userMessage))
        }

        return try {
            println("🤖 [AI] POST $baseUrl/api/chat | model=$model | msgs=${messages.size}")

            // Read as ByteArray to force full response buffering on JS/Wasm engine
            val rawBytes = client.post("$baseUrl/api/chat") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(
                    OllamaChatRequest.serializer(),
                    OllamaChatRequest(model = model, messages = messages, stream = false)
                ))
            }.body<ByteArray>()

            val rawResponse = rawBytes.decodeToString()
            println("🤖 [AI] Response length: ${rawResponse.length}")

            val json = Json { ignoreUnknownKeys = true; isLenient = true }

            // Ollama returns ndjson — each line is one token chunk
            // Concatenate all content fields (skip done:true line which has empty content)
            val fullContent = rawResponse
                .lines()
                .mapNotNull { runCatching { json.decodeFromString<OllamaChatResponse>(it) }.getOrNull() }
                .mapNotNull { it.message?.content }
                .filter { it.isNotEmpty() }
                .joinToString("")

            println("🤖 [AI] Full content (${fullContent.length} chars): ${fullContent.take(200)}")

            fullContent.ifBlank { "No response received." }
        } catch (e: Exception) {
            println("💥 [AI] ${e::class.simpleName}: ${e.message}")
            "Could not reach AI server: ${e.message}"
        }
    }

    private fun buildSystemPrompt(budget: List<BudgetCategory>): String {
        if (budget.isEmpty()) {
            return """
                You are a personal finance assistant. No budget data is loaded yet —
                answer general finance questions based on your training.
                Format all amounts in Philippine Peso (₱).
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
                remaining < 0 -> "OVER by ₱${FormatUtils.formatPeso(-remaining)}"
                else -> "₱${FormatUtils.formatPeso(remaining)} left"
            }
            "  ${c.category}: budget ₱${FormatUtils.formatPeso(c.monthlyBudget)}/mo | " +
                "spent ₱${FormatUtils.formatPeso(spent)} | $status"
        }

        // Year-to-date totals give the model trend context across months.
        val ytdTable = budget.joinToString("\n") { c ->
            "  ${c.category}: ₱${FormatUtils.formatPeso(c.totalSpent)} spent year-to-date"
        }

        return """
            You are a personal finance assistant for a monthly household budget.
            Use the data below to answer accurately. Format all amounts in Philippine Peso (₱).
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
