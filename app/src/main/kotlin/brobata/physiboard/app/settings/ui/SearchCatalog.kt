package brobata.physiboard.app.settings.ui

/**
 * One row of the static search catalogue (settings-catalog.md SS8). [route] is the destination
 * [title] deep-links to; [screenTitle] is the screen it lives on, shown as "In <screenTitle>" on a
 * result when it differs from [title].
 *
 * Regenerated from the screen map (SS13, "Keep, regenerate from the screen map so titles and
 * targets cannot drift"), most recently for the 3.1 category index (docs/plans/
 * settings-reorganization.md): every [screenTitle] is the title the screen now shows, and no
 * entry points at a setting no screen offers (the strip's buttons, its height and LEDs, the
 * suggestion row switch).
 */
data class SearchEntry(val title: String, val screenTitle: String, val route: String, val keywords: String)

object SearchCatalog {
    private const val TYPING = "Typing"
    private const val AUTOCORRECT = "Autocorrect & words"
    private const val LANGUAGES = "Languages & layouts"
    private const val LONG_PRESS = "Long press & accents"
    private const val SYM = "Sym pages"
    private const val VOICE = "Voice"
    private const val KEYS = "Keys & shortcuts"
    private const val APPS = "Apps"
    private const val LOOK = "Look & feel"
    private const val PRIVACY = "Privacy"
    private const val TITAN = "Titan tools"
    private const val BACKUP = "Backup & restore"
    private const val HELP = "Help"

