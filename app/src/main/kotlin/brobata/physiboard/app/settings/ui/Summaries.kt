package brobata.physiboard.app.settings.ui

import brobata.physiboard.core.actions.feedback.TypingSoundMode
import brobata.physiboard.core.keys.LongPressMode
import brobata.physiboard.core.settings.CustomSymPage
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.StripThemePresets
import brobata.physiboard.core.settings.SymPage
import brobata.physiboard.core.settings.SymPagesConfig
import brobata.physiboard.core.subtype.BundledLayoutCatalog
import brobata.physiboard.core.text.MessagingPreset
import java.util.Locale

/**
 * The one-line summaries the home index and the category screens show under or beside a row
 * (app-shell.md SS6.4, "each category row shows where its settings stand now"). Pure functions of
 * [Settings], so a summary can never disagree with the screen behind it and the index draws them
 * at once, with nothing to load.
 */
object Summaries {
    private const val SEP = " · "

    fun onOff(on: Boolean): String = if (on) "On" else "Off"

    /** "Typing": capitals and the double-space period, the two things that change every sentence. */
    fun typing(s: Settings): String {
        val t = s.typing
        val caps = t.capitalizeAtTextStart || t.capitalizeAfterSentenceEnd
        return "Auto-capitals ${onWord(caps)}${SEP}double-space period ${onWord(t.doubleSpaceToPeriod)}"
    }

    /** "Autocorrect & words": e.g. "Autocorrect on · mix-ups off". */
    fun autocorrect(s: Settings): String {
        val c = s.correction
        return "Autocorrect ${onWord(c.autoReplaceOnSpaceEnter)}${SEP}mix-ups ${onWord(c.fixWordMixups)}"
    }

    /** "Languages & layouts": the layout the keys type in, and whether it follows the language. */
    fun languages(s: Settings): String {
        val l = s.languages
        val layout = layoutName(l.keyboardLayout)
        return if (l.layoutAutoByLocale) "$layout${SEP}follows the language" else layout
    }

    /**
     * A stored layout id by the name the Keyboard layout screen gives it: the bundled catalogue's
     * display name (`qwertz` is "QWERTZ | Deutsch"). An imported layout's id is its file name,
     * which is shown as it is; its own name is inside the file, and a summary reads no files.
     */
    fun layoutName(id: String): String =
        if (id.isBlank()) BundledLayoutCatalog.infoFor("qwerty").displayName else BundledLayoutCatalog.infoFor(id).displayName

    /** "Long press & accents": what a held letter types, and after how long. */
    fun longPress(s: Settings): String = "${longPressModeLabel(s.keys.longPressMode)}${SEP}${s.keys.longPressThresholdMs} ms"

    fun longPressModeLabel(mode: LongPressMode): String = when (mode) {
        LongPressMode.ALT -> "Alt symbol"
        LongPressMode.SHIFT -> "Capital letter"
        LongPressMode.VARIATIONS -> "Accent"
        LongPressMode.SYM -> "First Sym page"
        LongPressMode.SYM_SYMBOLS -> "Sym symbol"
        LongPressMode.SYM_EMOJI -> "Sym emoji"
    }

    /** "Sym pages": the switched-on pages in the order Sym steps through them, e.g. "Emoji → Symbols". */
    fun symPages(s: Settings): String {
        val pages = s.symPages.pages
        val on = pages.order.filter { it != SymPage.FILL && isEnabled(pages, it) }
        if (on.isEmpty()) return "No pages switched on"
        return on.joinToString(" → ") { symPageName(it, s.symPages.customPages) }
    }

    fun isEnabled(pages: SymPagesConfig, page: SymPage): Boolean = when (page) {
        SymPage.EMOJI -> pages.emojiEnabled
        SymPage.SYMBOLS -> pages.symbolsEnabled
        SymPage.CLIPBOARD -> pages.clipboardEnabled
        SymPage.EMOJI_PICKER -> pages.emojiPickerEnabled
        SymPage.GIF -> pages.gifEnabled
        SymPage.CUSTOM_1 -> pages.custom1Enabled
        SymPage.CUSTOM_2 -> pages.custom2Enabled
        SymPage.CUSTOM_3 -> pages.custom3Enabled
        SymPage.FILL -> pages.fillEnabled
    }

