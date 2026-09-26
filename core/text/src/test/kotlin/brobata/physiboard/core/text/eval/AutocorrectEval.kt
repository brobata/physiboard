package brobata.physiboard.core.text.eval

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.text.AutocorrectMemory
import brobata.physiboard.core.text.AutocorrectSettings
import brobata.physiboard.core.text.BoundaryEngine
import brobata.physiboard.core.text.BoundaryOutcome
import brobata.physiboard.core.text.RankingOptions

/**
 * One corpus row's outcome. spec: autocorrect-suggestions.md SS12: "FIXED (typo corrected to the
 * intended word), MISSED (typo left alone), WRONG (typo corrected to something else), CLOBBERED
 * (a control was replaced), UNTOUCHED (a control survived)."
 */
enum class CaseOutcome { FIXED, MISSED, WRONG, CLOBBERED, UNTOUCHED }

/** One replayed row, with enough kept to explain a regression, not just count it. */
data class CaseResult(
    val typed: String,
    val intended: String,
    val committed: String?,
    val outcome: CaseOutcome,
    /** spec: SS12, "whether the typed word is in the dictionary, separating coverage holes from decision failures." */
    val typedWordKnown: Boolean,
)

/** spec: SS12's metrics, computed from a completed replay. */
data class EvalSummary(val results: List<CaseResult>) {
    val total: Int get() = results.size
    val typoCount: Int get() = results.count { it.typed != it.intended }
    val controlCount: Int get() = results.count { it.typed == it.intended }
    val fixed: Int get() = count(CaseOutcome.FIXED)
    val missed: Int get() = count(CaseOutcome.MISSED)
    val wrong: Int get() = count(CaseOutcome.WRONG)
    val clobbered: Int get() = count(CaseOutcome.CLOBBERED)
    val untouched: Int get() = count(CaseOutcome.UNTOUCHED)

    /** spec: SS12, "the headline number". */
    val falseCorrectionRate: Double get() = if (total == 0) 0.0 else (wrong + clobbered).toDouble() / total
    val recall: Double get() = if (typoCount == 0) 0.0 else fixed.toDouble() / typoCount

    /** spec: SS12, "1.0 when nothing was committed [wrongly]". */
    val precision: Double get() = if (fixed + wrong + clobbered == 0) 1.0 else fixed.toDouble() / (fixed + wrong + clobbered)

    /** spec: SS12, "controls missing from the dictionary". */
    val uncoveredControls: Int get() = results.count { it.typed == it.intended && !it.typedWordKnown }

    private fun count(outcome: CaseOutcome): Int = results.count { it.outcome == outcome }

    /** A one-line summary for a test failure message or a sweep's stdout line. spec: SS12's "vocabulary sweep ... prints fixed/missed/wrong/recall/fcr". */
    fun report(label: String): String =
        "$label: fixed=$fixed missed=$missed wrong=$wrong clobbered=$clobbered untouched=$untouched " +
            "fcr=${"%.3f".format(falseCorrectionRate)} recall=${"%.3f".format(recall)} precision=${"%.3f".format(precision)} " +
            "uncoveredControls=$uncoveredControls"
}

/**
 * Replays a typed/intended corpus through the exact engine the keyboard runs at a boundary
 * ([BoundaryEngine.evaluate]), so the measurement cannot drift from the rule the keyboard uses.
 * spec: autocorrect-suggestions.md SS12: "The replay uses the same two-candidate confidence
 * computation, the same shape gate, and the same commit predicate as the keyboard, with rejection
 * memory empty (it cannot carry over between words)."
 */
object AutocorrectEval {
    data class Case(val typed: String, val intended: String)

    /** Parses `typed<TAB>intended` rows, skipping the header line and blank lines. spec: SS12's `en_cases.tsv`. */
    fun parseCases(text: String): List<Case> = text.lineSequence()
        .drop(1)
        .filter { it.isNotBlank() }
        .map { line ->
            val parts = line.split('\t')
            Case(parts[0].trim(), parts[1].trim())
        }
        .toList()

    /** Parses `word<TAB>rawFrequency` rows. spec: SS12's `en_vocab.tsv`. */
    fun parseVocab(text: String): List<WordFrequency> = text.lineSequence()
        .filter { it.isNotBlank() }
        .map { line ->
            val parts = line.split('\t')
            WordFrequency(parts[0].trim(), parts[1].trim().toInt())
        }
        .toList()

    /**
     * Runs [cases] against a dictionary built from [vocab]. Defaults are the shipped Titan
     * baseline (spec SS13: distance 2, proximity on; accent matching's own code default is
     * already on). [suggestionLimit] mirrors SS9's "the engine is asked for 2 suggestions".
     */
    fun run(
        cases: List<Case>,
        vocab: List<WordFrequency>,
        settings: AutocorrectSettings = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 2, useKeyboardProximity = true),
        rankingOptions: RankingOptions = RankingOptions(useKeyboardProximity = settings.useKeyboardProximity, accentMatchingEnabled = settings.accentMatchingEnabled),
    ): EvalSummary {
        val dictionary = DictionaryIndex.build(LanguageCode.of("en")!!, vocab)
        val userWords = UserWordStore.empty()
        val results = cases.map { case ->
            val evaluation = BoundaryEngine.evaluate(
                trackedWord = case.typed,
                textBeforeCursor32 = case.typed,
                boundaryChar = ' ',
                ruleSets = emptyList(),
                dictionaries = listOf(dictionary),
                userWords = userWords,
                settings = settings,
                rankingOptions = rankingOptions,
                lengthChangeAllowance = 2,
                memory = AutocorrectMemory(),
            )
            val committed = (evaluation.outcome as? BoundaryOutcome.Replaced)?.replacement
            val isControl = case.typed == case.intended
            val outcome = when {
                isControl && committed == null -> CaseOutcome.UNTOUCHED
                isControl -> CaseOutcome.CLOBBERED
                committed == null -> CaseOutcome.MISSED
                committed == case.intended -> CaseOutcome.FIXED
                else -> CaseOutcome.WRONG
            }
            CaseResult(case.typed, case.intended, committed, outcome, typedWordKnown = dictionary.contains(case.typed))
        }
        return EvalSummary(results)
    }
}
