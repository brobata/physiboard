package it.palsoftware.pastiera

/**
 * Configuration for SYM pages order and visibility.
 */
data class SymPagesConfig(
    val emojiEnabled: Boolean = true,
    val symbolsEnabled: Boolean = true,
    val clipboardEnabled: Boolean = false,
    val emojiPickerEnabled: Boolean = false,
    val emojiFirst: Boolean = true
)
