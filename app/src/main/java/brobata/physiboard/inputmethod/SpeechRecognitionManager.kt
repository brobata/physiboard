package brobata.physiboard.inputmethod

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import brobata.physiboard.R
import brobata.physiboard.SettingsManager
import brobata.physiboard.inputmethod.AutoCapitalizeHelper
import brobata.physiboard.inputmethod.subtype.AdditionalSubtypeUtils.localeString
import java.util.Locale

/**
 * Manages speech recognition using SpeechRecognizer.
 * Handles initialization, recognition, and text insertion with real-time updates.
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val inputConnectionProvider: () -> InputConnection?,
    private val onError: ((String) -> Unit)? = null,
    private val onRecognitionStateChanged: ((Boolean) -> Unit)? = null,
    private val shouldDisableAutoCapitalize: () -> Boolean = { false },
    private val onAudioLevelChanged: ((Float) -> Unit)? = null
) {
    companion object {
        private const val TAG = "SpeechRecognitionMgr"
        /** Roughly how long the recognizer itself waits before ending a request on silence. */
        private const val RECOGNIZER_INTERNAL_SILENCE_MS = 1000
        private const val MIN_SILENCE_TIMER_MS = 400
        /** A continuation that errors faster than this is a failure loop, not silence. */
        private const val CONTINUATION_MIN_RUN_MS = 700L
        /**
         * How long past the configured pause a segmented engine has to close its own session.
         * Generous on purpose: firing early cuts a live dictation short, while firing late only
         * costs a couple of seconds before the session is recovered.
         */
        private const val SEGMENTED_WATCHDOG_SLACK_MS = 5000L
        /** An error this soon into a segmented session is the engine refusing the request. */
        private const val SEGMENTED_REJECT_WINDOW_MS = 1200L

        /**
         * Whether a dictation session should ask the engine to hold one segmented session open
         * and end it on the configured pause, rather than the keyboard restarting the recognizer
         * after every result and timing the pause itself.
         *
         * Needs Android 13, the user's opt-in, a pause to actually end on, and an engine that has
         * not already refused a segmented request.
         */
        internal fun shouldUseSegmentedSession(
            sdkInt: Int,
            userEnabled: Boolean,
            engineRefusedSegments: Boolean,
            pauseMs: Int
        ): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU &&
            userEnabled &&
            !engineRefusedSegments &&
            pauseMs > 0

        internal fun normalizeSubtypeLocaleToLanguageTag(subtypeLocale: String?): String? {
            val normalized = subtypeLocale
                ?.trim()
                ?.replace('_', '-')
                ?.takeIf { it.isNotEmpty() }
                ?: return null

            val locale = Locale.forLanguageTag(normalized)
            return if (locale.language.isNullOrEmpty()) null else locale.toLanguageTag()
        }

        internal fun buildRecognitionLanguageTag(
            imeSubtypeLocale: String?,
            deviceLocale: Locale?
        ): String {
            return normalizeSubtypeLocaleToLanguageTag(imeSubtypeLocale)
                ?: deviceLocale?.toLanguageTag()?.takeIf { it.isNotEmpty() }
                ?: "it-IT"
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isComposingPartialText: Boolean = false
    // Last partial hypothesis, used to detect when the recognizer starts a NEW utterance
    // after a pause within one session (so we commit the previous one instead of overwriting it).
    private var lastPartialText: String = ""

    /**
     * A partial is a NEW utterance (not a continuation of the previous one) when neither string
     * is a prefix of the other AND the first words differ. Growing text and mid-utterance
     * re-recognition (where the first word usually stays) are treated as the SAME utterance.
     */
    private fun looksLikeNewUtterance(prev: String, curr: String): Boolean {
        val p = prev.trim()
        val c = curr.trim()
        if (p.isEmpty() || c.isEmpty()) return false
        if (c.startsWith(p, ignoreCase = true) || p.startsWith(c, ignoreCase = true)) return false
        return !p.substringBefore(' ').equals(c.substringBefore(' '), ignoreCase = true)
    }

    /**
     * Normalizes punctuation words (e.g., "punto" -> ".") in recognized text.
     */
    private fun normalizePunctuationWords(text: String): String {
        var normalized = text
        
        // Italian punctuation words to symbols
        val punctuationMap = mapOf(
            " punto " to ". ",
            " punto" to ".",
            "punto " to ". ",
            " virgola " to ", ",
            " virgola" to ",",
            "virgola " to ", ",
            " punto e virgola " to "; ",
            " punto e virgola" to ";",
            " punto e virgola" to "; ",
            " due punti " to ": ",
            " due punti" to ":",
            "due punti " to ": ",
            " punto interrogativo " to "? ",
            " punto interrogativo" to "?",
            " punto interrogativo" to "? ",
            " punto esclamativo " to "! ",
            " punto esclamativo" to "!",
            " punto esclamativo" to "! "
        )
        
        // Replace in order of length (longer first to avoid partial matches)
        val sortedEntries = punctuationMap.entries.sortedByDescending { it.key.length }
        for ((word, symbol) in sortedEntries) {
            normalized = normalized.replace(word, symbol, ignoreCase = true)
        }
        
        return normalized
    }

    /**
     * Formats text according to standard auto-capitalization rules (first letter and after period).
     * Uses AutoCapitalizeHelper to check if capitalization should be applied.
     */
    private fun formatTextWithAutoCapitalization(text: String): String {
        if (text.isEmpty()) return text
        
        val inputConnection = inputConnectionProvider() ?: return text
        
        // Check if we should disable auto-capitalization
        if (shouldDisableAutoCapitalize()) {
            return text
        }
        
        var formatted = text
        
        // Capitalize first letter if needed
        val shouldCapitalizeFirst = AutoCapitalizeHelper.shouldAutoCapitalizeAtCursor(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize()
        ) && SettingsManager.getAutoCapitalizeFirstLetter(context)
        
        if (shouldCapitalizeFirst && formatted.isNotEmpty()) {
            formatted = formatted.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) 
                else it.toString() 
            }
        }
        
        // Capitalize after sentence-ending punctuation (., !, ?)
        if (SettingsManager.getAutoCapitalizeAfterPeriod(context)) {
            // Pattern: find . ! or ? followed by a space and a lowercase letter
            formatted = formatted.replace(Regex("([.!?]\\s+)([a-z])")) { matchResult ->
                matchResult.groupValues[1] + matchResult.groupValues[2].uppercase()
            }
        }
        
        return formatted
    }

    /**
     * Ensures SpeechRecognizer is initialized with a RecognitionListener.
     */
    private fun ensureSpeechRecognizer() {
        val engineId = SettingsManager.getDictationEngine(context)
        if (speechRecognizer != null && activeEngineId != engineId) {
            Log.d(TAG, "Dictation engine changed ('$activeEngineId' -> '$engineId') — rebuilding")
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            segmentedUnsupported = false
        }
        if (speechRecognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "Speech recognition is not available on this device")
                return
            }

            Log.d(TAG, "Creating SpeechRecognizer for engine '$engineId'")
            speechRecognizer = RecognitionEngines.createRecognizer(context, engineId)?.apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Speech recognition ready for speech")
                        // Reset composing state for new recognition session
                        isComposingPartialText = false
                        // Notify that recognition is active (hint will be shown by the UI).
                        // Once per session: continuations must not re-cue.
                        if (!sessionCueStarted) {
                            sessionCueStarted = true
                            onRecognitionStateChanged?.invoke(true)
                            playHapticCue(started = true)
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech recognition: beginning of speech")
                        // Speech resumed inside the pause window: keep the session open.
                        cancelSilenceTimer()
                        cancelSegmentedWatchdog()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Update UI feedback based on audio level
                        onAudioLevelChanged?.invoke(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        // Optional
                    }

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "Speech recognition: end of speech detected")
                        // The system automatically detected silence after speech
                        // onResults() will be called next with the final recognition result
                        // Only now can a segmented session be close to ending, so this is the
                        // point to start watching for one that never does.
                        if (segmentedSession) armSegmentedWatchdog()
                    }

                    override fun onError(error: Int) {
                        // A segmented session is optional in the RecognitionService contract, so
                        // an engine may simply refuse one. Failing that fast, before a single
                        // segment, is a refusal rather than a recognition problem: retry the same
                        // session as a plain request and stop asking for segments from here on.
                        val refusedSegments = segmentedSession &&
                            segmentsSeen == 0 &&
                            sessionActive &&
                            !stopRequested &&
                            SystemClock.uptimeMillis() - sessionStartedAt < SEGMENTED_REJECT_WINDOW_MS &&
                            error != SpeechRecognizer.ERROR_NO_MATCH &&
                            error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                            error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS &&
                            error != SpeechRecognizer.ERROR_AUDIO
                        if (refusedSegments) {
                            Log.w(TAG, "Segmented session refused (error $error) — retrying without it")
                            segmentedUnsupported = true
                            segmentedSession = false
                            cancelSegmentedWatchdog()
                            if (isComposingPartialText) clearPartialText()
                            restartWithoutSegmentedSession()
                            return
                        }

                        // Within a continued session the recognizer may give up on a silent
                        // stretch before our pause expires: keep waiting on our timer instead
                        // of ending the session. Guard against a tight failure loop.
                        val isQuietError = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                        val continuing = sessionActive && !stopRequested && continuationStartedAt > 0L
                        val ranLongEnough =
                            SystemClock.uptimeMillis() - continuationStartedAt > CONTINUATION_MIN_RUN_MS
                        if (continuing && isQuietError && ranLongEnough) {
                            Log.d(TAG, "Quiet continuation ended by recognizer — waiting for pause timer")
                            if (isComposingPartialText) clearPartialText()
                            if (configuredPauseMs() > 0) {
                                // Timer is still running from the last result; if it has
                                // already fired, endSession handled it.
                                if (sessionActive) continueSessionIfTimerPending()
                            } else {
                                endSession(cancelRecognizer = false)
                            }
                            return
                        }

                        // A segmented session that already produced text and now reports silence
                        // has simply run out of speech. That is the session ending the way it is
                        // meant to, so close it without complaining.
                        if (segmentedSession && isQuietError && segmentsSeen > 0) {
                            Log.d(TAG, "Segmented session ended on silence after $segmentsSeen segment(s)")
                            endSession(cancelRecognizer = false)
                            if (isComposingPartialText) clearPartialText()
                            return
                        }

                        // A callback for a session we already closed: the pause timer fired
                        // and cancelled the recognizer while its restarted request was still in
                        // flight, so the request reports the silence that ended the session as
                        // an error. Nothing failed and there is nothing to act on — stay quiet.
                        if (!sessionActive) {
                            Log.d(TAG, "Trailing error $error after the session ended \u2014 ignored")
                            if (isComposingPartialText) clearPartialText()
                            return
                        }

                        // Real error: end the session.
                        endSession(cancelRecognizer = false)
                        
                        // Clear partial text on error
                        if (isComposingPartialText) {
                            clearPartialText()
                        }
                        
                        val errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO - Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT - Other client side errors"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS - Insufficient permissions"
                            SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK - Network related errors"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT - Network operation timed out"
                            SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH - No recognition result matched"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY - RecognitionService busy"
                            SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER - Server sends error status"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT - No speech input"
                            else -> "UNKNOWN_ERROR($error)"
                        }
                        Log.w(TAG, "Speech recognition error: $errorMessage")
                        
                        // Show user-friendly error message
                        Handler(Looper.getMainLooper()).post {
                            val userMessage = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.speech_recognition_error_no_match)
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.speech_recognition_error_timeout)
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.speech_recognition_error_permission)
                                SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.speech_recognition_error_network)
                                else -> context.getString(R.string.speech_recognition_error_generic)
                            }
                            Toast.makeText(context, userMessage, Toast.LENGTH_SHORT).show()
                            onError?.invoke(userMessage)
                        }
                    }

                    override fun onResults(results: Bundle) {
                        commitRecognizedText(results)

                        if (segmentedSession) {
                            // A segmented engine reports finals through onSegmentResults and ends
                            // the session itself. Reaching here means it ignored the request and
                            // ran a plain one-shot, so let the watchdog close the session and fall
                            // back to the restart loop next time.
                            armSegmentedWatchdog()
                            return
                        }

                        // Keep the session open for the configured pause, then listen again.
                        if (sessionActive && !stopRequested && configuredPauseMs() > 0) {
                            armSilenceTimer()
                            continueSession()
                        } else {
                            endSession(cancelRecognizer = false)
                        }
                    }

                    /**
                     * Segmented sessions deliver one of these per utterance and keep listening;
                     * the engine, not our timer, decides when the pause has run out.
                     */
                    override fun onSegmentResults(segmentResults: Bundle) {
                        segmentsSeen++
                        Log.d(TAG, "Segment result #$segmentsSeen")
                        commitRecognizedText(segmentResults)
                        // The engine owes us an onEndOfSegmentedSession; if it never arrives the
                        // session would hang "listening" forever, so keep a watchdog on it.
                        armSegmentedWatchdog()
                    }

                    override fun onEndOfSegmentedSession() {
                        Log.d(TAG, "Segmented session ended by the engine")
                        cancelSegmentedWatchdog()
                        endSession(cancelRecognizer = false)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partialText = matches?.firstOrNull() ?: ""
                        
                        if (partialText.isNotEmpty()) {
                            Log.d(TAG, "Speech recognition partial results: '$partialText'")
                            cancelSilenceTimer()
                            cancelSegmentedWatchdog()
                            // Insert/update partial text in real-time
                            updatePartialSpeechText(partialText)
                        } else {
                            Log.d(TAG, "Speech recognition partial results: none")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {
                        // Optional
                    }
                })
            }
            activeEngineId = if (speechRecognizer != null) engineId else null
            Log.d(TAG, "SpeechRecognizer initialized")
        }
    }

    /**
     * Updates the input field with partial speech recognition results in real-time.
     * Uses setComposingText to show text as "being composed" which can be updated seamlessly.
     * Applies basic capitalization (first letter only) to partial text.
     */
    private fun updatePartialSpeechText(text: String) {
        Handler(Looper.getMainLooper()).post {
            val inputConnection = inputConnectionProvider() ?: return@post

            try {
                // If the recognizer started a NEW utterance after a pause (within one session),
                // commit the previous composing text and separate with a space, so the new
                // utterance appends at the cursor instead of overwriting the previous sentence.
                if (isComposingPartialText && looksLikeNewUtterance(lastPartialText, text)) {
                    inputConnection.finishComposingText()
                    isComposingPartialText = false
                    val before = inputConnection.getTextBeforeCursor(1, 0)
                    if (before != null && before.isNotEmpty() && before.last().isLetterOrDigit()) {
                        inputConnection.commitText(" ", 1)
                    }
                }
                lastPartialText = text

                // Apply basic capitalization to partial text (only first letter, not sentence endings)
                var formatted = text
                if (formatted.isNotEmpty() && !shouldDisableAutoCapitalize()) {
                    val shouldCapitalizeFirst = AutoCapitalizeHelper.shouldAutoCapitalizeAtCursor(
                        context = context,
                        inputConnection = inputConnection,
                        shouldDisableAutoCapitalize = shouldDisableAutoCapitalize()
                    ) && SettingsManager.getAutoCapitalizeFirstLetter(context)
                    
                    if (shouldCapitalizeFirst) {
                        formatted = formatted.replaceFirstChar { 
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) 
                            else it.toString() 
                        }
                    }
                }
                
                // Use setComposingText to show partial text as "being composed"
                // Offset 0 replaces any existing composing text
                inputConnection.setComposingText(formatted, 0)
                isComposingPartialText = true
                Log.d(TAG, "Partial text updated (composing): '$formatted'")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating partial text", e)
            }
        }
    }

    /**
     * Commits one final recognition result (a whole one-shot request, or one segment of a
     * segmented session) into the field.
     */
    private fun commitRecognizedText(results: Bundle) {
        val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidenceScores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        Log.d(TAG, "Speech recognition results: ${matches?.size ?: 0} matches")
        matches?.forEachIndexed { index, match ->
            val confidence = confidenceScores?.getOrNull(index)
            Log.d(TAG, "  Result[$index]: '$match' (confidence: $confidence)")
        }

        val text = matches?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
            val normalizedText = normalizePunctuationWords(text)
            val formattedText = formatTextWithAutoCapitalization(normalizedText)
            Log.d(TAG, "Using recognized text: '$formattedText' (original: '$text', normalized: '$normalizedText')")
            replacePartialWithFinalText(formattedText)
        } else {
            if (isComposingPartialText) {
                clearPartialText()
            }
            Log.w(TAG, "No text recognized")
        }
    }

    /**
     * Replaces partial composing text with the final normalized result.
     * Adds spacing rules:
     * - Always adds a space at the end
     * - Adds a space at the beginning if the text before cursor ends with a letter
     */
    private fun replacePartialWithFinalText(finalText: String) {
        Handler(Looper.getMainLooper()).post {
            val inputConnection = inputConnectionProvider() ?: return@post
            
            try {
                var textToCommit = finalText
                
                // Check if we need to add a space at the beginning
                // Read a reasonable amount of text before cursor to check context
                val textBeforeCursor = inputConnection.getTextBeforeCursor(10, 0)
                if (textBeforeCursor != null && textBeforeCursor.isNotEmpty()) {
                    val lastChar = textBeforeCursor.last()
                    // If the last character before cursor is a letter, add space before
                    if (lastChar.isLetter()) {
                        textToCommit = " $textToCommit"
                        Log.d(TAG, "Added space before text (previous char was letter: '$lastChar')")
                    }
                }
                
                // Always add a space at the end
                textToCommit += " "
                
                // If we're composing partial text, replace it directly with final text using setComposingText + commit
                if (isComposingPartialText) {
                    // First set the final text as composing text (this replaces the partial text)
                    inputConnection.setComposingText(textToCommit, 1)
                    // Then finish composing to commit it (this commits the final text and removes composing state)
                    inputConnection.finishComposingText()
                    isComposingPartialText = false
                    Log.d(TAG, "Final text committed (replaced partial): '$textToCommit'")
                } else {
                    // No partial text, just insert the final text
                    inputConnection.commitText(textToCommit, 1)
                    Log.d(TAG, "Final text inserted: '$textToCommit'")
                }
                lastPartialText = ""
            } catch (e: Exception) {
                Log.e(TAG, "Error replacing with final text", e)
            }
        }
    }

    /**
     * Clears any partial composing text.
     */
    private fun clearPartialText() {
        Handler(Looper.getMainLooper()).post {
            val inputConnection = inputConnectionProvider() ?: return@post
            
            try {
                if (isComposingPartialText) {
                    inputConnection.finishComposingText()
                    isComposingPartialText = false
                    Log.d(TAG, "Partial text cleared")
                }
                lastPartialText = ""
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing partial text", e)
            }
        }
    }

    /**
     * Starts voice input using SpeechRecognizer.
     */
    fun startRecognition() {
        // Check if RECORD_AUDIO permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            onError?.invoke(context.getString(R.string.speech_recognition_error_permission))
            return
        }

        lastPartialText = ""
        ensureSpeechRecognizer()
        
        if (speechRecognizer == null) {
            Log.e(TAG, "Cannot start speech recognition: SpeechRecognizer not available")
            onError?.invoke(context.getString(R.string.speech_recognition_error_not_available))
            return
        }
        
        try {
            val deviceLocale = context.resources.configuration.locales[0]
            val imeSubtypeLocale = context
                .getSystemService(InputMethodManager::class.java)
                ?.currentInputMethodSubtype
                ?.localeString()
            val languageTag = buildRecognitionLanguageTag(
                imeSubtypeLocale = imeSubtypeLocale,
                deviceLocale = deviceLocale
            )

            Log.d(
                TAG,
                "Speech locale source: subtype=$imeSubtypeLocale, device=${deviceLocale?.toLanguageTag()}, using=$languageTag"
            )
            recognitionLanguageTag = languageTag
            cancelSilenceTimer()
            cancelSegmentedWatchdog()
            sessionActive = true
            sessionCueStarted = false
            stopRequested = false
            continuationStartedAt = 0L
            segmentedSession = segmentedSessionSupported()
            segmentsSeen = 0
            sessionStartedAt = SystemClock.uptimeMillis()
            val intent = buildRecognizerIntent()

            Log.d(TAG, "Starting speech recognition with language: $languageTag (segmented=$segmentedSession)")
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Speech recognition started via SpeechRecognizer")
        } catch (e: SecurityException) {
            sessionActive = false
            Log.e(TAG, "Security exception starting speech recognition - permission denied", e)
            onError?.invoke(context.getString(R.string.speech_recognition_error_permission))
        } catch (e: Exception) {
            sessionActive = false
            Log.e(TAG, "Unable to start speech recognition", e)
            onError?.invoke(context.getString(R.string.speech_recognition_error_generic))
        }
    }

    private var recognitionLanguageTag: String? = null

    private fun buildRecognizerIntent(segmented: Boolean = segmentedSession): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                recognitionLanguageTag?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.speech_recognition_prompt))
                // Masking defaults to true in the recognizer, and keyboard-level Google voice
                // typing settings do not apply to SpeechRecognizer sessions.
                putExtra(
                    RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS,
                    SettingsManager.getDictationMaskOffensive(context)
                )
                // End-of-speech pause. In a one-shot request the recognizer treats these as
                // hints and may ignore them; in a segmented session the complete-silence value
                // below is what actually ends the session.
                val endSilenceMs = SettingsManager.getDictationEndSilenceMs(context)
                if (endSilenceMs > 0) {
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, endSilenceMs)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, endSilenceMs)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (SettingsManager.getDictationAutoPunctuation(context)) {
                        putExtra(
                            RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                            RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY
                        )
                    }
                    if (segmented) {
                        // One long session that the engine ends on the complete-silence length
                        // above, instead of us restarting after every result.
                        putExtra(
                            RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS
                        )
                    }
                }
            }

    /**
     * After a quiet continuation the recognizer stopped on its own; if our pause timer is
     * still pending, listen again so speech resuming inside the window is captured.
     */
    private fun continueSessionIfTimerPending() {
        if (!sessionActive) return
        if (mainHandler.hasCallbacks(silenceRunnable)) {
            continueSession()
        } else {
            endSession(cancelRecognizer = false)
        }
    }

    /**
     * Stops voice input if active.
     */
    fun stopRecognition() {
        // Explicit stop: let the in-flight utterance finish (onResults commits it and then
        // ends the session), but don't continue afterwards.
        stopRequested = true
        cancelSilenceTimer()
        speechRecognizer?.stopListening()
        // A segmented engine answers stopListening() with onEndOfSegmentedSession; if it stays
        // quiet the watchdog closes the session rather than leaving the mic latched on.
        if (segmentedSession) armSegmentedWatchdog()
        Log.d(TAG, "Speech recognition stopped")
    }

    /**
     * Destroys the SpeechRecognizer instance.
     */
    fun destroy() {
        cancelSilenceTimer()
        cancelSegmentedWatchdog()
        sessionActive = false
        sessionCueStarted = false
        stopRequested = false
        segmentedSession = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        activeEngineId = null
        // Clear any partial text
        if (isComposingPartialText) {
            clearPartialText()
        }
        Log.d(TAG, "SpeechRecognizer destroyed")
    }

    // A stop cue is only played for a session that played a start cue, so error callbacks
    // arriving after the results callback cannot vibrate twice.
    private var stopCuePending = false

    // ---- Continuous session ----
    // Google's recognizer treats the end-of-speech extras as hints and ends a request after
    // roughly a second of silence regardless. To honour the user's pause we keep the session
    // open ourselves: after each result we restart listening and only end the session when
    // our own silence timer (the configured pause) expires without new speech, when the user
    // stops explicitly, or on a real error.
    private val mainHandler = Handler(Looper.getMainLooper())

    // ---- Engine + segmented session ----
    // Android 13 can hold one session open and end it on our silence length itself, which is what
    // the restart loop above is imitating. Engines are free to ignore the request, so the loop
    // stays as the fallback and [segmentedUnsupported] latches once an engine proves it needs it.
    private var activeEngineId: String? = null
    private var segmentedSession = false
    private var segmentsSeen = 0
    private var segmentedUnsupported = false
    private var sessionStartedAt = 0L

    private val segmentedWatchdogRunnable = Runnable {
        // An engine that delivered segments clearly understands the mode, so a single slow close
        // is not evidence against it; only one that never segmented at all gets struck off.
        val neverSegmented = segmentsSeen == 0
        Log.w(
            TAG,
            "Segmented session never ended itself (segments=$segmentsSeen) — " +
                if (neverSegmented) "falling back to the restart loop" else "closing it here"
        )
        if (neverSegmented) segmentedUnsupported = true
        endSession(cancelRecognizer = true)
    }

    private fun segmentedSessionSupported(): Boolean = shouldUseSegmentedSession(
        sdkInt = Build.VERSION.SDK_INT,
        userEnabled = SettingsManager.getDictationContinuousSession(context),
        engineRefusedSegments = segmentedUnsupported,
        pauseMs = configuredPauseMs()
    )

    /** Armed only while the engine should be closing the session — never during live speech. */
    private fun armSegmentedWatchdog() {
        mainHandler.removeCallbacks(segmentedWatchdogRunnable)
        if (!segmentedSession || !sessionActive) return
        mainHandler.postDelayed(
            segmentedWatchdogRunnable,
            configuredPauseMs() + SEGMENTED_WATCHDOG_SLACK_MS
        )
    }

    private fun cancelSegmentedWatchdog() {
        mainHandler.removeCallbacks(segmentedWatchdogRunnable)
    }

    /** Re-runs the current session as a plain request after the engine refused segments. */
    private fun restartWithoutSegmentedSession() {
        mainHandler.post {
            if (!sessionActive) return@post
            continuationStartedAt = 0L
            runCatching { speechRecognizer?.startListening(buildRecognizerIntent(segmented = false)) }
                .onFailure {
                    Log.w(TAG, "Unable to restart without a segmented session", it)
                    endSession(cancelRecognizer = false)
                }
        }
    }

    private var sessionActive = false
    private var sessionCueStarted = false
    private var stopRequested = false
    private var continuationStartedAt = 0L
    private val silenceRunnable = Runnable {
        Log.d(TAG, "Silence timer expired — ending session")
        endSession(cancelRecognizer = true)
    }

    private fun configuredPauseMs(): Int = SettingsManager.getDictationEndSilenceMs(context)

    private fun armSilenceTimer() {
        mainHandler.removeCallbacks(silenceRunnable)
        val pause = configuredPauseMs()
        if (pause <= 0) return
        // The recognizer has already waited ~1s of silence before delivering the result.
        val remaining = (pause - RECOGNIZER_INTERNAL_SILENCE_MS).coerceAtLeast(MIN_SILENCE_TIMER_MS)
        mainHandler.postDelayed(silenceRunnable, remaining.toLong())
    }

    private fun cancelSilenceTimer() {
        mainHandler.removeCallbacks(silenceRunnable)
    }

    /** Ends the continuous session once: UI state, stop cue, timers. */
    private fun endSession(cancelRecognizer: Boolean) {
        cancelSilenceTimer()
        cancelSegmentedWatchdog()
        if (!sessionActive) return
        sessionActive = false
        sessionCueStarted = false
        stopRequested = false
        segmentedSession = false
        segmentsSeen = 0
        if (cancelRecognizer) {
            runCatching { speechRecognizer?.cancel() }
            if (isComposingPartialText) clearPartialText()
        }
        onRecognitionStateChanged?.invoke(false)
        playHapticCue(started = false)
    }

    /** Restart listening for the next utterance within the same session. */
    private fun continueSession() {
        if (!sessionActive || stopRequested) return
        continuationStartedAt = SystemClock.uptimeMillis()
        mainHandler.post {
            if (!sessionActive) return@post
            runCatching { speechRecognizer?.startListening(buildRecognizerIntent()) }
                .onFailure {
                    Log.w(TAG, "Unable to continue session", it)
                    endSession(cancelRecognizer = false)
                }
        }
    }

    private fun playHapticCue(started: Boolean) {
        if (!SettingsManager.getDictationHapticsEnabled(context)) return
        if (started) {
            stopCuePending = true
        } else {
            if (!stopCuePending) return
            stopCuePending = false
        }
        DictationHaptics.play(
            context = context,
            strength = SettingsManager.getDictationHapticStrength(context),
            started = started
        )
    }
}
