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
    const val OLLAMA_URL = "BUILD_TIME_OLLAMA_URL"
    const val OLLAMA_MODEL = "BUILD_TIME_OLLAMA_MODEL"
    
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

    fun getOllamaUrl(): String {
        return if (OLLAMA_URL == "BUILD_TIME_OLLAMA_URL") {
            ApiConfig.OLLAMA_URL // Use BuildConfig value from local.properties
        } else {
            OLLAMA_URL
        }
    }

    fun getOllamaModel(): String {
        return if (OLLAMA_MODEL == "BUILD_TIME_OLLAMA_MODEL") {
            "llama3.1:8b" // Development fallback
        } else {
            OLLAMA_MODEL
        }
    }
}