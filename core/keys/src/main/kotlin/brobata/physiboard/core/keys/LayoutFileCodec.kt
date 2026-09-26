package brobata.physiboard.core.keys

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Parses a custom layout file: SS9.1's JSON shape ("optional `name` and `description` strings and
 * a `mappings` object", each entry `{"lowercase", "uppercase", "multiTapEnabled", "taps"}"). Pure
 * (no android import, no file I/O): the "Import from file" flow (SS9.3) reads the picked
 * document's bytes and hands the text here; `:app` owns the document picker and the
 * `files/keyboard_layouts/<name>.json` write.
 */
object LayoutFileCodec {

    /** SS9.1: "the base layout is the identity map (a to z, A to Z on the 26 letter keys)", the fallback when a layout cannot be loaded at all. */
    val IDENTITY_LAYOUT: LayoutMap = LayoutMap(
        ('A'..'Z').associate { letter -> KeyId.Letter(letter) to LetterEntry(lowercase = letter.lowercaseChar().toString(), uppercase = letter.toString()) },
    )

    /** One successfully parsed layout file: SS9.3's "the name is the file's own `name` field" reads [name] for that; [description] shows on the Keyboard Layout screen's row (SS9.6). */
    data class ParsedLayout(val name: String?, val description: String?, val layout: LayoutMap)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** The 26 letter key names a layout file recognises (SS9.1: "letters, digits and the punctuation keys"), plus digits and punctuation, matching SS3.1's own vocabulary. */
    private fun keyIdForName(name: String): KeyId? {
        if (name.length == "KEYCODE_A".length && name.startsWith("KEYCODE_")) {
            val c = name.last()
            if (c in 'A'..'Z') return KeyId.Letter(c)
            if (c in '0'..'9') return KeyId.Digit(c)
        }
        return when (name) {
            "KEYCODE_GRAVE" -> KeyId.Punctuation(PunctuationKey.GRAVE)
            "KEYCODE_MINUS" -> KeyId.Punctuation(PunctuationKey.MINUS)
            "KEYCODE_EQUALS" -> KeyId.Punctuation(PunctuationKey.EQUALS)
            "KEYCODE_LEFT_BRACKET" -> KeyId.Punctuation(PunctuationKey.LEFT_BRACKET)
            "KEYCODE_RIGHT_BRACKET" -> KeyId.Punctuation(PunctuationKey.RIGHT_BRACKET)
            "KEYCODE_BACKSLASH" -> KeyId.Punctuation(PunctuationKey.BACKSLASH)
            "KEYCODE_SEMICOLON" -> KeyId.Punctuation(PunctuationKey.SEMICOLON)
            "KEYCODE_APOSTROPHE" -> KeyId.Punctuation(PunctuationKey.APOSTROPHE)
            "KEYCODE_COMMA" -> KeyId.Punctuation(PunctuationKey.COMMA)
            "KEYCODE_PERIOD" -> KeyId.Punctuation(PunctuationKey.PERIOD)
            "KEYCODE_SLASH" -> KeyId.Punctuation(PunctuationKey.SLASH)
            // SS9.1: "not KEYCODE_CTRL_LEFT or the Minimal Phone keys"; any other unrecognised name is skipped too.
            else -> null
        }
    }

    /**
     * SS9.1's per-entry rules: "entries missing `lowercase` or `uppercase` are dropped"; "`taps`
     * entries with both strings empty are dropped"; "multi-tap is honoured only when
     * `multiTapEnabled` is true and at least two taps survive, otherwise the entry is
     * single-character". Returns `null` for a dropped entry.
     */
    private fun decodeEntry(obj: JsonObject): LetterEntry? {
        val lowercase = (obj["lowercase"] as? JsonPrimitive)?.contentOrNull
        val uppercase = (obj["uppercase"] as? JsonPrimitive)?.contentOrNull
        if (lowercase == null || uppercase == null) return null
        val multiTapEnabled = (obj["multiTapEnabled"] as? JsonPrimitive)?.booleanOrNull ?: false
        val rawTaps = (obj["taps"] as? JsonArray).orEmpty()
        val survivingTaps = rawTaps.mapNotNull { element ->
            val tapObj = element as? JsonObject ?: return@mapNotNull null
            val tapLower = (tapObj["lowercase"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val tapUpper = (tapObj["uppercase"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (tapLower.isEmpty() && tapUpper.isEmpty()) null else Tap(tapLower, tapUpper)
        }
        val taps = if (multiTapEnabled && survivingTaps.size >= 2) survivingTaps else emptyList()
        return LetterEntry(lowercase = lowercase, uppercase = uppercase, taps = taps)
    }

    /**
     * SS9.1: "Files with no parsable `mappings` are rejected." Returns `null` for unreadable JSON,
     * a missing/malformed `mappings` object, or a `mappings` object with no entry that survives
     * [decodeEntry] and [keyIdForName].
     */
    fun decode(text: String?): ParsedLayout? {
        if (text.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val mappings = root["mappings"] as? JsonObject ?: return null
        val entries = LinkedHashMap<KeyId, LetterEntry>()
        for ((keyName, value) in mappings) {
            val keyId = keyIdForName(keyName) ?: continue
            val obj = value as? JsonObject ?: continue
            val entry = decodeEntry(obj) ?: continue
            entries[keyId] = entry
        }
        if (entries.isEmpty()) return null
        val name = (root["name"] as? JsonPrimitive)?.contentOrNull
        val description = (root["description"] as? JsonPrimitive)?.contentOrNull
        return ParsedLayout(name = name, description = description, layout = LayoutMap(entries))
    }
}
