package brobata.physiboard.core.suggestions

data class SuggestionSettings(
    val textReplacementsEnabled: Boolean = true,
    val suggestionsEnabled: Boolean = true,
    val accentMatching: Boolean = true,
    val autoReplaceOnSpaceEnter: Boolean = false,
    val maxAutoReplaceDistance: Int = 1,
    /**
     * How clearly a candidate must beat the runner-up before it is applied rather than merely
     * offered. 0.0 is the old behaviour: anything the shape gates permitted was committed,
     * however close the call.
     *
     * 0.10 is measured, not guessed. Against the shipped dictionary it takes real words
     * overruled from 4 to 1 and the false-correction rate from 0.041 to 0.014, for 0.05 of
     * recall - the best trade anywhere on the curve. Past 0.20 recall falls off a cliff
     * (0.725 -> 0.400) while buying nothing further.
     *
     * See docs/plans/autocorrect-rework.md for the full sweep, and
     * [AutoReplaceController.Confidence] for what the number measures.
     */
    val minAutoReplaceConfidence: Double = 0.10,
    val maxSuggestions: Int = 3,
    val useKeyboardProximity: Boolean = false,
    val useEditTypeRanking: Boolean = false,
    val frenchPunctuationSpacing: Boolean = false,
    val commaSpace: Boolean = false,
    val autoSpacePunctuation: String = brobata.physiboard.core.Punctuation.DEFAULT_AUTO_SPACE
)
