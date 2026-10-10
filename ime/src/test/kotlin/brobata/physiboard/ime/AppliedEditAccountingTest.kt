package brobata.physiboard.ime

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.text.EditorOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pure arithmetic behind two `:ime` decisions that used to be a single unbounded boolean
 * and a blanket "every key edits the field" call: where our own edit leaves the cursor (so the
 * editor's report of it is not mistaken for an external move, text-input.md SS2), and whether the
 * edit changed any text at all (dictation's c440844 invariant).
 */
class AppliedEditAccountingTest {

    @Test
    fun `a haptic-only or pass-through result changes no text`() {
        assertFalse(AppliedEditAccounting.changesText(listOf(EditorOp.Haptic)))
        assertFalse(AppliedEditAccounting.changesText(listOf(EditorOp.PassThroughKey)))
        assertFalse(AppliedEditAccounting.changesText(listOf(EditorOp.FinishComposing)))
        assertFalse(AppliedEditAccounting.changesText(emptyList()))
    }

    @Test
    fun `a commit, a delete, a replacement and a space fallback all change text`() {
        assertTrue(AppliedEditAccounting.changesText(listOf(EditorOp.CommitText("a"))))
        assertTrue(AppliedEditAccounting.changesText(listOf(EditorOp.DeleteSurrounding(1, 0))))
        assertTrue(AppliedEditAccounting.changesText(listOf(EditorOp.ReplaceBeforeCursor(3, "the"))))
        assertTrue(AppliedEditAccounting.changesText(listOf(EditorOp.Haptic, EditorOp.SendSpaceKeyFallback)))
    }

    @Test
    fun `a plain commit moves the cursor forward by the committed length`() {
        assertEquals(12, AppliedEditAccounting.expectedCursorAfter(10, 0, listOf(EditorOp.CommitText("ab"))))
    }

    @Test
    fun `a backspace moves the cursor back and never below zero`() {
        assertEquals(9, AppliedEditAccounting.expectedCursorAfter(10, 0, listOf(EditorOp.DeleteSurrounding(1, 0))))
        assertEquals(0, AppliedEditAccounting.expectedCursorAfter(0, 0, listOf(EditorOp.DeleteSurrounding(1, 0))))
    }

    @Test
    fun `an autocorrect replacement lands the cursor after the new word plus the space`() {
        // "teh" + Space: the word is replaced over a composing region, then the space commits.
        val ops = listOf(
            EditorOp.FinishComposing,
            EditorOp.SetComposingRegion(charsBeforeCursor = 3, length = 3),
            EditorOp.CommitText("the"),
            EditorOp.FinishComposing,
            EditorOp.CommitText(" "),
            EditorOp.Haptic,
        )
        assertEquals(14, AppliedEditAccounting.expectedCursorAfter(13, 0, ops))
    }

    @Test
    fun `a composing-region replacement of a longer word shrinks the cursor position`() {
        val ops = listOf(EditorOp.SetComposingRegion(5, 5), EditorOp.CommitText("hi"), EditorOp.FinishComposing)
        assertEquals(7, AppliedEditAccounting.expectedCursorAfter(10, 0, ops))
    }

    @Test
    fun `a set-selection op is translated through the window start`() {
        assertEquals(107, AppliedEditAccounting.expectedCursorAfter(10, 100, listOf(EditorOp.SetSelection(7, 9))))
    }

    @Test
    fun `the editor reporting exactly the expected cursor is our own edit`() {
        val expectation = OwnEditExpectation(selStart = 12, expiresAtMs = 1300)
        assertEquals(OwnEditExpectation.Verdict.OWN_EDIT, expectation.classify(newSelStart = 12, nowMs = 1010))
        assertEquals(OwnEditExpectation.Verdict.OWN_EDIT, expectation.classify(newSelStart = 12, nowMs = 5000), "a late but exact report is still ours")
    }

    @Test
    fun `a different cursor inside the window is the batch still settling, after it is external`() {
        val expectation = OwnEditExpectation(selStart = 12, expiresAtMs = 1300)
        assertEquals(OwnEditExpectation.Verdict.STILL_SETTLING, expectation.classify(newSelStart = 11, nowMs = 1010))
        assertEquals(OwnEditExpectation.Verdict.EXTERNAL, expectation.classify(newSelStart = 40, nowMs = 1300))
    }

    @Test
    fun `a second cursor report inside the settle window after the app already agreed is noise, not an external move`() {
        // Titan 2026-09-25, web chat field: "0->1" (agreed) then "1->0" ten milliseconds later.
        val expectation = OwnEditExpectation(selStart = 1, expiresAtMs = 1_300L)
        assertEquals(OwnEditExpectation.Verdict.OWN_EDIT, expectation.classify(1, 1_010L))
        val matched = expectation.copy(matched = true)
        assertEquals(OwnEditExpectation.Verdict.STILL_SETTLING, matched.classify(0, 1_020L))
        assertEquals(OwnEditExpectation.Verdict.EXTERNAL, matched.classify(0, 1_400L))
    }

    @Test
    fun `a passed-through Backspace is expected to land one before the cursor, forward delete in place, anything else unknown`() {
        assertEquals(4, AppliedEditAccounting.expectedCursorAfterPassThrough(KeyId.Control(ControlKey.BACKSPACE), 5, hasSelection = false))
        assertEquals(0, AppliedEditAccounting.expectedCursorAfterPassThrough(KeyId.Control(ControlKey.BACKSPACE), 0, hasSelection = false))
        assertEquals(5, AppliedEditAccounting.expectedCursorAfterPassThrough(KeyId.Control(ControlKey.FORWARD_DELETE), 5, hasSelection = false))
        assertEquals(null, AppliedEditAccounting.expectedCursorAfterPassThrough(KeyId.Control(ControlKey.BACKSPACE), 5, hasSelection = true))
        assertEquals(null, AppliedEditAccounting.expectedCursorAfterPassThrough(KeyId.Letter('X'), 5, hasSelection = false))
        // Ctrl or Alt held: the app deletes a word or a line, so where the cursor lands is its business.
        assertEquals(null, AppliedEditAccounting.expectedCursorAfterPassThrough(KeyId.Control(ControlKey.BACKSPACE), 5, hasSelection = false, ctrlOrAltHeld = true))
    }
}
