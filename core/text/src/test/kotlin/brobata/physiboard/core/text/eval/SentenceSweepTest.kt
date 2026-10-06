package brobata.physiboard.core.text.eval

import brobata.physiboard.core.text.ContextCorrection
import brobata.physiboard.core.text.ContextTuning
import brobata.physiboard.core.text.WordMixups
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The tuning sweep behind [ContextCorrection.Tuning]'s numbers (docs/plans/autocorrect-context.md).
 * Opt-in: `./gradlew :core:text:test --tests '*SentenceSweepTest*' -Pphysiboard.eval.sweep=true`.
 * It tunes on the first half of the held-out sentences only (the "dev" half); the ratchets in
 * [SentenceEvalTest] and the plan's tables report the whole set and the untouched second half.
 */
class SentenceSweepTest {

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/autocorrect/$name")) { "missing test resource autocorrect/$name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `sweep the context correction constants on the dev half`() {
        if (System.getProperty("physiboard.eval.sweep") != "true") return
        val sentences = SentenceCorpus.heldOutSentences(resource("en_heldout_sentences.txt"))
        // -Pphysiboard.eval.split=test|all re-checks chosen settings on text the sweep did not tune on.
        val dev = when (System.getProperty("physiboard.eval.split")) {
            "test" -> sentences.drop(sentences.size / 2)
            "all" -> sentences
            else -> sentences.take(sentences.size / 2)
        }
        val dictionary = assertNotNull(SentenceEval.shippedDictionary())
        val model = assertNotNull(SentenceEval.shippedContextModel())
        val userWords = SentenceEval.shippedUserWords()
        val clean = SentenceCorpus.clean(dev)
        val typos = SentenceCorpus.withTitanTypos(dev)
        val classic = SentenceCorpus.standalone(resource("en_cases.tsv"), "en_cases") + SentenceCorpus.standalone(resource("en_misspellings.tsv"), "misspellings")

        // -Pphysiboard.eval.grid="scale=4,5;keep=-10,-11;perLetter=1,1.5;share=0.6;start=1;mid=5"
        val grid = System.getProperty("physiboard.eval.grid") ?: "scale=3,4,5,6;keep=-6,-7,-8,-9,-10;perLetter=0.5,1,1.5;share=0.6,0.75,0.9;start=1;mid=5"
        val axes = grid.split(';').associate { part -> part.substringBefore('=') to part.substringAfter('=').split(',').map(String::toDouble) }
        fun axis(name: String, default: Double) = axes[name] ?: listOf(default)
        val configs = ArrayList<ContextCorrection.Tuning>()
        val base = ContextCorrection.DEFAULT_TUNING
        for (scale in axis("scale", base.channelScale))
            for (keep in axis("keep", base.keepLowercase))
                for (perLetter in axis("perLetter", base.keepPerLetter))
                    for (share in axis("share", base.commitShare))
                        for (start in axis("start", base.keepCapitalisedAtStart - base.keepLowercase))
                            for (mid in axis("mid", base.keepCapitalisedMidSentence - base.keepLowercase))
                                for (mix in axis("mix", base.unigramMix))
                                    for (vowel in axis("vowel", base.weights.vowelSubstitution))
                                      for (inflect in axis("inflect", 1.0))
                                        configs.add(
                                            base.copy(
                                                channelScale = scale, keepLowercase = keep, keepCapitalisedAtStart = keep + start, keepCapitalisedMidSentence = keep + mid,
                                                keepPerLetter = perLetter, commitShare = share, unigramMix = mix, weights = base.weights.copy(vowelSubstitution = vowel),
                                                keepInflections = inflect > 0.5,
                                            ),
                                        )
        println("SWEEP grid=$grid configs=${configs.size} dev sentences=${dev.size}")
        val pool = Executors.newFixedThreadPool(24)
        val futures = configs.map { tuning ->
            pool.submit<String> {
                val eval = SentenceEval(dictionary, model, tuning = ContextTuning(correction = tuning), userWords = userWords)
                val a = eval.run(clean)
                val b = eval.run(typos)
                val c = eval.run(classic)
                "SWEEP scale=${tuning.channelScale} keep=${tuning.keepLowercase} start=${tuning.keepCapitalisedAtStart - tuning.keepLowercase} mid=${tuning.keepCapitalisedMidSentence - tuning.keepLowercase} perLetter=${tuning.keepPerLetter} share=${tuning.commitShare} mix=${tuning.unigramMix} vowel=${tuning.weights.vowelSubstitution} inflect=${tuning.keepInflections} | " +
                    "clean clobbered=${a.clobbered} (lower=${a.clobberedOf(WordKind.UNKNOWN_LOWER)} capStart=${a.clobberedOf(WordKind.UNKNOWN_CAPITAL_START)} capMid=${a.clobberedOf(WordKind.UNKNOWN_CAPITAL_MID)}) | " +
                    "typos fixed=${b.fixed} wrong=${b.wrong} clob=${b.clobbered} recall=${"%.3f".format(b.recall)} | classic fixed=${c.fixed} wrong=${c.wrong} recall=${"%.3f".format(c.recall)}"
            }
        }
        for (f in futures) println(f.get())
        pool.shutdown()
    }

    @Test
    fun `sweep the mix-up thresholds per confusion set on the dev half`() {
        if (System.getProperty("physiboard.eval.sweep") != "mixups") return
        val sentences = SentenceCorpus.heldOutSentences(resource("en_heldout_sentences.txt"))
        val dev = if (System.getProperty("physiboard.eval.split") == "all") sentences else sentences.take(sentences.size / 2)
        val dictionary = assertNotNull(SentenceEval.shippedDictionary())
        val model = assertNotNull(SentenceEval.shippedContextModel())
        val userWords = SentenceEval.shippedUserWords()
        val clean = SentenceCorpus.clean(dev)
        val flips = SentenceCorpus.mixupFlips(dev)
        val all = WordMixups.measured.flatten().toSet()
        val settings = SentenceEval.SHIPPED.copy(fixWordMixups = true)
        val pool = Executors.newFixedThreadPool(24)
        val futures = ArrayList<java.util.concurrent.Future<String>>()
        for (ratio in listOf(3.0, 4.0, 5.0, 6.0)) for (evidence in listOf(5, 10)) for (side in listOf(Double.POSITIVE_INFINITY, 2.0, 1.0, 0.5, 0.0)) {
            futures.add(
                pool.submit<String> {
                    val tuning = ContextTuning(mixups = WordMixups.Tuning(minLogRatio = ratio, minEvidence = evidence, maxSideLoss = side), mixupWords = all)
                    val eval = SentenceEval(dictionary, model, settings, tuning, userWords)
                    val a = eval.run(clean)
                    val d = eval.run(flips, stopAfterError = true)
                    val lines = StringBuilder("MIXSWEEP ratio=$ratio evidence=$evidence side=$side clean clobbered=${a.clobbered}\n")
                    for ((pair, part) in d.byLabel().toSortedMap()) {
                        val members = pair.split('/').toSet()
                        val originals = a.words.filter { SentenceEval.key(it.typed) in members }
                        val clobbered = originals.count { it.outcome == CaseOutcome.CLOBBERED }
                        lines.append("MIXSET ratio=$ratio evidence=$evidence side=$side ${pair.padEnd(22)} flipped=${part.typos} fixed=${part.fixed} wrong=${part.wrong} | originals=${originals.size} clobbered=$clobbered\n")
                    }
                    lines.toString()
                },
            )
        }
        for (f in futures) print(f.get())
        pool.shutdown()
    }
}
