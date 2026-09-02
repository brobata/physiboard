package brobata.physiboard.inputmethod

import brobata.physiboard.core.suggestions.SuggestionResult

object DebugCaptureStore {

    enum class AutoCorrectionType { COMMIT, ATTEMPT }
    enum class AutoCorrectionTrigger { SPACE, ENTER, SUGGESTION_TAP, OTHER }
    enum class AutoCorrectionOutcome { APPLIED, SKIPPED, NOT_APPLICABLE }

    data class AutoCorrectionEvent(
        val timestampMs: Long,
        val type: String,
        val trigger: String,
        val source: String,
        val outcome: String,
        val before: String,
        val after: String?,
        val reason: String?,
        val distance: Int? = null,
        val kind: String? = null
    )

    data class SuggestionEntry(
        val candidate: String,
        val source: String,
        val kind: String
    )

    data class SuggestionsSnapshot(
        val timestampMs: Long,
        val entries: List<SuggestionEntry>
    )

    data class ImeContextSnapshot(
        val timestampMs: Long,
        val packageName: String?,
        val inputType: Int?,
        // imeOptions decides whether Enter can carry an editor action at all: an editor that
        // sets IME_FLAG_NO_ENTER_ACTION, or declares no action, cannot be sent to that way.
        // Its absence here is why a "Enter does nothing in this app" report could not be
        // answered from a debug export.
        val imeOptions: Int?,
        val resolvedEditorAction: String?,
        val subtypeLocale: String?,
        val resolvedLayout: String?,
        val physicalProfileOverride: String?
    )

    data class RawTrackpadEvent(
        val timestampMs: Long,
        val provider: String,
        val origin: String,
        val phase: String,
        val action: String,
        val outcome: String,
        val startX: Float?,
        val startY: Float?,
        val x: Float?,
        val y: Float?,
        val deltaX: Float?,
        val deltaY: Float?,
        val threshold: Float?,
        val deviceId: Int,
        val source: Int,
        val eventTimeUptimeMs: Long
    )

    private const val MAX_AUTOCORRECTIONS = 100
    private const val MAX_SUGGESTION_SNAPSHOTS = 50
    private const val MAX_RAW_TRACKPAD_EVENTS = 200

    private val autoCorrections = ArrayDeque<AutoCorrectionEvent>()
    private val suggestions = ArrayDeque<SuggestionsSnapshot>()
    private val rawTrackpadEvents = ArrayDeque<RawTrackpadEvent>()
    private var imeContextSnapshot: ImeContextSnapshot? = null

    /**
     * The last context from an app that is NOT PhysiBoard itself.
     *
     * The single snapshot above is overwritten by whatever field has focus, and the Diagnostics
     * screen has a text field of its own — so opening Diagnostics to export a report replaced the
     * context of the app being diagnosed with PhysiBoard's own. The report then answered a
     * question nobody asked. This slot survives that.
     */
    private var externalImeContextSnapshot: ImeContextSnapshot? = null

    @Synchronized
    fun recordAutoCorrectionAttempt(
        before: String,
        trigger: AutoCorrectionTrigger,
        source: String = "UNKNOWN",
        after: String? = null,
        outcome: AutoCorrectionOutcome = AutoCorrectionOutcome.NOT_APPLICABLE,
        reason: String? = null,
        distance: Int? = null,
        kind: String? = null
    ) {
        // Suppress low-signal noise when auto-replace is off and there is no current word context.
        if (
            outcome == AutoCorrectionOutcome.NOT_APPLICABLE &&
            reason == "auto_replace_disabled" &&
            before.isBlank() &&
            after.isNullOrBlank()
        ) {
            return
        }
        autoCorrections.addLast(
            AutoCorrectionEvent(
                timestampMs = System.currentTimeMillis(),
                type = AutoCorrectionType.ATTEMPT.name.lowercase(),
                trigger = trigger.name.lowercase(),
                source = source,
                outcome = outcome.name.lowercase(),
                before = before,
                after = after,
                reason = reason,
                distance = distance,
                kind = kind
            )
        )
        while (autoCorrections.size > MAX_AUTOCORRECTIONS) {
            autoCorrections.removeFirst()
        }
    }

