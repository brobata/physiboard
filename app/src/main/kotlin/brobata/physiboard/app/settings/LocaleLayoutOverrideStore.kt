package brobata.physiboard.app.settings

import android.content.Context
import brobata.physiboard.core.subtype.LocaleLayoutMapping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * Reads and writes `files/locale_layout_mapping.json`, the user's own override on top of
 * [LocaleLayoutMapping.BUNDLED_ASSET]. spec: dictionaries-languages.md SS10 ("Writing an
 * override": "the asset is read, every key of the existing override file is merged over it, the
 * new `L` is set, and the whole merged object is written pretty-printed"). The lookup rule itself
 * is pure and shared with `:ime` via [LocaleLayoutMapping]; this class only owns the file.
 */
class LocaleLayoutOverrideStore(private val context: Context) {
    private val file = File(context.filesDir, "locale_layout_mapping.json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

    suspend fun read(): Map<String, String> = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext emptyMap()
        val obj = runCatching { json.parseToJsonElement(file.readText()) as? JsonObject }.getOrNull() ?: return@withContext emptyMap()
        obj.entries.associate { (k, v) -> k to ((v as? JsonPrimitive)?.content.orEmpty()) }
    }

    /** SS10's "Writing an override": merges [locale] to [layoutId] over the existing file (or the bundled asset when there is none yet). */
    suspend fun setLayout(locale: String, layoutId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val merged = LocaleLayoutMapping.BUNDLED_ASSET + read() + (locale to layoutId)
            val obj = JsonObject(merged.mapValues { JsonPrimitive(it.value) })
            file.writeText(json.encodeToString(JsonObject.serializer(), obj))
            true
        }.getOrDefault(false)
    }

    /** [LocaleLayoutMapping.resolve] over this device's own override and the bundled asset. */
    suspend fun resolve(locale: String): String = LocaleLayoutMapping.resolve(locale, override = read())
}
