package brobata.physiboard.core.suggestions.eval

import brobata.physiboard.core.suggestions.SuggestionSettings
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * Scores a candidate word list before it is ever built into a `.dict`.
 *
 * Changing which words the keyboard knows is the highest-leverage change available and also
 * the easiest to get wrong in a way nobody notices: adding vocabulary fixes the words that
 * were missing, and simultaneously gives every typo more neighbours to be mistakenly corrected
 * to. Those two effects pull in opposite directions and neither is visible by inspection.
 *
 * So candidates are measured, not argued about. This takes a plain `word<TAB>frequency` file -
 * the `--tsv` output of `scripts/build_en_wordlist.py` - and runs both corpora through it, so a
 * proposed list can be compared against what ships before committing 13 MB of anything:
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*VocabularySweepTest*' \
 *         -Pphysiboard.eval.vocab=/path/to/vocab_shipped.tsv,/path/to/vocab_candidate.tsv
 *
 * Paths are comma-separated and scored in order. No assertions: this is an instrument for
 * choosing a word list, and the ratchet lives in the tests that score the list which was
 * actually chosen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VocabularySweepTest {

    @Test
    fun candidateWordListsAreScored() {
        val paths = System.getProperty("physiboard.eval.vocab").orEmpty()
            .split(',')
            .map(String::trim)
            .filter { it.isNotEmpty() }
        assumeTrue(
            "set -Pphysiboard.eval.vocab=<tsv>[,<tsv>...] to sweep candidate word lists",
            paths.isNotEmpty()
        )

        val cases = AutocorrectEval.loadCases()
        val controls = AutocorrectEval.loadControls()
        val shipped = SuggestionSettings(maxAutoReplaceDistance = 2, useKeyboardProximity = true)

        println("=== vocabulary sweep ===")
        paths.forEach { path ->
            val file = File(path)
            if (!file.isFile) {
                println("!! missing $path")
                return@forEach
            }
            val vocabulary = file.readLines()
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 2) null else parts[0] to (parts[1].trim().toIntOrNull() ?: return@mapNotNull null)
                }
            val repository = EvalDictionaryRepository(vocabulary, Locale.ENGLISH)

            val typos = AutocorrectEval.run(cases, repository, shipped, locale = Locale.ENGLISH)
            val real = AutocorrectEval.run(controls, repository, shipped, locale = Locale.ENGLISH)

            println()
            println("--- ${file.name}  (${vocabulary.size} words) ---")
            println(
                "  typo corpus:     fixed %d  missed %d  wrong %d  recall %.3f  fcr %.3f".format(
                    typos.fixed, typos.missed, typos.wrong, typos.recall, typos.falseCorrectionRate
                )
            )
            println(
                "  real-word sweep: missing %d/%d  overruled %d".format(
                    real.uncoveredControls, real.total, real.clobbered
                )
            )
            real.rows.filter { it.outcome == AutocorrectEval.Outcome.CLOBBERED }
                .forEach { println("      ${it.case.typed} -> ${it.committed}") }
            typos.rows.filter { it.outcome == AutocorrectEval.Outcome.WRONG }
                .forEach { println("      wrong: ${it.case.typed} -> ${it.committed} (meant ${it.case.intended})") }
        }
    }

    /**
     * What a confidence threshold buys, per word list.
     *
     * The dictionary work traded recall away (0.775 -> 0.650) to stop real words being
     * overruled. This is the other half of that bargain: the threshold should recover precision
     * on the cases the word list cannot help with - `definately -> defiantly` survives every
     * vocabulary, because both words are real and the scorer barely separates them.
     *
     * Same invocation as above, with -Pphysiboard.eval.vocab.
     */
    @Test
    fun confidenceThresholdsAreSwept() {
        val paths = System.getProperty("physiboard.eval.vocab").orEmpty()
            .split(',').map(String::trim).filter { it.isNotEmpty() }
        assumeTrue("set -Pphysiboard.eval.vocab=<tsv>[,...]", paths.isNotEmpty())

        val cases = AutocorrectEval.loadCases()
        val controls = AutocorrectEval.loadControls()
        val base = SuggestionSettings(maxAutoReplaceDistance = 2, useKeyboardProximity = true)
        val thresholds = listOf(0.0, 0.02, 0.05, 0.10, 0.20, 0.35, 0.50)

        println("=== confidence sweep ===")
        paths.map(::File).filter { it.isFile }.forEach { file ->
            val vocabulary = file.readLines().mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 2) null else parts[0] to (parts[1].trim().toIntOrNull() ?: return@mapNotNull null)
            }
            val repository = EvalDictionaryRepository(vocabulary, Locale.ENGLISH)
            println()
            println("--- ${file.name} (${vocabulary.size} words) ---")
            println("  threshold   fixed  missed  wrong  overruled   recall  precision    fcr")
            thresholds.forEach { threshold ->
                val settings = base.copy(minAutoReplaceConfidence = threshold)
                val typos = AutocorrectEval.run(cases, repository, settings, locale = Locale.ENGLISH)
                val real = AutocorrectEval.run(controls, repository, settings, locale = Locale.ENGLISH)
                println(
                    "  %9.2f   %5d  %6d  %5d  %9d   %6.3f  %9.3f  %5.3f".format(
                        threshold, typos.fixed, typos.missed, typos.wrong, real.clobbered,
                        typos.recall, typos.precision, typos.falseCorrectionRate
                    )
                )
            }
        }
    }
}
