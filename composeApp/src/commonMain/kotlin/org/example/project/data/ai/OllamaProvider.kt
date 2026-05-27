package org.example.project.data.ai

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.data.OllamaMessage

@Serializable
private data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
)

@Serializable
private data class OllamaChatResponse(
    val message: OllamaMessage? = null,
    val done: Boolean = false,
    val error: String? = null,
    // Token counts arrive on the final (done:true) chunk.
    @SerialName("prompt_eval_count") val promptEvalCount: Int? = null,
    @SerialName("eval_count") val evalCount: Int? = null,
)

/**
 * Fallback AI provider backed by the self-hosted Ollama server.
 *
 * Unchanged behaviour from the original `AiRepository`: no `ContentNegotiation` because Ollama
 * returns `application/x-ndjson` even with `stream:false`, so we buffer the raw bytes and
 * concatenate the per-line content fields ourselves.
 */
internal class OllamaProvider : AiProvider {

    override val id: AiProviderId = AiProviderId.OLLAMA
    override val name: String = "Ollama"

    private val client = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 15_000
        }
    }

    override suspend fun chat(
        systemPrompt: String,
        history: List<OllamaMessage>,
        userMessage: String,
    ): AiResult {
        val baseUrl = ConfigManager.getConfig().ollamaUrl
        val model = ConfigManager.getConfig().ollamaModel

        val messages = buildList {
            add(OllamaMessage("system", systemPrompt))
            addAll(history.takeLast(10))
            add(OllamaMessage("user", userMessage))
        }

        println("🤖 [Ollama] POST $baseUrl/api/chat | model=$model | msgs=${messages.size}")

        val rawBytes = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(
                Json.encodeToString(
                    OllamaChatRequest.serializer(),
                    OllamaChatRequest(model = model, messages = messages, stream = false),
                )
            )
        }.body<ByteArray>()

        val rawResponse = rawBytes.decodeToString()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        val parsedLines = rawResponse
            .lines()
            .mapNotNull { runCatching { json.decodeFromString<OllamaChatResponse>(it) }.getOrNull() }

        val fullContent = parsedLines
            .mapNotNull { it.message?.content }
            .filter { it.isNotEmpty() }
            .joinToString("")

        val promptTokens = parsedLines.lastOrNull { it.promptEvalCount != null }?.promptEvalCount ?: 0
        val responseTokens = parsedLines.lastOrNull { it.evalCount != null }?.evalCount ?: 0

        println("🤖 [Ollama] content=${fullContent.length} chars | tokens p=$promptTokens r=$responseTokens")
        return AiResult(
            text = fullContent,
            provider = id,
            model = model,
            promptTokens = promptTokens,
            responseTokens = responseTokens,
        )
    }
}
