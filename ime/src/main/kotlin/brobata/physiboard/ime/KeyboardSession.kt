package brobata.physiboard.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.keys.KeyCommands
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.pointer.caret.CaretGeometry
import brobata.physiboard.core.pointer.caret.CaretUsability
import brobata.physiboard.core.pointer.caret.CursorAnchorReport
import brobata.physiboard.core.pointer.caret.CursorUpdateRequestState
import brobata.physiboard.core.pointer.caret.CursorUpdateRetrySchedule
import brobata.physiboard.core.pointer.trackpad.TrackpadActivationSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadGestureSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadPhysicalKey
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.AppProfileResolver
import brobata.physiboard.core.text.EnterOverride
import brobata.physiboard.core.text.EnterOverrideResolver
import brobata.physiboard.core.text.MessagingPreset
import brobata.physiboard.device.titan.KeyNormalizer
import brobata.physiboard.device.titan.TitanLayouts
import brobata.physiboard.ime.pointer.CaretBadgeOverlayController
import brobata.physiboard.ime.pointer.TrackpadOverlayController

/**
 * Where one key event meets the pipeline.
 *
 * Everything that decides what should happen lives in [KeyboardPipeline] (no `android.*` import,
 * JVM-testable) and the pure modules it calls; this class only pulls the plain integer fields off
 * a real `KeyEvent`, normalises them through `:device:titan`, reads and writes the real
 * `InputConnection`, and schedules the one real-time timer (long press) the pure modules need a
 * clock for. spec: docs/plans/rebuild-from-scratch.md, "`:ime` decides nothing. It reads, it
 * calls, it applies."
 */
