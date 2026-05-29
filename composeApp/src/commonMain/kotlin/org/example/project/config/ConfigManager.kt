package org.example.project.config

/**
 * Single source of truth for runtime configuration.
 *
 * All values originate in `local.properties` and flow through the
 * BuildConfig plugin into [ApiConfig]. To add a new key:
 *   1. add a buildConfigField in composeApp/build.gradle.kts
 *   2. expose it on [ApiConfig]
 *   3. surface it here
 *
 * See SETUP.md for the full fork-and-deploy checklist.
 */
object ConfigManager {

    data class ApiConfiguration(
        val spreadsheetId: String,
        val apiKey: String,
        val scriptUrl: String,
        val writeSpreadsheetId: String,
        val writeScriptUrl: String,
        val sheetRange: String,
        val budgetRange: String,
        val summaryRange: String,
        val sheetSchema: String,
        val ollamaUrl: String,
        val ollamaModel: String,
        // Firebase AI Logic (primary AI provider on web)
        val firebaseApiKey: String,
        val firebaseAuthDomain: String,
        val firebaseProjectId: String,
        val firebaseStorageBucket: String,
        val firebaseMessagingSenderId: String,
        val firebaseAppId: String,
        val geminiModel: String,
    ) {
        /**
         * Firebase AI Logic is usable only when the core web-app identifiers are present.
         * When false, the app skips the Gemini path and uses Ollama directly.
         */
        val isFirebaseAiConfigured: Boolean
            get() = firebaseApiKey.isNotBlank() &&
                firebaseProjectId.isNotBlank() &&
                firebaseAppId.isNotBlank()
    }

    private var override: ApiConfiguration? = null

    fun getConfig(): ApiConfiguration = override ?: defaultConfig

    private val defaultConfig: ApiConfiguration by lazy {
        ApiConfiguration(
            spreadsheetId = ApiConfig.SPREADSHEET_ID,
            apiKey = ApiConfig.API_KEY,
            scriptUrl = ApiConfig.SCRIPT_URL,
            writeSpreadsheetId = ApiConfig.WRITE_SPREADSHEET_ID,
            writeScriptUrl = ApiConfig.WRITE_SCRIPT_URL,
            sheetRange = ApiConfig.SHEET_RANGE,
            budgetRange = ApiConfig.BUDGET_RANGE,
            summaryRange = ApiConfig.SUMMARY_TREND,
            sheetSchema = ApiConfig.SHEET_SCHEMA,
            ollamaUrl = ApiConfig.OLLAMA_URL,
            ollamaModel = ApiConfig.OLLAMA_MODEL,
            firebaseApiKey = ApiConfig.FIREBASE_API_KEY,
            firebaseAuthDomain = ApiConfig.FIREBASE_AUTH_DOMAIN,
            firebaseProjectId = ApiConfig.FIREBASE_PROJECT_ID,
            firebaseStorageBucket = ApiConfig.FIREBASE_STORAGE_BUCKET,
            firebaseMessagingSenderId = ApiConfig.FIREBASE_MESSAGING_SENDER_ID,
            firebaseAppId = ApiConfig.FIREBASE_APP_ID,
            geminiModel = ApiConfig.GEMINI_MODEL,
        )
    }

    /** Test-only hook to swap in a fixture configuration. */
    fun setConfig(config: ApiConfiguration) {
        override = config
    }

    fun reset() {
        override = null
    }
}
