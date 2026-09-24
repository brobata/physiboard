package brobata.physiboard.core.strip

/**
 * What tapping a strip button asks the keyboard to do. spec: status-bar.md SS6.1's "Tap" and
 * "Long press" columns, one value per distinct outcome so `:ime` dispatches on the outcome and
 * never on the button's id. Every action names a subsystem; which of those subsystems exist in a
 * given build is `:ime`'s problem (a missing one renders the button but does nothing, never
 * crashes), not this catalogue's.
 */
sealed class StripAction {
    /** The catalogue's `none` and every "Long press: none" cell. */
    data object Nothing : StripAction()

    /** spec SS6.1: clipboard opens page 3, symbols page 2, the emoji picker page 4. */
    data class OpenSymPage(val page: Int) : StripAction()

    data object StartDictation : StripAction()

    /** spec SS6.1, `language` tap: "Cycles to the next input style". */
    data object CycleLanguage : StripAction()

    /** spec SS6.1: the `settings` tap and the `language` long press. */
    data object OpenSettings : StripAction()

    /** spec SS6.4, the hamburger's quick-actions overlay. */
    data object OpenQuickActions : StripAction()

    /** spec SS6.1: undo sends Ctrl+Z, redo sends Ctrl+Y, as key events the app interprets. */
    data class SendCtrlCombo(val letter: Char) : StripAction()
}

/** spec: layers-sym-alt.md's page numbers as status-bar.md SS6.1 cites them: symbols is page 2, clipboard 3, the emoji picker 4. */
const val SYM_PAGE_SYMBOLS: Int = 2
const val SYM_PAGE_CLIPBOARD: Int = 3
const val SYM_PAGE_EMOJI_PICKER: Int = 4

/**
 * The haptic a button tap gives. spec: status-bar.md SS6.1, "Every tap gives the system
 * keyboard-tap haptic (undo and redo give the 25 ms haptic instead)".
 */
enum class TapHaptic { SYSTEM_KEYBOARD_TAP, FIXED_25_MS }

/**
 * The catalogue of assignable strip buttons. spec: status-bar.md SS6.1 (id, label, description,
 * tap, long press) filtered by SS19's Keep/Drop verdicts: `software_keyboard_mode` is gone
 * because 3.0 has no on-screen keyboard, and its stored id therefore reads as [NONE] like any
 * other unknown id (SS6.3, "Unknown ids in a stored list are replaced by `none`").
 *
 * [HAMBURGER] is kept with the 2.x behaviour the spec records rather than the one it wishes for:
 * SS6.4 and SS17 say the overlay "is closed... in hardware mode, on every strip refresh", so on
 * the Titan it survives only until the next key press. [StripModel] encodes exactly that (its
 * `quickActionsOpen` is false after every refresh); SS19 leaves fixing or dropping it undecided.
 */
enum class StripButton(
    val id: String,
    val label: String,
    val description: String,
    val tap: StripAction,
    val longPress: StripAction = StripAction.Nothing,
    /** spec SS6.1: clipboard and microphone release "a latched Shift or Alt layer" before acting. */
    val releasesLatchedLayerFirst: Boolean = false,
    val haptic: TapHaptic = TapHaptic.SYSTEM_KEYBOARD_TAP,
) {
    NONE("none", "None", "Empty", StripAction.Nothing),
    CLIPBOARD("clipboard", "Clipboard", "Clipboard history", StripAction.OpenSymPage(SYM_PAGE_CLIPBOARD), releasesLatchedLayerFirst = true),
    MICROPHONE("microphone", "Microphone", "Voice input", StripAction.StartDictation, releasesLatchedLayerFirst = true),
    EMOJI("emoji", "Emoji", "Emoji picker", StripAction.OpenSymPage(SYM_PAGE_EMOJI_PICKER)),
    LANGUAGE("language", "Language", "Switch language", StripAction.CycleLanguage, longPress = StripAction.OpenSettings),
    HAMBURGER("hamburger", "Menu", "Quick actions", StripAction.OpenQuickActions),
    SETTINGS("settings", "Settings", "Open settings", StripAction.OpenSettings),
    SYMBOLS("symbols", "Symbols", "Symbols keyboard", StripAction.OpenSymPage(SYM_PAGE_SYMBOLS)),
    UNDO("undo", "Undo", "Send Ctrl+Z", StripAction.SendCtrlCombo('Z'), haptic = TapHaptic.FIXED_25_MS),
    REDO("redo", "Redo", "Send Ctrl+Y", StripAction.SendCtrlCombo('Y'), haptic = TapHaptic.FIXED_25_MS),
    ;

    /** spec SS6.1: the clipboard button is the one that "carries a badge" with the item count. */
    val carriesClipboardBadge: Boolean get() = this == CLIPBOARD

    companion object {
        /** spec SS6.3: an id no build of the catalogue knows (older, newer, or dropped by SS19) reads as [NONE]. */
        fun fromId(id: String?): StripButton = entries.firstOrNull { it.id == id } ?: NONE

        /** spec SS6.3: "the dropdown lists all eleven ids"; in 3.0, the ten SS19 keeps, `none` first. */
        val assignable: List<StripButton> get() = entries
    }
}

/**
 * The quick-actions overlay's fixed content. spec: status-bar.md SS6.4: a close button "followed
 * by nine fixed buttons in this order". Keyboard mode is SS19-dropped with the soft keyboard, so
 * the row is eight actions here; the width rule still divides by "one close plus the items".
 */
object QuickActions {
    val ITEMS: List<StripButton> = listOf(
        StripButton.SYMBOLS, StripButton.EMOJI, StripButton.MICROPHONE, StripButton.CLIPBOARD,
        StripButton.UNDO, StripButton.REDO, StripButton.LANGUAGE, StripButton.SETTINGS,
    )

    /** spec SS6.4: one close button plus the items, each of equal width. */
    val BUTTON_COUNT: Int get() = ITEMS.size + 1

    /** spec SS6.4 and SS17: in hardware mode (all of 3.0) "the overlay closes" on every refresh. Recorded, not fixed (SS19). */
    const val CLOSES_ON_EVERY_REFRESH: Boolean = true
}
