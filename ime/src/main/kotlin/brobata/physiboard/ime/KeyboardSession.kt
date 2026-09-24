package brobata.physiboard.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.AppProfileResolver
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
) {

    // SPEC GAP / missing module: the Titan 2 Elite is the only device this build ships to (this
    // module's own rebuild plan), so the layout is not yet selectable; when a settings/layout
    // module exists this becomes a caller-supplied value instead of a constant.
    private val pipeline = KeyboardPipeline(layout = TitanLayouts.titan2EliteQwerty(), onCommand = ::handleCommand)

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { onLongPressTick() }

    private var candidatesStrip: CandidatesStripView? = null

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
        val profile = AppProfileResolver.resolve(info?.packageName, appProfiles, webApkHost)
        val field = classifyField(info, profile)
        pipeline.onStartInput(field, profile.editorTrust)
        service.setCandidatesViewShown(field.isReallyEditable)
        refreshCandidatesStrip()
    }

    fun onFinishInput() {
        handler.removeCallbacks(longPressRunnable)
        pipeline.onFinishInput()
        service.setCandidatesViewShown(false)
    }

    fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        if (suppressNextSelectionUpdate) {
            suppressNextSelectionUpdate = false
            return
        }
        val textBeforeCursor = runCatching { service.currentInputConnection?.getTextBeforeCursor(240, 0)?.toString() }.getOrNull()
        pipeline.onExternalSelectionChange(textBeforeCursor)
        refreshCandidatesStrip()
    }

    // -----------------------------------------------------------------------------------------
    // Key events
    // -----------------------------------------------------------------------------------------

    /** True when PhysiBoard consumed the event and the app must not see it. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val stroke = KeyNormalizer.normalize(
            keyCode = event.keyCode,
            scanCode = event.scanCode,
            action = event.action,
            repeatCount = event.repeatCount,
            metaState = event.metaState,
            deviceId = event.deviceId,
            eventTimeMs = event.eventTime,
        ) ?: return false

        val ic = service.currentInputConnection ?: return false
        val readout = ic.readEditorState(stroke.timeMs)
        val result = pipeline.onKeyStroke(stroke, readout.snapshot)
        applyResult(ic, result, readout)
        scheduleLongPressIfNeeded()
        refreshCandidatesStrip()
        return result.consumed
    }

    private fun onLongPressTick() {
        val ic = service.currentInputConnection ?: return
        val nowMs = SystemClock.uptimeMillis()
        val readout = ic.readEditorState(nowMs)
        val result = pipeline.checkLongPressTick(nowMs, readout.snapshot) ?: return
        applyResult(ic, result, readout)
        refreshCandidatesStrip()
    }

    private fun scheduleLongPressIfNeeded() {
        handler.removeCallbacks(longPressRunnable)
        val deadline = pipeline.pendingLongPressDeadlineMs ?: return
        val delay = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(longPressRunnable, delay)
    }

    private fun applyResult(ic: InputConnection, result: PipelineResult, readout: EditorReadout) {
        if (result.ops.isEmpty()) return
        suppressNextSelectionUpdate = true
        ic.applyEditorOps(
            ops = result.ops,
            windowStartOffset = readout.documentStartOffset,
            cursorAbsolute = readout.cursorAbsolute,
            sendSpaceKeyFallback = { ic.sendSpaceKeyFallback(SystemClock.uptimeMillis()) },
            haptic = ::performHaptic,
        )
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
        val ic = service.currentInputConnection ?: return
        val readout = ic.readEditorState(SystemClock.uptimeMillis())
        val result = pipeline.onAcceptSuggestion(word, readout.snapshot)
        applyResult(ic, result, readout)
        refreshCandidatesStrip()
    }

    private fun refreshCandidatesStrip() {
        candidatesStrip?.update(pipeline.suggestions().map { it.word })
    }

    private companion object {
        const val HAPTIC_DURATION_MS = 10L
        val PRIMARY_LANGUAGE: LanguageCode = LanguageCode.of("en")!!
    }
}
