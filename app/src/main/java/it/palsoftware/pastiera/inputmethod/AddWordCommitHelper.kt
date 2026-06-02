package it.palsoftware.pastiera.inputmethod

import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker
import it.palsoftware.pastiera.core.Punctuation

object AddWordCommitHelper {
    fun commitAutoSpaceAfterAddWord(inputConnection: InputConnection) {
        val next = inputConnection.getTextAfterCursor(1, 0)?.firstOrNull()
        if (next?.let { it.isWhitespace() || Punctuation.isWordBoundary(it, null, null) } == true) {
            AutoSpaceTracker.clear()
            return
        }
        inputConnection.commitText(" ", 1)
        AutoSpaceTracker.markAutoSpace()
    }
}
