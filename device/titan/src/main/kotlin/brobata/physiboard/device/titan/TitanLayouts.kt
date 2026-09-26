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
import brobata.physiboard.core.keys.Tap
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
    // The other seventeen bundled layouts. spec: layers-sym-alt.md SS9.2's table names them and
    // gives qwertz's own rule in prose ("Y and Z swapped") but spells out no key map for any of
    // them; SS9.2 also says "ALT, SYM and Ctrl mappings remain based on physical key position"
    // regardless of layout, so every layout below reuses [ALT_LAYER], [EMOJI_PAGE],
    // [SYMBOLS_PAGE], [CTRL_MAPPINGS], [SYM_PAGES_CONFIG] and [VARIATIONS] unchanged and differs
    // only in [LayoutDescription.baseLayout].
    //
    // Derived from the standard (or, where SS9.2 names it, phonetic) national physical keyboard
    // for that language, mapped onto the Titan's 26 QWERTY-position letter keys, since SS9.2 does
    // not spell these tables out: azerty, german_multitap_qwertz, turkish_multitap,
    // norwegian_multitap_qwerty, arabic, armenian_phonetic, bulgarian_phonetic,
    // bulgarian_phonetic_traditional, Cyrillic_Translite, greek, russian_jcuken, russian_standard,
    // russian_translit, serbian_cyrillic and ukrainian. Only qwertz (SS9.2's own "Y and Z
    // swapped") and vietnamese_telex_qwerty (identity; SS10's live Telex composer sits on top of
    // plain QWERTY typing and is out of scope, dropped for 3.0) need no derivation. Where a
    // language has more base letters than fit the 26 physical keys, the overflow is reached by a
    // second (or third) tap on the nearest already-used key, sized to match SS9.2's own
    // "Multi-tap keys" column for that name; each base layout's own comment says which letters
    // that covers and which are simply not reachable on this physical keyboard.
    // -----------------------------------------------------------------------------------------

    fun titanQwertz(): LayoutDescription = layoutWith(QWERTZ_BASE)
    fun titanAzerty(): LayoutDescription = layoutWith(AZERTY_BASE)
    fun titanGermanMultiTapQwertz(): LayoutDescription = layoutWith(GERMAN_MULTITAP_QWERTZ_BASE)
    fun titanTurkishMultiTap(): LayoutDescription = layoutWith(TURKISH_MULTITAP_BASE)
    fun titanNorwegianMultiTapQwerty(): LayoutDescription = layoutWith(NORWEGIAN_MULTITAP_QWERTY_BASE)
    fun titanArabic(): LayoutDescription = layoutWith(ARABIC_BASE)
    fun titanArmenianPhonetic(): LayoutDescription = layoutWith(ARMENIAN_PHONETIC_BASE)
    fun titanBulgarianPhonetic(): LayoutDescription = layoutWith(BULGARIAN_PHONETIC_BASE)
    fun titanBulgarianPhoneticTraditional(): LayoutDescription = layoutWith(BULGARIAN_PHONETIC_TRADITIONAL_BASE)
    fun titanCyrillicTranslite(): LayoutDescription = layoutWith(CYRILLIC_TRANSLITE_BASE)
    fun titanGreek(): LayoutDescription = layoutWith(GREEK_BASE)
    fun titanRussianJcuken(): LayoutDescription = layoutWith(RUSSIAN_JCUKEN_BASE)
    fun titanRussianStandard(): LayoutDescription = layoutWith(RUSSIAN_STANDARD_BASE)
    fun titanRussianTranslit(): LayoutDescription = layoutWith(RUSSIAN_TRANSLIT_BASE)
    fun titanSerbianCyrillic(): LayoutDescription = layoutWith(SERBIAN_CYRILLIC_BASE)
    fun titanUkrainian(): LayoutDescription = layoutWith(UKRAINIAN_BASE)
    fun titanVietnameseTelexQwerty(): LayoutDescription = layoutWith(BASE_LAYOUT)

    /**
     * Every bundled layout id (SS9.2, spelled exactly as its table names them) paired with the
     * locale this build defaults it to and the [LayoutDescription] it types. `:ime` turns this
     * straight into its list of `:core:subtype` `ShippedLayout`s.
     */
    fun bundled(): List<Triple<String, String, LayoutDescription>> = listOf(
        Triple("qwerty", "en_US", titan2EliteQwerty()),
        Triple("qwertz", "de_DE", titanQwertz()),
        Triple("azerty", "fr_FR", titanAzerty()),
        Triple("german_multitap_qwertz", "de_DE", titanGermanMultiTapQwertz()),
        Triple("turkish_multitap", "tr_TR", titanTurkishMultiTap()),
        Triple("norwegian_multitap_qwerty", "no_NO", titanNorwegianMultiTapQwerty()),
        Triple("arabic", "ar", titanArabic()),
        Triple("armenian_phonetic", "hy", titanArmenianPhonetic()),
        Triple("bulgarian_phonetic", "bg", titanBulgarianPhonetic()),
        Triple("bulgarian_phonetic_traditional", "bg", titanBulgarianPhoneticTraditional()),
        Triple("Cyrillic_Translite", "ru_RU", titanCyrillicTranslite()),
        Triple("greek", "el", titanGreek()),
        Triple("russian_jcuken", "ru_RU", titanRussianJcuken()),
        Triple("russian_standard", "ru_RU", titanRussianStandard()),
        Triple("russian_translit", "ru_RU", titanRussianTranslit()),
        Triple("serbian_cyrillic", "sr_RS", titanSerbianCyrillic()),
        Triple("ukrainian", "uk_UA", titanUkrainian()),
        Triple("vietnamese_telex_qwerty", "vi_VN", titanVietnameseTelexQwerty()),
    )

    private fun layoutWith(base: LayoutMap): LayoutDescription = LayoutDescription(
        baseLayout = base,
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
    // Where the spec spells a list out, it is used verbatim. Where it names a base character
    // but not its contents, the list is derived from Unicode itself: every Latin letter whose
    // canonical decomposition is this letter plus one combining mark, ordered grave, acute,
    // diaeresis, circumflex, tilde, ring, ogonek, caron, cedilla. That is neutral ground, and it
    // is checked: the derivation reproduces the spec's own worked examples for `a` and `p`
    // exactly and gives the same set for `e`, so the letters the spec left silent are filled in
    // by the same rule that reproduces the ones it did not.
    // -----------------------------------------------------------------------------------------

    private val VARIATIONS = VariationTable(
        mapOf(
            'a' to listOf("à", "á", "ä", "â", "ã", "å", "ą"),
            'e' to listOf("è", "é", "ê", "ë", "ę", "ě", "€"),
            's' to listOf("ß", "š", "ś", "ș", "ş", "ŝ", "$"),
            'p' to listOf("%"),
            // Derived from Unicode, as described above.
            'i' to listOf("ì", "í", "ï", "î", "ĩ", "į", "ǐ"),
            'o' to listOf("ò", "ó", "ö", "ô", "õ", "ǫ", "ǒ"),
            'u' to listOf("ù", "ú", "ü", "û", "ũ", "ů", "ų"),
            'c' to listOf("ć", "ĉ", "č", "ç", "ċ", "¢"),
            'l' to listOf("ĺ", "ľ", "ļ", "£"),
            'n' to listOf("ǹ", "ń", "ñ", "ň", "ņ"),
            'z' to listOf("ź", "ž", "ż"),
            'y' to listOf("ý", "ÿ", "ŷ", "ȳ", "¥"),
            'd' to listOf("ď"),
            'g' to listOf("ǵ", "ĝ", "ǧ", "ģ", "ğ", "ġ"),
            'r' to listOf("ŕ", "ř", "ŗ"),
            't' to listOf("ť", "ţ", "ț"),
            // Uppercase: the same list, uppercased, keeping the symbols the spec puts on the
            // lowercase key. S also offers the capital eszett the spec names in the qwertz row.
            'A' to listOf("À", "Á", "Ä", "Â", "Ã", "Å", "Ą"),
            'E' to listOf("È", "É", "Ê", "Ë", "Ę", "Ě", "€"),
            'S' to listOf("ẞ", "Š", "Ś", "Ș", "Ş", "Ŝ", "$"),
            'P' to listOf("%"),
            'I' to listOf("Ì", "Í", "Ï", "Î", "Ĩ", "Į", "Ǐ"),
            'O' to listOf("Ò", "Ó", "Ö", "Ô", "Õ", "Ǫ", "Ǒ"),
            'U' to listOf("Ù", "Ú", "Ü", "Û", "Ũ", "Ů", "Ų"),
            'C' to listOf("Ć", "Ĉ", "Č", "Ç", "Ċ", "¢"),
            'L' to listOf("Ĺ", "Ľ", "Ļ", "£"),
            'N' to listOf("Ǹ", "Ń", "Ñ", "Ň", "Ņ"),
            'Z' to listOf("Ź", "Ž", "Ż"),
            'Y' to listOf("Ý", "Ÿ", "Ŷ", "Ȳ", "¥"),
            'D' to listOf("Ď"),
            'G' to listOf("Ǵ", "Ĝ", "Ǧ", "Ģ", "Ğ", "Ġ"),
            'R' to listOf("Ŕ", "Ř", "Ŗ"),
            'T' to listOf("Ť", "Ţ", "Ț"),
            'е' to listOf("ё", "є"), // Cyrillic е -> ё, є
            'Е' to listOf("Ё"), // Cyrillic Е -> Ё
            'Р' to listOf("₽"), // Cyrillic Р -> ₽
            'Դ' to listOf("֏"), // Armenian Դ -> ֏
        ),
    )

    // -----------------------------------------------------------------------------------------
    // Base layouts for the other seventeen bundled names (SS9.2). Helpers first, then one
    // `..._BASE` per row of that table, in the table's own order.
    // -----------------------------------------------------------------------------------------

    /** One physical row's lowercase entries, uppercased with the locale-free default unless [uppers] overrides it (for a character whose real capital that rule gets wrong, e.g. Turkish dotless i). */
    private fun baseRow(letters: String, values: List<String>, uppers: List<String>? = null): Map<KeyId, LetterEntry> {
        require(letters.length == values.size) { "row/value length mismatch for '$letters'" }
        val upperValues = uppers ?: values.map { it.uppercase() }
        require(upperValues.size == values.size)
        return letters.toList().zip(values.zip(upperValues)).associate { (letter, pair) ->
            KeyId.Letter(letter) to LetterEntry(lowercase = pair.first, uppercase = pair.second)
        }
    }

    /** A full 26-key base layout from its three physical rows' output characters, in [TOP_ROW]/[HOME_ROW]/[BOTTOM_ROW] order. */
    private fun baseLayout(top: List<String>, home: List<String>, bottom: List<String>): LayoutMap =
        LayoutMap(baseRow(TOP_ROW, top) + baseRow(HOME_ROW, home) + baseRow(BOTTOM_ROW, bottom))

    /** One multi-tap key: [taps] in order, tap 1 also standing in for the entry's own top-level `lowercase`/`uppercase` (SS9.1's shape, matching `HoldAndRepeatTest`'s own fixtures). */
    private fun multiTap(letter: Char, vararg taps: Pair<String, String>): Pair<KeyId, LetterEntry> {
        require(taps.size >= 2) { "a multi-tap key needs at least two taps, letter was '$letter'" }
        val tapList = taps.map { Tap(it.first, it.second) }
        return KeyId.Letter(letter) to LetterEntry(lowercase = tapList[0].lowercase, uppercase = tapList[0].uppercase, taps = tapList)
    }

    /** [this] with some of its keys replaced by a multi-tap entry (SS9.1's `taps`), everything else unchanged. */
    private fun LayoutMap.withMultiTap(vararg overrides: Pair<KeyId, LetterEntry>): LayoutMap = LayoutMap(entries + overrides)

    /** SS9.2: "Y and Z swapped". Every other key is qwerty's own identity mapping. */
    private val QWERTZ_BASE: LayoutMap = baseLayout(
        top = listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p"),
        home = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        bottom = listOf("y", "x", "c", "v", "b", "n", "m"),
    )

    /**
     * Derived: the standard French AZERTY keyboard's own physical positions, restricted to the 26
     * QWERTY-analogous letter keys (the digit-row and bracket-key AZERTY letters have no physical
     * home here). Differs from qwerty on exactly five keys: Q/A swap, W/Z swap, and M becomes the
     * comma AZERTY prints there.
     */
    private val AZERTY_BASE: LayoutMap = baseLayout(
        top = listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
        home = listOf("q", "s", "d", "f", "g", "h", "j", "k", "l"),
        bottom = listOf("w", "x", "c", "v", "b", "n", ","),
    )

    /**
     * Derived: [QWERTZ_BASE] with the five umlaut/eszett/currency keys SS9.2's "ä/ö/ü/ß/€" note
     * (SS10) names as this layout's own reordered variation set, each reached by a second tap of
     * its plain-letter key (A, O, U, S, E), matching the table's "Multi-tap keys 5".
     */
    private val GERMAN_MULTITAP_QWERTZ_BASE: LayoutMap = QWERTZ_BASE.withMultiTap(
        multiTap('A', "a" to "A", "ä" to "Ä"),
        multiTap('O', "o" to "O", "ö" to "Ö"),
        multiTap('U', "u" to "U", "ü" to "Ü"),
        multiTap('S', "s" to "S", "ß" to "ẞ"),
        multiTap('E', "e" to "E", "€" to "€"),
    )

    /**
     * Derived: qwerty's own identity, plus a second tap on the six keys that reach Turkish's own
     * extra letters (ç, ğ, ö, ş, ü, and the dotless/dotted ı/İ pair sharing the I key), plus a
     * seventh (A) for the circumflex â some Turkish words still distinguish with (e.g. "kâğıt").
     * Matches the table's "Multi-tap keys 7". Tap 1 of I is the dotless ı/I a plain Turkish "I"
     * key gives; tap 2 is dotted i/İ.
     */
    private val TURKISH_MULTITAP_BASE: LayoutMap = BASE_LAYOUT.withMultiTap(
        multiTap('A', "a" to "A", "â" to "Â"),
        multiTap('C', "c" to "C", "ç" to "Ç"),
        multiTap('G', "g" to "G", "ğ" to "Ğ"),
        multiTap('I', "ı" to "I", "i" to "İ"),
        multiTap('O', "o" to "O", "ö" to "Ö"),
        multiTap('S', "s" to "S", "ş" to "Ş"),
        multiTap('U', "u" to "U", "ü" to "Ü"),
    )

    /**
     * Derived: qwerty's own identity, plus a second tap on A and O for å and ø (SS10's "å/æ/ø"
     * reordering note also covers æ, but that stays reachable only through the long-press
     * variation list, not a base-layout tap, to match the table's "Multi-tap keys 2").
     */
    private val NORWEGIAN_MULTITAP_QWERTY_BASE: LayoutMap = BASE_LAYOUT.withMultiTap(
        multiTap('A', "a" to "A", "å" to "Å"),
        multiTap('O', "o" to "O", "ø" to "Ø"),
    )

    /**
     * Derived: the standard Arabic keyboard (often called "Arabic (101)"), whose 26 physical
     * positions already match the Titan's letter row for row. Ten more Arabic letters that this
     * standard layout puts on non-letter (digit-row/bracket) keys with no physical home here
     * instead ride a second tap of the nearest already-used letter key, matching the table's
     * "Multi-tap keys 10": و ز ج د ك ط ظ ذ (the eight letters the direct 26 keys leave out) and
     * أ إ (the alif hamza forms; the base already carries ا, ء, ؤ, ئ). Arabic has no letter case,
     * so lowercase and uppercase are always the same string.
     */
    private val ARABIC_DIRECT: LayoutMap = baseLayout(
        top = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح"),
        home = listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م"),
        bottom = listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة"),
    )
    private val ARABIC_BASE: LayoutMap = ARABIC_DIRECT.withMultiTap(
        multiTap('W', "ص" to "ص", "و" to "و"),
        multiTap('Z', "ئ" to "ئ", "ز" to "ز"),
        multiTap('J', "ت" to "ت", "ج" to "ج"),
        multiTap('D', "ي" to "ي", "د" to "د"),
        multiTap('K', "ن" to "ن", "ك" to "ك"),
        multiTap('T', "ف" to "ف", "ط" to "ط"),
        multiTap('X', "ء" to "ء", "ظ" to "ظ"),
        multiTap('C', "ؤ" to "ؤ", "ذ" to "ذ"),
        multiTap('A', "ش" to "ش", "أ" to "أ"),
        multiTap('I', "ه" to "ه", "إ" to "إ"),
    )

    /**
     * Derived: the standard Greek keyboard (Q types the Greek question mark ";" rather than a
     * letter; W is the final-sigma ς, a letter of its own on this layout, not a tap of σ), plus a
     * second tap on each of the seven vowel keys (α ε η ι ο υ ω) for its own tonos-accented form,
     * matching the table's "Multi-tap keys 7".
     */
    private val GREEK_DIRECT: LayoutMap = baseLayout(
        top = listOf(";", "ς", "ε", "ρ", "τ", "υ", "θ", "ι", "ο", "π"),
        home = listOf("α", "σ", "δ", "φ", "γ", "η", "ξ", "κ", "λ"),
        bottom = listOf("ζ", "χ", "ψ", "ω", "β", "ν", "μ"),
    )
    private val GREEK_BASE: LayoutMap = GREEK_DIRECT.withMultiTap(
        multiTap('A', "α" to "Α", "ά" to "Ά"),
        multiTap('E', "ε" to "Ε", "έ" to "Έ"),
        multiTap('H', "η" to "Η", "ή" to "Ή"),
        multiTap('I', "ι" to "Ι", "ί" to "Ί"),
        multiTap('O', "ο" to "Ο", "ό" to "Ό"),
        multiTap('U', "υ" to "Υ", "ύ" to "Ύ"),
        multiTap('V', "ω" to "Ω", "ώ" to "Ώ"),
    )

    /**
     * Derived: the standard Russian ЙЦУКЕН keyboard's own 26 letter-key positions (shared by
     * [RUSSIAN_JCUKEN_BASE] and [RUSSIAN_STANDARD_BASE] below); the seven Cyrillic letters a real
     * ЙЦУКЕН keyboard puts past these 26 positions (х ъ ж э б ю, plus ё on its own dedicated key)
     * have no physical home here at all in this direct form.
     */
    private val RUSSIAN_DIRECT: LayoutMap = baseLayout(
        top = listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з"),
        home = listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д"),
        bottom = listOf("я", "ч", "с", "м", "и", "т", "ь"),
    )

    /**
     * Derived: [RUSSIAN_DIRECT] plus a second tap on four keys for the most-needed of the seven
     * overflow letters ([RUSSIAN_DIRECT]'s own KDoc), matching the table's "Multi-tap keys 4".
     */
    private val RUSSIAN_STANDARD_BASE: LayoutMap = RUSSIAN_DIRECT.withMultiTap(
        multiTap('Q', "й" to "Й", "ё" to "Ё"),
        multiTap('K', "л" to "Л", "э" to "Э"),
        multiTap('L', "д" to "Д", "ж" to "Ж"),
        multiTap('M', "ь" to "Ь", "ю" to "Ю"),
    )

    /**
     * Derived: [RUSSIAN_DIRECT] plus a second tap on all seven keys the [RUSSIAN_DIRECT] overflow
     * needs ("compact" ЙЦУКЕН, packing every Russian letter onto the 26 physical keys), matching
     * the table's "Multi-tap keys 7".
     */
    private val RUSSIAN_JCUKEN_BASE: LayoutMap = RUSSIAN_DIRECT.withMultiTap(
        multiTap('Q', "й" to "Й", "ё" to "Ё"),
        multiTap('K', "л" to "Л", "э" to "Э"),
        multiTap('L', "д" to "Д", "ж" to "Ж"),
        multiTap('M', "ь" to "Ь", "ю" to "Ю"),
        multiTap('N', "т" to "Т", "б" to "Б"),
        multiTap('O', "щ" to "Щ", "ъ" to "Ъ"),
        multiTap('P', "з" to "З", "х" to "Х"),
    )

    /**
     * Derived: a phonetic Latin-to-Cyrillic scheme (each Latin letter's own closest Cyrillic
     * sound; Q and W, unused by direct phonetic sounds, carry я and ю; X carries the "ks" digraph
     * Latin transliteration commonly gives it), plus a second (or third) tap on six keys for the
     * sounds one Latin letter cannot reach alone, matching the table's "Multi-tap keys 6".
     */
    private val RUSSIAN_TRANSLIT_DIRECT: LayoutMap = baseLayout(
        top = listOf("я", "ю", "е", "р", "т", "ы", "у", "и", "о", "п"),
        home = listOf("а", "с", "д", "ф", "г", "х", "й", "к", "л"),
        bottom = listOf("з", "кс", "ц", "в", "б", "н", "м"),
    )
    private val RUSSIAN_TRANSLIT_BASE: LayoutMap = RUSSIAN_TRANSLIT_DIRECT.withMultiTap(
        multiTap('C', "ц" to "Ц", "ч" to "Ч"),
        multiTap('S', "с" to "С", "ш" to "Ш", "щ" to "Щ"),
        multiTap('Z', "з" to "З", "ж" to "Ж"),
        multiTap('E', "е" to "Е", "э" to "Э", "ё" to "Ё"),
        multiTap('L', "л" to "Л", "ь" to "Ь"),
        multiTap('Y', "ы" to "Ы", "ъ" to "Ъ"),
    )

    /**
     * Derived: [RUSSIAN_TRANSLIT_DIRECT] with one more multi-tap key (H, otherwise the plain "х")
     * for щ, a generic pan-Cyrillic transliteration scheme rather than Russian-specific, matching
     * the table's "Multi-tap keys 7".
     */
    private val CYRILLIC_TRANSLITE_BASE: LayoutMap = RUSSIAN_TRANSLIT_BASE.withMultiTap(
        multiTap('H', "х" to "Х", "щ" to "Щ"),
    )

    /**
     * Derived: Serbian Cyrillic's own 1:1 correspondence with Serbian Latin (each Cyrillic letter
     * types on the Latin key its Latin counterpart would use; the four free Latin keys Serbian's
     * alphabet has no direct use for, Q/W/X/Y, carry џ/њ/ђ/ћ directly), plus a second tap on four
     * keys for the digraph-adjacent sounds č/š/ž/lj, matching the table's "Multi-tap keys 4".
     */
    private val SERBIAN_CYRILLIC_DIRECT: LayoutMap = baseLayout(
        top = listOf("џ", "њ", "е", "р", "т", "ћ", "у", "и", "о", "п"),
        home = listOf("а", "с", "д", "ф", "г", "х", "ј", "к", "л"),
        bottom = listOf("з", "ђ", "ц", "в", "б", "н", "м"),
    )
    private val SERBIAN_CYRILLIC_BASE: LayoutMap = SERBIAN_CYRILLIC_DIRECT.withMultiTap(
        multiTap('C', "ц" to "Ц", "ч" to "Ч"),
        multiTap('S', "с" to "С", "ш" to "Ш"),
        multiTap('Z', "з" to "З", "ж" to "Ж"),
        multiTap('L', "л" to "Л", "љ" to "Љ"),
    )

    /**
     * Derived: the standard Ukrainian ЙЦУКЕН keyboard (identical to [RUSSIAN_DIRECT] except S
     * types і, not ы, since Ukrainian has no ы), plus a second tap on six keys for six of the
     * seven letters a full Ukrainian keyboard puts past these 26 positions, matching the table's
     * "Multi-tap keys 6" (ґ, the seventh, has no physical home in this build at all).
     */
    private val UKRAINIAN_DIRECT: LayoutMap = baseLayout(
        top = listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з"),
        home = listOf("ф", "і", "в", "а", "п", "р", "о", "л", "д"),
        bottom = listOf("я", "ч", "с", "м", "и", "т", "ь"),
    )
    private val UKRAINIAN_BASE: LayoutMap = UKRAINIAN_DIRECT.withMultiTap(
        multiTap('O', "щ" to "Щ", "ї" to "Ї"),
        multiTap('P', "з" to "З", "х" to "Х"),
        multiTap('L', "д" to "Д", "ж" to "Ж"),
        multiTap('K', "л" to "Л", "є" to "Є"),
        multiTap('M', "ь" to "Ь", "б" to "Б"),
        multiTap('N', "т" to "Т", "ю" to "Ю"),
    )

    /**
     * Derived: a Bulgarian phonetic scheme (each Latin letter's closest Cyrillic sound). Four
     * Bulgarian letters (ч ш щ ъ) have no phonetic Latin key left free and are simply not
     * reachable through this layout's base characters at all, matching the table's own "Multi-tap
     * keys 0" for this name; [BULGARIAN_PHONETIC_TRADITIONAL_BASE] below is the variant that
     * reaches them.
     */
    private val BULGARIAN_PHONETIC_BASE: LayoutMap = baseLayout(
        top = listOf("я", "в", "е", "р", "т", "у", "ю", "и", "о", "п"),
        home = listOf("а", "с", "д", "ф", "г", "х", "й", "к", "л"),
        bottom = listOf("з", "ь", "ц", "ж", "б", "н", "м"),
    )

    /** Derived: [BULGARIAN_PHONETIC_BASE] plus a second tap on four keys for ъ/ч/ш/щ, matching the table's "Multi-tap keys 4". */
    private val BULGARIAN_PHONETIC_TRADITIONAL_BASE: LayoutMap = BULGARIAN_PHONETIC_BASE.withMultiTap(
        multiTap('A', "а" to "А", "ъ" to "Ъ"),
        multiTap('C', "ц" to "Ц", "ч" to "Ч"),
        multiTap('S', "с" to "С", "ш" to "Ш"),
        multiTap('K', "к" to "К", "щ" to "Щ"),
    )

    /**
     * Derived: the "Armenian Phonetic" scheme several platforms ship (each Latin letter's closest
     * Armenian sound), plus a second (or, for J, third) tap on nine keys for the aspirated/related
     * member of each sound pair Armenian distinguishes that a plain Latin letter cannot (see
     * T/K/P/C's aspirate triads and J's ch/ch' pair), matching the table's "Multi-tap keys 9".
     * Confidence here is lower than the other derived layouts: no shipped reference table exists
     * for Armenian at all, only the language name.
     */
    private val ARMENIAN_PHONETIC_DIRECT: LayoutMap = baseLayout(
        top = listOf("ղ", "և", "ե", "ր", "տ", "ը", "ւ", "ի", "ո", "պ"),
        home = listOf("ա", "ս", "դ", "ֆ", "գ", "հ", "ջ", "կ", "լ"),
        bottom = listOf("զ", "խ", "ծ", "վ", "բ", "ն", "մ"),
    )
    private val ARMENIAN_PHONETIC_BASE: LayoutMap = ARMENIAN_PHONETIC_DIRECT.withMultiTap(
        multiTap('T', "տ" to "Տ", "թ" to "Թ"),
        multiTap('K', "կ" to "Կ", "ք" to "Ք"),
        multiTap('P', "պ" to "Պ", "փ" to "Փ"),
        multiTap('C', "ծ" to "Ծ", "ց" to "Ց"),
        multiTap('J', "ջ" to "Ջ", "ճ" to "Ճ", "չ" to "Չ"),
        multiTap('Z', "զ" to "Զ", "ձ" to "Ձ"),
        multiTap('E', "ե" to "Ե", "է" to "Է"),
        multiTap('O', "ո" to "Ո", "օ" to "Օ"),
        multiTap('S', "ս" to "Ս", "շ" to "Շ"),
    )
}
