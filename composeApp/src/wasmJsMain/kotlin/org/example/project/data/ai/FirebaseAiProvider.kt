@file:OptIn(ExperimentalWasmJsInterop::class)

package org.example.project.data.ai

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.data.OllamaMessage
import kotlin.js.JsString
import kotlin.js.Promise

internal actual fun geminiProviderOrNull(): AiProvider? = FirebaseAiProvider()

@Serializable
private data class FirebaseConfig(
    val apiKey: String,
    val authDomain: String,
    val projectId: String,
    val storageBucket: String,
    val messagingSenderId: String,
    val appId: String,
)

@Serializable
private data class GeminiPart(val text: String)

@Serializable
private data class GeminiContent(val role: String, val parts: List<GeminiPart>)

/** Shape of the JSON the JS bridge returns: reply text + Gemini usageMetadata. */
@Serializable
private data class GeminiReply(
    val text: String = "",
    val promptTokens: Int = 0,
    val responseTokens: Int = 0,
    val totalTokens: Int = 0,
)

private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

// --- Bridge to the firebase/ai JS SDK (window.__financeAi is defined in index.html) ---

/** Initialises the Firebase app + AI Logic backend. Idempotent; returns a Promise we don't await
 *  here because [financeAiGenerate] internally awaits the same readiness gate. */
private fun financeAiInit(configJson: String): Unit =
    js("window.__financeAi.init(configJson)")

/** Runs a single budget-aware chat turn and resolves to the assistant's text. */
private fun financeAiGenerate(
    model: String,
    systemPrompt: String,
    historyJson: String,
    userMessage: String,
): Promise<JsString> =
    js("window.__financeAi.generate(model, systemPrompt, historyJson, userMessage)")

/**
 * Primary AI provider on web: true Firebase AI Logic via the modular `firebase/ai` JS SDK.
 *
 * The Gemini key is never present client-side — Firebase AI Logic proxies the request and keeps
 * it server-side. We pass the (non-secret) Firebase web config in at runtime from BuildConfig so
 * the project's "local.properties is source of truth" convention is preserved.
 */
internal class FirebaseAiProvider : AiProvider {

    override val id: AiProviderId = AiProviderId.GEMINI
    override val name: String = "Firebase AI Logic (Gemini)"

    private var initialized = false

    private fun ensureInitialized() {
        if (initialized) return
        val cfg = ConfigManager.getConfig()
        val configJson = json.encodeToString(
            FirebaseConfig.serializer(),
            FirebaseConfig(
                apiKey = cfg.firebaseApiKey,
                authDomain = cfg.firebaseAuthDomain,
                projectId = cfg.firebaseProjectId,
                storageBucket = cfg.firebaseStorageBucket,
                messagingSenderId = cfg.firebaseMessagingSenderId,
                appId = cfg.firebaseAppId,
            ),
        )
        financeAiInit(configJson)
        initialized = true
    }

    override suspend fun chat(
        systemPrompt: String,
        history: List<OllamaMessage>,
        userMessage: String,
    ): AiResult {
        ensureInitialized()

        // Gemini multi-turn history: drop system turns (handled via systemInstruction),
        // map assistant→model, and keep only the last 10 turns to bound the payload.
        val geminiHistory = history
            .filter { it.role != "system" }
            .takeLast(10)
            .map {
                GeminiContent(
                    role = if (it.role == "assistant") "model" else "user",
                    parts = listOf(GeminiPart(it.content)),
                )
            }
        val historyJson = json.encodeToString(
            ListSerializer(GeminiContent.serializer()),
            geminiHistory,
        )

        val model = ConfigManager.getConfig().geminiModel
        println("🤖 [Firebase AI] generateContent | model=$model | history=${geminiHistory.size}")

        val raw: JsString = financeAiGenerate(model, systemPrompt, historyJson, userMessage).await()
        val reply = json.decodeFromString(GeminiReply.serializer(), raw.toString())
        println("🤖 [Firebase AI] tokens p=${reply.promptTokens} r=${reply.responseTokens} total=${reply.totalTokens}")
        return AiResult(
            text = reply.text,
            provider = id,
            model = model,
            promptTokens = reply.promptTokens,
            responseTokens = reply.responseTokens,
        )
    }
}
