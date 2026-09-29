package org.example.project.config

/**
 * Single source of truth for runtime configuration.
 *
 * All values originate in `local.properties` and are baked into the generated [BuildConfig] by
 * composeApp/build.gradle.kts. To add a key: add a `field(...)` there, then surface it here.
 */
object ConfigManager {

    data class ApiConfiguration(
        /** Household sheet schema for accounts granted Sheets access: "tracker_1" | "tracker_2". */
        val sheetSchema: String,
        /** Apps Script Sheets gateway; blank on builds without Sheets access (e.g. store builds). */
        val sheetsGatewayUrl: String,
        // Firebase web-app config (not secret; access is enforced by Auth + Firestore rules)
        val firebaseApiKey: String,
        val firebaseAuthDomain: String,
        val firebaseProjectId: String,
        val firebaseStorageBucket: String,
        val firebaseMessagingSenderId: String,
        val firebaseAppId: String,
        val geminiModel: String,
    ) {
        /** Firebase AI Logic is usable only when the core web-app identifiers are present. */
        val isFirebaseAiConfigured: Boolean
            get() = firebaseApiKey.isNotBlank() &&
                firebaseProjectId.isNotBlank() &&
                firebaseAppId.isNotBlank()
    }

    private var override: ApiConfiguration? = null

    fun getConfig(): ApiConfiguration = override ?: defaultConfig

    private val defaultConfig: ApiConfiguration by lazy {
        ApiConfiguration(
            sheetSchema = BuildConfig.SHEET_SCHEMA,
            sheetsGatewayUrl = BuildConfig.SHEETS_GATEWAY_URL,
            firebaseApiKey = BuildConfig.FIREBASE_API_KEY,
            firebaseAuthDomain = BuildConfig.FIREBASE_AUTH_DOMAIN,
            firebaseProjectId = BuildConfig.FIREBASE_PROJECT_ID,
            firebaseStorageBucket = BuildConfig.FIREBASE_STORAGE_BUCKET,
            firebaseMessagingSenderId = BuildConfig.FIREBASE_MESSAGING_SENDER_ID,
            firebaseAppId = BuildConfig.FIREBASE_APP_ID,
            geminiModel = BuildConfig.GEMINI_MODEL,
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
