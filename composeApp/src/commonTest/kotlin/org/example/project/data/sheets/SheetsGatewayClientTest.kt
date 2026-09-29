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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.example.project.data.ledger.LedgerEntry
import org.example.project.model.Transaction
import org.example.project.util.UserFacingException

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

    @Test
    fun deleteRowSendsRowAndExpectedCells() = runTest {
        val entry = LedgerEntry(id = "7", description = "Lunch", amount = 250.0, category = "Food", monthNumber = 3, date = "3/1/2026")
        gateway("""{"success":true}""").deleteRow(entry)
        val body = (requests.single().body as TextContent).text
        assertTrue(""""action":"delete"""" in body)
        assertTrue(""""row":7""" in body)
        assertTrue(""""expectDate":"3/1/2026"""" in body)
        assertTrue(""""expectDescription":"Lunch"""" in body)
    }

    @Test
    fun deleteRowRefusesHeaderOrNonRowIds() = runTest {
        val header = LedgerEntry(id = "1", description = "x", amount = 1.0, category = "", monthNumber = 0)
        assertFailsWith<UserFacingException> { gateway("{}").deleteRow(header) }
        assertFailsWith<UserFacingException> { gateway("{}").deleteRow(header.copy(id = "firestore-doc-id")) }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun changedRowBecomesRefreshHint() = runTest {
        val entry = LedgerEntry(id = "7", description = "Lunch", amount = 250.0, category = "Food", monthNumber = 3)
        val error = assertFailsWith<UserFacingException> {
            gateway("""{"success":false,"error":"Row changed"}""").deleteRow(entry)
        }
        assertEquals("The sheet changed since it was loaded. Refresh and try again.", error.message)
    }

    @Test
    fun tracker1AppendLeavesUnusedAmountBlank() = runTest {
        Tracker1SheetDataSource(gateway("""{"success":true}""")).addTransaction(
            Transaction(date = "3/1/2026", description = "Lunch", outflow = 250.0, category = "Food", isPaid = true)
        )
        val body = (requests.single().body as TextContent).text
        assertTrue(""""inflow":""""" in body, body)
        assertTrue(""""outflow":250.0""" in body, body)
        assertTrue(""""isPaid":true""" in body, body)
    }

    @Test
    fun followsAppsScriptRedirectWithGetOnNativeClients() = runTest {
        val client = SheetsGatewayClient(
            idToken = { "id-token" },
            gatewayUrl = { "https://script.google.com/macros/s/abc/exec" },
            http = HttpClient(MockEngine { request ->
                requests += request
                if (request.method == HttpMethod.Post) {
                    respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://script.googleusercontent.com/echo?x=1"))
                } else {
                    respond("""{"success":true,"rows":[["h"],["r"]]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            }),
        )
        assertEquals(listOf(listOf("h"), listOf("r")), client.readRows())
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Get), requests.map { it.method })
        assertEquals("https://script.googleusercontent.com/echo?x=1", requests.last().url.toString())
    }

    @Test
    fun htmlPageFromWrongDeploymentGetsActionableMessage() = runTest {
        val error = assertFailsWith<UserFacingException> {
            gateway("""<!DOCTYPE html><html><head><title>Error</title></head></html>""").readRows()
        }
        assertEquals(
            "The shared sheet isn't set up correctly. Redeploy the Sheets gateway script and check its URL.",
            error.message,
        )
    }
}
