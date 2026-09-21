package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: text-input.md SS6.6, "Test cases" T17-T21. */
class DeferredSpaceTest {

    @Test
    fun `T17 punctuation in the list opens a debt`() {
        val debt = DeferredSpace.onPunctuationInList()
        assertEquals(true, debt.owed)
    }

    @Test
    fun `T18 a character in the no-space-before set keeps the debt`() {
        val debt = DeferredSpace.onPunctuationInList()
        val outcome = DeferredSpace.onNextCommit(debt, "W")
        assertEquals(DeferredSpaceOutcome.InsertSpaceBefore("W"), outcome)
    }

    @Test
    fun `T19 chained boundary punctuation keeps the debt until a letter arrives`() {
        var debt = DeferredSpace.onPunctuationInList()
        val afterBang = DeferredSpace.onNextCommit(debt, "!")
        assertEquals(DeferredSpaceOutcome.Kept(debt), afterBang)
        debt = DeferredSpace.onPunctuationInList() // "!" is itself in the list, so a new debt opens
        val afterW = DeferredSpace.onNextCommit(debt, "W")
        assertEquals(DeferredSpaceOutcome.InsertSpaceBefore("W"), afterW)
    }

    @Test
    fun `T20 whitespace cancels the debt without inserting anything`() {
        val debt = DeferredSpace.onPunctuationInList()
        assertEquals(DeferredSpaceOutcome.Cancelled, DeferredSpace.onNextCommit(debt, " "))
    }

    @Test
    fun `T21 enter cancels the debt`() {
        assertEquals(DeferredSpaceDebt.none(), DeferredSpace.cancelled())
    }

    @Test
    fun `no debt leaves ordinary commits unaffected`() {
        val outcome = DeferredSpace.onNextCommit(DeferredSpaceDebt.none(), "x")
        assertEquals(DeferredSpaceOutcome.Unaffected(DeferredSpaceDebt.none()), outcome)
    }
}
