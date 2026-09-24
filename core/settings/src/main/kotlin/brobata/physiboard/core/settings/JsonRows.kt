package brobata.physiboard.core.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The hand-rolled JSON reading the catalogue's JSON-shaped rows need. Every reader answers null
 * for a value of the wrong shape instead of throwing, which is the catalogue's rule for stored
 * JSON: "a value that does not parse reads as the default" (settings-catalog.md SS3.2).
 */
internal object JsonRows {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String?): JsonElement? {
        if (text.isNullOrBlank()) return null
        return runCatching { json.parseToJsonElement(text) }.getOrNull()
    }

    fun parseObject(text: String?): JsonObject? = parse(text) as? JsonObject
    fun parseArray(text: String?): JsonArray? = parse(text) as? JsonArray

    fun encode(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

    fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    fun JsonElement.asBoolean(): Boolean? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
    fun JsonElement.asInt(): Int? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.let { it.intOrNull ?: it.longOrNull?.toInt() }
    fun JsonElement.asDouble(): Double? = (this as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull

    /** A JSON array of strings; non-string members are skipped. */
    fun stringList(element: JsonElement?): List<String>? = (element as? JsonArray)?.mapNotNull { it.asString() }

    /** A JSON object whose values are all strings; members of another type are skipped. */
    fun stringMap(element: JsonElement?): Map<String, String>? =
        (element as? JsonObject)?.entries?.mapNotNull { (k, v) -> v.asString()?.let { k to it } }?.toMap()

    fun stringListOf(values: Iterable<String>): JsonArray = JsonArray(values.map { JsonPrimitive(it) })
    fun stringMapOf(values: Map<String, String>): JsonObject = JsonObject(values.mapValues { JsonPrimitive(it.value) })

    fun JsonObject.string(key: String): String? = this[key]?.asString()
    fun JsonObject.boolean(key: String): Boolean? = this[key]?.asBoolean()
    fun JsonObject.int(key: String): Int? = this[key]?.asInt()
    fun JsonObject.double(key: String): Double? = this[key]?.asDouble()
}
