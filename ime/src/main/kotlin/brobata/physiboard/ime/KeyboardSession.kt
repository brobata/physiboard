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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.AppProfileResolver
import brobata.physiboard.core.text.EnterOverride
import brobata.physiboard.core.text.EnterOverrideResolver
import brobata.physiboard.core.text.MessagingPreset
import brobata.physiboard.device.titan.KeyNormalizer
import brobata.physiboard.device.titan.TitanLayouts

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

    // spec: dictation.md. `:core:speech` holds the session's own rules; this class only owns the
    // two facts only `:ime` can supply: which field is current, and whether a key reaching the
    // ordinary typing pipeline while dictation is listening means the user just edited the field
    // out from under it (spec: the c440844 fix, DictationController.onUserEditedComposingText's
    // own KDoc). `trigger` is exposed for a future key binding; this task does not wire one (its
    // own instructions), so nothing calls it yet.
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
        pipeline.onFinishInput()
        service.setCandidatesViewShown(false)
        dictationController.onEditorFieldClosed()
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
        val stroke = KeyNormalizer.normalize(
            keyCode = event.keyCode,
            scanCode = event.scanCode,
            action = event.action,
            repeatCount = event.repeatCount,
            metaState = event.metaState,
            deviceId = event.deviceId,
            eventTimeMs = event.eventTime,
        ) ?: return@runCatching false

        val ic = service.currentInputConnection ?: return@runCatching false
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
        consumed
    }.getOrElse { error ->
        Log.e(TAG, "onKeyEvent crashed on keyCode=${event.keyCode}; letting the raw key through", error)
        false
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
    // `command` mapping): every id below names a subsystem (dictation, layout switching, nav
    // mode, the assistant) that has no owning module yet (rebuild-from-scratch build order steps
    // 4-5). There is nothing for `:ime` to call, so every command is accepted and ignored rather
    // than guessed at; wiring a real handler later is a change to this one function.
    // -----------------------------------------------------------------------------------------

    private fun handleCommand(commandId: String) {
        // Intentionally empty; see the KDoc above.
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

    private fun refreshCandidatesStrip() {
        candidatesStrip?.update(pipeline.suggestions().map { it.word })
    }

    private companion object {
        const val TAG = "PhysiBoardKeyboard"
        const val HAPTIC_DURATION_MS = 10L
        val PRIMARY_LANGUAGE: LanguageCode = LanguageCode.of("en")!!
    }
}
