package org.example.project.config

/**
 * Typed accessors over generated BuildConfig fields.
 * Values come from `local.properties` at build time (see build.gradle.kts).
 */
object ApiConfig {
    // Read path (Google Sheets API)
    val SPREADSHEET_ID = BuildConfig.SPREADSHEET_ID
    val API_KEY = BuildConfig.GOOGLE_API_KEY
    val SHEET_RANGE = BuildConfig.SHEET_RANGE

    // Write path (Google Apps Script)
    val SCRIPT_URL = BuildConfig.SCRIPT_URL
    val WRITE_SPREADSHEET_ID = BuildConfig.WRITE_SPREADSHEET_ID
    val WRITE_SCRIPT_URL = BuildConfig.WRITE_SCRIPT_URL

    // Schema variant for this fork: "tracker_1" | "tracker_2"
    val SHEET_SCHEMA = BuildConfig.SHEET_SCHEMA

    // AI chat (Ollama)
    val OLLAMA_URL = BuildConfig.OLLAMA_URL
    val OLLAMA_MODEL = BuildConfig.OLLAMA_MODEL
}
