package it.palsoftware.pastiera.core.suggestions

data class SuggestionSettings(
    val suggestionsEnabled: Boolean = true,
    val accentMatching: Boolean = true,
    val autoReplaceOnSpaceEnter: Boolean = false,
    val maxAutoReplaceDistance: Int = 1,
    val maxSuggestions: Int = 3,
    val useKeyboardProximity: Boolean = false,
    val useEditTypeRanking: Boolean = false,
    val frenchPunctuationSpacing: Boolean = false,
    val commaSpace: Boolean = false,
    val autoSpacePunctuation: String = it.palsoftware.pastiera.core.Punctuation.DEFAULT_AUTO_SPACE
)
