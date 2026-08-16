@file:OptIn(ExperimentalWasmJsInterop::class, kotlin.time.ExperimentalTime::class)

package org.example.project.data.lists

import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.example.project.config.ConfigManager
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.time.Clock

actual fun createUserListStore(): UserListStore = FirebaseUserListStore()

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
private fun dbGet(configJson: String, uid: String, docId: String): Promise<JsString> =
    js("window.__financeDb.get(configJson, uid, docId)")
private fun dbSet(configJson: String, uid: String, docId: String, dataJson: String): Promise<JsString> =
    js("window.__financeDb.set(configJson, uid, docId, dataJson)")

/**
 * Cloud Firestore list store for the deployed web target. Reads/writes a single small doc at
 * `users/{uid}/settings/{listId}` shaped `{ items: [...], updatedAt }` — same bridge and doc
 * family as [org.example.project.data.budget.FirebaseBudgetStore], keyed by a different docId.
 */
internal class FirebaseUserListStore : UserListStore {

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

    override suspend fun load(uid: String, listId: String): List<String>? {
        val raw: JsString = dbGet(configJson(), uid, listId).await()
        val root = json.parseToJsonElement(raw.toString()).jsonObject
        // Absent "items" means no doc has ever been saved -> null tells the caller to seed
        // defaults. An empty array means the user saved a deliberately-cleared list.
        val items = root["items"]?.jsonArray ?: return null
        return items.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
    }

    override suspend fun save(uid: String, listId: String, items: List<String>) {
        val payload = buildJsonObject {
            put("items", buildJsonArray { items.forEach { add(it) } })
            put("updatedAt", Clock.System.now().toEpochMilliseconds())
        }
        // Explicit type: Promise<T>.await() can't infer T when the result is unused (see auth bridge).
        val ignored: JsString = dbSet(configJson(), uid, listId, payload.toString()).await()
    }
}
