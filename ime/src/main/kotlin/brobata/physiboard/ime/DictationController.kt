package brobata.physiboard.ime

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.AppOpsManager
import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.ContextCompat
import brobata.physiboard.core.speech.AudioFocusChange
import brobata.physiboard.core.speech.CuePattern
import brobata.physiboard.core.speech.DictationCues
import brobata.physiboard.core.speech.DictationEffect
import brobata.physiboard.core.speech.DictationEngine
import brobata.physiboard.core.speech.DictationEvent
import brobata.physiboard.core.speech.DictationPhase
import brobata.physiboard.core.speech.DictationSession
import brobata.physiboard.core.speech.DictationSettings
import brobata.physiboard.core.speech.DictationStartFailureReason
import brobata.physiboard.core.speech.DictationTextSettings
import brobata.physiboard.core.speech.DirectCommit
import brobata.physiboard.core.speech.DirectCommitState
import brobata.physiboard.core.speech.DictationPartialDisplay
import brobata.physiboard.core.speech.LanguageTagResolver
import brobata.physiboard.core.speech.PendingUtterance
import brobata.physiboard.core.speech.RecognizerRequest
import brobata.physiboard.core.speech.SessionAudioRoute
import brobata.physiboard.core.speech.RecognizerResolution
import brobata.physiboard.core.speech.RecognizerTarget
import java.util.Locale

/**
 * The Android side of dictation: drives a real [SpeechRecognizer], translates its callbacks into
 * [DictationEvent]s, applies the [brobata.physiboard.core.speech.DictationTextOp]s
 * [DictationEngine] returns through the existing editor path ([applyDictationTextOps]), holds
 * audio focus for the session (spec: dictation.md SS6.7) and handles the microphone permission
 * (SS10). It decides nothing about the session itself; every rule that answers "what should
 * happen" lives in [DictationEngine].
 *
 * [trigger] is the entry point the Fn burst reaches (keys-and-modifiers.md SS3.3); [onKeyDown] is
 * what every other key reaches first while a session runs (SS3).
 */
