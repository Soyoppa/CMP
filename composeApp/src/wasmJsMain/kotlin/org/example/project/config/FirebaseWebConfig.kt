package org.example.project.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The Firebase web `firebaseConfig` object the JS bridges initialise the SDK with. */
@Serializable
private data class FirebaseWebConfig(
    val apiKey: String,
    val authDomain: String,
    val projectId: String,
    val storageBucket: String,
    val messagingSenderId: String,
    val appId: String,
)

/** JSON for `initializeApp(config)`, built from BuildConfig. None of these values are secret. */
internal fun firebaseWebConfigJson(): String {
    val c = ConfigManager.getConfig()
    return Json.encodeToString(
        FirebaseWebConfig.serializer(),
        FirebaseWebConfig(
            apiKey = c.firebaseApiKey,
            authDomain = c.firebaseAuthDomain,
            projectId = c.firebaseProjectId,
            storageBucket = c.firebaseStorageBucket,
            messagingSenderId = c.firebaseMessagingSenderId,
            appId = c.firebaseAppId,
        ),
    )
}
