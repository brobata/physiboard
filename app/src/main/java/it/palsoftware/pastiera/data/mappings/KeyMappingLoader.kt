package it.palsoftware.pastiera.data.mappings

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SymPagesConfig
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import org.json.JSONObject
import java.io.InputStream

/**
 * Helper for loading key mappings from JSON files.
 */
object KeyMappingLoader {
    private const val TAG = "KeyMappingLoader"

    fun getDeviceName(context: Context? = null): String {
        if (context != null) {
            val manualOverride = SettingsManager.getPhysicalKeyboardProfileOverride(context)
            if (manualOverride != "auto") {
                return manualOverride
            }
        }
        return DeviceSpecific.physicalKeyboardName()
    }

    private val keyCodeMap = mapOf(
        "KEYCODE_Q" to KeyEvent.KEYCODE_Q,
        "KEYCODE_W" to KeyEvent.KEYCODE_W,
        "KEYCODE_E" to KeyEvent.KEYCODE_E,
        "KEYCODE_R" to KeyEvent.KEYCODE_R,
        "KEYCODE_T" to KeyEvent.KEYCODE_T,
        "KEYCODE_Y" to KeyEvent.KEYCODE_Y,
        "KEYCODE_U" to KeyEvent.KEYCODE_U,
        "KEYCODE_I" to KeyEvent.KEYCODE_I,
        "KEYCODE_O" to KeyEvent.KEYCODE_O,
        "KEYCODE_P" to KeyEvent.KEYCODE_P,
        "KEYCODE_A" to KeyEvent.KEYCODE_A,
        "KEYCODE_S" to KeyEvent.KEYCODE_S,
        "KEYCODE_D" to KeyEvent.KEYCODE_D,
        "KEYCODE_F" to KeyEvent.KEYCODE_F,
        "KEYCODE_G" to KeyEvent.KEYCODE_G,
        "KEYCODE_H" to KeyEvent.KEYCODE_H,
        "KEYCODE_J" to KeyEvent.KEYCODE_J,
        "KEYCODE_K" to KeyEvent.KEYCODE_K,
        "KEYCODE_L" to KeyEvent.KEYCODE_L,
        "KEYCODE_Z" to KeyEvent.KEYCODE_Z,
        "KEYCODE_X" to KeyEvent.KEYCODE_X,
        "KEYCODE_C" to KeyEvent.KEYCODE_C,
        "KEYCODE_V" to KeyEvent.KEYCODE_V,
        "KEYCODE_B" to KeyEvent.KEYCODE_B,
        "KEYCODE_N" to KeyEvent.KEYCODE_N,
        "KEYCODE_M" to KeyEvent.KEYCODE_M,
        "KEYCODE_1" to KeyEvent.KEYCODE_1,
        "KEYCODE_2" to KeyEvent.KEYCODE_2,
        "KEYCODE_3" to KeyEvent.KEYCODE_3,
        "KEYCODE_4" to KeyEvent.KEYCODE_4,
        "KEYCODE_5" to KeyEvent.KEYCODE_5,
        "KEYCODE_6" to KeyEvent.KEYCODE_6,
        "KEYCODE_7" to KeyEvent.KEYCODE_7,
        "KEYCODE_8" to KeyEvent.KEYCODE_8,
        "KEYCODE_9" to KeyEvent.KEYCODE_9,
        "KEYCODE_0" to KeyEvent.KEYCODE_0,
        "KEYCODE_GRAVE" to KeyEvent.KEYCODE_GRAVE,
        "KEYCODE_MINUS" to KeyEvent.KEYCODE_MINUS,
        "KEYCODE_EQUALS" to KeyEvent.KEYCODE_EQUALS,
        "KEYCODE_LEFT_BRACKET" to KeyEvent.KEYCODE_LEFT_BRACKET,
        "KEYCODE_RIGHT_BRACKET" to KeyEvent.KEYCODE_RIGHT_BRACKET,
        "KEYCODE_SEMICOLON" to KeyEvent.KEYCODE_SEMICOLON,
        "KEYCODE_APOSTROPHE" to KeyEvent.KEYCODE_APOSTROPHE,
        "KEYCODE_COMMA" to KeyEvent.KEYCODE_COMMA,
        "KEYCODE_PERIOD" to KeyEvent.KEYCODE_PERIOD,
        "KEYCODE_SLASH" to KeyEvent.KEYCODE_SLASH,
        // Minimal Phone (MP01) custom keycodes
        "KEYCODE_EM" to 666,  // Emoji key
        "KEYCODE_MIC" to 667  // Mic key
    )