    @Synchronized
    fun recordAutoCorrectionCommit(
        before: String,
        after: String,
        trigger: AutoCorrectionTrigger,
        source: String = "UNKNOWN",
        distance: Int? = null,
        kind: String? = null
    ) {
        autoCorrections.addLast(
            AutoCorrectionEvent(
                timestampMs = System.currentTimeMillis(),
                type = AutoCorrectionType.COMMIT.name.lowercase(),
                trigger = trigger.name.lowercase(),
                source = source,
                outcome = AutoCorrectionOutcome.APPLIED.name.lowercase(),
                before = before,
                after = after,
                reason = null,
                distance = distance,
                kind = kind
            )
        )
        while (autoCorrections.size > MAX_AUTOCORRECTIONS) {
            autoCorrections.removeFirst()
        }
    }

    @Synchronized
    fun recordAutoCorrectionApplied(
        originalWord: String,
        correctedWord: String,
        trigger: AutoCorrectionTrigger = AutoCorrectionTrigger.OTHER,
        source: String = "TEXT_REPLACEMENT"
    ) {
        recordAutoCorrectionCommit(
            before = originalWord,
            after = correctedWord,
            trigger = trigger,
            source = source
        )
    }

    @Synchronized
    fun recordSuggestionsUpdated(suggestionResults: List<SuggestionResult>) {
        val entries = suggestionResults.map { result ->
            SuggestionEntry(
                candidate = result.candidate,
                source = result.source.name,
                kind = result.kind.name
            )
        }
        suggestions.addLast(
            SuggestionsSnapshot(
                timestampMs = System.currentTimeMillis(),
                entries = entries
            )
        )
        while (suggestions.size > MAX_SUGGESTION_SNAPSHOTS) {
            suggestions.removeFirst()
        }
    }

    @Synchronized
    fun updateImeContext(
        packageName: String?,
        inputType: Int?,
        imeOptions: Int?,
        resolvedEditorAction: String?,
        subtypeLocale: String?,
        resolvedLayout: String?,
        physicalProfileOverride: String?,
        isOwnApp: Boolean = false
    ) {
        val snapshot = ImeContextSnapshot(
            timestampMs = System.currentTimeMillis(),
            packageName = packageName,
            inputType = inputType,
            imeOptions = imeOptions,
            resolvedEditorAction = resolvedEditorAction,
            subtypeLocale = subtypeLocale,
            resolvedLayout = resolvedLayout,
            physicalProfileOverride = physicalProfileOverride
        )
        imeContextSnapshot = snapshot
        if (!isOwnApp) externalImeContextSnapshot = snapshot
    }

    @Synchronized
    fun recordRawTrackpadEvent(
        provider: String,
        origin: String,
        phase: String,
        action: String,
        outcome: String,
        startX: Float? = null,
        startY: Float? = null,
        x: Float? = null,
        y: Float? = null,
        deltaX: Float? = null,
        deltaY: Float? = null,
        threshold: Float? = null,
        deviceId: Int = -1,
        source: Int = 0,
        eventTimeUptimeMs: Long = 0L
    ) {
        rawTrackpadEvents.addLast(
            RawTrackpadEvent(
                timestampMs = System.currentTimeMillis(),
                provider = provider,
                origin = origin,
                phase = phase,
                action = action,
                outcome = outcome,
                startX = startX,
                startY = startY,
                x = x,
                y = y,
                deltaX = deltaX,
                deltaY = deltaY,
                threshold = threshold,
                deviceId = deviceId,
                source = source,
                eventTimeUptimeMs = eventTimeUptimeMs
            )
        )
        while (rawTrackpadEvents.size > MAX_RAW_TRACKPAD_EVENTS) {
            rawTrackpadEvents.removeFirst()
        }
    }

    @Synchronized
    fun autoCorrectionsSnapshot(): List<AutoCorrectionEvent> = autoCorrections.toList()

    @Synchronized
    fun suggestionsSnapshot(): List<SuggestionsSnapshot> = suggestions.toList()

    @Synchronized
    fun rawTrackpadEventsSnapshot(): List<RawTrackpadEvent> = rawTrackpadEvents.toList()

    @Synchronized
    fun imeContextSnapshot(): ImeContextSnapshot? = imeContextSnapshot

    /** The last context from an app other than PhysiBoard, for reports collected in-app. */
    @Synchronized
    fun externalImeContextSnapshot(): ImeContextSnapshot? = externalImeContextSnapshot

    @Synchronized
    fun clearAll() {
        autoCorrections.clear()
        suggestions.clear()
        rawTrackpadEvents.clear()
        imeContextSnapshot = null
        externalImeContextSnapshot = null
    }
}
