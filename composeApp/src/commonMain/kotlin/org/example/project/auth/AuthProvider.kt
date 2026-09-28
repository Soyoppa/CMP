package org.example.project.auth

/** A Firebase Auth user as reported by the platform provider. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val isAnonymous: Boolean,
)

/**
 * The platform's Firebase Auth binding — the only auth code that differs per target:
 *  - wasmJs: the `firebase/auth` JS SDK via `window.__financeAuth`
 *  - Android: the native `firebase-auth` SDK
 *  - iOS / desktop / JS: the Firebase Auth REST API ([RestAuthProvider])
 *
 * Every failure is thrown as a [org.example.project.util.UserFacingException] with a message that
 * is safe to show. Session bookkeeping lives in the common [AuthRepository].
 */
interface AuthProvider {
    /** The persisted user from a previous launch, if any. */
    suspend fun currentUser(): AuthUser?
    suspend fun signIn(email: String, password: String): AuthUser
    suspend fun signUp(email: String, password: String): AuthUser
    suspend fun signInAnonymously(): AuthUser
    suspend fun signOut()

    /** Deletes the signed-in Firebase account. May require a recent sign-in. */
    suspend fun deleteCurrentUser()

    /** The signed-in user's Firebase ID token for REST calls; null when signed out. */
    suspend fun idToken(forceRefresh: Boolean): String?
}

expect fun createAuthProvider(): AuthProvider

/** Maps Firebase Auth error codes (JS `auth/…`, REST `EMAIL_EXISTS`, …) to friendly text. */
internal fun friendlyAuthMessage(code: String?): String {
    val normalized = code.orEmpty().substringAfter("auth/").uppercase().replace('-', '_')
    return when {
        normalized.startsWith("INVALID_CREDENTIAL") || normalized.startsWith("INVALID_LOGIN_CREDENTIALS") ||
            normalized.startsWith("WRONG_PASSWORD") || normalized.startsWith("INVALID_PASSWORD") ||
            normalized.startsWith("EMAIL_NOT_FOUND") || normalized.startsWith("USER_NOT_FOUND") ->
            "Wrong email or password."
        normalized.startsWith("INVALID_EMAIL") -> "That email looks invalid."
        normalized.startsWith("EMAIL_EXISTS") || normalized.startsWith("EMAIL_ALREADY_IN_USE") ->
            "That email is already registered."
        normalized.startsWith("WEAK_PASSWORD") -> "Password must be at least 6 characters."
        normalized.startsWith("TOO_MANY") -> "Too many attempts — try again later."
        normalized.startsWith("USER_DISABLED") -> "This account has been disabled."
        normalized.startsWith("REQUIRES_RECENT_LOGIN") || normalized.startsWith("CREDENTIAL_TOO_OLD") ->
            "For your security, sign out, sign back in, and try again."
        normalized.startsWith("NETWORK") -> "No connection — check your network and try again."
        normalized.startsWith("OPERATION_NOT_ALLOWED") || normalized.startsWith("ADMIN_RESTRICTED") ->
            "This sign-in method isn't enabled."
        else -> "Authentication failed."
    }
}
