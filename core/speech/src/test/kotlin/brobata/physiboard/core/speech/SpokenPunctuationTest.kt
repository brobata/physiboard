package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: dictation.md SS7.7 (spoken punctuation) and SS7.8 (texting style), row by row. */
class SpokenPunctuationTest {

    private val english = SpokenPunctuation.Vocabulary.ENGLISH
    private fun convert(text: String) = SpokenPunctuation.apply(text, english).text
    private fun finish(raw: String, before: String?, settings: DictationTextSettings = DictationTextSettings()) =
        UtteranceFinisher.finish(raw, UtteranceContext(before, writtenBySession = ""), settings)

    // --- the table --------------------------------------------------------------------------

    @Test
    fun `each phrase becomes its mark with the spacing of that mark`() {
        assertEquals("hello, world", convert("hello comma world"))
        assertEquals("done. next", convert("done period next"))
        assertEquals("done. next", convert("done full stop next"))
        assertEquals("really? yes", convert("really question mark yes"))
        assertEquals("wow! yes", convert("wow exclamation point yes"))
        assertEquals("wow! yes", convert("wow exclamation mark yes"))
        assertEquals("two things: milk", convert("two things colon milk"))
        assertEquals("first; second", convert("first semicolon second"))
        assertEquals("first; second", convert("first semi colon second"))
        assertEquals("twenty-one", convert("twenty hyphen one"))
        assertEquals("one - two", convert("one dash two"))
        assertEquals("it's", convert("it apostrophe s"))
        assertEquals("well... maybe", convert("well dot dot dot maybe"))
        assertEquals("well... maybe", convert("well ellipsis maybe"))
        assertEquals("she said \"hello\" and left", convert("she said open quote hello close quote and left"))
        assertEquals("she said \"hello\" and left", convert("she said begin quote hello end quote and left"))
        assertEquals("a note (see below) here", convert("a note open paren see below close paren here"))
        assertEquals("a note (see below) here", convert("a note left parenthesis see below right parenthesis here"))
        assertEquals("first\nsecond", convert("first new line second"))
        assertEquals("first\nsecond", convert("first newline second"))
        assertEquals("first\n\nsecond", convert("first new paragraph second"))
    }

    @Test
    fun `the phrase is matched whatever its case and whatever mark the engine hung on it`() {
        assertEquals("hello, world", convert("hello Comma, world"))
        assertEquals("hello. World", convert("hello Period. World"))
        assertEquals("hello!", convert("hello exclamation point."))
    }

    @Test
    fun `a result with no phrase in it is returned untouched`() {
        val text = "Hold on,  I need to finish this thought."
        val converted = SpokenPunctuation.apply(text, english)
        assertTrue(converted.text === text)
        assertNull(converted.opening)
        assertNull(converted.closing)
    }

    // --- multiples --------------------------------------------------------------------------

    @Test
    fun `a count before the plural repeats the mark - the maintainer's three exclamation points`() {
        assertEquals("yes!!!", convert("yes three exclamation points"))
        assertEquals("yes!!!", convert("yes 3 exclamation points"))
        assertEquals("yes!!", convert("yes two exclamation marks"))
        assertEquals("what??", convert("what two question marks"))
        // A number before the singular is dictation; only the plural says the number counts marks.
        assertEquals("yes three!", convert("yes three exclamation point"))
        assertEquals("see you at 5.", convert("see you at 5 period"))
        assertEquals("step two.", convert("step two period"))
        assertEquals("I'm 40, and", convert("I'm 40 comma and"))
    }

    @Test
    fun `phrases in a row join as one run of marks`() {
        assertEquals("yes!!", convert("yes exclamation point exclamation point"))
        assertEquals("what?!", convert("what question mark exclamation point"))
    }

    @Test
    fun `a count has a ceiling, and only the counting words count`() {
        assertEquals("yes" + "!".repeat(10), convert("yes 40 exclamation points"))
        assertEquals("fifty exclamation points", convert("fifty exclamation points"), "not a count word: a plural with no count")
    }