internal class KeyboardSession(
    private val service: InputMethodService,
    // SPEC GAP / missing module: there is no `:settings` module yet (rebuild-from-scratch build
    // order step 6; this task's own instruction is not to build one), so the per-app profile list
    // and the WebAPK-to-host lookup (per-app-behavior.md SS2.2, read from installed-package
    // manifest metadata) are shipped defaults here, exactly like [KeyboardSettings]; wiring a real
    // settings store later means constructing this session with real values instead.
    private val appProfiles: List<AppProfile> = emptyList(),
    private val webApkHost: (String) -> String? = { null },
    // SPEC GAP / missing module: same as [appProfiles] above; the Enter override list and the
    // messaging preset (per-app-behavior.md SS3.12) are shipped defaults until a settings store
    // exists. The master switch defaults true and the preset to `SEND_SHIFT_NEWLINE`, matching
    // SS3.12's own "first-run defaults" (the one-shot baseline 2.x writes before any settings
    // exist), so a fresh build behaves like a fresh install rather than like every Enter override
    // being silently off; the four seeded WhatsApp/Discord/Messages/Instagram override rows that
    // baseline also writes are left for that future settings layer to seed, not hardcoded here.
    private val enterOverrides: List<EnterOverride> = emptyList(),
    private val enterPreset: MessagingPreset = MessagingPreset.SEND_SHIFT_NEWLINE,
    private val enterBehaviorEnabled: Boolean = true,
) {

    // SPEC GAP / missing module: the Titan 2 Elite is the only device this build ships to (this
    // module's own rebuild plan), so the layout is not yet selectable; when a settings/layout
    // module exists this becomes a caller-supplied value instead of a constant.
    private val pipeline = KeyboardPipeline(layout = TitanLayouts.titan2EliteQwerty(), onCommand = ::handleCommand)

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { onLongPressTick() }

    private var candidatesStrip: CandidatesStripView? = null

    // -----------------------------------------------------------------------------------------
    // Screen trackpad. spec: trackpad-caret-nav.md SS2. [TrackpadActivationSettings] and
    // [TrackpadGestureSettings] below are the shipped defaults, matching the settings-catalog.md
    // baseline (trigger Space, hold mode, 250 ms threshold, 32 px step) rather than needing an
    // override the way [KeyboardPipeline.KeyboardSettings] does for a couple of its own fields.
    //
    // Whether the feature runs at all is a different question, gated by
    // [KeyboardPipeline.KeyboardSettings.screenTrackpadEnabled] (checked at the top of
    // [interceptForTrackpad], not here): that field ships `false`, the settings-catalog.md CODE
    // DEFAULT for `screen_trackpad_enabled`, not the device baseline. An earlier revision wired
    // this section unconditionally, reasoning (wrongly) that the baseline being `true` made an
    // on/off gate unnecessary until a real `:settings` module existed; that shipped a feature
    // intercepting Space, the single most-pressed key, ahead of everything else in the key
    // pipeline, with no way for anyone to turn it back off when its hold-vs-tap timing misfired
    // on ordinary typing. See [interceptForTrackpad]'s own KDoc for what the gate guarantees.
    // -----------------------------------------------------------------------------------------

    private val trackpad = TrackpadOverlayController(
        service = service,
        handler = handler,
        activationSettings = TrackpadActivationSettings(),
        gestureSettings = TrackpadGestureSettings(),
        isShiftActive = pipeline::isTrackpadShiftActive,
        currentInputConnection = { service.currentInputConnection },
        replayTriggerDown = ::replayPendingTrackpadDown,
        replayTriggerDownAndUp = ::replayPendingTrackpadDownAndUp,
    )

    /**
     * The trigger-down [interceptForTrackpad] swallowed, kept only so [replayPendingTrackpadDown]
     * and [replayPendingTrackpadDownAndUp] have a stroke to replay. spec: SS2.3, "the raw event is
     * kept for replay". Never read except by those two functions, and cleared by both before they
     * do anything else, so a crash mid-replay cannot leave a stale down to be replayed twice.
     */
    private var pendingTrackpadDownEvent: KeyEvent? = null
    private var pendingTrackpadDownStroke: KeyStroke? = null

    /** The up event [interceptForTrackpad] is currently deciding about, reused as-is for a down-and-up replay. */
    private var pendingTrackpadUpEvent: KeyEvent? = null

    // -----------------------------------------------------------------------------------------
    // Caret badge. spec: trackpad-caret-nav.md SS4. `caret_modifier_badge`'s baseline default is
    // true (SS4.8); same no-`:settings`-module reasoning as the trackpad above, so this is also
    // wired unconditionally.
    // -----------------------------------------------------------------------------------------

    private val caretBadge = CaretBadgeOverlayController(service)

    /** The editor's last usable cursor-anchor report, or null; SS4.6, "forgotten... when the editor finishes". */
    private var lastCaretGeometry: CaretGeometry? = null

    /** spec: SS4.7's retry bookkeeping, one instance per editor (reset in [onStartInput]). */
    private var cursorUpdateState = CursorUpdateRequestState()

    /** Groups every scheduled cursor-update retry so [onStartInput]/[onFinishInput] can cancel them all in one call. */
    private val cursorUpdateToken = Any()

    // spec: dictation.md. `:core:speech` holds the session's own rules; this class only owns the
    // two facts only `:ime` can supply: which field is current, and whether a key reaching the
    // ordinary typing pipeline while dictation is listening means the user just edited the field
    // out from under it (spec: the c440844 fix, DictationController.onUserEditedComposingText's
    // own KDoc). `trigger` is now wired to the Fn-burst command [handleCommand] receives from
    // `:core:keys` (keys-and-modifiers.md SS3.3) and to the microphone key of a future strip; this
    // task only wires the former.
    private val dictationController = DictationController(service) { service.currentInputConnection }
    private var currentPackageName: String? = null

    fun onDictationTrigger() = dictationController.trigger(currentPackageName)

    fun onDictationServiceDestroyed() = dictationController.onServiceDestroyed()

    // SPEC GAP / missing module: there is no `:settings` module yet, so the primary suggestion
    // language cannot come from the current input style (dictionaries-languages.md SS8.7); `en`
    // is the only bundled dictionary today (docs/dictionaries.md), so it is the only one this
    // milestone can load regardless. Wiring a real subtype-driven language is a change to this
    // one line once a settings/subtype module exists.
    private val dictionaryLoader = DictionaryAssetLoader(service.assets, handler)

    init {
        // spec: autocorrect-suggestions.md SS2 point 3 and the "computation runs off the main
        // thread" rule: the keyboard must accept keystrokes immediately, typing with no
        // suggestions, and only start suggesting once this background load lands.
        dictionaryLoader.loadAsync(PRIMARY_LANGUAGE) { index ->
            pipeline.resources = pipeline.resources.copy(dictionaries = listOf(index))
            refreshCandidatesStrip()
        }
    }

    /**
     * Set right before this session calls [InputConnection.applyEditorOps] for a stroke that
     * moves the cursor, so the resulting [InputMethodService.onUpdateSelection] callback (our own
     * edit's forward step) is not mistaken for an external cursor move. spec: text-input.md SS2,
     * "every cursor change that is not the one-character forward step caused by its own last
     * commit". Distinguishing our own edit from a genuinely external one is exactly the fact only
     * this Android-side glue can know (a pure module never sees the real, asynchronous
     * `InputConnection` callback), so the flag lives here rather than in [KeyboardPipeline].
     */
    private var suppressNextSelectionUpdate = false

    private val vibrator: Vibrator? by lazy {
        runCatching {
            (service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        }.getOrNull()
    }

    // -----------------------------------------------------------------------------------------
    // Field lifecycle
    // -----------------------------------------------------------------------------------------

    fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        handler.removeCallbacks(longPressRunnable)
        // spec: trackpad-caret-nav.md SS4.6, "forgotten... when monitoring restarts for a new
        // editor" and SS4.7, "every new editor drops it": the old caret and retry count belong to
        // the field that just closed, restarting or not.
        lastCaretGeometry = null
        cursorUpdateState = CursorUpdateRequestState()
        scheduleCursorUpdateRetries()
        // spec: per-app-behavior.md SS2.1, "the package name comes from the editor"; SS2.2's
        // WebAPK-host rule is what lets a profile filed under a web app's own shell identity still
        // match here, since `info.packageName` reports the host browser for one, never the shell.
        val exactTypingProfile = AppProfileResolver.resolve(info?.packageName, appProfiles, webApkHost)
        // spec: SS2.2, "Only the exact-typing list is expanded ... The Enter behavior overrides ...
        // are matched by exact package name": no WebAPK-host lookup here, unlike the line above.
        val reportedPackage = info?.packageName
        val profile = exactTypingProfile.copy(
            // [AppProfile.packageName] on the matched exact-typing entry can be a WebAPK's own
            // shell identity (SS4.5); [EnterDecision] needs the reported package itself (SS2.2, no
            // WebAPK expansion for Enter), which is also what its own Discord `auto` special case
            // (SS3.6) compares against.
            packageName = reportedPackage.orEmpty(),
            enterBehavior = EnterOverrideResolver.resolveBehavior(reportedPackage, enterOverrides, enterPreset, enterBehaviorEnabled),
            enterSendMethod = EnterOverrideResolver.resolveSendMethod(reportedPackage, enterOverrides, enterBehaviorEnabled),
            enterActionAllowed = EnterOverrideResolver.isEditorActionAllowed(reportedPackage, enterOverrides, enterBehaviorEnabled),
        )
        val field = classifyField(info, profile)
        pipeline.onStartInput(field, profile.editorTrust, profile)
        service.setCandidatesViewShown(field.isReallyEditable)
        currentPackageName = reportedPackage
        dictationController.onEditorFieldOpened(reportedPackage)
        refreshCandidatesStrip()
    }

    fun onFinishInput() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacksAndMessages(cursorUpdateToken)
        pipeline.onFinishInput()
        service.setCandidatesViewShown(false)
        dictationController.onEditorFieldClosed()
        // spec: SS4.6, "forgotten and the badge hidden when the editor finishes".
        lastCaretGeometry = null
        caretBadge.hide()
    }

    /**
     * spec: trackpad-caret-nav.md SS2.4, "removed on... the keyboard window hiding". A new Android
     * entry point independent of any keystroke, guarded the same way [onKeyEvent] is: a bug in the
     * trackpad's own cleanup must not escape onto the input method's callback thread.
     */
    fun onKeyboardWindowHidden() {
        runCatching { trackpad.onKeyboardWindowHidden() }.onFailure { error -> Log.e(TAG, "onKeyboardWindowHidden crashed", error) }
    }

    /**
     * spec: trackpad-caret-nav.md SS4.7, the editor's own cursor-anchor report. A new Android entry
     * point Android can call at any time once [requestCursorUpdates] succeeds, guarded the same way
     * [onKeyEvent] is, since a misbehaving editor's report is exactly the kind of input this
     * function did not choose to receive.
     */
    fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        runCatching {
            cursorUpdateState = CursorUpdateRetrySchedule.onRequestAccepted(cursorUpdateState)
            // spec SS4.6: "unusable... when any of its horizontal, top or bottom values is not a
            // number". The platform's own contract for these three getters is exactly that: NaN
            // when the editor did not report an insertion marker, never an exception, so
            // [CaretUsability.isUsable] is the only filter needed here.
            val report = CursorAnchorReport(
                horizontalPx = info.insertionMarkerHorizontal,
                topPx = info.insertionMarkerTop,
                bottomPx = info.insertionMarkerBottom,
                hasInvisibleRegion = info.insertionMarkerFlags and CursorAnchorInfo.FLAG_HAS_INVISIBLE_REGION != 0,
                hasVisibleRegion = info.insertionMarkerFlags and CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION != 0,
            )
            lastCaretGeometry = if (CaretUsability.isUsable(report)) {
                CaretGeometry(leftPx = report.horizontalPx!!, topPx = report.topPx!!, bottomPx = report.bottomPx!!)
            } else {
                null
            }
            refreshCaretBadge()
        }.onFailure { error -> Log.e(TAG, "onUpdateCursorAnchorInfo crashed", error) }
    }

    // -----------------------------------------------------------------------------------------
    // Cursor-anchor requests. spec: trackpad-caret-nav.md SS4.7.
    // -----------------------------------------------------------------------------------------

    /** The immediate request plus the four staged retries (80, 250, 600, 1200 ms), all cancellable together via [cursorUpdateToken]. */
    private fun scheduleCursorUpdateRetries() {
        handler.removeCallbacksAndMessages(cursorUpdateToken)
        attemptCursorUpdateRequest()
        CursorUpdateRetrySchedule.SCHEDULE_OFFSETS_MS.drop(1).forEach { offsetMs ->
            handler.postDelayed({ attemptCursorUpdateRequest() }, cursorUpdateToken, offsetMs)
        }
    }

    /** spec: SS4.7, "on every strip refresh:... while the setting is on and no request has been accepted yet, a retry is attempted". */
    private fun retryCursorUpdateOnRefresh() {
        if (cursorUpdateState.accepted) return
        val (nextState, shouldAttempt) = CursorUpdateRetrySchedule.onRefresh(cursorUpdateState)
        cursorUpdateState = nextState
        if (shouldAttempt) issueCursorUpdateRequest()
    }

    private fun attemptCursorUpdateRequest() {
        if (cursorUpdateState.accepted) return
        val (nextState, shouldAttempt) = CursorUpdateRetrySchedule.onScheduledAttempt(cursorUpdateState)
        cursorUpdateState = nextState
        if (shouldAttempt) issueCursorUpdateRequest()
    }

    private fun issueCursorUpdateRequest() {
        runCatching {
            val ic = service.currentInputConnection ?: return@runCatching
            val flags = InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR
            if (ic.requestCursorUpdates(flags)) {
                cursorUpdateState = CursorUpdateRetrySchedule.onRequestAccepted(cursorUpdateState)
            }
        }.onFailure { error -> Log.e(TAG, "requestCursorUpdates crashed", error) }
    }

    fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        if (suppressNextSelectionUpdate) {
            suppressNextSelectionUpdate = false
            return
        }
        runCatching {
            val textBeforeCursor = runCatching { service.currentInputConnection?.getTextBeforeCursor(240, 0)?.toString() }.getOrNull()
            pipeline.onExternalSelectionChange(textBeforeCursor)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "onUpdateSelection crashed", error) }
    }

    // -----------------------------------------------------------------------------------------
    // Key events
    // -----------------------------------------------------------------------------------------

    /**
     * True when PhysiBoard consumed the event and the app must not see it.
     *
     * Every branch below is caught: this is the one function Android calls for every physical
     * keystroke on the only keyboard the device has, so a `RuntimeException` escaping from
     * anywhere in `:core:keys`/`:core:text` (an edge case none of their own JVM tests happened to
     * cover) would otherwise propagate out through [InputMethodService.onKeyDown]/`onKeyUp` and
     * crash this process outright -- a dead keyboard with no on-screen keyboard to fall back to and
     * no obvious way back for the person holding the phone, a categorically worse failure than any
     * single missing capital or correction. Falling through unconsumed on a failure, rather than
     * swallowing the keystroke, means the app still gets the raw key even though PhysiBoard's own
     * smart handling of it did not run. No JVM test can pin this guard the way the two typing
     * defects above are pinned: it exists precisely for the exception a test did not anticipate, on
     * the one code path (`android.inputmethodservice.InputMethodService`'s own callback contract)
     * that no unit test in this project runs against for real.
     */
    fun onKeyEvent(event: KeyEvent): Boolean = runCatching {
        if (interceptForTrackpad(event)) return@runCatching true
        val stroke = normalizeStroke(event) ?: return@runCatching false
        processKeyStroke(stroke)
    }.getOrElse { error ->
        Log.e(TAG, "onKeyEvent crashed on keyCode=${event.keyCode}; letting the raw key through", error)
        false
    }

    private fun normalizeStroke(event: KeyEvent): KeyStroke? = KeyNormalizer.normalize(
        keyCode = event.keyCode,
        scanCode = event.scanCode,
        action = event.action,
        repeatCount = event.repeatCount,
        metaState = event.metaState,
        deviceId = event.deviceId,
        eventTimeMs = event.eventTime,
    )

    /**
     * Runs one already-classified [KeyStroke] through [KeyboardPipeline] and applies whatever
     * comes back. spec: docs/plans/rebuild-from-scratch.md, "`:ime` decides nothing. It reads, it
     * calls, it applies." [onKeyEvent] calls this for a stroke [interceptForTrackpad] left alone;
     * [replayPendingTrackpadDown] and [replayPendingTrackpadDownAndUp] call it again for a stroke
     * the trackpad swallowed and then decided, after all, was not a hold (trackpad-caret-nav.md
     * SS2.3: "the swallowed down... replayed through the normal pipeline, so a quick tap still
     * types the key").
     */
    private fun processKeyStroke(stroke: KeyStroke): Boolean {
        val ic = service.currentInputConnection ?: return false
        // spec: the c440844 fix. Any key reaching the ordinary typing pipeline while dictation is
        // listening is the user changing the field by some means other than the dictation session
        // itself (typing over it, or deleting it), so whatever the engine remembers of the current
        // utterance can no longer be trusted; see DictationController.onUserEditedComposingText.
        if (dictationController.isActive) dictationController.onUserEditedComposingText()
        val readout = ic.readEditorState(stroke.timeMs)
        val result = pipeline.onKeyStroke(stroke, readout.snapshot)
        val consumed = applyResult(ic, result, readout)
        scheduleLongPressIfNeeded()
        refreshCandidatesStrip()
        return consumed
    }

    // -----------------------------------------------------------------------------------------
    // Screen trackpad's trigger. spec: trackpad-caret-nav.md SS2.2, "before everything else in
    // the key pipeline." Runs from inside [onKeyEvent]'s own guard, ahead of [processKeyStroke].
    // -----------------------------------------------------------------------------------------

    /**
     * spec: SS2.2's trigger-key table and SS2.3's activation-mode table. Classifies [event] and
     * hands it to [TrackpadOverlayController], caching whatever it would need to replay later
     * (SS2.3, "the raw event is kept for replay") before finding out whether a replay is actually
     * needed; only an event the trackpad has no opinion about, or explicitly leaves alone, reaches
     * [processKeyStroke] afterward.
     *
     * The [KeyboardPipeline.KeyboardSettings.screenTrackpadEnabled] check below is a hard gate,
     * not a preference the trackpad's own state machine is merely told about: when it is false
     * this function returns before classifying the key, before touching any `pendingTrackpad*`
     * field and before calling [trackpad] at all, so [TrackpadActivation] never sees the event and
     * [TrackpadOverlayController] never arms its timer. A key this function declines always falls
     * through to [onKeyEvent]'s own `normalizeStroke`/[processKeyStroke] call exactly as it would
     * have before this class had a trackpad section, which is what makes "off" mean the ordinary
     * path runs with nothing swallowed and nothing replayed, not just a smaller window for the
     * same swallow-and-replay behaviour.
     *
     * Regression fixed here: caching a down below used to run for ANY of the five
     * [TrackpadPhysicalKey] values (Space, either Shift, Sym, Back), not only the one actually
     * configured as [TrackpadActivationSettings.triggerKey]. With the default trigger (Space),
     * that meant a Shift key-down chording with a still-pending Space -- ordinary the moment a
     * user capitalises the first letter of the next word, i.e. right after almost every sentence
     * -- overwrote [pendingTrackpadDownEvent]/[pendingTrackpadDownStroke] with SHIFT's own down a
     * single statement before [trackpad]'s chord-abort logic replayed whatever that field held
     * (SS2.3: "the swallowed trigger down is replayed at once"). The replay therefore fired
     * Shift's down instead of Space's: the real Space keystroke was dropped entirely (never typed
     * directly, never replayed), and Shift's down reached `:core:keys` twice for one physical
     * press -- once via the mis-replay, once via its own ordinary delivery moments later, both
     * carrying the same event timestamp -- which satisfies `ModifierMachine.shiftDown`'s
     * same-instant double-tap check and can latch Caps Lock with no real double tap ever
     * happening (pinned at the `:core:keys` layer, where a real `KeyEvent` is not needed, by
     * `ModifierMachineTest`'s "a Shift key-down delivered twice with no release in between..."
     * case). Restricting the cache to a down that actually matches the configured trigger means a
     * chording Shift/Sym/Back is never written into a slot it does not own, so the eventual
     * replay -- if the trigger's own down is even still pending -- can only ever replay the
     * trigger's own stroke.
     */
    private fun interceptForTrackpad(event: KeyEvent): Boolean {
        if (!pipeline.settings.screenTrackpadEnabled) return false
        val trackpadKey = classifyTrackpadKey(event.keyCode)
        val isTriggerKey = trackpadKey != null && TrackpadPhysicalKey.matchesTrigger(trackpadKey, trackpad.activationSettings.triggerKey)
        // spec SS2.2: "A Space down that already carries Ctrl or Alt in its meta state is never a trigger."
        val carriesDisqualifyingMeta = trackpadKey == TrackpadPhysicalKey.SPACE &&
            (event.metaState and KeyEvent.META_CTRL_ON != 0 || event.metaState and KeyEvent.META_ALT_ON != 0)
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (isTriggerKey) {
                    pendingTrackpadDownEvent = event
                    pendingTrackpadDownStroke = normalizeStroke(event)
                }
                trackpad.onKeyDown(trackpadKey, event.repeatCount, event.eventTime, carriesDisqualifyingMeta)
            }
            KeyEvent.ACTION_UP -> {
                // Same reasoning as the down branch above: only the trigger's own up is ever
                // useful to [replayPendingTrackpadDownAndUp], and caching unconditionally used to
                // let an unrelated classified key's up (e.g. a Shift that was already held before
                // Space went down, released while Space is still pending) overwrite this field a
                // moment before the trigger's own up needed it, substituting the wrong stroke into
                // the replay.
                if (isTriggerKey) pendingTrackpadUpEvent = event
                trackpad.onKeyUp(trackpadKey, event.eventTime)
            }
            else -> false
        }
    }

    /**
     * spec: SS2.2's `screen_trackpad_trigger_key` table. [TrackpadPhysicalKey] deliberately keeps
     * Left and Right Shift apart (its own KDoc), which `:device:titan`'s `KeyId.Modifier(SHIFT)`
     * does not, so this reads the raw keycode directly rather than going through [KeyNormalizer].
     */
    private fun classifyTrackpadKey(keyCode: Int): TrackpadPhysicalKey? = when (keyCode) {
        KeyEvent.KEYCODE_SPACE -> TrackpadPhysicalKey.SPACE
        KeyEvent.KEYCODE_SHIFT_LEFT -> TrackpadPhysicalKey.SHIFT_LEFT
        KeyEvent.KEYCODE_SHIFT_RIGHT -> TrackpadPhysicalKey.SHIFT_RIGHT
        KeyEvent.KEYCODE_SYM -> TrackpadPhysicalKey.SYM
        KeyEvent.KEYCODE_BACK -> TrackpadPhysicalKey.BACK
        else -> null
    }

    /**
     * spec: SS2.3, the chord and permission-failure rows ("the swallowed trigger down is replayed
     * at once" / "trigger down replayed"). Called by [trackpad] itself, from inside a key event
     * this class is already guarding ([onKeyEvent]) or from its own hold timer, which is why this
     * wraps its own work rather than trusting the caller's guard.
     */
    private fun replayPendingTrackpadDown() {
        val downEvent = pendingTrackpadDownEvent
        val downStroke = pendingTrackpadDownStroke
        pendingTrackpadDownEvent = null
        pendingTrackpadDownStroke = null
        if (downStroke == null) return
        runCatching {
            // spec SS2.3: "If the normal pipeline does not handle the replayed down, the raw down
            // ... [is] sent to the editor through the input connection instead."
            if (!processKeyStroke(downStroke)) downEvent?.let { service.currentInputConnection?.sendKeyEvent(it) }
        }.onFailure { error -> Log.e(TAG, "trackpad replay (down) crashed", error) }
    }

    /** spec: SS2.3's `hold` row, "the swallowed down and the up are replayed through the normal pipeline". */
    private fun replayPendingTrackpadDownAndUp() {
        val downEvent = pendingTrackpadDownEvent
        val downStroke = pendingTrackpadDownStroke
        val upEvent = pendingTrackpadUpEvent
        pendingTrackpadDownEvent = null
        pendingTrackpadDownStroke = null
        pendingTrackpadUpEvent = null
        runCatching {
            val downConsumed = downStroke?.let(::processKeyStroke) ?: false
            if (!downConsumed) downEvent?.let { service.currentInputConnection?.sendKeyEvent(it) }
            val upStroke = upEvent?.let(::normalizeStroke)
            val upConsumed = upStroke?.let(::processKeyStroke) ?: false
            if (!upConsumed) upEvent?.let { service.currentInputConnection?.sendKeyEvent(it) }
        }.onFailure { error -> Log.e(TAG, "trackpad replay (down+up) crashed", error) }
    }

    private fun onLongPressTick() {
        runCatching {
            val ic = service.currentInputConnection ?: return@runCatching
            val nowMs = SystemClock.uptimeMillis()
            val readout = ic.readEditorState(nowMs)
            val result = pipeline.checkLongPressTick(nowMs, readout.snapshot) ?: return@runCatching
            applyResult(ic, result, readout)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "onLongPressTick crashed", error) }
    }

    private fun scheduleLongPressIfNeeded() {
        handler.removeCallbacks(longPressRunnable)
        val deadline = pipeline.pendingLongPressDeadlineMs ?: return
        val delay = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(longPressRunnable, delay)
    }

    /**
     * Applies whatever [result] carries, and answers whether the key should count as consumed.
     * [PipelineResult.ops] apply exactly as before; [PipelineResult.enterDelivery], when present,
     * is per-app-behavior.md SS3.4's real `InputConnection` call ([EditorBridge.performEnterDelivery]),
     * the one place in this whole feature where "was it delivered" can finally be answered, and
     * where a Ctrl-triggered send's Ctrl state actually gets cleared once that answer is yes.
     */
    private fun applyResult(ic: InputConnection, result: PipelineResult, readout: EditorReadout): Boolean {
        if (result.ops.isNotEmpty()) {
            suppressNextSelectionUpdate = true
            ic.applyEditorOps(
                ops = result.ops,
                windowStartOffset = readout.documentStartOffset,
                cursorAbsolute = readout.cursorAbsolute,
                sendSpaceKeyFallback = { ic.sendSpaceKeyFallback(SystemClock.uptimeMillis()) },
                haptic = ::performHaptic,
            )
        }
        val delivery = result.enterDelivery ?: return result.consumed
        val delivered = ic.performEnterDelivery(delivery, SystemClock.uptimeMillis())
        if (delivery.clearsCtrlState(delivered)) {
            pipeline.clearCtrlStateAfterEnterSend()
        }
        return delivered
    }

    /** spec: text-input.md's several "trigger a haptic on replacement" rules. Provisional: the real duration/style is a theme setting (status-bar.md SS9), not wired yet (no `:settings` module). */
    private fun performHaptic() {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(HAPTIC_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    // -----------------------------------------------------------------------------------------
    // Commands. spec: keys-and-modifiers.md SS3.3, SS4.4, SS15 item 3, SS12.2 (a Fn Layer
    // `command` mapping): every id below names a subsystem that has no owning module yet
    // (rebuild-from-scratch build order steps 4-5) except dictation, wired below; layout
    // switching, nav mode's own exit command and the assistant are accepted and ignored rather
    // than guessed at; wiring each one is a change to this one function.
    // -----------------------------------------------------------------------------------------

    /**
     * spec: keys-and-modifiers.md SS3.3, the Fn burst's own [KeyCommands.TOGGLE_DICTATION].
     * `:core:keys`' `ModifierMachine.fnBurstDown` is what counts the burst and emits this id (five
     * Fn-origin repeats, D3's "never sends a key-up" is exactly why a duration-based hold cannot
     * see it, dictation.md SS2.1); this is the one line that turns that count into a real session.
     * Wrapped like every other new entry point this task adds: [DictationController.trigger] does
     * real Android work (a permission check, possibly starting an activity) on the same call stack
     * that reached here from [onKeyEvent], and that stack's own `runCatching` is a defense the
     * command dispatch itself must not rely on being present forever.
     */
    private fun handleCommand(commandId: String) {
        if (commandId == KeyCommands.TOGGLE_DICTATION) {
            runCatching { onDictationTrigger() }.onFailure { error -> Log.e(TAG, "dictation trigger crashed", error) }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Candidates strip. spec: status-bar.md SS5 (the milestone-3 subset only; see
    // CandidatesStripView).
    // -----------------------------------------------------------------------------------------

    fun onCreateCandidatesView(): View {
        val strip = CandidatesStripView(service)
        strip.onSuggestionTapped = ::onSuggestionTapped
        candidatesStrip = strip
        refreshCandidatesStrip()
        return strip
    }

    private fun onSuggestionTapped(word: String) {
        runCatching {
            val ic = service.currentInputConnection ?: return@runCatching
            val readout = ic.readEditorState(SystemClock.uptimeMillis())
            val result = pipeline.onAcceptSuggestion(word, readout.snapshot)
            applyResult(ic, result, readout)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "onSuggestionTapped crashed", error) }
    }

    /** spec: status-bar.md's "refresh"; also SS4.6's "recomputes its items on every strip refresh" for the caret badge and SS4.7's refresh-driven retry. */
    private fun refreshCandidatesStrip() {
        candidatesStrip?.update(pipeline.suggestions().map { it.word })
        refreshCaretBadge()
        retryCursorUpdateOnRefresh()
    }

    /**
     * spec: trackpad-caret-nav.md SS4.6. Self-guarded rather than trusting its callers: some of
     * [refreshCandidatesStrip]'s own call sites (the dictionary loader's background callback,
     * [onStartInput]) predate this task and are not wrapped in a `runCatching` of their own.
     */
    private fun refreshCaretBadge() {
        runCatching {
            val metrics = service.resources.displayMetrics
            caretBadge.update(pipeline.modifierGlyphInput(), lastCaretGeometry, metrics.widthPixels.toFloat(), metrics.density)
        }.onFailure { error -> Log.e(TAG, "caret badge refresh crashed", error) }
    }

    private companion object {
        const val TAG = "PhysiBoardKeyboard"
        const val HAPTIC_DURATION_MS = 10L
        val PRIMARY_LANGUAGE: LanguageCode = LanguageCode.of("en")!!
    }
}
