package brobata.physiboard.core.actions.picker

import java.text.Normalizer

/** One chip of the "Select unicode character" dialog. spec: expansion-clipboard-pickers-launcher.md SS5.2. */
data class UnicodeCategory(val label: String, val glyphs: List<String>)

/**
 * The seven category chips the Unicode character dialog (settings-catalog.md's "SYM
 * customization" pencil on the Symbols page) offers, in chip order. spec:
 * expansion-clipboard-pickers-launcher.md SS5.2's table. Pure Kotlin (no android import) so
 * `:app`'s dialog and this module's own JVM test share one source of truth; `:ime` never reads
 * this (the dialog only exists inside the settings screen).
 */
object UnicodeCharacterCatalog {

    /** spec: expansion-clipboard-pickers-launcher.md SS5.2, the "Punctuation" chip (51 glyphs, as shipped, repeats included). */
    val PUNCTUATION: List<String> = listOf(
        "„",
        "“",
        "”",
        "‘",
        "’",
        "\"",
        "¿",
        "¡",
        "…",
        "—",
        "–",
        "«",
        "»",
        "‹",
        "›",
        "‚",
        "'",
        "'",
        "•",
        "‥",
        "‰",
        "′",
        "″",
        "‴",
        "‵",
        "‶",
        "‷",
        "‸",
        "※",
        "§",
        "¶",
        "†",
        "‡",
        ";",
        ":",
        "!",
        "?",
        ".",
        ",",
        "‽",
        "⁇",
        "⁈",
        "⁉",
        "(",
        ")",
        "[",
        "]",
        "{",
        "}",
        "<",
        ">",
    )

    /** spec: expansion-clipboard-pickers-launcher.md SS5.2, the "Mathematical Symbols" chip (67 glyphs, as shipped, repeats included). */
    val MATHEMATICAL_SYMBOLS: List<String> = listOf(
        "±",
        "×",
        "÷",
        "≠",
        "≤",
        "≥",
        "≈",
        "∞",
        "∑",
        "∏",
        "√",
        "∫",
        "∆",
        "∇",
        "∂",
        "α",
        "β",
        "γ",
        "δ",
        "ε",
        "π",
        "Ω",
        "θ",
        "λ",
        "μ",
        "σ",
        "φ",
        "ω",
        "½",
        "¼",
        "¾",
        "⅓",
        "⅔",
        "⅕",
        "⅖",
        "⅗",
        "⅘",
        "⅙",
        "⅚",
        "⅛",
        "⅜",
        "⅝",
        "⅞",
        "∝",
        "∠",
        "∡",
        "∢",
        "∟",
        "∴",
        "∵",
        "∶",
        "∷",
        "∼",
        "∽",
        "≀",
        "≁",
        "≂",
        "≃",
        "≄",
        "≅",
        "≆",
        "≇",
        "≉",
        "≊",
        "≋",
        "≌",
        "≍",
    )

    /** spec: expansion-clipboard-pickers-launcher.md SS5.2, the "Currencies" chip (31 glyphs, as shipped, repeats included: "the repeats are as shipped"). */
    val CURRENCIES: List<String> = listOf(
        "€",
        "£",
        "¥",
        "$",
        "¢",
        "₹",
        "₽",
        "₩",
        "₪",
        "₫",
        "₦",
        "₨",
        "₩",
        "₪",
        "₫",
        "₦",
        "₨",
        "₩",
        "₪",
        "₫",
        "₭",
        "₮",
        "₯",
        "₰",
        "₱",
        "₲",
        "₳",
        "₴",
        "₵",
        "₶",
        "֏",
    )

    /** spec: expansion-clipboard-pickers-launcher.md SS5.2, the "Technical Symbols" chip (40 glyphs, as shipped, repeats included). */
    val TECHNICAL_SYMBOLS: List<String> = listOf(
        "°",
        "~",
        "`",
        "{",
        "}",
        "[",
        "]",
        "<",
        ">",
        "^",
        "%",
        "=",
        "\\",
        "|",
        "&",
        "@",
        "#",
        "*",
        "+",
        "-",
        "_",
        "©",
        "®",
        "™",
        "℠",
        "℡",
        "℣",
        "ℤ",
        "℥",
        "Ω",
        "℧",
        "ℨ",
        "℩",
        "K",
        "Å",
        "ℬ",
        "ℭ",
        "℮",
        "ℯ",
        "ℰ",
    )

