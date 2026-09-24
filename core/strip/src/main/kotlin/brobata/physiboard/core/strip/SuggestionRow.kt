package brobata.physiboard.core.strip

/** The three equal-width boxes of the suggestion row. spec: status-bar.md SS1, "Slot: ... Left, center, right." */
enum class SlotPosition { LEFT, CENTER, RIGHT }

/** What a slot's text means when tapped. spec: status-bar.md SS5.3 (suggestion versus add-word) and SS5.2 (expansion). */
enum class SlotKind { EMPTY, SUGGESTION, ADD_WORD, EXPANSION }

/**
 * One slot's content. spec SS5.1: "Empty slots are still drawn, identical in color to full
 * ones... they are not clickable", hence [EMPTY] is a real value rather than a missing slot.
 */
data class Slot(val text: String, val kind: SlotKind) {
    val isTappable: Boolean get() = kind != SlotKind.EMPTY

    companion object {
        val EMPTY: Slot = Slot("", SlotKind.EMPTY)
    }
}

/**
 * The suggestion row as the strip draws it. spec: status-bar.md SS5.1 (three slots or one
 * full-width add-word slot) and SS5.2 (the row removed, buttons remaining).
 */
sealed class SuggestionRow {
    /** spec SS5.2: "The slots are removed (the frame stays, so the buttons remain)". */
    data object Hidden : SuggestionRow()

    data class Slots(val left: Slot, val center: Slot, val right: Slot) : SuggestionRow() {
        operator fun get(position: SlotPosition): Slot = when (position) {
            SlotPosition.LEFT -> left
            SlotPosition.CENTER -> center
            SlotPosition.RIGHT -> right
        }

        val texts: List<String> get() = listOf(left.text, center.text, right.text)
    }

    /** spec SS5.1: "When the add-word candidate is the only content... a single slot with three times the weight spans the whole row". */
    data class AddWordOnly(val word: String) : SuggestionRow()
}

/** spec: status-bar.md SS5.3, action mode's "one or two icon buttons". */
enum class SlotActionButton { HIDE_SUGGESTION, DELETE_FROM_PERSONAL_DICTIONARY }

/**
 * The pure rules of the suggestion row: which text lands in which slot, when the row is hidden,
 * which slot a flash or a trackpad swipe refers to. spec: status-bar.md SS5.
 */
object SuggestionRowRules {
    /** spec SS14: "Slot flash 160 ms"; SS5.3, "the slot flashes (pressed color for 160 ms)". */
    const val FLASH_MS: Long = 160

    /** spec SS14: "Maximum suggestions in the row 3" and "Expansion suggestions kept 3". */
    const val MAX_SUGGESTIONS: Int = 3

    /**
     * spec SS5.1's slot table. Left: the add-word candidate unless it already appears among the
     * suggestions case-insensitively (T20), else the third suggestion; center: the first; right:
     * the second only when there are at least two. With no suggestions and an add-word candidate,
     * [SuggestionRow.AddWordOnly] (T21).
     */
    fun map(suggestions: List<String>, addWordCandidate: String?): SuggestionRow {
        val ranked = suggestions.take(MAX_SUGGESTIONS)
        val addWord = addWordCandidate?.takeIf { candidate ->
            candidate.isNotEmpty() && ranked.none { it.equals(candidate, ignoreCase = true) }
        }
        if (ranked.isEmpty() && addWord != null) return SuggestionRow.AddWordOnly(addWord)
        val left = when {
            addWord != null -> Slot(addWord, SlotKind.ADD_WORD)
            ranked.size >= 3 -> Slot(ranked[2], SlotKind.SUGGESTION)
            else -> Slot.EMPTY
        }
        val center = ranked.getOrNull(0)?.let { Slot(it, SlotKind.SUGGESTION) } ?: Slot.EMPTY
        val right = ranked.getOrNull(1)?.let { Slot(it, SlotKind.SUGGESTION) } ?: Slot.EMPTY
        return SuggestionRow.Slots(left, center, right)
    }

    /** spec SS5.2: expansion suggestions take "the same three slots (left = third, center = first, right = second), with no add-word candidate". */
    fun mapExpansion(expansions: List<String>): SuggestionRow {
        val kept = expansions.take(MAX_SUGGESTIONS)
        fun slot(index: Int): Slot = kept.getOrNull(index)?.let { Slot(it, SlotKind.EXPANSION) } ?: Slot.EMPTY
        return SuggestionRow.Slots(left = slot(2), center = slot(0), right = slot(1))
    }

    /**
     * spec SS5.2's hidden-row list, reduced to what 3.0 has (no on-screen keyboard, so every
     * field is "in hardware mode"): suggestions off, a field that disables them, a Sym page open,
     * the clipboard overlay open, or no dictionary for the language. Expansion suggestions win
     * over everything ("they show even in a restricted field and without a dictionary").
     */
    fun rowVisible(
        suggestionsEnabled: Boolean,
        fieldAllowsSuggestions: Boolean,
        symPageOpen: Boolean,
        clipboardOverlayOpen: Boolean,
        dictionaryInstalled: Boolean,
        expansionActive: Boolean,
    ): Boolean {
        if (expansionActive) return true
        return suggestionsEnabled && fieldAllowsSuggestions && !symPageOpen && !clipboardOverlayOpen && dictionaryInstalled
    }

    /** spec SS5.4 and T22: suggestion index 0 flashes the center slot, 1 the right, 2 the left; anything else nothing. */
    fun flashSlotFor(suggestionIndex: Int): SlotPosition? = when (suggestionIndex) {
        0 -> SlotPosition.CENTER
        1 -> SlotPosition.RIGHT
        2 -> SlotPosition.LEFT
        else -> null
    }

    /** spec SS5.4 and T23: "the swipe's left third maps to the third suggestion, center third to the first, right third to the second". */
    fun trackpadThirdToSuggestionIndex(third: Int): Int? = when (third) {
        0 -> 2
        1 -> 0
        2 -> 1
        else -> null
    }

    /**
     * spec SS5.3, long press on a suggestion: "an 'eye' (hide this suggestion) always, and a
     * 'trash' (delete from the personal dictionary) only when the word is in the personal
     * dictionary". Only a [SlotKind.SUGGESTION] has an action mode (SS17: expansion and empty
     * slots do nothing on long press).
     */
    fun actionModeButtons(slot: Slot, wordInPersonalDictionary: Boolean): List<SlotActionButton> {
        if (slot.kind != SlotKind.SUGGESTION) return emptyList()
        return if (wordInPersonalDictionary) {
            listOf(SlotActionButton.HIDE_SUGGESTION, SlotActionButton.DELETE_FROM_PERSONAL_DICTIONARY)
        } else {
            listOf(SlotActionButton.HIDE_SUGGESTION)
        }
    }
}
