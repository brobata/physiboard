package brobata.physiboard.core.actions.launcher

import brobata.physiboard.core.actions.commands.Command
import brobata.physiboard.core.actions.commands.CommandSource
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.ModifierKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** `quick_launcher_behavior`. spec: expansion-clipboard-pickers-launcher.md SS7.1. */
enum class LauncherBehavior { PHYSIBOARD, NIAGARA }

/**
 * The quick launcher's own rows. spec SS7.7 minus the dropped Appearance rows and the animation
 * duration (fixed at [QuickLauncherRules.ANIMATION_MS], SS13).
 */
data class QuickLauncherSettings(
    val behavior: LauncherBehavior = LauncherBehavior.PHYSIBOARD,
    val openUniqueMatch: Boolean = false,
    val limitResults: Boolean = false,
    val respectKeyboardLayout: Boolean = true,
    val typoTolerantRanking: Boolean = true,
    val showAliasFirst: Boolean = true,
    val highlightFavorites: Boolean = true,
    /** `quick_launcher_static_top_highlight`. spec SS7.5: off means the top match uses its own or an icon-derived color instead. */
    val staticTopHighlight: Boolean = false,
    /** `quick_launcher_static_top_highlight_color`. spec SS7.5/SS7.7. */
    val staticTopHighlightColor: Int = QuickLauncherRules.STATIC_TOP_HIGHLIGHT_COLOR,
)

/** The Niagara handoff. spec SS7.1: `android.intent.action.VIEW` of `niagara://search` to `bitpit.launcher`, browsable, new task, clear top, falling back to PhysiBoard's sheet. */
object NiagaraSearch {
    const val ACTION = "android.intent.action.VIEW"
    const val URI = "niagara://search"
    const val PACKAGE = "bitpit.launcher"
    const val CATEGORY = "android.intent.category.BROWSABLE"
}

/** The sheet's fixed numbers. spec SS7.1, SS7.2, SS7.5; the settings SS13 drops become constants here. */
object QuickLauncherRules {
    const val TITLE: String = "PhysiBoard-QuickLauncher"
    const val HINT: String = "Start typing..."
    const val ENTER_HINT: String = "Enter"
    const val LOADING: String = "Loading apps..."
    const val EMPTY_LIMITED: String = "Start typing to search apps"
    const val EMPTY_NO_ENTRIES: String = "No apps found"
    fun emptyNoResults(query: String): String = "No results for \"$query\""

    /** spec SS13: the duration setting is dropped, fixed at the 2.x default; the fade is half. */
    const val ANIMATION_MS: Long = 120
    const val WIDTH_PERCENT: Int = 100
    const val MAX_HEIGHT_PERCENT: Int = 78
    const val LIMITED_RESULTS: Int = 3
    const val GROUP_HEADERS_ABOVE: Int = 4
    const val PADDING_H_DP: Int = 14
    const val PADDING_V_DP: Int = 8
    const val TOP_HIGHLIGHT_ALPHA: Double = 0.58
    const val FAVORITE_BORDER_DP: Int = 2

    /** spec SS7.5: the eight entry colour swatches. */
    val ENTRY_COLORS: List<Int> = listOf(0x7A4285F4, 0x7A34A853, 0x7AFABB05, 0x7AEA4335, 0x7AA142F4, 0x7A00ACC1, 0x7AFF7043, 0x7A888888)
    const val STATIC_TOP_HIGHLIGHT_COLOR: Int = 0x7A4285F4
}

/** One entry's saved customisation. spec SS7.5, `quick_launcher_command_customizations`. */
data class CommandCustomization(
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val alias: String = "",
    val favoriteOrder: Int = UNORDERED,
    val color: Int? = null,
) {
    /** spec SS7.5: "an entry that is back to all defaults is removed from the object" (T51). */
    val isDefault: Boolean get() = !favorite && !hidden && alias.isEmpty() && favoriteOrder == UNORDERED && color == null

    companion object {
        /** spec SS7.5: "`favorite_order` defaults to the largest possible int (unordered)". */
        const val UNORDERED: Int = Int.MAX_VALUE
    }
}

