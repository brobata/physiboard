package brobata.physiboard.core.actions.clipboard

/** One entry of the clipboard history. spec: expansion-clipboard-pickers-launcher.md SS1 ("Clip"), SS3.2's row shape. */
data class Clip(val id: Long, val text: String, val timestampMs: Long, val pinned: Boolean = false)

/** The capture rule for one system clip. spec SS3.1, SS3.7. */
object ClipCapture {
    /**
     * Whether a clip's first item is taken: the keyboard may remember things right now
     * ([learningAllowed] is false in private mode and in a field that asks for no learning, SS3.1
     * and app-shell.md SS31), the clip declares a `text/` prefixed type or no type at all, the
     * coerced text is not empty, and (3.0, SS13 "Sensitive-clip exclusion: keep (add it)") the
     * source did not flag it sensitive, which is what a password manager's copy carries.
     */
    fun accepts(mimeTypes: List<String>, coercedText: String?, sensitive: Boolean, learningAllowed: Boolean): Boolean {
        if (!learningAllowed) return false
        if (sensitive) return false
        if (mimeTypes.isNotEmpty() && mimeTypes.none { it.startsWith("text/") }) return false
        return !coercedText.isNullOrEmpty()
    }

    /**
     * spec SS3.7: the text the keyboard keeps or pastes for a clip, with tracking stripped from
     * its links when `clean_links` is on. Used both when a clip is stored and when a card is
     * pasted, so a clip stored with the setting off is still pasted clean once it is on.
     */
    fun text(text: String, cleanLinks: Boolean): String = if (cleanLinks) LinkCleaner.cleanText(text) else text
}

/**
 * The whole history as one immutable value: the working set every read is served from (SS3.2).
 * Every operation returns the next value; the caller mirrors the difference to its database on
 * its own thread. Ordering everywhere is pinned first, then most recent first (SS3.2).
 */
