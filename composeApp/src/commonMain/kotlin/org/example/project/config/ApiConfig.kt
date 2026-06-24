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
    val BUDGET_RANGE = BuildConfig.BUDGET_RANGE
    val SUMMARY_TREND = BuildConfig.SUMMARY_TREND

    // Write path (Google Apps Script)
    val SCRIPT_URL = BuildConfig.SCRIPT_URL
    val WRITE_SPREADSHEET_ID = BuildConfig.WRITE_SPREADSHEET_ID
    val WRITE_SCRIPT_URL = BuildConfig.WRITE_SCRIPT_URL

    // Schema variant for this fork: "tracker_1" | "tracker_2"
    val SHEET_SCHEMA = BuildConfig.SHEET_SCHEMA

    // AI chat (Firebase AI Logic — primary provider, web)
    val FIREBASE_API_KEY = BuildConfig.FIREBASE_API_KEY
    val FIREBASE_AUTH_DOMAIN = BuildConfig.FIREBASE_AUTH_DOMAIN
    val FIREBASE_PROJECT_ID = BuildConfig.FIREBASE_PROJECT_ID
    val FIREBASE_STORAGE_BUCKET = BuildConfig.FIREBASE_STORAGE_BUCKET
    val FIREBASE_MESSAGING_SENDER_ID = BuildConfig.FIREBASE_MESSAGING_SENDER_ID
    val FIREBASE_APP_ID = BuildConfig.FIREBASE_APP_ID
    val GEMINI_MODEL = BuildConfig.GEMINI_MODEL
}
