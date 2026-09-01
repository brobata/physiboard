package brobata.physiboard

import brobata.physiboard.core.suggestions.AutoReplaceController
import brobata.physiboard.core.suggestions.SuggestionKind
import brobata.physiboard.core.suggestions.SuggestionResult
import brobata.physiboard.core.suggestions.SuggestionSettings
import brobata.physiboard.core.suggestions.SuggestionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cases taken from a real debug export off the maintainer's Titan, 2026-08-31.
 *
 * The shape gate accepted only same-length edits and doubled letters, so `definetly` and
 * `sensitivy` were both logged as `outcome=skipped reason='unsafe_shape'` with the correct
 * candidate right there, while `Oliva` -> `Olive` applied purely because the lengths matched.
 */
class TypoShapeProfileTest {

    private val settings = SuggestionSettings(maxAutoReplaceDistance = 2)

    private fun candidate(
        word: String,
        distance: Int,
        kind: SuggestionKind = SuggestionKind.CURRENT_WORD
    ) = SuggestionResult(
        candidate = word,
        distance = distance,
        score = 1.0,
        source = SuggestionSource.MAIN,
        kind = kind
    )

    private fun safe(
        input: String,
        candidate: String,
        distance: Int,
        language: String,
        orthographic: Boolean = false
    ) = AutoReplaceController.isSafeAutoReplaceCandidate(
        input = input,
        lookupWord = input,
        candidate = candidate(candidate, distance),
        settings = settings,
        isOrthographicVariant = orthographic,
        languageCode = language
    )

    // ---- the regression, in English ---------------------------------------------------------

    @Test
    fun droppedLetterIsCorrectedInEnglish() {
        assertTrue(safe("definetly", "definitely", distance = 2, language = "en"))
    }

    @Test
    fun twoDroppedLettersAreCorrectedInEnglish() {
        assertTrue(safe("sensitivy", "sensitivity", distance = 2, language = "en"))
    }

    @Test
    fun sameLengthSubstitutionStillWorks() {
        assertTrue(safe("Oliva", "Olive", distance = 1, language = "en"))
    }

    // ---- what must stay refused --------------------------------------------------------------

    @Test
    fun suffixGrowthIsNotATypo() {
        // "work" -> "works" is morphology; the user typed a complete word.
        assertFalse(safe("work", "works", distance = 1, language = "en"))
    }

    @Test
    fun wordCompletionIsNotATypo() {
        // "behavio" -> "behavior" is a completion, not a slip.
        assertFalse(safe("behavio", "behavior", distance = 1, language = "en"))
    }

    @Test
    fun changingTheFirstLetterIsRefused() {
        assertFalse(safe("elly", "ally", distance = 1, language = "en"))
    }

    @Test
    fun distanceBeyondTheUserSettingIsRefused() {
        assertFalse(safe("definetly", "definitely", distance = 5, language = "en"))
    }

    @Test
    fun aCandidateThatIsNotTheCurrentWordIsRefused() {
        val result = AutoReplaceController.isSafeAutoReplaceCandidate(
            input = "definetly",
            lookupWord = "definetly",
            candidate = candidate("definitely", 2, kind = SuggestionKind.NEXT_WORD),
            settings = settings,
            isOrthographicVariant = false,
            languageCode = "en"
        )
        assertFalse(result)
    }

    @Test
    fun aLengthChangeBeyondTheProfileIsRefused() {
        // Four characters adrift is not a slip, whatever the distance says.
        assertFalse(safe("dfntly", "definitely", distance = 2, language = "en"))
    }

    // ---- languages that have not been opened up ----------------------------------------------

    @Test
    fun anUnverifiedLanguageKeepsTheConservativeShapeGate() {
        // Turkish morphology is suffix-driven; allowing length changes blind would make the
        // keyboard aggressively wrong rather than merely unhelpful.
        assertFalse(safe("definetly", "definitely", distance = 2, language = "tr"))
    }

    @Test
    fun anUnknownLanguageKeepsTheConservativeShapeGate() {
        assertFalse(safe("definetly", "definitely", distance = 2, language = ""))
    }

    @Test
    fun sameLengthEditsStillWorkInEveryLanguage() {
        assertTrue(safe("Oliva", "Olive", distance = 1, language = "tr"))
        assertTrue(safe("Oliva", "Olive", distance = 1, language = "hu"))
    }
}
