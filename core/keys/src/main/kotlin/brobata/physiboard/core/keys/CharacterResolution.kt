package brobata.physiboard.core.keys

/**
 * Character-lookup helpers shared by [LayerResolver] and [LongPress].
 *
 * spec: layers-sym-alt.md SS9.1 (a layout entry's `lowercase`/`uppercase`/`taps`), SS3.1 (Sym
 * page entries), keys-and-modifiers.md SS7.4 step 10 ("any key whose system unicode character is
 * a letter: commit it"). This module has no platform character map to fall back on, so the
 * "system unicode character" for an unmapped key is taken to be the physical glyph the key's own
 * [KeyId] names: the QWERTY letter, the digit, or the punctuation mark.
 */
object CharacterResolution {

    /** The physical glyph a key names, with no layout, Alt or Sym mapping applied. */
    fun defaultCharacter(key: KeyId): Char? = when (key) {
        is KeyId.Letter -> key.qwertyLetter
        is KeyId.Digit -> key.digit
        is KeyId.Punctuation -> when (key.key) {
            PunctuationKey.GRAVE -> '`'
            PunctuationKey.MINUS -> '-'
            PunctuationKey.EQUALS -> '='
            PunctuationKey.LEFT_BRACKET -> '['
            PunctuationKey.RIGHT_BRACKET -> ']'
            PunctuationKey.BACKSLASH -> '\\'
            PunctuationKey.SEMICOLON -> ';'
            PunctuationKey.APOSTROPHE -> '\''
            PunctuationKey.COMMA -> ','
            PunctuationKey.PERIOD -> '.'
            PunctuationKey.SLASH -> '/'
        }
        is KeyId.Modifier -> null
        is KeyId.Control -> null
    }

    /** [defaultCharacter], cased for [uppercase]; only letters have a case. */
    fun defaultCharacterText(key: KeyId, uppercase: Boolean): String? {
        val ch = defaultCharacter(key) ?: return null
        return if (key is KeyId.Letter) {
            (if (uppercase) ch.uppercaseChar() else ch.lowercaseChar()).toString()
        } else {
            ch.toString()
        }
    }

    /**
     * The layout's own character for [key], honouring multi-tap; `null` when the layout does not
     * define this key at all. spec: layers-sym-alt.md SS9.1 ("the character for a key is
     * `uppercase` when... otherwise `lowercase`. With multi-tap, the tap index selects the
     * entry, wrapping").
     */
    fun baseCharacter(key: KeyId, uppercase: Boolean, tapIndex: Int, layout: LayoutMap): String? {
        val entry = layout[key] ?: return null
        val tap = if (entry.isMultiTap) entry.taps[tapIndex.mod(entry.taps.size)] else null
        return if (tap != null) {
            if (uppercase) tap.uppercase else tap.lowercase
        } else {
            if (uppercase) entry.uppercase else entry.lowercase
        }
    }

    /**
     * [baseCharacter], falling back to the key's own default glyph ([defaultCharacterText]) when
     * the layout has no entry for it at all, for any key that carries a character at all (a
     * letter, a digit or a punctuation mark; [defaultCharacter] is `null` for [KeyId.Modifier]
     * and [KeyId.Control], so this still answers `null` for those and lets the caller fall
     * through). spec: keys-and-modifiers.md SS7.4 steps 9-10 ("an alphabetic keycode mapped in
     * the layout: commit the layout character"; "any key whose system unicode character is a
     * letter: commit it") read on their own as if only letters get this fallback and a digit or
     * punctuation key with no layout entry should fall to step 11's bare "otherwise pass to app".
     * That was tried (a previous revision of this KDoc called it deliberate) and found wrong the
     * same way Space, Enter and Backspace were (see [LayerResolver.withBaselineControlAction]):
     * text-input.md SS5 and SS6 need the deferred-space debt, the double-space period, smart
     * quotes, French spacing and the word-boundary hand-off to see every ordinary character, not
     * only the ones a layout happens to map, and a device whose base layout maps just the 26
     * letters (the Titan 2 Elite's, D1: punctuation and digits normally arrive through the Alt
     * layer instead) left every digit and punctuation keystroke that reaches this function
     * un-mapped, silently skipping `:core:text` and drifting its tracked word out of step with
     * whatever the app actually received. Where keys-and-modifiers.md and text-input.md disagree
     * about a character-bearing key, text-input.md wins, consistent with the other two cases.
     */
    fun layoutOrDefaultCharacter(key: KeyId, uppercase: Boolean, tapIndex: Int, layout: LayoutMap): String? =
        baseCharacter(key, uppercase, tapIndex, layout) ?: defaultCharacterText(key, uppercase)

    /** spec: layers-sym-alt.md SS3.1 ("uppercase entry... used when Shift is active during a Sym chord or long press"). */
    fun symPageEntryText(entry: SymPageEntry?, shiftEffective: Boolean): String? {
        entry ?: return null
        return if (shiftEffective) entry.uppercase ?: entry.lowercase else entry.lowercase
    }
}
