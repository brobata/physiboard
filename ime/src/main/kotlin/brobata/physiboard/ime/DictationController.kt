package brobata.physiboard.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.ContextCompat
import brobata.physiboard.core.speech.CuePattern
import brobata.physiboard.core.speech.DictationCues
import brobata.physiboard.core.speech.DictationEffect
import brobata.physiboard.core.speech.DictationEngine
import brobata.physiboard.core.speech.DictationEvent
import brobata.physiboard.core.speech.DictationMode
import brobata.physiboard.core.speech.DictationSession
import brobata.physiboard.core.speech.DictationSettings
import brobata.physiboard.core.speech.DictationStartFailureReason
import brobata.physiboard.core.speech.DictationTextSettings
import brobata.physiboard.core.speech.LanguageTagResolver
import brobata.physiboard.core.speech.RecognizerRequestOptions
import brobata.physiboard.core.speech.RecognizerResolution
import brobata.physiboard.core.speech.RecognizerTarget
import java.util.Locale

/**
 * The Android side of dictation: drives a real [SpeechRecognizer], translates its callbacks into
 * [DictationEvent]s, applies the [brobata.physiboard.core.speech.DictationTextOp]s
 * [DictationEngine] returns through the existing editor path ([applyDictationTextOps]), and handles
 * the microphone permission (spec: dictation.md SS10). It decides nothing about the session itself;
 * every rule that answers "what should happen" lives in [DictationEngine].
 *
 * [trigger] is the one entry point a future key binding (out of scope for this task, see the
 * module's own task instructions) or any other caller would invoke; this class does not bind it to
 * a key itself.
 */
