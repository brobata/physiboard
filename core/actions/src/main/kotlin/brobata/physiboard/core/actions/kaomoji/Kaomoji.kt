package brobata.physiboard.core.actions.kaomoji

import brobata.physiboard.core.actions.picker.NameSearch

/** One kaomoji: the text it inserts, a name, and the words it is also found by. spec: expansion-clipboard-pickers-launcher.md SS4.9. */
data class Kaomoji(val text: String, val name: String, val tags: List<String>)

/** One mood tab: [tabLabel] is the short kaomoji the tab shows. */
data class KaomojiGroup(val id: String, val label: String, val tabLabel: String, val entries: List<Kaomoji>)

/** A scored search hit. */
data class KaomojiHit(val kaomoji: Kaomoji, val groupId: String, val score: Int)

/**
 * The kaomoji collection, grouped by mood or action, and its search. spec:
 * expansion-clipboard-pickers-launcher.md SS4.9. The list is PhysiBoard's own ([KaomojiData]);
 * it is parsed once, on first use, from text compiled into the app, so nothing is read from disk.
 */
object KaomojiCatalog {
    const val RECENTS_ID: String = "KAOMOJI_RECENTS"
    const val MAX_RESULTS: Int = 200

    /** A name match outranks the same match on a tag. */
    const val NAME_BONUS: Int = 50

    /** The group's own label ("Sad") counts as a tag, but below every real tag. */
    const val GROUP_LABEL_PENALTY: Int = 100

    val groups: List<KaomojiGroup> by lazy { parse(KaomojiData.GROUPS) }

    /** Every kaomoji, in group then list order. */
    val all: List<Kaomoji> get() = groups.flatMap { it.entries }

    /** Parses [KaomojiData]'s format: `kaomoji  ::  name  ::  tag, tag`, one per line; blank lines are skipped. */
    fun parse(source: List<KaomojiData.Source>): List<KaomojiGroup> = source.map { group ->
        val entries = group.body.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val parts = line.split(SEPARATOR)
            require(parts.size == 3) { "kaomoji line needs three fields: '$line'" }
            Kaomoji(
                text = parts[0].trim(),
                name = parts[1].trim(),
                tags = parts[2].split(',').map { it.trim() }.filter { it.isNotEmpty() },
            )
        }.toList()
        KaomojiGroup(group.id, group.label, group.tabLabel, entries)
    }

    /** The search index over [groups], built once on first search. */
    val index: KaomojiIndex by lazy { KaomojiIndex(groups) }

    /** spec SS4.9: [KaomojiIndex.search] over the shipped collection. */
    fun search(rawQuery: String, limit: Int = MAX_RESULTS): List<KaomojiHit> = index.search(rawQuery, limit)

    private const val SEPARATOR: String = "  ::  "
}

/**
 * spec SS4.9: ranks every kaomoji by [NameSearch] against its name (+[KaomojiCatalog.NAME_BONUS]),
 * its tags, and its group's label (-[KaomojiCatalog.GROUP_LABEL_PENALTY]); the best term wins. A
 * query that is exactly a kaomoji's text ranks that kaomoji first. Ties keep group order, then list
 * order. The terms are normalised once, here, not per query.
 */
class KaomojiIndex(groups: List<KaomojiGroup>) {
    private class Indexed(val kaomoji: Kaomoji, val groupId: String, val name: NameSearch.Term, val tags: List<NameSearch.Term>, val group: NameSearch.Term)

    private val entries: List<Indexed> = groups.flatMap { group ->
        val groupTerm = NameSearch.Term.of(group.label)
        group.entries.map { k -> Indexed(k, group.id, NameSearch.Term.of(k.name), k.tags.map(NameSearch.Term::of), groupTerm) }
    }

    fun search(rawQuery: String, limit: Int = KaomojiCatalog.MAX_RESULTS): List<KaomojiHit> {
        val query = NameSearch.Query(rawQuery)
        if (query.raw.isEmpty()) return emptyList()
        val hits = ArrayList<KaomojiHit>()
        for (e in entries) {
            val score = scoreOf(e, query) ?: continue
            hits.add(KaomojiHit(e.kaomoji, e.groupId, score))
        }
        // sortedByDescending is stable, so equal scores keep group then list order.
        return hits.sortedByDescending { it.score }.take(limit)
    }

    private fun scoreOf(e: Indexed, query: NameSearch.Query): Int? {
        if (query.raw == e.kaomoji.text) return EXACT_TEXT
        if (query.isEmpty) return null
        var best: Int? = NameSearch.score(query, e.name)?.plus(KaomojiCatalog.NAME_BONUS)
        for (tag in e.tags) {
            val s = NameSearch.score(query, tag) ?: continue
            if (best == null || s > best) best = s
        }
        val g = NameSearch.score(query, e.group)?.minus(KaomojiCatalog.GROUP_LABEL_PENALTY)
        if (g != null && (best == null || g > best)) best = g
        return best
    }

    private companion object {
        const val EXACT_TEXT: Int = 2000
    }
}
