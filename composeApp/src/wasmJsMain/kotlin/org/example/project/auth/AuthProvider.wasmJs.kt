@file:OptIn(ExperimentalWasmJsInterop::class)

package org.example.project.auth

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.firebaseWebConfigJson
import org.example.project.util.UserFacingException

actual fun createAuthProvider(): AuthProvider = FirebaseJsAuthProvider()

/** Shape returned by the JS auth bridge. */
@Serializable
private data class BridgeUser(
    val signedIn: Boolean = false,
    val email: String? = null,
    val isGuest: Boolean = false,
    val uid: String? = null,
)

private val authJson = Json { ignoreUnknownKeys = true }

// --- Bridge to firebase/auth (window.__financeAuth in firebase-bridge.js) ---
private fun authInit(configJson: String): Promise<JsString> = js("window.__financeAuth.init(configJson)")
private fun authSignIn(email: String, password: String): Promise<JsString> =
    js("window.__financeAuth.signIn(email, password)")
private fun authSignUp(email: String, password: String): Promise<JsString> =
    js("window.__financeAuth.signUp(email, password)")
private fun authSignOut(): Promise<JsString> = js("window.__financeAuth.signOut()")
private fun authIdToken(forceRefresh: Boolean): Promise<JsString> = js("window.__financeAuth.idToken(forceRefresh)")
private fun authDeleteUser(): Promise<JsString> = js("window.__financeAuth.deleteUser()")

/** Firebase Auth via the JS SDK, which persists the session in the browser (local persistence). */
internal class FirebaseJsAuthProvider : AuthProvider {

    override suspend fun currentUser(): AuthUser? = parseUser(call { authInit(firebaseWebConfigJson()) })

    override suspend fun signIn(email: String, password: String): AuthUser =
        requireUser(call { authSignIn(email, password) })

    override suspend fun signUp(email: String, password: String): AuthUser =
        requireUser(call { authSignUp(email, password) })

    override suspend fun signOut() {
        call { authSignOut() }
    }

    override suspend fun deleteCurrentUser() {
        call { authDeleteUser() }
    }

    override suspend fun idToken(forceRefresh: Boolean): String? =
        call { authIdToken(forceRefresh) }.ifEmpty { null }

    /**
     * Awaits one bridge promise. The bridge only rejects with its own allow-listed, friendly
     * messages (see `_friendly`), so they're safe to surface as-is.
     */
    private suspend fun call(block: () -> Promise<JsString>): String = try {
        block().await<JsString>().toString()
    } catch (e: Throwable) {
        throw UserFacingException(e.message?.takeIf { it.isNotBlank() } ?: "Authentication failed.")
    }

    private fun parseUser(raw: String): AuthUser? {
        val u = authJson.decodeFromString(BridgeUser.serializer(), raw)
        val uid = u.uid
        return if (u.signedIn && uid != null) AuthUser(uid = uid, email = u.email, isAnonymous = u.isGuest) else null
    }

    private fun requireUser(raw: String): AuthUser =
        parseUser(raw) ?: throw UserFacingException("Authentication failed.")
}