internal class DictationController(
    private val service: InputMethodService,
    private val currentInputConnection: () -> InputConnection?,
) {
    private var session: DictationSession? = null
    private var recognizer: SpeechRecognizer? = null
    /** The `dictation_engine` value [recognizer] was built for; drives the rebuild rule of spec SS2.6 step 4. */
    private var recognizerEngineId: String? = null
    private var segmentedRefusalLatch: Boolean = false
    private var startPendingOwnerPackage: String? = null

    /** SPEC GAP / missing module: no `:settings` module yet, same as [KeyboardSession]'s own several such gaps; shipped defaults until one exists. */
    var settings: DictationSettings = DictationSettings(androidApiLevel = Build.VERSION.SDK_INT)
    var textSettings: DictationTextSettings = DictationTextSettings()

    /** spec SS5.1 step 1: the active input style's locale string, kept current by [KeyboardSession.applySettings]; null falls straight to the device locale (SS5.1 step 2). */
    var subtypeLanguageTag: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = Runnable { dispatch(DictationEvent.ClockTick) }

    /** spec SS10: answers [DictationPermissionActivity]'s package-restricted broadcast pair. */
    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val granted = intent.action == DictationPermissionActivity.ACTION_GRANTED
            val pendingOwner = startPendingOwnerPackage
            startPendingOwnerPackage = null
            if (granted) trigger(pendingOwner)
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(DictationPermissionActivity.ACTION_GRANTED)
            addAction(DictationPermissionActivity.ACTION_DENIED)
        }
        runCatching { ContextCompat.registerReceiver(service, permissionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    val isActive: Boolean get() = session?.active == true

    /**
     * The recognizer's audio level reports, for the strip's microphone button colour. spec:
     * status-bar.md SS6.1, "on every audio level report, a red between (128, 0, 0) and
     * (255, 50, 50)". Set by [KeyboardSession]; null when nothing on screen wants the level.
     */
    var onAudioLevel: ((Float) -> Unit)? = null

    /**
     * Fires when [isActive] flips, so the strip can re-render the microphone. Without it the
     * button turned red on the trigger and stayed red after the session ended, because nothing
     * asked the strip to look again (Titan, 2026-09-25).
     */
    var onActiveChanged: ((Boolean) -> Unit)? = null

    // -----------------------------------------------------------------------------------------
    // The trigger. spec: dictation.md SS2, SS10.
    // -----------------------------------------------------------------------------------------

    fun trigger(ownerPackage: String?) {
        if (hasMicPermission()) {
            dispatch(DictationEvent.Trigger(ownerPackage, currentTextBeforeCursor()))
            return
        }
        // spec SS2.6 step 1 / SS10: remember the pending start, open the permission activity, and
        // resume once the answer comes back over [permissionReceiver]. A trigger that arrives with
        // a session already active needs no permission (it is asking to stop, and the mic is
        // already open), but a session can only be active once a request has actually started,
        // which itself required the permission, so this branch is reached only when nothing is
        // running yet.
        startPendingOwnerPackage = ownerPackage
        val intent = Intent(service, DictationPermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        runCatching { service.startActivity(intent) }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // -----------------------------------------------------------------------------------------
    // Editor lifecycle. spec SS3.
    // -----------------------------------------------------------------------------------------

    fun onEditorFieldClosed() {
        if (session != null) dispatch(DictationEvent.EditorFieldClosed)
    }

    fun onEditorFieldOpened(ownerPackage: String?) {
        if (session != null) dispatch(DictationEvent.EditorFieldOpened(ownerPackage))
    }

    /**
     * spec: the c440844 fix, [brobata.physiboard.core.speech.PendingUtterance]'s own KDoc. Called
     * when the ordinary typing pipeline actually changed the field's text while a dictation
     * session is running (never for a bare modifier press, a key-up or a Fn repeat, which edit
     * nothing): that is the user changing the field by some means other than the dictation
     * session itself, so whatever the engine remembers of this utterance can no longer be trusted.
     */
    fun onUserEditedComposingText() {
        if (session != null) dispatch(DictationEvent.UserEditedComposingText)
    }

    fun onEditorRejectedInsert() {
        if (session != null) dispatch(DictationEvent.EditorRejectedInsert)
    }

    /** spec SS3: "Keyboard service destroyed: timers cancelled, recognizer destroyed, partial cleared; no session-end bookkeeping." */
    fun onServiceDestroyed() {
        if (isActive) runCatching { onActiveChanged?.invoke(false) }
        handler.removeCallbacks(clockRunnable)
        runCatching { service.unregisterReceiver(permissionReceiver) }
        runCatching { recognizer?.destroy() }
        recognizer = null
        session = null
    }

    // -----------------------------------------------------------------------------------------
    // The state machine boundary.
    // -----------------------------------------------------------------------------------------

    private fun dispatch(event: DictationEvent) {
        val wasActive = isActive
        val outcome = DictationEngine.handle(session, event, now(), settings, textSettings, segmentedRefusalLatch)
        session = outcome.session
        if (isActive != wasActive) runCatching { onActiveChanged?.invoke(isActive) }
        // spec SS6.3: the fourth finding's other half. Since SS5's segmented-session request extra
        // is unverified device-side, the one thing this code can guarantee is that the mode actually
        // driving the session's timers is visible in the log, both when it is decided and if it ever
        // flips underneath a session that thought it was segmented.
        outcome.newSegmentedRefusalLatch?.let {
            segmentedRefusalLatch = it
            if (it) DiagnosticLog.i(TAG) { "segmented mode refused by the engine; falling back to restart-loop" }
        }
        // spec SS3: "The field rejected an insert (exception while writing)": the one write this
        // whole feature makes that can throw (a hostile or misbehaving editor), so it is the one
        // write wrapped; a failure here re-enters this same function once with EditorRejectedInsert,
        // safe because this class only ever runs on the main thread the IME already serialises on.
        val wroteCleanly = runCatching { currentInputConnection()?.applyDictationTextOps(outcome.textOps) }.isSuccess
        outcome.effects.forEach(::applyEffect)
        rescheduleClock()
        if (!wroteCleanly) onEditorRejectedInsert()
    }

    private fun applyEffect(effect: DictationEffect) {
        when (effect) {
            is DictationEffect.StartListening -> startListening(effect.mode)
            DictationEffect.StopListening -> runCatching { recognizer?.stopListening() }
            DictationEffect.CancelListening -> runCatching { recognizer?.cancel() }
            DictationEffect.PlayStartCue -> playCue(isStart = true)
            DictationEffect.PlayStopCue -> playCue(isStart = false)
            // spec SS6.6, SS3: the message every session-ending error computes, shown the same way
            // the rest of :ime already surfaces user-facing feedback (LauncherKeysController,
            // CommandExecutor, TrackpadOverlayController).
            is DictationEffect.ShowMessage -> toast(effect.message.text)
            // spec SS2.6 steps 4 and 7: a start failure's message is log-only, never shown.
            is DictationEffect.LogMessage -> DiagnosticLog.i(TAG) { "start failed: ${effect.message.text}" }
        }
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(service, text, Toast.LENGTH_SHORT).show() }
    }

    private fun rescheduleClock() {
        handler.removeCallbacks(clockRunnable)
        val deadline = session?.nextDeadlineMs ?: return
        handler.postDelayed(clockRunnable, (deadline - now()).coerceAtLeast(0))
    }

    private fun now(): Long = SystemClock.uptimeMillis()

    private fun currentTextBeforeCursor(): String? =
        runCatching { currentInputConnection()?.getTextBeforeCursor(TEXT_BEFORE_SESSION_WINDOW, 0)?.toString() }.getOrNull()

    // -----------------------------------------------------------------------------------------
    // The recognizer. spec SS4.2 (resolution is out of scope: no engine picker/settings), SS5.
    // -----------------------------------------------------------------------------------------

    private fun startListening(mode: DictationMode) {
        // spec SS6.3, SS5: the mode actually in force, logged at the one point every request of
        // every session passes through, so a phone session's log can say whether a given cutoff
        // happened in segmented or restart-loop mode without needing a debugger attached.
        DiagnosticLog.i(TAG) { "start listening mode=$mode pauseMs=${settings.pauseMs}" }
        val speechRecognizer = ensureRecognizer()
        if (speechRecognizer == null) {
            // spec SS2.6 step 4: "If the platform reports that speech recognition is unavailable
            // ... 'Speech recognition not available.'" (also reached when recognizer creation itself
            // fails, per SS4.2's "any creation failure" row bottoming out here).
            dispatch(DictationEvent.StartFailed(DictationStartFailureReason.RECOGNITION_UNAVAILABLE))
            return
        }
        // spec SS2.6 step 7: "A security failure or any other failure at this point ... reports
        // 'Microphone permission denied.' or 'Speech recognition error.'" A SecurityException here
        // is the permission race the trigger's own check cannot fully close: granted at trigger
        // time, revoked before this call actually reaches the recognizer.
        runCatching { speechRecognizer.startListening(buildRecognizerIntent(mode)) }
            .onFailure { error ->
                val reason = if (error is SecurityException) {
                    DictationStartFailureReason.SECURITY_FAILURE
                } else {
                    DictationStartFailureReason.OTHER_FAILURE
                }
                dispatch(DictationEvent.StartFailed(reason))
            }
    }

    /**
     * spec SS2.6 step 4, SS4.2: makes sure a recognizer exists for `dictation_engine`'s stored id.
     * "If a recognizer exists for a different id, it is destroyed and the 'engine refuses
     * segmented sessions' latch is cleared." "Any creation failure" falls to the system default;
     * if that fails too, there is no recognizer and the caller reports the start failure.
     */
    private fun ensureRecognizer(): SpeechRecognizer? {
        val engineId = settings.engineId
        recognizer?.let { if (recognizerEngineId == engineId) return it }
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
        recognizerEngineId = null
        segmentedRefusalLatch = false
        if (!SpeechRecognizer.isRecognitionAvailable(service)) return null
        val onDeviceAvailable = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(service) }.getOrDefault(false)
        val target = RecognizerResolution.resolve(engineId, onDeviceAvailable, ::isRecognitionServiceInstalled)
        val created = createRecognizer(target) ?: if (target != RecognizerTarget.SystemDefault) createRecognizer(RecognizerTarget.SystemDefault) else null
        recognizer = created
        recognizerEngineId = if (created != null) engineId else null
        return created
    }

    private fun createRecognizer(target: RecognizerTarget): SpeechRecognizer? = runCatching {
        val created = when (target) {
            RecognizerTarget.SystemDefault -> SpeechRecognizer.createSpeechRecognizer(service)
            RecognizerTarget.OnDevice -> SpeechRecognizer.createOnDeviceSpeechRecognizer(service)
            is RecognizerTarget.Component -> {
                val component = ComponentName.unflattenFromString(target.flattenedName) ?: return@runCatching null
                SpeechRecognizer.createSpeechRecognizer(service, component)
            }
        }
        created?.apply { setRecognitionListener(recognitionListener) }
    }.getOrNull()

    /** spec SS4.2's "`package/class` still installed as a recognition service" test. */
    private fun isRecognitionServiceInstalled(flattenedName: String): Boolean {
        val component = ComponentName.unflattenFromString(flattenedName) ?: return false
        return runCatching {
            service.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0).any {
                val info = it.serviceInfo ?: return@any false
                info.packageName == component.packageName && info.name == component.className
            }
        }.getOrDefault(false)
    }

    /** spec SS5, SS5.1. */
    private fun buildRecognizerIntent(mode: DictationMode): Intent {
        val pauseMs = settings.pauseMs
        // spec SS4.2: masking follows `dictation_mask_offensive`; formatting is asked for only on
        // Android 13 or later with `dictation_auto_punctuation` on. `:core:speech` decides both.
        val options = RecognizerRequestOptions.from(settings)
        val languageTag = LanguageTagResolver.resolve(subtypeLanguageTag, Locale.getDefault().toLanguageTag())
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, service.packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, options.maskOffensive)
            if (pauseMs > 0L) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, pauseMs)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (options.enableFormatting) putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
                // SPEC GAP: the exact segmented-session extra key/shape needs device evidence (D-series
                // facts in dictation.md are all Titan measurements this task's clean-room rule forbids
                // rederiving from the old source); EXTRA_SEGMENTED_SESSION keyed to the pause is the
                // platform's documented shape and is what SS5's "segmented-session option keyed to the
                // complete-silence length" describes.
                if (mode == DictationMode.SEGMENTED) {
                    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, pauseMs)
                }
            }
        }
    }

    /** Translates the recognizer's real callbacks into [DictationEvent]s; decides nothing itself. */
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = dispatch(DictationEvent.ReadyForSpeech)
        override fun onBeginningOfSpeech() = dispatch(DictationEvent.BeginningOfSpeech)
        override fun onRmsChanged(rmsdB: Float) {
            onAudioLevel?.invoke(rmsdB)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = dispatch(DictationEvent.EndOfSpeech)
        override fun onError(error: Int) = dispatch(DictationEvent.Error(error))
        override fun onResults(results: Bundle?) = dispatch(DictationEvent.FinalResult(firstResult(results)))
        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { dispatch(DictationEvent.PartialResult(it)) }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onSegmentResults(segmentResults: Bundle) {
            firstResult(segmentResults)?.let { dispatch(DictationEvent.SegmentResult(it)) }
        }
        override fun onEndOfSegmentedSession() = dispatch(DictationEvent.SegmentedSessionEnded)
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    // -----------------------------------------------------------------------------------------
    // Cues. spec SS8.1: a plain vibration, never the notification channel, "because notification
    // vibration being off must not silence typing feedback" (the same rule KeyboardSession's own
    // performHaptic already follows for ordinary typing).
    // -----------------------------------------------------------------------------------------

    private val vibrator: Vibrator? by lazy {
        runCatching { (service.getSystemService(InputMethodService.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator }.getOrNull()
    }

    /** spec SS8.1: `dictation_haptics` and the system toggle both gate; the pattern is the strength's row of the cue table. */
    private fun playCue(isStart: Boolean) {
        if (!DictationCues.shouldPlay(settings.hapticsEnabled, systemHapticsEnabled())) return
        val pattern = if (isStart) DictationCues.startCue(settings.hapticStrength) else DictationCues.stopCue(settings.hapticStrength)
        runCatching { vibrator?.vibrate(waveform(pattern)) }
    }

    /** spec SS8.1: "the system's own haptic feedback toggle (`Settings.System` key `haptic_feedback_enabled`, read as on when unreadable)". */
    private fun systemHapticsEnabled(): Boolean = runCatching {
        android.provider.Settings.System.getInt(service.contentResolver, android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    }.getOrDefault(true)

    private fun waveform(pattern: CuePattern): VibrationEffect =
        VibrationEffect.createWaveform(pattern.timingsMs.toLongArray(), pattern.amplitudes.toIntArray(), -1)

    private companion object {
        const val TEXT_BEFORE_SESSION_WINDOW = 240
        const val TAG = "PhysiBoardDictation"
    }
}
