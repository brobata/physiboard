package it.palsoftware.pastiera.core

import android.content.Context
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager

/**
 * Defers spaces after configured punctuation until more text is actually typed.
 * This avoids trailing whitespace when punctuation ends a message.
 */
object DeferredPunctuationSpaceTracker {
    private const val NO_SPACE_BEFORE: String = ".,;:!?/\\)]}»›"

    @Volatile
    private var pending: Boolean = false

    fun prepareForTextCommit(
        context: Context,
        inputConnection: InputConnection,
        text: CharSequence
    ): Boolean {
        val first = text.firstOrNull() ?: return false
        if (first.isWhitespace()) {
            pending = false
            return false
        }

        val hadPending = pending
        var insertedSpace = false
        if (hadPending && first !in NO_SPACE_BEFORE) {
            inputConnection.commitText(" ", 1)
            pending = false
            insertedSpace = true
        }

        val configured = SettingsManager.getSpaceAfterPunctuation(context)
        pending = when {
            first in configured -> true
            hadPending && first in NO_SPACE_BEFORE -> true
            else -> false
        }
        return insertedSpace
    }

    fun clear() {
        pending = false
    }

    fun onTextCommitted(context: Context, text: CharSequence) {
        val first = text.firstOrNull() ?: return
        if (first in SettingsManager.getSpaceAfterPunctuation(context)) {
            pending = true
        }
    }

    internal fun isPending(): Boolean = pending
}
