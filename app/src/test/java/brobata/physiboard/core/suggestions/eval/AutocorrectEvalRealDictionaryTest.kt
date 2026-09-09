package brobata.physiboard.core.suggestions.eval

import android.content.Context
import brobata.physiboard.core.suggestions.AndroidDictionaryRepository
import brobata.physiboard.core.suggestions.SuggestionSettings
import brobata.physiboard.core.suggestions.UserDictionaryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The same corpus, against the dictionary that actually ships.
 *
 * [AutocorrectEvalTest] holds dictionary coverage constant with a 105-word vocabulary, which
 * makes its false-correction rate close to meaningless: clobbering a correctly typed word needs
 * a crowded dictionary before it can happen at all. `en_base.dict` has ~48k normalized keys, so
 * this is where a control like `form` finally has enough neighbours to be in danger.
 *
 * It is slow — a 13 MB CBOR decode into three successive in-memory representations, which is
 * the shipped load path and not something to work around here — so it is opt-in:
 *
 *     ./gradlew :app:testDebugUnitTest -Pphysiboard.eval.realDictionary=true \
 *         --tests '*AutocorrectEvalRealDictionaryTest*'
 *
 * Without the flag it skips rather than fails, so the normal test loop stays fast.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutocorrectEvalRealDictionaryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        assumeTrue(
            "set -Pphysiboard.eval.realDictionary=true to run the real-dictionary evaluation",
            System.getProperty("physiboard.eval.realDictionary") == "true"
        )
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun theShippedDictionaryIsScored() {
        val repository = AndroidDictionaryRepository(
            context = context,
            assets = context.assets,
            userDictionaryStore = UserDictionaryStore(),
            baseLocale = Locale.ENGLISH
        )
        // loadIfNeeded refuses to run on the main thread (DictionaryRepository.kt:125), and
        // Robolectric's runBlocking sits on it, so the load has to be handed to another one.
        runBlocking { withContext(Dispatchers.IO) { repository.loadIfNeeded() } }
        assertTrue("the English dictionary did not load", repository.isReady)

        val cases = AutocorrectEval.loadCases()
        val shipped = SuggestionSettings(maxAutoReplaceDistance = 2, useKeyboardProximity = true)

        val aggressive = AutocorrectEval.run(cases, repository, shipped, locale = Locale.ENGLISH)
        val conservative = AutocorrectEval.run(
            cases, repository, shipped.copy(maxAutoReplaceDistance = 1), locale = Locale.ENGLISH
        )
        val noProximity = AutocorrectEval.run(
            cases, repository, shipped.copy(useKeyboardProximity = false), locale = Locale.ENGLISH
        )

        println("=== real en_base.dict ===")
        println("--- shipped: distance 2, proximity on ---")
        println(aggressive.format())
        println("--- distance 1, proximity on ---")
        println(conservative.format())
        println("--- distance 2, proximity off ---")
        println(noProximity.format())

        // Ratchets, set to the first measured run (2026-09-08): 0.041 / 0.775 shipped.
        // Tighten when a change earns it; never loosen one to make a change pass.
        assertTrue(
            "false-correction rate regressed: ${aggressive.falseCorrectionRate}",
            aggressive.falseCorrectionRate <= 0.041
        )
        assertTrue("recall regressed: ${aggressive.recall}", aggressive.recall >= 0.775)

        // The two real decision failures are `definately -> defiantly` and `wierd -> wired`:
        // a genuine word beating the intended one at equal or lower edit distance, with nothing
        // in the scorer able to prefer the right one. That is the exact shape W2, W3 and W5
        // exist to fix, so it is worth failing loudly if it silently gets worse.
        assertTrue(
            "more wrong corrections than the two known ones: ${aggressive.wrong}",
            aggressive.wrong <= 2
        )
    }
}
