package it.palsoftware.pastiera.inputmethod.subtype

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import java.util.Locale

/**
 * Utility class for managing additional IME subtypes (custom input styles).
 * Handles parsing, validation, creation, and serialization of subtypes.
 */
object AdditionalSubtypeUtils {
    private const val TAG = "AdditionalSubtypeUtils"
    
    const val PREF_CUSTOM_INPUT_STYLES = "custom_input_styles"
    private const val EXTRA_KEY_KEYBOARD_LAYOUT_SET = "KeyboardLayoutSet"
    private const val EXTRA_KEY_ASCII_CAPABLE = "AsciiCapable"
    private const val EXTRA_KEY_EMOJI_CAPABLE = "EmojiCapable"
    private const val EXTRA_KEY_IS_ADDITIONAL_SUBTYPE = "isAdditionalSubtype"
    
    /**
     * Parses a preference string and creates an array of InputMethodSubtype objects.
     * Format: "locale:layout[:extra];locale:layout[:extra];..."
     * 
     * @param prefString The preference string containing subtype definitions
     * @param assets AssetManager to check layout availability
     * @param context Context for checking layout availability
     * @return Array of valid InputMethodSubtype objects
     */
    fun createAdditionalSubtypesArray(
        prefString: String?,
        assets: AssetManager,
        context: Context
    ): Array<InputMethodSubtype> {
        if (prefString.isNullOrBlank()) {
            return emptyArray()
        }
        
        val availableLayouts = LayoutMappingRepository.getAvailableLayouts(assets, context).toSet()
        val subtypes = mutableListOf<InputMethodSubtype>()
        
        val entries = prefString.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        
        for (entry in entries) {
            try {
                val parts = entry.split(":").map { it.trim() }
                if (parts.size < 2) {
                    Log.w(TAG, "Invalid entry format (missing locale or layout): $entry")
                    continue
                }
                
                val localeStr = parts[0]
                val layoutName = parts[1]
                val extra = if (parts.size > 2) parts[2] else ""
                
                // Validate locale
                if (!isValidLocale(localeStr)) {
                    Log.w(TAG, "Invalid locale: $localeStr")
                    continue
                }
                
                // Validate layout exists
                if (!availableLayouts.contains(layoutName)) {
                    Log.w(TAG, "Layout not available, skipping: $layoutName")
                    continue
                }
                
                // Create subtype
                val subtype = createSubtype(localeStr, layoutName, extra)
                if (subtype != null) {
                    subtypes.add(subtype)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing entry: $entry", e)
            }
        }
        
        return subtypes.toTypedArray()
    }
    
    /**
     * Creates a preference string from an array of subtypes.
     * Format: "locale:layout[:extra];locale:layout[:extra];..."
     */
    fun createPrefSubtypes(subtypeArray: Array<InputMethodSubtype>): String {
        return subtypeArray.joinToString(";") { subtype ->
            val locale = subtype.locale ?: ""
            val extraValue = subtype.extraValue ?: ""
            val layoutName = extractLayoutFromExtraValue(extraValue) ?: ""
            val otherExtras = extractOtherExtras(extraValue)
            
            if (otherExtras.isNotEmpty()) {
                "$locale:$layoutName:$otherExtras"
            } else {
                "$locale:$layoutName"
            }
        }
    }
    
    /**
     * Checks if a subtype is an additional (custom) subtype.
     */
    fun isAdditionalSubtype(subtype: InputMethodSubtype): Boolean {
        val extraValue = subtype.extraValue ?: return false
        return extraValue.contains(EXTRA_KEY_IS_ADDITIONAL_SUBTYPE)
    }
    
    /**
     * Creates an InputMethodSubtype with the specified locale and layout.
     */
    private fun createSubtype(
        localeStr: String,
        layoutName: String,
        extra: String
    ): InputMethodSubtype? {
        return try {
            // Parse locale
            val locale = parseLocale(localeStr)
            if (locale == null) {
                Log.w(TAG, "Failed to parse locale: $localeStr")
                return null
            }
            
            // Build extra value
            val extraValueBuilder = StringBuilder()
            extraValueBuilder.append("$EXTRA_KEY_KEYBOARD_LAYOUT_SET=$layoutName")
            extraValueBuilder.append(",")
            extraValueBuilder.append(EXTRA_KEY_ASCII_CAPABLE)
            extraValueBuilder.append(",")
            extraValueBuilder.append(EXTRA_KEY_EMOJI_CAPABLE)
            extraValueBuilder.append(",")
            extraValueBuilder.append(EXTRA_KEY_IS_ADDITIONAL_SUBTYPE)
            
            if (extra.isNotEmpty()) {
                extraValueBuilder.append(",")
                extraValueBuilder.append(extra)
            }
            
            val extraValue = extraValueBuilder.toString()
            
            // Get name resource ID for locale
            val nameResId = getLocaleNameResId(localeStr)
            
            // Create subtype
            InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeNameResId(nameResId)
                .setSubtypeLocale(localeStr)
                .setSubtypeMode("keyboard")
                .setSubtypeExtraValue(extraValue)
                .setIsAuxiliary(false)
                .setOverridesImplicitlyEnabledSubtype(false)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating subtype for $localeStr:$layoutName", e)
            null
        }
    }
    
    /**
     * Validates if a locale string is valid.
     */
    private fun isValidLocale(localeStr: String): Boolean {
        return try {
            parseLocale(localeStr) != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Parses a locale string (e.g., "en_US", "it_IT", "fr") into a Locale object.
     */
    private fun parseLocale(localeStr: String): Locale? {
        return try {
            val parts = localeStr.split("_")
            when (parts.size) {
                2 -> Locale(parts[0], parts[1])
                1 -> Locale(parts[0])
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the resource ID for a locale's display name.
     * Falls back to a generic name if not found.
     */
    private fun getLocaleNameResId(localeStr: String): Int {
        val langCode = localeStr.split("_")[0].lowercase()
        return when (langCode) {
            "en" -> R.string.input_method_name_en
            "it" -> R.string.input_method_name_it
            "fr" -> R.string.input_method_name_fr
            "de" -> R.string.input_method_name_de
            "pl" -> R.string.input_method_name_pl
            "es" -> R.string.input_method_name_es
            "pt" -> R.string.input_method_name_pt
            "ru" -> R.string.input_method_name_ru
            else -> R.string.input_method_name // Fallback
        }
    }
    
    /**
     * Extracts the layout name from extraValue.
     */
    private fun extractLayoutFromExtraValue(extraValue: String): String? {
        val parts = extraValue.split(",")
        for (part in parts) {
            if (part.startsWith("$EXTRA_KEY_KEYBOARD_LAYOUT_SET=")) {
                return part.substringAfter("=")
            }
        }
        return null
    }
    
    /**
     * Extracts other extras (excluding layout, ascii, emoji, isAdditionalSubtype).
     */
    private fun extractOtherExtras(extraValue: String): String {
        val parts = extraValue.split(",")
        val filtered = parts.filter { part ->
            !part.startsWith("$EXTRA_KEY_KEYBOARD_LAYOUT_SET=") &&
            part != EXTRA_KEY_ASCII_CAPABLE &&
            part != EXTRA_KEY_EMOJI_CAPABLE &&
            part != EXTRA_KEY_IS_ADDITIONAL_SUBTYPE
        }
        return filtered.joinToString(",")
    }
    
    /**
     * Finds a subtype by locale.
     */
    fun findSubtypeByLocale(
        subtypes: Array<InputMethodSubtype>,
        locale: String
    ): InputMethodSubtype? {
        return subtypes.firstOrNull { it.locale == locale }
    }
    
    /**
     * Finds a subtype by locale and keyboard layout set.
     */
    fun findSubtypeByLocaleAndKeyboardLayoutSet(
        subtypes: Array<InputMethodSubtype>,
        locale: String,
        layoutName: String
    ): InputMethodSubtype? {
        return subtypes.firstOrNull { subtype ->
            subtype.locale == locale && 
            extractLayoutFromExtraValue(subtype.extraValue ?: "") == layoutName
        }
    }
    
    /**
     * Gets the keyboard layout name from a subtype's extraValue.
     */
    fun getKeyboardLayoutFromSubtype(subtype: InputMethodSubtype): String? {
        return extractLayoutFromExtraValue(subtype.extraValue ?: "")
    }
}

