package org.example.project.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.model.AiSummaryRecord
import org.example.project.model.CATEGORY_GROUP_MAP
import org.example.project.model.Transaction
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
        transactions: List<Transaction>,
        summaryRecords: List<AiSummaryRecord> = emptyList(),
        history: List<OllamaMessage> = emptyList()
    ): String {
        val baseUrl = ConfigManager.getConfig().ollamaUrl
        val model = ConfigManager.getConfig().ollamaModel
        val systemPrompt = buildSystemPrompt(transactions, summaryRecords)

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

    private fun buildSystemPrompt(
        transactions: List<Transaction>,
        summaryRecords: List<AiSummaryRecord> = emptyList()
    ): String {
        if (transactions.isEmpty() && summaryRecords.isEmpty()) {
            return """
                You are a personal finance assistant for a Filipino user.
                No transaction data is loaded yet. Answer general finance questions.
                Format amounts in Philippine Peso (₱).
            """.trimIndent()
        }

        val totalInflow = transactions.sumOf { it.inflow }
        val totalOutflow = transactions.sumOf { it.outflow }
        val net = totalInflow - totalOutflow

        val byCategory = transactions
            .filter { it.outflow > 0 }
            .groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.outflow } }
            .entries.sortedByDescending { it.value }
            .take(10)
            .joinToString("\n") { (cat, amt) -> "  - $cat: ${FormatUtils.formatPeso(amt)}" }

        val recentRows = transactions.takeLast(20).joinToString("\n") { t ->
            val amount = if (t.inflow > 0) "+${FormatUtils.formatPeso(t.inflow)}" else "-${FormatUtils.formatPeso(t.outflow)}"
            "  ${t.date} | ${t.description} | $amount | ${t.category} | ${t.modeOfPayment}"
        }

        // Build monthly summary table from AISummaryRecords
        val summarySection = if (summaryRecords.isNotEmpty()) {
            val categoryGrouping = CATEGORY_GROUP_MAP.entries.joinToString("\n") { (parent, subs) ->
                "  $parent → ${subs.joinToString(", ")}"
            }

            val monthlyTable = summaryRecords.joinToString("\n") { record ->
                val months = record.monthlyAmounts.entries
                    .filter { it.value > 0 }
                    .joinToString(" | ") { (month, amt) -> "$month: ${FormatUtils.formatPeso(amt)}" }
                "  ${record.category}: $months  [Year Total: ${FormatUtils.formatPeso(record.yearTotal)}]"
            }

            """

            === CATEGORY GROUPS (sub-categories roll up to parent) ===
            $categoryGrouping

            === MONTHLY EXPENSE SUMMARY BY PARENT CATEGORY ===
            $monthlyTable
            """.trimIndent()
        } else ""

        return """
            You are a personal finance assistant for a Filipino user.
            Answer questions based on the transaction data below. Be concise and helpful.
            Format all amounts in Philippine Peso (₱).

            === SUMMARY ===
            Total Income:  ${FormatUtils.formatPeso(totalInflow)}
            Total Expenses: ${FormatUtils.formatPeso(totalOutflow)}
            Net Balance:   ${FormatUtils.formatPeso(net)}
            Total Transactions: ${transactions.size}

            === TOP EXPENSE CATEGORIES ===
            $byCategory

            === RECENT TRANSACTIONS (last 20) ===
            $recentRows
            $summarySection
        """.trimIndent()
    }
}
