package brobata.physiboard.core.text.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * spec: autocorrect-suggestions.md SS12: "the only gate on correction quality." Always runs (no
 * opt-in flag, unlike the real-dictionary sweep this milestone does not have): a small,
 * hand-built vocabulary and a corpus seeded from the spec's own device-harvested pairs (SS14 D1)
 * and ordinary English typing errors, replayed through the actual [brobata.physiboard.core.text.BoundaryEngine].
 *
 * Ratchet, spec SS12: "tighten when earned, never loosen." The numbers below are this corpus's
 * measured baseline the day this harness was built (recorded here so a future change that makes
 * corrections worse shows up as a failing test, not a surprise on a device): 40 typos, 34
 * controls, 0 clobbered, 0 wrong, every control present in the vocabulary.
 */
class AutocorrectEvalTest {

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/autocorrect/$name")) { "missing test resource autocorrect/$name" }
            .bufferedReader().use { it.readText() }

    private val cases = AutocorrectEval.parseCases(resource("en_cases.tsv"))
    private val vocab = AutocorrectEval.parseVocab(resource("en_vocab.tsv"))

    @Test
    fun `the corpus is the shape SS12 describes, 40 typos and 34 controls`() {
        assertEquals(74, cases.size)
        assertEquals(40, cases.count { it.typed != it.intended })
        assertEquals(34, cases.count { it.typed == it.intended })
    }

    @Test
    fun `small-vocabulary eval never clobbers a control and never commits a wrong correction`() {
        val summary = AutocorrectEval.run(cases, vocab)
        // spec SS10's invariant, restated for this corpus: a correctly spelled word is never
        // overwritten, and every replacement this small a vocabulary risks is either right or
        // left alone.
        assertEquals(0, summary.clobbered, summary.report("small-vocabulary"))
        assertEquals(0, summary.wrong, summary.report("small-vocabulary"))
        assertEquals(0.0, summary.falseCorrectionRate, summary.report("small-vocabulary"))
    }

    @Test
    fun `small-vocabulary eval fixes at least half the corpus's typos`() {
        val summary = AutocorrectEval.run(cases, vocab)
        // Recorded baseline: this corpus, this vocabulary, the shipped Titan settings. Tighten
        // this number only when a real change earns it (SS12's ratchet rule); never loosen it.
        assertTrue(summary.recall >= 0.5, summary.report("small-vocabulary"))
    }

    @Test
    fun `every control word is covered by the small vocabulary`() {
        val summary = AutocorrectEval.run(cases, vocab)
        assertEquals(0, summary.uncoveredControls, summary.report("small-vocabulary"))
    }

    @Test
    fun `distance 1 and distance 2 must differ in at least one of fixed, wrong or clobbered`() {
        // spec SS12's small-vocabulary eval assertion: the two settings must not be
        // indistinguishable, or the ratchet is not actually exercising `max_auto_replace_distance`.
        val distance1 = AutocorrectEval.run(cases, vocab, settings = brobata.physiboard.core.text.AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1, useKeyboardProximity = true))
        val distance2 = AutocorrectEval.run(cases, vocab)
        val differs = distance1.fixed != distance2.fixed || distance1.wrong != distance2.wrong || distance1.clobbered != distance2.clobbered
        assertTrue(differs, "distance 1 and 2 gave identical results: ${distance1.report("d1")} vs ${distance2.report("d2")}")
    }
}