    fun loadAltKeyMappings(assets: AssetManager, context: Context? = null): Map<Int, String> {
        if (context != null) {
            return loadConfiguredAltMappings(assets, context, virtualDefault = false)
        }
        return loadDeviceAltMappings(assets, null, null)
    }

    fun loadDeviceSymKeyMappings(assets: AssetManager, context: Context): Map<Int, String> =
        loadDeviceAltMappings(assets, context, null)

    private fun loadDeviceAltMappings(
        assets: AssetManager,
        context: Context?,
        profileOverride: String?
    ): Map<Int, String> {
        val deviceName = profileOverride ?: getDeviceName(context)
        val candidateDeviceNames = if (deviceName == "unknown") {
            listOf("titan2")
        } else {
            listOf(deviceName)
        }

        for (candidateDeviceName in candidateDeviceNames) {
            try {
                val filePath = "devices/$candidateDeviceName/alt_key_mappings.json"
                val altKeyMap = loadStringMappings(assets, filePath).toMutableMap()
                applyCurrencySymbolOverride(altKeyMap, context)
                Log.d(TAG, "Loaded Alt mappings for device: $candidateDeviceName")
                return altKeyMap
            } catch (e: Exception) {
                Log.w(TAG, "Error loading Alt mappings for device: $candidateDeviceName", e)
            }
        }

        try {
            val filePath = "devices/titan2/alt_key_mappings.json"
            val altKeyMap = loadStringMappings(assets, filePath).toMutableMap()
            applyCurrencySymbolOverride(altKeyMap, context)
            Log.d(TAG, "Loaded fallback Alt mappings for device: titan2")
            return altKeyMap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Alt mappings", e)
            return mapOf(
                KeyEvent.KEYCODE_T to "(",
                KeyEvent.KEYCODE_Y to ")"
            )
        }
    }

    /**
     * The on-screen Device SYM layer is its own input source. Keep its default mapping independent
     * from whichever built-in or accessory keyboard profile happens to be active.
     */
    fun loadVirtualAltKeyMappings(assets: AssetManager): Map<Int, String> {
        return try {
            loadStringMappings(assets, "common/alt/virtual_alt_key_mappings.json")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading virtual Alt mappings", e)
            emptyMap()
        }
    }

    fun loadVirtualAltKeyMappings(assets: AssetManager, context: Context): Map<Int, String> =
        loadConfiguredAltMappings(assets, context, virtualDefault = true)

    private fun loadConfiguredAltMappings(
        assets: AssetManager,
        context: Context,
        virtualDefault: Boolean
    ): Map<Int, String> {
        val configured = SettingsManager.getAltCharacterLayerBinding(context)
        val binding = if (configured == "first") {
            SettingsManager.getSymPagesConfig(context).firstKeyLayer()
        } else {
            configured
        }
        return when {
            binding == SymPagesConfig.PAGE_EMOJI || binding == "emoji" ->
                SettingsManager.getSymMappings(context).takeIf { it.isNotEmpty() }
                    ?: loadSymKeyMappings(assets)
            binding == SymPagesConfig.PAGE_SYMBOLS || binding == "symbols" ->
                SettingsManager.getSymMappingsPage2(context).takeIf { it.isNotEmpty() }
                    ?: loadSymKeyMappingsPage2(assets)
            binding == SymPagesConfig.PAGE_DEVICE || binding == "device:auto" ->
                if (virtualDefault) loadVirtualAltKeyMappings(assets)
                else loadDeviceAltMappings(assets, context, null)
            binding.startsWith("device:") ->
                loadDeviceAltMappings(assets, context, binding.removePrefix("device:"))
            else -> if (virtualDefault) loadVirtualAltKeyMappings(assets)
                else loadDeviceAltMappings(assets, context, null)
        }
    }

