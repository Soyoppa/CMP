@file:OptIn(ExperimentalWasmJsInterop::class)

package org.example.project.auth

import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise

actual fun createAuthRepository(): AuthRepository = FirebaseAuthRepository()

@Serializable
private data class FbConfig(
    val apiKey: String,
    val authDomain: String,
    val projectId: String,
    val storageBucket: String,
    val messagingSenderId: String,
    val appId: String,
)

/** Shape returned by the JS auth bridge. */
@Serializable
private data class FbAuthUser(
    val signedIn: Boolean = false,
    val email: String? = null,
    val isGuest: Boolean = false,
)

private val authJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

// --- Bridge to firebase/auth (window.__financeAuth in index.html) ---
private fun authInit(configJson: String): Promise<JsString> = js("window.__financeAuth.init(configJson)")
private fun authSignIn(email: String, password: String): Promise<JsString> =
    js("window.__financeAuth.signIn(email, password)")
private fun authSignUp(email: String, password: String): Promise<JsString> =
    js("window.__financeAuth.signUp(email, password)")
private fun authGuest(): Promise<JsString> = js("window.__financeAuth.guest()")
private fun authSignOut(): Promise<JsString> = js("window.__financeAuth.signOut()")

internal class FirebaseAuthRepository : AuthRepository {

    private fun configJson(): String {
        val c = ConfigManager.getConfig()
        return authJson.encodeToString(
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

    private fun applyUser(rawJson: String) {
        val u = authJson.decodeFromString(FbAuthUser.serializer(), rawJson)
        if (u.signedIn) Session.setAuthenticated(AppUser(email = u.email, isGuest = u.isGuest))
        else Session.setSignedOut()
    }

    override suspend fun restoreSession() {
        val raw: JsString = authInit(configJson()).await()
        applyUser(raw.toString())
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val raw: JsString = authSignIn(email.trim(), password).await()
        applyUser(raw.toString())
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        val raw: JsString = authSignUp(email.trim(), password).await()
        applyUser(raw.toString())
    }

    override suspend fun continueAsGuest(): Result<Unit> = runCatching {
        val raw: JsString = authGuest().await()
        applyUser(raw.toString())
    }

    override suspend fun signOut() {
        runCatching {
            val ignored: JsString = authSignOut().await()
            ignored
        }
        Session.setSignedOut()
    }
}
