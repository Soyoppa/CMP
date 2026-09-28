package org.example.project.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.example.project.data.ledger.LedgerSource

/** The signed-in principal. Guests are real (anonymous) Firebase users with no email. */
data class AppUser(
    val email: String?,
    val isGuest: Boolean,
    /** Firebase Auth uid — the key for all per-user cloud data. */
    val uid: String,
    /** Which ledger this session reads and writes; resolved once at sign-in. */
    val ledgerSource: LedgerSource = LedgerSource.CLOUD,
)

// --- Capabilities: single source of truth for guest gating across the app. ---
/** Max AI messages a guest may send in a session (full users: unlimited). */
val AppUser.aiMessageLimit: Int get() = if (isGuest) 2 else Int.MAX_VALUE
/** Max characters per AI message: a small taste for guests, a generous cap for everyone else. */
val AppUser.aiCharLimit: Int get() = if (isGuest) 20 else 4_000

sealed interface AuthState {
    /** Determining persisted session at startup. */
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class Authenticated(val user: AppUser) : AuthState
}

/**
 * Process-wide auth session, read by the App gate, repositories (for routing) and screens.
 * Only [AuthRepository] mutates it; everything else observes [state].
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
}
