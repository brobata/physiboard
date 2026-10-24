package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordFrequency
import brobata.physiboard.core.keys.Action
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** spec: autocorrect-suggestions.md SS9, SS10, "Test cases" T24, T25, T51. */
class AutocorrectDecisionTest {

    // ---- Confidence (T51) ----

    @Test
    fun `T51 confidence is the relative margin, clamped and defaulted`() {
        assertEquals(0.01, Confidence.compute(1.0, 0.99), absoluteTolerance = 1e-9)
        assertEquals(1.0, Confidence.compute(1.0, null))
        assertEquals(0.0, Confidence.compute(0.0, 0.5))
    }

    private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
        assertEquals(true, kotlin.math.abs(expected - actual) < absoluteTolerance, "expected $expected but was $actual")
    }

    // ---- Safe shape, distance 2, English (T24) ----

    private fun safe(word: String, candidate: String, distance: Int, maxDistance: Int = 2, allowance: Int = 2, currentWord: Boolean = true): Boolean =
        SafeShape.evaluate(word, candidate, distance, currentWord, isOrthographicVariant = false, isCaseVariant = false, maxAutoReplaceDistance = maxDistance, lengthChangeAllowance = allowance)

    @Test
    fun `T24 accepted corrections at distance 1 and 2 in English`() {
        assertEquals(true, safe("definetly", "definitely", distance = 2))
        assertEquals(true, safe("sensitivy", "sensitivity", distance = 2))
        assertEquals(true, safe("Oliva", "Olive", distance = 1))
    }

    @Test
    fun `T24 pure suffix growth is refused`() {
        assertFalse(safe("work", "works", distance = 1))
        assertFalse(safe("behavio", "behavior", distance = 1))
    }

    @Test
    fun `T24 same length with a changed first letter is refused`() {
        assertFalse(safe("elly", "ally", distance = 1))
    }

    @Test
    fun `T24 a candidate beyond the configured distance is refused`() {
        assertFalse(safe("definetly", "definitely", distance = 5))
    }

    @Test
    fun `T24 a next-word candidate is never safe`() {
        assertFalse(safe("definetly", "definitely", distance = 2, currentWord = false))
    }

    @Test
    fun `T24 a candidate far beyond edit reach is refused`() {
        assertFalse(safe("dfntly", "definitely", distance = 4))
    }

    @Test
    fun `T24 non-English languages get no length allowance`() {
        assertFalse(safe("definetly", "definitely", distance = 2, allowance = 0))
        // Same-length corrections are unaffected by the language allowance.
        assertEquals(true, safe("Oliva", "Olive", distance = 1, allowance = 0))
    }

    // ---- Safe shape, distance 1 (T25) ----

    @Test
    fun `T25 distance-0 candidates need an orthographic or case reason`() {
        assertFalse(safe("hal", "hallo", distance = 0, maxDistance = 1, allowance = 0))
        assertEquals(
            true,
            SafeShape.evaluate("problem", "Problem", distance = 0, isCurrentWordCandidate = true, isOrthographicVariant = false, isCaseVariant = true, maxAutoReplaceDistance = 1, lengthChangeAllowance = 0),
        )
    }

    @Test
    fun `T25 an inserted letter that doubles a neighbour is accepted`() {
        assertEquals(true, safe("wil", "will", distance = 1, maxDistance = 1, allowance = 0))
    }

    @Test
    fun `T25 kaputte to kaputt is refused (a pure truncation)`() {
        assertFalse(safe("kaputte", "kaputt", distance = 1, maxDistance = 1, allowance = 0))
    }

    @Test
    fun `T25 an acronym-like candidate for a lowercase word is refused`() {
        assertFalse(safe("idk", "IFK", distance = 1, maxDistance = 1, allowance = 0))
    }

    @Test
    fun `T25 lowercase never becomes capitalized unless a case variant`() {
        assertFalse(safe("hallo", "Halle", distance = 1, maxDistance = 1, allowance = 0))
    }

    @Test
    fun `T25 same length, first letter changed is refused, unchanged is accepted`() {
        assertFalse(safe("ding", "fing", distance = 1, maxDistance = 1, allowance = 0))
        assertEquals(true, safe("fihg", "fing", distance = 1, maxDistance = 1, allowance = 0))
    }

    // ---- The rule: a correctly spelled word is never overwritten (SS10 invariant) ----

    @Test
    fun `invariant - a known word is never committed as a plain fuzzy replacement`() {
        val knownWords = listOf("form", "trail", "quiet", "salve", "work", "definitely", "hello", "und", "the")
        for (word in knownWords) {
            val facts = AutocorrectCandidateFacts(
                word = word,
                candidate = word + "x", // some other spelling entirely; never orthographic or a case variant
                distance = 1,
                isCurrentWordCandidate = true,
                isOrthographicVariant = false,
                isCaseVariant = false,
                isKnown = true,
                exactKnownExists = true,
                exactPrimaryCaseExists = true,
                isRejected = false,
                topScore = 10.0,
                runnerUpScore = 1.0,
            )
            val outcome = AutocorrectDecision.evaluate(facts, maxAutoReplaceDistance = 3, lengthChangeAllowance = 2)
            assertEquals(AutocorrectOutcome.Refuse(AutocorrectRefusalReason.KNOWN_WORD), outcome)
        }
    }

    @Test
    fun `invariant - a known word can still receive a case or accent repair`() {
        val caseFacts = AutocorrectCandidateFacts(
            word = "problem", candidate = "Problem", distance = 0, isCurrentWordCandidate = true,
            isOrthographicVariant = false, isCaseVariant = true, isKnown = true,
            exactKnownExists = true, exactPrimaryCaseExists = false, isRejected = false,
            topScore = 10.0, runnerUpScore = 1.0,
        )
        val outcome = AutocorrectDecision.evaluate(caseFacts, maxAutoReplaceDistance = 2, lengthChangeAllowance = 0)
        assertEquals(AutocorrectOutcome.Commit("Problem"), outcome)
    }

    @Test
    fun `T39-T40 style - a word known elsewhere with an exact entry in its own case is left alone`() {
        val facts = AutocorrectCandidateFacts(
            word = "und", candidate = "Und", distance = 0, isCurrentWordCandidate = true,
            isOrthographicVariant = false, isCaseVariant = true, isKnown = true,
            exactKnownExists = true, exactPrimaryCaseExists = true, isRejected = false,
            topScore = 10.0, runnerUpScore = 1.0,
        )
        val outcome = AutocorrectDecision.evaluate(facts, maxAutoReplaceDistance = 2, lengthChangeAllowance = 0)
        assertEquals(AutocorrectOutcome.Refuse(AutocorrectRefusalReason.KNOWN_WORD), outcome)
    }

    @Test
    fun `a rejected word is refused regardless of shape`() {
        val facts = AutocorrectCandidateFacts(
            word = "its", candidate = "it's", distance = 1, isCurrentWordCandidate = true,
            isOrthographicVariant = false, isCaseVariant = false, isKnown = false,
            exactKnownExists = false, exactPrimaryCaseExists = false, isRejected = true,
            topScore = 10.0, runnerUpScore = 1.0,
        )
        val outcome = AutocorrectDecision.evaluate(facts, maxAutoReplaceDistance = 2, lengthChangeAllowance = 0)
        assertEquals(AutocorrectOutcome.Refuse(AutocorrectRefusalReason.REJECTED_BY_USER), outcome)
    }

    @Test
    fun `too close to call refuses commit`() {
        val facts = AutocorrectCandidateFacts(
            word = "definately", candidate = "defiantly", distance = 2, isCurrentWordCandidate = true,
            isOrthographicVariant = false, isCaseVariant = false, isKnown = false,
            exactKnownExists = false, exactPrimaryCaseExists = false, isRejected = false,
            topScore = 1.0, runnerUpScore = 0.99,
        )
        val outcome = AutocorrectDecision.evaluate(facts, maxAutoReplaceDistance = 2, lengthChangeAllowance = 2)
        assertEquals(AutocorrectOutcome.Refuse(AutocorrectRefusalReason.TOO_CLOSE_TO_CALL), outcome)
    }

    // ---- Edit reach (SS9, 2026-10-23): two edits only in a long word, only as thumb slips, only with a clear margin ----

    private fun unknownWordFacts(word: String, candidate: String, distance: Int, topScore: Double = 10.0, runnerUpScore: Double? = 1.0) = AutocorrectCandidateFacts(
        word = word, candidate = candidate, distance = distance, isCurrentWordCandidate = true,
        isOrthographicVariant = false, isCaseVariant = false, isKnown = false,
        exactKnownExists = false, exactPrimaryCaseExists = false, isRejected = false,
        topScore = topScore, runnerUpScore = runnerUpScore,
    )

    @Test
    fun `reach - a word under seven letters reaches one edit, whatever the setting says`() {
        assertEquals(1, EditReach.maxDistance(letters = 4, settingCap = 2))
        assertEquals(1, EditReach.maxDistance(letters = 6, settingCap = 2))
        assertEquals(2, EditReach.maxDistance(letters = 7, settingCap = 2))
        assertEquals(0, EditReach.maxDistance(letters = 9, settingCap = 0))
        // `ckiemt` is two adjacent-key slips from `client`, and six letters: out of reach.
        assertEquals(AutocorrectOutcome.Refuse(AutocorrectRefusalReason.DISTANCE_TOO_HIGH), AutocorrectDecision.evaluate(unknownWordFacts("ckiemt", "client", distance = 2), maxAutoReplaceDistance = 2, lengthChangeAllowance = 1))
        // One slip in the same word is in reach.
        assertEquals(AutocorrectOutcome.Commit("client"), AutocorrectDecision.evaluate(unknownWordFacts("ckient", "client", distance = 1), maxAutoReplaceDistance = 2, lengthChangeAllowance = 1))
    }

    @Test
    fun `reach - two edits in a long word pass only as a pair of thumb slips`() {
        assertEquals(true, EditReach.isThumbSlipPair("keyboatf", "keyboard")) // r->t and d->f, keys that touch: 0.70
        assertEquals(true, EditReach.isThumbSlipPair("restsurajt", "restaurant")) // a->s and n->j: 0.70
        assertEquals(true, EditReach.isThumbSlipPair("sensitivy", "sensitivity")) // two dropped letters: 1.00
        assertEquals(true, EditReach.isThumbSlipPair("definetly", "definitely")) // a dropped i and an et/te swap: 0.90 (the sentence's agreement is what stops this one on the context path)
        assertFalse(EditReach.isThumbSlipPair("restaraunt", "restaurant")) // a letter moved three places: 1.40
        assertFalse(EditReach.isThumbSlipPair("maintainance", "maintenance")) // 1.70
        assertFalse(EditReach.isThumbSlipPair("kitcpem", "kitchen")) // a far key and a neighbouring one: 1.75
        assertEquals(AutocorrectOutcome.Commit("keyboard"), AutocorrectDecision.evaluate(unknownWordFacts("keyboatf", "keyboard", distance = 2), maxAutoReplaceDistance = 2, lengthChangeAllowance = 1))
        assertEquals(AutocorrectOutcome.Refuse(AutocorrectRefusalReason.DISTANCE_TOO_HIGH), AutocorrectDecision.evaluate(unknownWordFacts("restaraunt", "restaurant", distance = 2), maxAutoReplaceDistance = 2, lengthChangeAllowance = 1))
    }

    @Test
    fun `reach - two edits need a margin of 0,10, one edit still 0,02`() {
        assertEquals(0.02, Confidence.thresholdFor(1), absoluteTolerance = 1e-9)
        assertEquals(0.10, Confidence.thresholdFor(2), absoluteTolerance = 1e-9)
        // A 5% margin commits one edit and refuses two.
        assertEquals(AutocorrectOutcome.Commit("client"), AutocorrectDecision.evaluate(unknownWordFacts("ckient", "client", distance = 1, topScore = 1.0, runnerUpScore = 0.95), maxAutoReplaceDistance = 2, lengthChangeAllowance = 1))
        assertEquals(AutocorrectOutcome.Refuse(AutocorrectRefusalReason.TOO_CLOSE_TO_CALL), AutocorrectDecision.evaluate(unknownWordFacts("keyboatf", "keyboard", distance = 2, topScore = 1.0, runnerUpScore = 0.95), maxAutoReplaceDistance = 2, lengthChangeAllowance = 1))
    }

    @Test
    fun `reach - English allows a length change of one, doubled letters aside`() {
        assertFalse(safe("accompli", "accomplish", distance = 2, allowance = LengthChangeAllowance.ENGLISH))
        assertFalse(safe("sensitivy", "sensitivity", distance = 2, allowance = LengthChangeAllowance.ENGLISH))
        assertEquals(true, safe("postr", "poster", distance = 1, allowance = LengthChangeAllowance.ENGLISH))
        assertEquals(true, safe("wil", "will", distance = 1, allowance = LengthChangeAllowance.ENGLISH))
        // Two missing halves of doubles are two letters of length, and still the one slip allowed past the allowance.
        assertEquals(true, safe("acomodate", "accommodate", distance = 2, allowance = LengthChangeAllowance.ENGLISH))
        // As typed at the start of a sentence, against the dictionary's lowercase spelling.
        assertEquals(true, safe("Acomodate", "accommodate", distance = 2, allowance = LengthChangeAllowance.ENGLISH))
        assertEquals(true, safe("Komen", "kommen", distance = 1, allowance = 0))
        assertEquals(true, SafeShape.extraLettersDoubleNeighbours("ocurence", "occurrence"))
        // A dropped pair is not a doubled letter: the extras must double a letter the word has.
        assertFalse(SafeShape.extraLettersDoubleNeighbours("mi", "miss"))
        assertFalse(SafeShape.extraLettersDoubleNeighbours("ab", "accb"))
        assertFalse(SafeShape.extraLettersDoubleNeighbours("accompli", "accomplish"))
        assertFalse(SafeShape.extraLettersDoubleNeighbours("sensitivy", "sensitivity"))
        assertFalse(SafeShape.extraLettersDoubleNeighbours("will", "will"))
    }

    @Test
    fun `reach - a word with letters off the Titan's grid is not judged by the geometry`() {
        // Cyrillic (ru, uk ship): the key geometry has nothing to say, so the length and margin gates alone decide.
        assertEquals(true, EditReach.isThumbSlipPair("привет", "превед"))
        assertEquals(AutocorrectOutcome.Commit("преведик"), AutocorrectDecision.evaluate(unknownWordFacts("приветик", "преведик", distance = 2), maxAutoReplaceDistance = 2, lengthChangeAllowance = 0))
    }

    // ---- The invariant, adversarially, through the paths that reach the decision ----

    private val en = LanguageCode.of("en")!!
    private fun dict(vararg entries: Pair<String, Int>): DictionaryIndex = DictionaryIndex.build(en, entries.map { WordFrequency(it.first, it.second) })

    /** Types [keys] into a plain field one request at a time and returns the field's text; a [Action.ReplaceRecent] stands in for a long press. */
    private fun typeThrough(actions: List<Action>, resources: TextInputResources): String {
        var text = ""
        var state = TextInputState()
        val settings = TextInputSettingsBundle(autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 1), lengthChangeAllowance = 2)
        for (action in actions) {
            val result = TextInputPipeline.handle(TextInputRequest.Key(action), FieldContext(FieldKind.NORMAL), settings, resources, state, EditorSnapshot(textBeforeCursor = text))
            for (op in result.ops) {
                when (op) {
                    is EditorOp.CommitText -> text += op.text
                    is EditorOp.DeleteSurrounding -> text = text.dropLast(op.before)
                    is EditorOp.ReplaceBeforeCursor -> text = text.dropLast(op.count) + op.text
                    else -> Unit
                }
            }
            state = result.state
        }
        return text
    }

    @Test
    fun `invariant - a long-press punctuation mark never overwrites the correctly spelled word before it`() {
        // The long press commits the held key's letter first and swaps it for the mark a moment
        // later (text-input.md SS5.2). The decision must be asked about "cat", never "catm": with
        // "cats" the best candidate for "catm", the letter-inclusive question overwrites a known word.
        val resources = TextInputResources(dictionaries = listOf(dict("cats" to 250, "cat" to 100, "have" to 200, "i" to 200, "a" to 200)))
        val actions = "I have a cat".map { Action.Commit(it.toString()) } + Action.Commit("m") + Action.ReplaceRecent(1, ".")
        assertEquals("I have a cat.", typeThrough(actions, resources))
    }

    @Test
    fun `invariant - a user-added lowercase word is never recased by primary case repair`() {
        val resources = TextInputResources(
            dictionaries = listOf(dict("Paris" to 200)),
            userWords = UserWordStore.empty().withPersonalWordAdded("paris", nowMillis = 0L),
        )
        val actions = "paris".map { Action.Commit(it.toString()) } + Action.Commit(" ")
        assertEquals("paris ", typeThrough(actions, resources))
    }
}
