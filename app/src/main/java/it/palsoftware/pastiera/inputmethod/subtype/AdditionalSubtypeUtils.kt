package it.palsoftware.pastiera.inputmethod.subtype

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.layout.LayoutFileStore
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService
import org.json.JSONObject
import java.util.Locale

/**
 * Utility class for managing additional IME subtypes (custom input styles).
 * Handles parsing, validation, creation, and serialization of subtypes.
 */
object AdditionalSubtypeUtils {
    private const val TAG = "AdditionalSubtypeUtils"
    
    const val PREF_CUSTOM_INPUT_STYLES = "custom_input_styles"
    private const val PREF_AUTO_ADDED_SYSTEM_LOCALES = "auto_added_system_locales"
    private const val EXTRA_KEY_KEYBOARD_LAYOUT_SET = "KeyboardLayoutSet"
    private const val EXTRA_KEY_ASCII_CAPABLE = "AsciiCapable"
    private const val EXTRA_KEY_EMOJI_CAPABLE = "EmojiCapable"
    private const val EXTRA_KEY_IS_ADDITIONAL_SUBTYPE = "isAdditionalSubtype"
    private val BASE_SUBTYPE_LOCALES = setOf(
        "en_US", "it_IT", "fr_FR", "de_DE", "pl_PL", "da_DK",
        "no_NO", "es_ES", "pt_PT", "ru_RU", "sr_RS", "uk_UA"
    )

    fun InputMethodSubtype.localeString(): String =
        languageTag.takeIf { it.isNotBlank() }
            ?: legacyLocaleString()

