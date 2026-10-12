package brobata.physiboard.core.actions.emoji

/**
 * The tone the user picked for each emoji, the way a phone's own emoji keyboard keeps it. spec:
 * expansion-clipboard-pickers-launcher.md SS4.7, "Remembered tones".
 *
 * A toned pick in the picker's long-press popup is remembered for that one emoji: the grid, the
 * search results and the recents show and insert that form from then on. Picking the plain
 * (untoned) form forgets it, and the emoji follows `emoji_default_skin_tone` again like every
 * emoji the user never picked a tone for. Nothing is remembered in private mode or a field that
 * asks for no learning (app-shell.md SS31).
 *
 * Keyed by [SkinTones.toneKeyOf], so every form of one emoji shares one entry; the value is the
 * exact form picked, which also keeps a mixed pair (🧑🏻‍🤝‍🧑🏿) the popup offers. Stored beside
 * the recents, in `recent_emojis_prefs` under [PREF_KEY]; like the recents it is learned from
 * use, so it is not part of a backup.
 */
object RememberedSkinTones {
    /** The row in `recent_emojis_prefs`. */
    const val PREF_KEY: String = "emoji_skin_tones"

    /** At most this many emoji are remembered; the oldest pick goes first. */
    const val MAX: Int = 400

    /**
     * The map after [picked] was chosen from the popup. Unchanged when learning is off, for an
     * emoji that takes no tone, or when nothing would change. Returns the same instance when
     * nothing changed, so the caller writes nothing.
     */
    fun afterPick(remembered: Map<String, String>, picked: String, learningAllowed: Boolean): Map<String, String> {
        if (!learningAllowed) return remembered
        val key = SkinTones.toneKeyOf(picked) ?: return remembered
        if (SkinTones.toneOf(picked) == SkinTone.NONE) {
            return if (key in remembered) remembered - key else remembered
        }
        if (remembered[key] == picked && remembered.keys.lastOrNull() == key) return remembered
        // Most recent last, so the oldest is the first to go past [MAX].
        val next = LinkedHashMap<String, String>(remembered.size + 1)
        for ((k, v) in remembered) if (k != key) next[k] = v
        next[key] = picked
        while (next.size > MAX) next.remove(next.keys.first())
        return next
    }

    /**
     * What a cell for [base] (the untoned entry, with its available [variants]) shows and inserts:
     * the remembered form when there is one the device can draw, else [base] in the default tone
     * when the device has that form, else [base].
     */
    fun displayForm(base: String, variants: List<String>, remembered: Map<String, String>, default: SkinTone): String {
        if (remembered.isNotEmpty()) {
            SkinTones.toneKeyOf(base)?.let { key ->
                remembered[key]?.let { form -> if (form == base || form in variants) return form }
            }
        }
        val toned = SkinTones.withDefault(base, default)
        return if (toned == base || toned in variants) toned else base
    }

    /**
     * The recents as the picker draws them: an entry recorded with a tone (before tones were
     * remembered, a popup pick went into the recents as picked) becomes its emoji, so one emoji is
     * listed once and follows its remembered tone. Order is kept; the first wins. [baseByKey] maps
     * a tone key to the entry the emoji assets list for it (an entry's own spelling of the untoned
     * form), so the recent finds its variants.
     */
    fun collapseRecents(recents: List<String>, baseByKey: Map<String, String>): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>(recents.size)
        for (emoji in recents) {
            val key = SkinTones.toneKeyOf(emoji)
            if (seen.add(key ?: emoji)) out.add(if (key == null) emoji else baseByKey[key] ?: key)
        }
        return out
    }

    /** The tone key of every toneable entry in [categories], to the entry's own base. */
    fun baseByKey(categories: List<EmojiCategory>): Map<String, String> {
        val out = HashMap<String, String>()
        for (c in categories) for (e in c.entries) SkinTones.toneKeyOf(e.base)?.let { out.putIfAbsent(it, e.base) }
        return out
    }

    /** Decodes the stored row: `key<TAB>form` lines, most recent last. Anything malformed is skipped. */
    fun decode(stored: String?): Map<String, String> {
        if (stored.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (line in stored.split('\n')) {
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.length - 1) continue
            out[line.substring(0, tab)] = line.substring(tab + 1)
        }
        return out
    }

    fun encode(remembered: Map<String, String>): String = remembered.entries.joinToString("\n") { (k, v) -> "$k\t$v" }
}
