package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

class SuggestionController(
    context: Context,
    assets: AssetManager,
    private val settingsProvider: () -> SuggestionSettings,
    private val isEnabled: () -> Boolean = { true },
    debugLogging: Boolean = false,
    onSuggestionsUpdated: (List<SuggestionResult>) -> Unit
) {

    private val appContext = context.applicationContext
    private val debugLogging: Boolean = debugLogging
    private val userDictionaryStore = UserDictionaryStore()
    private val dictionaryRepository = DictionaryRepository(appContext, assets, userDictionaryStore, debugLogging = debugLogging)
    private val suggestionEngine = SuggestionEngine(dictionaryRepository, debugLogging = debugLogging)
    private val tracker = CurrentWordTracker(
        onWordChanged = { word ->
            val settings = settingsProvider()
            if (settings.suggestionsEnabled) {
                onSuggestionsUpdated(suggestionEngine.suggest(word, settings.maxSuggestions, settings.accentMatching))
            }
        },
        onWordReset = { onSuggestionsUpdated(emptyList()) }
    )
    private val autoReplaceController = AutoReplaceController(dictionaryRepository, suggestionEngine, settingsProvider)
    private val latestSuggestions: AtomicReference<List<SuggestionResult>> = AtomicReference(emptyList())

    var suggestionsListener: ((List<SuggestionResult>) -> Unit)? = onSuggestionsUpdated

    fun onCharacterCommitted(text: CharSequence, inputConnection: InputConnection?) {
        if (!isEnabled()) return
        if (debugLogging) Log.d("PastieraIME", "SuggestionController.onCharacterCommitted('$text')")
        rebuildFromContext(inputConnection, fallback = { tracker.onCharacterCommitted(text) })
        updateSuggestions()
    }

    /**
     * Rebuild the current word from the text field (used on backspace or cursor edits).
     */
    fun refreshFromInputConnection(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        rebuildFromContext(inputConnection, fallback = { tracker.reset() })
        updateSuggestions()
    }

    private fun rebuildFromContext(
        inputConnection: InputConnection?,
        fallback: () -> Unit
    ) {
        val contextWord = extractWordAtCursor(inputConnection)
        if (!contextWord.isNullOrBlank()) {
            tracker.setWord(contextWord)
        } else {
            fallback()
        }
    }

    private fun updateSuggestions() {
        val settings = settingsProvider()
        if (settings.suggestionsEnabled) {
            val next = suggestionEngine.suggest(tracker.currentWord, settings.maxSuggestions, settings.accentMatching)
            val summary = next.take(3).joinToString { "${it.candidate}:${it.distance}" }
            if (debugLogging) Log.d("PastieraIME", "suggestions (${next.size}): $summary")
            latestSuggestions.set(next)
            suggestionsListener?.invoke(next)
        } else {
            suggestionsListener?.invoke(emptyList())
        }
    }

    fun onBoundaryKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?
    ): AutoReplaceController.ReplaceResult {
        if (debugLogging) {
            Log.d(
                "PastieraIME",
                "SuggestionController.onBoundaryKey keyCode=$keyCode char=${event?.unicodeChar}"
            )
        }
        val result = autoReplaceController.handleBoundary(keyCode, event, tracker, inputConnection)
        if (result.replaced) {
            dictionaryRepository.refreshUserEntries()
        }
        suggestionsListener?.invoke(emptyList())
        return result
    }

    fun onCursorMoved(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        rebuildFromContext(inputConnection, fallback = { tracker.reset() })
        updateSuggestions()
    }

    fun onContextReset() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        suggestionsListener?.invoke(emptyList())
    }

    fun onNavModeToggle() {
        if (!isEnabled()) return
        tracker.onContextChanged()
    }

    fun addUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.addUserEntry(word)
    }

    fun removeUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.removeUserEntry(word)
    }

    fun markUsed(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.markUsed(word)
    }

    fun currentSuggestions(): List<SuggestionResult> = latestSuggestions.get()

    fun userDictionarySnapshot(): List<UserDictionaryStore.UserEntry> = userDictionaryStore.getSnapshot()

    private fun extractWordAtCursor(inputConnection: InputConnection?): String? {
        if (inputConnection == null) return null
        return try {
            val before = inputConnection.getTextBeforeCursor(64, 0)?.toString() ?: ""
            val after = inputConnection.getTextAfterCursor(64, 0)?.toString() ?: ""
            val boundary = " \t\n\r.,;:!?()[]{}\"'"
            var start = before.length
            while (start > 0 && !boundary.contains(before[start - 1])) {
                start--
            }
            var end = 0
            while (end < after.length && !boundary.contains(after[end])) {
                end++
            }
            val word = before.substring(start) + after.substring(0, end)
            if (word.isBlank()) null else word
        } catch (e: Exception) {
            Log.d("PastieraIME", "extractWordAtCursor failed: ${e.message}")
            null
        }
    }
}
