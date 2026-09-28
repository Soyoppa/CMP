@file:OptIn(ExperimentalSettingsImplementation::class)

package org.example.project.auth

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import kotlinx.serialization.json.Json

// iOS uses the Firebase Auth REST API (no CocoaPods/SPM Firebase dependency needed) with the
// credential persisted in the Keychain, so sessions survive app restarts.
actual fun createAuthProvider(): AuthProvider = RestAuthProvider(KeychainCredentialStore())

/** Stores the refresh/ID token pair in the iOS Keychain (never in NSUserDefaults). */
private class KeychainCredentialStore : AuthCredentialStore {
    private val keychain = KeychainSettings(service = "org.example.project.auth")
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): StoredAuth? =
        keychain.getStringOrNull(KEY)?.let { runCatching { json.decodeFromString(StoredAuth.serializer(), it) }.getOrNull() }

    override fun save(auth: StoredAuth?) {
        if (auth == null) keychain.remove(KEY)
        else keychain.putString(KEY, json.encodeToString(StoredAuth.serializer(), auth))
    }

    private companion object {
        const val KEY = "firebase_credential"
    }
}
