package org.example.project.data.firestore

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Converts between plain Kotlin values and the Firestore REST `Value` encoding
 * (`{"stringValue": "…"}`, `{"doubleValue": 1.5}`, …).
 *
 * Supported Kotlin types: String, Double/Float, Int/Long, Boolean, null, List<*> and Map<String, *>.
 * Decoding yields the same set (integers as Long, doubles as Double).
 */
internal object FirestoreValues {

    fun encodeFields(fields: Map<String, Any?>): JsonObject = buildJsonObject {
        fields.forEach { (name, value) -> put(name, encode(value)) }
    }

    fun decodeFields(fields: JsonObject?): Map<String, Any?> =
        fields?.mapValues { (_, value) -> decode(value.jsonObject) }.orEmpty()

    fun encode(value: Any?): JsonObject = buildJsonObject {
        when (value) {
            null -> put("nullValue", JsonPrimitive(null as String?))
            is String -> put("stringValue", value)
            is Boolean -> put("booleanValue", value)
            // Firestore's REST API carries 64-bit integers as strings.
            is Int, is Long -> put("integerValue", value.toString())
            is Double -> put("doubleValue", value)
            is Float -> put("doubleValue", value.toDouble())
            is List<*> -> put("arrayValue", buildJsonObject {
                put("values", buildJsonArray { value.forEach { add(encode(it)) } })
            })
            is Map<*, *> -> put("mapValue", buildJsonObject {
                put("fields", encodeFields(value.entries.associate { (k, v) -> k.toString() to v }))
            })
            else -> error("Unsupported Firestore value type: ${value::class.simpleName}")
        }
    }

    fun decode(value: JsonObject): Any? {
        val (type, raw) = value.entries.firstOrNull() ?: return null
        return when (type) {
            "stringValue" -> raw.jsonPrimitive.contentOrNull
            "booleanValue" -> raw.jsonPrimitive.booleanOrNull
            "integerValue" -> raw.jsonPrimitive.contentOrNull?.toLongOrNull()
            "doubleValue" -> raw.jsonPrimitive.doubleOrNull
            "timestampValue" -> raw.jsonPrimitive.contentOrNull
            "arrayValue" -> (raw.jsonObject["values"] as? JsonArray)?.map { decode(it.jsonObject) }.orEmpty()
            "mapValue" -> decodeFields(raw.jsonObject["fields"]?.jsonObject)
            else -> null // nullValue, and types this app never writes (geo, bytes, references)
        }
    }
}

/** Numeric field as Double whether Firestore stored it as an integer or a double. */
internal fun Map<String, Any?>.number(name: String): Double? = when (val v = this[name]) {
    is Double -> v
    is Long -> v.toDouble()
    else -> null
}

internal fun Map<String, Any?>.string(name: String): String? = this[name] as? String

internal fun Map<String, Any?>.boolean(name: String): Boolean? = this[name] as? Boolean
