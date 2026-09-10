package brobata.physiboard.core.suggestions.eval

import brobata.physiboard.core.suggestions.AutoReplaceController
import brobata.physiboard.core.suggestions.DictionaryRepository
import brobata.physiboard.core.suggestions.SuggestionEngine
import brobata.physiboard.core.suggestions.SuggestionResult
import brobata.physiboard.core.suggestions.SuggestionSettings
import java.util.Locale

/**
 * Offline scoring of what autocorrect actually does to a corpus of typed words.
 *
 * ## Why this exists
 *
 * Correction quality is not observable from a diff. Every constant in [SuggestionEngine]'s
 * ranking and every clause in the commit rule was chosen by judgement, and there has been no
 * way to tell whether changing one helps or hurts beyond typing for a while and forming an
 * impression. That is how a keyboard gets quietly worse: each change is locally reasonable and
 * the aggregate is unmeasured.
 *
 * ## What it measures
 *
 * Every case is one typed word with the word the user meant. When the two are equal the row is
 * a CONTROL - a real word that must survive untouched.
 *
 * Four outcomes, and they are not equally bad:
 *
 *  - **FIXED**       the typo was corrected to the intended word. The point of the feature.
 *  - **MISSED**      a typo was left alone. Cheap: the user sees their own mistake and fixes it.
 *  - **WRONG**       a typo was corrected to something else. Expensive.
 *  - **CLOBBERED**   a correctly typed word was replaced. The most expensive outcome there is,
 *                    because the user did nothing wrong and the keyboard overruled them.
 *
 * [Report.falseCorrectionRate] - WRONG plus CLOBBERED over all cases - is the headline number.
 * Recall is the secondary one. A change that lifts recall while lifting the false-correction
 * rate is a regression however good it looks in isolation: users forgive a keyboard that misses
 * and abandon one that overrules them.
 *
 * ## What it does not measure
 *
 * Dictionary coverage and real frequency data are held constant by construction (see
 * [EvalDictionaryRepository]). Context is not modelled because the engine cannot use it yet.
 * Casing is out of scope - the corpus is lower-case apart from the one device row that is not.
 */
object AutocorrectEval {

    data class Case(val typed: String, val intended: String) {
        val isControl: Boolean get() = typed == intended
    }

    enum class Outcome { FIXED, MISSED, WRONG, CLOBBERED, UNTOUCHED }

    data class Row(val case: Case, val committed: String?, val outcome: Outcome, val inDictionary: Boolean)

    data class Report(val rows: List<Row>) {
        val total: Int get() = rows.size
        val controls: Int get() = rows.count { it.case.isControl }
        val typos: Int get() = total - controls

        val fixed: Int get() = rows.count { it.outcome == Outcome.FIXED }
        val missed: Int get() = rows.count { it.outcome == Outcome.MISSED }
        val wrong: Int get() = rows.count { it.outcome == Outcome.WRONG }
        val clobbered: Int get() = rows.count { it.outcome == Outcome.CLOBBERED }

        /**
         * Controls the dictionary has never heard of. These separate two failures that look
         * identical in the outcome column: a bad DECISION (a known word was overruled) and a
         * COVERAGE hole (a real word is missing, so the engine had no way to know it was real).
         * Only the first is fixable by anything in W2-W6; the second is W7.
         */
        val uncoveredControls: Int get() = rows.count { it.case.isControl && !it.inDictionary }

        /** The number that matters: how often the keyboard made things worse. */
        val falseCorrectionRate: Double get() = (wrong + clobbered).toDouble() / total.coerceAtLeast(1)

        /** Of the typos present, how many were repaired. */
        val recall: Double get() = fixed.toDouble() / typos.coerceAtLeast(1)

        /** Of the corrections committed, how many were right. */
        val precision: Double
            get() {
                val committed = fixed + wrong + clobbered
                return if (committed == 0) 1.0 else fixed.toDouble() / committed
            }

        fun format(): String = buildString {
            appendLine("autocorrect eval — $total cases ($typos typos, $controls controls)")
            appendLine("  fixed      %4d".format(fixed))
            appendLine("  missed     %4d".format(missed))
            appendLine("  wrong      %4d   (typo corrected to the wrong word)".format(wrong))
            appendLine("  clobbered  %4d   (correct word overruled)".format(clobbered))
            appendLine("  ----")
            appendLine("  false-correction rate  %.3f".format(falseCorrectionRate))
            appendLine("  recall                 %.3f".format(recall))
            appendLine("  precision              %.3f".format(precision))
            if (uncoveredControls > 0) {
                appendLine("  controls missing from the dictionary  %d  (coverage, not decisions)"
                    .format(uncoveredControls))
            }
            val damage = rows.filter { it.outcome == Outcome.WRONG || it.outcome == Outcome.CLOBBERED }
            if (damage.isNotEmpty()) {
                appendLine("  ----")
                appendLine("  damage:")
                damage.forEach {
                val note = if (it.case.isControl && !it.inDictionary) "  [not in dictionary]" else ""
                appendLine("    ${it.case.typed} -> ${it.committed}  (meant ${it.case.intended})$note")
            }
            }
        }
    }

