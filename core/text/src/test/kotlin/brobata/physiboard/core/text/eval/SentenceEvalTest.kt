package brobata.physiboard.core.text.eval

import brobata.physiboard.core.dict.ContextModel
import brobata.physiboard.core.text.AutocorrectSettings
import brobata.physiboard.core.text.ContextTuning
import brobata.physiboard.core.text.WordMixups
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The sentence-level evaluation harness on the real English dictionary and word-pair table, as
 * the app ships them. spec: autocorrect-suggestions.md §12 (corpora a to d) and §10's invariant.
 * Always runs: the whole set replays in well under a minute on a desktop JVM, so it gates every
 * change to the boundary engine, and its printed tables are what docs/plans/autocorrect-context.md
 * records (`./gradlew :core:text:test --tests '*SentenceEvalTest*' -i`).
 *
 * Corpora, all from the 6,000 Tatoeba sentences held out of the word-pair counts:
 * - a: the sentences as written (every word a control);
 * - b: one synthetic Titan slip per sentence ([SentenceCorpus.withTitanTypos]);
 * - c: `en_cases.tsv` and `en_misspellings.tsv`, each word typed alone into an empty field;
 * - d: sentences using a confusion-set word, flipped to its twin ([SentenceCorpus.mixupFlips]).
 *
 * The ratchets are the numbers measured when the context-aware correction landed: tighten when
 * earned, never loosen.
 */
class SentenceEvalTest {

    private companion object {
        fun resource(name: String): String =
            checkNotNull(SentenceEvalTest::class.java.getResourceAsStream("/autocorrect/$name")) { "missing test resource autocorrect/$name" }
                .bufferedReader().use { it.readText() }

        val sentences by lazy { SentenceCorpus.heldOutSentences(resource("en_heldout_sentences.txt")) }
        val dictionary by lazy { assertNotNull(SentenceEval.shippedDictionary(), "en.pbd is missing or failed to parse") }
        val contextModel: ContextModel by lazy { assertNotNull(SentenceEval.shippedContextModel(), "en.bigrams is missing or failed to parse") }
        val userWords by lazy { SentenceEval.shippedUserWords() }
        val testHalf by lazy { sentences.drop(sentences.size / 2) }

        val shipped: AutocorrectSettings = SentenceEval.SHIPPED
        val withMixups: AutocorrectSettings = SentenceEval.SHIPPED.copy(fixWordMixups = true)

        fun replay(settings: AutocorrectSettings = shipped, tuning: ContextTuning = ContextTuning.DEFAULT, model: ContextModel? = contextModel) =
            SentenceEval(dictionary, model, settings, tuning, userWords)

        fun row(label: String, s: SentenceSummary): String =
            "| $label | ${s.fixed} | ${s.missed} | ${s.wrong} | ${s.clobbered} | ${"%.3f".format(s.recall)} | ${s.wordsPerClobber} |"
    }

    @Test
    fun `a - clean held-out text, correct words changed per N words`() {
        val summary = replay().run(SentenceCorpus.clean(sentences))
        println(summary.report("a clean"))
        println(row("a clean (all 6,000)", summary))
        println(row("a clean (test half)", replay().run(SentenceCorpus.clean(testHalf))))
        println(SentenceEval.describe(summary.words.filter { it.outcome == CaseOutcome.CLOBBERED }, limit = 60))
        assertEquals(0, summary.clobberedOf(WordKind.KNOWN), "§10: a correctly spelled word was overwritten:\n" + SentenceEval.describe(summary.words.filter { it.outcome == CaseOutcome.CLOBBERED && it.typedKnown }))
        assertTrue(summary.controls >= 45_000, "the held-out corpus shrank: ${summary.controls} words")
        // Ratchet: 10 in 45,645 words when this landed (1 per 4,564); three of the ten "correct"
        // words are typos in Tatoeba's own text (presidenf, albumn, and Mery for Mary).
        assertTrue(summary.clobbered <= 10, summary.report("a clean"))

        val mixed = replay(withMixups).run(SentenceCorpus.clean(sentences))
        println(mixed.report("a clean, fix_word_mixups on"))
        println(row("a clean, mix-ups on", mixed))
        println(SentenceEval.describe(mixed.words.filter { it.outcome == CaseOutcome.CLOBBERED && it.typedKnown }, limit = 20))
        // The mix-up fix is the one path allowed to touch a known word; on clean text it must not
        // change a single word the run without it left alone (compared word by word, not as a total).
        fun changed(s: SentenceSummary) = s.words.filter { it.outcome == CaseOutcome.CLOBBERED }.map { it.sentence to it.index }.toSet()
        assertEquals(changed(summary), changed(mixed), "the mix-up fix changed a correct word on clean text")
    }