    fun InputMethodSubtype.languageCode(): String? =
        localeString()
            .replace('_', '-')
            .split('-')
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }

    fun localeFromSubtypeString(localeString: String): Locale {
        val normalized = localeString.replace('_', '-')
        return Locale.forLanguageTag(normalized).takeIf { it.language.isNotBlank() }
            ?: Locale.ITALIAN
    }

    @Suppress("DEPRECATION")
    private fun InputMethodSubtype.legacyLocaleString(): String = locale.orEmpty()

    @Suppress("DEPRECATION")
    fun setAdditionalInputMethodSubtypesCompat(
        imm: InputMethodManager,
        imeId: String,
        subtypes: Array<InputMethodSubtype>
    ) {
        imm.setAdditionalInputMethodSubtypes(imeId, subtypes)
    }

    @Suppress("DEPRECATION")
    fun setInputMethodAndSubtypeCompat(
        imm: InputMethodManager,
        token: android.os.IBinder,
        imeId: String,
        subtype: InputMethodSubtype
    ) {
        imm.setInputMethodAndSubtype(token, imeId, subtype)
    }
    
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

                if (isRedundantWithBaseSubtype(assets, context, localeStr, layoutName)) {
                    Log.d(TAG, "Skipping redundant custom subtype: $localeStr:$layoutName")
                    continue
                }
                
                // Create subtype
                val subtype = createSubtype(localeStr, layoutName, extra, assets, context)
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
            val locale = subtype.localeString()
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
        extra: String,
        assets: AssetManager,
        context: Context
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
            
            val supportsNameOverride = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            val builder = InputMethodSubtype.InputMethodSubtypeBuilder()
                .setSubtypeNameResId(if (supportsNameOverride) 0 else nameResId)
                .setSubtypeLocale(localeStr)
                .setLanguageTag(locale.toLanguageTag())
                .setSubtypeMode("keyboard")
                .setSubtypeExtraValue(extraValue)
                .setSubtypeId(stableSubtypeId(localeStr, layoutName))
                .setIsAuxiliary(false)
                .setOverridesImplicitlyEnabledSubtype(false)

            if (supportsNameOverride) {
                builder.setSubtypeNameOverride(
                    buildSubtypeDisplayName(context, assets, locale, localeStr, layoutName)
                )
            }

            builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating subtype for $localeStr:$layoutName", e)
            null
        }
    }

    private fun buildSubtypeDisplayName(
        context: Context,
        assets: AssetManager,
        locale: Locale,
        localeStr: String,
        layoutName: String
    ): String {
        val languageLabel = getLocaleNameResId(localeStr)
            .takeIf { it != 0 }
            ?.let(context::getString)
            ?: locale.getDisplayLanguage(context.resources.configuration.locales[0])
                .replaceFirstChar { character ->
                    if (character.isLowerCase()) character.titlecase() else character.toString()
                }
        val layoutLabel = (
            LayoutFileStore.getLayoutMetadataFromAssets(assets, layoutName)
                ?: LayoutFileStore.getLayoutMetadata(context, layoutName)
            )?.name
            ?.takeIf { it.isNotBlank() }
            ?.substringBefore(" | ")
            ?: layoutName

        return "$languageLabel · $layoutLabel"
    }

    private fun stableSubtypeId(locale: String, layout: String): Int {
        return "pastiera-subtype-v2|$locale|$layout".hashCode().takeUnless { it == 0 } ?: 1
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
            Locale.forLanguageTag(localeStr.replace('_', '-'))
                .takeIf { it.language.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the resource ID for a locale's display name.
     * Returns 0 for custom locales - Android will auto-generate the name from the locale.
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
            else -> 0 // Use 0 for custom locales - Android will auto-generate name from locale
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
        return subtypes.firstOrNull { it.localeString() == locale }
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
            subtype.localeString() == locale && 
            extractLayoutFromExtraValue(subtype.extraValue ?: "") == layoutName
        }
    }
    
    /**
     * Gets the keyboard layout name from a subtype's extraValue.
     */
    fun getKeyboardLayoutFromSubtype(subtype: InputMethodSubtype): String? {
        return extractLayoutFromExtraValue(subtype.extraValue ?: "")
    }

    fun matchesLocaleAndKeyboardLayoutSet(
        subtype: InputMethodSubtype,
        locale: String,
        layoutName: String
    ): Boolean {
        return subtype.localeString() == locale &&
            getKeyboardLayoutFromSubtype(subtype) == layoutName
    }

    private fun isRedundantWithBaseSubtype(
        assets: AssetManager,
        context: Context,
        locale: String,
        layoutName: String
    ): Boolean {
        if (locale !in BASE_SUBTYPE_LOCALES) {
            return false
        }
        return getLayoutForLocale(assets, locale, context) == layoutName
    }

    /**
     * Resolves the active keyboard layout based on the central layout mode:
     * - Auto mode: subtype layout -> locale mapping
     * - Manual mode: explicit user-selected layout from settings
     */
    fun resolveActiveLayout(
        assets: AssetManager,
        context: Context,
        subtype: InputMethodSubtype?
    ): String {
        if (!SettingsManager.isKeyboardLayoutAutoByLocale(context)) {
            return SettingsManager.getKeyboardLayout(context)
        }

        if (subtype != null) {
            val layoutFromSubtype = getKeyboardLayoutFromSubtype(subtype)
            if (!layoutFromSubtype.isNullOrEmpty()) {
                return layoutFromSubtype
            }
            val locale = subtype.localeString().ifBlank { "en_US" }
            return getLayoutForLocale(assets, locale, context)
        }

        // No subtype available: keep current selection to avoid unexpected jumps.
        return SettingsManager.getKeyboardLayout(context)
    }

    /**
     * Resolves the concrete layout declared by an input style, when present.
     * Falls back to the normal active-layout resolver for base/system subtypes.
     */
    fun resolveInputStyleLayout(
        assets: AssetManager,
        context: Context,
        subtype: InputMethodSubtype?
    ): String {
        val layoutFromSubtype = subtype?.let { getKeyboardLayoutFromSubtype(it) }
        if (!layoutFromSubtype.isNullOrEmpty()) {
            return layoutFromSubtype
        }
        return resolveActiveLayout(assets, context, subtype)
    }
    
    /**
     * Gets the default keyboard layout for a locale from the JSON mapping.
     * First checks custom file (if context provided), then falls back to assets.
     * Falls back to "qwerty" if not found.
     */
    fun getLayoutForLocale(assets: AssetManager, locale: String, context: Context? = null): String {
        val languageOnly = locale.substringBefore("_").substringBefore("-")

        // First, try custom file if context is provided
        if (context != null) {
            try {
                val customMappingFile = java.io.File(context.filesDir, "locale_layout_mapping.json")
                if (customMappingFile.exists() && customMappingFile.canRead()) {
                    val jsonString = customMappingFile.readText()
                    val json = JSONObject(jsonString)
                    if (json.has(locale)) {
                        val layout = json.getString(locale)
                        if (layout.isNotEmpty()) {
                            return layout
                        }
                    }
                    if (languageOnly.isNotEmpty() && json.has(languageOnly)) {
                        val layout = json.getString(languageOnly)
                        if (layout.isNotEmpty()) {
                            return layout
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading custom locale-layout mapping, falling back to assets", e)
            }
        }
        
        // Fallback to assets
        return try {
            assets.open("common/locale_layout_mapping.json").use { input ->
                val jsonString = input.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)
                val exact = json.optString(locale, "")
                when {
                    exact.isNotEmpty() -> exact
                    languageOnly.isNotEmpty() -> json.optString(languageOnly, "qwerty")
                    else -> "qwerty"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading layout for locale $locale, defaulting to qwerty", e)
            "qwerty"
        }
    }
    
    /**
     * Loads the set of system locales that were auto-added (no dictionary).
     */
    private fun loadAutoAddedLocales(context: Context): Set<String> {
        return SettingsManager.getPreferences(context)
            .getStringSet(PREF_AUTO_ADDED_SYSTEM_LOCALES, emptySet())
            ?: emptySet()
    }
    
    /**
     * Saves the set of system locales that were auto-added.
     */
    private fun saveAutoAddedLocales(context: Context, locales: Set<String>) {
        SettingsManager.getPreferences(context)
            .edit()
            .putStringSet(PREF_AUTO_ADDED_SYSTEM_LOCALES, locales)
            .apply()
    }
    
    /**
     * Removes only the system locales (without dictionary) that were auto-added and are no longer present in the system.
     * Does NOT remove user-created custom input styles.
     */
    fun removeSystemLocalesWithoutDictionary(context: Context) {
        try {
            val currentSystemLocales = getSystemEnabledLocales(context).toSet()
            val trackedAutoAdded = loadAutoAddedLocales(context)
            
            // Locales that were auto-added but are no longer in system
            val toRemove = trackedAutoAdded.filterNot { currentSystemLocales.contains(it) }.toSet()
            if (toRemove.isEmpty()) return
            
            val currentStyles = SettingsManager.getCustomInputStyles(context)
            if (currentStyles.isBlank()) return
            
            val entries = currentStyles.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val kept = entries.filterNot { entry ->
                val locale = entry.substringBefore(":")
                toRemove.contains(locale)
            }
            
            SettingsManager.setCustomInputStyles(context, kept.joinToString(";"))
            
            // Update tracking: keep only those still in system
            val updatedTracked = trackedAutoAdded.intersect(currentSystemLocales)
            saveAutoAddedLocales(context, updatedTracked)
            
            Log.d(TAG, "Removed ${entries.size - kept.size} auto-added system locales without dictionary (no longer in system)")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing system locales without dictionary", e)
        }
    }
    
    /**
     * Checks if a subtype should be kept based on current system locales.
     * Returns true if the subtype should be kept, false if it should be removed.
     */
    fun shouldKeepSubtype(
        subtype: InputMethodSubtype,
        currentSystemLocales: Set<String>,
        systemLanguageCodes: Set<String>
    ): Boolean {
        // Keep ALL additional (custom) subtypes. They may not be present in system locales.
        if (isAdditionalSubtype(subtype)) return true

        // For system subtypes (from method.xml), keep only if locale (or language root) is still in system.
        val subtypeLocale = subtype.localeString()
        val languageCode = subtypeLocale.split("_").first().lowercase()
        return currentSystemLocales.contains(subtypeLocale) || systemLanguageCodes.contains(languageCode)
    }

    fun shouldKeepSubtype(
        context: Context,
        assets: AssetManager,
        subtype: InputMethodSubtype,
        currentSystemLocales: Set<String>,
        systemLanguageCodes: Set<String>
    ): Boolean {
        if (!shouldKeepSubtype(subtype, currentSystemLocales, systemLanguageCodes)) {
            return false
        }
        if (isAdditionalSubtype(subtype)) {
            return true
        }

        val locale = subtype.localeString()
        val layout = getKeyboardLayoutFromSubtype(subtype)
            ?: getLayoutForLocale(assets, locale, context)
        return !SettingsManager.isSystemInputStyleHidden(context, locale, layout)
    }
    
    /**
     * Automatically adds system locales without dictionary to custom input styles.
     * This ensures that new system languages are immediately usable even if they don't have a dictionary.
     * 
     * NOTE: This function does NOT remove locales - that should be done separately via
     * removeSystemLocalesWithoutDictionary() only when system configuration changes.
     */
    fun autoAddSystemLocalesWithoutDictionary(context: Context) {
        try {
            val systemLocales = getSystemEnabledLocales(context)
            val localesWithDict = getLocalesWithDictionary(context)
            val baseSubtypesInMethodXml = BASE_SUBTYPE_LOCALES
            
            // Get current custom input styles
            val currentStyles = SettingsManager.getCustomInputStyles(context)
            val existingLocales = mutableSetOf<String>()
            if (currentStyles.isNotBlank()) {
                currentStyles.split(";").forEach { entry ->
                    val parts = entry.split(":").map { it.trim() }
                    if (parts.isNotEmpty()) {
                        existingLocales.add(parts[0])
                    }
                }
            }
            
            // Find system locales that need to be added
            val localesToAdd = systemLocales.filter { locale ->
                // Must not have dictionary
                !localesWithDict.contains(locale) &&
                // Must not be in method.xml
                !baseSubtypesInMethodXml.contains(locale) &&
                // Must not already be in custom input styles
                !existingLocales.contains(locale)
            }
            
            if (localesToAdd.isEmpty()) {
                Log.d(TAG, "No system locales without dictionary to add")
                return
            }
            
            // Add each locale with default layout
            val newEntries = mutableListOf<String>()
            localesToAdd.forEach { locale ->
                val defaultLayout = getLayoutForLocale(context.assets, locale, context)
                newEntries.add("$locale:$defaultLayout")
                Log.d(TAG, "Auto-adding system locale without dictionary: $locale with layout $defaultLayout")
            }
            
            // Merge with existing styles
            val updatedStyles = if (currentStyles.isBlank()) {
                newEntries.joinToString(";")
            } else {
                "$currentStyles;${newEntries.joinToString(";")}"
            }
            
            // Save updated styles
            SettingsManager.setCustomInputStyles(context, updatedStyles)
            // Track auto-added locales for future cleanup
            val tracked = loadAutoAddedLocales(context).toMutableSet()
            tracked.addAll(localesToAdd)
            saveAutoAddedLocales(context, tracked)
            Log.d(TAG, "Auto-added ${localesToAdd.size} system locales without dictionary to custom input styles")
        } catch (e: Exception) {
            Log.e(TAG, "Error auto-adding system locales without dictionary", e)
        }
    }
    
    /**
     * Gets the list of system-enabled locales.
     * Returns locales in format "en_US", "it_IT", etc.
     */
    private fun getSystemEnabledLocales(context: Context): List<String> {
        val locales = mutableListOf<String>()
        try {
            val config = context.applicationContext.resources.configuration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = config.locales
                for (i in 0 until localeList.size()) {
                    val locale = localeList[i]
                    val localeStr = formatLocaleStringForSystem(locale)
                    if (localeStr.isNotEmpty() && !locales.contains(localeStr)) {
                        locales.add(localeStr)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val locale = config.locale
                val localeStr = formatLocaleStringForSystem(locale)
                if (localeStr.isNotEmpty()) {
                    locales.add(localeStr)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system locales", e)
        }
        return locales
    }
    
    /**
     * Formats a Locale object to "en_US" format.
     */
    private fun formatLocaleStringForSystem(locale: Locale): String {
        val language = locale.language
        val country = locale.country
        return if (country.isNotEmpty()) {
            "${language}_$country"
        } else {
            language
        }
    }
    
    /**
     * Gets the list of locales that have dictionaries available.
     * Checks both serialized (.dict) and JSON (.json) formats.
     */
    private fun getLocalesWithDictionary(context: Context): Set<String> {
        val localesWithDict = mutableSetOf<String>()
        try {
            val assets = context.assets
            
            // Check serialized dictionaries from assets
            try {
                val serializedFiles = assets.list("common/dictionaries_serialized")
                serializedFiles?.forEach { fileName ->
                    if (fileName.endsWith("_base.dict")) {
                        val langCode = fileName.removeSuffix("_base.dict")
                        localesWithDict.addAll(getLocaleVariantsForLanguage(langCode))
                    }
                }
            } catch (e: Exception) {
                // If serialized directory doesn't exist, continue
            }

            // Check custom/imported serialized dictionaries in app storage
            try {
                val localDir = java.io.File(context.filesDir, "dictionaries_serialized/custom")
                val localFiles = localDir.listFiles { file ->
                    file.isFile && file.name.endsWith("_base.dict")
                }
                localFiles?.forEach { file ->
                    val langCode = file.name.removeSuffix("_base.dict")
                    localesWithDict.addAll(getLocaleVariantsForLanguage(langCode))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking local dictionaries_serialized", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking dictionaries", e)
        }
        return localesWithDict
    }
    
    /**
     * Maps a language code (e.g., "en", "it") to common locale variants.
     */
    private fun getLocaleVariantsForLanguage(langCode: String): List<String> {
        // Common locale variants for each language
        val variants = when (langCode.lowercase()) {
            "en" -> listOf("en_US", "en_GB", "en_AU", "en_CA", "en")
            "it" -> listOf("it_IT", "it_CH", "it")
            "fr" -> listOf("fr_FR", "fr_CA", "fr_CH", "fr_BE", "fr")
            "de" -> listOf("de_DE", "de_AT", "de_CH", "de")
            "pl" -> listOf("pl_PL", "pl")
            "da" -> listOf("da_DK", "da")
            "no" -> listOf("no_NO", "nb_NO", "nn_NO", "nb", "nn", "no")
            "nb" -> listOf("nb_NO", "no_NO", "nb", "no")
            "nn" -> listOf("nn_NO", "no_NO", "nn", "no")
            "es" -> listOf("es_ES", "es_MX", "es_AR", "es")
            "pt" -> listOf("pt_PT", "pt_BR", "pt")
            "ru" -> listOf("ru_RU", "ru")
            else -> listOf(langCode)
        }
        return variants
    }
    
    /**
     * Registers additional subtypes (custom input styles) with the system.
     * Can be called from MainActivity or from the IME service.
     * 
     * @param context Context for accessing system services and assets
     */
    fun registerAdditionalSubtypes(context: Context) {
        try {
            // Auto-add system locales without dictionary first
            autoAddSystemLocalesWithoutDictionary(context)
            
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                ?: run {
                    Log.e(TAG, "InputMethodManager not available")
                    return
                }
            
            // Get IME ID
            val componentName = android.content.ComponentName(
                context,
                PhysicalKeyboardInputMethodService::class.java
            )
            
            // Find the actual IME in the system list to get the correct ID format
            val inputMethodInfo = imm.inputMethodList.firstOrNull { info ->
                info.packageName == context.packageName && 
                info.serviceName == PhysicalKeyboardInputMethodService::class.java.name
            }
            
            if (inputMethodInfo == null) {
                Log.d(TAG, "IME not found in system list, will retry when IME is enabled")
                return
            }
            
            val imeId = inputMethodInfo.id
            Log.d(TAG, "Registering additional subtypes for IME: $imeId")
            
            val prefString = SettingsManager.getCustomInputStyles(context)
            val subtypes = createAdditionalSubtypesArray(
                prefString,
                context.assets,
                context
            )
            
            Log.d(TAG, "Created ${subtypes.size} additional subtypes")
            
            // Always call setAdditionalInputMethodSubtypes, even with empty array to remove old subtypes
            setAdditionalInputMethodSubtypesCompat(imm, imeId, subtypes)
            Log.d(TAG, "Successfully called setAdditionalInputMethodSubtypes with ${subtypes.size} subtypes")
            
            if (subtypes.isNotEmpty()) {
                // Try to explicitly enable the additional subtypes after a delay
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Re-fetch InputMethodInfo to get updated subtype list
                        val updatedInfo = imm.inputMethodList.firstOrNull { 
                            it.packageName == context.packageName && 
                            it.serviceName == PhysicalKeyboardInputMethodService::class.java.name
                        }
                        
                        if (updatedInfo != null) {
                            // Get all subtypes from InputMethodInfo (including the newly added ones)
                            val allSubtypes = mutableListOf<InputMethodSubtype>()
                            for (i in 0 until updatedInfo.subtypeCount) {
                                allSubtypes.add(updatedInfo.getSubtypeAt(i))
                            }
                            
                            // Get currently enabled subtypes (include implicit ones from method.xml)
                            val currentlyEnabled = imm.getEnabledInputMethodSubtypeList(updatedInfo, true)
                            val currentSystemLocales = getSystemEnabledLocales(context).toSet()
                            val systemLanguageCodes = currentSystemLocales.map { locale ->
                                locale.split("_").first().lowercase(Locale.ROOT)
                            }.toSet()
                            val enabledHashCodes = currentlyEnabled
                                .filter { subtype ->
                                    shouldKeepSubtype(
                                        context,
                                        context.assets,
                                        subtype,
                                        currentSystemLocales,
                                        systemLanguageCodes
                                    )
                                }
                                .map { it.hashCode() }
                                .toMutableSet()
                            
                            // Add hash codes of additional subtypes to enabled set
                            subtypes.forEach { additionalSubtype ->
                                // Find matching subtype in allSubtypes by locale and extraValue
                                val additionalLayout = getKeyboardLayoutFromSubtype(additionalSubtype)
                                val matchingSubtype = allSubtypes.firstOrNull { subtype ->
                                    additionalLayout != null &&
                                        isAdditionalSubtype(subtype) &&
                                        matchesLocaleAndKeyboardLayoutSet(
                                            subtype,
                                            additionalSubtype.localeString(),
                                            additionalLayout
                                        )
                                }
                                if (matchingSubtype != null) {
                                    enabledHashCodes.add(matchingSubtype.hashCode())
                                    Log.d(TAG, "Adding subtype to enabled list: locale=${matchingSubtype.localeString()}, hashCode=${matchingSubtype.hashCode()}")
                                }
                            }
                            
                            // Enable visible base subtypes plus all configured additional subtypes.
                            if (enabledHashCodes.isNotEmpty()) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    imm.setExplicitlyEnabledInputMethodSubtypes(
                                        updatedInfo.id,
                                        enabledHashCodes.toIntArray()
                                    )
                                    Log.d(TAG, "Explicitly enabled ${enabledHashCodes.size} subtypes (${subtypes.size} additional)")
                                } else {
                                    Log.d(TAG, "Skipping explicit subtype enable: requires Android 14+")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not explicitly enable subtypes", e)
                    }
                }, 500) // Wait 500ms for system to process registration
            } else {
                Log.d(TAG, "No subtypes to register, removed all additional subtypes")
                // When removing all subtypes, also clean up enabled subtypes list
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val updatedInfo = imm.inputMethodList.firstOrNull { 
                            it.packageName == context.packageName && 
                            it.serviceName == PhysicalKeyboardInputMethodService::class.java.name
                        }
                        
                        if (updatedInfo != null) {
                            // Get currently enabled subtypes (include implicit ones from method.xml)
                            val currentlyEnabled = imm.getEnabledInputMethodSubtypeList(updatedInfo, true)
                            val currentSystemLocales = getSystemEnabledLocales(context).toSet()
                            val systemLanguageCodes = currentSystemLocales.map { locale ->
                                locale.split("_").first().lowercase(Locale.ROOT)
                            }.toSet()

                            val systemSubtypes = currentlyEnabled.filter { subtype ->
                                !isAdditionalSubtype(subtype) &&
                                    shouldKeepSubtype(
                                        context,
                                        context.assets,
                                        subtype,
                                        currentSystemLocales,
                                        systemLanguageCodes
                                    )
                            }
                            
                            if (systemSubtypes.isNotEmpty()) {
                                val systemHashCodes = systemSubtypes.map { it.hashCode() }.toIntArray()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    imm.setExplicitlyEnabledInputMethodSubtypes(
                                        updatedInfo.id,
                                        systemHashCodes
                                    )
                                    Log.d(TAG, "Cleaned up enabled subtypes, kept ${systemHashCodes.size} system subtypes")
                                } else {
                                    Log.d(TAG, "Skipping enabled subtype cleanup: requires Android 14+")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not clean up enabled subtypes", e)
                    }
                }, 500) // Wait 500ms for system to process removal
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering additional subtypes", e)
        }
    }
}