    /**
     * Replays [cases] through the shipped decision path: real [SymSpell] retrieval, real
     * [SuggestionEngine] ranking, the real shape gate, and
     * [AutoReplaceController.shouldAutoReplace] - the same predicate `handleBoundary` commits on.
     *
     * Nothing here re-implements the rule. A copy would drift from the shipped one inside a
     * release, and a measurement of a rule the keyboard does not use is worse than no
     * measurement at all.
     */
    fun run(
        cases: List<Case>,
        repository: DictionaryRepository,
        settings: SuggestionSettings,
        languageCode: String = "en",
        locale: Locale = Locale.ENGLISH
    ): Report {
        val engine = SuggestionEngine(repository, locale)
        val rows = cases.map { case ->
            val committed = commitFor(case.typed, engine, repository, settings, languageCode)
            Row(case, committed, classify(case, committed), repository.isKnownWord(case.typed))
        }
        return Report(rows)
    }

    private fun classify(case: Case, committed: String?): Outcome = when {
        case.isControl && committed == null -> Outcome.UNTOUCHED
        case.isControl -> Outcome.CLOBBERED
        committed == null -> Outcome.MISSED
        committed.equals(case.intended, ignoreCase = true) -> Outcome.FIXED
        else -> Outcome.WRONG
    }

    /** The word autocorrect would commit for [typed], or null when it leaves the text alone. */
    private fun commitFor(
        typed: String,
        engine: SuggestionEngine,
        repository: DictionaryRepository,
        settings: SuggestionSettings,
        languageCode: String
    ): String? {
        val suggestions = engine.suggest(
            typed,
            limit = 2,
            includeAccentMatching = settings.accentMatching,
            useKeyboardProximity = settings.useKeyboardProximity,
            useEditTypeRanking = settings.useEditTypeRanking
        )
        val top: SuggestionResult = suggestions.firstOrNull() ?: return null
        val confidence = AutoReplaceController.Confidence.of(
            top = top.score,
            runnerUp = suggestions.getOrNull(1)
                ?.takeIf { it.kind == brobata.physiboard.core.suggestions.SuggestionKind.CURRENT_WORD }
                ?.score
        )

        val isOrthographicVariant = AutoReplaceController.isAccentOnlyVariant(typed, top.candidate)
        val isCaseVariant = typed != top.candidate && typed.equals(top.candidate, ignoreCase = true)
        val isExactKnownWord = repository.getExactWordFrequency(typed) > 0

        val facts = AutoReplaceController.ReplaceFacts(
            word = typed,
            lookupWord = typed,
            top = top,
            isKnownWord = repository.isKnownWord(typed),
            isExactKnownWord = isExactKnownWord,
            // Production consults the primary dictionary for an exact-case hit. With an
            // all-lower-case vocabulary that is the same question as isExactKnownWord.
            hasExactPrimaryCase = isExactKnownWord,
            // Rejection memory is per-session and cleared on the next keystroke, so a corpus
            // replay never has any. That it cannot carry over between words is W6.
            isRejected = false,
            isOrthographicVariant = isOrthographicVariant,
            isCaseVariant = isCaseVariant,
            confidence = confidence,
            isSafeCandidate = AutoReplaceController.isSafeAutoReplaceCandidate(
                input = typed,
                lookupWord = typed,
                candidate = top,
                settings = settings,
                isOrthographicVariant = isOrthographicVariant,
                languageCode = languageCode
            )
        )
        if (!AutoReplaceController.shouldAutoReplace(facts, settings)) return null
        return top.candidate.takeIf { !it.equals(typed, ignoreCase = false) }
    }

    // ---- corpus loading ------------------------------------------------------------------

    fun loadCases(resource: String = "/autocorrect/en_cases.tsv"): List<Case> =
        readRows(resource).map { Case(it[0], it[1]) }

    /**
     * The ordinary-English control list. Column two is a note for a human, not data.
     * These become [Case] rows whose intended word is themselves: none may ever be corrected.
     */
    fun loadControls(resource: String = "/autocorrect/en_controls.tsv"): List<Case> =
        readRows(resource).map { Case(it[0], it[0]) }.distinctBy { it.typed }

    fun loadVocabulary(resource: String = "/autocorrect/en_vocab.tsv"): List<Pair<String, Int>> =
        readRows(resource).map { it[0] to it[1].toInt() }

    private fun readRows(resource: String): List<List<String>> {
        val text = AutocorrectEval::class.java.getResourceAsStream(resource)
            ?.bufferedReader()?.readText()
            ?: error("missing evaluation resource $resource")
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split('\t').map(String::trim) }
            .filter { it.size >= 2 }
            .toList()
    }
}
