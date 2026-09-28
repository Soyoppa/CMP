package org.example.project.data.sheets

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.example.project.util.UserFacingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheetsGatewayClientTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun gateway(reply: String, token: String? = "id-token") = SheetsGatewayClient(
        idToken = { token },
        gatewayUrl = { "https://script.google.com/macros/s/abc/exec" },
        http = HttpClient(MockEngine { request ->
            requests += request
            respond(reply, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
    )

    @Test
    fun listSendsTokenInPlainTextPostAndParsesRows() = runTest {
        val rows = gateway("""{"success":true,"rows":[["Date","Description"],["3/1/2026","Lunch"]]}""").readRows()
        assertEquals(listOf(listOf("Date", "Description"), listOf("3/1/2026", "Lunch")), rows)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        val body = request.body as TextContent
        // text/plain keeps it a CORS "simple request" (no preflight, which Apps Script can't answer).
        assertEquals(ContentType.Text.Plain, body.contentType.withoutParameters())
        assertTrue(""""idToken":"id-token"""" in body.text)
        assertTrue(""""action":"list"""" in body.text)
    }

    @Test
    fun appendReportsUnauthorizedAsFriendlyError() = runTest {
        val result = gateway("""{"success":false,"error":"Unauthorized"}""").append(mapOf("date" to "3/1/2026"))
        assertFalse(result.success)
        assertEquals("This account doesn't have access to the shared sheet.", result.errorMessage)
    }

    @Test
    fun missingTokenNeverHitsTheNetwork() = runTest {
        assertFailsWith<UserFacingException> { gateway("{}", token = null).readRows() }
        assertTrue(requests.isEmpty())
    }
}
