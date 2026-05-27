package org.example.project.auth

/**
 * Authentication actions. Implementations update [Session] on success/failure.
 * - wasmJs: real Firebase Auth via the `firebase/auth` JS bridge.
 * - other targets: [BypassAuthRepository] (auth disabled; non-deployed dev targets).
 */
interface AuthRepository {
    /** Restore any persisted session at startup, then push the result into [Session]. */
    suspend fun restoreSession()
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signUp(email: String, password: String): Result<Unit>
    suspend fun continueAsGuest(): Result<Unit>
    suspend fun signOut()
}

expect fun createAuthRepository(): AuthRepository

/**
 * No-op auth for platforms without a Firebase Auth binding (jvm/js/ios, and Android until the
 * `firebase-auth` actual + google-services.json are wired). Treats the local session as a full,
 * signed-in user so those targets aren't locked out during development.
 */
internal class BypassAuthRepository : AuthRepository {
    override suspend fun restoreSession() {
        Session.setAuthenticated(AppUser(email = "local@device", isGuest = false))
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        Session.setAuthenticated(AppUser(email = email, isGuest = false))
        return Result.success(Unit)
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> = signIn(email, password)

    override suspend fun continueAsGuest(): Result<Unit> {
        Session.setAuthenticated(AppUser(email = null, isGuest = true))
        return Result.success(Unit)
    }

    override suspend fun signOut() {
        Session.setSignedOut()
    }
}
