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
 * Reset device settings to stock, More Customization) is left out rather than pointed at a screen
 * that does not exist yet. Remove bloat, Screen density, System tweaks and Key mapping now have
 * real screens (broker-privileged-toolbox.md) and are added below.
 */
data class SearchEntry(val title: String, val screenTitle: String, val route: String, val keywords: String)

object SearchCatalog {
    val entries: List<SearchEntry> = listOf(
        SearchEntry("Screen trackpad", "Screen trackpad", Routes.SCREEN_TRACKPAD, "trackpad cursor swipe screen spacebar hold arrow select"),
        SearchEntry("Smart Features", "Smart Features", Routes.SMART_FEATURES, "typing punctuation spaces smart quotes curly apostrophe dash double space period"),
        SearchEntry("Auto-correction", "Auto-correction", Routes.AUTO_CORRECTION, "autocorrect spell dictionary suggestions typo"),
        SearchEntry("Input Languages", "Input Languages", Routes.INPUT_LANGUAGES, "language layout azerty qwertz input style"),
        SearchEntry("App Language", "Input Languages", Routes.INPUT_LANGUAGES, "language locale translate"),
        SearchEntry("Fn Layer", "Fn Layer", Routes.FN_LAYER, "navigation arrows cursor dpad scroll"),
        SearchEntry("Theme", "Theme", Routes.STATUS_BAR_THEME, "theme status bar dark light color colour appearance keyboard"),
        SearchEntry("Sym page buttons", "Theme", Routes.STATUS_BAR_THEME, "microphone mic emoji hamburger buttons slots sym page"),
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
        SearchEntry("Customize entries", "PhysiBoard-QuickLauncher", Routes.CUSTOMIZE_ENTRIES, "favorites favourites hidden alias search color colour quick launcher"),
        SearchEntry("Clipboard history", "Clipboard history", Routes.CLIPBOARD_HISTORY, "clipboard copy paste history retention pin clips"),
        // app-shell.md SS31, expansion-clipboard-pickers-launcher.md SS3.7.
        SearchEntry("Private mode", "Privacy", Routes.PRIVACY, "private privacy incognito offline learn learning history network"),
        SearchEntry("Clean links", "Privacy", Routes.PRIVACY, "clean links tracking utm fbclid gclid url redirect copy paste clipboard privacy"),
        // broker-privileged-toolbox.md, device-backlight-ring.md: the T2E Tools toolbox screens.
        SearchEntry("Smart keyboard backlight", "Smart keyboard backlight", Routes.SMART_BACKLIGHT, "backlight keyboard light always on pairing broker adb"),
        SearchEntry("Remove bloat", "Remove bloat", Routes.REMOVE_BLOAT, "bloat vendor factory uninstall disable bloatware titan"),
        SearchEntry("Screen density", "Screen density", Routes.SCREEN_DENSITY, "density dpi wm zoom scale screen size"),
        SearchEntry("System tweaks", "System tweaks", Routes.SYSTEM_TWEAKS, "animation scale notification history one-handed mode tweaks"),
        SearchEntry("Notification ring", "Notification ring", Routes.NOTIFICATION_RING, "ring glow camera hole notification lock screen backlight color colour"),
        SearchEntry("Key mapping", "Key mapping", Routes.KEY_MAPPING, "key mapping fn sym orange side key vendor inventory"),
        // dictionaries-languages.md SS6, SS7, SS8.2; status-bar.md SS9.2-9.4; per-app-behavior.md
        // SS3.11; trackpad-caret-nav.md SS5.8: this module's feature work.
        SearchEntry("Fix mixed-up words", "Auto-correction", Routes.AUTO_CORRECTION, "mixup mixed-up its it's your you're their there then than grammar context sentence homophone"),
        SearchEntry("Personal dictionary", "Auto-correction", Routes.PERSONAL_DICTIONARY, "personal dictionary user words add delete edit"),
        SearchEntry("Installed dictionaries", "Input Languages", Routes.INSTALLED_DICTIONARIES, "dictionary download import language install manage"),
        SearchEntry("Manage input styles", "Input Languages", Routes.INPUT_STYLES, "input style locale layout suggestion dictionary add edit"),
        SearchEntry("Keyboard Layout", "Keyboard Layout", Routes.KEYBOARD_LAYOUT, "keyboard layout qwertz azerty multitap standard import viewer"),
        SearchEntry("Saved themes", "Theme", Routes.SAVED_THEMES, "saved theme apply delete custom"),
        SearchEntry("Layout overrides", "Theme", Routes.THEME_LAYOUT_OVERRIDES, "theme layout override locale language per-language"),
        SearchEntry("App overrides", "Enter key behaviour", Routes.appPicker(PerAppListKind.ENTER_OVERRIDES), "enter send method shortcut per app override"),
        SearchEntry("Configure Fn layer key mappings", "Fn Layer", Routes.FN_LAYER, "fn layer key grid mapping keycode action command"),
        // layers-sym-alt.md SS5.9: "Customize SYM Keyboard".
        SearchEntry("Customize SYM Keyboard", "Customize SYM Keyboard", Routes.CUSTOMIZE_SYM_KEYBOARD, "sym emoji symbols pages order edit auto-close picker"),
        SearchEntry("Arrange SYM pages order", "Customize SYM Keyboard", Routes.CUSTOMIZE_SYM_KEYBOARD, "sym pages order emoji symbols clipboard picker cycle"),
        SearchEntry("Auto-Close SYM Layout", "Customize SYM Keyboard", Routes.CUSTOMIZE_SYM_KEYBOARD, "sym auto close layout one-shot"),
        SearchEntry("Larger emoji picker", "Customize SYM Keyboard", Routes.CUSTOMIZE_SYM_KEYBOARD, "emoji picker height expanded larger sym"),
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