    private fun loadStringMappings(assets: AssetManager, filePath: String): Map<Int, String> {
        val jsonString = assets.open(filePath).bufferedReader().use { it.readText() }
        val mappingsObject = JSONObject(jsonString).getJSONObject("mappings")
        val mappings = mutableMapOf<Int, String>()
        val keys = mappingsObject.keys()
        while (keys.hasNext()) {
            val keyName = keys.next()
            val keyCode = keyCodeMap[keyName] ?: continue
            mappings[keyCode] = mappingsObject.getString(keyName)
        }
        return mappings
    }

    private fun applyCurrencySymbolOverride(altKeyMap: MutableMap<Int, String>, context: Context?) {
        if (context == null || !altKeyMap.containsKey(KeyEvent.KEYCODE_GRAVE)) {
            return
        }
        altKeyMap[KeyEvent.KEYCODE_GRAVE] = SettingsManager.getPhysicalKeyboardCurrencySymbol(context)
    }

    fun loadSymKeyMappings(assets: AssetManager): Map<Int, String> {
        val symKeyMap = mutableMapOf<Int, String>()
        try {
            val filePath = "common/sym/sym_key_mappings.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                if (keyCode != null) {
                    // Support both String and Object format for backward compatibility.
                    val value = mappingsObject.get(keyName)
                    val emoji = when (value) {
                        is String -> value
                        is JSONObject -> value.optString("lowercase", "")
                        else -> ""
                    }
                    if (emoji.isNotEmpty()) {
                        symKeyMap[keyCode] = emoji
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading SYM mappings", e)
            symKeyMap[KeyEvent.KEYCODE_Q] = "😀"
            symKeyMap[KeyEvent.KEYCODE_W] = "😂"
        }
        return symKeyMap
    }

    fun loadSymKeyMappingsPage2(assets: AssetManager): Map<Int, String> {
        val symKeyMap = mutableMapOf<Int, String>()
        try {
            val filePath = "common/sym/sym_key_mappings_page2.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                if (keyCode != null) {
                    // Support both String and Object format for backward compatibility.
                    val value = mappingsObject.get(keyName)
                    val character = when (value) {
                        is String -> value
                        is JSONObject -> value.optString("lowercase", "")
                        else -> ""
                    }
                    if (character.isNotEmpty()) {
                        symKeyMap[keyCode] = character
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading SYM page 2 mappings", e)
        }
        return symKeyMap
    }

    /**
     * Loads uppercase/shifted Sym key mappings from JSON.
     * Only entries with explicit "uppercase" definitions are returned.
     */
    fun loadSymKeyMappingsUppercase(assets: AssetManager): Map<Int, String> {
        val symKeyMap = mutableMapOf<Int, String>()
        try {
            val filePath = "common/sym/sym_key_mappings.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                if (keyCode != null) {
                    val value = mappingsObject.get(keyName)
                    if (value is JSONObject) {
                        val uppercase = value.optString("uppercase", "")
                        if (uppercase.isNotEmpty()) {
                            symKeyMap[keyCode] = uppercase
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uppercase SYM mappings", e)
        }
        return symKeyMap
    }

    /**
     * Loads uppercase/shifted Sym key mappings for Page 2 from JSON.
     * Only entries with explicit "uppercase" definitions are returned.
     */
    fun loadSymKeyMappingsPage2Uppercase(assets: AssetManager): Map<Int, String> {
        val symKeyMap = mutableMapOf<Int, String>()
        try {
            val filePath = "common/sym/sym_key_mappings_page2.json"
            val inputStream: InputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                if (keyCode != null) {
                    val value = mappingsObject.get(keyName)
                    if (value is JSONObject) {
                        val uppercase = value.optString("uppercase", "")
                        if (uppercase.isNotEmpty()) {
                            symKeyMap[keyCode] = uppercase
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uppercase SYM page 2 mappings", e)
        }
        return symKeyMap
    }

    data class CtrlMapping(val type: String, val value: String)

    fun loadCtrlKeyMappings(assets: AssetManager, context: Context? = null): Map<Int, CtrlMapping> {
        val ctrlKeyMap = mutableMapOf<Int, CtrlMapping>()
        try {
            val jsonString = if (context != null) {
                val customFile = SettingsManager.getNavModeMappingsFile(context)
                if (customFile.exists()) {
                    try {
                        customFile.readText()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading custom nav mode mappings file, falling back to assets", e)
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            } ?: run {
                val filePath = "common/ctrl/ctrl_key_mappings.json"
                val inputStream: InputStream = assets.open(filePath)
                inputStream.bufferedReader().use { it.readText() }
            }

            val jsonObject = JSONObject(jsonString)
            val mappingsObject = jsonObject.getJSONObject("mappings")

            val specialKeyCodeMap = mapOf(
                "DPAD_UP" to KeyEvent.KEYCODE_DPAD_UP,
                "DPAD_DOWN" to KeyEvent.KEYCODE_DPAD_DOWN,
                "DPAD_LEFT" to KeyEvent.KEYCODE_DPAD_LEFT,
                "DPAD_RIGHT" to KeyEvent.KEYCODE_DPAD_RIGHT,
                "DPAD_CENTER" to KeyEvent.KEYCODE_DPAD_CENTER,
                "TAB" to KeyEvent.KEYCODE_TAB,
                "MOVE_HOME" to KeyEvent.KEYCODE_MOVE_HOME,
                "MOVE_END" to KeyEvent.KEYCODE_MOVE_END,
                "PAGE_UP" to KeyEvent.KEYCODE_PAGE_UP,
                "PAGE_DOWN" to KeyEvent.KEYCODE_PAGE_DOWN,
                "ESCAPE" to KeyEvent.KEYCODE_ESCAPE,
                "FORWARD_DEL" to KeyEvent.KEYCODE_FORWARD_DEL
            )

            val keys = mappingsObject.keys()
            while (keys.hasNext()) {
                val keyName = keys.next()
                val keyCode = keyCodeMap[keyName]
                val mappingObject = mappingsObject.getJSONObject(keyName)
                val type = mappingObject.getString("type")

                if (keyCode != null) {
                    when (type) {
                        "action" -> {
                            val action = mappingObject.getString("action")
                            ctrlKeyMap[keyCode] = CtrlMapping("action", action)
                        }
                        "keycode" -> {
                            val keycodeName = mappingObject.getString("keycode")
                            val mappedKeyCode = specialKeyCodeMap[keycodeName]
                            if (mappedKeyCode != null) {
                                ctrlKeyMap[keyCode] = CtrlMapping("keycode", keycodeName)
                            }
                        }
                        "native_ctrl" -> ctrlKeyMap[keyCode] = CtrlMapping("native_ctrl", "")
                        "command" -> {
                            val commandId = mappingObject.getString("command")
                            ctrlKeyMap[keyCode] = CtrlMapping("command", commandId)
                        }
                        "none" -> Unit
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Ctrl mappings", e)
            ctrlKeyMap[KeyEvent.KEYCODE_C] = CtrlMapping("action", "copy")
            ctrlKeyMap[KeyEvent.KEYCODE_V] = CtrlMapping("action", "paste")
            ctrlKeyMap[KeyEvent.KEYCODE_X] = CtrlMapping("action", "cut")
            ctrlKeyMap[KeyEvent.KEYCODE_Z] = CtrlMapping("action", "undo")
            ctrlKeyMap[KeyEvent.KEYCODE_E] = CtrlMapping("keycode", "DPAD_UP")
            ctrlKeyMap[KeyEvent.KEYCODE_S] = CtrlMapping("keycode", "DPAD_DOWN")
            ctrlKeyMap[KeyEvent.KEYCODE_D] = CtrlMapping("keycode", "DPAD_LEFT")
            ctrlKeyMap[KeyEvent.KEYCODE_F] = CtrlMapping("keycode", "DPAD_RIGHT")
            ctrlKeyMap[KeyEvent.KEYCODE_N] = CtrlMapping("action", "move_word_left")
            ctrlKeyMap[KeyEvent.KEYCODE_M] = CtrlMapping("action", "move_word_right")
            ctrlKeyMap[KeyEvent.KEYCODE_U] = CtrlMapping("action", "expand_selection_word_left")
            ctrlKeyMap[KeyEvent.KEYCODE_I] = CtrlMapping("action", "expand_selection_word_right")
        }
        return ctrlKeyMap
    }
}
