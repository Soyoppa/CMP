package org.example.project.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import org.example.project.config.ConfigManager

internal actual fun geminiProviderOrNull(): AiProvider? = FirebaseAiAndroidProvider()

/**
 * Firebase AI Logic (Gemini Developer API backend) via the native Android SDK. The Gemini key
 * never ships in the app — Firebase proxies the call for this app's Firebase project.
 */
internal class FirebaseAiAndroidProvider : AiProvider {

    override val id: AiProviderId = AiProviderId.GEMINI
    override val name: String = "Firebase AI Logic (Gemini)"

    override suspend fun chat(systemPrompt: String, history: List<ChatTurn>, userMessage: String): AiResult {
        val modelName = ConfigManager.getConfig().geminiModel
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = modelName,
            systemInstruction = content { text(systemPrompt) },
        )
        // Same bounded history as the web provider: last 10 user/assistant turns.
        val chat = model.startChat(
            history = history.filter { it.role != "system" }.takeLast(10).map { turn ->
                content(role = if (turn.role == "assistant") "model" else "user") { text(turn.content) }
            }
        )
        val response = chat.sendMessage(userMessage)
        return AiResult(
            text = response.text.orEmpty(),
            provider = id,
            model = modelName,
            promptTokens = response.usageMetadata?.promptTokenCount ?: 0,
            responseTokens = response.usageMetadata?.candidatesTokenCount ?: 0,
        )
    }
}
