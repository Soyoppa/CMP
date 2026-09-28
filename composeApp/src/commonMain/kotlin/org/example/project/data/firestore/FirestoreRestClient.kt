package org.example.project.data.firestore

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.util.UserFacingException

/** One stored document: its id (last path segment) and decoded fields. */
data class FirestoreDocument(val id: String, val fields: Map<String, Any?>)

/**
 * Minimal Cloud Firestore client over the REST API, authenticated as the signed-in user with a
 * Firebase ID token — so `firestore.rules` apply exactly as they would to the SDKs.
 *
 * Using REST (instead of each platform's SDK) keeps one implementation for web, Android, iOS and
 * desktop. Paths are relative to the database root, e.g. `users/{uid}/settings/budget`.
 *
 * @param idToken returns the current user's ID token; `forceRefresh` is set after a 401.
 */
class FirestoreRestClient(
    private val projectId: () -> String,
    private val idToken: suspend (forceRefresh: Boolean) -> String?,
    private val http: HttpClient = defaultHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun url(path: String): String =
        "https://firestore.googleapis.com/v1/projects/${projectId()}/databases/(default)/documents/$path"

    /** The document's fields, or null when it doesn't exist. */
    suspend fun getDocument(path: String): Map<String, Any?>? {
        val response = authorized { http.get(url(path)) { it() } }
        if (response.status == HttpStatusCode.NotFound) return null
        val body = requireSuccess(response)
        return FirestoreValues.decodeFields(json.parseToJsonElement(body).jsonObject["fields"]?.jsonObject)
    }

    /** Creates or fully replaces the document at [path]. */
    suspend fun setDocument(path: String, fields: Map<String, Any?>) {
        val body = buildJsonObject { put("fields", FirestoreValues.encodeFields(fields)) }.toString()
        requireSuccess(authorized {
            http.patch(url(path)) {
                it()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        })
    }

    /** Adds a document with a generated id to [collectionPath]; returns the new id. */
    suspend fun createDocument(collectionPath: String, fields: Map<String, Any?>): String {
        val body = buildJsonObject { put("fields", FirestoreValues.encodeFields(fields)) }.toString()
        val response = requireSuccess(authorized {
            http.post(url(collectionPath)) {
                it()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        })
        return documentId(json.parseToJsonElement(response).jsonObject["name"]?.jsonPrimitive?.contentOrNull)
    }

    /** Every document directly under [collectionPath], following pagination. */
    suspend fun listDocuments(collectionPath: String): List<FirestoreDocument> {
        val documents = mutableListOf<FirestoreDocument>()
        var pageToken: String? = null
        do {
            val token = pageToken
            val response = authorized {
                http.get(url(collectionPath)) {
                    it()
                    parameter("pageSize", PAGE_SIZE)
                    if (token != null) parameter("pageToken", token)
                }
            }
            // A collection that was never written reads as 404 on some paths; treat it as empty.
            if (response.status == HttpStatusCode.NotFound) return documents
            val root = json.parseToJsonElement(requireSuccess(response)).jsonObject
            (root["documents"] as? JsonArray)?.forEach { element ->
                val doc = element.jsonObject
                documents += FirestoreDocument(
                    id = documentId(doc["name"]?.jsonPrimitive?.contentOrNull),
                    fields = FirestoreValues.decodeFields(doc["fields"]?.jsonObject),
                )
            }
            pageToken = root["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (pageToken != null)
        return documents
    }

    /** Deletes the document at [path]; deleting a missing document is not an error. */
    suspend fun deleteDocument(path: String) {
        val response = authorized { http.delete(url(path)) { it() } }
        if (response.status != HttpStatusCode.NotFound) requireSuccess(response)
    }

    /**
     * Runs [request] with the user's bearer token, retrying once with a refreshed token when the
     * first attempt is rejected as unauthenticated (the cached token may have just expired).
     */
    private suspend fun authorized(
        request: suspend (auth: HttpRequestBuilder.() -> Unit) -> HttpResponse,
    ): HttpResponse {
        suspend fun attempt(forceRefresh: Boolean): HttpResponse {
            val token = idToken(forceRefresh) ?: throw UserFacingException("Please sign in again.")
            return request { bearerAuth(token) }
        }
        val first = attempt(forceRefresh = false)
        return if (first.status == HttpStatusCode.Unauthorized) attempt(forceRefresh = true) else first
    }

    private suspend fun requireSuccess(response: HttpResponse): String {
        val body = response.bodyAsText()
        if (response.status.isSuccess()) return body
        throw UserFacingException(
            when (response.status) {
                HttpStatusCode.Unauthorized -> "Your session expired. Please sign in again."
                HttpStatusCode.Forbidden -> "You don't have access to this data."
                else -> "Couldn't reach your cloud data (HTTP ${response.status.value}). Please try again."
            }
        )
    }

    private fun documentId(name: String?): String = name?.substringAfterLast('/').orEmpty()

    private companion object {
        const val PAGE_SIZE = 300
    }
}

internal fun defaultHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
    }
}