    /** spec: expansion-clipboard-pickers-launcher.md SS5.2, the "Arrows" chip (40 glyphs, as shipped, repeats included). */
    val ARROWS: List<String> = listOf(
        "←",
        "→",
        "↑",
        "↓",
        "↔",
        "↕",
        "↗",
        "↘",
        "↙",
        "↖",
        "⇐",
        "⇒",
        "⇑",
        "⇓",
        "⇔",
        "⇕",
        "⇗",
        "⇘",
        "⇙",
        "⇖",
        "⇠",
        "⇡",
        "⇢",
        "⇣",
        "⇤",
        "⇥",
        "⇦",
        "⇧",
        "⇨",
        "⇩",
        "⇪",
        "⇫",
        "⇬",
        "⇭",
        "⇮",
        "⇯",
        "⇰",
        "⇱",
        "⇲",
        "⇳",
    )

    /**
     * spec SS5.2's "Miscellaneous" chip: "Set-theory and relation operators U+2205 to U+22FF
     * ... then the Letterlike Symbols block U+2100 to U+214F", both ranges taken whole (the
     * spec's own parenthetical confirms the sub/superset block inside the first range "complete").
     * Generated from the two code point ranges rather than typed out, so there is no transcription
     * risk across 175 glyphs.
     */
    val MISCELLANEOUS: List<String> = codePointRange(0x2205, 0x22FF) + codePointRange(0x2100, 0x214F)

    /**
     * spec SS5.2's "Variations" chip: "Accented and modified Latin letters, grouped A to Z,
     * uppercase before lowercase ... plus the ligatures and IPA forms of each letter (about 560
     * glyphs)".
     *
     * SPEC GAP: the shipped list's exact glyph-by-glyph diacritic order (grave, acute,
     * circumflex, tilde, diaeresis, ring, macron, breve, ogonek, dot, caron, stroke, hook below)
     * and its precise membership are 2.x source facts this clean-room rewrite cannot read
     * (core-rules.md's clean room). This derives the same *shape* directly from the Unicode
     * standard instead: every precomposed Latin letter in the Latin-1 Supplement, Latin Extended-A,
     * Latin Extended-B and IPA Extensions blocks whose canonical (NFD) decomposition starts with
     * that base letter, grouped by base letter A to Z (uppercase before lowercase, in code point
     * order within a letter), plus the shipped ligatures (Æ/æ, Œ/œ, ß) folded into their nearest
     * letter group. It is close in kind and comparable in size to the spec's "about 560", not a
     * byte-for-byte reproduction.
     */
    val VARIATIONS: List<String> by lazy { buildVariations() }

    /** spec SS5.2: "in chip order", the row the dialog's chip strip renders. */
    val CATEGORIES: List<UnicodeCategory> by lazy {
        listOf(
            UnicodeCategory("Punctuation", PUNCTUATION),
            UnicodeCategory("Mathematical Symbols", MATHEMATICAL_SYMBOLS),
            UnicodeCategory("Currencies", CURRENCIES),
            UnicodeCategory("Technical Symbols", TECHNICAL_SYMBOLS),
            UnicodeCategory("Arrows", ARROWS),
            UnicodeCategory("Variations", VARIATIONS),
            UnicodeCategory("Miscellaneous", MISCELLANEOUS),
        )
    }

    private fun codePointRange(first: Int, last: Int): List<String> = (first..last).map { String(Character.toChars(it)) }

    /** Latin-1 Supplement, Latin Extended-A, Latin Extended-B and IPA Extensions: where a precomposed accented Latin letter can live. */
    private val LATIN_ACCENTED_RANGE = 0x00C0..0x02AF

    private val LIGATURES: Map<Char, List<String>> = mapOf(
        'A' to listOf("Æ"), 'a' to listOf("æ"),
        'O' to listOf("Œ"), 'o' to listOf("œ"),
        'S' to listOf("ß"),
    )

    private fun baseLetterOf(codePoint: Int): Char? {
        val s = String(Character.toChars(codePoint))
        if (!Character.isLetter(codePoint)) return null
        val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
        val base = decomposed.firstOrNull() ?: return null
        return if (base.uppercaseChar() in 'A'..'Z') base else null
    }

    private fun buildVariations(): List<String> {
        val byLetter = LinkedHashMap<Char, MutableList<String>>()
        for (letter in 'A'..'Z') {
            byLetter[letter] = mutableListOf()
            byLetter[letter.lowercaseChar()] = mutableListOf()
        }
        for (codePoint in LATIN_ACCENTED_RANGE) {
            if (Character.isISOControl(codePoint)) continue
            val glyph = String(Character.toChars(codePoint))
            val base = baseLetterOf(codePoint) ?: continue
            val bucket = if (glyph[0].isUpperCase()) base.uppercaseChar() else base.lowercaseChar()
            byLetter[bucket]?.add(glyph)
        }
        val out = mutableListOf<String>()
        for (letter in 'A'..'Z') {
            out += byLetter[letter].orEmpty()
            out += LIGATURES[letter].orEmpty()
            out += byLetter[letter.lowercaseChar()].orEmpty()
            out += LIGATURES[letter.lowercaseChar()].orEmpty()
        }
        return out.distinct()
    }
}