/** The whole customisation document, keyed by command id. spec SS7.5. */
data class CommandCustomizations(val entries: Map<String, CommandCustomization> = emptyMap()) {
    operator fun get(commandId: String): CommandCustomization = entries[commandId] ?: CommandCustomization()

    fun set(commandId: String, value: CommandCustomization): CommandCustomizations =
        CommandCustomizations(if (value.isDefault) entries - commandId else entries + (commandId to value))

    /** spec SS7.5: "a new favourite gets one more than the current highest order". */
    fun setFavorite(commandId: String, favorite: Boolean): CommandCustomizations {
        val current = get(commandId)
        if (favorite == current.favorite) return this
        val order = if (favorite) (highestOrder() ?: -1) + 1 else CommandCustomization.UNORDERED
        return set(commandId, current.copy(favorite = favorite, favoriteOrder = order))
    }

    fun setHidden(commandId: String, hidden: Boolean): CommandCustomizations = set(commandId, get(commandId).copy(hidden = hidden))
    fun setAlias(commandId: String, alias: String): CommandCustomizations = set(commandId, get(commandId).copy(alias = alias.trim()))
    fun setColor(commandId: String, color: Int?): CommandCustomizations = set(commandId, get(commandId).copy(color = color))

    /** spec SS7.5: "Move up" and "Move down" swap saved orders with the neighbour in the favourites' order. */
    fun moveFavorite(commandId: String, up: Boolean): CommandCustomizations {
        val favorites = entries.filterValues { it.favorite }.entries.sortedBy { it.value.favoriteOrder }.map { it.key }
        val index = favorites.indexOf(commandId)
        if (index < 0) return this
        val neighbour = favorites.getOrNull(if (up) index - 1 else index + 1) ?: return this
        val mine = get(commandId)
        val theirs = get(neighbour)
        return set(commandId, mine.copy(favoriteOrder = theirs.favoriteOrder)).set(neighbour, theirs.copy(favoriteOrder = mine.favoriteOrder))
    }

    private fun highestOrder(): Int? = entries.values.filter { it.favorite && it.favoriteOrder != CommandCustomization.UNORDERED }.maxOfOrNull { it.favoriteOrder }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(text: String?): CommandCustomizations {
            if (text.isNullOrBlank()) return CommandCustomizations()
            val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return CommandCustomizations()
            val out = LinkedHashMap<String, CommandCustomization>()
            for ((id, value) in obj) {
                val o = value as? JsonObject ?: continue
                val c = CommandCustomization(
                    favorite = o.bool("favorite") ?: false,
                    hidden = o.bool("hidden") ?: false,
                    alias = (o["custom_search"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    favoriteOrder = o.int("favorite_order") ?: CommandCustomization.UNORDERED,
                    color = o.int("color"),
                )
                if (!c.isDefault) out[id] = c
            }
            return CommandCustomizations(out)
        }

        /** spec settings-catalog.md SS2.12: "only the non-default fields... entries sorted by id". */
        fun encode(customizations: CommandCustomizations): String {
            val obj = JsonObject(
                customizations.entries.entries.sortedBy { it.key }.associate { (id, c) ->
                    id to JsonObject(
                        buildMap {
                            if (c.favorite) put("favorite", JsonPrimitive(true))
                            if (c.hidden) put("hidden", JsonPrimitive(true))
                            if (c.alias.isNotEmpty()) put("custom_search", JsonPrimitive(c.alias))
                            if (c.favoriteOrder != CommandCustomization.UNORDERED) put("favorite_order", JsonPrimitive(c.favoriteOrder))
                            c.color?.let { put("color", JsonPrimitive(it)) }
                        },
                    )
                },
            )
            return json.encodeToString(JsonObject.serializer(), obj)
        }

        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
        private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    }
}

/** One row of the sheet: the command plus its customisation, with the display rules of SS7.5 applied. */
data class LauncherRow(val command: Command, val customization: CommandCustomization) {
    val isFavorite: Boolean get() = customization.favorite
    val alias: String? get() = customization.alias.takeIf { it.isNotEmpty() }