    // --- ambiguity --------------------------------------------------------------------------

    @Test
    fun `after a determiner the phrase is the noun and stays a word`() {
        assertEquals("a period of time", convert("a period of time"))
        assertEquals("the comma in that sentence", convert("the comma in that sentence"))
        assertEquals("the question mark", convert("the question mark"))
        assertEquals("another comma", convert("another comma"))
        assertEquals("every full stop", convert("every full stop"))
        assertEquals("my apostrophe", convert("my apostrophe"))
        assertEquals("the three exclamation points", convert("the three exclamation points"))
    }

    @Test
    fun `a pronoun or a word that merely can be a determiner does not keep the word`() {
        // "her", "that", "no" end sentences far more often than they name a mark (review, 2026-10-23).
        assertEquals("I told her.", convert("I told her period"))
        assertEquals("I called her, but", convert("I called her comma but"))
        assertEquals("don't do that.", convert("don't do that period"))
        assertEquals("the answer is no.", convert("the answer is no period"))
        assertEquals("is this?", convert("is this question mark"))
    }

    @Test
    fun `the engine's opening punctuation on the phrase keeps it a word`() {
        assertEquals("the word (comma) here", convert("the word (comma) here"))
        assertEquals("say \"period\" to end", convert("say \"period\" to end"))
    }

    @Test
    fun `a plural with no count stays a word`() {
        assertEquals("too many commas", convert("too many commas"))
        assertEquals("exclamation points everywhere", convert("exclamation points everywhere"))
    }

    @Test
    fun `a determiner the engine ended its sentence on is not this phrase's determiner`() {
        assertEquals("I like that.", convert("I like that. Period"))
    }

    @Test
    fun `the word before the phrase decides, not the word before that`() {
        assertEquals("the word, then", convert("the word comma then"))
    }

    // --- merging with the engine's own punctuation --------------------------------------------

    @Test
    fun `the maintainer's sentence - the engine's full stop and capital before the spoken period go, the spoken one stands`() {
        // 2026-10-23, dictated: "it just wrote the word comma, then appear. Period, then started a new sentence"
        val finished = finish("it just wrote the word comma, then appear. Period, then started a new sentence", "")
        assertEquals("It just wrote the word, then appear. Then started a new sentence ", finished.plainText)
    }

    @Test
    fun `an engine mark attached to the phrase itself is absorbed`() {
        assertEquals("hello, world", convert("hello comma, world"))
        assertEquals("hello. world", convert("hello period. world"))
    }

    @Test
    fun `an engine mark on the word before the phrase is absorbed - the spoken one wins`() {
        assertEquals("hello, world", convert("hello. comma world"))
        assertEquals("hello, world", convert("hello, comma world"))
        assertEquals("hello! world", convert("hello. exclamation point world"))
    }

    @Test
    fun `an engine sentence end absorbed by a comma takes its capital with it, a spoken sentence end keeps it`() {
        assertEquals("the word, then appear", convert("the word comma. Then appear"))
        assertEquals("the word, then appear", convert("the word. Comma Then appear"))
        assertEquals("the word. Then appear", convert("the word period. Then appear"))
        // The pronoun and a word that is not the engine's plain capital are left alone.
        assertEquals("yes, I know", convert("yes comma. I know"))
        assertEquals("yes, NASA knows", convert("yes comma. NASA knows"))
    }

    @Test
    fun `a line break, an opening mark or a dash stands after the engine's mark instead of replacing it`() {
        // Review, 2026-10-23: across utterances the full stop was kept; inside one it was lost.
        assertEquals("See you then.\n\nThanks, Bob.", convert("See you then. New paragraph. Thanks, Bob."))
        assertEquals("He said. \"hi", convert("He said. Open quote hi"))
        assertEquals("one. - two", convert("one. dash two"))
        // A mark that takes the engine's place still replaces it.
        assertEquals("See you then! Thanks", convert("See you then. Exclamation point Thanks"))
    }

