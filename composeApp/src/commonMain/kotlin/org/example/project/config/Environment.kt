package org.example.project.config

/**
 * Environment configuration that can be injected at build time
 * This allows us to keep sensitive data out of source code
 */
object Environment {
    // These will be replaced at build time with actual values
    const val SPREADSHEET_ID = "BUILD_TIME_SPREADSHEET_ID"
    const val API_KEY = "BUILD_TIME_API_KEY" 
    const val SCRIPT_URL = "BUILD_TIME_SCRIPT_URL"
    
    // Fallback to hardcoded values for development (will be removed in production)
    fun getSpreadsheetId(): String {
        return if (SPREADSHEET_ID == "BUILD_TIME_SPREADSHEET_ID") {
            ApiConfig.SPREADSHEET_ID // Development fallback
        } else {
            SPREADSHEET_ID
        }
    }
    
    fun getApiKey(): String {
        return if (API_KEY == "BUILD_TIME_API_KEY") {
            ApiConfig.API_KEY // Development fallback
        } else {
            API_KEY
        }
    }
    
    fun getScriptUrl(): String {
        return if (SCRIPT_URL == "BUILD_TIME_SCRIPT_URL") {
            ApiConfig.SCRIPT_URL // Development fallback
        } else {
            SCRIPT_URL
        }
    }
}