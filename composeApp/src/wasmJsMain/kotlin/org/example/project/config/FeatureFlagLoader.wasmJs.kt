@file:OptIn(ExperimentalWasmJsInterop::class)

package org.example.project.config

import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise

actual fun createFeatureFlagLoader(): FeatureFlagLoader = RemoteConfigFeatureFlagLoader()

@Serializable
private data class FbConfig(
    val apiKey: String,
    val authDomain: String,
    val projectId: String,
    val storageBucket: String,
    val messagingSenderId: String,
    val appId: String,
)

/** Shape returned by the JS flags bridge (snake_case keys mirror the Remote Config parameter names). */
@Serializable
private data class FbFlags(
    val signup_enabled: Boolean = true,
    val guest_mode_enabled: Boolean = true,
    val chat_enabled: Boolean = true,
)

private val flagJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

// --- Bridge to firebase/remote-config (window.__financeFlags in index.html) ---
private fun flagsGet(configJson: String): Promise<JsString> = js("window.__financeFlags.get(configJson)")

internal class RemoteConfigFeatureFlagLoader : FeatureFlagLoader {

    private fun configJson(): String {
        val c = ConfigManager.getConfig()
        return flagJson.encodeToString(
            FbConfig.serializer(),
            FbConfig(
                apiKey = c.firebaseApiKey,
                authDomain = c.firebaseAuthDomain,
                projectId = c.firebaseProjectId,
                storageBucket = c.firebaseStorageBucket,
                messagingSenderId = c.firebaseMessagingSenderId,
                appId = c.firebaseAppId,
            ),
        )
    }

    override suspend fun load() {
        // The JS bridge swallows its own errors and returns defaults, so this won't throw in practice;
        // runCatching keeps a fail-open posture if the interop itself fails.
        runCatching {
            val raw: JsString = flagsGet(configJson()).await()
            val f = flagJson.decodeFromString(FbFlags.serializer(), raw.toString())
            FeatureFlagStore.set(
                FeatureFlags(
                    signupEnabled = f.signup_enabled,
                    guestModeEnabled = f.guest_mode_enabled,
                    chatEnabled = f.chat_enabled,
                )
            )
        }
    }
}
