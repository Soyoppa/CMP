package org.example.project.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remote kill-switches for UI features, sourced from Firebase Remote Config (web) and
 * defaulting to fully-enabled everywhere else. Defaults are "fail-open": if Remote Config
 * is unreachable or hasn't been fetched yet, every feature stays on.
 */
data class FeatureFlags(
    val signupEnabled: Boolean = false,
    val guestModeEnabled: Boolean = true,
    val chatEnabled: Boolean = true,
)

/**
 * Process-wide feature-flag holder, observed by screens (same shared-holder pattern as
 * [org.example.project.auth.Session]). Only [FeatureFlagLoader] mutates it; everything else
 * reads [state].
 */
object FeatureFlagStore {
    private val _state = MutableStateFlow(FeatureFlags())
    val state: StateFlow<FeatureFlags> = _state.asStateFlow()

    internal fun set(flags: FeatureFlags) { _state.value = flags }
}

/** Loads remote flags into [FeatureFlagStore]. Safe to call at startup; failures keep defaults. */
interface FeatureFlagLoader {
    suspend fun load()
}

expect fun createFeatureFlagLoader(): FeatureFlagLoader

/** No-op loader for platforms without a Remote Config binding — flags stay fully enabled. */
internal class NoopFeatureFlagLoader : FeatureFlagLoader {
    override suspend fun load() {}
}
