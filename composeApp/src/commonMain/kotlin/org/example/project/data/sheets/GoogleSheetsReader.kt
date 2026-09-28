package org.example.project.data.sheets

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.example.project.config.ConfigManager
import org.example.project.util.UserFacingException

@Serializable
private data class SheetsResponse(val values: List<List<String>>? = null)

@Serializable
private data class SheetsErrorEnvelope(val error: SheetsApiError? = null)

@Serializable
private data class SheetsApiError(val code: Int = 0, val message: String = "", val status: String = "")

private val sheetsJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Read-only access to the schema's spreadsheet via the Google Sheets API v4 (API-key auth).
 * Shared by every schema's data source so the HTTP/error handling lives in one place.
 */
class GoogleSheetsReader {
    private val client = HttpClient {
        install(ContentNegotiation) { json(sheetsJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    /**
     * All rows of [range] (A1 notation, e.g. `'Data Dump'!A:H`), header included.
     *
     * The client doesn't fail on non-2xx, and an error body like
     * `{"error":{"message":"Unable to parse range: ..."}}` would otherwise deserialize into an
     * empty, "successful" read. Such failures are raised as a [UserFacingException] instead —
     * its message is built from the API's own error text, never the request URL (which carries
     * the API key).
     */
    suspend fun readRange(range: String): List<List<String>> {
        val config = ConfigManager.getConfig()
        val response = client.get(
            "https://sheets.googleapis.com/v4/spreadsheets/${config.spreadsheetId}/values/$range"
        ) {
            parameter("key", config.apiKey)
        }
        if (!response.status.isSuccess()) {
            throw UserFacingException(sheetsErrorMessage(response.bodyAsText(), response.status.value))
        }
        return response.body<SheetsResponse>().values.orEmpty()
    }

    private fun sheetsErrorMessage(body: String, httpStatus: Int): String {
        val apiMessage = runCatching {
            sheetsJson.decodeFromString<SheetsErrorEnvelope>(body).error?.message
        }.getOrNull()
        return if (apiMessage.isNullOrBlank()) "Couldn't read the sheet (HTTP $httpStatus)."
        else "Couldn't read the sheet (HTTP $httpStatus): $apiMessage"
    }
}

/** Parses a sheet money cell like "₱2,950" or "-₱1,250"; blank/garbage reads as 0. */
internal fun parseSheetAmount(raw: String?): Double =
    raw?.replace("₱", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
