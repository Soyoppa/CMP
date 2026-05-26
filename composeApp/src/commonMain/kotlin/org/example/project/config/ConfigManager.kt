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
        val sheetSchema: String,
        val ollamaUrl: String,
        val ollamaModel: String,
    )

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
            sheetSchema = ApiConfig.SHEET_SCHEMA,
            ollamaUrl = ApiConfig.OLLAMA_URL,
            ollamaModel = ApiConfig.OLLAMA_MODEL,
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
