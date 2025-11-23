package it.palsoftware.pastiera.inputmethod

import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection
import java.util.Locale

/**
 * Handles clicks on variation buttons.
 */
object VariationButtonHandler {
    private const val TAG = "VariationButtonHandler"
    
    /**
     * Callback called when a variation is selected.
     */
    interface OnVariationSelectedListener {
        /**
         * Called when a variation is selected.
         * @param variation The selected variation character
         */
        fun onVariationSelected(variation: String)
    }
    
    /**
     * Creates a listener for a variation button.
     * When clicked, replaces the current word (not just one char) with the variation,
     * preserving basic casing (all caps or leading capital).
     */
    fun createVariationClickListener(
        variation: String,
        inputConnection: InputConnection?,
        listener: OnVariationSelectedListener? = null
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click on variation button: $variation")
            
            if (inputConnection == null) {
                Log.w(TAG, "No inputConnection available to insert variation")
                return@OnClickListener
            }
            
            replaceCurrentWord(inputConnection, variation)
            
            // Notify listener if present
            listener?.onVariationSelected(variation)
        }
    }

    /**
     * Creates a listener for a static variation button.
     * When clicked, inserts the variation without deleting the character before the cursor.
     */
    fun createStaticVariationClickListener(
        variation: String,
        inputConnection: InputConnection?,
        listener: OnVariationSelectedListener? = null
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click on static variation button: $variation")

            if (inputConnection == null) {
                Log.w(TAG, "No inputConnection available to insert static variation")
                return@OnClickListener
            }

            // Insert variation without deleting previous character
            inputConnection.commitText(variation, 1)
            Log.d(TAG, "Static variation '$variation' inserted")

            // Notify listener if present
            listener?.onVariationSelected(variation)
        }
    }

    /**
     * Replace the word immediately before the cursor with the given variation.
     * Deletes up to the nearest whitespace/punctuation boundary and applies basic casing
     * from the original word to the variation.
     */
    private fun replaceCurrentWord(inputConnection: InputConnection, variation: String) {
        val before = inputConnection.getTextBeforeCursor(64, 0) ?: ""
        if (before.isEmpty()) {
            inputConnection.commitText(variation, 1)
            Log.d(TAG, "Variation '$variation' inserted (no text before cursor)")
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

        val replacement = applyCasing(variation, currentWord)

        val deleted = inputConnection.deleteSurroundingText(deleteCount, 0)
        if (deleted) {
            Log.d(TAG, "Deleted $deleteCount chars ('$currentWord') before inserting variation")
        } else {
            Log.w(TAG, "Unable to delete $deleteCount chars before cursor; inserting anyway")
        }

        inputConnection.commitText(replacement, 1)
        Log.d(TAG, "Variation inserted as '$replacement'")
    }

    private fun applyCasing(candidate: String, original: String): String {
        if (original.isEmpty()) return candidate
        return when {
            original.all { it.isUpperCase() } -> candidate.uppercase(Locale.getDefault())
            original.first().isUpperCase() && original.drop(1).all { it.isLowerCase() } ->
                candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            else -> candidate
        }
    }
}
