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
     * [baseCharacter], falling back to [defaultCharacterText] only for a letter key with no
     * layout entry. spec: keys-and-modifiers.md SS7.4 steps 9-10 ("an alphabetic keycode mapped
     * in the layout: commit the layout character"; "any key whose system unicode character is a
     * letter: commit it"). A digit or punctuation key with no layout entry is deliberately left
     * unresolved here (step 11: "otherwise pass to app") since only letters get the system-map
     * fallback.
     */
    fun layoutOrLetterFallback(key: KeyId, uppercase: Boolean, tapIndex: Int, layout: LayoutMap): String? =
        baseCharacter(key, uppercase, tapIndex, layout)
            ?: (key as? KeyId.Letter)?.let { defaultCharacterText(key, uppercase) }

    /** spec: layers-sym-alt.md SS3.1 ("uppercase entry... used when Shift is active during a Sym chord or long press"). */
    fun symPageEntryText(entry: SymPageEntry?, shiftEffective: Boolean): String? {
        entry ?: return null
        return if (shiftEffective) entry.uppercase ?: entry.lowercase else entry.lowercase
    }
}