data class ClipboardHistory(
    val entries: List<Clip> = emptyList(),
    val nextId: Long = 1,
    /** When the last cleanup ran, for the 5 s debounce of unforced runs (SS3.3, T23). */
    val lastCleanupMs: Long? = null,
) {
    /** spec SS3.2: "pinned entries first, then most recent first within each group". */
    val ordered: List<Clip> get() = entries.sortedWith(compareByDescending<Clip> { it.pinned }.thenByDescending { it.timestampMs }.thenByDescending { it.id })

    /** spec SS3.4: "The number of entries (pinned and not)". */
    val count: Int get() = entries.size

    /**
     * spec SS3.1: a clip equal to an existing entry's text (exact, case-sensitive) is not added
     * again; the existing entry's timestamp is refreshed so it moves to the top of its group (T18).
     */
    fun capture(text: String, nowMs: Long): ClipboardHistory {
        val existing = entries.firstOrNull { it.text == text }
        return if (existing != null) {
            replace(existing.copy(timestampMs = nowMs))
        } else {
            copy(entries = entries + Clip(nextId, text, nowMs), nextId = nextId + 1)
        }
    }

    /** spec SS3.5: pin or unpin "toggles the flag and refreshes the timestamp, so the entry jumps to the top of its new group". */
    fun togglePinned(id: Long, nowMs: Long): ClipboardHistory {
        val clip = entries.firstOrNull { it.id == id } ?: return this
        return replace(clip.copy(pinned = !clip.pinned, timestampMs = nowMs))
    }

    /** spec SS3.5: Delete "removes the entry even if pinned" (T22). */
    fun delete(id: Long): ClipboardHistory = copy(entries = entries.filterNot { it.id == id })

    /** spec SS3.5: Clear All "removes every non-pinned entry... Pinned entries stay" (T21). Emptying the system clipboard is the caller's. */
    fun clearAll(): ClipboardHistory = copy(entries = entries.filter { it.pinned })

    /**
     * spec SS3.3: non-pinned entries older than [retentionMinutes] are removed; pinned entries never
     * expire; 0 or less means never delete (T19, T20). An unforced run within [CLEANUP_DEBOUNCE_MS]
     * of the last one is skipped (T23); every run the spec lists is forced.
     */
    fun cleanup(nowMs: Long, retentionMinutes: Long, forced: Boolean = true): CleanupResult {
        val last = lastCleanupMs
        if (!forced && last != null && nowMs - last < CLEANUP_DEBOUNCE_MS) return CleanupResult(this, ran = false)
        if (retentionMinutes <= 0) return CleanupResult(copy(lastCleanupMs = nowMs), ran = true)
        val cutoff = nowMs - retentionMinutes * 60_000L
        val kept = entries.filter { it.pinned || it.timestampMs >= cutoff }
        return CleanupResult(copy(entries = kept, lastCleanupMs = nowMs), ran = true)
    }

    /**
     * spec SS3.2: the table is loaded in the background; "a clip copied before the load finished is
     * already in memory, so a stored row with the same text is deleted as superseded rather than
     * loaded twice". [Clip.text] duplicates already sitting among [stored] itself (left over from
     * an older, buggier version that did not enforce SS3.1's one-row-per-text rule) are collapsed
     * the same way: the same-text row with the latest timestamp is kept, the rest are reported as
     * superseded too, so a reload does not keep re-loading rows [capture] would never have created.
     * Returns the merged history and the ids of the stored rows to delete.
     */
    fun mergeLoaded(stored: List<Clip>): Pair<ClipboardHistory, List<Long>> {
        val inMemoryTexts = entries.map { it.text }.toSet()
        val superseded = mutableListOf<Long>()
        val keptByText = LinkedHashMap<String, Clip>()
        for (clip in stored) {
            if (clip.text in inMemoryTexts) {
                superseded += clip.id
                continue
            }
            val existing = keptByText[clip.text]
            when {
                existing == null -> keptByText[clip.text] = clip
                clip.timestampMs > existing.timestampMs -> {
                    superseded += existing.id
                    keptByText[clip.text] = clip
                }
                else -> superseded += clip.id
            }
        }
        val maxId = (entries.map { it.id } + stored.map { it.id }).maxOrNull() ?: 0
        return copy(entries = keptByText.values.toList() + entries, nextId = maxOf(nextId, maxId + 1)) to superseded
    }

    private fun replace(clip: Clip): ClipboardHistory = copy(entries = entries.map { if (it.id == clip.id) clip else it })

    data class CleanupResult(val history: ClipboardHistory, val ran: Boolean)

    companion object {
        /** spec SS3.3: `clipboard_retention_time` default, minutes. */
        const val DEFAULT_RETENTION_MINUTES: Long = 5

        /** spec SS3.3: "every 60 s while an input view is active". */
        const val CLEANUP_INTERVAL_MS: Long = 60_000

        /** spec SS3.3: "Cleanup requests that are not forced are ignored if one ran less than 5 s ago". */
        const val CLEANUP_DEBOUNCE_MS: Long = 5_000
    }
}

/**
 * The clipboard panel's fixed geometry, in dp, so the view has no numbers of its own. spec SS3.5,
 * hardware mode (the software-keyboard height is dropped for 3.0).
 */
object ClipboardPanelGeometry {
    const val HEIGHT_DP: Int = 177
    const val COLUMNS: Int = 3
    const val CARD_HEIGHT_DP: Int = 64
    const val CARD_PADDING_DP: Int = 12
    const val CARD_GAP_DP: Int = 4
    const val GRID_PADDING_DP: Int = 8
    const val GRID_EXTRA_BOTTOM_DP: Int = CARD_HEIGHT_DP * 2
    const val CARD_CORNER_DP: Int = 6
    const val CARD_TEXT_SP: Int = 14
    const val CARD_MAX_LINES: Int = 2
    const val HEADER_SIDE_PADDING_DP: Int = 8
    const val HEADER_TOP_PADDING_DP: Int = 8
    const val HEADER_BOTTOM_PADDING_DP: Int = 4
    const val HEADER_TEXT_SP: Int = 12
    const val CLOSE_WIDTH_DP: Int = 36
    const val CLOSE_HEIGHT_DP: Int = 32
    const val TITLE: String = "Clipboard History"

    /** app-shell.md SS31.4: the header while private mode (or a field's own flag) stops new copies being kept. */
    const val TITLE_NOT_SAVING: String = "Clipboard History · private, new copies not saved"
    const val CLEAR_ALL: String = "Clear All"
    const val EMPTY: String = "No clipboard history"
    const val MENU_PIN: String = "Pin"
    const val MENU_UNPIN: String = "Unpin"
    const val MENU_DELETE: String = "Delete"
}
