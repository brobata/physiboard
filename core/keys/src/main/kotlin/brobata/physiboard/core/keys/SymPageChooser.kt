package brobata.physiboard.core.keys

/**
 * Where one row of the Sym page chooser leads. Kaomoji and Unicode symbols are modes of the
 * picker page (4), reachable here on their own because the chooser is the only direct way in
 * now that the strip's page buttons are gone. spec: layers-sym-alt.md SS5.10.
 */
enum class SymChooserTarget(val letter: Char, val label: String, val page: SymPageId) {
    EMOJI('E', "Emoji", SymPageId.EMOJI),
    SYMBOLS('S', "Symbols", SymPageId.SYMBOLS),
    CLIPBOARD('C', "Clipboard", SymPageId.CLIPBOARD),
    EMOJI_PICKER('P', "Emoji picker", SymPageId.EMOJI_PICKER),
    KAOMOJI('K', "Kaomoji", SymPageId.EMOJI_PICKER),
    UNICODE_SYMBOLS('U', "Unicode symbols", SymPageId.EMOJI_PICKER),
    GIF('G', "GIFs", SymPageId.GIF),
}

/** One chooser row: the target, and whether its page is in the Sym cycle (shown, not obeyed). */
data class SymChooserEntry(val target: SymChooserTarget, val inCycle: Boolean)

/**
 * The Sym page chooser's rules. spec: layers-sym-alt.md SS5.10. The chooser lists every page,
 * enabled or not, each with the key that opens it; a page opened from here opens even when it is
 * switched off for the cycle.
 */
object SymPageChooser {

    /** What one key press does while the chooser is open. */
    sealed class KeyOutcome {
        /** Close the chooser and open [target]; the key (and its release) is consumed. */
        data class Open(val target: SymChooserTarget) : KeyOutcome()

        /** Close the chooser; the key (and its release) is consumed. Back and Sym. */
        data object Dismiss : KeyOutcome()

        /** Ignore the key and stay open (Shift, so Shift+letter still picks; auto-repeats of a consumed key). */
        data object Swallow : KeyOutcome()

        /** Close the chooser and let the key do what it always does. */
        data object CloseAndPassOn : KeyOutcome()
    }

    /**
     * The rows in display order: the pages in the user's cycle order (the picker's two extra
     * modes right after the picker itself), each marked with whether it is in the cycle.
     */
    fun entries(config: SymPagesConfig): List<SymChooserEntry> {
        val rows = ArrayList<SymChooserEntry>()
        for (page in config.normalizedOrder) {
            val inCycle = config.isEnabled(page)
            when (page) {
                SymPageId.EMOJI -> rows.add(SymChooserEntry(SymChooserTarget.EMOJI, inCycle))
                SymPageId.SYMBOLS -> rows.add(SymChooserEntry(SymChooserTarget.SYMBOLS, inCycle))
                SymPageId.CLIPBOARD -> rows.add(SymChooserEntry(SymChooserTarget.CLIPBOARD, inCycle))
                SymPageId.EMOJI_PICKER -> {
                    rows.add(SymChooserEntry(SymChooserTarget.EMOJI_PICKER, inCycle))
                    rows.add(SymChooserEntry(SymChooserTarget.KAOMOJI, inCycle))
                    rows.add(SymChooserEntry(SymChooserTarget.UNICODE_SYMBOLS, inCycle))
                }
                SymPageId.GIF -> rows.add(SymChooserEntry(SymChooserTarget.GIF, inCycle))
            }
        }
        return rows
    }

    /** The target the physical letter key [letter] (the QWERTY letter printed on it) opens, or null. */
    fun targetFor(letter: Char): SymChooserTarget? = SymChooserTarget.entries.firstOrNull { it.letter == letter.uppercaseChar() }

    /**
     * spec SS5.10's key table for one key down while the chooser is open. [isRepeat] means an
     * auto-repeat of a key the chooser itself consumed (a held pick key or Shift); any other
     * repeat, such as the Titan's Fn whose first event is already a repeat, counts as a fresh key.
     */
    fun onKeyDown(key: KeyId, isRepeat: Boolean): KeyOutcome = when {
        isRepeat -> KeyOutcome.Swallow
        key == KeyId.Control(ControlKey.BACK) -> KeyOutcome.Dismiss
        key == KeyId.Modifier(ModifierKey.SYM) -> KeyOutcome.Dismiss
        key == KeyId.Modifier(ModifierKey.SHIFT) -> KeyOutcome.Swallow
        key is KeyId.Letter -> targetFor(key.qwertyLetter)?.let { KeyOutcome.Open(it) } ?: KeyOutcome.CloseAndPassOn
        else -> KeyOutcome.CloseAndPassOn
    }
}