    @Test
    fun `a sentence that opens with a spoken quote or parenthesis is capitalised at its first letter`() {
        assertEquals("\"Hello\" she said ", finish("open quote hello close quote she said", "").plainText)
        assertEquals("\"Hello", DictationPartialDisplay.display("open quote hello", UtteranceContext(""), DictationTextSettings()))
        assertEquals("Fine. \"Hello\" ", finish("fine period open quote hello close quote", "").plainText)
        assertEquals("Fine\n(Hello) ", finish("fine new line open paren hello close paren", "").plainText)
    }

    @Test
    fun `a phrase followed by of is the noun`() {
        assertEquals("a long period of time", convert("a long period of time"))
        assertEquals("two periods of silence", convert("two periods of silence"))
        assertEquals(".", convert("period."), "the engine's mark on it is not 'of'")
        assertEquals("the end. Of course", convert("the end period. Of course"), "a mark between them is a sentence end, not a complement")
    }

    @Test
    fun `new lines is prose, not a count of breaks`() {
        assertEquals("I added two new lines of code", convert("I added two new lines of code"))
    }

    @Test
    fun `a closing quote the engine hung on the phrase is kept after the mark`() {
        assertEquals("she said \"hello.\"", convert("she said open quote hello period\""))
    }

    // --- capitalisation and spacing through the finisher ------------------------------------

    @Test
    fun `the finisher capitalises after a spoken sentence end and never after a spoken comma`() {
        assertEquals("Hello, world. Next one? Yes! Sure ", finish("hello comma world period next one question mark yes exclamation point sure", "").plainText)
    }

    @Test
    fun `a spoken new line or paragraph ends the text without a space and the next word is capitalised`() {
        val line = finish("first line new line second line", "")
        assertEquals("First line\nSecond line ", line.plainText)
        val paragraph = finish("first new paragraph", "")
        assertEquals("First\n\n", paragraph.plainText)
        // The next utterance's context ends in the newline: capitalised, no leading space.
        val next = finish("next", "First\n\n")
        assertEquals("Next ", next.plainText)
    }

    @Test
    fun `an utterance that opens with a spoken mark joins onto the word before it`() {
        // "I went home" landed as "I went home " with the finisher's space; "period" said after a pause.
        val finished = finish("Period.", "I went home ", DictationTextSettings())
        assertEquals(
            listOf(
                DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing, // the partial's region, out of the way of the read
                DictationTextOp.DeleteBeforeCursorIfText(" ", anchor = "I went home "),
                DictationTextOp.SetComposingText(". "), DictationTextOp.FinishComposing,
            ),
            finished.ops,
        )
        assertEquals(". ", finished.plainText)
        assertEquals(" ", finished.joinedOver)
    }

    @Test
    fun `a leading spoken mark replaces the engine's mark this session left, never the user's own`() {
        val own = UtteranceContext("I went home. ", writtenBySession = "I went home. ")
        val spoken = UtteranceFinisher.finish("exclamation point", own, DictationTextSettings())
        assertEquals(". ", spoken.joinedOver)
        assertEquals("! ", spoken.plainText)

        val users = UtteranceContext("I went home. ", writtenBySession = "")
        val afterUser = UtteranceFinisher.finish("exclamation point", users, DictationTextSettings())
        assertEquals(" ", afterUser.joinedOver, "the user's full stop stays")

        val run = UtteranceContext("Wow!!! ", writtenBySession = "Wow!!! ")
        val afterRun = UtteranceFinisher.finish("question mark", run, DictationTextSettings())
        assertEquals(" ", afterRun.joinedOver, "a run of spoken marks is the user's")
    }

    @Test
    fun `a leading new line takes the spaces but keeps the mark before it`() {
        val own = UtteranceContext("I went home. ", writtenBySession = "I went home. ")
        val finished = UtteranceFinisher.finish("new line next", own, DictationTextSettings())
        assertEquals(" ", finished.joinedOver)
        assertEquals("\nNext ", finished.plainText)
    }

