package org.example.project.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.project.config.ConfigManager
import org.example.project.data.firestore.defaultHttpClient
import org.example.project.util.UserFacingException

/** The persisted credential of a REST-authenticated user. */
@Serializable
data class StoredAuth(
    val uid: String,
    val email: String? = null,
    val isAnonymous: Boolean,
    val idToken: String,
    val refreshToken: String,
    /** Epoch millis after which [idToken] must be refreshed. */
    val expiresAt: Long,
)

/** Where [RestAuthProvider] keeps the credential between launches (Keychain on iOS). */
interface AuthCredentialStore {
    fun load(): StoredAuth?
    fun save(auth: StoredAuth?)
}

/** Keeps the credential for the process lifetime only — the user signs in again next launch. */
class InMemoryCredentialStore : AuthCredentialStore {
    private var auth: StoredAuth? = null
    override fun load(): StoredAuth? = auth
    override fun save(auth: StoredAuth?) { this.auth = auth }
}

/**
 * Firebase Auth over its REST API (Identity Toolkit + Secure Token), for targets without a
 * Firebase SDK binding. Supports email/password and anonymous sign-in, token refresh and
 * account deletion — everything [AuthProvider] needs.
 */
@OptIn(ExperimentalTime::class)
class RestAuthProvider(
    private val store: AuthCredentialStore,
    private val http: HttpClient = defaultHttpClient(),
    private val apiKey: () -> String = { ConfigManager.getConfig().firebaseApiKey },
) : AuthProvider {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val refreshLock = Mutex()

    override suspend fun currentUser(): AuthUser? = store.load()?.toAuthUser()

    override suspend fun signIn(email: String, password: String): AuthUser =
        identity("accounts:signInWithPassword") {
            put("email", email)
            put("password", password)
        }

    override suspend fun signUp(email: String, password: String): AuthUser =
        identity("accounts:signUp") {
            put("email", email)
            put("password", password)
        }

    override suspend fun signOut() = store.save(null)

    override suspend fun deleteCurrentUser() {
        val token = idToken(forceRefresh = false) ?: throw UserFacingException("You're not signed in.")
        val response = http.post("$IDENTITY_URL/accounts:delete") {
            parameter("key", apiKey())
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("idToken", token) }.toString())
        }
        requireSuccess(response)
        store.save(null)
    }

    override suspend fun idToken(forceRefresh: Boolean): String? = refreshLock.withLock {
        val current = store.load() ?: return null
        if (!forceRefresh && current.expiresAt - EXPIRY_MARGIN_MS > now()) return current.idToken

        val response = http.submitForm(
            url = "$SECURE_TOKEN_URL?key=${apiKey()}",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", current.refreshToken)
            },
        )
        val body = requireSuccess(response)
        val root = json.parseToJsonElement(body).jsonObject
        val refreshed = current.copy(
            idToken = root.string("id_token") ?: return null,
            refreshToken = root.string("refresh_token") ?: current.refreshToken,
            expiresAt = now() + (root.string("expires_in")?.toLongOrNull() ?: 3600L) * 1000,
        )
        store.save(refreshed)
        refreshed.idToken
    }

    /** Calls an Identity Toolkit sign-in/up endpoint and persists the returned credential. */
    private suspend fun identity(
        endpoint: String,
        fields: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): AuthUser {
        val response = try {
            http.post("$IDENTITY_URL/$endpoint") {
                parameter("key", apiKey())
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { fields(); put("returnSecureToken", true) }.toString())
            }
        } catch (e: Exception) {
            throw UserFacingException(friendlyAuthMessage("NETWORK"))
        }
        val root = json.parseToJsonElement(requireSuccess(response)).jsonObject
        val email = root.string("email")?.takeIf { it.isNotBlank() }
        val auth = StoredAuth(
            uid = root.string("localId") ?: throw UserFacingException("Authentication failed."),
            email = email,
            isAnonymous = email == null,
            idToken = root.string("idToken") ?: throw UserFacingException("Authentication failed."),
            refreshToken = root.string("refreshToken") ?: throw UserFacingException("Authentication failed."),
            expiresAt = now() + (root.string("expiresIn")?.toLongOrNull() ?: 3600L) * 1000,
        )
        store.save(auth)
        return auth.toAuthUser()
    }

    /** Body of a 2xx response; otherwise a friendly error from Firebase's error code. */
    private suspend fun requireSuccess(response: HttpResponse): String {
        val body = response.bodyAsText()
        if (response.status.isSuccess()) return body
        val code = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.string("message")
        }.getOrNull()
        // Codes look like "WEAK_PASSWORD : Password should be at least 6 characters".
        throw UserFacingException(friendlyAuthMessage(code?.substringBefore(' ')))
    }

    private fun StoredAuth.toAuthUser() = AuthUser(uid = uid, email = email, isAnonymous = isAnonymous)

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private companion object {
        const val IDENTITY_URL = "https://identitytoolkit.googleapis.com/v1"
        const val SECURE_TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
