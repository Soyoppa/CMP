package org.example.project.data.sheets

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.example.project.config.ConfigManager
import org.example.project.data.firestore.defaultHttpClient
import org.example.project.data.ledger.AddTransactionResult
import org.example.project.data.ledger.LedgerEntry
import org.example.project.util.UserFacingException

/**
 * Talks to the household sheet through the Apps Script gateway (apps-script/sheets-gateway.gs).
 *
 * Every request carries the caller's Firebase ID token; the gateway verifies it server-side and
 * checks the uid against its allow-list, so the spreadsheet itself stays private and the app
 * ships no Sheets API key.
 *
 * Requests are `POST` with a `text/plain` JSON body: that's a CORS "simple request", so browsers
 * send it without a preflight (Apps Script can't answer one). Works on every platform — see
 * [call] for the redirect Apps Script replies with.
 */
class SheetsGatewayClient(
    private val idToken: suspend () -> String?,
    private val gatewayUrl: () -> String = { ConfigManager.getConfig().sheetsGatewayUrl },
    private val http: HttpClient = defaultHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Every row of the ledger tab as displayed text, header row included. */
    suspend fun readRows(): List<List<String>> {
        val reply = call("list", emptyMap())
        return (reply["rows"] as? JsonArray).orEmpty().map { row ->
            row.jsonArray.map { cell -> cell.jsonPrimitive.contentOrNull.orEmpty() }
        }
    }

    /** Appends one row; [fields] are the schema's column values (see the gateway script). */
    suspend fun append(fields: Map<String, Any>): AddTransactionResult = try {
        call("append", fields)
        AddTransactionResult(success = true)
    } catch (e: UserFacingException) {
        AddTransactionResult(success = false, errorMessage = e.message)
    }

    /**
     * Deletes the sheet row [entry] was read from. The gateway re-checks that the row still holds
     * this entry's date and description first, so if the sheet was edited since the read (rows
     * shifted) nothing is deleted and the user is asked to refresh.
     */
    suspend fun deleteRow(entry: LedgerEntry) {
        val row = entry.id.toIntOrNull()?.takeIf { it >= 2 }
            ?: throw UserFacingException("This transaction can't be deleted.")
        call("delete", mapOf("row" to row, "expectDate" to entry.date, "expectDescription" to entry.description))
    }

    private suspend fun call(action: String, fields: Map<String, Any>): JsonObject {
        val url = gatewayUrl()
        if (url.isBlank()) throw UserFacingException("The shared sheet isn't configured on this build.")
        val token = idToken() ?: throw UserFacingException("Please sign in again.")

        val body = JsonObject(
            fields.mapValues { (_, value) -> value.toJson() } +
                mapOf("action" to JsonPrimitive(action), "idToken" to JsonPrimitive(token))
        ).toString()

        val response = try {
            val posted = http.post(url) { setBody(TextContent(body, ContentType.Text.Plain)) }
            // Apps Script answers a POST with a 302 to a one-time result URL. Browsers follow it
            // (as a GET) on their own; Ktor on Android/iOS/desktop doesn't follow redirects for
            // POST, so fetch the result ourselves. The script has already run at this point.
            val location = posted.headers[HttpHeaders.Location]
            if (posted.status.value in 301..303 && location != null) http.get(location) else posted
        } catch (e: Exception) {
            throw UserFacingException("Couldn't reach the shared sheet. Check your connection and try again.")
        }
        val text = response.bodyAsText()
        val reply = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (!response.status.isSuccess() || reply == null) {
            throw UserFacingException("The shared sheet returned an unexpected response. Please try again.")
        }
        if (reply["success"]?.jsonPrimitive?.booleanOrNull != true) {
            val error = reply["error"]?.jsonPrimitive?.contentOrNull
            throw UserFacingException(
                when (error) {
                    "Unauthorized" -> "This account doesn't have access to the shared sheet."
                    "Row changed" -> "The sheet changed since it was loaded. Refresh and try again."
                    else -> "The shared sheet rejected the request: ${error ?: "unknown error"}"
                }
            )
        }
        return reply
    }

    private fun Any.toJson(): JsonElement = when (this) {
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> JsonNull
    }
}

/** Pairs each data row with its 1-based sheet row number (the header is row 1). */
internal fun List<List<String>>.withSheetRowNumbers(): List<Pair<Int, List<String>>> =
    drop(1).mapIndexed { index, row -> (index + 2) to row }

/** Parses a sheet money cell like "₱2,950" or "-₱1,250"; blank/garbage reads as 0. */
internal fun parseSheetAmount(raw: String?): Double =
    raw?.replace("₱", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