    fun symPageName(page: SymPage, customPages: List<CustomSymPage>): String = when (page) {
        SymPage.EMOJI -> "Emoji keys"
        SymPage.SYMBOLS -> "Symbols"
        SymPage.CLIPBOARD -> "Clipboard"
        SymPage.EMOJI_PICKER -> "Emoji"
        SymPage.GIF -> "GIFs"
        SymPage.FILL -> "Fill"
        SymPage.CUSTOM_1, SymPage.CUSTOM_2, SymPage.CUSTOM_3 -> {
            val index = page.ordinal - SymPage.CUSTOM_1.ordinal
            customPages.getOrNull(index)?.name?.trim()?.ifEmpty { null } ?: "My page ${index + 1}"
        }
    }

    /** "Voice": the trigger and when a session stops, e.g. "Hold Fn · stops after 2.5 s". */
    fun voice(s: Settings): String {
        val d = s.dictation
        if (!d.fnLongPressSpeech) return "Hold Fn is off"
        return "Hold Fn${SEP}${silenceLabel(d.stopAfterSilenceMs)}"
    }

    /** `dictation_stop_after_silence_ms` as a phrase; 0 means it runs until stopped. */
    fun silenceLabel(ms: Int): String {
        if (ms <= 0) return "runs until you stop it"
        val seconds = ms / 1000.0
        val text = if (ms % 1000 == 0) "${ms / 1000}" else String.format(Locale.ROOT, "%.1f", seconds)
        return "stops after $text s"
    }

    /** "Keys & shortcuts": the Fn layer and the screen trackpad, the two that change what keys do. */
    fun keys(s: Settings): String =
        "Fn layer ${onWord(s.keys.navModeEnabled)}${SEP}trackpad ${onWord(s.trackpad.enabled)}"

    /** "Apps": how many apps get raw typing and what Enter does in messaging apps. */
    fun apps(s: Settings): String {
        val terminal = s.perApp.exactTypingPackages.size
        val terminalText = when (terminal) {
            0 -> "No terminal apps"
            1 -> "1 terminal app"
            else -> "$terminal terminal apps"
        }
        val enter = if (s.perApp.enterBehaviorEnabled) enterPresetShort(s.perApp.enterPreset) else "Enter as each app sets it"
        return "$terminalText$SEP$enter"
    }

    /** The messaging preset in a few words, for a summary line. */
    fun enterPresetShort(preset: MessagingPreset): String = when (preset) {
        MessagingPreset.APP_DEFAULT -> "Enter as each app sets it"
        MessagingPreset.SEND_SHIFT_NEWLINE -> "Enter sends"
        MessagingPreset.NEWLINE_CTRL_SEND -> "Ctrl+Enter sends"
        MessagingPreset.CUSTOM -> "Enter set per app"
    }

    /** "Look & feel": the theme's name and whether keys click. */
    fun look(s: Settings): String = "${themeName(s)}$SEP${soundLabel(s.feedback.typingSoundMode)}"

    fun themeName(s: Settings): String {
        val active = s.statusBar.theme
        return StripThemePresets.ALL.firstOrNull { it.theme == active }?.name
            ?: s.statusBar.savedThemes.firstOrNull { it.theme == active }?.name
            ?: "Custom colours"
    }

    fun soundLabel(mode: TypingSoundMode): String = when (mode) {
        TypingSoundMode.OFF -> "silent keys"
        TypingSoundMode.CLICK -> "click sounds"
        TypingSoundMode.TYPEWRITER -> "typewriter sounds"
    }

    /** "Privacy": private mode first, since it is the switch someone comes here for. */
    fun privacy(s: Settings): String =
        "Private mode ${onWord(s.privacy.privateMode)}${SEP}clean links ${onWord(s.privacy.cleanLinks)}"

    private fun onWord(on: Boolean): String = if (on) "on" else "off"
}
