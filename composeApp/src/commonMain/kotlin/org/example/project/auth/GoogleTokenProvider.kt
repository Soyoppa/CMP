package org.example.project.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the Google OAuth access token obtained from [AuthRepository.signInWithGoogle].
 *
 * The token is short-lived (~1h) and Firebase does NOT refresh it, so callers that get a
 * 401 from the Sheets API should prompt the user to sign in again. Kept separate from
 * [Session] (which is just the signed-in principal) because the token is a transient
 * credential, not identity — and so the data layer can read it without depending on auth UI.
 *
 * Only [AuthRepository] mutates it; the data layer observes/reads.
 */
object GoogleTokenProvider {
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    /** Current bearer token, or null if the user hasn't completed Google sign-in this session. */
    val accessToken: String? get() = _token.value

    internal fun set(token: String?) { _token.value = token }
    internal fun clear() { _token.value = null }
}
