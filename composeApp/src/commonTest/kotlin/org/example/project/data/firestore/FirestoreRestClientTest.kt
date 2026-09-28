package org.example.project.data.firestore

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.example.project.util.UserFacingException

class FirestoreRestClientTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val refreshFlags = mutableListOf<Boolean>()

    private fun client(handler: (HttpRequestData) -> Pair<HttpStatusCode, String>) = FirestoreRestClient(
        projectId = { "demo-project" },
        idToken = { force -> refreshFlags += force; if (force) "fresh-token" else "cached-token" },
        http = HttpClient(MockEngine { request ->
            requests += request
            val (status, body) = handler(request)
            respond(body, status, json)
        }),
    )

    @Test
    fun missingDocumentReadsAsNull() = runTest {
        val firestore = client { HttpStatusCode.NotFound to "{}" }
        assertNull(firestore.getDocument("users/u1/settings/budget"))
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/demo-project/databases/(default)/documents/users/u1/settings/budget",
            requests.single().url.toString(),
        )
    }

    @Test
    fun decodesDocumentFields() = runTest {
        val firestore = client {
            HttpStatusCode.OK to """{"fields":{"Food":{"doubleValue":1500.5},"updatedAt":{"integerValue":"42"},
                |"items":{"arrayValue":{"values":[{"stringValue":"Cash"}]}}}}""".trimMargin()
        }
        val fields = firestore.getDocument("users/u1/settings/budget")!!
        assertEquals(1500.5, fields.number("Food"))
        assertEquals(42L, fields["updatedAt"])
        assertEquals(listOf("Cash"), fields["items"])
    }

    @Test
    fun retriesOnceWithRefreshedTokenAfter401() = runTest {
        val firestore = client { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer cached-token") HttpStatusCode.Unauthorized to "{}"
            else HttpStatusCode.OK to """{"fields":{}}"""
        }
        firestore.getDocument("users/u1/settings/budget")
        assertEquals(listOf(false, true), refreshFlags)
        assertEquals("Bearer fresh-token", requests.last().headers[HttpHeaders.Authorization])
    }

    @Test
    fun createDocumentPostsEncodedFieldsAndReturnsId() = runTest {
        val firestore = client {
            HttpStatusCode.OK to """{"name":"projects/demo-project/databases/(default)/documents/users/u1/transactions/abc123"}"""
        }
        val id = firestore.createDocument("users/u1/transactions", mapOf("description" to "Lunch", "outflow" to 250.0, "isPaid" to true))
        assertEquals("abc123", id)
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        val body = (request.body as TextContent).text
        assertTrue(""""description":{"stringValue":"Lunch"}""" in body)
        assertTrue(""""outflow":{"doubleValue":250.0}""" in body)
        assertTrue(""""isPaid":{"booleanValue":true}""" in body)
    }

    @Test
    fun listDocumentsFollowsPagination() = runTest {
        val firestore = client { request ->
            if (request.url.parameters["pageToken"] == null) {
                HttpStatusCode.OK to """{"documents":[{"name":"x/t1","fields":{"description":{"stringValue":"A"}}}],"nextPageToken":"p2"}"""
            } else {
                HttpStatusCode.OK to """{"documents":[{"name":"x/t2","fields":{"description":{"stringValue":"B"}}}]}"""
            }
        }
        val docs = firestore.listDocuments("users/u1/transactions")
        assertEquals(listOf("t1", "t2"), docs.map { it.id })
        assertEquals("B", docs[1].fields.string("description"))
    }

    @Test
    fun serverErrorsBecomeUserFacingWithoutLeakingUrl() = runTest {
        val firestore = client { HttpStatusCode.Forbidden to """{"error":{"message":"Missing or insufficient permissions."}}""" }
        val error = assertFailsWith<UserFacingException> { firestore.getDocument("users/other/settings/budget") }
        assertEquals("You don't have access to this data.", error.message)
    }

    @Test
    fun valuesRoundTrip() {
        val original = mapOf(
            "s" to "text", "d" to 1.25, "l" to 7L, "b" to false, "n" to null,
            "list" to listOf("a", "b"), "map" to mapOf("k" to "v"),
        )
        val decoded = FirestoreValues.decodeFields(FirestoreValues.encodeFields(original))
        assertEquals(original, decoded)
    }
}
