package org.example.project.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.example.project.util.UserFacingException

class RestAuthProviderTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val store = InMemoryCredentialStore()

    private fun provider(handler: (HttpRequestData) -> Pair<HttpStatusCode, String>) = RestAuthProvider(
        store = store,
        apiKey = { "test-key" },
        http = HttpClient(MockEngine { request ->
            requests += request
            val (status, body) = handler(request)
            respond(body, status, json)
        }),
    )

    @Test
    fun signInPersistsCredential() = runTest {
        val auth = provider {
            HttpStatusCode.OK to """{"localId":"uid-1","email":"a@b.co","idToken":"id-1","refreshToken":"r-1","expiresIn":"3600"}"""
        }
        val user = auth.signIn("a@b.co", "secret1")
        assertEquals(AuthUser("uid-1", "a@b.co", isAnonymous = false), user)
        assertEquals(user, auth.currentUser())
        assertEquals("id-1", auth.idToken(forceRefresh = false))
        assertTrue(requests.single().url.toString().startsWith("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"))
    }

    @Test
    fun anonymousSignInHasNoEmail() = runTest {
        val auth = provider {
            HttpStatusCode.OK to """{"localId":"anon","idToken":"id","refreshToken":"r","expiresIn":"3600"}"""
        }
        assertEquals(true, auth.signInAnonymously().isAnonymous)
    }

    @Test
    fun firebaseErrorCodesBecomeFriendlyMessages() = runTest {
        val auth = provider {
            HttpStatusCode.BadRequest to """{"error":{"message":"WEAK_PASSWORD : Password should be at least 6 characters"}}"""
        }
        val error = assertFailsWith<UserFacingException> { auth.signUp("a@b.co", "123") }
        assertEquals("Password must be at least 6 characters.", error.message)
        assertNull(store.load())
    }

    @Test
    fun forcedRefreshUsesSecureTokenEndpoint() = runTest {
        store.save(StoredAuth("uid", "a@b.co", false, idToken = "old", refreshToken = "r-1", expiresAt = Long.MAX_VALUE))
        val auth = provider {
            HttpStatusCode.OK to """{"id_token":"new","refresh_token":"r-2","expires_in":"3600","user_id":"uid"}"""
        }
        assertEquals("new", auth.idToken(forceRefresh = true))
        assertEquals("r-2", store.load()?.refreshToken)
        assertTrue(requests.single().url.toString().startsWith("https://securetoken.googleapis.com/v1/token"))
    }

    @Test
    fun signOutClearsCredential() = runTest {
        store.save(StoredAuth("uid", null, true, "id", "r", Long.MAX_VALUE))
        provider { HttpStatusCode.OK to "{}" }.signOut()
        assertNull(store.load())
    }
}
