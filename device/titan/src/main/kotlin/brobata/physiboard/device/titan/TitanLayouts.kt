package brobata.physiboard.device.titan

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.CtrlMapping
import brobata.physiboard.core.keys.CtrlMappingTable
import brobata.physiboard.core.keys.DeviceLayerMap
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.LayoutDescription
import brobata.physiboard.core.keys.LayoutMap
import brobata.physiboard.core.keys.LetterEntry
import brobata.physiboard.core.keys.SymPageEntry
import brobata.physiboard.core.keys.SymPageMap
import brobata.physiboard.core.keys.SymPagesConfig
import brobata.physiboard.core.keys.VariationTable

/**
 * The Titan 2 Elite's shipped `qwerty` layout, handed to `:core:keys` as plain data.
 *
 * `:core:keys` deliberately loads nothing of its own (`LayoutDescription.kt`); this object is the
 * value a caller (`:ime`, or a test) hands it for the Titan 2 Elite. 3.0 is Elite-only
 * (layers-sym-alt.md SS15: the non-Elite `titan2` legend is "undecided" and the virtual on-screen
 * Alt file is "drop"; keys-and-modifiers.md SS22 drops `physical_keyboard_profile_override`
 * outright), so only this one device's `qwerty` layout is authored here.
 *
 * spec: layers-sym-alt.md SS3.2 (the Elite's Alt/device-layer table, D3), SS3.3 (the shipped Emoji
 * and Symbols Sym page tables), SS9.1-9.2 (the `qwerty` bundled layout is the identity map, no
 * multi-tap keys), SS8.1 (the shipped variation lists); keys-and-modifiers.md SS12.3 (the shipped
 * Fn Layer Ctrl mappings).
 */
object TitanLayouts {

    /** spec: layers-sym-alt.md SS3.2, SS3.3, SS9.1-9.2, SS8.1; keys-and-modifiers.md SS12.3. */
    fun titan2EliteQwerty(): LayoutDescription = LayoutDescription(
        baseLayout = BASE_LAYOUT,
        deviceLayer = ALT_LAYER,
        emojiPage = EMOJI_PAGE,
        symbolsPage = SYMBOLS_PAGE,
        ctrlMappings = CTRL_MAPPINGS,
        symPagesConfig = SYM_PAGES_CONFIG,
        variations = VARIATIONS,
    )

    // -----------------------------------------------------------------------------------------
    // Physical rows, named once so every table below is built and checked against the same
    // key order the spec's own tables print them in (layers-sym-alt.md SS3.2, SS3.3, SS5.7).
    // -----------------------------------------------------------------------------------------

    private const val TOP_ROW = "QWERTYUIOP"
    private const val HOME_ROW = "ASDFGHJKL"
    private const val BOTTOM_ROW = "ZXCVBNM"

    private fun deviceRow(letters: String, values: List<String>): Map<KeyId, String> {
        require(letters.length == values.size) { "row/value length mismatch for '$letters'" }
        return letters.toList().zip(values).associate { (letter, value) -> KeyId.Letter(letter) to value }
    }

    private fun symRow(letters: String, values: List<String>): Map<KeyId, SymPageEntry> {
        require(letters.length == values.size) { "row/value length mismatch for '$letters'" }
        return letters.toList().zip(values).associate { (letter, value) -> KeyId.Letter(letter) to SymPageEntry(lowercase = value) }
    }

    // -----------------------------------------------------------------------------------------
    // Base layout. spec: layers-sym-alt.md SS9.1 ("uppercase"/"lowercase" per entry), SS9.2
    // ("qwerty ... identity mapping ... Multi-tap keys 0").
    // -----------------------------------------------------------------------------------------

    private val BASE_LAYOUT = LayoutMap(
        ('A'..'Z').associate { letter ->
            KeyId.Letter(letter) to LetterEntry(lowercase = letter.lowercaseChar().toString(), uppercase = letter.toString())
        },
    )

    // -----------------------------------------------------------------------------------------
    // Alt (device) layer. spec: layers-sym-alt.md SS3.2 `titan2elite_qwerty` table, D3.
    // -----------------------------------------------------------------------------------------

    private val ALT_LAYER = DeviceLayerMap(
        deviceRow(TOP_ROW, listOf("0", "1", "2", "3", "(", ")", "_", "-", "+", "@")) +
            deviceRow(HOME_ROW, listOf("*", "4", "5", "6", "/", ":", "#", "'", "\"")) +
            deviceRow(BOTTOM_ROW, listOf("7", "8", "9", "?", "!", ",", ".")),
    )

    // -----------------------------------------------------------------------------------------
    // Sym pages. spec: layers-sym-alt.md SS3.3. Both shipped files use the plain-string form, so
    // every entry's uppercase map is empty (SymPageEntry.uppercase left null, SS3.1).
    // -----------------------------------------------------------------------------------------

    private val EMOJI_PAGE = SymPageMap(
        symRow(TOP_ROW, listOf("😀", "😂", "😍", "😊", "😎", "👍", "❤️", "😘", "😡", "😉")) +
            symRow(HOME_ROW, listOf("😢", "😭", "😱", "😰", "😴", "🤔", "🤢", "🙄", "😌")) +
            symRow(BOTTOM_ROW, listOf("😔", "🥳", "😅", "🤗", "🥰", "👎", "😳")),
    )

