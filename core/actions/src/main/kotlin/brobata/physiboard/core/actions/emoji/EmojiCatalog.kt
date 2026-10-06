package brobata.physiboard.core.actions.emoji

/** One emoji as the picker shows it: the base and its variants (the skin-tone forms). spec: expansion-clipboard-pickers-launcher.md SS4.1. */
data class EmojiEntry(val base: String, val variants: List<String> = emptyList()) {
    val hasVariants: Boolean get() = variants.isNotEmpty()
}

/** One category tab. spec SS4.1's table; [icon] is the tab's Material icon named for what it shows. */
data class EmojiCategory(val id: String, val label: String, val icon: EmojiTabIcon, val entries: List<EmojiEntry>)

enum class EmojiTabIcon { SATISFIED_FACE, PERSON, PAW, FORK_AND_KNIFE, AIRPLANE, FOOTBALL, LIGHT_BULB, SYMBOLS, FLAG, CLOCK, FILE }

/** The nine shipped category files in display order, plus the Recents tab. spec SS4.1. */
object EmojiCategories {
    data class Shipped(val id: String, val fileName: String, val label: String, val icon: EmojiTabIcon)

    val SHIPPED: List<Shipped> = listOf(
        Shipped("SMILEYS_AND_EMOTION", "SMILEYS_AND_EMOTION.txt", "Smileys & Emotion", EmojiTabIcon.SATISFIED_FACE),
        Shipped("PEOPLE_AND_BODY", "PEOPLE_AND_BODY.txt", "People & Body", EmojiTabIcon.PERSON),
        Shipped("ANIMALS_AND_NATURE", "ANIMALS_AND_NATURE.txt", "Animals & Nature", EmojiTabIcon.PAW),
        Shipped("FOOD_AND_DRINK", "FOOD_AND_DRINK.txt", "Food & Drink", EmojiTabIcon.FORK_AND_KNIFE),
        Shipped("TRAVEL_AND_PLACES", "TRAVEL_AND_PLACES.txt", "Travel & Places", EmojiTabIcon.AIRPLANE),
        Shipped("ACTIVITIES", "ACTIVITIES.txt", "Activities", EmojiTabIcon.FOOTBALL),
        Shipped("OBJECTS", "OBJECTS.txt", "Objects", EmojiTabIcon.LIGHT_BULB),
        Shipped("SYMBOLS", "SYMBOLS.txt", "Symbols", EmojiTabIcon.SYMBOLS),
        Shipped("FLAGS", "FLAGS.txt", "Flags", EmojiTabIcon.FLAG),
    )

    const val RECENTS_ID: String = "RECENTS"
    const val RECENTS_LABEL: String = "Recents"
    const val MIN_API_FILE: String = "minApi.txt"

    /** spec SS4.1: "one entry per line; tokens separated by single spaces; the first token is the base emoji, the remaining tokens are its variants". */
    fun parseCategoryFile(text: String): List<EmojiEntry> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val tokens = line.split(' ').filter { it.isNotEmpty() }
            EmojiEntry(tokens.first(), tokens.drop(1))
        }
        .toList()

    /** spec SS4.1: `minApi.txt` has one line per API level, "the level followed by the emoji that the platform font first shipped at that level". Answers emoji to level. */
    fun parseMinApi(text: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (line in text.lineSequence()) {
            val tokens = line.trim().split(' ').filter { it.isNotEmpty() }
            val level = tokens.firstOrNull()?.toIntOrNull() ?: continue
            for (emoji in tokens.drop(1)) out[emoji] = level
        }
        return out
    }

    /**
     * Any other `.txt` in the directory is appended after Flags with its file name as label
     * (SS4.1); `minApi.txt` is data, not a category.
     */
    fun extraCategoryLabel(fileName: String): String? {
        if (!fileName.endsWith(".txt") || fileName == MIN_API_FILE) return null
        if (SHIPPED.any { it.fileName == fileName }) return null
        return fileName.removeSuffix(".txt")
    }
}

/** spec SS4.1: whether an emoji can be shown on this device. */
object EmojiAvailability {
    /**
     * "available when either its listed minimum API is known, positive and at or below the running
     * API level, or the system font reports a glyph for it" (T24, T25). [fontHasGlyph] is only
     * consulted when the API map does not answer, so a listed emoji never pays a glyph probe.
     */
    fun isAvailable(emoji: String, minApiByEmoji: Map<String, Int>, runningApi: Int, fontHasGlyph: (String) -> Boolean): Boolean {
        val listed = minApiByEmoji[emoji]
        if (listed != null && listed > 0 && listed <= runningApi) return true
        return fontHasGlyph(emoji)
    }

    /**
     * spec SS4.1: "Bases and variants are filtered independently; an unavailable base drops its
     * whole line; a category that ends up empty is dropped."
     */
    fun filterCategories(categories: List<EmojiCategory>, minApiByEmoji: Map<String, Int>, runningApi: Int, fontHasGlyph: (String) -> Boolean): List<EmojiCategory> =
        categories.mapNotNull { category ->
            val kept = category.entries.mapNotNull { entry ->
                if (!isAvailable(entry.base, minApiByEmoji, runningApi, fontHasGlyph)) return@mapNotNull null
                entry.copy(variants = entry.variants.filter { isAvailable(it, minApiByEmoji, runningApi, fontHasGlyph) })
            }
            if (kept.isEmpty()) null else category.copy(entries = kept)
        }
}