    /** spec SS7.5: "the label (or `alias | label` when `quick_launcher_show_alias_first`... and an alias is set)". */
    fun displayLabel(showAliasFirst: Boolean): String = if (showAliasFirst && alias != null) "$alias | ${command.label}" else command.label

    /** spec SS7.5: "the subtitle (or the source label when there is none)". */
    val displaySubtitle: String get() = command.subtitle ?: command.source.label
}

/** spec SS7.4: the ranking. Lower scores rank first; ties break by label. */
object QuickLauncherRanking {
    const val FAVORITE_BONUS: Int = 25

    /**
     * The best (smallest) score for [row] against [query], or null when nothing matches. When an
     * alias is set and matches, the alias score is used alone; a favourite's normal score is
     * reduced by 25, not below 0 (T46 to T49).
     */
    fun score(row: LauncherRow, rawQuery: String, typoTolerant: Boolean): Int? {
        val query = rawQuery.lowercase().trim()
        if (query.isEmpty()) return null
        val alias = row.alias?.lowercase()?.trim()
        if (alias != null) {
            aliasScore(alias, query)?.let { return it }
        }
        val label = row.command.label.lowercase().trim()
        val subtitle = row.command.subtitle?.lowercase()?.trim().orEmpty()
        val candidates = ArrayList<Int>(8)
        if (label == query) candidates.add(0)
        if (label.startsWith(query)) candidates.add(10 + label.length)
        if (label.split(' ').any { it.startsWith(query) }) candidates.add(40 + label.length)
        val subtitleAt = subtitle.indexOf(query)
        if (subtitle.isNotEmpty() && subtitleAt >= 0) candidates.add(200 + subtitleAt)
        for (token in row.command.searchTokens) {
            val at = token.lowercase().indexOf(query)
            if (at >= 0) candidates.add(220 + at)
        }
        subsequenceGap(label, query)?.let { candidates.add(80 + it + label.length) }
        if (typoTolerant) typoScore(label, query)?.let(candidates::add)
        if (subtitle.isNotEmpty()) subsequenceGap(subtitle, query)?.let { candidates.add(it + 240) }
        val best = candidates.minOrNull() ?: return null
        return if (row.isFavorite) (best - FAVORITE_BONUS).coerceAtLeast(0) else best
    }

    private fun aliasScore(alias: String, query: String): Int? {
        val candidates = ArrayList<Int>(5)
        if (alias == query) candidates.add(0)
        if (alias.startsWith(query)) candidates.add(2 + alias.length)
        if (alias.split(' ').any { it.startsWith(query) }) candidates.add(12 + alias.length)
        val at = alias.indexOf(query)
        if (at >= 0) candidates.add(30 + at)
        subsequenceGap(alias, query)?.let { candidates.add(it + 50) }
        return candidates.minOrNull()
    }

    /** "A subsequence match requires every query character to appear in order"; answers the total gap characters skipped. */
    fun subsequenceGap(text: String, query: String): Int? {
        var qi = 0
        var gaps = 0
        var started = false
        for (c in text) {
            if (qi >= query.length) break
            if (c == query[qi]) {
                qi++
                started = true
            } else if (started) {
                gaps++
            }
        }
        if (qi < query.length) return null
        return gaps
    }

