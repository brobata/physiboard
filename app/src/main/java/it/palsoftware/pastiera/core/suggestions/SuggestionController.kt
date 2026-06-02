package it.palsoftware.pastiera.core.suggestions

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CancellationException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import it.palsoftware.pastiera.inputmethod.NotificationHelper
import java.io.File
import org.json.JSONObject

class SuggestionController(
    context: Context,
    private val assets: AssetManager,
    private val settingsProvider: () -> SuggestionSettings,
    private val isEnabled: () -> Boolean = { true },
    debugLogging: Boolean = false,
    private val onSuggestionsUpdated: (List<SuggestionResult>) -> Unit,
    private var currentLocale: Locale = Locale.ITALIAN,
    private val keyboardLayoutProvider: () -> String = { "qwerty" },
    private val dictionaryRepositoryFactory: ((
        Context,
        AssetManager,
        UserDictionaryStore,
        Locale,
        Boolean
    ) -> DictionaryRepository)? = null,
    nextWordPredictorOverride: NextWordPredictor? = null,
    private val activeSuggestionLocalesProvider: (() -> List<Locale>)? = null
) {

    private val appContext = context.applicationContext
    private val debugLogging: Boolean = debugLogging
    private val userDictionaryStore = UserDictionaryStore()
    private var dictionaryRepository: DictionaryRepository = createDictionaryRepository(currentLocale)
    private var suggestionEngine = SuggestionEngine(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging).apply {
        setKeyboardLayout(keyboardLayoutProvider())
    }
    private var tracker = CurrentWordTracker(
        onWordChanged = { word ->
            updateSuggestionsForWord(word)
        },
        onWordReset = {
            latestSuggestions.set(emptyList())
            pendingAddUserWord = null
            suggestionsListener?.invoke(emptyList())
        }
    )
    private var autoReplaceController = AutoReplaceController(dictionaryRepository, suggestionEngine, settingsProvider)
    private val nextWordPredictor = nextWordPredictorOverride ?: NextWordPredictor(UserNGramStore(appContext))
    private val extraSuggestionEngines = mutableMapOf<String, SuggestionLanguageEngine>()

    private data class SuggestionLanguageEngine(
        val locale: Locale,
        val repository: DictionaryRepository,
        val engine: SuggestionEngine
    )

    private fun createDictionaryRepository(locale: Locale): DictionaryRepository {
        return dictionaryRepositoryFactory?.invoke(appContext, assets, userDictionaryStore, locale, debugLogging)
            ?: AndroidDictionaryRepository(
                appContext,
                assets,
                userDictionaryStore,
                baseLocale = locale,
                debugLogging = debugLogging
            )
    }
    
    /**
     * Updates the locale and reloads the dictionary for the new language.
     */
    fun updateLocale(newLocale: Locale) {
        if (newLocale == currentLocale) return
        
        // Cancel previous load job if still running to prevent conflicts
        currentLoadJob?.cancel()
        currentLoadJob = null
        
        currentLocale = newLocale
        dictionaryRepository = createDictionaryRepository(currentLocale)
        suggestionEngine = SuggestionEngine(dictionaryRepository, locale = currentLocale, debugLogging = debugLogging).apply {
            setKeyboardLayout(keyboardLayoutProvider())
        }
        autoReplaceController = AutoReplaceController(dictionaryRepository, suggestionEngine, settingsProvider)
        extraSuggestionEngines.clear()
        
        // Recreate tracker to use new engine (tracker captures suggestionEngine in closure)
        tracker = CurrentWordTracker(
            onWordChanged = { word ->
                updateSuggestionsForWord(word)
            },
            onWordReset = {
                latestSuggestions.set(emptyList())
                pendingAddUserWord = null
                suggestionsListener?.invoke(emptyList())
            }
        )
        
        // Reload dictionary in background
        currentLoadJob = loadScope.launch {
            dictionaryRepository.loadIfNeeded()
        }
        
        // Reset tracker and clear suggestions
        previousCompletedWord = null
        tracker.reset()
        suggestionsListener?.invoke(emptyList())
    }

    /**
     * Updates the keyboard layout for proximity-based ranking.
     */
    fun updateKeyboardLayout(layout: String) {
        suggestionEngine.setKeyboardLayout(layout)
        extraSuggestionEngines.values.forEach { it.engine.setKeyboardLayout(layout) }
    }

    private val latestSuggestions: AtomicReference<List<SuggestionResult>> = AtomicReference(emptyList())
    // Dedicated IO scope so dictionary preload never blocks the main thread.
    private val loadScope = CoroutineScope(Dispatchers.IO)
    private var currentLoadJob: Job? = null
    private val cursorHandler = Handler(Looper.getMainLooper())
    private var cursorRunnable: Runnable? = null
    private val cursorDebounceMs = 120L
    private var pendingAddUserWord: String? = null
    private var previousCompletedWord: String? = null

    private fun updateSuggestionsForWord(word: String) {
        val settings = settingsProvider()
        if (!settings.suggestionsEnabled) {
            pendingAddUserWord = null
            latestSuggestions.set(emptyList())
            suggestionsListener?.invoke(emptyList())
            return
        }
        if (debugLogging) {
            Log.d("PastieraIME", "trackerWordChanged='$word' len=${word.length}")
        }
        val primary = suggestionEngine.suggest(
            word,
            settings.maxSuggestions,
            settings.accentMatching,
            settings.useKeyboardProximity,
            settings.useEditTypeRanking
        )
        val extraSuggestions = activeExtraSuggestionEngines().flatMap { extra ->
            if (!extra.repository.isReady) {
                scheduleRepositoryLoad(extra.repository)
                emptyList()
            } else {
                extra.engine.suggest(
                    word,
                    settings.maxSuggestions,
                    settings.accentMatching,
                    settings.useKeyboardProximity,
                    settings.useEditTypeRanking
                )
            }
        }
        val next = mergeSuggestionResults(primary, extraSuggestions, settings.maxSuggestions)
        pendingAddUserWord = addWordCandidateFor(word)
        latestSuggestions.set(next)
        suggestionsListener?.invoke(next)
    }

    private fun addWordCandidateFor(word: String?): String? {
        val candidate = word?.trim() ?: return null
        if (candidate.isEmpty() || candidate.none { it.isLetterOrDigit() }) return null
        if (!dictionaryRepository.isReady) return null
        return if (dictionaryRepository.isKnownWord(candidate)) null else candidate
    }
    
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

    var suggestionsListener: ((List<SuggestionResult>) -> Unit)? = onSuggestionsUpdated

    fun onCharacterCommitted(text: CharSequence, inputConnection: InputConnection?) {
        if (!isEnabled()) return
        // #region agent log
        val trackerWordBefore = tracker.currentWord
        debugLog("A", "SuggestionController.onCharacterCommitted:entry", "onCharacterCommitted called", mapOf(
            "text" to text.toString(),
            "trackerWordBefore" to trackerWordBefore,
            "trackerWordLengthBefore" to trackerWordBefore.length
        ))
        // #endregion
        if (debugLogging) {
            val caller = Throwable().stackTrace.getOrNull(1)?.let { "${it.className}#${it.methodName}:${it.lineNumber}" }
            Log.d("PastieraIME", "SuggestionController.onCharacterCommitted('$text') caller=$caller")
        }
        ensureDictionaryLoaded()

        // Normalize curly/variant apostrophes to straight for tracking and suggestions.
        val normalizedText = text
            .toString()
            .replace("'", "'")
            .replace("'", "'")
            .replace("ʼ", "'")
        
        // Clear last replacement if user types new characters
        autoReplaceController.clearLastReplacement()
        
        // Clear rejected words when user types a new letter (allows re-correction)
        if (normalizedText.isNotEmpty() && normalizedText.any { it.isLetterOrDigit() }) {
            autoReplaceController.clearRejectedWords()
            pendingAddUserWord = null
        }
        
        tracker.onCharacterCommitted(normalizedText)
        // #region agent log
        val trackerWordAfter = tracker.currentWord
        debugLog("A", "SuggestionController.onCharacterCommitted:exit", "tracker updated after onCharacterCommitted", mapOf(
            "trackerWordAfter" to trackerWordAfter,
            "trackerWordLengthAfter" to trackerWordAfter.length,
            "normalizedText" to normalizedText
        ))
        // #endregion
    }

    fun refreshFromInputConnection(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        tracker.onBackspace()
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
        ensureDictionaryLoaded()

        // CRITICAL FIX: Sync tracker with actual text before processing boundary
        // The cursor debounce can cause tracker to be out of sync with the actual text field
        if (inputConnection != null && dictionaryRepository.isReady) {
            val word = extractWordAtCursor(inputConnection, includeAfterCursor = false)
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
                Log.d("PastieraIME", "SYNC: Synced tracker to actual word='$word' before boundary")
            }
        }

        val boundaryChar = boundaryCharFor(keyCode, event)
        val wordBeforeBoundary = tracker.currentWord.takeIf { it.isNotBlank() }
        val result = autoReplaceController.handleBoundary(keyCode, event, tracker, inputConnection)
        val completedWord = result.replacement ?: wordBeforeBoundary
        if (result.replaced) {
            pendingAddUserWord = addWordCandidateFor(result.replacement)
            NotificationHelper.triggerHapticFeedback(appContext)
        } else {
            pendingAddUserWord = null
        }
        handleCompletedWordBoundary(completedWord, boundaryChar)
        return result
    }

    /**
     * Reads the word at cursor immediately without debounce.
     * Use this when entering a text field to show suggestions right away.
     * If dictionary is not ready yet, does nothing - normal typing/cursor flow will handle it.
     */
    fun readInitialContext(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        if (inputConnection == null || !dictionaryRepository.isReady) return
        
        val word = extractWordAtCursor(inputConnection)
        if (!word.isNullOrBlank()) {
            tracker.setWord(word)
        }
    }

    fun onCursorMoved(inputConnection: InputConnection?) {
        if (!isEnabled()) return
        // #region agent log
        val trackerWordBefore = tracker.currentWord
        debugLog("A", "SuggestionController.onCursorMoved:entry", "onCursorMoved called", mapOf(
            "trackerWordBefore" to trackerWordBefore,
            "trackerWordLengthBefore" to trackerWordBefore.length
        ))
        // #endregion
        ensureDictionaryLoaded()
        cursorRunnable?.let { cursorHandler.removeCallbacks(it) }
        if (inputConnection == null) {
            tracker.reset()
            previousCompletedWord = null
            suggestionsListener?.invoke(emptyList())
            return
        }
        cursorRunnable = Runnable {
            // #region agent log
            val trackerWordBeforeExtract = tracker.currentWord
            debugLog("B", "SuggestionController.onCursorMoved:runnable", "extractWordAtCursor about to be called", mapOf(
                "trackerWordBeforeExtract" to trackerWordBeforeExtract,
                "trackerWordLengthBeforeExtract" to trackerWordBeforeExtract.length
            ))
            // #endregion
            if (!dictionaryRepository.isReady) {
                tracker.reset()
                previousCompletedWord = null
                suggestionsListener?.invoke(emptyList())
                return@Runnable
            }
            val word = extractWordAtCursor(inputConnection)
            // #region agent log
            debugLog("B", "SuggestionController.onCursorMoved:afterExtract", "extractWordAtCursor returned", mapOf(
                "extractedWord" to (word ?: "null"),
                "extractedWordLength" to (word?.length ?: 0),
                "trackerWordBeforeSet" to trackerWordBeforeExtract,
                "trackerWordLengthBeforeSet" to trackerWordBeforeExtract.length
            ))
            // #endregion
            if (!word.isNullOrBlank()) {
                tracker.setWord(word)
                // #region agent log
                val trackerWordAfter = tracker.currentWord
                debugLog("B", "SuggestionController.onCursorMoved:afterSet", "tracker.setWord called", mapOf(
                    "trackerWordAfter" to trackerWordAfter,
                    "trackerWordLengthAfter" to trackerWordAfter.length,
                    "extractedWord" to word
                ))
                // #endregion
            } else {
                tracker.reset()
                val previous = previousCompletedWord
                val lastChar = lastCharBeforeCursor(inputConnection)
                if (previous != null && isSoftPredictionBoundary(lastChar)) {
                    publishNextWordPredictions(previous)
                } else {
                    previousCompletedWord = null
                    suggestionsListener?.invoke(emptyList())
                }
            }
        }
        cursorHandler.postDelayed(cursorRunnable!!, cursorDebounceMs)
    }

    fun onContextReset() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        pendingAddUserWord = null
        previousCompletedWord = null
        suggestionsListener?.invoke(emptyList())
    }

    fun onNavModeToggle() {
        if (!isEnabled()) return
        tracker.onContextChanged()
        previousCompletedWord = null
    }

    fun addUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.addUserEntryQuick(word)
    }

    fun removeUserWord(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.removeUserEntry(word)
        refreshUserDictionary()
    }

    fun markUsed(word: String) {
        if (!isEnabled()) return
        dictionaryRepository.markUsed(word)
    }

    fun isKnownWordInActiveDictionaries(word: String): Boolean {
        if (!isEnabled()) return false
        val candidate = word.trim()
        if (candidate.isEmpty()) return false

        ensureDictionaryLoaded()
        if (dictionaryRepository.isReady && dictionaryRepository.isKnownWord(candidate)) {
            return true
        }

        return activeExtraSuggestionEngines().any { extra ->
            if (!extra.repository.isReady) {
                scheduleRepositoryLoad(extra.repository)
                // Defer legacy auto-substitution while an explicitly active
                // extra dictionary is still loading; wrong replacements are
                // worse than skipping one boundary.
                true
            } else {
                extra.repository.isKnownWord(candidate)
            }
        }
    }

    fun currentSuggestions(): List<SuggestionResult> = latestSuggestions.get()

    fun userDictionarySnapshot(): List<UserDictionaryStore.UserEntry> = userDictionaryStore.getSnapshot()

    /**
     * Forces a refresh of user dictionary entries.
     * Should be called when words are added/removed from settings.
     */
    fun refreshUserDictionary() {
        if (!isEnabled()) return
        loadScope.launch {
            try {
                dictionaryRepository.refreshUserEntries()
            } catch (_: CancellationException) {
                // Cancelled due to rapid switches; safe to ignore.
            } catch (e: Exception) {
                Log.e("PastieraIME", "Failed to refresh user dictionary", e)
            }
        }
    }

    fun handleBackspaceUndo(keyCode: Int, inputConnection: InputConnection?): Boolean {
        if (!isEnabled()) return false
        val undone = autoReplaceController.handleBackspaceUndo(keyCode, inputConnection)
        if (undone) {
            pendingAddUserWord = autoReplaceController.consumeLastUndoOriginalWord()
        }
        return undone
    }

    fun pendingAddWord(): String? = pendingAddUserWord
    fun clearPendingAddWord() {
        pendingAddUserWord = null
    }

    internal fun clearLearnedNextWordsForTests() {
        nextWordPredictor.clearAll()
        previousCompletedWord = null
    }

    private fun handleCompletedWordBoundary(completedWord: String?, boundaryChar: Char?) {
        val settings = settingsProvider()
        if (!settings.suggestionsEnabled) {
            previousCompletedWord = null
            latestSuggestions.set(emptyList())
            suggestionsListener?.invoke(emptyList())
            return
        }

        val cleanWord = completedWord?.trim()?.takeIf { it.any { ch -> ch.isLetterOrDigit() } }
        if (cleanWord != null) {
            previousCompletedWord?.let { previous ->
                nextWordPredictor.learn(currentLocale, previous, cleanWord)
            }
        }

        when {
            cleanWord != null && isSoftPredictionBoundary(boundaryChar) -> {
                previousCompletedWord = cleanWord
                publishNextWordPredictions(cleanWord)
            }
            cleanWord == null && isSoftPredictionBoundary(boundaryChar) -> {
                val previous = previousCompletedWord
                if (previous != null) {
                    publishNextWordPredictions(previous)
                } else {
                    latestSuggestions.set(emptyList())
                    suggestionsListener?.invoke(emptyList())
                }
            }
            else -> {
                previousCompletedWord = null
                latestSuggestions.set(emptyList())
                suggestionsListener?.invoke(emptyList())
            }
        }
    }

    private fun publishNextWordPredictions(previousWord: String) {
        val settings = settingsProvider()
        val primary = nextWordPredictor.predict(
            currentLocale,
            previousWord,
            settings.maxSuggestions
        )
        val extras = activeExtraLocales().flatMap { locale ->
            nextWordPredictor.predict(locale, previousWord, settings.maxSuggestions)
        }
        val predictions = mergeSuggestionResults(primary, extras, settings.maxSuggestions)
        latestSuggestions.set(predictions)
        suggestionsListener?.invoke(predictions)
    }

    private fun mergeSuggestionResults(
        primary: List<SuggestionResult>,
        extras: List<SuggestionResult>,
        limit: Int
    ): List<SuggestionResult> {
        val seen = HashSet<String>()
        return (primary.map { it to PRIMARY_SUGGESTION_BOOST } + extras.map { it to 0.0 })
            .sortedWith(
                compareByDescending<Pair<SuggestionResult, Double>> { (result, boost) ->
                    result.score + boost
                }.thenBy { (result, _) -> result.candidate.length }
            )
            .map { it.first }
            .filter { result -> seen.add(result.candidate.lowercase(currentLocale)) }
            .take(limit)
    }

    private fun activeExtraLocales(): List<Locale> {
        val primaryLanguage = currentLocale.language.lowercase(Locale.ROOT)
        return activeSuggestionLocalesProvider?.invoke().orEmpty()
            .filter { it.language.isNotBlank() }
            .filter { it.language.lowercase(Locale.ROOT) != primaryLanguage }
            .distinctBy { it.toLanguageTag().lowercase(Locale.ROOT) }
    }

    private fun activeExtraSuggestionEngines(): List<SuggestionLanguageEngine> {
        val activeLocales = activeExtraLocales()
        val activeTags = activeLocales.map { it.toLanguageTag() }.toSet()
        extraSuggestionEngines.keys
            .filterNot { it in activeTags }
            .forEach { extraSuggestionEngines.remove(it) }
        return activeLocales.map { locale ->
            val tag = locale.toLanguageTag()
            extraSuggestionEngines.getOrPut(tag) {
                val repository = createDictionaryRepository(locale)
                val engine = SuggestionEngine(repository, locale = locale, debugLogging = debugLogging).apply {
                    setKeyboardLayout(keyboardLayoutProvider())
                }
                SuggestionLanguageEngine(locale, repository, engine)
            }
        }
    }

    private fun scheduleRepositoryLoad(repository: DictionaryRepository) {
        if (!repository.isReady && !repository.isLoadStarted) {
            loadScope.launch {
                repository.loadIfNeeded()
            }
        }
    }

    private fun isSoftPredictionBoundary(boundaryChar: Char?): Boolean {
        return boundaryChar == ' ' || boundaryChar == ',' || boundaryChar == ';' || boundaryChar == ':'
    }

    private fun boundaryCharFor(keyCode: Int, event: KeyEvent?): Char? {
        val unicodeChar = event?.unicodeChar ?: 0
        return when {
            unicodeChar != 0 -> unicodeChar.toChar()
            keyCode == KeyEvent.KEYCODE_SPACE -> ' '
            keyCode == KeyEvent.KEYCODE_ENTER -> '\n'
            keyCode == KeyEvent.KEYCODE_COMMA -> ','
            keyCode == KeyEvent.KEYCODE_SEMICOLON -> ';'
            keyCode == KeyEvent.KEYCODE_PERIOD -> '.'
            keyCode == KeyEvent.KEYCODE_SLASH -> '/'
            keyCode == KeyEvent.KEYCODE_LEFT_BRACKET -> '['
            keyCode == KeyEvent.KEYCODE_RIGHT_BRACKET -> ']'
            keyCode == KeyEvent.KEYCODE_BACKSLASH -> '\\'
            else -> KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                .get(keyCode, 0)
                .takeIf { it != 0 }
                ?.toChar()
        }
    }

    private fun lastCharBeforeCursor(inputConnection: InputConnection?): Char? {
        return try {
            inputConnection?.getTextBeforeCursor(1, 0)?.lastOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clears the pending add-word candidate if the cursor is no longer on that word.
     * Keeps the candidate only while the cursor remains on the originating token.
     */
    fun clearPendingAddWordIfCursorOutside(inputConnection: InputConnection?) {
        val pending = pendingAddUserWord ?: return
        val currentWord = extractWordAtCursor(inputConnection)
        if (currentWord == null || !currentWord.equals(pending, ignoreCase = true)) {
            pendingAddUserWord = null
        }
    }

    private fun extractWordAtCursor(
        inputConnection: InputConnection?,
        includeAfterCursor: Boolean = true
    ): String? {
        if (inputConnection == null) return null
        return try {
            val before = inputConnection.getTextBeforeCursor(CURSOR_WORD_CONTEXT_CHARS, 0)?.toString() ?: ""
            val after = if (includeAfterCursor) {
                inputConnection.getTextAfterCursor(CURSOR_WORD_CONTEXT_CHARS, 0)?.toString() ?: ""
            } else {
                ""
            }
            // #region agent log
            debugLog("B", "SuggestionController.extractWordAtCursor:before", "getTextBeforeCursor/getTextAfterCursor called", mapOf(
                "before" to before,
                "beforeLength" to before.length,
                "after" to after,
                "afterLength" to after.length,
                "includeAfterCursor" to includeAfterCursor
            ))
            // #endregion
            var start = before.length
            while (start > 0) {
                val ch = before[start - 1]
                val prev = before.getOrNull(start - 2)
                val next = before.getOrNull(start)
                if (!it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)) {
                    start--
                    continue
                }
                break
            }
            var end = 0
            if (includeAfterCursor) {
                while (end < after.length) {
                    val ch = after[end]
                    val prev = if (end == 0) before.lastOrNull() else after[end - 1]
                    val next = after.getOrNull(end + 1)
                    if (!it.palsoftware.pastiera.core.Punctuation.isWordBoundary(ch, prev, next)) {
                        end++
                        continue
                    }
                    break
                }
            }
            val word = before.substring(start) + after.substring(0, end)
            // #region agent log
            debugLog("B", "SuggestionController.extractWordAtCursor:after", "word extracted", mapOf(
                "extractedWord" to (if (word.isBlank()) "null" else word),
                "extractedWordLength" to word.length,
                "beforeSubstring" to before.substring(start),
                "afterSubstring" to after.substring(0, end)
            ))
            // #endregion
            if (word.isBlank()) null else word
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Preloads the dictionary in background.
     * Should be called during initialization to have dictionary ready when user focuses a field.
     */
    fun preloadDictionary() {
        if (!dictionaryRepository.isReady && !dictionaryRepository.isLoadStarted) {
            loadScope.launch {
                dictionaryRepository.loadIfNeeded()
            }
        }
        activeExtraSuggestionEngines().forEach { scheduleRepositoryLoad(it.repository) }
    }

    private fun ensureDictionaryLoaded() {
        if (!dictionaryRepository.isReady) {
            dictionaryRepository.ensureLoadScheduled {
                loadScope.launch {
                    dictionaryRepository.loadIfNeeded()
                }
            }
        }
    }

    companion object {
        private const val CURSOR_WORD_CONTEXT_CHARS = 128
        private const val PRIMARY_SUGGESTION_BOOST = 0.35
    }
}
