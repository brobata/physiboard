package it.palsoftware.pastiera.inputmethod

import it.palsoftware.pastiera.core.suggestions.SuggestionResult

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
        val reason: String?
    )

    data class SuggestionEntry(
        val candidate: String,
        val source: String
    )

    data class SuggestionsSnapshot(
        val timestampMs: Long,
        val entries: List<SuggestionEntry>
    )

    data class ImeContextSnapshot(
        val timestampMs: Long,
        val packageName: String?,
        val inputType: Int?,
        val subtypeLocale: String?,
        val resolvedLayout: String?,
        val physicalProfileOverride: String?
    )

    private const val MAX_AUTOCORRECTIONS = 100
    private const val MAX_SUGGESTION_SNAPSHOTS = 50

    private val autoCorrections = ArrayDeque<AutoCorrectionEvent>()
    private val suggestions = ArrayDeque<SuggestionsSnapshot>()
    private var imeContextSnapshot: ImeContextSnapshot? = null

    @Synchronized
    fun recordAutoCorrectionAttempt(
        before: String,
        trigger: AutoCorrectionTrigger,
        source: String = "UNKNOWN",
        after: String? = null,
        outcome: AutoCorrectionOutcome = AutoCorrectionOutcome.NOT_APPLICABLE,
        reason: String? = null
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
                reason = reason
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
        source: String = "UNKNOWN"
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
                reason = null
            )
        )
        while (autoCorrections.size > MAX_AUTOCORRECTIONS) {
            autoCorrections.removeFirst()
        }
    }

    @Synchronized
    fun recordAutoCorrectionApplied(originalWord: String, correctedWord: String) {
        recordAutoCorrectionCommit(
            before = originalWord,
            after = correctedWord,
            trigger = AutoCorrectionTrigger.OTHER
        )
    }

    @Synchronized
    fun recordSuggestionsUpdated(suggestionResults: List<SuggestionResult>) {
        val entries = suggestionResults.map { result ->
            SuggestionEntry(
                candidate = result.candidate,
                source = result.source.name
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
        subtypeLocale: String?,
        resolvedLayout: String?,
        physicalProfileOverride: String?
    ) {
        imeContextSnapshot = ImeContextSnapshot(
            timestampMs = System.currentTimeMillis(),
            packageName = packageName,
            inputType = inputType,
            subtypeLocale = subtypeLocale,
            resolvedLayout = resolvedLayout,
            physicalProfileOverride = physicalProfileOverride
        )
    }

    @Synchronized
    fun autoCorrectionsSnapshot(): List<AutoCorrectionEvent> = autoCorrections.toList()

    @Synchronized
    fun suggestionsSnapshot(): List<SuggestionsSnapshot> = suggestions.toList()

    @Synchronized
    fun imeContextSnapshot(): ImeContextSnapshot? = imeContextSnapshot

    @Synchronized
    fun clearAll() {
        autoCorrections.clear()
        suggestions.clear()
        imeContextSnapshot = null
    }
}
