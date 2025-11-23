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
    onSuggestionsUpdated: (List<SuggestionResult>) -> Unit
) {

    private val appContext = context.applicationContext
    private val userDictionaryStore = UserDictionaryStore()
    private val dictionaryRepository = DictionaryRepository(appContext, assets, userDictionaryStore)
    private val suggestionEngine = SuggestionEngine(dictionaryRepository)
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
        Log.d("PastieraIME", "SuggestionController.onCharacterCommitted('$text')")
        rebuildFromContext(inputConnection, fallback = { tracker.onCharacterCommitted(text) })
        updateSuggestions()
    }

    /**
     * Rebuild the current word from the text field (used on backspace or cursor edits).
     */
    fun refreshFromInputConnection(inputConnection: InputConnection?) {
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
            Log.d("PastieraIME", "suggestions (${next.size}): $summary")
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
        Log.d(
            "PastieraIME",
            "SuggestionController.onBoundaryKey keyCode=$keyCode char=${event?.unicodeChar}"
        )
        val result = autoReplaceController.handleBoundary(keyCode, event, tracker, inputConnection)
        if (result.replaced) {
            dictionaryRepository.refreshUserEntries()
        }
        suggestionsListener?.invoke(emptyList())
        return result
    }

    fun onCursorMoved(inputConnection: InputConnection?) {
        rebuildFromContext(inputConnection, fallback = { tracker.reset() })
        updateSuggestions()
    }

    fun onContextReset() {
        tracker.onContextChanged()
        suggestionsListener?.invoke(emptyList())
    }

    fun onNavModeToggle() {
        tracker.onContextChanged()
    }

    fun addUserWord(word: String) {
        dictionaryRepository.addUserEntry(word)
    }

    fun removeUserWord(word: String) {
        dictionaryRepository.removeUserEntry(word)
    }

    fun markUsed(word: String) {
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
