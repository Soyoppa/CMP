package org.example.project.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

actual fun createFeatureFlagLoader(): FeatureFlagLoader = RemoteConfigFeatureFlagLoader()

/**
 * Firebase Remote Config kill-switches on Android — the same parameters the web build reads
 * (`signup_enabled`, `chat_enabled`). Fails open to [FeatureFlags] defaults.
 */
internal class RemoteConfigFeatureFlagLoader : FeatureFlagLoader {

    override suspend fun load() {
        runCatching {
            val defaults = FeatureFlags()
            val rc = FirebaseRemoteConfig.getInstance()
            rc.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(600) // kill-switch latency: 10 min
                    .build()
            ).await()
            rc.setDefaultsAsync(
                mapOf(
                    "signup_enabled" to defaults.signupEnabled,
                    "chat_enabled" to defaults.chatEnabled,
                )
            ).await()
            rc.fetchAndActivate().await()
            FeatureFlagStore.set(
                FeatureFlags(
                    signupEnabled = rc.getBoolean("signup_enabled"),
                    chatEnabled = rc.getBoolean("chat_enabled"),
                )
            )
        }
    }
}
