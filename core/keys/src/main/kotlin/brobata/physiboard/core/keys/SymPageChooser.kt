package brobata.physiboard.core.keys

/**
 * Where one row of the Sym page chooser leads. Kaomoji and Unicode symbols are modes of the
 * picker page (4), reachable here on their own because the chooser is the only direct way in
 * now that the strip's page buttons are gone. spec: layers-sym-alt.md SS5.10.
 */
enum class SymChooserTarget(val letter: Char, val label: String, val page: SymPageId) {
    /** The key layer with one emoji per letter key. The picker is the page called "Emoji". */
    EMOJI('E', "Emoji keys", SymPageId.EMOJI),
    SYMBOLS('S', "Symbols", SymPageId.SYMBOLS),
    CLIPBOARD('C', "Clipboard", SymPageId.CLIPBOARD),
    EMOJI_PICKER('P', "Emoji", SymPageId.EMOJI_PICKER),
    KAOMOJI('K', "Kaomoji", SymPageId.EMOJI_PICKER),
    UNICODE_SYMBOLS('U', "Unicode symbols", SymPageId.EMOJI_PICKER),
    GIF('G', "GIFs", SymPageId.GIF),

    /** The user's own pages (SS4.6): M for "My page", then the two keys beside it. */
    CUSTOM_1('M', "My page 1", SymPageId.CUSTOM_1),
    CUSTOM_2('N', "My page 2", SymPageId.CUSTOM_2),
    CUSTOM_3('B', "My page 3", SymPageId.CUSTOM_3),
}

/**
 * One chooser row: the target, whether its page is in the Sym cycle (shown, not obeyed), and the
 * name shown for it (the user's own name for one of their pages, else the target's label).
 */
data class SymChooserEntry(val target: SymChooserTarget, val inCycle: Boolean, val label: String = target.label)

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
     *
     * [customPageNames] names the user's own pages that are set up (switched on, or holding at
     * least one key); one missing from it has no row, so three empty "My page" rows never crowd
     * the chooser. A blank name shows the page's default name.
     *
     * Kaomoji has a row only when [kaomojiEnabled] (`emoji_picker_kaomoji`, off by default): the
     * user who never asked for kaomoji never meets them. Unicode symbols always keep theirs, the
     * secondary way into the picker's symbol search.
     */
    fun entries(config: SymPagesConfig, customPageNames: Map<SymPageId, String> = emptyMap(), kaomojiEnabled: Boolean = false): List<SymChooserEntry> {
        val rows = ArrayList<SymChooserEntry>()
        for (page in config.normalizedOrder) {
            val inCycle = config.isEnabled(page)
            when (page) {
                SymPageId.EMOJI -> rows.add(SymChooserEntry(SymChooserTarget.EMOJI, inCycle))
                SymPageId.SYMBOLS -> rows.add(SymChooserEntry(SymChooserTarget.SYMBOLS, inCycle))
                SymPageId.CLIPBOARD -> rows.add(SymChooserEntry(SymChooserTarget.CLIPBOARD, inCycle))
                SymPageId.EMOJI_PICKER -> {
                    rows.add(SymChooserEntry(SymChooserTarget.EMOJI_PICKER, inCycle))
                    if (kaomojiEnabled) rows.add(SymChooserEntry(SymChooserTarget.KAOMOJI, inCycle))
                    rows.add(SymChooserEntry(SymChooserTarget.UNICODE_SYMBOLS, inCycle))
                }
                SymPageId.GIF -> rows.add(SymChooserEntry(SymChooserTarget.GIF, inCycle))
                SymPageId.CUSTOM_1, SymPageId.CUSTOM_2, SymPageId.CUSTOM_3 -> {
                    val name = customPageNames[page] ?: continue
                    val target = SymChooserTarget.entries.first { it.page == page }
                    rows.add(SymChooserEntry(target, inCycle, name.trim().ifEmpty { target.label }))
                }
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
     * [listed] is the targets the chooser shows: a letter whose row is not shown (one of the
     * user's own pages that is not set up) closes the chooser and types, like any other letter.
     */
    fun onKeyDown(key: KeyId, isRepeat: Boolean, listed: Set<SymChooserTarget> = SymChooserTarget.entries.toSet()): KeyOutcome = when {
        isRepeat -> KeyOutcome.Swallow
        key == KeyId.Control(ControlKey.BACK) -> KeyOutcome.Dismiss
        key == KeyId.Modifier(ModifierKey.SYM) -> KeyOutcome.Dismiss
        key == KeyId.Modifier(ModifierKey.SHIFT) -> KeyOutcome.Swallow
        key is KeyId.Letter -> targetFor(key.qwertyLetter)?.takeIf { it in listed }?.let { KeyOutcome.Open(it) } ?: KeyOutcome.CloseAndPassOn
        else -> KeyOutcome.CloseAndPassOn
    }
}