    @Test
    fun `the join is decided by what was converted, never by the first character of the engine's text`() {
        // Review, 2026-10-23: "'Cause", "'90s" and "-5 degrees" are the engine's own words.
        val own = UtteranceContext("I went home. ", writtenBySession = "I went home. ")
        for (engineText in listOf("'Cause I wanted to", "'90s music", "-5 degrees")) {
            val finished = UtteranceFinisher.finish(engineText, own, DictationTextSettings())
            assertEquals("", finished.joinedOver, engineText)
            assertEquals(listOf(DictationTextOp.SetComposingText("${lowerEngineSegmentCapital(engineText)} "), DictationTextOp.FinishComposing), finished.ops, engineText)
        }
    }

    @Test
    fun `a mark the user spoke is joined onto, not replaced - as a run of marks is built across pauses`() {
        val wow = finish("wow exclamation point", "", DictationTextSettings())
        assertEquals("Wow! ", wow.plainText)
        assertTrue(wow.endsWithSpokenMark)
        val own = UtteranceContext("Wow! ", writtenBySession = "Wow! ", sessionTailIsSpokenMark = true)
        val then = UtteranceFinisher.finish("question mark", own, DictationTextSettings())
        assertEquals(" ", then.joinedOver, "the spoken ! stays")
        assertEquals("? ", then.plainText)
        // The engine's own mark is still replaced.
        val engine = UtteranceContext("Wow. ", writtenBySession = "Wow. ", sessionTailIsSpokenMark = false)
        assertEquals(". ", UtteranceFinisher.finish("question mark", engine, DictationTextSettings()).joinedOver)
    }

    @Test
    fun `the join carries the frozen context's tail as its anchor, so the field is checked at the right place`() {
        val before = "Thanks. I told them I went home. "
        val finished = finish("period", before)
        val join = finished.ops.filterIsInstance<DictationTextOp.DeleteBeforeCursorIfText>().single()
        assertEquals(" ", join.expected)
        assertEquals(before.takeLast(DictationSpacing.JOIN_ANCHOR), join.anchor)
        assertTrue(join.anchor.endsWith(join.expected))
    }

    @Test
    fun `a hyphen said alone glues to both sides`() {
        val finished = finish("hyphen", "twenty ")
        assertEquals(" ", finished.joinedOver)
        assertEquals("-", finished.plainText, "no trailing space either: the next word joins on")
        assertEquals("one ", finish("one", "twenty-").plainText, "and the next utterance gets no leading space after it")
    }

    @Test
    fun `nothing to join over means no delete at all`() {
        val finished = finish("Period.", "I went home", DictationTextSettings())
        assertEquals(listOf(DictationTextOp.SetComposingText(". "), DictationTextOp.FinishComposing), finished.ops)
        assertEquals("", finished.joinedOver)
    }

    @Test
    fun `the spoken words are remembered as said, the field as written`() {
        val finished = finish("wow three exclamation points", "")
        assertEquals("Wow!!! ", finished.plainText)
        assertEquals("wow three exclamation points", finished.spokenWords)
    }

    // --- the switch and the language -------------------------------------------------------------

    @Test
    fun `off, the words are written as the engine gave them`() {
        val off = DictationTextSettings(spokenPunctuation = false)
        assertEquals("Hello comma world ", finish("hello comma world", "", off).plainText)
    }

    @Test
    fun `only English has a table - another language passes through`() {
        assertNull(SpokenPunctuation.vocabularyFor("it-IT"))
        assertNull(SpokenPunctuation.vocabularyFor("de"))
        assertTrue(SpokenPunctuation.vocabularyFor("en-US") === english)
        assertTrue(SpokenPunctuation.vocabularyFor("en_GB") === english)
        assertTrue(SpokenPunctuation.vocabularyFor(null) === english, "the one bundled language")
        val italian = DictationTextSettings(languageTag = "it-IT")
        assertEquals("Hello comma world ", finish("hello comma world", "", italian).plainText)
        assertFalse(italian.textingStyleInForce)
    }

