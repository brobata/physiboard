package brobata.physiboard.core.keys

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads and writes `ctrl_key_mappings.json`, the Fn Layer map the settings screen's 26-key grid
 * editor and the keyboard's [CtrlMappingTable] share (trackpad-caret-nav.md SS5.4, SS5.8, SS5.9;
 * keys-and-modifiers.md SS12). "Shipped... as a `mappings` object keyed by `KEYCODE_<letter>`,
 * each entry `{"type": ..}` with `keycode`, `action` or `command` as the value field." Only the
 * 26 letter keys are ever written or read; SS5.4: "Unknown key names are skipped; a `keycode`
 * entry whose value is not one of the twelve allowed names is skipped."
 */
object CtrlMappingCodec {
    /** SS5.5: the twelve keycode names the `keycode` type accepts, in the file's own vocabulary. */
    private val KEYCODE_NAMES: Map<String, ControlKey> = mapOf(
        "DPAD_UP" to ControlKey.DPAD_UP,
        "DPAD_DOWN" to ControlKey.DPAD_DOWN,
        "DPAD_LEFT" to ControlKey.DPAD_LEFT,
        "DPAD_RIGHT" to ControlKey.DPAD_RIGHT,
        "DPAD_CENTER" to ControlKey.DPAD_CENTER,
        "TAB" to ControlKey.TAB,
        "MOVE_HOME" to ControlKey.MOVE_HOME,
        "MOVE_END" to ControlKey.MOVE_END,
        "PAGE_UP" to ControlKey.PAGE_UP,
        "PAGE_DOWN" to ControlKey.PAGE_DOWN,
        "ESCAPE" to ControlKey.ESCAPE,
        "FORWARD_DEL" to ControlKey.FORWARD_DELETE,
    )
    private val KEYCODE_TO_NAME: Map<ControlKey, String> = KEYCODE_NAMES.entries.associate { (name, key) -> key to name }

    /** SS5.4/5.8: the sixteen action ids the `action` type accepts. */
    val ACTION_IDS: List<String> = listOf(
        "copy", "paste", "cut", "undo", "select_all",
        "expand_selection_left", "expand_selection_right",
        "move_word_left", "move_word_right",
        "expand_selection_word_left", "expand_selection_word_right",
        "page_start", "page_end",
        "media_play_pause", "media_previous", "media_next",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun encode(table: CtrlMappingTable): String {
        val mappings = LinkedHashMap<String, JsonObject>()
        for (letter in 'A'..'Z') {
            val mapping = table.mappingFor(KeyId.Letter(letter))
            // spec layers-sym-alt.md: a key the user switched off is stored as its own "none"
            // record. Omitting it made the key read back as its default on the next load, so
            // switching an Fn-layer key off never survived a save.
            mappings["KEYCODE_$letter"] = encodeMapping(mapping) ?: continue
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(mapOf("mappings" to JsonObject(mappings))))
    }

    private fun encodeMapping(mapping: CtrlMapping): JsonObject? = when (mapping) {
        is CtrlMapping.Keycode -> {
            val control = (mapping.key as? KeyId.Control)?.key
            val name = control?.let { KEYCODE_TO_NAME[it] } ?: return null
            JsonObject(mapOf("type" to JsonPrimitive("keycode"), "keycode" to JsonPrimitive(name)))
        }
        is CtrlMapping.NamedAction -> JsonObject(mapOf("type" to JsonPrimitive("action"), "action" to JsonPrimitive(mapping.actionId)))
        is CtrlMapping.Command -> JsonObject(mapOf("type" to JsonPrimitive("command"), "command" to JsonPrimitive(mapping.commandId)))
        CtrlMapping.NativeCtrl -> JsonObject(mapOf("type" to JsonPrimitive("native_ctrl")))
        // The decoder already reads "none"; writing it is what makes a switched-off key survive a save.
        CtrlMapping.None -> JsonObject(mapOf("type" to JsonPrimitive("none")))
    }

    /** SS5.4: "The mapping file is loaded from the private files directory when it exists, else from the assets." An unreadable file decodes to an empty table (every key `none`). */
    fun decode(text: String?): CtrlMappingTable {
        if (text.isNullOrBlank()) return CtrlMappingTable()
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return CtrlMappingTable()
        val mappings = root["mappings"] as? JsonObject ?: return CtrlMappingTable()
        val entries = LinkedHashMap<KeyId, CtrlMapping>()
        for ((keyName, value) in mappings) {
            val letter = keyName.removePrefix("KEYCODE_").singleOrNull()?.uppercaseChar() ?: continue
            if (letter !in 'A'..'Z') continue
            val obj = value as? JsonObject ?: continue
            val mapping = decodeMapping(obj) ?: continue
            entries[KeyId.Letter(letter)] = mapping
        }
        return CtrlMappingTable(entries)
    }

    private fun decodeMapping(obj: JsonObject): CtrlMapping? = when ((obj["type"] as? JsonPrimitive)?.content) {
        "keycode" -> {
            val name = (obj["keycode"] as? JsonPrimitive)?.content
            val control = KEYCODE_NAMES[name] ?: return null
            CtrlMapping.Keycode(KeyId.Control(control))
        }
        "action" -> (obj["action"] as? JsonPrimitive)?.content?.let { CtrlMapping.NamedAction(it) }
        "command" -> (obj["command"] as? JsonPrimitive)?.content?.let { CtrlMapping.Command(it) }
        "native_ctrl" -> CtrlMapping.NativeCtrl
        "none" -> CtrlMapping.None
        else -> null
    }
}
