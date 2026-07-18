package it.palsoftware.pastiera.core

import android.util.Log
import android.view.inputmethod.InputConnection

/**
 * Tracks when the IME commits a trailing space automatically (e.g., after accepting
 * a suggestion or auto-replace). Used to selectively trim that space if the next
 * keypress is punctuation, without affecting user-typed spaces.
 */
object AutoSpaceTracker {
    private const val TAG = "AutoSpaceTracker"
    private const val QUOTE_CONTEXT_LOOKBACK = 240
    @Volatile
    private var autoSpacePending: Boolean = false

    fun markAutoSpace() {
        autoSpacePending = true
        Log.d(TAG, "markAutoSpace -> pending=true")
    }

    /**
     * Returns true if an auto-space was pending and clears the flag.
     */
    fun consumeAutoSpace(): Boolean {
        val had = autoSpacePending
        autoSpacePending = false
        Log.d(TAG, "consumeAutoSpace had=$had")
        return had
    }

    fun isPending(): Boolean {
        return autoSpacePending
    }

    fun clear() {
        autoSpacePending = false
        Log.d(TAG, "clear pending=false")
    }

    /**
     * If an auto-space is pending and the cursor is at "word<space>", replace the space
     * with "<punctuation> " in a single batch edit. Returns true if applied.
     */
    fun replaceAutoSpaceWithPunctuation(
        inputConnection: InputConnection,
        punctuation: String
    ): Boolean {
        if (!autoSpacePending) return false
        val beforeTwo = inputConnection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        val lastIsSpace = beforeTwo.lastOrNull() == ' '
        val prevIsWordChar = beforeTwo.dropLast(1).lastOrNull()?.isLetterOrDigit() == true
        if (!lastIsSpace || !prevIsWordChar) {
            return false
        }
        if (punctuation == "\"" && !hasUnclosedStraightDoubleQuote(inputConnection)) {
            autoSpacePending = false
            Log.d(TAG, "Keeping auto-space before opening quote")
            return false
        }
        autoSpacePending = false
        inputConnection.beginBatchEdit()
        inputConnection.deleteSurroundingText(1, 0)
        inputConnection.commitText("$punctuation ", 1)
        inputConnection.endBatchEdit()
        Log.d(TAG, "replaceAutoSpaceWithPunctuation -> '$punctuation '")
        return true
    }

    private fun hasUnclosedStraightDoubleQuote(inputConnection: InputConnection): Boolean {
        val before = inputConnection.getTextBeforeCursor(QUOTE_CONTEXT_LOOKBACK, 0)
            ?.toString()
            .orEmpty()
            .dropLast(1)
            .substringAfterLast('\n')
        var quoteOpen = false
        before.forEachIndexed { index, char ->
            if (char != '"') return@forEachIndexed
            if (quoteOpen) {
                quoteOpen = false
            } else if (isOpeningQuoteContext(before, index)) {
                quoteOpen = true
            }
        }
        return quoteOpen
    }

    private fun isOpeningQuoteContext(text: String, quoteIndex: Int): Boolean {
        if (quoteIndex == 0) return true
        val previous = text[quoteIndex - 1]
        return previous.isWhitespace() ||
            previous in setOf('(', '[', '{', '<', '«', '‹', '„', '“', '”', '»', '›', '-', '–', '—')
    }
}
