package it.palsoftware.pastiera.inputmethod

import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection
import java.util.Locale

/**
 * Handles clicks on suggestion buttons (full word replacements).
 */
object SuggestionButtonHandler {
    private const val TAG = "SuggestionButtonHandler"

    fun createSuggestionClickListener(
        suggestion: String,
        inputConnection: InputConnection?,
        listener: VariationButtonHandler.OnVariationSelectedListener? = null
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click on suggestion button: $suggestion")

            if (inputConnection == null) {
                Log.w(TAG, "No inputConnection available to insert suggestion")
                return@OnClickListener
            }

            replaceCurrentWord(inputConnection, suggestion)
            listener?.onVariationSelected(suggestion)
        }
    }

    /**
     * Replace the word immediately before the cursor with the given suggestion.
     * Deletes up to the nearest whitespace/punctuation boundary and applies basic casing
     * (leading capital only). All-caps input (e.g., CapsLock) will not force the suggestion to uppercase.
     */
    private fun replaceCurrentWord(inputConnection: InputConnection, suggestion: String) {
        val before = inputConnection.getTextBeforeCursor(64, 0) ?: ""
        if (before.isEmpty()) {
            inputConnection.commitText("$suggestion ", 1)
            Log.d(TAG, "Suggestion '$suggestion' inserted (no text before cursor)")
            return
        }

        // Find word start (stop at whitespace or punctuation)
        val boundaryChars = " \t\n\r.,;:!?()[]{}\"'"
        var start = before.length
        while (start > 0 && !boundaryChars.contains(before[start - 1])) {
            start--
        }
        val currentWord = before.substring(start)
        val deleteCount = currentWord.length

        val replacement = applyCasing(suggestion, currentWord)

        val deleted = inputConnection.deleteSurroundingText(deleteCount, 0)
        if (deleted) {
            Log.d(TAG, "Deleted $deleteCount chars ('$currentWord') before inserting suggestion")
        } else {
            Log.w(TAG, "Unable to delete $deleteCount chars before cursor; inserting anyway")
        }

        inputConnection.commitText("$replacement ", 1)
        Log.d(TAG, "Suggestion inserted as '$replacement '")
    }

    private fun applyCasing(candidate: String, original: String): String {
        if (original.isEmpty()) return candidate
        return when {
            original.first().isUpperCase() && original.drop(1).all { it.isLowerCase() } ->
                candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            else -> candidate
        }
    }
}
