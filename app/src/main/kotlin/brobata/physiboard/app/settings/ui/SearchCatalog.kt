package brobata.physiboard.app.settings.ui

/**
 * One row of the static search catalogue (settings-catalog.md SS8). [route] is the 3.0
 * destination [title] deep-links to; [screenTitle] is what a Settings-origin result shows as
 * "In <screenTitle>" when it differs from [title] (SS8, "on Settings the row's description reads
 * 'In <screen>'").
 *
 * Regenerated from the 3.0 screen map rather than copied from the 2.x table (SS13, "Keep,
 * regenerate from the screen map so titles and targets cannot drift"): four 2.x entries pointed
 * at the wrong place (the two "Vibrate" rows at Voice instead of Sound & Haptics, "Text expansion"
 * at Smart Features instead of Extras, and "App Language" at a standalone screen this build never
 * gives a reachable entry point, folded here into its Input Languages dropdown). Every entry whose
 * only target is out of scope for this milestone (About, Diagnostics, Backup, Restore, Updates,
 * Reset device settings to stock, Remove bloat, Screen density, System tweaks, Key mapping, More
 * Customization) is left out rather than pointed at a screen that does not exist yet.
 */
data class SearchEntry(val title: String, val screenTitle: String, val route: String, val keywords: String)

object SearchCatalog {
    val entries: List<SearchEntry> = listOf(
        SearchEntry("Screen trackpad", "Screen trackpad", Routes.SCREEN_TRACKPAD, "trackpad cursor swipe screen spacebar hold arrow select"),
        SearchEntry("Smart Features", "Smart Features", Routes.SMART_FEATURES, "typing punctuation spaces"),
        SearchEntry("Auto-correction", "Auto-correction", Routes.AUTO_CORRECTION, "autocorrect spell dictionary suggestions typo"),
        SearchEntry("Input Languages", "Input Languages", Routes.INPUT_LANGUAGES, "language layout azerty qwertz input style"),
        SearchEntry("App Language", "Input Languages", Routes.INPUT_LANGUAGES, "language locale translate"),
        SearchEntry("Fn Layer", "Fn Layer", Routes.FN_LAYER, "navigation arrows cursor dpad scroll"),
        SearchEntry("Status Bar Theme", "Status Bar Theme", Routes.STATUS_BAR_THEME, "theme dark light color appearance keyboard"),
        SearchEntry("Status Bar", "Status Bar Theme", Routes.STATUS_BAR_THEME, "microphone mic emoji hamburger bottom bar status slots"),
        SearchEntry("PhysiBoard-QuickLauncher", "PhysiBoard-QuickLauncher", Routes.QUICK_LAUNCHER, "quick launcher apps shortcut launch"),
        SearchEntry("Enter key behaviour", "Enter key behaviour", Routes.ENTER_KEY_BEHAVIOUR, "enter send newline whatsapp per app"),
        SearchEntry("T2E Tools", "T2E Tools", Routes.T2E_TOOLS, "device toolbox titan unihertz system tools"),
        SearchEntry("Voice", "Voice", Routes.VOICE, "voice dictation microphone speech talk transcribe"),
        SearchEntry("Long-press Fn for speech input", "Voice", Routes.VOICE, "voice dictation microphone speech fn hold"),
        SearchEntry("Vibrate on dictation start/stop", "Sound & Haptics", Routes.SOUND_HAPTICS, "vibrate vibration haptic dictation voice"),
        SearchEntry("Vibration strength", "Sound & Haptics", Routes.SOUND_HAPTICS, "vibration strength stronger firmer haptic dictation voice"),
        SearchEntry("End-of-speech pause", "Voice", Routes.VOICE, "pause silence timeout cutoff dictation voice"),
        SearchEntry("Block offensive words", "Voice", Routes.VOICE, "profanity censor swear offensive"),
        SearchEntry("Hold Sym for the assistant", "Voice", Routes.VOICE, "assistant gemini sym hold long press voice ask siri"),
        SearchEntry("Orange key opens the assistant", "Voice", Routes.VOICE, "assistant gemini orange side key func1 shortcut voice ask"),
        SearchEntry("How the assistant opens", "Voice", Routes.VOICE, "assistant action intent listening gemini voice command hands free assist"),
        SearchEntry("Speech engine", "Voice", Routes.VOICE, "engine recognizer speech service google on-device offline dictation voice"),
        SearchEntry("Let the engine time the pause", "Voice", Routes.VOICE, "continuous segmented session pause cutoff dictation voice"),
        SearchEntry("Automatic punctuation", "Voice", Routes.VOICE, "punctuation comma period capitalization formatting dictation voice"),
        SearchEntry("Capitalize at text start", "Smart Features", Routes.SMART_FEATURES, "capital uppercase sentence autocap"),
        SearchEntry("Double Space inserts period", "Smart Features", Routes.SMART_FEATURES, "period full stop double space"),
        SearchEntry("Text expansion", "Text expansion", Routes.TEXT_EXPANSION, "snippet abbreviation expand shortcut"),
        SearchEntry("Exact typing", "Exact typing", Routes.appPicker(PerAppListKind.EXACT_TYPING), "terminal termux disable smart per app raw exceptions"),
        SearchEntry("Text box under the bar", "Text box under the bar", Routes.appPicker(PerAppListKind.TEXT_BOX_UNDER_BAR), "teams hidden covered text box compose field under bar inset blink"),
        // app-shell.md: the shell's own rows, now that Settings, About, Diagnostics and the update
        // checker exist (this module's report). Re-added per SS8's own rule: point at the real
        // screen, not a placeholder.
        SearchEntry("Status", "Settings", Routes.STATUS, "status enabled selected active keyboard language backlight version"),
        SearchEntry("About", "Settings", Routes.ABOUT, "about version licence credits support sponsor report problem tutorial"),
        SearchEntry("Diagnostics", "Settings", Routes.DIAGNOSTICS, "diagnostics debug export key event logger bug report record"),
        SearchEntry("Updates", "Settings", Routes.SETTINGS, "update github release check version download apk"),
        SearchEntry("Backup now", "Settings", Routes.SETTINGS, "backup export settings file"),
        SearchEntry("Restore from file", "Settings", Routes.SETTINGS, "restore import backup settings file"),
        // expansion-clipboard-pickers-launcher.md: the list editors and the launcher rows.
        SearchEntry("Manage snippets", "Text expansion", Routes.MANAGE_SNIPPETS, "snippet shortcut replacement expand abbreviation"),
        SearchEntry("Manage text replacements", "Auto-correction", Routes.CUSTOM_SUBSTITUTIONS, "text replacements custom substitutions correction language"),
        SearchEntry("Assigned launcher keys", "PhysiBoard-QuickLauncher", Routes.ASSIGNED_LAUNCHER_KEYS, "launcher key assign shortcut sym space app command"),
        SearchEntry("QuickLauncher entries", "PhysiBoard-QuickLauncher", Routes.QUICK_LAUNCHER_ENTRIES, "quick launcher sources apps device control navigation"),
        SearchEntry("Customize entries", "PhysiBoard-QuickLauncher", Routes.CUSTOMIZE_ENTRIES, "favorites hidden alias search color quick launcher"),
        SearchEntry("Clipboard history", "Clipboard history", Routes.CLIPBOARD_HISTORY, "clipboard copy paste history retention pin clips"),
    )

    /** spec: SS8, "the trimmed query is a case-insensitive substring of the title, of the screen title, or of the keywords." Results keep catalogue order. */
    fun search(query: String): List<SearchEntry> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return entries.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.screenTitle.contains(trimmed, ignoreCase = true) ||
                it.keywords.contains(trimmed, ignoreCase = true)
        }
    }
}
