package brobata.physiboard.core.keys

/**
 * The plain data this module needs to resolve a key into a character or action. A caller (a
 * settings/data module, or a test) builds this from whatever storage it likes; this module never
 * loads a file or an asset itself.
 *
 * spec: layers-sym-alt.md SS2 ("the layers in one picture"), SS3 (mapping file shapes, described
 * here as already-parsed data), SS9.1 (layout file shape).
 */
data class LayoutDescription(
    val baseLayout: LayoutMap,
    val deviceLayer: DeviceLayerMap,
    val emojiPage: SymPageMap,
    val symbolsPage: SymPageMap,
    val ctrlMappings: CtrlMappingTable,
    val symPagesConfig: SymPagesConfig = SymPagesConfig(),
    val variations: VariationTable = VariationTable(emptyMap()),
    val longPress: LongPressSettings = LongPressSettings(),
)

/** One key's entry in a base layout file. spec: layers-sym-alt.md SS9.1. */
data class LetterEntry(
    val lowercase: String,
    val uppercase: String,
    val taps: List<Tap> = emptyList(),
) {
    /** spec: layers-sym-alt.md SS9.1 ("multi-tap is honoured only when... at least two taps survive"). */
    val isMultiTap: Boolean get() = taps.size >= 2
}

/** One step of a multi-tap key's cycle. spec: layers-sym-alt.md SS9.1. */
data class Tap(val lowercase: String, val uppercase: String)

/**
 * The base letter/digit/punctuation layout. spec: layers-sym-alt.md SS9.1, SS9.2. When a key has
 * no entry, callers fall back to whatever the platform's own character map says (out of this
 * module's scope, since that is a platform fact, not a layer decision).
 */
data class LayoutMap(val entries: Map<KeyId, LetterEntry> = emptyMap()) {
    operator fun get(key: KeyId): LetterEntry? = entries[key]
}

/**
 * spec: layers-sym-alt.md SS3.2 (the two Titan device-layer files, resolved to a single map by
 * the caller). The `__DPAD_*__` sentinel values of SS3.1 are dropped for 3.0 (SS15 Keep/Drop:
 * "Clicks-only"), so a device-layer entry is always literal text.
 */
data class DeviceLayerMap(val entries: Map<KeyId, String> = emptyMap()) {
    operator fun get(key: KeyId): String? = entries[key]
}

/**
 * One key's entry on a Sym key layer (Emoji or Symbols). spec: layers-sym-alt.md SS3.1: shipped
 * files use the plain-string form (no [uppercase]); a custom map (SS4.4) always clears it.
 */
data class SymPageEntry(val lowercase: String, val uppercase: String? = null)

/** spec: layers-sym-alt.md SS3.3 (the shipped Emoji and Symbols pages), SS4.4 (custom pages). */
data class SymPageMap(val entries: Map<KeyId, SymPageEntry> = emptyMap()) {
    operator fun get(key: KeyId): SymPageEntry? = entries[key]
}

/**
 * spec: layers-sym-alt.md SS1 (the page id/page number contract). The Device page (page 5) is
 * dropped for 3.0 (SS15 Keep/Drop: "duplicates Alt, off by default, marked under construction"),
 * so only the two key layers and two panels remain.
 */
enum class SymPageId(val pageNumber: Int) {
    EMOJI(1), SYMBOLS(2), CLIPBOARD(3), EMOJI_PICKER(4);

    /** The two pages that remap the 26 letter keys, as opposed to the two content panels. */
    val isKeyLayer: Boolean get() = this == EMOJI || this == SYMBOLS

    companion object {
        /** spec: layers-sym-alt.md SS4.1 default `symPageOrder`, minus the dropped Device page. */
        val DEFAULT_ORDER: List<SymPageId> = listOf(EMOJI, SYMBOLS, CLIPBOARD, EMOJI_PICKER)
    }
}

/**
 * Which Sym pages are in the cycle, and in what order. spec: layers-sym-alt.md SS4.1, SS4.2.
 *
 * This type only carries the already-normalised order (deduplicated, every id present); a caller
 * that parses `sym_pages_config` JSON does the string-level tolerance (unknown ids dropped,
 * whitespace trimmed) described in SS4.1 before building one of these, since that is data
 * loading, not a layer decision.
 */
