package brobata.physiboard.core.dict

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses a bundled or custom rule-set file's JSON body into a [RuleSet]. spec:
 * autocorrect-suggestions.md SS8.1: "a JSON object whose keys are triggers and values are
 * replacements; the optional key `__name` holds a display name." Reading the asset bytes (for a
 * bundled set) or the preference string (for a custom one) is the caller's job; this is the pure
 * half so a JVM test can pin the shape without an `AssetManager` or a `SharedPreferences`.
 */
object RuleSetCodec {
    private const val NAME_KEY = "__name"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Returns null when [body] is not a JSON object at all (a missing or corrupt file); a
     * non-string value under an ordinary key is simply skipped rather than failing the whole
     * parse, since one bad row should not cost every other rule in the set.
     */
    fun parse(code: String, body: String): RuleSet? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val displayName = (root[NAME_KEY] as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifBlank { null }
        val rules = root.entries
            .filter { (key, _) -> key != NAME_KEY }
            .mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it } }
            .toMap()
        return RuleSet(code, displayName, rules)
    }
}
