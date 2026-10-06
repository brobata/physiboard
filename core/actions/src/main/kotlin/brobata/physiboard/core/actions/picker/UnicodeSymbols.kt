package brobata.physiboard.core.actions.picker

/**
 * The Unicode symbol groups the picker's Symbols mode shows, and the name search over every
 * symbol. spec: expansion-clipboard-pickers-launcher.md SS4.8.
 *
 * Names are not shipped: they come from the platform ([Character.getName], which Android answers
 * from ICU and the JVM from its own Unicode tables), passed in as [NameSource] so a test can pin
 * them. A code point is listed only when it has a name, and, on the phone, only when the font
 * draws it (the caller's [GlyphCheck]).
 */
object UnicodeSymbols {

    /** One tab. [ranges] are inclusive code point ranges; [tabLabel] is the glyph the tab shows. */
    data class Group(val id: String, val label: String, val tabLabel: String, val ranges: List<IntRange>, val searchOnly: Boolean = false)

    fun interface NameSource {
        fun nameOf(codePoint: Int): String?
    }

    fun interface GlyphCheck {
        fun draws(symbol: String): Boolean
    }

    /** The platform's names. */
    val PLATFORM_NAMES: NameSource = NameSource { cp -> runCatching { Character.getName(cp) }.getOrNull() }

    private fun r(from: Int, to: Int): IntRange = from..to
    private fun one(cp: Int): IntRange = cp..cp

    /**
     * spec SS4.8's table, in tab order. A code point that falls in two groups belongs to the
     * first in [CLAIM_ORDER] (so ¢ £ ¥ are Currency, not Punctuation, and ♪ is Music, not
     * Dingbats).
     */
    val GROUPS: List<Group> = listOf(
        Group(
            "PUNCTUATION", "Punctuation", "#",
            listOf(r(0x21, 0x2F), r(0x3A, 0x40), r(0x5B, 0x60), r(0x7B, 0x7E), r(0xA1, 0xBF), r(0x2010, 0x205E), r(0x2E00, 0x2E5D)),
        ),
        Group(
            "ARROWS", "Arrows", "→",
            listOf(r(0x2190, 0x21FF), r(0x27F0, 0x27FF), r(0x2900, 0x297F), r(0x2B00, 0x2BFF), r(0x1F800, 0x1F8FF)),
        ),
        Group(
            "MATH", "Maths", "∑",
            listOf(
                one(0xAC), one(0xB1), one(0xD7), one(0xF7), r(0x391, 0x3A9), r(0x3B1, 0x3C9), r(0x2070, 0x209F), r(0x2150, 0x218F),
                r(0x2200, 0x22FF), r(0x27C0, 0x27EF), r(0x2980, 0x29FF), r(0x2A00, 0x2AFF),
            ),
        ),
        Group(
            "CURRENCY", "Currency", "€",
            listOf(
                one(0x24), r(0xA2, 0xA5), one(0x58F), one(0x60B), r(0x9F2, 0x9F3), one(0xAF1), one(0xBF9), one(0xE3F),
                one(0x17DB), r(0x20A0, 0x20C0), one(0xFDFC), one(0x1E2FF),
            ),
        ),
        Group(
            "TECHNICAL", "Signs & technical", "⌘",
            listOf(r(0x2100, 0x214F), r(0x2300, 0x23FF), r(0x2400, 0x2426), r(0x2440, 0x244A), r(0x2460, 0x24FF)),
        ),
        Group("BOX", "Box drawing & blocks", "─", listOf(r(0x2500, 0x259F), r(0x1FB00, 0x1FBFF))),
        Group("SHAPES", "Shapes", "■", listOf(r(0x25A0, 0x25FF), r(0x1F780, 0x1F7FF))),
        Group("DINGBATS", "Dingbats & symbols", "★", listOf(r(0x2600, 0x26FF), r(0x2700, 0x27BF))),
        Group("MUSIC", "Music", "♪", listOf(r(0x2669, 0x266F), r(0x1D100, 0x1D1FF))),
        // Every other symbol or punctuation mark with a name: found by search, never drawn as a
        // tab (it runs to thousands of marks from every script).
        Group("OTHER", "Other", "※", emptyList(), searchOnly = true),
    )

    /** Which group claims a code point that two groups list. */
    private val CLAIM_ORDER: List<String> = listOf("CURRENCY", "MUSIC", "MATH", "ARROWS", "BOX", "SHAPES", "DINGBATS", "TECHNICAL", "PUNCTUATION")

    /**
     * Code point ranges the Other group never takes: emoji pictographs (the emoji picker has
     * them), Sutton SignWriting, the private use areas, and tags and variation selectors.
     */
    private val OTHER_EXCLUDED: List<IntRange> = listOf(r(0x1F000, 0x1FAFF), r(0x1D800, 0x1DAAF), r(0xE000, 0xF8FF), r(0xF0000, 0x10FFFF), r(0xE0000, 0xE0FFF), r(0xFE00, 0xFE0F))