    @Test
    fun `b - synthetic Titan typos, fixed missed wrong`() {
        val summary = replay().run(SentenceCorpus.withTitanTypos(sentences))
        println(summary.report("b titan typos"))
        println(row("b Titan typos (all)", summary))
        println(row("b Titan typos (test half)", replay().run(SentenceCorpus.withTitanTypos(testHalf))))
        for ((kind, part) in summary.byLabel().toSortedMap()) println("  " + part.report(kind))
        println(SentenceEval.describe(summary.words.filter { it.outcome == CaseOutcome.WRONG || it.outcome == CaseOutcome.CLOBBERED }, limit = 40))
        println("  missed non-word typos by reason: " + summary.words.filter { it.outcome == CaseOutcome.MISSED && !it.typedKnown }.groupingBy { it.reason }.eachCount())
        assertEquals(0, summary.clobberedOf(WordKind.KNOWN), SentenceEval.describe(summary.words.filter { it.outcome == CaseOutcome.CLOBBERED && it.typedKnown }))
        // Ratchets: recall 0.750 (0.858 of the typos that are not themselves real words), 88 wrong, 6 clobbered, when this landed.
        assertTrue(summary.recall >= 0.75, summary.report("b"))
        assertTrue(summary.wrong <= 88, summary.report("b"))
        assertTrue(summary.clobbered <= 6, summary.report("b"))
    }

    @Test
    fun `c - classic misspellings and the device corpus, typed alone`() {
        val cases = SentenceCorpus.standalone(resource("en_cases.tsv"), "en_cases") + SentenceCorpus.standalone(resource("en_misspellings.tsv"), "misspellings")
        val summary = replay().run(cases)
        println(summary.report("c misspellings"))
        println(row("c misspellings", summary))
        for ((label, part) in summary.byLabel().toSortedMap()) println("  " + part.report(label))
        println(SentenceEval.describe(summary.words.filter { it.outcome != CaseOutcome.FIXED && it.outcome != CaseOutcome.UNTOUCHED }, limit = 60))
        assertEquals(0, summary.clobbered, SentenceEval.describe(summary.words.filter { it.outcome == CaseOutcome.CLOBBERED }))
        // Ratchets: 138 of 166 fixed, 3 wrong, when this landed.
        assertTrue(summary.fixed >= 138, summary.report("c"))
        assertTrue(summary.wrong <= 3, summary.report("c"))
    }

