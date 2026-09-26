package brobata.physiboard.core.pointer.keyboardswipe

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: trackpad-caret-nav.md SS3.5, points 2 to 5. */
class KeyboardSwipeUpDecisionTest {

    private val settings = KeyboardSwipeSettings()
    private val openGate = SwipeUpGate(
        symPageIsZero = true,
        suggestionsEnabled = true,
        fieldAllowsSuggestions = true,
        suggestionVisible = true,
        addWordCandidatePending = false,
    )

    @Test
    fun `sym page open ignores every third`() {
        val gate = openGate.copy(symPageIsZero = false)
        assertEquals(SwipeUpAction.Ignored, KeyboardSwipeUpDecision.decide(SwipeThird.CENTRE, gate, settings))
    }

    @Test
    fun `suggestions disabled ignores every third`() {
        val gate = openGate.copy(suggestionsEnabled = false)
        assertEquals(SwipeUpAction.Ignored, KeyboardSwipeUpDecision.decide(SwipeThird.CENTRE, gate, settings))
    }

    @Test
    fun `field disallows suggestions ignores every third`() {
        val gate = openGate.copy(fieldAllowsSuggestions = false)
        assertEquals(SwipeUpAction.Ignored, KeyboardSwipeUpDecision.decide(SwipeThird.CENTRE, gate, settings))
    }

    @Test
    fun `no suggestion visible and no add-word candidate ignores every third`() {
        val gate = openGate.copy(suggestionVisible = false, addWordCandidatePending = false)
        assertEquals(SwipeUpAction.Ignored, KeyboardSwipeUpDecision.decide(SwipeThird.CENTRE, gate, settings))
    }

    @Test
    fun `with suggestions visible each third accepts its own slot`() {
        assertEquals(SwipeUpAction.AcceptSlot(SwipeSlot.CENTRE), KeyboardSwipeUpDecision.decide(SwipeThird.CENTRE, openGate, settings))
        assertEquals(SwipeUpAction.AcceptSlot(SwipeSlot.RIGHT), KeyboardSwipeUpDecision.decide(SwipeThird.RIGHT, openGate, settings))
        assertEquals(SwipeUpAction.AcceptSlot(SwipeSlot.LEFT), KeyboardSwipeUpDecision.decide(SwipeThird.LEFT, openGate, settings))
    }

    @Test
    fun `left third always adds the pending word regardless of other suggestions`() {
        val gate = openGate.copy(addWordCandidatePending = true)
        assertEquals(SwipeUpAction.AddWord, KeyboardSwipeUpDecision.decide(SwipeThird.LEFT, gate, settings))
    }

    @Test
    fun `centre or right third adds the word only with full width enabled and nothing else visible`() {
        val gate = SwipeUpGate(
            symPageIsZero = true, suggestionsEnabled = true, fieldAllowsSuggestions = true,
            suggestionVisible = false, addWordCandidatePending = true,
        )
        assertEquals(SwipeUpAction.AddWord, KeyboardSwipeUpDecision.decide(SwipeThird.CENTRE, gate, settings))
        assertEquals(
            SwipeUpAction.Ignored,
            KeyboardSwipeUpDecision.decide(SwipeThird.CENTRE, gate, settings.copy(addWordFullWidthEnabled = false)),
        )
    }

    @Test
    fun `centre or right third never adds when a real suggestion is also visible`() {
        val gate = openGate.copy(addWordCandidatePending = true, suggestionVisible = true)
        assertEquals(SwipeUpAction.AcceptSlot(SwipeSlot.CENTRE), KeyboardSwipeUpDecision.decide(SwipeThird.CENTRE, gate, settings))
    }

    @Test
    fun `add word disabled never fires even from the left third`() {
        val gate = SwipeUpGate(
            symPageIsZero = true, suggestionsEnabled = true, fieldAllowsSuggestions = true,
            suggestionVisible = false, addWordCandidatePending = true,
        )
        assertEquals(SwipeUpAction.Ignored, KeyboardSwipeUpDecision.decide(SwipeThird.LEFT, gate, settings.copy(addWordEnabled = false)))
    }
}