    /**
     * spec SS7.4, the typo-tolerant row: "180 + 25 × edit distance + length difference; only for
     * queries of 3+ characters; allowed distance 1 for 3..5 characters, 2 for 6+; computed against
     * each word of the label, the label without spaces, and every prefix of those within the
     * allowed length window, using edit distance with transpositions" (T47).
     */
    fun typoScore(label: String, query: String): Int? {
        if (query.length < 3) return null
        val allowed = if (query.length <= 5) 1 else 2
        val targets = label.split(' ').filter { it.isNotEmpty() } + label.replace(" ", "")
        var best: Int? = null
        for (target in targets) {
            val prefixes = (maxOf(1, query.length - allowed)..minOf(target.length, query.length + allowed)).map { target.substring(0, it) } + target
            for (candidate in prefixes.distinct()) {
                val distance = damerauLevenshtein(candidate, query)
                // A distance of 0 is a plain prefix or equality, which the rows above already
                // score (T47: the whole-word distance-1 match is the typo score, 206, not the
                // exact 7-character prefix's 180).
                if (distance == 0 || distance > allowed) continue
                val score = 180 + 25 * distance + kotlin.math.abs(candidate.length - query.length)
                if (best == null || score < best) best = score
            }
        }
        return best
    }

    /** Optimal string alignment distance (edit distance with adjacent transpositions). */
    fun damerauLevenshtein(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    /**
     * spec SS7.2 (empty query: favourites in saved order, ties by label, then the rest by label)
     * and SS7.4 (ranked by score, ties by label; limit to the best 3 with `limit_results`; with an
     * empty query and `limit_results`, favourites only). Hidden entries are excluded first (T50).
     */
    fun rank(rows: List<LauncherRow>, query: String, settings: QuickLauncherSettings): List<LauncherRow> {
        val visible = rows.filterNot { it.customization.hidden }
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            val favorites = visible.filter { it.isFavorite }.sortedWith(compareBy<LauncherRow> { it.customization.favoriteOrder }.thenBy { it.command.label.lowercase() })
            if (settings.limitResults) return favorites
            val rest = visible.filterNot { it.isFavorite }.sortedBy { it.command.label.lowercase() }
            return favorites + rest
        }
        val scored = visible.mapNotNull { row -> score(row, trimmed, settings.typoTolerantRanking)?.let { row to it } }
            .sortedWith(compareBy<Pair<LauncherRow, Int>> { it.second }.thenBy { it.first.command.label.lowercase() })
            .map { it.first }
        return if (settings.limitResults) scored.take(QuickLauncherRules.LIMITED_RESULTS) else scored
    }

    /** spec SS7.2: "grouped under source headers when there are more than 4 entries in total" (empty query only). */
    fun showsSourceHeaders(query: String, total: Int): Boolean = query.isBlank() && total > QuickLauncherRules.GROUP_HEADERS_ABOVE

    /** spec SS7.3: with `quick_launcher_auto_start_single`, a non-blank query leaving exactly one entry launches it at once. */
    fun autoLaunches(query: String, results: List<LauncherRow>, settings: QuickLauncherSettings): LauncherRow? =
        if (settings.openUniqueMatch && query.isNotBlank() && results.size == 1) results[0] else null

