package org.example.project.config

object ApiConfig {
    // For reading data (Google Sheets API)
    val SPREADSHEET_ID = BuildConfig.SPREADSHEET_ID
    val API_KEY = BuildConfig.GOOGLE_API_KEY
    val SHEET_RANGE = BuildConfig.SHEET_RANGE
    
    // For writing data (Google Apps Script)
    val SCRIPT_URL = BuildConfig.SCRIPT_URL
}