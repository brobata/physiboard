package brobata.physiboard.ime

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.os.Build
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
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.strip.DipEffect
import brobata.physiboard.core.strip.LanguageTapDebounce
import brobata.physiboard.core.strip.Slot
import brobata.physiboard.core.strip.SlotKind
import brobata.physiboard.core.strip.SlotTextSizeSp
import brobata.physiboard.core.strip.StripAction
import brobata.physiboard.core.strip.StripButton
import brobata.physiboard.core.strip.StripDip
import brobata.physiboard.core.strip.StripGeometry
import brobata.physiboard.core.strip.StripInsets
import brobata.physiboard.core.strip.TapHaptic
import brobata.physiboard.core.strip.TouchableArea
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.AppProfileResolver
import brobata.physiboard.core.text.EnterIntent
import brobata.physiboard.core.text.EnterOverride
import brobata.physiboard.core.text.EnterOverrideResolver
import brobata.physiboard.core.text.MessagingPreset
import brobata.physiboard.device.titan.KeyNormalizer
import brobata.physiboard.device.titan.TitanLayouts
import brobata.physiboard.ime.pointer.CaretBadgeOverlayController
import brobata.physiboard.ime.pointer.TrackpadOverlayController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
    // The store ([SettingsSource], owned by `:app`) feeds every value below and [KeyboardSettings]
    // through [applySettings]; the constructor values are the shipped defaults the keyboard types
    // with until the store's first emission lands, so typing never waits on I/O. A null source
    // (a JVM test, or a host without the store) leaves them in place for good.
    private val settingsSource: SettingsSource? = null,
    private var appProfiles: List<AppProfile> = emptyList(),
    // SPEC GAP / missing module: the WebAPK-to-host lookup (per-app-behavior.md SS2.2) reads
    // installed-package manifest metadata, which no module does yet; a shipped no-op until then.
    private val webApkHost: (String) -> String? = { null },
    private var enterOverrides: List<EnterOverride> = emptyList(),
    private var enterPreset: MessagingPreset = MessagingPreset.SEND_SHIFT_NEWLINE,
    private var enterBehaviorEnabled: Boolean = true,
) {

    /** Collects [settingsSource] on the main looper for the session's lifetime; cancelled in [onServiceDestroyed]. */
    private val settingsScope = MainScope()

    // SPEC GAP / missing module: the Titan 2 Elite is the only device this build ships to (this
    // module's own rebuild plan), so the layout is not yet selectable; when a settings/layout
    // module exists this becomes a caller-supplied value instead of a constant.
    private val pipeline = KeyboardPipeline(layout = TitanLayouts.titan2EliteQwerty(), onCommand = ::handleCommand)

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { onLongPressTick() }

    private var statusBar: StatusBarView? = null

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

    /**
     * The service is going away. Every callback this session posted on the main handler (the
     * long-press tick, the staged cursor-update retries, the trackpad's hold timer, dictation's
     * clock) would otherwise fire against a destroyed service; spec dictation.md SS3 "Keyboard
     * service destroyed: timers cancelled".
     */
    fun onServiceDestroyed() {
        settingsScope.cancel()
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacksAndMessages(cursorUpdateToken)
        handler.removeCallbacks(dipReshowRunnable)
        runCatching { trackpad.onKeyboardWindowHidden() }.onFailure { error -> Log.e(TAG, "trackpad teardown crashed", error) }
        runCatching { caretBadge.hide() }.onFailure { error -> Log.e(TAG, "caret badge teardown crashed", error) }
        dictationController.onServiceDestroyed()
    }

    // SPEC GAP / missing module: there is no `:settings` module yet, so the primary suggestion
    // language cannot come from the current input style (dictionaries-languages.md SS8.7); `en`
    // is the only bundled dictionary today (docs/dictionaries.md), so it is the only one this
    // milestone can load regardless. Wiring a real subtype-driven language is a change to this
    // one line once a settings/subtype module exists.
    private val dictionaryLoader = DictionaryAssetLoader(service.assets, handler)

    init {
        // spec: status-bar.md SS6.1: the microphone button follows the recognizer's level reports.
        // Dictation starts asynchronously, so the first report is also the first moment the strip
        // can learn the session is active; the refresh is equality-guarded and cheap.
        dictationController.onAudioLevel = { level ->
            runCatching {
                refreshCandidatesStrip()
                statusBar?.setMicrophoneLevel(level)
            }.onFailure { error -> Log.e(TAG, "audio level crashed", error) }
        }
        // spec: autocorrect-suggestions.md SS2 point 3 and the "computation runs off the main
        // thread" rule: the keyboard must accept keystrokes immediately, typing with no
        // suggestions, and only start suggesting once this background load lands.
        dictionaryLoader.loadAsync(PRIMARY_LANGUAGE) { index ->
            pipeline.resources = pipeline.resources.copy(dictionaries = listOf(index))
            refreshCandidatesStrip()
        }
        // The store is read the same way: the shipped defaults above stand until the first value
        // arrives, and every later emission re-applies live (settings-catalog.md SS1).
        settingsSource?.let { source ->
            settingsScope.launch {
                source.settings.collect { settings ->
                    runCatching { applySettings(settings) }.onFailure { error -> Log.e(TAG, "applying settings crashed", error) }
                }
            }
        }
    }

    /** One stored [Settings] value, handed to every module that takes a bundle; [ImeSettings] names which row feeds which field. */
    private fun applySettings(settings: Settings) {
        pipeline.settings = ImeSettings.keyboardSettings(settings)
        pipeline.layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), settings)
        trackpad.activationSettings = ImeSettings.trackpadActivation(settings)
        trackpad.gestureSettings = ImeSettings.trackpadGesture(settings)
        appProfiles = ImeSettings.appProfiles(settings)
        enterOverrides = ImeSettings.enterOverrides(settings)
        enterPreset = settings.perApp.enterPreset
        enterBehaviorEnabled = settings.perApp.enterBehaviorEnabled
        dictationController.settings = ImeSettings.dictationSettings(settings, Build.VERSION.SDK_INT)
        dictationController.textSettings = ImeSettings.dictationTextSettings(dictationController.textSettings, settings)
    }

    /**
     * Set right before this session calls [InputConnection.applyEditorOps] for a stroke that
     * moves the cursor, so the resulting [InputMethodService.onUpdateSelection] callback (our own
     * edit's forward step) is not mistaken for an external cursor move. spec: text-input.md SS2,
     * "every cursor change that is not the one-character forward step caused by its own last
     * commit". Distinguishing our own edit from a genuinely external one is exactly the fact only
     * this Android-side glue can know (a pure module never sees the real, asynchronous
     * `InputConnection` callback), so it lives here; the arithmetic itself is
     * [AppliedEditAccounting]'s, where JUnit can reach it. See [OwnEditExpectation] for why this
     * is a position with an expiry and not a boolean.
     */
    private var ownEdit: OwnEditExpectation? = null

    /** The selection start the editor last reported, the cursor fact used when a stroke does not read the whole document. */
    private var lastReportedSelStart = 0

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
        ownEdit = null
        lastReportedSelStart = info?.initialSelStart?.coerceAtLeast(0) ?: 0
        if (restarting) {
            // spec: text-input.md line 85, 463, 497: a restart reclassifies and re-evaluates, but
            // does not wipe the word in progress; web fields restart input mid-word all the time.
            val textBeforeCursor = runCatching { service.currentInputConnection?.getTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString() }.getOrNull()
            pipeline.onRestartInput(field, profile.editorTrust, profile, textBeforeCursor)
            // The pipeline keeps a pending long press across a restart (the key is still held);
            // the timer cancelled above is re-armed for it rather than leaving it to never fire.
            scheduleLongPressIfNeeded()
        } else {
            pipeline.onStartInput(field, profile.editorTrust, profile)
        }
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
     * spec: status-bar.md SS13, "When Android hides the window and no dip is in flight: the screen
     * trackpad is deactivated, ... the render cache is invalidated, ... modifier state is reset
     * (nav mode preserved), and the suggestion context is reset"; trackpad-caret-nav.md SS2.4.
     * [KeyboardPipeline.onWindowHidden] answers whether a dip is in flight (SS12.2: then "the
     * window-hidden handling is skipped entirely", SS17: "the in-flight flag cannot tell the two
     * apart"), and everything here follows that one answer. Guarded the same way [onKeyEvent] is.
     */
    fun onKeyboardWindowHidden() {
        runCatching {
            if (!pipeline.onWindowHidden(SystemClock.uptimeMillis())) return@runCatching
            trackpad.onKeyboardWindowHidden()
            statusBar?.invalidateRenderCache()
        }.onFailure { error -> Log.e(TAG, "onKeyboardWindowHidden crashed", error) }
    }

    /** spec: status-bar.md SS13, "When the window is shown again the strip is refreshed immediately." */
    fun onKeyboardWindowShown() {
        runCatching { refreshCandidatesStrip() }.onFailure { error -> Log.e(TAG, "onKeyboardWindowShown crashed", error) }
    }

    // -----------------------------------------------------------------------------------------
    // The per-app dip. spec: status-bar.md SS12. The decisions are [StripDip]'s, via the
    // pipeline; this section owns the one real timer and the real `setCandidatesViewShown` calls.
    // -----------------------------------------------------------------------------------------

    private val dipReshowRunnable = Runnable { onDipHoldElapsed() }

    /**
     * Android asked whether to show the input view and the answer was [refused] (always, on this
     * keyboard: the service's `onEvaluateInputViewShown` is false). spec SS12.2: a refused request
     * for a listed app, with the strip actually on screen and not a configuration change, hides
     * the strip now and arms the 200 ms re-show.
     */
    fun onShowInputRequested(configurationChange: Boolean, refused: Boolean) {
        if (!refused) return
        runCatching {
            val rendered = statusBar?.isRenderedOnScreen() ?: false
            val decision = pipeline.onShowRequestRefused(SystemClock.uptimeMillis(), rendered, configurationChange)
            applyDipEffects(decision.effects)
            if (decision.started) {
                handler.removeCallbacks(dipReshowRunnable)
                handler.postDelayed(dipReshowRunnable, StripDip.HOLD_MS)
            }
        }.onFailure { error -> Log.e(TAG, "onShowInputRequested crashed", error) }
    }

    /** spec SS12.2: the service consults this before honouring any request to show the candidates view. */
    fun refusesCandidatesShow(): Boolean = runCatching { pipeline.refusesCandidatesShow(SystemClock.uptimeMillis()) }.getOrDefault(false)

    private fun onDipHoldElapsed() {
        runCatching { applyDipEffects(pipeline.onDipHoldElapsed(SystemClock.uptimeMillis())) }
            .onFailure { error -> Log.e(TAG, "dip re-show crashed", error) }
    }

    /** spec SS12.2 steps 2 and 3, plus SS3.2's "one turn after that forces the enclosing container back to visible". */
    private fun applyDipEffects(effects: List<DipEffect>) {
        effects.forEach { effect ->
            when (effect) {
                DipEffect.HIDE_STRIP -> service.setCandidatesViewShown(false)
                DipEffect.SHOW_STRIP -> {
                    service.setCandidatesViewShown(true)
                    handler.post { (statusBar?.parent as? View)?.visibility = View.VISIBLE }
                }
            }
        }
    }

    /**
     * spec: status-bar.md SS11, the inset policy, applied after Android computed its own. The
     * decision is [StripInsets]'; the window's size is the one fact only this side can read.
     */
    fun onComputeInsets(outInsets: InputMethodService.Insets) {
        runCatching {
            val decor = service.window?.window?.decorView
            val decision = StripInsets.decide(
                candidatesOnly = !service.isInputViewShown,
                contentTopPx = outInsets.contentTopInsets,
                visibleTopPx = outInsets.visibleTopInsets,
                windowWidthPx = decor?.width ?: 0,
                windowHeightPx = decor?.height ?: 0,
            )
            outInsets.contentTopInsets = decision.contentTopPx
            when (decision.touchable) {
                TouchableArea.REGION -> {
                    val rect = decision.touchableRect ?: return@runCatching
                    outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
                    outInsets.touchableRegion.set(Rect(rect.left, rect.top, rect.right, rect.bottom))
                }
                TouchableArea.CONTENT -> outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT
                null -> Unit
            }
        }.onFailure { error -> Log.e(TAG, "onComputeInsets crashed", error) }
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
        lastReportedSelStart = newSelStart
        ownEdit?.let { expectation ->
            when (expectation.classify(newSelStart, SystemClock.uptimeMillis())) {
                OwnEditExpectation.Verdict.OWN_EDIT -> {
                    ownEdit = null
                    return
                }
                OwnEditExpectation.Verdict.STILL_SETTLING -> return
                OwnEditExpectation.Verdict.EXTERNAL -> ownEdit = null
            }
        }
        runCatching {
            val textBeforeCursor = runCatching { service.currentInputConnection?.getTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString() }.getOrNull()
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
        val readout = ic.readEditorState(stroke.timeMs, wholeDocument = pipeline.needsWholeDocument(stroke), fallbackCursorAbsolute = lastReportedSelStart)
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
            val readout = ic.readEditorState(nowMs, wholeDocument = false, fallbackCursorAbsolute = lastReportedSelStart)
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
            if (AppliedEditAccounting.movesCursor(result.ops)) {
                ownEdit = OwnEditExpectation(
                    selStart = AppliedEditAccounting.expectedCursorAfter(readout.cursorAbsolute, readout.documentStartOffset, result.ops),
                    expiresAtMs = SystemClock.uptimeMillis() + OwnEditExpectation.SETTLE_WINDOW_MS,
                )
            }
            ic.applyEditorOps(
                ops = result.ops,
                windowStartOffset = readout.documentStartOffset,
                cursorAbsolute = readout.cursorAbsolute,
                sendSpaceKeyFallback = { ic.sendSpaceKeyFallback(SystemClock.uptimeMillis()) },
                haptic = ::performHaptic,
            )
        }
        // spec: the c440844 invariant. An edit this keyboard made to the text, or a key it handed
        // to the app knowing the app will delete or paste with it (never a modifier press, a
        // key-up or a Fn repeat), means the user changed the field under a listening dictation
        // session; see DictationController.onUserEditedComposingText.
        if (AppliedEditAccounting.editsField(result)) noteFieldEditedDuringDictation()
        val delivery = result.enterDelivery ?: return result.consumed
        val delivered = ic.performEnterDelivery(delivery, SystemClock.uptimeMillis())
        if (delivery.clearsCtrlState(delivered)) {
            pipeline.clearCtrlStateAfterEnterSend()
        }
        if (delivered && delivery.editsField) noteFieldEditedDuringDictation()
        return delivered
    }

    private fun noteFieldEditedDuringDictation() {
        if (dictationController.isActive) dictationController.onUserEditedComposingText()
    }

    /** A delivered send or newline changes the field (or clears it entirely); a swallow or a decline leaves it untouched. */
    private val EnterIntent.editsField: Boolean
        get() = when (this) {
            is EnterIntent.RequestEditorAction, EnterIntent.SendPlainEnter, EnterIntent.SendCtrlEnter, EnterIntent.InsertNewline -> true
            is EnterIntent.Swallow, EnterIntent.Decline -> false
        }

    /** spec: text-input.md's several "trigger a haptic on replacement" rules. Provisional: the real duration/style is a theme setting (status-bar.md SS9), not wired yet (no `:settings` module). */
    private fun performHaptic(durationMs: Long = HAPTIC_DURATION_MS) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)) }
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
    // The status bar. spec: status-bar.md. The model is the pipeline's ([KeyboardPipeline.stripModel]),
    // the drawing is [StatusBarView]'s; this section wires the taps to the subsystems that exist.
    // -----------------------------------------------------------------------------------------

    /** spec: status-bar.md SS3.1 in its 3.0 form (SS19): one strip, on the candidates surface. Built at the Titan's geometry (SS2) from the shipped settings. */
    fun onCreateCandidatesView(): View {
        val strip = pipeline.settings.statusBar
        val density = service.resources.displayMetrics.density
        val view = StatusBarView(
            context = service,
            geometry = StripGeometry.forBar(
                barHeightDp = strip.barHeightDp,
                pxPerDp = density,
                suggestionsHeightScale = strip.theme.suggestionsHeightScale,
                keyRounding = strip.theme.keyCornerRatio,
                chromeRounding = strip.theme.chromeCornerRatio,
            ),
            theme = strip.theme,
            roundedCorners = strip.roundedCorners,
            slotTextSize = SlotTextSizeSp.forScale(strip.theme.suggestionsHeightScale),
            listener = stripListener,
        )
        statusBar = view
        refreshCandidatesStrip()
        return view
    }

    private var lastLanguageTapMs: Long? = null

    private val stripListener = object : StatusBarView.Listener {
        override fun onSlotTapped(slot: Slot) = onStripSlotTapped(slot)

        /** SPEC GAP / missing module: action mode (SS5.3) needs the personal dictionary's hide/delete, which has no owner yet; a long press does nothing visible. */
        override fun onSlotLongPressed(slot: Slot) = Unit

        override fun onButtonTapped(button: StripButton) = onStripButtonTapped(button)
        override fun onButtonLongPressed(button: StripButton) = performStripAction(button.longPress, button)
    }

    /** spec SS5.3: a suggestion "is committed through the same path as accepting it from the keyboard"; the add-word and expansion taps have no owning module yet and are accepted as suggestions rather than dropped. */
    private fun onStripSlotTapped(slot: Slot) {
        runCatching {
            if (slot.kind == SlotKind.EMPTY) return@runCatching
            performHaptic()
            val ic = service.currentInputConnection ?: return@runCatching
            val readout = ic.readEditorState(SystemClock.uptimeMillis(), wholeDocument = true, fallbackCursorAbsolute = lastReportedSelStart)
            val result = pipeline.onAcceptSuggestion(slot.text, readout.snapshot)
            applyResult(ic, result, readout)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "slot tap crashed", error) }
    }

    /** spec SS6.1: the tap haptic, the "latched layer released first" rule, then the action. */
    private fun onStripButtonTapped(button: StripButton) {
        runCatching {
            when (button.haptic) {
                TapHaptic.SYSTEM_KEYBOARD_TAP -> performHaptic()
                TapHaptic.FIXED_25_MS -> performHaptic(STRIP_FIXED_HAPTIC_MS)
            }
            if (button.releasesLatchedLayerFirst) pipeline.releaseLatchedLayersForStripButton()
            performStripAction(button.tap, button)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "${button.id} tap crashed", error) }
    }

    /**
     * spec SS6.1's actions, wired to what exists today. Dictation (dictation.md) and the Ctrl+Z /
     * Ctrl+Y key events are real. SPEC GAP / missing module for the rest: the Sym pages
     * (layers-sym-alt.md), language switching (dictionaries-languages.md), the quick-actions
     * overlay (SS6.4) and a settings app do not exist in this milestone, so those buttons render,
     * give their haptic, and do nothing visible; "Open settings" launches the app's only activity.
     */
    private fun performStripAction(action: StripAction, button: StripButton) {
        when (action) {
            StripAction.Nothing -> Unit
            StripAction.StartDictation -> onDictationTrigger()
            is StripAction.SendCtrlCombo -> sendCtrlCombo(action.letter)
            StripAction.CycleLanguage -> {
                // spec SS6.1: "a tap within 500 ms of the last accepted tap is ignored".
                val now = SystemClock.uptimeMillis()
                if (LanguageTapDebounce.accepts(lastLanguageTapMs, now)) lastLanguageTapMs = now
                Log.i(TAG, "language button: no input-style switching yet (placeholder)")
            }
            StripAction.OpenSettings -> openOwnApp()
            is StripAction.OpenSymPage -> Log.i(TAG, "${button.id}: Sym page ${action.page} has no surface yet (placeholder)")
            StripAction.OpenQuickActions -> Log.i(TAG, "quick actions overlay not built yet (placeholder)")
        }
    }

    /** spec SS6.1: "Sends Ctrl+Z to the app" / "Sends Ctrl+Y", as a down and up pair the app interprets. */
    private fun sendCtrlCombo(letter: Char) {
        val ic = service.currentInputConnection ?: return
        val keyCode = KeyEvent.KEYCODE_A + (letter.uppercaseChar() - 'A')
        val now = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private fun openOwnApp() {
        val intent = service.packageManager.getLaunchIntentForPackage(service.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
    }

    /**
     * spec: status-bar.md SS1's "refresh"; also trackpad-caret-nav.md SS4.6's "recomputes its items
     * on every strip refresh" for the caret badge and SS4.7's refresh-driven retry. On the typing
     * path: the model is a handful of allocations and [StatusBarView.render] returns at once when
     * nothing changed, and both are guarded so a strip bug never reaches the keystroke.
     */
    private fun refreshCandidatesStrip() {
        statusBar?.let { view ->
            runCatching {
                view.render(
                    pipeline.stripModel(
                        clipboardCount = 0, // SPEC GAP / missing module: no clipboard history yet (expansion-clipboard-pickers-launcher.md).
                        dictationActive = dictationController.isActive,
                        dictionaryInstalled = pipeline.resources.dictionaries.isNotEmpty(),
                        subtypeLocale = PRIMARY_LANGUAGE.value, // SPEC GAP / missing module: no subtype yet; the one bundled language.
                    ),
                )
            }.onFailure { error -> Log.e(TAG, "strip refresh crashed", error) }
        }
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

        /** spec: status-bar.md SS6.1, "undo and redo give the 25 ms haptic instead". */
        const val STRIP_FIXED_HAPTIC_MS = 25L

        /** spec: text-input.md SS2's one unified 240-character read. */
        const val TEXT_BEFORE_CURSOR_READ = 240
        val PRIMARY_LANGUAGE: LanguageCode = LanguageCode.of("en")!!
    }
}