internal class DictationController(
    private val service: InputMethodService,
    private val currentInputConnection: () -> InputConnection?,
) {
    private var session: DictationSession? = null
    private var recognizer: SpeechRecognizer? = null
    /** The `dictation_engine` value [recognizer] was built for; drives the rebuild rule of spec SS2.6 step 4. */
    private var recognizerEngineId: String? = null

    /**
     * spec SS6.3: "the refusal latch is set (segmented mode is not asked for again until the
     * engine setting changes)". Kept here, per engine id, because the recognizer object itself is
     * rebuilt for every session (see [releaseRecognizer]) and must not take the latch with it.
     */
    private var segmentedRefusalLatch: Boolean = false
    private var latchEngineId: String = ""
    private var startPendingOwnerPackage: String? = null

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

    /** A session exists: starting, listening or stopping. The strip's microphone, the status icon and the key hook all read this. */
    val isActive: Boolean get() = session != null

    /**
     * spec SS3: the session has put text into this field, finished or still composing, so the
     * field going empty under it is the app's doing. In a field that cannot hold a composing
     * region (DirectCommit) a partial is not in the field yet; [composingAllowed] gates the caller.
     */
    val hasTextInFieldThisSession: Boolean
        get() = session?.utterance?.let { it.finishedThisSession.isNotEmpty() || it.pending is PendingUtterance.Live } == true

    /** The composing partial as the field shows it (capitalised like [DictationPartialDisplay] did), for [FieldVerdict] checks; null when nothing is composing. */
    val composingTextInField: String?
        get() {
            val utterance = session?.utterance ?: return null
            val live = utterance.pending as? PendingUtterance.Live ?: return null
            return DictationPartialDisplay.display(live.text, utterance.context, textSettings)
        }

    /** spec SS3: the app emptied the field itself (a send); the session ends and nothing later lands. */
    fun onFieldClearedByApp() {
        if (session != null) dispatch(DictationEvent.FieldClearedByApp)
    }

    /**
     * spec SS3, SS7.4: before a result is applied over a composing partial, [KeyboardSession] is
     * asked whether the field still holds that partial. Emptied: the session ends. Changed: the
     * utterance is dead, the result writes nothing. Intact or unknown: the result applies.
     */
    var verifyField: (() -> FieldVerdict)? = null

    /** Fires when the session ended because the app emptied the field, so a press meant to stop it is not taken as a start. */
    var onEndedByApp: (() -> Unit)? = null

    private fun beforeResult() {
        if (composingTextInField == null || !composingAllowed) return
        when (runCatching { verifyField?.invoke() }.getOrNull() ?: FieldVerdict.UNKNOWN) {
            FieldVerdict.CLEARED -> dispatch(DictationEvent.FieldClearedByApp)
            FieldVerdict.CHANGED -> dispatch(DictationEvent.UserEditedComposingText)
            FieldVerdict.INTACT, FieldVerdict.UNKNOWN -> Unit
        }
    }

    /**
     * The recognizer's audio level reports, for the strip's microphone button colour. spec:
     * status-bar.md SS6.1, "on every audio level report, a red between (128, 0, 0) and
     * (255, 50, 50)". Set by [KeyboardSession]; null when nothing on screen wants the level.
     */
    var onAudioLevel: ((Float) -> Unit)? = null

    /**
     * spec SS6.8: the session needs the keyboard to count as shown for the system, or the
     * microphone is silently fed zeros (D22). [KeyboardSession] owns the window and answers these.
     */
    var onHoldImeVisible: (() -> Unit)? = null
    var onReleaseImeVisible: (() -> Unit)? = null

    /**
     * Fires when [isActive] flips, so the strip and the status icon can re-render. Without it the
     * button turned red on the trigger and stayed red after the session ended, because nothing
     * asked the strip to look again (Titan, 2026-09-25).
     */
    var onActiveChanged: ((Boolean) -> Unit)? = null

    // -----------------------------------------------------------------------------------------
    // The trigger and the keys. spec: dictation.md SS2, SS3, SS10.
    // -----------------------------------------------------------------------------------------

    fun trigger(ownerPackage: String?) {
        if (session != null || hasMicPermission()) {
            val route = if (session == null) audioWatch.currentRoute().also { sessionRoute = it } else SessionAudioRoute.LOCAL
            dispatch(DictationEvent.Trigger(ownerPackage, currentTextBeforeCursor(), route))
            return
        }
        // spec SS2.6 step 1 / SS10: remember the pending start, open the permission activity, and
        // resume once the answer comes back over [permissionReceiver].
        startPendingOwnerPackage = ownerPackage
        val intent = Intent(service, DictationPermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        runCatching { service.startActivity(intent) }
    }

    /**
     * spec SS3: a key other than a modifier went down while a session runs. [DictationEngine]
     * decides (by `dictation_stop_on_typing`) whether that stops the session; the key itself is
     * never consumed here and goes on to do its usual work after this returns.
     */
    fun onKeyDown() {
        if (session != null) dispatch(DictationEvent.KeyDown)
    }

    /**
     * app-shell.md SS31, dictation.md SS4.3: private mode keeps speech on the phone. Turning it on
     * while a session has already gone online ends that session; the next one plans offline.
     */
    fun onPrivateModeChanged(privateMode: Boolean) {
        if (settings.privateMode == privateMode) return
        settings = settings.copy(privateMode = privateMode)
        if (privateMode && session != null) dispatch(DictationEvent.PrivateModeTurnedOn)
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // -----------------------------------------------------------------------------------------
    // Editor lifecycle. spec SS3.
    // -----------------------------------------------------------------------------------------

    fun onEditorFieldClosed() {
        if (session != null) dispatch(DictationEvent.EditorFieldClosed)
    }

    /**
     * False for a field that will draw dictation's staged words and never adopt them. The
     * maintainer's web terminal did exactly that (2026-09-26): the sentence appeared inverted at
     * the caret and none of it was ever committed. Set from the field the keyboard just
     * classified; see [DirectCommit].
     */
    var composingAllowed: Boolean = true

    private var directCommit = DirectCommitState()

    fun onEditorFieldOpened(ownerPackage: String?) {
        if (session != null) dispatch(DictationEvent.EditorFieldOpened(ownerPackage))
    }

    /**
     * spec: the c440844 fix, [brobata.physiboard.core.speech.PendingUtterance]'s own KDoc. Called
     * when the ordinary typing pipeline actually changed the field's text while a dictation
     * session is still running (only possible with `dictation_stop_on_typing` off): whatever the
     * engine remembers of this utterance can no longer be trusted.
     */
    fun onUserEditedComposingText() {
        if (session != null) dispatch(DictationEvent.UserEditedComposingText)
    }

    fun onEditorRejectedInsert() {
        if (session != null) dispatch(DictationEvent.EditorRejectedInsert)
    }

    /** spec SS3: "Keyboard service destroyed: timers cancelled, recognizer destroyed, focus given back, partial cleared; no session-end bookkeeping." */
    fun onServiceDestroyed() {
        if (isActive) runCatching { onActiveChanged?.invoke(false) }
        handler.removeCallbacks(clockRunnable)
        cancelPendingStart()
        runCatching { service.unregisterReceiver(permissionReceiver) }
        runCatching { recognizer?.destroy() }
        recognizer = null
        session = null
        KeyboardActivity.dictationActive = false
        abandonAudioFocus()
        audioWatch.stop()
        DictationTrace.sessionEnded()
    }

    // -----------------------------------------------------------------------------------------
    // The car. spec SS6.10: the route the microphone takes and the calls that end a session.
    // -----------------------------------------------------------------------------------------

    /** The route [trigger] read for the session now starting, handed to the watch for its trace. */
    private var sessionRoute: SessionAudioRoute = SessionAudioRoute.LOCAL

    private val audioWatch = DictationAudioWatch(service, handler).apply {
        onRouteSettling = { if (session != null) dispatch(DictationEvent.InputRouteSettling) }
        onRouteSettled = { if (session != null) dispatch(DictationEvent.InputRouteSettled) }
        onCallStarted = { if (session != null) dispatch(DictationEvent.CallStarted) }
    }

    // -----------------------------------------------------------------------------------------
    // The state machine boundary.
    // -----------------------------------------------------------------------------------------

    private fun dispatch(event: DictationEvent) {
        val wasActive = isActive
        val engineId = settings.engineId
        if (engineId != latchEngineId) {
            // spec SS6.3: the latch belongs to one engine; a different engine starts unjudged.
            latchEngineId = engineId
            segmentedRefusalLatch = false
        }
        val outcome = DictationEngine.handle(session, event, now(), settings, textSettings, segmentedRefusalLatch)
        session = outcome.session
        KeyboardActivity.dictationActive = isActive
        if (isActive != wasActive) runCatching { onActiveChanged?.invoke(isActive) }
        if (wasActive && session == null && event == DictationEvent.FieldClearedByApp) runCatching { onEndedByApp?.invoke() }
        outcome.newSegmentedRefusalLatch?.let { segmentedRefusalLatch = it }
        // spec SS3: "The field rejected an insert (exception while writing)": the one write this
        // whole feature makes that can throw (a hostile or misbehaving editor), so it is the one
        // write wrapped; a failure here re-enters this same function once with EditorRejectedInsert,
        // safe because this class only ever runs on the main thread the IME already serialises on.
        val textOps = if (composingAllowed) {
            outcome.textOps
        } else {
            val translated = DirectCommit.translate(outcome.textOps, directCommit)
            directCommit = translated.state
            translated.ops
        }
        val wroteCleanly = runCatching { currentInputConnection()?.applyDictationTextOps(textOps) }.isSuccess
        // spec SS6.9: the trace latches whether this session is private when it starts.
        if (!wasActive && session != null) DictationTrace.sessionStarted()
        DictationTrace.dispatched(event, textOps, outcome.effects, session)
        outcome.effects.forEach(::applyEffect)
        // A recognizer kept between sessions goes stale: Android unbinds the remote speech
        // service while nothing is listening, and the next request reaches a dead connection
        // ("Connection to speech recognition service lost, but no #startListening has been
        // invoked yet", the maintainer's Titan, 2026-09-26: dictation took a long time to
        // start and then typed nothing). Every session gets a recognizer of its own; the
        // ending's own cancel (above) has already run against it by now.
        if (wasActive && session == null) {
            cancelPendingStart()
            releaseRecognizer()
            // Anything still staged belonged to the session that just ended; it must not be
            // written into whatever the next one says.
            directCommit = DirectCommitState()
        }
        rescheduleClock()
        // spec SS6.10: the audio around the session is watched (and traced) from its start to its
        // end. The start is posted to the front of the queue: it is some twenty calls into the
        // audio service, kept off the trigger key's own stroke, and still lands well before the
        // recognizer's first callback (a bind and a microphone open away).
        if (!wasActive && session != null) {
            val route = sessionRoute
            handler.postAtFrontOfQueue { if (session != null) audioWatch.start(route) }
        }
        if (wasActive && session == null) {
            audioWatch.stop()
            DictationTrace.sessionEnded()
        }
        if (!wroteCleanly) onEditorRejectedInsert()
    }

    private fun applyEffect(effect: DictationEffect) {
        when (effect) {
            is DictationEffect.StartListening -> startListeningOnceMicrophoneAllowed(effect.request)
            DictationEffect.StopListening -> {
                cancelPendingStart()
                runCatching { recognizer?.stopListening() }
            }
            DictationEffect.CancelListening -> {
                cancelPendingStart()
                runCatching { recognizer?.cancel() }
            }
            DictationEffect.HoldImeVisible -> runCatching { onHoldImeVisible?.invoke() }.onFailure { error -> Log.e(TAG, "hold IME visible crashed", error) }
            DictationEffect.ReleaseImeVisible -> runCatching { onReleaseImeVisible?.invoke() }.onFailure { error -> Log.e(TAG, "release IME visible crashed", error) }
            DictationEffect.AcquireAudioFocus -> requestAudioFocus()
            DictationEffect.ReleaseAudioFocus -> abandonAudioFocus()
            DictationEffect.PlayStartCue -> playCue(isStart = true)
            DictationEffect.PlayStopCue -> playCue(isStart = false)
            // spec SS6.6: the message every session-ending error computes, shown the same way
            // the rest of :ime already surfaces user-facing feedback.
            is DictationEffect.ShowMessage -> toast(effect.message.text)
            is DictationEffect.LogMessage -> DiagnosticLog.i(TAG) { effect.message.text }
        }
    }

    private fun toast(text: String) {
        runCatching { Toast.makeText(service, text, Toast.LENGTH_LONG).show() }
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
    // Audio focus. spec SS6.7.
    // -----------------------------------------------------------------------------------------

    private val audioManager: AudioManager? by lazy {
        runCatching { service.getSystemService(AudioManager::class.java) }.getOrNull()
    }

    private var focusRequest: AudioFocusRequest? = null

    /**
     * spec SS6.7: every change is traced with its likely source and handed to the engine, which
     * decides: a loss during a call ends the session; a media app taking the audio for good is
     * taken back once and then left playing; a transient loss (the recognizer's own request for
     * the same microphone session) is ignored, and focus comes back to this request when the
     * recognizer lets go, which keeps the music paused across the engine's internal restarts.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        audioWatch.traceFocusChange(change, ownRequestHeld = focusRequest != null)
        val mapped = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> AudioFocusChange.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> AudioFocusChange.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocusChange.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusChange.LOSS_TRANSIENT_CAN_DUCK
            else -> null
        } ?: return@OnAudioFocusChangeListener
        // A permanent loss takes this request off the system's focus stack: nothing is held any
        // more, and a retake must be a new request.
        if (mapped == AudioFocusChange.LOSS) focusRequest = null
        if (session != null) dispatch(DictationEvent.AudioFocusChanged(mapped, audioWatch.callActive()))
    }

    /**
     * spec SS6.7: exclusive transient focus for the whole session (the platform's documented use
     * for speech recognition), taken before the microphone opens so a player that honours focus
     * has paused by the first word, and given back once at the end so it resumes once. Google's
     * recognizer takes and drops its own focus per request; on the Titan that paused and resumed
     * Audible every five seconds (D14's MediaFocusControl lines).
     */
    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        if (focusRequest != null) return
        val retake = session?.focusRetaken == true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build()
        focusRequest = request
        val result = runCatching { manager.requestAudioFocus(request) }.getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        audioWatch.traceFocusRequest(result, retake)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) focusRequest = null
    }

    private fun abandonAudioFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        runCatching { audioManager?.abandonAudioFocusRequest(request) }
    }

    // -----------------------------------------------------------------------------------------
    // The microphone grant. spec SS6.8.
    // -----------------------------------------------------------------------------------------

    private val appOps: AppOpsManager? by lazy { runCatching { service.getSystemService(AppOpsManager::class.java) }.getOrNull() }
    private var pendingStart: Runnable? = null

    /**
     * spec SS6.8: RECORD_AUDIO is a "while in use" permission, evaluated by the uid's process
     * state and microphone capability at the moment the recording starts. The visibility hold
     * ([onHoldImeVisible]) is what grants that capability, but it lands over a binder round trip
     * and a service rebind, and the first request of the session must not race it (on the
     * Titan the engine's record was silenced for its whole five seconds when it did, D22). So
     * the request waits, in 40 ms steps up to 400 ms, until the system's own answer for this
     * package reads allowed, and goes out at once when it already does (every re-listen).
     */
    private fun startListeningOnceMicrophoneAllowed(request: RecognizerRequest, attempt: Int = 0) {
        cancelPendingStart()
        if (session == null) return
        if (attempt >= MIC_GRANT_MAX_ATTEMPTS || microphoneAllowedNow()) {
            if (attempt >= MIC_GRANT_MAX_ATTEMPTS) Log.e(TAG, "microphone still not allowed after ${attempt * MIC_GRANT_POLL_MS} ms; listening anyway")
            else if (attempt > 0) DiagnosticLog.i(TAG) { "microphone allowed after ${attempt * MIC_GRANT_POLL_MS} ms" }
            startListening(request)
            return
        }
        // The grant is missing: the keyboard was hidden under the running session (an app's own
        // hide, Back with stop-on-typing off), which dropped the visible binding. Hold again
        // before waiting; a refused show puts nothing on screen (SS6.8).
        if (attempt == 0) runCatching { onHoldImeVisible?.invoke() }.onFailure { error -> Log.e(TAG, "re-hold IME visible crashed", error) }
        val runnable = Runnable {
            pendingStart = null
            startListeningOnceMicrophoneAllowed(request, attempt + 1)
        }
        pendingStart = runnable
        handler.postDelayed(runnable, MIC_GRANT_POLL_MS)
    }

    private fun cancelPendingStart() {
        pendingStart?.let { handler.removeCallbacks(it) }
        pendingStart = null
    }

    /** The system's own foreground evaluation of RECORD_AUDIO for this package right now (AppOpsUidStateTracker.evalMode, D22). */
    private fun microphoneAllowedNow(): Boolean {
        val ops = appOps ?: return true
        return runCatching { ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_RECORD_AUDIO, Process.myUid(), service.packageName) == AppOpsManager.MODE_ALLOWED }
            .getOrDefault(true)
    }

    // -----------------------------------------------------------------------------------------
    // The recognizer. spec SS4.2, SS5.
    // -----------------------------------------------------------------------------------------

    private fun startListening(request: RecognizerRequest) {
        DiagnosticLog.i(TAG) { "start listening segmented=${request.segmented} offline=${request.preferOffline} silenceMs=${request.completeSilenceMs} minimumMs=${request.minimumLengthMs}" }
        audioWatch.noteListenStarted()
        val speechRecognizer = ensureRecognizer()
        if (speechRecognizer == null) {
            // spec SS2.6 step 4: no recognizer could be created.
            dispatch(DictationEvent.StartFailed(DictationStartFailureReason.RECOGNITION_UNAVAILABLE))
            return
        }
        // spec SS2.6 step 7. A SecurityException here is the permission race the trigger's own
        // check cannot fully close: granted at trigger time, revoked before this call actually
        // reaches the recognizer.
        runCatching { speechRecognizer.startListening(buildRecognizerIntent(request)) }
            .onFailure { error ->
                Log.e(TAG, "startListening failed", error)
                val reason = if (error is SecurityException) DictationStartFailureReason.SECURITY_FAILURE else DictationStartFailureReason.OTHER_FAILURE
                dispatch(DictationEvent.StartFailed(reason))
            }
    }

    /** Destroys the recognizer so the next session binds a fresh connection to the speech service. */
    private fun releaseRecognizer() {
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
        recognizerEngineId = null
        DiagnosticLog.i(TAG) { "recognizer released at the end of the session" }
    }

    /**
     * spec SS2.6 step 4, SS4.2: makes sure a recognizer exists for `dictation_engine`'s stored id.
     * "Any creation failure" falls to the system default; if that fails too, there is no
     * recognizer and the caller reports the start failure.
     */
    private fun ensureRecognizer(): SpeechRecognizer? {
        val engineId = settings.engineId
        recognizer?.let { if (recognizerEngineId == engineId) return it }
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
        recognizerEngineId = null
        if (!SpeechRecognizer.isRecognitionAvailable(service)) return null
        // D18: on the Titan 2 the platform's on-device recognizer slot is empty, so this is false
        // there and `ondevice` falls to the system default; offline recognition comes from the
        // system default's own on-device engine instead (SS4.3).
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

    /** spec SS5, SS5.1: the request, field by field, from what `:core:speech` decided. */
    private fun buildRecognizerIntent(request: RecognizerRequest): Intent {
        val languageTag = LanguageTagResolver.resolve(subtypeLanguageTag, Locale.getDefault().toLanguageTag())
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, service.packageName)
            putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, request.maskOffensive)
            // SS4.3: the on-device recognizer. On the Titan Google's service also refuses to
            // format (punctuate) unless this is set (D15's "EXTRA_ENABLE_FORMATTING can't be
            // used when EXTRA_PREFER_OFFLINE is false").
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, request.preferOffline)
            // SS5, D23: INTS. Google reads both lengths with getIntExtra; a long is thrown away
            // ("expected Integer but value was a java.lang.Long. The default value 0 was
            // returned" ... "is not set with positive value; ignoring EXTRA_SEGMENTED_SESSION"),
            // which is why no build, 2.x included, had ever asked for a segmented session, and
            // why the engine ended every request at the first breath.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, request.completeSilenceMs.toInt())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, request.minimumLengthMs.toInt())
            // SS5: Google's own dictation-mode flag, the one Chrome's Web Speech glue sets for a
            // continuous session (D17). Undocumented, so nothing here depends on it; the
            // segmented-session extra below is the documented request for the same thing.
            putExtra(GOOGLE_DICTATION_MODE_EXTRA, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (request.enableFormatting) putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
                // SS5: the value of EXTRA_SEGMENTED_SESSION is the NAME of the extra that ends the
                // session, as a String. 2.x and the first 3.0 build put the pause itself (a Long)
                // here, which Google's service rejected on every request ("expected String but
                // value was a java.lang.Long ... ignoring it", D15): segmented mode was never in
                // force and every session was a one-shot.
                if (request.segmented) putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
            }
        }
    }

    /** Translates the recognizer's real callbacks into [DictationEvent]s; decides nothing itself. */
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = dispatch(DictationEvent.ReadyForSpeech)
        override fun onBeginningOfSpeech() = dispatch(DictationEvent.BeginningOfSpeech)
        override fun onRmsChanged(rmsdB: Float) {
            // spec SS8.1: the first level report is the proof the microphone is open; only the
            // first one is worth a dispatch, the rest only colour the strip's button, except
            // while a continuation probe is waiting for exactly this sign of life (SS6.3).
            // While the cue waits for the route (SS6.10) the first report is remembered once.
            if (session?.let { !it.cuePlayed && !it.firstAudioSeen } == true) dispatch(DictationEvent.FirstAudio)
            else if (session?.continuationProbeDeadlineMs != null) dispatch(DictationEvent.EngineActivity)
            onAudioLevel?.invoke(rmsdB)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = dispatch(DictationEvent.EndOfSpeech)
        override fun onError(error: Int) {
            DiagnosticLog.i(TAG) { "recognizer error $error phase=${session?.phase}" }
            dispatch(DictationEvent.Error(error))
        }
        override fun onResults(results: Bundle?) {
            beforeResult()
            dispatch(DictationEvent.FinalResult(firstResult(results)))
        }
        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let {
                beforeResult()
                dispatch(DictationEvent.PartialResult(it))
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onSegmentResults(segmentResults: Bundle) {
            firstResult(segmentResults)?.let {
                beforeResult()
                dispatch(DictationEvent.SegmentResult(it))
            }
        }
        override fun onEndOfSegmentedSession() {
            DiagnosticLog.i(TAG) { "segmented session ended phase=${session?.phase}" }
            dispatch(DictationEvent.SegmentedSessionEnded)
        }
    }

    /** With formatting on, the engine lists the formatted hypothesis first and the raw one second (RecognizerIntent.EXTRA_ENABLE_FORMATTING); the first is always the one wanted. */
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
        const val MIC_GRANT_POLL_MS = 40L
        const val MIC_GRANT_MAX_ATTEMPTS = 10
        const val TAG = "PhysiBoardDictation"
        /** Google's recognizer's own continuous-dictation flag; see [buildRecognizerIntent]. */
        const val GOOGLE_DICTATION_MODE_EXTRA = "android.speech.extra.DICTATION_MODE"
    }
}

/** spec: dictation.md SS3: what the field holds of the composing partial, as [KeyboardSession] can tell. */
internal enum class FieldVerdict {
    /** The partial is still there at the caret. */
    INTACT,
    /** The field is empty (the app's send cleared it). */
    CLEARED,
    /** The field has text, but not the partial at the caret: the app or the user changed it. */
    CHANGED,
    /** The field cannot be read reliably (a web field answering nothing). */
    UNKNOWN,
}