    @Test
    fun `d - mix-ups, the flipped word is fixed and the original is left alone`() {
        // Every measured set is switched on here, the dropped ones included, so the decision to drop them stays visible.
        val all = ContextTuning(mixupWords = WordMixups.measured.flatten().toSet())
        val flipped = replay(withMixups, all).run(SentenceCorpus.mixupFlips(sentences), stopAfterError = true)
        val clean = replay(withMixups, all).run(SentenceCorpus.clean(sentences))
        println(flipped.report("d mixups flipped (every measured set)"))
        val shippedWords = WordMixups.sets.flatten().toSet()
        var shippedFixed = 0
        var shippedFlipped = 0
        var shippedClobbered = 0
        for ((pair, part) in flipped.byLabel().toSortedMap()) {
            val members = pair.split('/').toSet()
            val originals = clean.words.filter { SentenceEval.key(it.typed) in members }
            val clobbered = originals.count { it.outcome == CaseOutcome.CLOBBERED }
            val ships = members.any { it in shippedWords }
            println("| $pair | ${if (ships) "kept" else "dropped"} | ${part.typos} | ${part.fixed} | ${part.wrong} | ${originals.size} | $clobbered |")
            if (ships) {
                shippedFixed += part.fixed
                shippedFlipped += part.typos
                shippedClobbered += clobbered
            }
        }
        println(SentenceEval.describe(flipped.words.filter { it.outcome == CaseOutcome.WRONG || it.outcome == CaseOutcome.CLOBBERED }, limit = 40))
        println(SentenceEval.describe(clean.words.filter { it.outcome == CaseOutcome.CLOBBERED && it.typedKnown }, limit = 40))
        println("d shipped sets: flipped=$shippedFlipped fixed=$shippedFixed clean clobbered=$shippedClobbered")
        assertEquals(0, shippedClobbered, "a shipped confusion set changed a correct word on clean text")
        // Ratchet: the shipped sets fixed 1,414 of 1,773 flips when this landed.
        assertTrue(shippedFixed >= 1_414, "shipped sets fixed $shippedFixed of $shippedFlipped")
    }

    @Test
    fun `the cost of a boundary, the keyboard's 12 ms budget divided by the phone's slowdown`() {
        // Warm the JIT first, as a phone that has been typing for a while would be.
        replay().run(SentenceCorpus.clean(sentences.take(500)))
        val clean = replay(withMixups).run(SentenceCorpus.clean(sentences))
        val typos = replay(withMixups).run(SentenceCorpus.withTitanTypos(sentences))
        val legacy = replay(model = null).run(SentenceCorpus.clean(sentences.take(1000)))
        println("timing clean, mix-ups on: boundaries=${clean.boundaries} mean=${"%.0f".format(clean.meanMicros)}us p50=${"%.0f".format(clean.percentileMicros(50))}us p99=${"%.0f".format(clean.percentileMicros(99))}us max=${"%.0f".format(clean.maxMicros)}us")
        println("timing typos, mix-ups on: boundaries=${typos.boundaries} mean=${"%.0f".format(typos.meanMicros)}us p50=${"%.0f".format(typos.percentileMicros(50))}us p99=${"%.0f".format(typos.percentileMicros(99))}us max=${"%.0f".format(typos.maxMicros)}us")
        println("timing clean, no table (the engine before): boundaries=${legacy.boundaries} mean=${"%.0f".format(legacy.meanMicros)}us p50=${"%.0f".format(legacy.percentileMicros(50))}us p99=${"%.0f".format(legacy.percentileMicros(99))}us")
        // A desktop JVM is roughly 5-10x a Titan; a typical boundary must stay far inside the phone's 12 ms log line.
        assertTrue(clean.percentileMicros(50) < 200.0, "median boundary ${clean.percentileMicros(50)} us")
        // The median says little (most words are known and cost microseconds); the slow boundaries
        // are the unknown words, which pay the fuzzy walk. Ratchet on the typo text's 99th
        // percentile: about 0.9 ms when the walk was banded and stopped bisecting the whole list
        // per pruned prefix (1.9 ms before), so a return to the old cost fails here.
        assertTrue(typos.percentileMicros(99) < 1_500.0, "99th percentile boundary on typo text ${typos.percentileMicros(99)} us")
    }

    @Test
    fun `without the context model the engine is the one it was before the table existed`() {
        // The fail-soft contract: a missing or unreadable `.bigrams` leaves the old decision path in
        // place. Its numbers are the BEFORE column, measured on a357dee itself (the plan says how).
        val summary = replay(model = null).run(SentenceCorpus.withTitanTypos(sentences.take(1500)))
        println(summary.report("b titan typos, no table, first 1,500"))
        assertTrue(summary.recall < 0.5, "the no-table path is not the old engine any more: ${summary.report("no table")}")
    }
}
