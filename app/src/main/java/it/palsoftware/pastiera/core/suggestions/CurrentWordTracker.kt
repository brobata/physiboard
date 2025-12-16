package it.palsoftware.pastiera.core.suggestions

import android.util.Log
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker
import java.io.File
import org.json.JSONObject

class CurrentWordTracker(
    private val onWordChanged: (String) -> Unit,
    private val onWordReset: () -> Unit,
    private val maxLength: Int = 48
) {

    private val current = StringBuilder()
    private val tag = "CurrentWordTracker"
    private val debugLogging = false
    
    // #region agent log
    private fun debugLog(hypothesisId: String, location: String, message: String, data: Map<String, Any?> = emptyMap()) {
        try {
            val logFile = File("/Users/andrea/Desktop/DEV/Pastiera/pastiera/.cursor/debug.log")
            val logEntry = JSONObject().apply {
                put("sessionId", "debug-session")
                put("runId", "run1")
                put("hypothesisId", hypothesisId)
                put("location", location)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                put("data", JSONObject(data))
            }
            logFile.appendText(logEntry.toString() + "\n")
        } catch (e: Exception) {
            // Ignore log errors
        }
    }
    // #endregion

    val currentWord: String
        get() = current.toString()

    fun setWord(word: String) {
        // #region agent log
        val wordBefore = current.toString()
        debugLog("D", "CurrentWordTracker.setWord:entry", "setWord called", mapOf(
            "wordBefore" to wordBefore,
            "wordBeforeLength" to wordBefore.length,
            "newWord" to word,
            "newWordLength" to word.length
        ))
        // #endregion
        current.clear()
        if (word.length <= maxLength) {
            current.append(word)
        } else {
            current.append(word.takeLast(maxLength))
        }
        if (debugLogging) Log.d(tag, "setWord currentWord='$current'")
        onWordChanged(current.toString())
        // #region agent log
        val wordAfter = current.toString()
        debugLog("D", "CurrentWordTracker.setWord:exit", "setWord completed", mapOf(
            "wordAfter" to wordAfter,
            "wordAfterLength" to wordAfter.length
        ))
        // #endregion
    }

    fun onCharacterCommitted(text: CharSequence) {
        if (text.isEmpty()) return
        // #region agent log
        val wordBefore = current.toString()
        debugLog("A", "CurrentWordTracker.onCharacterCommitted:entry", "onCharacterCommitted called", mapOf(
            "text" to text.toString(),
            "wordBefore" to wordBefore,
            "wordBeforeLength" to wordBefore.length
        ))
        // #endregion
        text.forEach { char ->
            // Special case: "\bX" pattern means "replace last with X" (used by multi-tap).
            if (char == '\b') {
                onBackspace()
                return@forEach
            }
            val normalizedChar = normalizeApostrophe(char)
            val isWordChar = normalizedChar.isLetterOrDigit() ||
                (normalizedChar == '\'' && current.isNotEmpty() && current.last().isLetterOrDigit())
            if (isWordChar) {
                if (current.length < maxLength) {
                    current.append(normalizedChar)
                    if (debugLogging) Log.d(tag, "currentWord='$current'")
                    onWordChanged(current.toString())
                    // #region agent log
                    val wordAfter = current.toString()
                    debugLog("A", "CurrentWordTracker.onCharacterCommitted:charAdded", "character added to tracker", mapOf(
                        "char" to normalizedChar.toString(),
                        "wordAfter" to wordAfter,
                        "wordAfterLength" to wordAfter.length
                    ))
                    // #endregion
                }
            } else {
                if (debugLogging) Log.d(tag, "reset on non-letter char='$char'")
                reset()
            }
        }
    }

    private fun normalizeApostrophe(c: Char): Char = when (c) {
        '’', '‘', 'ʼ' -> '\''
        else -> c
    }

    fun onBackspace() {
        if (current.isNotEmpty()) {
            current.deleteCharAt(current.length - 1)
            if (current.isNotEmpty()) {
                if (debugLogging) Log.d(tag, "currentWord after backspace='$current'")
                onWordChanged(current.toString())
            } else {
                reset()
            }
        }
    }

    fun onBoundaryReached(boundaryChar: Char? = null, inputConnection: InputConnection? = null) {
        if (boundaryChar != null) {
            // If an auto-space is pending, replace it with "<punctuation> " when punctuation is pressed.
            val punctuationSet = it.palsoftware.pastiera.core.Punctuation.AUTO_SPACE
            if (inputConnection != null && boundaryChar in punctuationSet) {
                val replaced = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(
                    inputConnection,
                    boundaryChar.toString()
                )
                if (replaced) {
                    reset()
                    return
                }
            }
            inputConnection?.commitText(boundaryChar.toString(), 1)
        }
        reset()
    }

    fun reset() {
        if (current.isNotEmpty()) {
            if (debugLogging) Log.d(tag, "reset currentWord='$current'")
            current.clear()
            onWordReset()
        }
    }

    fun onCursorMoved() {
        reset()
    }

    fun onContextChanged() {
        reset()
    }
}
