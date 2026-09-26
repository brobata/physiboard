package brobata.physiboard.core.pointer.keyboardswipe

/**
 * The three equal-width strip slots a swipe's third can land on, in the same left/centre/right
 * order the status bar document draws them. This module cannot depend on `:core:strip` (a
 * `:core:*` module never depends on another feature's `:core:*` module), so `:ime` maps this back
 * onto `core.strip.SlotPosition` itself; the two enums share the same three names on purpose.
 */
enum class SwipeSlot { LEFT, CENTRE, RIGHT }

/** spec SS3.5 point 2: everything the gesture needs to know about the current field and strip. */
data class SwipeUpGate(
    val symPageIsZero: Boolean,
    val suggestionsEnabled: Boolean,
    val fieldAllowsSuggestions: Boolean,
    /** Whether the strip currently shows at least one real suggestion slot. */
    val suggestionVisible: Boolean,
    /** Whether an add-word candidate is pending for the current word (autocorrect-suggestions.md). */
    val addWordCandidatePending: Boolean,
)

/** What an accepted up-swipe should do. spec SS3.5 points 3 to 5. */
sealed class SwipeUpAction {
    /** spec SS3.5 point 2: the gate failed, or the third's slot has nothing in it. */
    data object Ignored : SwipeUpAction()

    /** spec SS3.5 point 4. */
    data object AddWord : SwipeUpAction()

    /** spec SS3.5 point 5: commit the suggestion at this strip slot. */
    data class AcceptSlot(val slot: SwipeSlot) : SwipeUpAction()
}

/**
 * spec: trackpad-caret-nav.md SS3.5, points 2 to 5: the pure decision an accepted up-swipe (SS3.3)
 * feeds into. `:ime` supplies [SwipeUpGate] from the live strip model and applies the result by
 * calling the same commit path a real slot tap uses (status-bar.md SS5.3: "committed through the
 * same path").
 */
object KeyboardSwipeUpDecision {

    fun decide(third: SwipeThird, gate: SwipeUpGate, settings: KeyboardSwipeSettings): SwipeUpAction {
        val slot = when (third) {
            SwipeThird.LEFT -> SwipeSlot.LEFT
            SwipeThird.CENTRE -> SwipeSlot.CENTRE
            SwipeThird.RIGHT -> SwipeSlot.RIGHT
        }
        // spec SS3.5 point 3: "a swipe in the left third always adds the word; a swipe in the
        // centre or right third adds it only when [full width] is true and no suggestions are
        // visible." A visible suggestion row means this is not the add-word-only row, so the
        // "no suggestions are visible" clause is folded into [SwipeUpGate.suggestionVisible].
        val addWordAllowed = settings.addWordEnabled && gate.addWordCandidatePending &&
            (slot == SwipeSlot.LEFT || (settings.addWordFullWidthEnabled && !gate.suggestionVisible))

        // spec SS3.5 point 2: "allowed only when the Sym page is 0, suggestions are enabled, smart
        // features are not disabled for this field, and either at least one suggestion is visible
        // or the add-word rule... allows adding. Otherwise it is ignored."
        val gateOpen = gate.symPageIsZero && gate.suggestionsEnabled && gate.fieldAllowsSuggestions &&
            (gate.suggestionVisible || addWordAllowed)
        if (!gateOpen) return SwipeUpAction.Ignored

        if (addWordAllowed) return SwipeUpAction.AddWord
        return SwipeUpAction.AcceptSlot(slot)
    }
}