    val entries: List<SearchEntry> = listOf(
        // The categories themselves.
        SearchEntry(TYPING, TYPING, Routes.TYPING, "typing smart features capital capitalization punctuation backspace delete alt"),
        SearchEntry(AUTOCORRECT, AUTOCORRECT, Routes.AUTO_CORRECTION, "autocorrect auto-correction spell typo correct dictionary words"),
        SearchEntry(LANGUAGES, LANGUAGES, Routes.INPUT_LANGUAGES, "language languages input layout azerty qwertz input style"),
        SearchEntry(LONG_PRESS, LONG_PRESS, Routes.LONG_PRESS, "long press hold held key alt symbol capital uppercase accents diacritics variations sym emoji"),
        SearchEntry(SYM, SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "sym pages order emoji symbols gif gifs clipboard picker cycle reorder customize sym keyboard"),
        SearchEntry(VOICE, VOICE, Routes.VOICE, "voice dictation microphone speech talk transcribe"),
        SearchEntry(KEYS, KEYS, Routes.KEYS, "keys shortcuts fn layer trackpad key mapping launcher"),
        SearchEntry(APPS, APPS, Routes.APPS, "apps per app terminal enter"),
        SearchEntry(LOOK, LOOK, Routes.LOOK, "look feel appearance theme sound haptics badge"),
        SearchEntry(PRIVACY, PRIVACY, Routes.PRIVACY, "privacy private incognito"),
        SearchEntry(TITAN, TITAN, Routes.T2E_TOOLS, "t2e tools device toolbox titan unihertz system tools"),
        SearchEntry(BACKUP, BACKUP, Routes.BACKUP, "backup restore export import reset"),
        SearchEntry(HELP, HELP, Routes.HELP, "help support problem"),
        // Typing.
        SearchEntry("Capitals", TYPING, Routes.TYPING, "capital uppercase sentence autocap capitalize text start shift"),
        SearchEntry("Double Space types a full stop", TYPING, Routes.TYPING, "period full stop double space"),
        SearchEntry("More punctuation", TYPING, Routes.TYPING, "smart quotes curly apostrophe dash hyphen comma space guillemets"),
        SearchEntry("Spaces around punctuation", TYPING, Routes.PUNCTUATION_SPACING, "punctuation spacing space before after remove"),
        SearchEntry("Backspace deletes forward", TYPING, Routes.TYPING, "backspace delete forward shift alt line start"),
        SearchEntry("Space or Enter releases Alt", TYPING, Routes.TYPING, "alt release space latch one-shot"),
        SearchEntry("Text expansion", "Text expansion", Routes.TEXT_EXPANSION, "snippet abbreviation expand shortcut"),
        SearchEntry("Snippets", "Text expansion", Routes.MANAGE_SNIPPETS, "snippet shortcut replacement expand abbreviation manage"),
        // Autocorrect & words.
        SearchEntry("Fix typos", AUTOCORRECT, Routes.AUTO_CORRECTION, "automatic correction autocorrect typo misspelled"),
        SearchEntry("Fix mixed-up words", AUTOCORRECT, Routes.AUTO_CORRECTION, "mixup mixed-up its it's your you're their there then than grammar context sentence homophone"),
        SearchEntry("Add missing apostrophes and accents", AUTOCORRECT, Routes.AUTO_CORRECTION, "apostrophe accent marks diacritics dont don't"),
        SearchEntry("Personal dictionary", AUTOCORRECT, Routes.PERSONAL_DICTIONARY, "personal dictionary user words add delete edit"),
        SearchEntry("Text replacements", AUTOCORRECT, Routes.CUSTOM_SUBSTITUTIONS, "text replacements custom substitutions correction language rules"),
        SearchEntry("System spell checker", AUTOCORRECT, Routes.AUTO_CORRECTION, "spell checker spellcheck underline red misspelled squiggle typo apps android"),
        SearchEntry("Choose PhysiBoard as the spell checker", AUTOCORRECT, Routes.AUTO_CORRECTION, "spell checker spellcheck automatic select default pairing turn on"),
        SearchEntry("How far a correction may reach", AUTOCORRECT, Routes.AUTO_CORRECTION, "maximum correction distance edit distance fine-tuning proximity nearby keys"),
        // Languages & layouts.
        SearchEntry("Keyboard layout", LANGUAGES, Routes.KEYBOARD_LAYOUT, "keyboard layout qwertz azerty multitap standard import viewer follow language automatic"),
        SearchEntry("Languages you type in", LANGUAGES, Routes.INPUT_STYLES, "input style locale layout suggestion dictionary add edit manage"),
        SearchEntry("Dictionaries", LANGUAGES, Routes.INSTALLED_DICTIONARIES, "dictionary download import language install manage installed"),
        SearchEntry("Switch language with", LANGUAGES, Routes.INPUT_LANGUAGES, "alt shift enter ctrl space switch layout language chord"),
        // Long press & accents.
        SearchEntry("Long press types", LONG_PRESS, Routes.LONG_PRESS, "long press mode accent variation capital alt symbol sym emoji diacritics"),
        SearchEntry("Hold time", LONG_PRESS, Routes.LONG_PRESS, "hold time long press threshold delay milliseconds ms"),
        SearchEntry("Show every accent", LONG_PRESS, Routes.LONG_PRESS, "accents diacritics variations chooser bar pick number"),
        SearchEntry("Customize Variations", LONG_PRESS, Routes.CUSTOMIZE_VARIATIONS, "accents diacritics variations letters ą ć ę ł ń ó ś ź ż é è ü ö ä ß ñ ç polish french german"),
        // Sym pages.
        SearchEntry("Kaomoji on the Emoji page", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "kaomoji text faces emoticons emoji picker mode"),
        SearchEntry("Larger emoji picker", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "emoji picker height expanded larger sym"),
        SearchEntry("Default skin tone", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "emoji skin tone colour color fitzpatrick hand people default"),
        SearchEntry("GIFs", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "gif gifs klipy tenor animated sym page search"),
        SearchEntry("My Sym pages", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "my page own custom sym layer extra symbols characters keys personal"),
        SearchEntry("Fill page", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "fill autofill one-time code otp sms verification 2fa login password sym page"),
        SearchEntry("One-time codes from notifications", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "otp one-time code verification sms text message email 2fa two-factor notifications fill"),
        SearchEntry("Password manager suggestions", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "password manager autofill inline suggestions logins bitwarden google fill experimental"),
        SearchEntry("Double-tap Sym for the page chooser", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "sym double tap chooser page letter open kaomoji gif"),
        SearchEntry("Close Sym after typing a character", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "sym auto close layout one-shot"),
        SearchEntry("Sym+C/V/X/A", SYM, Routes.CUSTOMIZE_SYM_KEYBOARD, "copy paste cut select all sym shortcuts edit"),
        SearchEntry("Clipboard history", "Clipboard history", Routes.CLIPBOARD_HISTORY, "clipboard copy paste history retention pin clips"),
        // Voice.
        SearchEntry("Long-press Fn for speech input", VOICE, Routes.VOICE, "voice dictation microphone speech fn hold"),
        SearchEntry("Stop after silence", VOICE, Routes.VOICE, "pause silence timeout cutoff stop dictation voice never"),
        SearchEntry("Typing stops dictation", VOICE, Routes.VOICE, "typing key stop end dictation voice"),
        SearchEntry("Keep speech on the phone", VOICE, Routes.VOICE, "offline on-device private speech pack download dictation voice"),
        SearchEntry("Pause music while dictating", VOICE, Routes.VOICE, "music media audio focus pause audible spotify dictation voice"),
        SearchEntry("Block offensive words", VOICE, Routes.VOICE, "profanity censor swear offensive"),
        SearchEntry("Hold Sym for the assistant", VOICE, Routes.VOICE, "assistant gemini sym hold long press voice ask siri"),
        SearchEntry("Orange key opens the assistant", VOICE, Routes.VOICE, "assistant gemini orange side key func1 shortcut voice ask"),
        SearchEntry("How the assistant opens", VOICE, Routes.VOICE, "assistant action intent listening gemini voice command hands free assist"),
        SearchEntry("Speech engine", VOICE, Routes.VOICE, "engine recognizer speech service google on-device offline dictation voice"),
        SearchEntry("Automatic punctuation", VOICE, Routes.VOICE, "punctuation comma period capitalization formatting dictation voice"),
        // Keys & shortcuts.
        SearchEntry("Key mapping", KEYS, Routes.KEY_MAPPING, "key mapping fn sym orange side key vendor inventory"),
        SearchEntry("Fn layer", KEYS, Routes.FN_LAYER, "fn layer navigation arrows cursor dpad scroll key grid mapping keycode action command ctrl"),
        SearchEntry("Screen trackpad", KEYS, Routes.SCREEN_TRACKPAD, "trackpad cursor swipe screen spacebar hold arrow select"),
        SearchEntry("Accessibility service", KEYS, Routes.ACCESSIBILITY_SERVICE, "accessibility service restricted settings no text box camera everywhere"),
        SearchEntry("Fn shortcuts everywhere", "Accessibility service", Routes.ACCESSIBILITY_SERVICE, "fn sym shortcuts launcher home camera video settings no text box field accessibility"),
        SearchEntry("Focus the text box when I start typing", "Accessibility service", Routes.ACCESSIBILITY_SERVICE, "focus cursor caret messages backspace box tap select accessibility"),
        SearchEntry("Quick launcher", KEYS, Routes.QUICK_LAUNCHER, "quick launcher apps shortcut launch search hold reassign change remove reset unassign clear"),
        SearchEntry("Assigned launcher keys", "Quick launcher", Routes.ASSIGNED_LAUNCHER_KEYS, "launcher key assign shortcut sym space app command hold reassign change remove reset unassign clear"),
        SearchEntry("QuickLauncher entries", "Quick launcher", Routes.QUICK_LAUNCHER_ENTRIES, "quick launcher sources apps device control navigation"),
        SearchEntry("Customize entries", "Quick launcher", Routes.CUSTOMIZE_ENTRIES, "favorites favourites hidden alias search color colour quick launcher"),
        // Apps.
        SearchEntry("Terminal mode", APPS, Routes.appPicker(PerAppListKind.EXACT_TYPING), "exact typing terminal ssh code termux disable smart per app raw exceptions ctrl escape"),
        SearchEntry("Enter key", APPS, Routes.ENTER_KEY_BEHAVIOUR, "enter send newline whatsapp per app messaging preset"),
        SearchEntry("App overrides", "Enter key", Routes.appPicker(PerAppListKind.ENTER_OVERRIDES), "enter send method shortcut per app override"),
        // Look & feel.
        SearchEntry("Theme", LOOK, Routes.THEME, "theme dark light color colour appearance keyboard preset sym pages"),
        SearchEntry("Colours", "Theme", Routes.CUSTOMIZE_COLORS, "colors colours customize keys buttons accent background save"),
        SearchEntry("Saved themes", "Theme", Routes.SAVED_THEMES, "saved theme apply delete custom"),
        SearchEntry("Per-language themes", "Theme", Routes.THEME_LAYOUT_OVERRIDES, "theme layout override locale language per-language"),
        SearchEntry("Sound & haptics", LOOK, Routes.SOUND_HAPTICS, "sound click typewriter vibration haptic tap"),
        SearchEntry("Vibrate on every key", "Sound & haptics", Routes.SOUND_HAPTICS, "vibrate vibration haptic key press tick typing strength"),
        SearchEntry("Feedback vibrations", "Sound & haptics", Routes.SOUND_HAPTICS, "vibrate vibration haptic shift caps sym accent correction undo long press feel"),
        SearchEntry("Vibrate when dictation starts and stops", "Sound & haptics", Routes.SOUND_HAPTICS, "vibrate vibration haptic dictation voice"),
        SearchEntry("Vibration strength", "Sound & haptics", Routes.SOUND_HAPTICS, "vibration strength stronger firmer haptic dictation voice"),
        SearchEntry("Modifier badge", LOOK, Routes.LOOK, "caret cursor badge shift alt ctrl sym modifier indicator"),
        SearchEntry("App language", LOOK, Routes.APP_LANGUAGE, "language locale translate app settings"),
        // Privacy.
        SearchEntry("Private mode", PRIVACY, Routes.PRIVACY, "private privacy incognito offline learn learning history network"),
        SearchEntry("Clean links", PRIVACY, Routes.PRIVACY, "clean links tracking utm fbclid gclid url redirect copy paste clipboard privacy"),
        SearchEntry("Notification access", PRIVACY, Routes.PRIVACY, "notification access listener permission one-time codes otp allow"),
        // Titan tools.
        SearchEntry("Smart keyboard backlight", TITAN, Routes.SMART_BACKLIGHT, "backlight keyboard light always on pairing broker adb"),
        SearchEntry("Remove bloat", TITAN, Routes.REMOVE_BLOAT, "bloat vendor factory uninstall disable bloatware titan"),
        SearchEntry("Screen density", TITAN, Routes.SCREEN_DENSITY, "density dpi wm zoom scale screen size"),
        SearchEntry("System tweaks", TITAN, Routes.SYSTEM_TWEAKS, "animation scale notification history one-handed mode tweaks"),
        SearchEntry("Notification ring", TITAN, Routes.NOTIFICATION_RING, "ring glow camera hole notification lock screen backlight color colour"),
        // Backup & restore, Help, About.
        SearchEntry("Back up now", BACKUP, Routes.BACKUP, "backup export settings file save"),
        SearchEntry("Restore from a file", BACKUP, Routes.BACKUP, "restore import backup settings file"),
        SearchEntry("Reset all settings", BACKUP, Routes.BACKUP, "reset defaults factory start over"),
        SearchEntry("Reset device settings to stock", BACKUP, Routes.BACKUP, "reset stock uninstall fn key backlight device accessibility service"),
        SearchEntry("Status check", HELP, Routes.STATUS, "status enabled selected active keyboard language backlight version"),
        SearchEntry("Test field", HELP, Routes.TEST_FIELD, "test type try field"),
        SearchEntry("Diagnostics", HELP, Routes.DIAGNOSTICS, "diagnostics debug export key event logger bug report record"),
        SearchEntry("Check for updates", HELP, Routes.HELP, "update github release check version download apk"),
        SearchEntry("Show the tutorial", HELP, Routes.HELP, "tutorial walkthrough intro setup first run onboarding welcome again"),
        SearchEntry("About", "About", Routes.ABOUT, "about version licence license credits support sponsor report problem"),
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
