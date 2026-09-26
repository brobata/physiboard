package brobata.physiboard.core.dict

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads and writes the two JSON files the personal-dictionary screen and the keyboard share:
 * the personal word list (autocorrect-suggestions.md SS6.1, `{"w": word, "f": count, "u":
 * lastUsedMillis}`) and the default user word list (dictionaries-languages.md SS7,
 * `{"w": word, "f": count}`, `f` defaulting to 1 when missing). Both files are hand-parsed JSON,
 * same pattern as `:core:settings`' JsonRows, so a malformed entry is skipped rather than
 * thrown, matching every other file this codebase reads back from disk.
 *
 * SPEC GAP: 2.x stored personal words in the preference `user_dictionary_entries`; 3.0's schema
 * deliberately drops that row (`CorrectionPrefs`'s own KDoc: "owned by `:core:dict`, not a
 * setting"). Neither settings-catalog.md nor dictionaries-languages.md names a 3.0 file path for
 * it, so this module names one: `personal_dictionary.json` in the app's private files directory,
 * next to `user_defaults.json` (whose path dictionaries-languages.md SS7 does name). Both the
 * settings screen and the keyboard read this same path, which is the point of keeping the codec
 * here rather than duplicated in `:app` and `:ime`.
 */
object UserWordFileCodec {
    /** The shared file name for the personal word list (see the class KDoc's SPEC GAP). */
    const val PERSONAL_WORDS_FILE_NAME: String = "personal_dictionary.json"

    /**
     * The monitor both writers of [PERSONAL_WORDS_FILE_NAME] hold for the whole of their write:
     * `:ime`'s `UserWordFileLoader` (the strip's own add/delete-word path) and `:app`'s
     * `UserWordFileStore` (the Personal Dictionary screen's own edits). Both run in the same
     * process (no `android:process` split) on independent threads, and neither write was
     * otherwise serialized against the other: whichever `renameTo` landed last silently won,
     * discarding the other side's edit with no conflict signal to either writer. A plain JVM
     * object is enough since this codec is pure Kotlin with no coroutine dispatcher of its own to
     * coordinate through.
     */
    object PersonalDictionaryFileLock

    /** dictionaries-languages.md SS7: the default-word file, copied from the asset on first use. */
    const val DEFAULT_WORDS_FILE_NAME: String = "user_defaults.json"

    /** dictionaries-languages.md SS7: where the shipped default-word list lives as an asset. */
    const val DEFAULT_WORDS_ASSET_PATH: String = "common/dictionaries/user_defaults.json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Encodes [words] as the `[{"w":..,"f":..,"u":..}]` array autocorrect-suggestions.md SS6.1 describes. */
    fun encodePersonalWords(words: List<PersonalWord>): String {
        val array = JsonArray(
            words.map {
                JsonObject(
                    mapOf(
                        "w" to JsonPrimitive(it.word),
                        "f" to JsonPrimitive(it.frequency),
                        "u" to JsonPrimitive(it.lastUsedMillis),
                    ),
                )
            },
        )
        return json.encodeToString(JsonArray.serializer(), array)
    }

    /** Decodes a personal-word file; an unreadable or malformed file, or an unparsable entry, contributes nothing rather than throwing. */
    fun decodePersonalWords(text: String?): List<PersonalWord> {
        val array = parseArray(text) ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val word = (obj["w"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            if (word.isBlank()) return@mapNotNull null
            val frequency = (obj["f"] as? JsonPrimitive)?.intOrNull ?: 1
            val lastUsed = (obj["u"] as? JsonPrimitive)?.longOrNull ?: 0L
            PersonalWord(word, frequency, lastUsed)
        }
    }

    /** Encodes [words] as the `[{"w":..,"f":..}]` array dictionaries-languages.md SS7 describes for `user_defaults.json`. */
    fun encodeDefaultWords(words: List<WordFrequency>): String {
        val array = JsonArray(words.map { JsonObject(mapOf("w" to JsonPrimitive(it.word), "f" to JsonPrimitive(it.frequency))) })
        return json.encodeToString(JsonArray.serializer(), array)
    }

    /** Decodes a default-word file; SS7: "A default word's `f` defaults to 1 when missing." */
    fun decodeDefaultWords(text: String?): List<WordFrequency> {
        val array = parseArray(text) ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val word = (obj["w"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            if (word.isBlank()) return@mapNotNull null
            val frequency = (obj["f"] as? JsonPrimitive)?.intOrNull ?: 1
            WordFrequency(word, frequency)
        }
    }

    private fun parseArray(text: String?): JsonArray? {
        if (text.isNullOrBlank()) return null
        return runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()
    }
}

/**
 * Whether typed [text] is an acceptable new personal-dictionary word: autocorrect-suggestions.md
 * SS6.3 ("a plus button (add dialog, single line, OK enabled when non-blank)"). Kept as a pure
 * function so the screen's "OK" button and any future caller share one rule.
 */
fun isValidNewDictionaryWord(text: String): Boolean = text.trim().isNotEmpty()