    @Test
    fun `a partial shows the marks it will land, first-letter capitalised only`() {
        val settings = DictationTextSettings()
        assertEquals("Wow!!!", DictationPartialDisplay.display("wow three exclamation points", UtteranceContext(""), settings))
        assertEquals("wow three", DictationPartialDisplay.display("wow three", UtteranceContext("Hi "), settings))
        assertEquals("done. next", DictationPartialDisplay.display("done period next", UtteranceContext("Hi "), settings))
    }

    // --- texting style (SS7.8) ------------------------------------------------------------------

    @Test
    fun `laugh runs are joined, one syllable and lol are not`() {
        assertEquals("hahaha", TextingStyle.apply("ha ha ha"))
        assertEquals("Haha", TextingStyle.apply("Ha ha"))
        assertEquals("that was hahaha. Really", TextingStyle.apply("that was ha ha ha. Really"))
        assertEquals("hahahaha", TextingStyle.apply("haha haha"))
        assertEquals("hehheh", TextingStyle.apply("heh heh"))
        assertEquals("ha", TextingStyle.apply("ha"))
        assertEquals("lol that was funny", TextingStyle.apply("lol that was funny"))
        assertEquals("haha", TextingStyle.apply("haha"))
    }

    @Test
    fun `a mark between syllables ends the run, and he is a pronoun`() {
        assertEquals("ha. Ha", TextingStyle.apply("ha. Ha"))
        assertEquals("ha, he said", TextingStyle.apply("ha, he said"))
        assertEquals("he he", TextingStyle.apply("he he"))
    }

    @Test
    fun `aw and aww become aww anywhere, in the case they came in`() {
        assertEquals("aww", TextingStyle.apply("aw"))
        assertEquals("Aww, that's sweet", TextingStyle.apply("Aw, that's sweet"))
        assertEquals("and aww no", TextingStyle.apply("and awww no"))
        assertEquals("aww", TextingStyle.apply("awwwww"))
    }

    @Test
    fun `awe and daw are words - a sigh only first in the sentence with something a sigh leads into`() {
        assertEquals("Aww, that's so sweet", TextingStyle.apply("Awe, that's so sweet"))
        assertEquals("aww that's sweet", TextingStyle.apply("awe that's sweet"))
        assertEquals("Aww", TextingStyle.apply("Awe"))
        assertEquals("ok. Aww poor thing", TextingStyle.apply("ok. Awe poor thing"))
        assertEquals("Aww Jeremiah", TextingStyle.apply("Awe Jeremiah"), "a name after it")
        assertEquals("Daww look at him", TextingStyle.apply("Daw look at him"))
        assertEquals("Daww", TextingStyle.apply("Dawww"))
        // The real words.
        assertEquals("I stood in awe of it", TextingStyle.apply("I stood in awe of it"))
        assertEquals("awe inspires people", TextingStyle.apply("awe inspires people"))
        assertEquals("the dog barked", TextingStyle.apply("the dog barked"))
        assertEquals("a daw is a bird", TextingStyle.apply("a daw is a bird"))
        assertEquals("Daw is a bird", TextingStyle.apply("Daw is a bird"))
        // The start of a result is a sentence start only when the context says so: "I was in", a pause, "Awe.".
        assertEquals("Awe.", TextingStyle.apply("Awe.", atSentenceStart = false))
        assertEquals("Awe. ", finish("Awe.", "I was in ").plainText)
        assertEquals("Aww. ", finish("Awe.", "I was. ").plainText)
        assertEquals("aww. ", finish("aw.", "I was in ").plainText, "aw is no word: a sigh anywhere")
    }

    @Test
    fun `texting style runs after the marks and keeps the line breaks`() {
        val finished = finish("ha ha ha new line aw that's sweet", "")
        assertEquals("Hahaha\nAww that's sweet ", finished.plainText)
        val off = finish("ha ha ha comma aw", "", DictationTextSettings(textingStyle = false))
        assertEquals("Ha ha ha, aw ", off.plainText)
    }
}