    private val SYMBOLS_PAGE = SymPageMap(
        symRow(TOP_ROW, listOf("~", "`", "{", "}", "[", "]", "<", ">", "°", "%")) +
            symRow(HOME_ROW, listOf("=", ";", "±", "–", "\\", "|", "„", "“", "”")) +
            symRow(BOTTOM_ROW, listOf("»", "«", "&", "^", "¡", "§", "$")),
    )

    /**
     * spec: layers-sym-alt.md SS1, SS4.1: only Emoji and Symbols are key layers left in 3.0 (the
     * Device page is dropped, SS15), so this is exactly [SymPagesConfig]'s own schema default; it
     * is spelled out here rather than relying on the parameter default so the order it encodes
     * (Emoji, Symbols, Clipboard, Emoji Picker) is visibly the one SS4.1's default `symPageOrder`
     * gives once `device` is dropped.
     */
    private val SYM_PAGES_CONFIG = SymPagesConfig(
        emojiEnabled = true,
        symbolsEnabled = true,
        clipboardEnabled = false,
        emojiPickerEnabled = false,
    )

    // -----------------------------------------------------------------------------------------
    // Ctrl (Fn Layer) mappings. spec: keys-and-modifiers.md SS12.3 shipped defaults table.
    // -----------------------------------------------------------------------------------------

    private fun keycode(key: ControlKey) = CtrlMapping.Keycode(KeyId.Control(key))
    private fun action(actionId: String) = CtrlMapping.NamedAction(actionId)

    private val CTRL_MAPPINGS = CtrlMappingTable(
        mapOf(
            KeyId.Letter('Q') to keycode(ControlKey.ESCAPE),
            KeyId.Letter('W') to action("expand_selection_left"),
            KeyId.Letter('E') to keycode(ControlKey.DPAD_UP),
            KeyId.Letter('R') to action("expand_selection_right"),
            KeyId.Letter('T') to keycode(ControlKey.TAB),
            KeyId.Letter('Y') to keycode(ControlKey.PAGE_UP),
            KeyId.Letter('U') to action("expand_selection_word_left"),
            KeyId.Letter('I') to action("expand_selection_word_right"),
            KeyId.Letter('O') to keycode(ControlKey.DPAD_CENTER),
            // spec keys-and-modifiers.md SS12.3: mapped to an action id SS7.3's table does not
            // implement, so this falls through to the app as shipped (a stale default, per spec).
            KeyId.Letter('P') to action("toggle_minimal_ui"),
            KeyId.Letter('A') to action("select_all"),
            KeyId.Letter('S') to keycode(ControlKey.DPAD_LEFT),
            KeyId.Letter('D') to keycode(ControlKey.DPAD_DOWN),
            KeyId.Letter('F') to keycode(ControlKey.DPAD_RIGHT),
            KeyId.Letter('G') to CtrlMapping.None,
            KeyId.Letter('H') to keycode(ControlKey.PAGE_DOWN),
            KeyId.Letter('J') to keycode(ControlKey.DPAD_LEFT),
            KeyId.Letter('K') to keycode(ControlKey.DPAD_DOWN),
            KeyId.Letter('L') to keycode(ControlKey.DPAD_RIGHT),
            KeyId.Letter('Z') to action("undo"),
            KeyId.Letter('X') to action("cut"),
            KeyId.Letter('C') to action("copy"),
            KeyId.Letter('V') to action("paste"),
            // Ctrl+B toggled the software keyboard in 2.x. 3.0 has no software keyboard at
            // all, so the key is left unmapped rather than pointed at something that cannot
            // happen. See docs/plans/rebuild-from-scratch.md, decision 2.
            KeyId.Letter('N') to action("move_word_left"),
            KeyId.Letter('M') to action("move_word_right"),
        ),
    )

    // -----------------------------------------------------------------------------------------
    // Variations. spec: layers-sym-alt.md SS8.1, SS8.2. This layout is unmodified `qwerty`, which
    // has no entry in the shipped `layoutVariationOverrides` (only qwertz, german_multitap_qwertz
    // and norwegian_multitap_qwerty do), so the effective table for this layout is the base
    // `variations` map with no override merge (SS8.2 step 2 has nothing to merge in).
    //
    // SPEC GAP: SS8.1 names i, o, u, l, c, n, z, y, d, g, r, t (lowercase and uppercase) and the
    // uppercase forms of a, e, s, p as shipped base characters too ("Shipped base characters: a e
    // i o u l c n s z y d g r p t (lowercase and uppercase)..."), but only gives the exact list
    // contents for lowercase a, e, s, p and for the named Cyrillic/Armenian characters. Rather
    // than invent contents for the rest (which would mean guessing at, not reading, an asset this
    // task's clean room does not admit), this table encodes only the characters whose lists the
    // spec spells out in full; the omission is reported back rather than filled in.
    // -----------------------------------------------------------------------------------------

    private val VARIATIONS = VariationTable(
        mapOf(
            'a' to listOf("à", "á", "ä", "â", "ã", "å", "ą"),
            'e' to listOf("è", "é", "ê", "ë", "ę", "ě", "€"),
            's' to listOf("ß", "š", "ś", "ș", "ş", "ŝ", "$"),
            'p' to listOf("%"),
            'е' to listOf("ё", "є"), // Cyrillic е -> ё, є
            'Е' to listOf("Ё"), // Cyrillic Е -> Ё
            'Р' to listOf("₽"), // Cyrillic Р -> ₽
            'Դ' to listOf("֏"), // Armenian Դ -> ֏
        ),
    )
}
