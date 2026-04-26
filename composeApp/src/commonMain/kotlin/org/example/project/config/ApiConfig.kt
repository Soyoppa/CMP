package org.example.project.config

object ApiConfig {
    // For reading data (Google Sheets API)
    val SPREADSHEET_ID = BuildConfig.SPREADSHEET_ID
    val API_KEY = BuildConfig.GOOGLE_API_KEY
    val SHEET_RANGE = BuildConfig.SHEET_RANGE

    // For writing data (Google Apps Script — separate spreadsheet)
    val SCRIPT_URL = BuildConfig.SCRIPT_URL
    val WRITE_SPREADSHEET_ID = BuildConfig.WRITE_SPREADSHEET_ID
    val WRITE_SCRIPT_URL = BuildConfig.WRITE_SCRIPT_URL

    // For AI chat (Ollama)
    val OLLAMA_URL = BuildConfig.OLLAMA_URL
    val OLLAMA_MODEL = BuildConfig.OLLAMA_MODEL
}