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
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import brobata.physiboard.core.actions.clipboard.Clip
import brobata.physiboard.core.actions.feedback.TapVibration
import brobata.physiboard.core.actions.launcher.AssignableKeys
import brobata.physiboard.core.actions.snippets.SnippetExpansion
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.keys.CharacterResolution
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyCommands
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.pointer.caret.CaretGeometry
import brobata.physiboard.core.pointer.caret.CaretUsability
import brobata.physiboard.core.pointer.caret.CursorAnchorReport
import brobata.physiboard.core.pointer.caret.CursorUpdateRequestPolicy
import brobata.physiboard.core.pointer.caret.CursorUpdateRequestState
import brobata.physiboard.core.pointer.caret.CursorUpdateRetrySchedule
import brobata.physiboard.core.pointer.trackpad.TrackpadActivationSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadGestureSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadPhysicalKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.speech.AssistantLaunch
import brobata.physiboard.core.speech.AssistantRequest
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
import brobata.physiboard.ime.actions.AndroidCommandCatalog
import brobata.physiboard.ime.actions.ClipboardHistoryController
import brobata.physiboard.ime.actions.ClipboardPanelController
import brobata.physiboard.ime.actions.CommandExecutor
import brobata.physiboard.ime.actions.EmojiAssets
import brobata.physiboard.ime.actions.EmojiPickerController
import brobata.physiboard.ime.actions.ExpansionPopupController
import brobata.physiboard.ime.actions.LauncherKeysController
import brobata.physiboard.ime.actions.QuickLauncherController
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

    // -----------------------------------------------------------------------------------------
    // Expansion, clipboard, the pickers, launcher keys. spec: expansion-clipboard-pickers-launcher.md.
    // Every decision is `:core:actions`' or the pipeline's; these controllers own the windows,
    // the clipboard listener, the database, the intents and the timers.
    // -----------------------------------------------------------------------------------------

    private val expansionPopup = ExpansionPopupController(service)
    private val expansionRefreshRunnable = Runnable { refreshExpansionFromEditor() }

    /** spec SS3.1: `clipboard_history_enabled` is read once; the store's first emission is that read (ClipboardHistoryController.applyEnabledOnce). */
    private val clipboard = ClipboardHistoryController(service, handler) { onClipboardChanged() }
    private val clipboardPanel = ClipboardPanelController(service)
    private val emojiAssets = EmojiAssets(service.assets, handler, Build.VERSION.SDK_INT)
    private val emojiPicker = EmojiPickerController(service, handler, emojiAssets)
    private var emojiPickerExpanded = false
    private var symAutoClose = true
    private var symAutoCloseOnTouch = true

    private val commandCatalog = AndroidCommandCatalog(service)
    private val quickLauncher = QuickLauncherController(service, handler, commandCatalog) { key, uppercase -> layoutText(key, uppercase) }
    private val commandExecutor = CommandExecutor(
        service,
        openQuickLauncher = { quickLauncher.open() },
        startVoiceAssistant = ::startVoiceAssistant,
        runNavAction = ::runNavAction,
    )
    private val launcherKeys = LauncherKeysController(service, handler, commandCatalog, commandExecutor, quickLauncher) { nowMs ->
        pipeline.onPowerShortcutTimeout(nowMs)
    }

    /** spec SS6.2 A: "the foreground package being one that answers the HOME intent (the list is queried once per service lifetime and cached)". */
    private val homePackages: Set<String> by lazy {
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            service.packageManager.queryIntentActivities(home, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    /** spec SS9.2: the suggestion-slot tap vibration rows. */
    private var tapHapticUseSystem = true
    private var tapHapticDurationMs = TapVibration.DEFAULT_DURATION_MS

    private fun layoutText(key: KeyId, uppercase: Boolean): String? =
        CharacterResolution.layoutOrDefaultCharacter(key, uppercase, tapIndex = 0, pipeline.layout.baseLayout)

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
        handler.removeCallbacks(expansionRefreshRunnable)
        runCatching { expansionPopup.hide(); clipboardPanel.hide(); emojiPicker.hide(); quickLauncher.onServiceDestroyed() }
            .onFailure { error -> Log.e(TAG, "panel teardown crashed", error) }
        clipboard.onServiceDestroyed()
        launcherKeys.onServiceDestroyed()
    }

    // SPEC GAP / missing module: there is no `:settings` module yet, so the primary suggestion
    // language cannot come from the current input style (dictionaries-languages.md SS8.7); `en`
    // is the only bundled dictionary today (docs/dictionaries.md), so it is the only one this
    // milestone can load regardless. Wiring a real subtype-driven language is a change to this
    // one line once a settings/subtype module exists.
    private val dictionaryLoader = DictionaryAssetLoader(service.assets, handler)

    /** The loaded dictionaries by language, primary and extras alike; [rebuildDictionaries] hands the wanted ones to the pipeline, primary first. */
    private val loadedDictionaries = linkedMapOf<LanguageCode, DictionaryIndex>()
    private val dictionaryLoadsInFlight = mutableSetOf<LanguageCode>()

    /**
     * spec: autocorrect-suggestions.md SS8.1: the bundled substitution rule sets, loaded once (a
     * few kilobytes, unlike a dictionary) and handed to [ImeSettings.ruleSets] on every settings
     * change. Only `auto_corrections_en.json` ships today; the other five bundled codes are a
     * documented gap ([ImeSettings.ruleSets]'s own KDoc).
     */
    private val bundledRuleSets = RuleSetAssetLoader(service.assets).loadAll(ImeSettings.BUNDLED_RULE_SET_CODES)

    /** spec: dictionaries-languages.md SS8.4, the active style's extra suggestion languages (`input_style_suggestion_locales`). */
    private var extraLanguages: List<LanguageCode> = emptyList()

    /** spec: dictation.md SS11.2, `assistant_action`; null is `auto`. */
    private var assistantRequest: AssistantRequest? = null

    init {
        quickLauncher.executor = commandExecutor
        quickLauncher.quickLauncherKey = pipeline.settings.launcherShortcuts.quickLauncherKeycode?.let(AssignableKeys::keyOf)
        // spec: status-bar.md SS6.1: the microphone button follows the recognizer's level reports.
        // Dictation starts asynchronously, so the first report is also the first moment the strip
        // can learn the session is active; the refresh is equality-guarded and cheap.
        dictationController.onActiveChanged = { _ -> runCatching { refreshCandidatesStrip() }.onFailure { error -> Log.e(TAG, "dictation state refresh crashed", error) } }
        dictationController.onAudioLevel = { level ->
            runCatching {
                refreshCandidatesStrip()
                statusBar?.setMicrophoneLevel(level)
            }.onFailure { error -> Log.e(TAG, "audio level crashed", error) }
        }
        // spec: autocorrect-suggestions.md SS2 point 3 and the "computation runs off the main
        // thread" rule: the keyboard must accept keystrokes immediately, typing with no
        // suggestions, and only start suggesting once this background load lands.
        loadDictionary(PRIMARY_LANGUAGE)
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

    /**
     * spec: autocorrect-suggestions.md SS2 point 3 and the "computation runs off the main thread"
     * rule: the keyboard must accept keystrokes immediately, typing with no suggestions, and only
     * start suggesting once the background load lands; dictionaries-languages.md SS8.4: an extra
     * language "that has not finished loading is simply absent... the load is scheduled and the
     * strip refreshes when it completes".
     */
    private fun loadDictionary(language: LanguageCode) {
        if (language in loadedDictionaries || !dictionaryLoadsInFlight.add(language)) return
        dictionaryLoader.loadAsync(language) { index ->
            dictionaryLoadsInFlight.remove(language)
            loadedDictionaries[language] = index
            rebuildDictionaries()
            refreshCandidatesStrip()
        }
    }

    /** spec SS8.4: "engines for languages no longer listed are dropped"; the primary comes first (`TextInputResources`' own contract). */
    private fun rebuildDictionaries() {
        val wanted = (listOf(PRIMARY_LANGUAGE) + extraLanguages).mapNotNull { loadedDictionaries[it] }
        if (wanted != pipeline.resources.dictionaries) pipeline.resources = pipeline.resources.copy(dictionaries = wanted)
    }

    /** One stored [Settings] value, handed to every module that takes a bundle; [ImeSettings] names which row feeds which field. */
    private fun applySettings(settings: Settings) {
        val previousStrip = pipeline.settings.statusBar
        pipeline.settings = ImeSettings.keyboardSettings(settings, PRIMARY_LANGUAGE.value)
        pipeline.layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), settings)
        // spec: status-bar.md SS9 and SS4: the theme, bar height and corner insets are the view's
        // construction facts, so a change to any of them rebuilds the candidates view; every other
        // strip row (visibility, apps, slots, the dip list) is read on the next refresh.
        val strip = pipeline.settings.statusBar
        if (statusBar != null && (strip.theme != previousStrip.theme || strip.barHeightDp != previousStrip.barHeightDp || strip.roundedCorners != previousStrip.roundedCorners)) {
            runCatching { service.setCandidatesView(onCreateCandidatesView()) }.onFailure { error -> Log.e(TAG, "strip rebuild crashed", error) }
        }
        // spec: autocorrect-suggestions.md SS8.2 and dictionaries-languages.md SS8.4: the rule
        // sets searched and the extra suggestion languages both come from the store.
        pipeline.resources = pipeline.resources.copy(ruleSets = ImeSettings.ruleSets(settings, java.util.Locale.getDefault().language, bundledRuleSets))
        extraLanguages = ImeSettings.extraSuggestionLanguages(settings, PRIMARY_LANGUAGE, PRIMARY_LANGUAGE.value)
        rebuildDictionaries()
        extraLanguages.forEach(::loadDictionary)
        // spec: trackpad-caret-nav.md SS4.7: "if the setting's value differs from the last one
        // seen, the counters reset and the request is re-issued".
        val badge = ImeSettings.caretBadge(settings)
        val badgeSwitchChanged = caretBadge.settings.enabled != badge.enabled
        caretBadge.settings = badge
        if (badgeSwitchChanged) {
            cursorUpdateState = CursorUpdateRequestState()
            scheduleCursorUpdateRetries()
            refreshCaretBadge()
        }
        assistantRequest = ImeSettings.assistantRequest(settings)
        trackpad.activationSettings = ImeSettings.trackpadActivation(settings)
        trackpad.gestureSettings = ImeSettings.trackpadGesture(settings)
        appProfiles = ImeSettings.appProfiles(settings)
        enterOverrides = ImeSettings.enterOverrides(settings)
        enterPreset = settings.perApp.enterPreset
        enterBehaviorEnabled = settings.perApp.enterBehaviorEnabled
        dictationController.settings = ImeSettings.dictationSettings(settings, Build.VERSION.SDK_INT)
        dictationController.textSettings = ImeSettings.dictationTextSettings(dictationController.textSettings, settings)
        clipboard.retentionMinutes = settings.expansion.clipboardRetentionMinutes
        clipboard.applyEnabledOnce(settings.expansion.clipboardHistoryEnabled)
        emojiPickerExpanded = settings.symPages.emojiPickerExpandedHeight
        symAutoClose = settings.symPages.autoClose
        symAutoCloseOnTouch = settings.symPages.autoCloseOnTouch
        quickLauncher.settings = ImeSettings.quickLauncherSettings(settings)
        quickLauncher.customizations = ImeSettings.commandCustomizations(settings)
        quickLauncher.visibility = ImeSettings.sourceVisibility(settings)
        quickLauncher.quickLauncherKey = pipeline.settings.launcherShortcuts.quickLauncherKeycode?.let(AssignableKeys::keyOf)
        quickLauncher.executor = commandExecutor
        tapHapticUseSystem = settings.feedback.tapHapticUseSystem
        tapHapticDurationMs = settings.feedback.tapHapticDurationMs
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
    /** The editor's last report said the selection was collapsed; a passed-through Backspace on a real selection lands somewhere this side cannot predict. */
    private var lastReportedSelectionCollapsed = true

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
        DiagnosticLog.i(TAG) { "field: pkg=$reportedPackage restarting=$restarting inputType=0x${Integer.toHexString(info?.inputType ?: 0)} caps=${field.capFlags} kind=${field.kind} trust=${profile.editorTrust}" }
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
            val textBeforeCursor = runCatching { service.currentInputConnection?.getTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString() }.getOrNull()
            pipeline.onStartInput(field, profile.editorTrust, profile, textBeforeCursor)
        }
        service.setCandidatesViewShown(field.isReallyEditable)
        currentPackageName = reportedPackage
        // spec expansion-clipboard-pickers-launcher.md SS6.2 A: the home screen path needs the foreground launcher.
        pipeline.foregroundIsHome = reportedPackage != null && reportedPackage in homePackages
        dictationController.onEditorFieldOpened(reportedPackage)
        clipboard.onFieldStarted()
        // spec SS2.4: matches are cleared "on every start of input".
        handler.removeCallbacks(expansionRefreshRunnable)
        expansionPopup.hide()
        syncSymPanels()
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
        clipboard.onFieldFinished()
        handler.removeCallbacks(expansionRefreshRunnable)
        expansionPopup.hide()
        emojiPicker.onAppSelectionChanged()
        syncSymPanels()
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
            expansionPopup.hide()
            syncSymPanels()
        }.onFailure { error -> Log.e(TAG, "onKeyboardWindowHidden crashed", error) }
    }

    /** spec: status-bar.md SS13, "When the window is shown again the strip is refreshed immediately." */
    fun onKeyboardWindowShown() {
        runCatching {
            val textBeforeCursor = runCatching { service.currentInputConnection?.getTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString() }.getOrNull()
            pipeline.onWindowShown(textBeforeCursor)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "onKeyboardWindowShown crashed", error) }
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
            val bar = statusBar
            val loc = IntArray(2).also { bar?.getLocationInWindow(it) }
            val stripTop = loc[1].takeIf { bar != null && bar.isShown && bar.height > 0 }
            // This keyboard never shows an input view (the service's onEvaluateInputViewShown is
            // false), so the window is always in candidates-only mode. The platform's own
            // isInputViewShown() cannot be asked: on Android 16 it returns whether the window
            // is visible at all, which was true here and left the app told to make room for
            // nothing, so the strip floated over its text box (Titan, 2026-09-25).
            val decision = StripInsets.decide(
                candidatesOnly = true,
                contentTopPx = outInsets.contentTopInsets,
                visibleTopPx = outInsets.visibleTopInsets,
                windowWidthPx = decor?.width ?: 0,
                windowHeightPx = decor?.height ?: 0,
                stripTopPx = stripTop,
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
            // spec SS4.7: reports are asked for while the badge (or the emoji-picker search) needs
            // them; "with neither, a request with no flags is issued to turn monitoring off".
            // SPEC GAP: the emoji picker's search does not read the caret yet, so its half is false.
            val wanted = CursorUpdateRequestPolicy.wantsReports(caretBadge.settings.enabled, emojiSearchNeedsCaret = false)
            val flags = if (wanted) InputConnection.CURSOR_UPDATE_IMMEDIATE or InputConnection.CURSOR_UPDATE_MONITOR else 0
            if (ic.requestCursorUpdates(flags)) {
                cursorUpdateState = CursorUpdateRetrySchedule.onRequestAccepted(cursorUpdateState)
            }
        }.onFailure { error -> Log.e(TAG, "requestCursorUpdates crashed", error) }
    }

    fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        lastReportedSelStart = newSelStart
        lastReportedSelectionCollapsed = newSelStart == newSelEnd
        ownEdit?.let { expectation ->
            val verdict = expectation.classify(newSelStart, SystemClock.uptimeMillis())
            DiagnosticLog.i(TAG) { "selection: $oldSelStart->$newSelStart verdict=$verdict" }
            when (verdict) {
                OwnEditExpectation.Verdict.OWN_EDIT -> {
                    ownEdit = expectation.copy(matched = true)
                    return
                }
                OwnEditExpectation.Verdict.STILL_SETTLING -> return
                OwnEditExpectation.Verdict.EXTERNAL -> ownEdit = null
            }
        }
        runCatching {
            val textBeforeCursor = runCatching { service.currentInputConnection?.getTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString() }.getOrNull()
            DiagnosticLog.i(TAG) { "selection external: $oldSelStart->$newSelStart textBefore='${textBeforeCursor?.takeLast(12)}'" }
            pipeline.onExternalSelectionChange(textBeforeCursor, selectionCollapsed = newSelStart == newSelEnd)
            // spec SS4.5: the app's own caret moved between two captured keys, so capture drops.
            emojiPicker.onAppSelectionChanged()
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
        // spec expansion-clipboard-pickers-launcher.md SS4.5: while page 4's search captures, hardware keys type into it.
        if (emojiPicker.isShown && emojiPicker.captureOn && emojiPicker.onHardwareKey(event, normalizeStroke(event)?.key)) return@runCatching true
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
        val glyphBefore = pipeline.modifierGlyphInput()
        val result = pipeline.onKeyStroke(stroke, readout.snapshot)
        val consumed = applyResult(ic, result, readout)
        if (result.appMayEditField && stroke.edge == KeyEdge.DOWN && !AppliedEditAccounting.movesCursor(result.ops)) {
            AppliedEditAccounting.expectedCursorAfterPassThrough(stroke.key, readout.cursorAbsolute, hasSelection = !lastReportedSelectionCollapsed)?.let { expected ->
                ownEdit = OwnEditExpectation(selStart = expected, expiresAtMs = SystemClock.uptimeMillis() + OwnEditExpectation.SETTLE_WINDOW_MS)
            }
        }
        if (stroke.edge == KeyEdge.DOWN) {
            val g = pipeline.modifierGlyphInput()
            DiagnosticLog.i(TAG) { "stroke: ${stroke.key} shiftMeta=${stroke.meta.shift} before[caps=${glyphBefore.capsLockOn} oneShot=${glyphBefore.shiftOneShotArmed}] after[caps=${g.capsLockOn} oneShot=${g.shiftOneShotArmed}] textBefore='${readout.snapshot.textBeforeCursor?.takeLast(12)}' ops=${result.ops} dicts=${pipeline.resources.dictionaries.size} sugg=${runCatching { pipeline.suggestions().map { it.word } }.getOrDefault(emptyList())}" }
        }
        scheduleLongPressIfNeeded()
        // spec expansion-clipboard-pickers-launcher.md SS6.2: an assigned key fired, or the Sym-armed mode just armed.
        result.launcherKey?.let { decision -> runCatching { launcherKeys.perform(decision) }.onFailure { error -> Log.e(TAG, "launcher key crashed", error) } }
        result.powerModeArmedAtMs?.let { at -> launcherKeys.onPowerModeArmed(at) { pipeline.powerShortcutArmedAtMs == it } }
        if (pipeline.powerShortcutArmedAtMs == null) launcherKeys.onPowerModeDisarmed()
        syncSymPanels()
        // spec SS2.4: the lookup is "scheduled, coalesced to one run 24 ms after the last request: after every hardware key release that is not a pure modifier".
        if (stroke.edge == KeyEdge.UP && stroke.key !is KeyId.Modifier) {
            handler.removeCallbacks(expansionRefreshRunnable)
            handler.postDelayed(expansionRefreshRunnable, SnippetExpansion.LOOKUP_DELAY_MS)
        }
        refreshCandidatesStrip()
        return consumed
    }

    // -----------------------------------------------------------------------------------------
    // Text expansion's popup. spec: expansion-clipboard-pickers-launcher.md SS2.4, SS2.5.
    // -----------------------------------------------------------------------------------------

    private fun refreshExpansionFromEditor() {
        runCatching {
            val text = service.currentInputConnection?.getTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString()
            pipeline.refreshExpansion(text)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "expansion refresh crashed", error) }
    }

    private fun refreshExpansionPopup() {
        val rows = pipeline.expansionPopupRows()
        if (rows.isEmpty() || !service.isInputViewShown && statusBar?.isRenderedOnScreen() != true) {
            expansionPopup.hide()
            return
        }
        expansionPopup.render(rows, pipeline.expansionHighlight, aboveBottomPx = stripHeightPx()) { index -> onExpansionRowTapped(index) }
    }

    private fun onExpansionRowTapped(index: Int) {
        runCatching {
            val ic = service.currentInputConnection ?: return@runCatching
            val readout = ic.readEditorState(SystemClock.uptimeMillis(), wholeDocument = false, fallbackCursorAbsolute = lastReportedSelStart)
            applyResult(ic, pipeline.onExpansionRowTapped(index, readout.snapshot), readout)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "expansion row tap crashed", error) }
    }

    private fun stripHeightPx(): Int = statusBar?.takeIf { it.isRenderedOnScreen() }?.height ?: 0

    // -----------------------------------------------------------------------------------------
    // The Sym panels: the clipboard history (page 3) and the emoji picker (page 4).
    // spec: expansion-clipboard-pickers-launcher.md SS3.5, SS4; layers-sym-alt.md SS4.3, SS5.4.
    // -----------------------------------------------------------------------------------------

    private val clipboardPanelListener = object : ClipboardPanelController.Listener {
        override fun onClipTapped(clip: Clip) {
            // spec SS3.5: committed "as finished text (no composing, no auto-space, no autocorrect)"; the panel stays open.
            commitFinishedText(clip.text)
        }

        override fun onTogglePinned(clip: Clip) {
            clipboard.togglePinned(clip.id)
            clipboardPanel.refresh(clipboard.history, this, scrollToTop = true, force = true)
        }

        override fun onDelete(clip: Clip) = clipboard.delete(clip.id)
        override fun onClearAll() = clipboard.clearAll()
        override fun onClose() = closeSymPanel()
    }

    private val emojiPickerListener = object : EmojiPickerController.Listener {
        override fun onEmojiChosen(emoji: String) {
            // spec SS4.4: with `sym_auto_close` and `sym_auto_close_on_touch` both on, the page closes first and the commit is posted after.
            if (symAutoClose && symAutoCloseOnTouch) {
                closeSymPanel()
                handler.post { commitFinishedText(emoji) }
            } else {
                commitFinishedText(emoji)
            }
        }

        override fun onClose() = closeSymPanel()
        override fun layoutText(key: KeyId, uppercase: Boolean): String? = this@KeyboardSession.layoutText(key, uppercase)
    }

    private fun commitFinishedText(text: String) {
        runCatching {
            val ic = service.currentInputConnection ?: return@runCatching
            ic.beginBatchEdit()
            ic.finishComposingText()
            ic.commitText(text, 1)
            ic.endBatchEdit()
            noteFieldEditedDuringDictation()
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "panel commit crashed", error) }
    }

    private fun closeSymPanel() {
        pipeline.closeSymPage()
        syncSymPanels()
        refreshCandidatesStrip()
    }

    /** Shows or hides the two panels to match the pipeline's open Sym page (0, 3 or 4). */
    private fun syncSymPanels() {
        runCatching {
            val page = pipeline.currentSymPage
            val theme = pipeline.settings.statusBar.theme
            if (page == brobata.physiboard.core.strip.SYM_PAGE_CLIPBOARD) {
                clipboard.cleanup(forced = true)
                clipboardPanel.show(clipboard.history, theme, stripHeightPx(), clipboardPanelListener)
            } else {
                clipboardPanel.hide()
            }
            if (page == brobata.physiboard.core.strip.SYM_PAGE_EMOJI_PICKER) {
                emojiPicker.show(emojiPickerExpanded, theme, stripHeightPx(), emojiPickerListener)
            } else {
                emojiPicker.hide()
            }
        }.onFailure { error -> Log.e(TAG, "sym panel sync crashed", error) }
    }

    private fun onClipboardChanged() {
        runCatching {
            if (clipboardPanel.isShown) clipboardPanel.refresh(clipboard.history, clipboardPanelListener, scrollToTop = false)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "clipboard change crashed", error) }
    }

    // -----------------------------------------------------------------------------------------
    // What the command executor cannot do on its own. spec SS8.2.
    // -----------------------------------------------------------------------------------------

    /**
     * spec SS8.2 `pastiera.voice_assistant`: "Open it already listening"; dictation.md SS11.2's
     * launch: the requests in `assistant_action`'s order ([AssistantLaunch]), first targeted at the
     * assistant package `Settings.Secure` names (steps 2 and 3, "targeting avoids the system
     * chooser"), then untargeted (step 4); "the first that starts wins".
     */
    private fun startVoiceAssistant(): Boolean {
        val order = AssistantLaunch.order(assistantRequest)
        val assistantPackage = assistantPackageName()
        if (assistantPackage != null && order.any { startAssistantIntent(it, assistantPackage) }) return true
        return order.any { startAssistantIntent(it, targetPackage = null) }
    }

    private fun startAssistantIntent(request: AssistantRequest, targetPackage: String?): Boolean = runCatching {
        val intent = Intent(request.intentAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        targetPackage?.let(intent::setPackage)
        if (intent.resolveActivity(service.packageManager) == null) return false
        service.startActivity(intent)
        true
    }.getOrDefault(false)

    /** spec dictation.md SS11.2 step 2: "the first non-empty of `Settings.Secure` keys `assistant` and `voice_interaction_service`, taken as a flattened component's package, or as a bare package name". */
    private fun assistantPackageName(): String? = runCatching {
        listOf("assistant", "voice_interaction_service")
            .firstNotNullOfOrNull { key -> android.provider.Settings.Secure.getString(service.contentResolver, key)?.takeIf { it.isNotBlank() } }
            ?.let { raw -> android.content.ComponentName.unflattenFromString(raw)?.packageName ?: raw }
    }.getOrNull()

    /** spec SS8.2 "Navigation": a keycode is sent as a key pair; the four editing actions go through the editor's context menu; the rest have no owning module yet. */
    private fun runNavAction(mappingType: String, value: String): Boolean {
        val ic = service.currentInputConnection ?: return false
        if (mappingType == "keycode") {
            val keyCode = runCatching { KeyEvent.keyCodeFromString("KEYCODE_$value") }.getOrDefault(KeyEvent.KEYCODE_UNKNOWN)
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return false
            val now = SystemClock.uptimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
            return true
        }
        val menuId = when (value) {
            "copy" -> android.R.id.copy
            "paste" -> android.R.id.paste
            "cut" -> android.R.id.cut
            "select_all" -> android.R.id.selectAll
            else -> return false
        }
        return ic.performContextMenuAction(menuId)
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
        when (commandId) {
            KeyCommands.TOGGLE_DICTATION -> runCatching { onDictationTrigger() }.onFailure { error -> Log.e(TAG, "dictation trigger crashed", error) }
            // spec: keys-and-modifiers.md SS4.4: the Sym hold "launches the assistant already listening"; "if no assistant is available... a toast".
            KeyCommands.LAUNCH_ASSISTANT -> runCatching {
                if (!startVoiceAssistant()) android.widget.Toast.makeText(service, brobata.physiboard.core.actions.commands.CommandFailure.NO_VOICE_ASSISTANT, android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure { error -> Log.e(TAG, "assistant launch crashed", error) }
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
            performSlotTapHaptic()
            val ic = service.currentInputConnection ?: return@runCatching
            // spec expansion-clipboard-pickers-launcher.md SS2.5, suggestion bar: "Tapping a slot commits that match with no trailing space."
            if (pipeline.expansionOwnsStripSlots) {
                val index = pipeline.expansionPopupRowsForBar().indexOfFirst { it.label == slot.text }
                if (index >= 0) {
                    val readout = ic.readEditorState(SystemClock.uptimeMillis(), wholeDocument = false, fallbackCursorAbsolute = lastReportedSelStart)
                    applyResult(ic, pipeline.onExpansionRowTapped(index, readout.snapshot), readout)
                    refreshCandidatesStrip()
                    return@runCatching
                }
            }
            val readout = ic.readEditorState(SystemClock.uptimeMillis(), wholeDocument = true, fallbackCursorAbsolute = lastReportedSelStart)
            val result = pipeline.onAcceptSuggestion(slot.text, readout.snapshot)
            applyResult(ic, result, readout)
            refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "slot tap crashed", error) }
    }

    /** spec SS9.2: slot taps use the system keyboard-tap haptic, or a fixed one-shot when `tap_haptic_use_system` is off; strip buttons always use the system one. */
    private fun performSlotTapHaptic() {
        when (val effect = TapVibration.slotTapEffect(tapHapticUseSystem, tapHapticDurationMs)) {
            TapVibration.Effect.SystemKeyboardTap -> if (statusBar?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) != true) performHaptic()
            is TapVibration.Effect.OneShot -> performHaptic(effect.durationMs)
        }
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
                DiagnosticLog.i(TAG) { "language button: no input-style switching yet (placeholder)" }
            }
            StripAction.OpenSettings -> openOwnApp()
            // spec layers-sym-alt.md SS4.3: the clipboard and emoji picker buttons open their page directly and toggle; the key layers still have no surface.
            is StripAction.OpenSymPage -> if (action.page == brobata.physiboard.core.strip.SYM_PAGE_CLIPBOARD || action.page == brobata.physiboard.core.strip.SYM_PAGE_EMOJI_PICKER) {
                pipeline.toggleSymPage(action.page)
                syncSymPanels()
            } else {
                DiagnosticLog.i(TAG) { "${button.id}: Sym page ${action.page} has no surface yet (placeholder)" }
            }
            StripAction.OpenQuickActions -> DiagnosticLog.i(TAG) { "quick actions overlay not built yet (placeholder)" }
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
                        clipboardCount = clipboard.count, // spec expansion-clipboard-pickers-launcher.md SS3.4: the count is pushed on every refresh.
                        dictationActive = dictationController.isActive,
                        dictionaryInstalled = pipeline.resources.dictionaries.isNotEmpty(),
                        subtypeLocale = PRIMARY_LANGUAGE.value, // SPEC GAP / missing module: no subtype yet; the one bundled language.
                        clipboardOverlayOpen = clipboardPanel.isShown,
                    ),
                )
            }.onFailure { error -> Log.e(TAG, "strip refresh crashed", error) }
        }
        runCatching { refreshExpansionPopup() }.onFailure { error -> Log.e(TAG, "expansion popup crashed", error) }
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
