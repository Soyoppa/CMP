package org.example.project.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in principal. Guests are real (anonymous) Firebase users with no email. */
data class AppUser(
    val email: String?,
    val isGuest: Boolean,
) {
    val displayName: String get() = email ?: "Guest"
}

// --- Capabilities: single source of truth for guest gating across the app. ---
val AppUser.canWriteTransactions: Boolean get() = !isGuest
val AppUser.canUseDebugActions: Boolean get() = !isGuest
/** Max AI messages a guest may send in a session (full users: unlimited). */
val AppUser.aiMessageLimit: Int get() = if (isGuest) 1 else Int.MAX_VALUE
/** Max characters a guest may type per AI message (full users: unlimited). */
val AppUser.aiCharLimit: Int get() = if (isGuest) 20 else Int.MAX_VALUE

sealed interface AuthState {
    /** Determining persisted session at startup. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class Authenticated(val user: AppUser) : AuthState
}

/**
 * Process-wide auth session, read by the App gate, ViewModels (for capability gating), and screens.
 * Same shared-holder pattern as [org.example.project.data.ai.AiUsageTracker]: only the
 * [AuthRepository] mutates it; everything else observes [state]. Auth is genuinely app-global.
 */
object Session {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val currentUser: AppUser?
        get() = (_state.value as? AuthState.Authenticated)?.user

    val isGuest: Boolean
        get() = currentUser?.isGuest == true

    internal fun setAuthenticated(user: AppUser) { _state.value = AuthState.Authenticated(user) }
    internal fun setSignedOut() { _state.value = AuthState.SignedOut }
    internal fun setLoading() { _state.value = AuthState.Loading }
}
