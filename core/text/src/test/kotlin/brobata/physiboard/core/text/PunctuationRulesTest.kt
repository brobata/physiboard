package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: text-input.md SS6, "Test cases" T1, T3, T5-T25, T31-T39. */
class PunctuationRulesTest {

    private fun apply(ops: List<EditorOp>, before: String): String {
        var text = before
        for (op in ops) {
            when (op) {
                is EditorOp.DeleteSurrounding -> text = text.dropLast(op.before)
                is EditorOp.CommitText -> text += op.text
                else -> Unit
            }
        }
        return text
    }

    // ---- Double-space period (T1, T3, T5) ----

    @Test
    fun `T1 two spaces within window convert to period`() {
        val outcome = DoubleSpacePeriod.apply(enabled = true, textBeforeCursor = "hello ", isSecondPressWithinWindow = true)
        val ops = (outcome as DoubleSpacePeriodOutcome.Fires).ops
        assertEquals("hello. ", apply(ops, "hello "))
    }

    @Test
    fun `T3 double space after sentence end is blocked but still normal`() {
        val outcome = DoubleSpacePeriod.apply(enabled = true, textBeforeCursor = "hello. ", isSecondPressWithinWindow = true)
        assertEquals(DoubleSpacePeriodOutcome.BlockedBySentenceEnd, outcome)
    }

    @Test
    fun `T5 auto-space plus one typed space still converts`() {
        val outcome = DoubleSpacePeriod.apply(enabled = true, textBeforeCursor = "word ", isSecondPressWithinWindow = true)
        val ops = (outcome as DoubleSpacePeriodOutcome.Fires).ops
        assertEquals("word. ", apply(ops, "word "))
    }

    @Test
    fun `outside the window nothing fires`() {
        val outcome = DoubleSpacePeriod.apply(enabled = true, textBeforeCursor = "hello ", isSecondPressWithinWindow = false)
        assertEquals(DoubleSpacePeriodOutcome.NotDue, outcome)
    }

    // ---- Remove-before / auto-space replacement (T6-T12) ----

    @Test
    fun `T6 comma in remove-before list replaces the space`() {
        val ops = AutoSpaceReplacement.apply(pending = true, typed = ',', twoCharsBeforeCursor = "o ", removeBeforeList = ",", hasUnclosedOpeningQuote = false)
        assertEquals("hello, ", apply(ops!!, "hello "))
    }

    @Test
    fun `T7 comma not in remove-before list commits normally`() {
        val ops = AutoSpaceReplacement.apply(pending = true, typed = ',', twoCharsBeforeCursor = "o ", removeBeforeList = "", hasUnclosedOpeningQuote = false)
        assertNull(ops)
    }

    @Test
    fun `T8 default lists keep ASCII smileys intact`() {
        // Neither ':' nor ')' is in the default (empty) remove-before list.
        assertNull(AutoSpaceReplacement.apply(true, ':', "t ", "", false))
        assertNull(AutoSpaceReplacement.apply(false, ')', "t:", "", false))
    }

    @Test
    fun `T9 colon in remove-before list replaces the space, bracket after does not`() {
        val ops = AutoSpaceReplacement.apply(true, ':', "t ", ":", false)
        assertEquals("text: ", apply(ops!!, "text "))
        assertNull(AutoSpaceReplacement.apply(false, ')', "t:", ":", false))
    }

    @Test
    fun `T10 quote with no unclosed opener keeps the space and clears the flag`() {
        val ops = AutoSpaceReplacement.apply(true, '"', "t ", "\"", hasUnclosedOpeningQuote = false)
        assertNull(ops)
    }

    @Test
    fun `T11 quote with an unclosed opener replaces the space`() {
        val ops = AutoSpaceReplacement.apply(true, '"', "r ", "\"", hasUnclosedOpeningQuote = true)
        assertEquals("\"bonjour\" ", apply(ops!!, "\"bonjour "))
    }

    @Test
    fun `T12 an attached quote earlier in the line is not an opener`() {
        assertEquals(false, QuoteScan.hasUnclosedOpeningQuote("Erster Versuch\" Zweiter Versuch "))
    }

    // ---- Comma space (T13-T16) ----

    @Test
    fun `T13 comma space on plain text`() {
        assertEquals("Hi, ", apply(CommaSpace.apply("Hi"), "Hi"))
    }

    @Test
    fun `T14 comma space removes an existing trailing space`() {
        assertEquals("Hi, ", apply(CommaSpace.apply("Hi "), "Hi "))
    }

    @Test
    fun `T15 comma space cleans a space before an already-committed comma`() {
        assertEquals("Hi, ", apply(CommaSpace.apply("Hi ,"), "Hi ,"))
    }

    @Test
    fun `already-spaced comma is left unchanged`() {
        assertEquals(emptyList(), CommaSpace.apply("Hi, "))
    }

    // ---- Spaced hyphen to dash (T22-T24) ----

    @Test
    fun `T22 spaced hyphen becomes an en dash by default`() {
        val ops = SpacedHyphenDash.apply("hello -", DashStyle.EN_DASH)
        assertEquals("hello – ", apply(ops!!, "hello -"))
    }

    @Test
    fun `T23 em dash style`() {
        val ops = SpacedHyphenDash.apply("hello -", DashStyle.EM_DASH)
        assertEquals("hello — ", apply(ops!!, "hello -"))
    }

    @Test
    fun `T24 hyphen at line start is left alone`() {
        assertNull(SpacedHyphenDash.apply("  -", DashStyle.EN_DASH))
    }

    // ---- French spacing (T38, T39) ----

    @Test
    fun `T38 french spacing attaches a narrow no-break space`() {
        val ops = FrenchSpacing.apply("bonjour", '?')
        assertEquals("bonjour ?", apply(ops!!, "bonjour"))
    }

    @Test
    fun `T39 french spacing replaces an existing space`() {
        val ops = FrenchSpacing.apply("bonjour ", '?')
        assertEquals("bonjour ?", apply(ops!!, "bonjour "))
    }

    @Test
    fun `french spacing does nothing at text start`() {
        assertNull(FrenchSpacing.apply("", '?'))
    }

    // ---- Smart quotes (T31, T33, T34, T35, T36) ----

    @Test
    fun `T31 smart quotes convert to german guillemets on space`() {
        val ops = SmartQuotes.apply("\"Hallo\"", ' ', SmartQuoteStyle.GERMAN_GUILLEMETS)
        assertEquals("»Hallo« ", apply(ops!!, "\"Hallo\""))
    }

    @Test
    fun `T33 smart quotes fire on a hyphen delimiter mid-sentence`() {
        val ops = SmartQuotes.apply("Sogenannter \"Hooligang\"", '-', SmartQuoteStyle.GERMAN_GUILLEMETS)
        assertEquals("Sogenannter »Hooligang«-", apply(ops!!, "Sogenannter \"Hooligang\""))
    }

    @Test
    fun `T34 a quote attached to a word is not an opener`() {
        assertNull(SmartQuotes.apply("foo\"bar\"", ' ', SmartQuoteStyle.GERMAN_GUILLEMETS))
    }

    @Test
    fun `T35 a letter is not a delimiter`() {
        assertNull(SmartQuotes.apply("\"Hallo\"", 'a', SmartQuoteStyle.GERMAN_GUILLEMETS))
    }

    @Test
    fun `T36 typing the closing quote itself does not convert`() {
        assertNull(SmartQuotes.apply("\"Hallo", '"', SmartQuoteStyle.GERMAN_GUILLEMETS))
    }
}