/** spec SS4.4: the recents list, most recent first, at most 40. */
object RecentEmojis {
    const val MAX: Int = 40

    /**
     * "choosing one that is already first changes nothing; choosing one further down moves it to
     * the front and drops nothing; a new one pushes the 41st off the end" (T30).
     */
    fun add(recents: List<String>, emoji: String): List<String> {
        if (recents.firstOrNull() == emoji) return recents
        return (listOf(emoji) + recents.filterNot { it == emoji }).take(MAX)
    }

    /** spec SS4.4: "The Recents section is rebuilt from that list with each entry's variants looked up from the categories." */
    fun category(recents: List<String>, categories: List<EmojiCategory>): EmojiCategory? {
        if (recents.isEmpty()) return null
        val byEmoji = HashMap<String, EmojiEntry>()
        for (c in categories) for (e in c.entries) {
            byEmoji.putIfAbsent(e.base, e)
            for (v in e.variants) byEmoji.putIfAbsent(v, e)
        }
        val entries = recents.map { emoji -> byEmoji[emoji]?.let { EmojiEntry(emoji, (listOf(it.base) + it.variants).filter { v -> v != emoji }) } ?: EmojiEntry(emoji) }
        return EmojiCategory(EmojiCategories.RECENTS_ID, EmojiCategories.RECENTS_LABEL, EmojiTabIcon.CLOCK, entries)
    }
}

/** The picker's geometry in dp. spec SS4.3 (hardware mode only; the software-keyboard height is dropped, SS13). */
object EmojiPickerGeometry {
    const val COMPACT_HEIGHT_DP: Int = 177
    const val EXPANDED_FACTOR: Double = 1.5
    const val CELL_DP: Int = 48
    const val CELL_GAP_DP: Int = 4
    const val GRID_PADDING_DP: Int = 8
    const val GRID_EXTRA_BOTTOM_DP: Int = 44
    const val GLYPH_SP: Double = 28.8
    const val TAB_ROW_HEIGHT_DP: Int = 32
    const val TAB_PADDING_DP: Int = 4
    const val SEARCH_TOGGLE_DP: Int = 32
    const val VARIANT_POPUP_GLYPH_SP: Int = 24
    const val MIN_COLUMNS: Int = 4
    const val MAX_COLUMNS: Int = 10
    const val SEARCH_HINT: String = "Search emoji..."
    const val SEARCH_DEBOUNCE_MS: Long = 120
    const val NO_RESULTS: String = "No emoji found"
    const val LOAD_FAILED: String = "Unable to load emoji"

    /** spec SS4.3: 177 dp, or 1.5 times that (265 dp) when `emoji_picker_expanded_height` is on. */
    fun heightDp(expanded: Boolean): Int = if (expanded) (COMPACT_HEIGHT_DP * EXPANDED_FACTOR).toInt() else COMPACT_HEIGHT_DP

    /**
     * spec SS4.3: "the largest number of 48 dp cells with 4 dp gaps that fit in the screen width
     * minus 16 dp, clamped to 4..10"; on the Titan's 1080 px at 1.875 px/dp that is 10 (D1).
     */
    fun columns(screenWidthPx: Int, pxPerDp: Double): Int {
        val available = screenWidthPx / pxPerDp - 16.0
        val fit = ((available + CELL_GAP_DP) / (CELL_DP + CELL_GAP_DP)).toInt()
        return fit.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }
}

/**
 * What the picker page (Sym page 4) is showing. spec SS4.3: the mode button in the tab row cycles
 * Emoji, Kaomoji, Symbols and back; the search field searches the mode on screen.
 */
enum class PickerMode(val buttonLabel: String, val searchHint: String, val noResults: String, val recentsKey: String) {
    EMOJI("Emoji", "Search emoji...", "No emoji found", "recent_emojis"),
    KAOMOJI("Kaomoji", "Search kaomoji...", "No kaomoji found", "recent_kaomoji"),
    SYMBOLS("Symbols", "Search symbols...", "No symbols found", "recent_symbols"),
    ;

    fun next(): PickerMode = entries[(ordinal + 1) % entries.size]
}

/** The kaomoji and symbol grids' geometry. spec SS4.3. */
object PickerModeGeometry {
    const val KAOMOJI_COLUMNS: Int = 3
    const val KAOMOJI_CELL_DP: Int = 40
    const val KAOMOJI_MAX_SP: Int = 16
    const val KAOMOJI_MIN_SP: Int = 9
    const val SYMBOL_GLYPH_SP: Int = 24
    const val MODE_BUTTON_DP: Int = 56
    const val LOADING_SYMBOLS: String = "Loading symbols..."
    const val SYMBOLS_FAILED: String = "Unable to load symbols"
}