    private const val SCAN_LIMIT: Int = 0x2FFFF

    private val OTHER_TYPES: Set<Int> = setOf(
        Character.MATH_SYMBOL.toInt(), Character.CURRENCY_SYMBOL.toInt(), Character.MODIFIER_SYMBOL.toInt(), Character.OTHER_SYMBOL.toInt(),
        Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(), Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
        Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(), Character.OTHER_PUNCTUATION.toInt(),
    )

    /** One listed symbol: its text, the platform's name for it, and its group. */
    data class Symbol(val codePoint: Int, val text: String, val name: String, val groupId: String)

    /** The listed symbols, grouped, in [GROUPS] order then code point order. */
    class Catalog(val groups: List<Pair<Group, List<Symbol>>>) {
        val all: List<Symbol> get() = groups.flatMap { it.second }
        fun symbolsOf(groupId: String): List<Symbol> = groups.firstOrNull { it.first.id == groupId }?.second.orEmpty()
        val tabs: List<Group> get() = groups.map { it.first }.filter { !it.searchOnly }
        val size: Int get() = groups.sumOf { it.second.size }
    }

    /**
     * Builds the catalogue: every named code point of each group's ranges, claimed by the first
     * group in [CLAIM_ORDER], plus the Other group from a scan of the planes 0 to 2 for symbol and
     * punctuation categories. Costs one name lookup per candidate (some thousands), so the caller
     * runs it once, off the main thread, and keeps the result.
     */
    fun build(names: NameSource = PLATFORM_NAMES, glyphs: GlyphCheck = GlyphCheck { true }): Catalog {
        val owner = HashMap<Int, String>()
        val byId = GROUPS.associateBy { it.id }
        for (id in CLAIM_ORDER) {
            for (range in byId.getValue(id).ranges) for (cp in range) owner.putIfAbsent(cp, id)
        }
        val listed = HashMap<String, MutableList<Symbol>>()
        fun add(cp: Int, groupId: String) {
            if (Character.isISOControl(cp) || Character.isWhitespace(cp)) return
            val name = names.nameOf(cp)?.takeIf { it.isNotBlank() } ?: return
            val text = String(Character.toChars(cp))
            if (!glyphs.draws(text)) return
            listed.getOrPut(groupId) { ArrayList() }.add(Symbol(cp, text, name, groupId))
        }
        for (cp in owner.keys.sorted()) add(cp, owner.getValue(cp))
        for (cp in 0..SCAN_LIMIT) {
            if (cp in owner) continue
            if (Character.getType(cp) !in OTHER_TYPES) continue
            if (OTHER_EXCLUDED.any { cp in it }) continue
            add(cp, "OTHER")
        }
        return Catalog(GROUPS.map { it to listed[it.id].orEmpty() })
    }
}

/** A scored symbol search hit. */
data class SymbolHit(val symbol: UnicodeSymbols.Symbol, val score: Int)

/**
 * The name search over a [UnicodeSymbols.Catalog]. spec SS4.8: the symbol itself typed as the
 * query ranks first (2000); a code point written as `U+2192` or `u2192` ranks next (1900); then
 * [NameSearch]'s table on the Unicode name. Ties go to group order, then code point. Cut to
 * [MAX_RESULTS].
 */
class SymbolSearchIndex(catalog: UnicodeSymbols.Catalog) {
    private class Indexed(val symbol: UnicodeSymbols.Symbol, val groupOrder: Int, val term: NameSearch.Term)

    private val entries: List<Indexed> = catalog.groups.flatMapIndexed { order, (_, symbols) ->
        symbols.map { Indexed(it, order, NameSearch.Term.of(it.name)) }
    }

    val size: Int get() = entries.size

    fun search(rawQuery: String, limit: Int = MAX_RESULTS): List<SymbolHit> {
        val query = NameSearch.Query(rawQuery)
        if (query.raw.isEmpty()) return emptyList()
        val codePoint = CODE_POINT.matchEntire(query.raw)?.groupValues?.get(1)?.toIntOrNull(16)
        val hits = ArrayList<Pair<Indexed, Int>>()
        for (e in entries) {
            val score = when {
                query.raw == e.symbol.text -> SCORE_SYMBOL
                codePoint == e.symbol.codePoint -> SCORE_CODE_POINT
                else -> NameSearch.score(query, e.term)
            } ?: continue
            hits.add(e to score)
        }
        return hits.sortedWith(compareByDescending<Pair<Indexed, Int>> { it.second }.thenBy { it.first.groupOrder }.thenBy { it.first.symbol.codePoint })
            .take(limit)
            .map { SymbolHit(it.first.symbol, it.second) }
    }

    companion object {
        const val MAX_RESULTS: Int = 200
        const val SCORE_SYMBOL: Int = 2000
        const val SCORE_CODE_POINT: Int = 1900
        private val CODE_POINT = Regex("^[uU]\\+?([0-9a-fA-F]{4,6})$")
    }
}
