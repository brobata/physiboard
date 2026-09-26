package brobata.physiboard.core.shell

/** spec: app-shell.md SS11. One autocorrection record: a commit or a live attempt. */
data class AutocorrectionRecord(
    val atMs: Long,
    val type: String,
    val trigger: String,
    val source: String,
    val outcome: String,
    val before: String,
    val after: String,
    val reason: String,
    val distance: Int? = null,
    val kind: String? = null,
)

/** spec: SS11. A suggestion-strip snapshot: the candidate strings shown, oldest first. */
data class SuggestionSnapshot(val atMs: Long, val candidates: List<String>)

/** spec: SS11. One raw trackpad event, kept as an opaque rendered line since only the export ever reads it back. */
data class RawTrackpadRecord(val atMs: Long, val line: String)

/** spec: SS10.7, SS11. The keyboard's last-attached-field snapshot; two slots, both replaced (not appended) on every attach. */
data class ImeContextSnapshot(val atMs: Long, val packageName: String, val fields: Map<String, String>)

/**
 * The in-memory, process-wide capture store (app-shell.md SS11): fixed-capacity ring buffers that
 * fill from the moment the keyboard runs, independent of whether Diagnostics is open, and are
 * cleared only by "Clear" or the process dying. Not thread-safe by itself: the Android host
 * (the keyboard service, on its own looper) is this store's only writer for autocorrections,
 * suggestions and trackpad events, so a plain list is enough; a host that writes from more than
 * one thread must add its own lock.
 */
class DebugCaptureStore {
    private val autocorrections = ArrayDeque<AutocorrectionRecord>()
    private val suggestions = ArrayDeque<SuggestionSnapshot>()
    private val rawTrackpad = ArrayDeque<RawTrackpadRecord>()
    private var lastField: ImeContextSnapshot? = null
    private var lastFieldFromAnotherApp: ImeContextSnapshot? = null
    private var keyboardEventListener: KeyboardEventListener? = null

    /**
     * spec: SS11. Dropped at record time (pure noise, never kept): an attempt with outcome
     * `not_applicable`, reason `auto_replace_disabled`, blank before and blank after. Capacity 100,
     * oldest dropped (T28, T29).
     */
    fun recordAutocorrection(record: AutocorrectionRecord) {
        val isNoise = record.type == "attempt" && record.outcome == "not_applicable" &&
            record.reason == "auto_replace_disabled" && record.before.isBlank() && record.after.isBlank()
        if (isNoise) return
        autocorrections.addLast(record)
        if (autocorrections.size > AUTOCORRECTION_CAPACITY) autocorrections.removeFirst()
    }

    fun autocorrections(): List<AutocorrectionRecord> = autocorrections.toList()

    /** spec: SS11. Capacity 50, oldest dropped; nothing is filtered at record time (empties and duplicates are export-time concerns, T30). */
    fun recordSuggestionSnapshot(snapshot: SuggestionSnapshot) {
        suggestions.addLast(snapshot)
        if (suggestions.size > SUGGESTION_CAPACITY) suggestions.removeFirst()
    }

    fun suggestionSnapshots(): List<SuggestionSnapshot> = suggestions.toList()

    /** spec: SS11. Capacity 200, oldest dropped, nothing filtered. */
    fun recordRawTrackpad(record: RawTrackpadRecord) {
        rawTrackpad.addLast(record)
        if (rawTrackpad.size > RAW_TRACKPAD_CAPACITY) rawTrackpad.removeFirst()
    }

    fun rawTrackpadEvents(): List<RawTrackpadRecord> = rawTrackpad.toList()

    /** spec: SS10.7, SS11. Slot 1 replaces on every attach, including PhysiBoard's own fields. */
    fun recordFieldAttach(snapshot: ImeContextSnapshot, isPhysiBoardOwnPackage: Boolean) {
        lastField = snapshot
        if (!isPhysiBoardOwnPackage) lastFieldFromAnotherApp = snapshot
    }

    fun lastField(): ImeContextSnapshot? = lastField
    fun lastFieldFromAnotherApp(): ImeContextSnapshot? = lastFieldFromAnotherApp

    /**
     * spec: SS10.2, "it registers as the ONE listener for key events reported by the keyboard
     * service." A new registration replaces any previous one; `null` unregisters (leaving the
     * Diagnostics screen stops the keyboard's events from being recorded at all).
     */
    fun setKeyboardEventListener(listener: KeyboardEventListener?) {
        keyboardEventListener = listener
    }

    /** spec: SS10.2. The keyboard calls this for every event; it is a no-op while nothing is registered. */
    fun reportKeyboardEvent(event: KeyboardEventRecord) {
        keyboardEventListener?.onKeyboardEvent(event)
    }

    /** spec: SS10.4 "Clear": wipes every buffer and both context slots for the whole process. */
    fun clear() {
        autocorrections.clear()
        suggestions.clear()
        rawTrackpad.clear()
        lastField = null
        lastFieldFromAnotherApp = null
    }

    companion object {
        const val AUTOCORRECTION_CAPACITY = 100
        const val SUGGESTION_CAPACITY = 50
        const val RAW_TRACKPAD_CAPACITY = 200
    }
}

/**
 * spec: SS10.6 `[suggestions]`: consecutive identical snapshots collapse into one row carrying the
 * last timestamp and a repeat count, and empty snapshots are dropped (T30). Pure so the export and
 * a JVM test agree on the same collapsing rule.
 */
object SuggestionExport {
    data class CollapsedRow(val atMs: Long, val candidates: List<String>, val repeatCount: Int)

    fun collapse(snapshots: List<SuggestionSnapshot>): List<CollapsedRow> {
        val nonEmpty = snapshots.filter { it.candidates.isNotEmpty() }
        val rows = mutableListOf<CollapsedRow>()
        for (snapshot in nonEmpty) {
            val last = rows.lastOrNull()
            if (last != null && last.candidates == snapshot.candidates) {
                rows[rows.lastIndex] = last.copy(atMs = snapshot.atMs, repeatCount = last.repeatCount + 1)
            } else {
                rows += CollapsedRow(snapshot.atMs, snapshot.candidates, 1)
            }
        }
        return rows
    }
}
