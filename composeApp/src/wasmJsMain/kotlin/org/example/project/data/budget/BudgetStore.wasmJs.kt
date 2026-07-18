@file:OptIn(ExperimentalWasmJsInterop::class, kotlin.time.ExperimentalTime::class)

package org.example.project.data.budget

import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.project.config.ConfigManager
import org.example.project.model.CategoryGroups
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.time.Clock

actual fun createBudgetStore(): BudgetStore = FirebaseBudgetStore()

@Serializable
private data class FbConfig(
    val apiKey: String,
    val authDomain: String,
    val projectId: String,
    val storageBucket: String,
    val messagingSenderId: String,
    val appId: String,
)

// --- Bridge to firebase/firestore (window.__financeDb in index.html) ---
private fun dbGet(configJson: String, uid: String): Promise<JsString> =
    js("window.__financeDb.get(configJson, uid)")
private fun dbSet(configJson: String, uid: String, dataJson: String): Promise<JsString> =
    js("window.__financeDb.set(configJson, uid, dataJson)")

/**
 * Cloud Firestore budget store for the deployed web target. Reads/writes a single small doc
 * at `users/{uid}/settings/budget`. Only the known buckets are extracted on read; `updatedAt`
 * and any other stray fields are ignored.
 */
internal class FirebaseBudgetStore : BudgetStore {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; isLenient = true }

    private fun configJson(): String {
        val c = ConfigManager.getConfig()
        return json.encodeToString(
            FbConfig.serializer(),
            FbConfig(
                apiKey = c.firebaseApiKey,
                authDomain = c.firebaseAuthDomain,
                projectId = c.firebaseProjectId,
                storageBucket = c.firebaseStorageBucket,
                messagingSenderId = c.firebaseMessagingSenderId,
                appId = c.firebaseAppId,
            ),
        )
    }

    override suspend fun load(uid: String): Map<String, Double> {
        val raw: JsString = dbGet(configJson(), uid).await()
        val root = json.parseToJsonElement(raw.toString()).jsonObject
        val keys = CategoryGroups.buckets + CategoryGroups.OTHER
        return keys.mapNotNull { key ->
            val amount = root[key]?.jsonPrimitive?.doubleOrNull
            if (amount != null) key to amount else null
        }.toMap()
    }

    override suspend fun save(uid: String, budgets: Map<String, Double>) {
        val payload = buildJsonObject {
            budgets.forEach { (bucket, amount) -> put(bucket, amount) }
            put("updatedAt", Clock.System.now().toEpochMilliseconds())
        }
        // Explicit type: Promise<T>.await() can't infer T when the result is unused (see auth bridge).
        val ignored: JsString = dbSet(configJson(), uid, payload.toString()).await()
    }
}