data class SymPagesConfig(
    val emojiEnabled: Boolean = true,
    val symbolsEnabled: Boolean = true,
    val clipboardEnabled: Boolean = false,
    val emojiPickerEnabled: Boolean = false,
    val order: List<SymPageId> = SymPageId.DEFAULT_ORDER,
) {
    /** spec: layers-sym-alt.md SS4.1 ("duplicates collapse to the first occurrence, every known id missing... is appended"). */
    val normalizedOrder: List<SymPageId> by lazy {
        val deduped = order.distinct()
        deduped + SymPageId.DEFAULT_ORDER.filter { it !in deduped }
    }

    private fun isEnabled(id: SymPageId): Boolean = when (id) {
        SymPageId.EMOJI -> emojiEnabled
        SymPageId.SYMBOLS -> symbolsEnabled
        SymPageId.CLIPBOARD -> clipboardEnabled
        SymPageId.EMOJI_PICKER -> emojiPickerEnabled
    }

    /** spec: layers-sym-alt.md SS4.2 ("the ordered list of enabled pages with 'no page' (0) prepended"). */
    val cycle: List<Int> by lazy {
        listOf(0) + normalizedOrder.filter { isEnabled(it) }.map { it.pageNumber }
    }

    /** spec: layers-sym-alt.md SS4.2 ("tapping Sym moves one step forward and wraps"). */
    fun nextPage(currentPageNumber: Int): Int {
        val steps = cycle
        val index = steps.indexOf(currentPageNumber)
        val nextIndex = if (index < 0) 1.coerceAtMost(steps.lastIndex) else (index + 1) % steps.size
        return steps[nextIndex]
    }

    /**
     * spec: layers-sym-alt.md SS4.2 ("if the current page is the Emoji page and it is not in the
     * enabled cycle, it is replaced by the first enabled page, or 'no page'"). Pages 2, 3, 4 and 5
     * are exempt, because they can be opened directly.
     */
    fun consistentPage(currentPageNumber: Int): Int {
        if (currentPageNumber != SymPageId.EMOJI.pageNumber) return currentPageNumber
        if (isEnabled(SymPageId.EMOJI)) return currentPageNumber
        return cycle.getOrElse(1) { 0 }
    }

    /** spec: layers-sym-alt.md SS4.3 (direct-open buttons ignore the enabled switch). */
    fun directOpen(page: SymPageId, currentPageNumber: Int): Int =
        if (currentPageNumber == page.pageNumber) 0 else page.pageNumber
}

/** spec: keys-and-modifiers.md SS12.2. */
sealed class CtrlMapping {
    data class Command(val commandId: String) : CtrlMapping()
    data class NamedAction(val actionId: String) : CtrlMapping()
    object NativeCtrl : CtrlMapping()
    data class Keycode(val key: KeyId) : CtrlMapping()
    object None : CtrlMapping()
}

/** spec: keys-and-modifiers.md SS12 (the Fn Layer / Ctrl mapping file, already parsed). */
data class CtrlMappingTable(val entries: Map<KeyId, CtrlMapping> = emptyMap()) {
    fun mappingFor(key: KeyId): CtrlMapping = entries[key] ?: CtrlMapping.None
}

/** spec: layers-sym-alt.md SS8 (already resolved for the active layout; the merge across layout overrides is a loader concern, not a layer decision). */
data class VariationTable(val variations: Map<Char, List<String>>) {
    fun listFor(character: Char): List<String> = variations[character] ?: emptyList()
}

/** spec: keys-and-modifiers.md SS8.2. */
enum class LongPressMode { ALT, SHIFT, VARIATIONS, SYM, SYM_SYMBOLS, SYM_EMOJI }

/** spec: keys-and-modifiers.md SS8.3, SS18 (`long_press_threshold` clamped 50 to 1000). */
data class LongPressSettings(
    val mode: LongPressMode = LongPressMode.ALT,
    val thresholdMs: Long = 500,
) {
    val clampedThresholdMs: Long get() = thresholdMs.coerceIn(50, 1000)
}