    /** spec SS7.5: the per-source hue used when an icon yields no colour. */
    fun sourceHue(source: CommandSource): Int = source.hue
}

/**
 * The sheet's key bookkeeping: which downs were consumed so their releases are consumed too, and
 * which downs were decided as a dismissal ([dismissDowns]) so the release acts on that same
 * decision rather than recomputing it from whatever is held at release time. spec SS7.1, SS7.3.
 */
data class SheetKeyState(
    val consumedDowns: Set<KeyId> = emptySet(),
    val enterHandledDown: Boolean = false,
    val dismissDowns: Set<KeyId> = emptySet(),
)

/** What the sheet does with one key. spec SS7.3. */
sealed class SheetKeyEffect {
    data object Dismiss : SheetKeyEffect()
    data object LaunchTop : SheetKeyEffect()
    data class AppendText(val text: String) : SheetKeyEffect()
    data object DeleteLast : SheetKeyEffect()
    data object ConsumedOnly : SheetKeyEffect()
    data object NotConsumed : SheetKeyEffect()
}

/**
 * The quick launcher sheet's hardware-key handling. spec SS7.1 (dismissal at key release, downs
 * consumed and remembered), SS7.3 (typing), T40 to T44. [quickLauncherKey] is the key the sheet is
 * bound to, so Sym held plus that key toggles it closed; [layoutText] is the caller's
 * layout-resolved text for the key (Shift-aware), or null; [eventChar] the event's own character.
 */
object QuickLauncherKeys {
    private val BACK = KeyId.Control(ControlKey.BACK)
    private val ESCAPE = KeyId.Control(ControlKey.ESCAPE)
    private val ENTER = KeyId.Control(ControlKey.ENTER)
    private val BACKSPACE = KeyId.Control(ControlKey.BACKSPACE)

    fun onKeyDown(
        state: SheetKeyState,
        key: KeyId,
        symHeld: Boolean,
        ctrl: Boolean,
        quickLauncherKey: KeyId?,
        layoutText: String?,
        eventChar: Char?,
    ): Pair<SheetKeyState, SheetKeyEffect> {
        if (key is KeyId.Modifier) return state to SheetKeyEffect.NotConsumed
        val isDismissKey = key == BACK || key == ESCAPE || (symHeld && key == quickLauncherKey)
        if (isDismissKey) {
            return state.copy(consumedDowns = state.consumedDowns + key, dismissDowns = state.dismissDowns + key) to SheetKeyEffect.ConsumedOnly
        }
        if (key == ENTER) return state.copy(consumedDowns = state.consumedDowns + key, enterHandledDown = true) to SheetKeyEffect.LaunchTop
        if (key == BACKSPACE) return state.copy(consumedDowns = state.consumedDowns + key) to SheetKeyEffect.DeleteLast
        if (ctrl) return state to SheetKeyEffect.NotConsumed
        val text = layoutText ?: eventChar?.takeUnless { it.isISOControl() || it == '\u0000' }?.toString() ?: return state to SheetKeyEffect.NotConsumed
        return state.copy(consumedDowns = state.consumedDowns + key) to SheetKeyEffect.AppendText(text)
    }

    /**
     * spec SS7.1: dismiss keys act on release; a cancelled release does nothing. The dismiss
     * decision is the one made at key down ([SheetKeyState.dismissDowns]), not recomputed from
     * whatever is held at release: releasing Sym slightly before the bound key must still dismiss.
     * SS7.3: an Enter release without a handled down launches.
     */
    fun onKeyUp(state: SheetKeyState, key: KeyId, cancelled: Boolean): Pair<SheetKeyState, SheetKeyEffect> {
        if (key is KeyId.Modifier) return state to SheetKeyEffect.NotConsumed
        val wasConsumed = key in state.consumedDowns
        val wasDismissDown = key in state.dismissDowns
        val next = state.copy(
            consumedDowns = state.consumedDowns - key,
            dismissDowns = state.dismissDowns - key,
            enterHandledDown = if (key == ENTER) false else state.enterHandledDown,
        )
        if (cancelled) return next to (if (wasConsumed) SheetKeyEffect.ConsumedOnly else SheetKeyEffect.NotConsumed)
        if (wasDismissDown && wasConsumed) return next to SheetKeyEffect.Dismiss
        if (key == ENTER && !state.enterHandledDown) return next to SheetKeyEffect.LaunchTop
        return next to (if (wasConsumed) SheetKeyEffect.ConsumedOnly else SheetKeyEffect.NotConsumed)
    }

    /** spec SS7.3: Alt-modified letters still go through the layout; a caller asks the layout with `uppercase` only from Shift, never from Alt. */
    fun isSymKey(key: KeyId): Boolean = key == KeyId.Modifier(ModifierKey.SYM)
}
