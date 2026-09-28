package org.example.project.auth

import org.example.project.data.ledger.LedgerAccessResolver
import org.example.project.data.settings.AccountDataEraser
import org.example.project.util.UserFacingException

/**
 * Authentication use-cases shared by every platform. Talks to the platform [AuthProvider] and
 * publishes the result to [Session], resolving the account's ledger before the app opens.
 *
 * All methods returning [Result] fail with a [UserFacingException] message suitable for the UI.
 */
class AuthRepository(
    private val provider: AuthProvider,
    private val ledgerAccess: LedgerAccessResolver,
    private val dataEraser: AccountDataEraser,
) {
    /** Restores a persisted session at startup, or lands on the sign-in screen. */
    suspend fun restoreSession() {
        val user = runCatching { provider.currentUser() }.getOrNull()
        if (user == null) Session.setSignedOut() else startSession(user)
    }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        runCatching { startSession(provider.signIn(email.trim(), password)) }

    suspend fun signUp(email: String, password: String): Result<Unit> =
        runCatching { startSession(provider.signUp(email.trim(), password)) }

    suspend fun continueAsGuest(): Result<Unit> =
        runCatching { startSession(provider.signInAnonymously()) }

    suspend fun signOut() {
        runCatching { provider.signOut() }
        Session.setSignedOut()
    }

    /**
     * Permanently deletes the account and all of its cloud data.
     *
     * Email accounts must confirm with [password]: Firebase only deletes accounts that signed in
     * recently, so we re-authenticate first — otherwise the data could be erased while the account
     * deletion itself is rejected. Guests (anonymous) have no stored data and no password.
     */
    suspend fun deleteAccount(password: String?): Result<Unit> = runCatching {
        val user = Session.currentUser ?: throw UserFacingException("You're not signed in.")
        if (!user.isGuest) {
            val email = user.email ?: throw UserFacingException("This account can't be verified.")
            if (password.isNullOrEmpty()) throw UserFacingException("Enter your password to confirm.")
            provider.signIn(email, password) // re-authenticates; throws "Wrong email or password."
            dataEraser.eraseAll(user.uid)
        }
        provider.deleteCurrentUser()
        Session.setSignedOut()
    }

    private suspend fun startSession(user: AuthUser) {
        val ledgerSource = ledgerAccess.resolve(user)
        Session.setAuthenticated(
            AppUser(email = user.email, isGuest = user.isAnonymous, uid = user.uid, ledgerSource = ledgerSource)
        )
    }
}
