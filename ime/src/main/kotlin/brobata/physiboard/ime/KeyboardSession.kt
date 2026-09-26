package brobata.physiboard.ime

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
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
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import androidx.core.content.ContextCompat
import brobata.physiboard.core.actions.clipboard.Clip
import brobata.physiboard.core.actions.feedback.SoundGroup
import brobata.physiboard.core.actions.feedback.TapVibration
import brobata.physiboard.core.actions.feedback.TypingSoundMode
import brobata.physiboard.core.actions.feedback.TypingSounds
import brobata.physiboard.core.actions.launcher.AssignableKeys
import brobata.physiboard.core.actions.launcher.AssignmentSheet
import brobata.physiboard.core.actions.picker.AddSubstitutionSheet
import brobata.physiboard.core.actions.picker.SymCustomizationLink
import brobata.physiboard.core.actions.snippets.SnippetExpansion
import brobata.physiboard.core.dict.DictionaryBroadcastActions
import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.dict.WordSource
import brobata.physiboard.core.keys.CharacterResolution
import brobata.physiboard.core.keys.EditEffect
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyCommands
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierIconState
import brobata.physiboard.core.keys.StatusBarIcon
import brobata.physiboard.core.keys.StatusBarModifierIcon
import brobata.physiboard.core.pointer.caret.CaretGeometry
import brobata.physiboard.core.pointer.caret.CaretUsability
import brobata.physiboard.core.pointer.caret.CursorAnchorReport
import brobata.physiboard.core.pointer.caret.CursorUpdateRequestPolicy
import brobata.physiboard.core.pointer.caret.CursorUpdateRequestState
import brobata.physiboard.core.pointer.caret.CursorUpdateRetrySchedule
import brobata.physiboard.core.pointer.keyboardswipe.FirmwareSwipeKeycode
import brobata.physiboard.core.pointer.keyboardswipe.FirmwareSwipeResult
import brobata.physiboard.core.pointer.keyboardswipe.KeyboardSwipeSettings
import brobata.physiboard.core.pointer.keyboardswipe.KeyboardSwipeUpDecision
import brobata.physiboard.core.pointer.keyboardswipe.SwipeEvaluation
import brobata.physiboard.core.pointer.keyboardswipe.SwipeSlot
import brobata.physiboard.core.pointer.keyboardswipe.SwipeThird
import brobata.physiboard.core.pointer.keyboardswipe.SwipeUpAction
import brobata.physiboard.core.pointer.keyboardswipe.SwipeUpGate
import brobata.physiboard.core.pointer.navmode.NavModeTransition
import brobata.physiboard.core.pointer.trackpad.TrackpadActivationSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadGestureSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadPhysicalKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.TypingSoundOutputMode
import brobata.physiboard.core.shell.AutocorrectionRecord
import brobata.physiboard.core.shell.ImeContextSnapshot
import brobata.physiboard.core.shell.KeyboardEventRecord
import brobata.physiboard.core.speech.AssistantLaunch
import brobata.physiboard.core.speech.AssistantRequest
import brobata.physiboard.core.subtype.AdditionalSubtypeBuilder
import brobata.physiboard.core.subtype.InputStyle
import brobata.physiboard.core.subtype.InputStyleCatalog
import brobata.physiboard.core.subtype.LocaleLayoutMapping
import brobata.physiboard.core.subtype.ShippedLayout
import brobata.physiboard.core.strip.BacklightNudge
import brobata.physiboard.core.strip.BacklightNudgeEpisode
import brobata.physiboard.core.strip.BacklightNudgeMemory
import brobata.physiboard.core.strip.BacklightNudgeVisibility
import brobata.physiboard.core.strip.DipEffect
import brobata.physiboard.core.strip.LanguageTapDebounce
import brobata.physiboard.core.strip.Slot
import brobata.physiboard.core.strip.SlotActionButton
import brobata.physiboard.core.strip.SlotKind
import brobata.physiboard.core.strip.SlotPosition
import brobata.physiboard.core.strip.SlotTextSizeSp
import brobata.physiboard.core.strip.StripAction
import brobata.physiboard.core.strip.StripButton
import brobata.physiboard.core.strip.StripDip
import brobata.physiboard.core.strip.StripGeometry
import brobata.physiboard.core.strip.StripInsets
import brobata.physiboard.core.strip.SuggestionRow
import brobata.physiboard.core.strip.SuggestionRowRules
import brobata.physiboard.core.strip.SurfaceTransitionOutcome
import brobata.physiboard.core.strip.SurfaceTransitionRetry
import brobata.physiboard.core.strip.SurfaceTransitionState
import brobata.physiboard.core.strip.SymGridPage
import brobata.physiboard.core.strip.TapHaptic
import brobata.physiboard.core.strip.TouchableArea
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.AppProfileResolver
import brobata.physiboard.core.text.BoundaryDebugInfo
import brobata.physiboard.core.text.CurrentWordTracker
import brobata.physiboard.core.text.EnterIntent
import brobata.physiboard.core.text.EnterOverride
import brobata.physiboard.core.text.EnterOverrideResolver
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.MessagingPreset
import brobata.physiboard.device.titan.DeviceIdentity
import brobata.physiboard.device.titan.KeyNormalizer
import brobata.physiboard.device.titan.TitanLayouts
import brobata.physiboard.device.titan.VendorKeyCodes
import brobata.physiboard.ime.actions.AndroidCommandCatalog
import brobata.physiboard.ime.actions.ClipboardHistoryController
import brobata.physiboard.ime.actions.ClipboardPanelController
import brobata.physiboard.ime.actions.CommandExecutor
import brobata.physiboard.ime.actions.EmojiAssets
import brobata.physiboard.ime.actions.EmojiPickerController
import brobata.physiboard.ime.actions.ExpansionPopupController
import brobata.physiboard.ime.actions.LauncherKeysController
import brobata.physiboard.ime.actions.QuickLauncherController
import brobata.physiboard.ime.actions.SymGridPanelController
import brobata.physiboard.ime.actions.TypingSoundPlayer
import brobata.physiboard.ime.pointer.CaretBadgeOverlayController
import brobata.physiboard.ime.pointer.KeyboardSwipeController
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
    // app-shell.md SS10.2, SS10.7: the Diagnostics screen's capture store, owned by `:app`; a null
    // sink (a JVM test, or a host without the wiring) leaves this session silent, same as [settingsSource].
    private val debugCaptureSink: DebugCaptureSink? = null,
) {

    /** Collects [settingsSource] on the main looper for the session's lifetime; cancelled in [onServiceDestroyed]. */
    private val settingsScope = MainScope()

    // -----------------------------------------------------------------------------------------
    // Layout switching. spec: dictionaries-languages.md SS8, SS9; keys-and-modifiers.md SS7.5.
    // `:core:subtype` owns the available styles, the cycle order and what one switch changes;
    // this class supplies the shipped layout catalog, the store's own settings, and the real
    // dictionary-load/Toast calls only `:ime` can make.
    // -----------------------------------------------------------------------------------------

    /** layers-sym-alt.md SS9.2: every bundled layout `:device:titan`'s `TitanLayouts.bundled()` ships, `qwerty` first so it stays the startup default (`SHIPPED_LAYOUT_ID`, [ImeSettings.DEFAULT_SUBTYPE_LOCALE]). */
    private val shippedLayouts: List<ShippedLayout> =
        TitanLayouts.bundled().map { (layoutId, defaultLocale, layout) -> ShippedLayout(layoutId = layoutId, defaultLocale = defaultLocale, layout = layout) }

    /** spec dictionaries-languages.md SS9: the style the keyboard currently types with. Reassigned by [applySettings] (a settings-driven refresh) and [switchToNextInputStyle] (a chord, the language button, or a future settings-screen switch). */
    private var currentStyle: InputStyle = InputStyle(locale = shippedLayouts.first().defaultLocale, layoutId = shippedLayouts.first().layoutId, shipped = true)

    /** spec SS8.7: the active style's own language, the one `:core:dict` loads a dictionary for. Kept alongside [currentStyle] rather than derived on every use, since [rebuildDictionaries] and the dictionary loader both need it. */
    private var primaryLanguage: LanguageCode = LanguageCode.fromLocale(currentStyle.locale) ?: LanguageCode.of(ImeSettings.DEFAULT_SUBTYPE_LOCALE)!!

    /** spec dictionaries-languages.md SS10: `keyboard_layout_auto_by_locale` picks the starting style once, at the first settings emission; every later emission keeps [currentStyle] if it still exists in the (possibly changed) available list instead of re-picking it from the system locale. */
    private var startupStyleChosen = false

    /** The last [Settings] [applySettings] received, kept so [switchToNextInputStyle] can reapply everything a switch changes without waiting for the store to re-emit. */
    private var lastSettings: Settings = Settings()

    private val pipeline = KeyboardPipeline(layout = shippedLayouts.first().layout, onCommand = ::handleCommand)

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

    // -----------------------------------------------------------------------------------------
    // The keyboard-surface swipe (upstream "trackpad gestures"), a different gesture on a
    // different surface than the screen trackpad above. spec: trackpad-caret-nav.md SS3. Off by
    // default (SS3.7); [applySettings] feeds the live preference values in.
    // -----------------------------------------------------------------------------------------

    private val keyboardSwipe = KeyboardSwipeController(
        settings = KeyboardSwipeSettings(),
        isEligibleDevice = {
            DeviceIdentity.isTitan2EliteQwerty(Build.BRAND, Build.MANUFACTURER, Build.MODEL, Build.DEVICE, Build.PRODUCT, Build.BOARD, Build.DISPLAY)
        },
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
    // Touch-screen-awake. spec: trackpad-caret-nav.md SS6. No preference; wired unconditionally
    // to the candidates view (the "chrome layout") in [onCreateCandidatesView].
    // -----------------------------------------------------------------------------------------

    private val touchAwakeLock = TouchAwakeLock(service)

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
    /** spec layers-sym-alt.md SS5.7: the on-screen grid for the Emoji (page 1) and Symbols (page 2) key layers. */
    private val symGridPanel = SymGridPanelController(service)
    private var emojiPickerExpanded = false
    private var symAutoClose = true
    private var symAutoCloseOnTouch = true

    private val commandCatalog = AndroidCommandCatalog(service)
    private val quickLauncher = QuickLauncherController(service, handler, commandCatalog) { key, uppercase -> layoutText(key, uppercase) }
        .apply {
            // spec expansion-clipboard-pickers-launcher.md SS6.2/SS7.1: at most one bottom overlay
            // shown at once. The Sym-grid/clipboard/emoji trio is already exclusive among itself
            // (one `currentSymPage` int); this is the other half, closing that trio whenever the
            // quick launcher is the one that just opened (see [syncSymPanels] for the reverse).
            onOpened = { closeSymPanel() }
        }
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

    /**
     * spec: expansion-clipboard-pickers-launcher.md SS9.1. Rebuilt in [applySettings] whenever
     * `typing_sound_mode` or `typing_sound_output_mode` changes, released in [onServiceDestroyed].
     */
    private var typingSoundPlayer: TypingSoundPlayer? = null
    private var typingSoundMode = TypingSoundMode.OFF
    private var typingSoundOutputMode = TypingSoundOutputMode.MEDIA

    private fun layoutText(key: KeyId, uppercase: Boolean): String? =
        CharacterResolution.layoutOrDefaultCharacter(key, uppercase, tapIndex = 0, pipeline.layout.baseLayout)

    /** The editor's last usable cursor-anchor report, or null; SS4.6, "forgotten... when the editor finishes". */
    private var lastCaretGeometry: CaretGeometry? = null

    /** spec: SS4.7's retry bookkeeping, one instance per editor (reset in [onStartInput]). */
    private var cursorUpdateState = CursorUpdateRequestState()

    /** Groups every scheduled cursor-update retry so [onStartInput]/[onFinishInput] can cancel them all in one call. */
    private val cursorUpdateToken = Any()

    /**
     * spec: autocorrect-suggestions.md SS1.2: "The cursor moves ... 120 ms debounce; a second
     * move within 120 ms cancels the first read." Groups the tracker resync so a second selection
     * change arriving before the delay elapses cancels the pending one rather than both running.
     */
    private val selectionSyncToken = Any()

    // spec: dictation.md. `:core:speech` holds the session's own rules; this class only owns the
    // two facts only `:ime` can supply: which field is current, and whether a key reaching the
    // ordinary typing pipeline while dictation is listening means the user just edited the field
    // out from under it (spec: the c440844 fix, DictationController.onUserEditedComposingText's
    // own KDoc). `trigger` is now wired to the Fn-burst command [handleCommand] receives from
    // `:core:keys` (keys-and-modifiers.md SS3.3) and to the microphone key of a future strip; this
    // task only wires the former.
    private val dictationController = DictationController(service) { service.currentInputConnection }
    private var currentPackageName: String? = null

    /**
     * The focused field's [FieldKind], tracked so [reportKeyboardDebugEvent] can refuse to record
     * from a password field regardless of whether Diagnostics is open or what it captured before
     * (app-shell.md SS10.2 never claims an exemption for password fields; the field's own
     * sensitivity outranks the debug pipeline).
     */
    private var currentFieldKind: FieldKind = FieldKind.NOT_EDITABLE

    fun onDictationTrigger() = dictationController.trigger(currentPackageName)

    /**
     * The service is going away. Every callback this session posted on the main handler (the
     * long-press tick, the staged cursor-update retries, the trackpad's hold timer, dictation's
     * clock) would otherwise fire against a destroyed service; spec dictation.md SS3 "Keyboard
     * service destroyed: timers cancelled".
     */
    fun onServiceDestroyed() {
        runCatching { service.unregisterReceiver(dictionaryChangeReceiver) }.onFailure { error -> Log.e(TAG, "dictionary receiver teardown crashed", error) }
        runCatching { service.unregisterReceiver(runCommandNowReceiver) }.onFailure { error -> Log.e(TAG, "run-command receiver teardown crashed", error) }
        settingsScope.cancel()
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacksAndMessages(cursorUpdateToken)
        handler.removeCallbacksAndMessages(selectionSyncToken)
        handler.removeCallbacks(dipReshowRunnable)
        handler.removeCallbacksAndMessages(surfaceTransitionToken)
        handler.removeCallbacks(backlightNudgeTimeoutRunnable)
        runCatching { trackpad.onKeyboardWindowHidden() }.onFailure { error -> Log.e(TAG, "trackpad teardown crashed", error) }
        runCatching { caretBadge.hide() }.onFailure { error -> Log.e(TAG, "caret badge teardown crashed", error) }
        // spec: trackpad-caret-nav.md SS5.7: the status icon is hidden "when the keyboard service is destroyed".
        runCatching { service.hideStatusIcon() }.onFailure { error -> Log.e(TAG, "status icon teardown crashed", error) }
        lastShownStatusIcon = StatusBarIcon.None
        dictationController.onServiceDestroyed()
        handler.removeCallbacks(expansionRefreshRunnable)
        runCatching { expansionPopup.hide(); clipboardPanel.hide(); emojiPicker.hide(); symGridPanel.hide(); quickLauncher.onServiceDestroyed() }
            .onFailure { error -> Log.e(TAG, "panel teardown crashed", error) }
        clipboard.onServiceDestroyed()
        runCatching { emojiAssets.shutdown() }.onFailure { error -> Log.e(TAG, "emoji loader teardown crashed", error) }
        launcherKeys.onServiceDestroyed()
        // spec: expansion-clipboard-pickers-launcher.md SS9.1: "released when the service is destroyed".
        runCatching { typingSoundPlayer?.release() }.onFailure { error -> Log.e(TAG, "typing sound player teardown crashed", error) }
    }

    // spec dictionaries-languages.md SS8.7: [primaryLanguage] names which dictionary to load, from
    // the current input style. `en` is the only bundled dictionary today (docs/dictionaries.md),
    // so switching to a style whose language is not `en` simply loads no dictionary for it
    // (DictionaryAssetLoader's own KDoc: a missing asset is a silent, already-handled failure),
    // not a crash; bundling the other eighteen is a documented gap of its own, unrelated to this.
    private val dictionaryLoader = DictionaryAssetLoader(service, handler)

    /** The loaded dictionaries by language, primary and extras alike; [rebuildDictionaries] hands the wanted ones to the pipeline, primary first. */
    private val loadedDictionaries = linkedMapOf<LanguageCode, DictionaryIndex>()

    /**
     * Which [dictionaryLoadGeneration] owns the in-flight load for each language, not just whether
     * one is in flight: [reloadAllDictionaries] used to `clear()` a plain set here without
     * cancelling the actual background thread [loadDictionary] had already started for it, so a
     * slow load from before the reload could still land afterward and, with no ordering guarantee
     * against the fresh reload's own load, overwrite it depending only on which one happened to
     * post to the main thread last. Tagging each in-flight entry with the generation it belongs to
     * lets a stale completion recognise itself as superseded (its generation no longer matches the
     * map's current entry for that language) without touching the fresher load's own entry.
     */
    private val dictionaryLoadsInFlight = mutableMapOf<LanguageCode, Int>()
    private var dictionaryLoadGeneration = 0

    // spec dictionaries-languages.md SS7: the default and personal user words, loaded once at
    // startup and reloaded whenever the Personal Dictionary screen (or the strip's own add-word,
    // via [persistAddedWord]) changes them. `:core:text`'s `TextInputResources.userWords` is the
    // only consumer; [UserWordFileLoader] only supplies the files and the background thread.
    private val userWordLoader = UserWordFileLoader(service, handler)
    private var userWordStore: UserWordStore = UserWordStore.empty()

    /**
     * spec: autocorrect-suggestions.md SS4: `user_ngrams.db`'s persistence, loaded once at startup
     * and merged into [pipeline]'s own in-memory overlay ([KeyboardPipeline.onNgramStoreLoaded]);
     * every learn from then on writes through [KeyboardPipeline.onBigramLearned] below.
     */
    private val ngramLoader = UserNgramLoader(service, handler)

    /**
     * spec SS17's Keep/Drop fix ("reload on install, import, uninstall") and SS7 ("...again
     * whenever the broadcast `ACTION_USER_DICTIONARY_UPDATED` arrives"): the settings screens run
     * in `:app`'s process, so the only way this process learns of a file it did not itself write
     * is a package-internal broadcast. Registered in [init], unregistered in [onServiceDestroyed].
     */
    private val dictionaryChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                DictionaryBroadcastActions.DICTIONARY_CHANGED -> reloadAllDictionaries()
                DictionaryBroadcastActions.USER_DICTIONARY_UPDATED -> loadUserWords()
            }
        }
    }

    /**
     * spec: expansion-clipboard-pickers-launcher.md SS6.2/SS6.4: "when the sheet was opened by a
     * key press the command also runs immediately." The assignment sheet (`:app`'s
     * `LauncherAssignmentActivity`) can start an app or an intent itself, but an
     * `InternalAction`/`NavAction` command needs this running session (its quick launcher, its
     * input connection), so it sends [AssignmentSheet.ACTION_RUN_COMMAND_NOW] here instead.
     */
    private val runCommandNowReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val commandId = intent.getStringExtra(AssignmentSheet.EXTRA_COMMAND_ID) ?: return
            val command = commandCatalog.build().find(commandId) ?: return
            commandExecutor.run(command)
        }
    }

    /**
     * spec: autocorrect-suggestions.md SS8.1: the bundled substitution rule sets, loaded once (a
     * few kilobytes, unlike a dictionary) and handed to [ImeSettings.ruleSets] on every settings
     * change. Only `auto_corrections_en.json` ships today; the other five bundled codes are a
     * documented gap ([ImeSettings.ruleSets]'s own KDoc).
     */
    private val bundledRuleSets = RuleSetAssetLoader(service.assets).loadAll(ImeSettings.BUNDLED_RULE_SET_CODES)

    /**
     * spec: trackpad-caret-nav.md SS5.4, SS5.9; keys-and-modifiers.md SS12: the Fn Layer map,
     * loaded once from the user's private file (falling back to the shipped asset) and reloaded in
     * [applySettings] whenever `nav_mode_mappings_updated` changes, which is what the Fn Layer
     * screen's save and "Revert to Default" bump. [ctrlMappingLoader]'s own KDoc explains why
     * `:app`'s identical `FnLayerMappingStore` cannot be reused directly.
     */
    private val ctrlMappingLoader = CtrlMappingFileLoader(service)
    private var ctrlMappings: brobata.physiboard.core.keys.CtrlMappingTable = ctrlMappingLoader.load(lastSettings.keys.navModeDefaultMappingsVersion)
    private var lastAppliedCtrlMappingsUpdatedAtMs: Long = -1L

    /**
     * spec dictionaries-languages.md SS10: the Input Languages screen's "System" row override,
     * consulted by [onCurrentInputMethodSubtypeChanged] to resolve a base subtype's layout the same
     * way the screen itself previews it. Re-read on every subtype change rather than cached for the
     * session's whole lifetime: this file changes rarely (an explicit save from the screen), so a
     * fresh small synchronous read costs nothing and never risks acting on a stale override.
     */
    private val localeLayoutOverrideLoader = LocaleLayoutOverrideFileLoader(service)

    /**
     * spec: keys-and-modifiers.md SS12.1: an install whose private `ctrl_key_mappings.json`
     * predates a later shipped default (already backfilled into [ctrlMappings] by [CtrlMappingFileLoader
     * .load]) gets that default written back to the file, and `nav_mode_default_mappings_version`
     * bumped, so this only ever runs once per install and a later deliberate "none" survives.
     * [settingsSource] being null (a JVM test, or a host without the store) just skips persisting;
     * the in-memory migration [ctrlMappings] already carries still runs the keyboard correctly.
     */
    private fun persistCtrlMappingMigrationIfNeeded(currentVersion: Int) {
        if (currentVersion >= brobata.physiboard.core.keys.CTRL_MAPPING_DEFAULTS_VERSION) return
        ctrlMappingLoader.save(ctrlMappings)
        settingsSource?.write { stored -> stored.copy(keys = stored.keys.copy(navModeDefaultMappingsVersion = brobata.physiboard.core.keys.CTRL_MAPPING_DEFAULTS_VERSION)) }
    }

    /** spec: dictionaries-languages.md SS8.4, the active style's extra suggestion languages (`input_style_suggestion_locales`). */
    private var extraLanguages: List<LanguageCode> = emptyList()

    /** spec: dictation.md SS11.2, `assistant_action`; null is `auto`. */
    private var assistantRequest: AssistantRequest? = null

    init {
        persistCtrlMappingMigrationIfNeeded(lastSettings.keys.navModeDefaultMappingsVersion)
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
        loadDictionary(primaryLanguage)
        // spec dictionaries-languages.md SS7: the default and personal words load the same way,
        // off the main thread, and are merged in the moment they land.
        loadUserWords()
        // spec SS4: `user_ngrams.db`'s rows, loaded the same way and merged into whatever this
        // session has already learned before the load lands ([NgramStore.mergedWith]).
        pipeline.onBigramLearned = { locale, prefix, nextWord -> ngramLoader.learnAsync(locale, prefix, nextWord, System.currentTimeMillis()) }
        pipeline.onBigramForgotten = { locale, prefix, nextWord -> ngramLoader.forgetAsync(locale, prefix, nextWord) }
        ngramLoader.loadAsync { rows -> pipeline.onNgramStoreLoaded(rows) }
        // spec SS8.3: "when the keyboard service is created", with whatever `custom_input_styles`
        // the shipped defaults hold until the store's first emission (an empty array still
        // replaces Android's previous additional set, per SS8.3's own note).
        registerAdditionalSubtypes(Settings().languages.inputStyles)
        val dictionaryChangeFilter = IntentFilter().apply {
            addAction(DictionaryBroadcastActions.DICTIONARY_CHANGED)
            addAction(DictionaryBroadcastActions.USER_DICTIONARY_UPDATED)
        }
        ContextCompat.registerReceiver(service, dictionaryChangeReceiver, dictionaryChangeFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(
            service, runCommandNowReceiver, IntentFilter(AssignmentSheet.ACTION_RUN_COMMAND_NOW), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // The store is read the same way: the shipped defaults above stand until the first value
        // arrives, and every later emission re-applies live (settings-catalog.md SS1).
        // spec expansion-clipboard-pickers-launcher.md SS3.1: `clipboard_history_enabled` is read
        // once, at service creation. With no store to read (a host without `:app`'s wiring) that
        // read is the shipped default, and it still has to happen or the clipboard never starts.
        if (settingsSource == null) clipboard.applyEnabledOnce(Settings().expansion.clipboardHistoryEnabled)
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
        if (language in loadedDictionaries || language in dictionaryLoadsInFlight) return
        val generation = dictionaryLoadGeneration
        dictionaryLoadsInFlight[language] = generation
        dictionaryLoader.loadAsync(language) { index ->
            // A completion whose generation no longer owns this language's in-flight entry was
            // superseded by [reloadAllDictionaries] while it was running: neither its result nor
            // its removal of the in-flight marker (which would belong to the fresher load by now)
            // should apply.
            if (dictionaryLoadsInFlight[language] != generation) return@loadAsync
            dictionaryLoadsInFlight.remove(language)
            loadedDictionaries[language] = index
            rebuildDictionaries()
            refreshCandidatesStrip()
        }
    }

    /** spec SS8.4: "engines for languages no longer listed are dropped"; the primary comes first (`TextInputResources`' own contract). */
    private fun rebuildDictionaries() {
        val wanted = (listOf(primaryLanguage) + extraLanguages).mapNotNull { loadedDictionaries[it] }
        if (wanted != pipeline.resources.dictionaries) pipeline.resources = pipeline.resources.copy(dictionaries = wanted)
    }

    /**
     * spec SS17's Keep/Drop fix ("Per-process dictionary cache never invalidated | Fix | Reload
     * on install, import, uninstall"): an install, import or uninstall on the settings screen
     * cannot know which language changed from here (the broadcast carries none), and a dictionary
     * is cheap enough to reload compared to typing with a stale one, so every cached dictionary is
     * dropped and the ones this session actually wants (primary and extras) are loaded again at
     * once, immediately, rather than waiting for the next field or the next process start.
     */
    private fun reloadAllDictionaries() {
        // Bumped before clearing, so any load already in flight for a language this call also
        // re-requests recognises itself as stale when it completes (see [loadDictionary]) instead
        // of racing the fresh load this call starts for the identical language.
        dictionaryLoadGeneration++
        loadedDictionaries.clear()
        dictionaryLoadsInFlight.clear()
        pipeline.resources = pipeline.resources.copy(dictionaries = emptyList())
        loadDictionary(primaryLanguage)
        extraLanguages.forEach(::loadDictionary)
    }

    /** spec SS7: loads the default and personal word files into [userWordStore] and the pipeline; reload is the same path a broadcast triggers. */
    private fun loadUserWords() {
        userWordLoader.loadAsync { store ->
            userWordStore = store
            pipeline.resources = pipeline.resources.copy(userWords = store)
            refreshCandidatesStrip()
        }
    }

    /**
     * spec SS7: "a word added from the strip is merged into the primary dictionary at once".
     * [KeyboardSession.onStripSlotTapped]'s add-word slot calls this after committing the text, so
     * the word is a known word (protected from autocorrect, suggested back) before the next
     * keystroke, and durable across a process restart.
     */
    private fun persistAddedWord(word: String) {
        userWordStore = userWordStore.withPersonalWordAdded(word, System.currentTimeMillis())
        pipeline.resources = pipeline.resources.copy(userWords = userWordStore)
        userWordLoader.savePersonalAsync(userWordStore.personalWords(), ::reportPersonalWordSaveResult)
    }

    /**
     * spec: autocorrect-suggestions.md SS6.3's "save failed" surfacing, applied to the strip's own
     * add/delete path: the word already looks added in this session (the in-memory store update is
     * immediate and unconditional), so a silent write failure would otherwise mean the addition is
     * gone the next time the process restarts with nobody told. Reported the same way this class
     * already surfaces the layout-switch toast (line ~593).
     */
    private fun reportPersonalWordSaveResult(saved: Boolean) {
        if (saved) return
        Log.e(TAG, "personal dictionary save failed")
        runCatching {
            android.widget.Toast.makeText(service, "Personal dictionary: save failed", android.widget.Toast.LENGTH_SHORT).show()
        }.onFailure { error -> Log.e(TAG, "personal dictionary save-failed toast crashed", error) }
    }

    /** [registerAdditionalSubtypes] only re-registers when `custom_input_styles` actually changed, matching SS8.3's own triggers rather than every settings emission. */
    private var lastRegisteredInputStyles: List<String>? = null

    /**
     * spec SS8.1/SS8.3: hands Android the additional subtypes `custom_input_styles` describes, so
     * the app's own input-style switching ([InputStyleCatalog]) and Android's keyboard picker,
     * Settings > Languages entry and subtype-changed callback agree on the same set. Decided
     * purely by [AdditionalSubtypeBuilder] (no android import there); this function only turns
     * that decision into real [InputMethodSubtype] objects. Called at [init] ("when the keyboard
     * service is created") and from [applySettings] whenever `custom_input_styles` changes; SS8.3's
     * other triggers (the app process starting, an immediate re-registration from the Input
     * Languages screen's own process, and the 500 ms explicitly-enabled-set rewrite on Android 14+)
     * are not wired from here.
     */
    private fun registerAdditionalSubtypes(inputStyles: List<String>) {
        if (inputStyles == lastRegisteredInputStyles) return
        lastRegisteredInputStyles = inputStyles
        runCatching {
            val imm = service.getSystemService(InputMethodManager::class.java) ?: return
            val subtypes = AdditionalSubtypeBuilder.build(inputStyles).map { spec ->
                InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeLocale(spec.locale)
                    .setSubtypeMode("keyboard")
                    .setSubtypeExtraValue(spec.extraValue)
                    .setSubtypeId(spec.id)
                    .setIsAsciiCapable(true)
                    .build()
            }.toTypedArray()
            // SS8.3: "The whole array replaces Android's previous additional set, even when it is empty."
            val imeId = ComponentName(service, service.javaClass).flattenToShortString()
            imm.setAdditionalInputMethodSubtypes(imeId, subtypes)
        }.onFailure { error -> Log.e(TAG, "subtype registration crashed", error) }
    }

    /**
     * spec: status-bar.md SS5.3, autocorrect-suggestions.md SS5: the action-mode "trash" button.
     * "Delete removes the word from the personal dictionary in every loaded dictionary" is one
     * store update here, since 3.0 keeps a single shared [UserWordStore] rather than per-dictionary
     * copies (`TextInputResources.userWords`' own contract); "forgets it as a next word everywhere"
     * (SS4, SS5) is [KeyboardPipeline.forgetWordAsNextWordEverywhere].
     */
    private fun deletePersonalWord(word: String) {
        userWordStore = userWordStore.withPersonalWordRemoved(word)
        pipeline.resources = pipeline.resources.copy(userWords = userWordStore)
        userWordLoader.savePersonalAsync(userWordStore.personalWords(), ::reportPersonalWordSaveResult)
        pipeline.forgetWordAsNextWordEverywhere(word)
        ngramLoader.forgetEverywhereAsync(word)
    }

    /**
     * One stored [Settings] value, handed to every module that takes a bundle; [ImeSettings]
     * names which row feeds which field. [announceSwitch] is true only when
     * [switchToNextInputStyle] calls this to reapply a just-chosen style (spec
     * dictionaries-languages.md SS9.3 step 6's toast); a plain settings-driven refresh never
     * announces one.
     */
    private fun applySettings(settings: Settings, announceSwitch: Boolean = false) {
        lastSettings = settings
        // spec dictionaries-languages.md SS8.2, SS9 and keys-and-modifiers.md SS7.5: the
        // available styles and whether a chord (or the language button) has anywhere to switch to
        // are both recomputed from the store on every emission; SS10's
        // `keyboard_layout_auto_by_locale` only ever picks the starting style, at the very first
        // emission ([startupStyleChosen]); a later one keeps [currentStyle] if it still exists.
        val styles = InputStyleCatalog.availableStyles(shippedLayouts, settings.languages)
        currentStyle = if (!startupStyleChosen) {
            startupStyleChosen = true
            InputStyleCatalog.startupStyle(styles, java.util.Locale.getDefault().toString(), settings.languages.layoutAutoByLocale) ?: currentStyle
        } else {
            InputStyleCatalog.current(styles, currentStyle.key) ?: currentStyle
        }
        pipeline.anotherSubtypeAvailable = InputStyleCatalog.anotherStyleAvailable(styles)
        primaryLanguage = LanguageCode.fromLocale(currentStyle.locale) ?: primaryLanguage

        val previousStrip = pipeline.settings.statusBar
        pipeline.settings = ImeSettings.keyboardSettings(settings, currentStyle.locale)
        // spec: trackpad-caret-nav.md SS5.9: "any change [to `nav_mode_mappings_updated`] makes
        // the running keyboard reload the map" -- the Fn Layer screen's save and "Revert to
        // Default" both bump it (see FnLayerMappingStore's write path in `:app`).
        if (settings.keys.navModeMappingsUpdatedAtMs != lastAppliedCtrlMappingsUpdatedAtMs) {
            lastAppliedCtrlMappingsUpdatedAtMs = settings.keys.navModeMappingsUpdatedAtMs
            ctrlMappings = runCatching { ctrlMappingLoader.load(settings.keys.navModeDefaultMappingsVersion) }.getOrDefault(ctrlMappings)
        }
        // spec: keys-and-modifiers.md SS12.1: catches the case the branch above misses, an install
        // whose file predates a later default that arrives with no save/"Revert to Default" of its
        // own to bump `nav_mode_mappings_updated` (an app update alone).
        persistCtrlMappingMigrationIfNeeded(settings.keys.navModeDefaultMappingsVersion)
        pipeline.layout = ImeSettings.layout(InputStyleCatalog.layoutFor(currentStyle, shippedLayouts) ?: pipeline.layout, settings, ctrlMappings)
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
        extraLanguages = ImeSettings.extraSuggestionLanguages(settings, primaryLanguage, currentStyle.locale)
        loadDictionary(primaryLanguage)
        rebuildDictionaries()
        extraLanguages.forEach(::loadDictionary)
        // spec SS8.3: "whenever `custom_input_styles` changes"; a no-op when it did not (the
        // function's own equality guard).
        registerAdditionalSubtypes(settings.languages.inputStyles)
        if (announceSwitch && settings.languages.toastOnLayoutSwitch) {
            runCatching {
                val text = InputStyleCatalog.switchToastText(currentStyle, extraLanguages.map { it.value })
                android.widget.Toast.makeText(service, text, android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure { error -> Log.e(TAG, "layout switch toast crashed", error) }
        }
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
        keyboardSwipe.settings = ImeSettings.keyboardSwipeSettings(settings)
        // spec trackpad-caret-nav.md SS3.3: re-attached "whenever ... the provider preference changes".
        attachKeyboardSwipeListener()
        appProfiles = ImeSettings.appProfiles(settings)
        enterOverrides = ImeSettings.enterOverrides(settings)
        enterPreset = settings.perApp.enterPreset
        enterBehaviorEnabled = settings.perApp.enterBehaviorEnabled
        dictationController.settings = ImeSettings.dictationSettings(settings, Build.VERSION.SDK_INT)
        dictationController.textSettings = ImeSettings.dictationTextSettings(dictationController.textSettings, settings)
        // spec: dictation.md SS5.1 step 1: the recognizer's language follows the active input style's locale.
        dictationController.subtypeLanguageTag = currentStyle.locale
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
        // spec: expansion-clipboard-pickers-launcher.md SS9.1: "rebuilt whenever ... changes".
        if (settings.feedback.typingSoundMode != typingSoundMode || settings.feedback.typingSoundOutputMode != typingSoundOutputMode) {
            typingSoundMode = settings.feedback.typingSoundMode
            typingSoundOutputMode = settings.feedback.typingSoundOutputMode
            runCatching { typingSoundPlayer?.release() }
            typingSoundPlayer = if (typingSoundMode == TypingSoundMode.OFF) null else TypingSoundPlayer(service, typingSoundMode, typingSoundOutputMode)
        }
        // spec SS5.6: the suggestion row's live announcements and their delay. `:core:strip`'s own
        // [StripSettings] (`strip`, above) has no accessibility fields (SS9.1's Keep/Drop is about
        // the strip's *theme*), so these two come straight off the raw store settings.
        statusBar?.liveAnnouncementsEnabled = settings.statusBar.accessibilityLiveAnnouncementsEnabled
        statusBar?.announcementDelayMs = settings.statusBar.accessibilitySuggestionsAnnouncementDelayMs
        // spec SS11: `ime_overlay_debug_logging`.
        statusBar?.overlayDebugLoggingEnabled = settings.statusBar.overlayDebugLoggingEnabled
    }

    /**
     * spec keys-and-modifiers.md SS7.5's three chords and dictionaries-languages.md SS9's button
     * and menu: cycles [currentStyle] to [InputStyleCatalog.next] and reapplies everything a
     * switch changes (the layout `:core:keys` resolves against, the primary dictionary
     * `:core:dict` loads, the strip's theme override and the language button's label) through
     * [applySettings], then announces it.
     *
     * Called from [handleCommand]'s [KeyCommands.SWITCH_LAYOUT] (the three chords, once
     * `:core:keys` has already decided to fire one) and from the strip's language button
     * ([performStripAction]'s [StripAction.CycleLanguage]) directly, so both keep switching styles
     * with all three chords off (the project's default-ON rule, `LanguagePrefs`'s own KDoc):
     * neither path reads `alt_shift_layout_switch`/`alt_enter_layout_switch`/
     * `ctrl_space_layout_switch` at all, only [KeyboardPipeline.anotherSubtypeAvailable] (whether
     * there is anywhere to switch to), which this function does not gate on either -- with one
     * available style [InputStyleCatalog.next] simply returns that same style and nothing visible
     * changes.
     */
    private fun switchToNextInputStyle() {
        val styles = InputStyleCatalog.availableStyles(shippedLayouts, lastSettings.languages)
        val next = InputStyleCatalog.next(styles, currentStyle.key) ?: return
        switchToInputStyle(next, announceSwitch = true)
    }

    /** The reapplication both [switchToNextInputStyle] and [onCurrentInputMethodSubtypeChanged] need once a target [InputStyle] is chosen. */
    private fun switchToInputStyle(target: InputStyle, announceSwitch: Boolean) {
        currentStyle = target
        applySettings(lastSettings, announceSwitch = announceSwitch)
        // spec dictionaries-languages.md SS10: `keyboard_layout` is "rewritten by every language
        // switch", so the choice survives the next start instead of falling back to the
        // locale-derived pick.
        settingsSource?.write { stored -> stored.copy(languages = stored.languages.copy(keyboardLayout = target.layoutId)) }
    }

    /**
     * spec dictionaries-languages.md SS9.4: Android's own language-switch key, or Settings >
     * Languages, picking a different base subtype must keep [currentStyle] (and so the loaded
     * dictionary and layout) following it, not just PhysiBoard's own in-app cycle
     * ([switchToNextInputStyle]). [InputStyleCatalog]'s own KDoc used to record this as a SPEC GAP
     * ("this project has no subtype-changed callback to hook"); this is that callback, wired from
     * [PhysiBoardInputMethodService.onCurrentInputMethodSubtypeChanged]. A subtype Android reports
     * that matches no known style (locale not in [InputStyleCatalog.availableStyles], or a stale
     * callback during startup) is left alone rather than guessed at.
     */
    fun onCurrentInputMethodSubtypeChanged(subtype: InputMethodSubtype?) {
        runCatching {
            val locale = subtype?.locale?.takeIf { it.isNotBlank() } ?: return@runCatching
            val styles = InputStyleCatalog.availableStyles(shippedLayouts, lastSettings.languages)
            // spec SS8.3 step 3: an additional subtype's extra value carries `KeyboardLayoutSet=<id>`.
            val layoutFromExtra = subtype.extraValue.orEmpty().split(',')
                .firstOrNull { it.startsWith("KeyboardLayoutSet=") }
                ?.substringAfter('=')
            val target = if (layoutFromExtra != null) {
                styles.firstOrNull { normalizedLocale(it.locale) == normalizedLocale(locale) && it.layoutId == layoutFromExtra }
            } else {
                // One of the twelve base subtypes declared in method.xml: no PhysiBoard-authored
                // extra value to read a layout id from, so this resolves it exactly the way the
                // Input Languages screen's "System" row does (SS10), honoring the on-device
                // override that row writes to `locale_layout_mapping.json` -- previously read by
                // nothing under `:ime` at all, so a saved override never reached the keyboard.
                val resolvedLayoutId = LocaleLayoutMapping.resolve(locale, override = localeLayoutOverrideLoader.load())
                InputStyle(locale = locale, layoutId = resolvedLayoutId, shipped = true)
                    .takeIf { shippedLayouts.any { shipped -> shipped.layoutId == resolvedLayoutId } }
            }
            val resolved = target ?: styles.firstOrNull { normalizedLocale(it.locale) == normalizedLocale(locale) } ?: return@runCatching
            if (resolved.key == currentStyle.key) return@runCatching
            switchToInputStyle(resolved, announceSwitch = false)
        }.onFailure { error -> Log.e(TAG, "subtype-changed resync crashed", error) }
    }

    private fun normalizedLocale(locale: String): String = locale.replace('_', '-').lowercase()

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
            extraSendShortcut = EnterOverrideResolver.resolveExtraShortcut(reportedPackage, enterOverrides, enterBehaviorEnabled),
        )
        val field = classifyField(info, profile)
        currentFieldKind = field.kind
        // spec: text-input.md SS3: set only after classification, which needs the app's own value.
        applyNoSuggestionsFlag(info, field)
        DiagnosticLog.i(TAG) { "field: pkg=$reportedPackage restarting=$restarting inputType=0x${Integer.toHexString(info?.inputType ?: 0)} caps=${field.capFlags} kind=${field.kind} trust=${profile.editorTrust}" }
        reportFieldAttachDebug(reportedPackage, info)
        ownEdit = null
        lastReportedSelStart = info?.initialSelStart?.coerceAtLeast(0) ?: 0
        val openingText = initialTextBeforeCursor(info)
        if (restarting) {
            // spec: text-input.md line 85, 463, 497: a restart reclassifies and re-evaluates, but
            // does not wipe the word in progress; web fields restart input mid-word all the time.
            pipeline.onRestartInput(field, profile.editorTrust, profile, openingText)
            // The pipeline keeps a pending long press across a restart (the key is still held);
            // the timer cancelled above is re-armed for it rather than leaving it to never fire.
            scheduleLongPressIfNeeded()
        } else {
            pipeline.onStartInput(field, profile.editorTrust, profile, openingText)
        }
        requestCandidatesShown(field.isReallyEditable)
        currentPackageName = reportedPackage
        // spec expansion-clipboard-pickers-launcher.md SS6.2 A: the home screen path needs the foreground launcher.
        pipeline.foregroundIsHome = reportedPackage != null && reportedPackage in homePackages
        dictationController.onEditorFieldOpened(reportedPackage)
        clipboard.onFieldStarted()
        // spec SS2.4: matches are cleared "on every start of input".
        handler.removeCallbacks(expansionRefreshRunnable)
        expansionPopup.hide()
        // spec: layers-sym-alt.md SS5.8: "when the IME next starts input and restore_sym_page is
        // greater than 0... the preference is then cleared." Runs on every start (restart
        // included); once consumed the stored value is 0, so a later start is a no-op.
        val pendingSymPageRestore = lastSettings.symPages.restoreSymPage
        if (pendingSymPageRestore > 0) {
            pipeline.restoreSymPage(pendingSymPageRestore)
            settingsSource?.write { stored -> stored.copy(symPages = stored.symPages.copy(restoreSymPage = 0)) }
        }
        syncSymPanels()
        refreshCandidatesStrip()
        // spec trackpad-caret-nav.md SS3.3: re-attached "whenever the editor starts".
        attachKeyboardSwipeListener()
    }

    /**
     * The text before the cursor as a field opens. The input connection is usually not ready to
     * answer a read this early: in Messages on the Titan (2026-09-25) it returned nothing at the
     * compose field's start, so "capitalise at the start of the text" saw no evidence and the
     * first letter of every message came out lower-case. The [EditorInfo] the platform has just
     * handed over carries the same text with no call to the app at all, and when it carries none,
     * its initial selection still says whether the cursor sits at the very start of the document.
     */
    private fun initialTextBeforeCursor(info: EditorInfo?): String? {
        runCatching { info?.getInitialTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString() }.getOrNull()?.let { return it }
        runCatching { service.currentInputConnection?.getTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString() }.getOrNull()?.let { return it }
        return if (info != null && info.initialSelStart <= 0 && info.initialSelEnd <= 0) "" else null
    }

    fun onFinishInput() {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacksAndMessages(cursorUpdateToken)
        handler.removeCallbacksAndMessages(selectionSyncToken)
        currentFieldKind = FieldKind.NOT_EDITABLE
        pipeline.onFinishInput()
        requestCandidatesShown(false)
        dictationController.onEditorFieldClosed()
        // spec: SS4.6, "forgotten and the badge hidden when the editor finishes".
        lastCaretGeometry = null
        caretBadge.hide()
        clipboard.onFieldFinished()
        handler.removeCallbacks(expansionRefreshRunnable)
        expansionPopup.hide()
        emojiPicker.onAppSelectionChanged()
        // spec SS5.3: "Action mode also ends when... the field finishes"; SS6.4: "the overlay is
        // also closed whenever the connection to the app changes".
        statusBar?.exitActionMode()
        closeQuickActions()
        syncSymPanels()
        // The quick launcher is a bottom overlay like the Sym-grid/clipboard/emoji trio above, but
        // was not being dismissed here: left open, it could keep floating over whatever app the
        // user switched to once the field it was opened over finished.
        if (quickLauncher.isOpen) quickLauncher.dismiss()
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
            // spec SS5.3: "Action mode also ends when... the window hides"; SS6.4: the overlay too.
            statusBar?.exitActionMode()
            closeQuickActions()
            expansionPopup.hide()
            syncSymPanels()
            // The quick launcher is a bottom overlay too, but nothing dismissed it here: left open,
            // it could be seen floating over whatever app the user switched to once the keyboard's
            // own window was gone (expansion-clipboard-pickers-launcher.md SS7.1's dismissal list).
            if (quickLauncher.isOpen) quickLauncher.dismiss()
        }.onFailure { error -> Log.e(TAG, "onKeyboardWindowHidden crashed", error) }
    }

    /** spec: status-bar.md SS13, "When the window is shown again the strip is refreshed immediately." */
    fun onKeyboardWindowShown() {
        runCatching {
            val textBeforeCursor = runCatching { service.currentInputConnection?.getTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString() }.getOrNull()
            pipeline.onWindowShown(textBeforeCursor)
            refreshCandidatesStrip()
            // spec trackpad-caret-nav.md SS3.3: re-attached "whenever ... the keyboard window is shown".
            attachKeyboardSwipeListener()
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
                // The dip's own hide is deliberately immediate (SS12.2 step 2: "the strip is hidden
                // immediately"), not routed through [requestCandidatesShown]'s "next UI turn" post:
                // D5 measured that even a 21 ms delay on the *re-show* half of the dip broke Teams'
                // inset, so the dip keeps its own precise timing rather than sharing the general
                // surface-transition safety net below.
                DipEffect.HIDE_STRIP -> service.setCandidatesViewShown(false)
                DipEffect.SHOW_STRIP -> {
                    service.setCandidatesViewShown(true)
                    handler.post { (statusBar?.parent as? View)?.visibility = View.VISIBLE }
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // The surface-transition retry safety net. spec: status-bar.md SS3.2, SS14, T39, T40.
    // -----------------------------------------------------------------------------------------

    /** Cancels a not-yet-run posted decision (SS3.2: "a later evaluation cancels any earlier posted one that has not run yet", T39), the same token-cancellation idiom [cursorUpdateToken] already uses. */
    private val surfaceTransitionToken = Any()
    private var surfaceTransitionState = SurfaceTransitionState()

    /**
     * spec SS3.2: posts the real `setCandidatesViewShown` call for the next UI turn rather than
     * calling it from this stack, so a second evaluation that lands before the first one ran can
     * cancel it outright (T39). A transition to shown is then checked against
     * [StatusBarView.isRenderedOnScreen] and retried up to [SurfaceTransitionRetry.MAX_ATTEMPTS]
     * times, [SurfaceTransitionRetry.INTERVAL_MS] apart, before it is abandoned (T40).
     */
    private fun requestCandidatesShown(shown: Boolean) {
        handler.removeCallbacksAndMessages(surfaceTransitionToken)
        surfaceTransitionState = SurfaceTransitionState()
        handler.postDelayed({ performCandidatesShown(shown) }, surfaceTransitionToken, 0L)
    }

    private fun performCandidatesShown(shown: Boolean) {
        runCatching { service.setCandidatesViewShown(shown) }.onFailure { error -> Log.e(TAG, "candidates view shown crashed", error) }
        if (!shown) return
        // spec SS3.2: "one turn after that forces the enclosing container back to visible".
        handler.postDelayed({
            runCatching { (statusBar?.parent as? View)?.visibility = View.VISIBLE }
                .onFailure { error -> Log.e(TAG, "container visibility fix crashed", error) }
            checkSurfaceTransition(shown = true)
        }, surfaceTransitionToken, 0L)
    }

    private fun checkSurfaceTransition(shown: Boolean) {
        val rendered = statusBar?.isRenderedOnScreen() ?: false
        val (nextState, outcome) = SurfaceTransitionRetry.onCheck(surfaceTransitionState, requestedShown = shown, actuallyRendered = rendered)
        surfaceTransitionState = nextState
        when (outcome) {
            SurfaceTransitionOutcome.SATISFIED -> Unit
            SurfaceTransitionOutcome.RETRY -> handler.postDelayed({ checkSurfaceTransition(shown) }, surfaceTransitionToken, SurfaceTransitionRetry.INTERVAL_MS)
            // spec T40: "abandoned; requested-shown state set to what is actually rendered". There is
            // no separate "requested-shown" flag in this class to roll back (the platform's own
            // `isInputViewShown`/candidates state is that record); the abandon itself, giving up on
            // further retries, is what keeps this class from spinning forever on a surface Android
            // never actually renders.
            SurfaceTransitionOutcome.ABANDONED -> Unit
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
            // expansion-clipboard-pickers-launcher.md SS4.5: "while capture is on, the app's caret
            // position is monitored (the single caret-monitoring switch is shared with the caret
            // badge and is reconciled so neither feature turns it off under the other)".
            val wanted = CursorUpdateRequestPolicy.wantsReports(caretBadge.settings.enabled, emojiSearchNeedsCaret = emojiPicker.isShown && emojiPicker.captureOn)
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
            // spec SS4.5: the app's own caret moved between two captured keys, so capture drops;
            // this is not part of the tracker resync and stays immediate.
            emojiPicker.onAppSelectionChanged()
            // spec autocorrect-suggestions.md SS1.2: the tracker resync this triggers is debounced
            // 120 ms, and a second selection change arriving first cancels the pending one, so the
            // strip never re-syncs against a cursor position the user has already moved past.
            val selectionCollapsed = newSelStart == newSelEnd
            handler.removeCallbacksAndMessages(selectionSyncToken)
            handler.postDelayed({
                val textBeforeCursor = runCatching { service.currentInputConnection?.getTextBeforeCursor(TEXT_BEFORE_CURSOR_READ, 0)?.toString() }.getOrNull()
                DiagnosticLog.i(TAG) { "selection external: $oldSelStart->$newSelStart textBefore='${textBeforeCursor?.takeLast(12)}'" }
                pipeline.onExternalSelectionChange(textBeforeCursor, selectionCollapsed = selectionCollapsed)
                refreshCandidatesStrip()
            }, selectionSyncToken, CurrentWordTracker.CURSOR_MOVE_DEBOUNCE_MS)
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
        // app-shell.md SS10.2: origin `ime_service`, reported before anything below can consume or
        // rewrite the event, so the Diagnostics panel sees exactly what Android delivered.
        reportKeyboardDebugEvent(event)
        // spec SS6.4: "The close button and the hardware Back key close it." Consumed outright,
        // like the close button, rather than falling through to nav mode or the app.
        if (quickActionsOpen && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) closeQuickActions()
            return@runCatching true
        }
        // spec expansion-clipboard-pickers-launcher.md SS4.5: while page 4's search captures, hardware keys type into it.
        if (emojiPicker.isShown && emojiPicker.captureOn && emojiPicker.onHardwareKey(event, normalizeStroke(event)?.key)) return@runCatching true
        if (interceptFirmwareSwipeKeycode(event)) return@runCatching true
        if (interceptForTrackpad(event)) return@runCatching true
        val stroke = normalizeStroke(event) ?: return@runCatching false
        processKeyStroke(stroke)
    }.getOrElse { error ->
        Log.e(TAG, "onKeyEvent crashed on keyCode=${event.keyCode}; letting the raw key through", error)
        false
    }

    /**
     * spec app-shell.md SS10.7: "The keyboard records a context snapshot every time it attaches to
     * a field", so the store's "last field from another app" slot still describes the app being
     * reported even after Diagnostics' own text field steals the "last field" slot. 3.0 ships only
     * the Titan 2 Elite profile, so there is no override to read; both profile fields are constant.
     */
    private fun reportFieldAttachDebug(reportedPackage: String?, info: EditorInfo?) {
        val sink = debugCaptureSink ?: return
        val imeOptions = info?.imeOptions ?: 0
        val fields = mapOf(
            "input_type" to "0x${Integer.toHexString(info?.inputType ?: 0)}",
            "ime_options" to "0x${Integer.toHexString(imeOptions)}",
            "ime_no_enter_action" to ((imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0).toString(),
            "resolved_editor_action" to editorActionName(imeOptions and EditorInfo.IME_MASK_ACTION),
            "subtype_locale" to currentStyle.locale,
            "resolved_layout" to currentStyle.layoutId,
            "profile_override_snapshot" to "auto",
            "resolved_physical_profile_snapshot" to "titan2elite_qwerty",
        )
        sink.reportFieldAttach(
            ImeContextSnapshot(atMs = SystemClock.uptimeMillis(), packageName = reportedPackage.orEmpty(), fields = fields),
            isPhysiBoardOwnPackage = reportedPackage == service.packageName,
        )
    }

    /** spec app-shell.md SS10.6 `[ime_context]`: the six names the IME test screen's own "IME Actions" section uses. */
    private fun editorActionName(action: Int): String = when (action) {
        EditorInfo.IME_ACTION_GO -> "go"
        EditorInfo.IME_ACTION_SEARCH -> "search"
        EditorInfo.IME_ACTION_SEND -> "send"
        EditorInfo.IME_ACTION_NEXT -> "next"
        EditorInfo.IME_ACTION_DONE -> "done"
        EditorInfo.IME_ACTION_PREVIOUS -> "previous"
        EditorInfo.IME_ACTION_UNSPECIFIED -> "unspecified"
        else -> "none"
    }

    /**
     * spec app-shell.md SS11, autocorrect-suggestions.md SS7.2: "each attempt is recorded in the
     * debug capture with its outcome", regardless of whether Diagnostics is open. [debug] is
     * `:core:text`'s own decision ([brobata.physiboard.core.text.BoundaryDebugInfo]); this only
     * adds the wall-clock timestamp the pure module has no business knowing.
     */
    private fun reportAutocorrectionDebug(debug: BoundaryDebugInfo) {
        val sink = debugCaptureSink ?: return
        sink.recordAutocorrection(
            AutocorrectionRecord(
                atMs = SystemClock.uptimeMillis(),
                type = debug.type,
                trigger = debug.trigger,
                source = debug.source.orEmpty(),
                outcome = debug.outcome,
                before = debug.before,
                after = debug.after,
                reason = debug.reason,
                distance = debug.distance,
            ),
        )
    }

    /**
     * spec app-shell.md SS10.2, SS10.3: reports the raw event, under origin `ime_service`, to
     * whichever screen is registered (the Diagnostics screen; a no-op the rest of the time,
     * [DebugCaptureSink.report] costs nothing while unregistered). SPEC GAP: the four other
     * origins the spec names (`ime_router`, `ime_decor`, `bounce_keys`, `accidental_keys`) are
     * internal pipeline stages this build does not separately instrument; every physical event
     * still reaches the panel and the export under this one origin. The "Output" field is left
     * unset here: telling whether the pipeline translated the key into another one needs the
     * pipeline's own result, which this boundary does not see.
     */
    private fun reportKeyboardDebugEvent(event: KeyEvent) {
        // A password field's keystrokes are never recorded, no matter what the Diagnostics screen
        // or its "Record" toggle is doing: the field's own sensitivity outranks the debug pipeline.
        if (currentFieldKind == FieldKind.PASSWORD) return
        val sink = debugCaptureSink ?: return
        val glyph = pipeline.modifierGlyphInput()
        val unicodeRaw = runCatching { event.unicodeChar }.getOrDefault(0)
        val unicodeEffective = runCatching { event.getUnicodeChar(event.metaState) }.getOrDefault(0)
        sink.report(
            KeyboardEventRecord(
                origin = "ime_service",
                action = if (event.action == KeyEvent.ACTION_DOWN) "KEY_DOWN" else "KEY_UP",
                keyCode = event.keyCode,
                scanCode = event.scanCode,
                deviceId = event.deviceId,
                source = event.source,
                flags = event.flags,
                repeatCount = event.repeatCount,
                metaState = event.metaState,
                unicodeRaw = unicodeRaw,
                unicodeEffective = unicodeEffective,
                layout = currentStyle.layoutId,
                shift = event.isShiftPressed,
                ctrl = event.isCtrlPressed,
                alt = event.isAltPressed,
                altLatch = glyph.altLatched,
                altOneShot = glyph.altOneShotArmed,
                shiftLatch = glyph.capsLockOn,
                ctrlLatch = glyph.ctrlLatchedNotNavMode,
                symPage = if (glyph.symPageOpen) "open" else "none",
                eventUptimeMs = event.eventTime,
            ),
        )
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
        // spec: expansion-clipboard-pickers-launcher.md SS9.1: "on every hardware key down with
        // repeat count 0 while an editable field is active" -- reaching this line already means an
        // editor has an active InputConnection; [shouldPlay] itself only tests the repeat count.
        if (stroke.edge == KeyEdge.DOWN && TypingSounds.shouldPlay(typingSoundMode, stroke.key, stroke.repeatCount, editableFieldActive = true)) {
            typingSoundPlayer?.play(SoundGroup.forKey(stroke.key))
        }
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
        // spec app-shell.md SS11, autocorrect-suggestions.md SS7.2: "each attempt is recorded in
        // the debug capture with its outcome", regardless of whether Diagnostics is open.
        result.autocorrectDebug?.let(::reportAutocorrectionDebug)
        // spec expansion-clipboard-pickers-launcher.md SS6.2: an assigned key fired, or the Sym-armed mode just armed.
        result.launcherKey?.let { decision -> runCatching { launcherKeys.perform(decision) }.onFailure { error -> Log.e(TAG, "launcher key crashed", error) } }
        // spec: trackpad-caret-nav.md SS5.2, SS5.7: "70 ms haptic when nav mode turns on; none when it turns off."
        if (result.navModeTransition == NavModeTransition.ENTERED) performHaptic(NAV_MODE_HAPTIC_MS)
        // spec trackpad-caret-nav.md SS5.5's `native_ctrl` row, "with no field": nav mode's Ctrl
        // is a latch, not a physical hold, so the raw stroke carries no Ctrl meta bit for the app
        // to see; this synthesizes the real combo instead of the bare letter that used to reach it.
        result.forwardAsCtrlCombo?.let { key -> runCatching { sendNavModeCtrlCombo(key) }.onFailure { error -> Log.e(TAG, "nav mode Ctrl combo synth crashed", error) } }
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

    private val symGridListener = object : SymGridPanelController.Listener {
        // spec SS5.4, SS5.7: the same `KeyboardPipeline.onKeyStroke` path a physical press of this
        // letter takes while the page is open, so `trySymPageKey`'s commit, French spacing and
        // `sym_auto_close` all run exactly as they do for the hardware key (`processKeyStroke`
        // already calls `syncSymPanels`/`refreshCandidatesStrip` when it returns).
        override fun onKeyTapped(letter: Char) {
            runCatching {
                val now = SystemClock.uptimeMillis()
                val key = KeyId.Letter(letter)
                processKeyStroke(KeyStroke(key = key, edge = KeyEdge.DOWN, repeatCount = 0, timeMs = now))
                processKeyStroke(KeyStroke(key = key, edge = KeyEdge.UP, repeatCount = 0, timeMs = now))
            }.onFailure { error -> Log.e(TAG, "sym grid key tap crashed", error) }
        }

        // spec SS5.7, SS5.8: opens "Customize SYM Keyboard" already on this page's editor; a long
        // press also opens that letter's picker immediately and returns here when it closes.
        override fun onKeyLongPressed(letter: Char) = openSymCustomization(letter)
        override fun onPencil() = openSymCustomization(letter = null)

        // spec SS5.7: "opens the system input-method picker."
        override fun onGlobe() {
            runCatching { service.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() }
                .onFailure { error -> Log.e(TAG, "sym grid globe crashed", error) }
        }

        override fun onClose() = closeSymPanel()
    }

    /**
     * Shows or hides the clipboard panel, the emoji picker and the Emoji/Symbols key-layer grid to
     * match the pipeline's open Sym page (0 through 4). These three are already mutually exclusive
     * by construction (one `currentSymPage` int), but the quick launcher is a fourth bottom overlay
     * with no idea any of this exists; opening a Sym page while it is up would otherwise stack two
     * overlays on screen at once. This function dismisses the quick launcher when a Sym page opens;
     * [QuickLauncherController.onOpened] (wired to [closeSymPanel] below) does the reverse.
     */
    private fun syncSymPanels() {
        runCatching {
            val page = pipeline.currentSymPage
            if (page > 0 && quickLauncher.isOpen) quickLauncher.dismiss()
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
            val gridPage = SymGridPage.forPageNumber(page)
            if (gridPage != null) {
                symGridPanel.show(gridPage, symGridCharacters(gridPage), theme, stripHeightPx(), symGridListener)
            } else {
                symGridPanel.hide()
            }
        }.onFailure { error -> Log.e(TAG, "sym panel sync crashed", error) }
    }

    /**
     * spec SS5.7: the grid's per-key characters, from the same layout a physical key on this page
     * reads (`ImeSettings.layout`'s custom-page merge, SS4.4). [ModifierState.shiftForcesUppercase]'s
     * exact Caps-Lock-plus-held-Shift-means-lowercase override is not reproduced here (that state
     * is private to `KeyboardPipeline`); the shipped Emoji and Symbols pages carry no uppercase
     * entry at all (layers-sym-alt.md SS3.3), so this only matters for a custom page (SS4.4).
     */
    private fun symGridCharacters(page: SymGridPage): Map<Char, String> {
        val glyph = pipeline.modifierGlyphInput()
        val shiftEffective = glyph.capsLockOn || glyph.shiftOneShotArmed || glyph.shiftPhysicallyHeld
        val map = when (page) {
            SymGridPage.EMOJI -> pipeline.layout.emojiPage
            SymGridPage.SYMBOLS -> pipeline.layout.symbolsPage
        }
        return CharacterResolution.symPageCharacters(map, shiftEffective)
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

    /** spec keys-and-modifiers.md SS7.3: "media_play_pause/media_previous/media_next: the media key dispatched through the audio manager", for [EditorOp.DispatchMediaKey] (a Ctrl mapping or nav mode letter mapped to a media action). */
    private fun dispatchMediaKey(effect: EditEffect) {
        val keyCode = when (effect) {
            EditEffect.MEDIA_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            EditEffect.MEDIA_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            EditEffect.MEDIA_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> return
        }
        val audio = service.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val now = SystemClock.uptimeMillis()
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
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
    /**
     * spec: trackpad-caret-nav.md SS3.6: the vendor firmware's own swipe-to-delete keycodes (D5),
     * independent of both providers above. Both edges of the two recognised keycodes are
     * consumed so the raw event never reaches the pipeline or the app; only a down with repeat
     * count 0 decides anything.
     */
    private fun interceptFirmwareSwipeKeycode(event: KeyEvent): Boolean {
        val isFirmwareCode = event.keyCode == VendorKeyCodes.SWIPE_TO_DELETE_PRIMARY || event.keyCode == VendorKeyCodes.SWIPE_TO_DELETE_SECONDARY
        if (!isFirmwareCode) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true
        val ic = service.currentInputConnection ?: return true
        val result = FirmwareSwipeKeycode.decide(
            keycode = event.keyCode,
            isFirmwareSwipeKeycode = true,
            swipeToDelete = lastSettings.typing.swipeToDelete,
            provider = lastSettings.keyboardSwipe.swipeToDeleteProvider,
        )
        if (result != FirmwareSwipeResult.DELETE_LAST_WORD) return true
        val deleted = deleteLastWordBeforeCursor(ic)
        if (deleted) refreshCandidatesStrip()
        // spec SS3.6: "consumed when something was deleted, otherwise falls through".
        return deleted
    }

    /**
     * spec: trackpad-caret-nav.md SS3.5's last paragraph, SS3.6's shared delete: "up to 100
     * characters are inspected; trailing whitespace is skipped, then the run of non-whitespace is
     * removed." A literal, self-contained implementation against the raw text before the cursor,
     * not `:core:text`'s richer word-boundary rules (out of this task's scope). The trailing
     * whitespace between the deleted word and the cursor is deleted along with it and reinserted,
     * since `InputConnection.deleteSurroundingText` only removes a run touching the cursor.
     */
    private fun deleteLastWordBeforeCursor(ic: InputConnection): Boolean {
        val text = ic.getTextBeforeCursor(100, 0)?.toString() ?: return false
        var end = text.length
        while (end > 0 && text[end - 1].isWhitespace()) end--
        var start = end
        while (start > 0 && !text[start - 1].isWhitespace()) start--
        if (start == end) return false
        val trailing = text.substring(end)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(text.length - start, 0)
        if (trailing.isNotEmpty()) ic.commitText(trailing, 1)
        ic.endBatchEdit()
        return true
    }

    /** spec: trackpad-caret-nav.md SS3.3's evaluated result, from the [keyboardSwipe] native provider. */
    private fun onKeyboardSwipeEvaluated(evaluation: SwipeEvaluation) {
        when (evaluation) {
            is SwipeEvaluation.Up -> onKeyboardSwipeUp(evaluation.third)
            SwipeEvaluation.Left -> onKeyboardSwipeLeft()
            SwipeEvaluation.Candidate, SwipeEvaluation.Debounced, SwipeEvaluation.None -> Unit
        }
    }

    /**
     * spec SS3.5, points 1 to 5. The gate and the add-word/slot decision are
     * [KeyboardSwipeUpDecision]'s pure call; committing the result reuses [onStripSlotTapped],
     * the exact path status-bar.md SS5.3 says a swipe's accept is "committed through".
     */
    private fun onKeyboardSwipeUp(third: SwipeThird) {
        runCatching {
            val model = pipeline.stripModel(
                clipboardCount = clipboard.count,
                dictationActive = dictationController.isActive,
                dictionaryInstalled = pipeline.resources.dictionaries.isNotEmpty(),
                subtypeLocale = currentStyle.locale,
                clipboardOverlayOpen = clipboardPanel.isShown,
            )
            val row = model.row
            // `:core:strip`'s own SuggestionRowRules.rowVisible already ANDs "Sym page 0, suggestions
            // enabled, field allows suggestions" (plus no clipboard overlay, a dictionary loaded)
            // into whether [row] is [SuggestionRow.Hidden] at all, so that one check stands in for
            // SS3.5 point 2's first three conditions without re-deriving them here.
            val rowVisible = row !is SuggestionRow.Hidden
            val gate = SwipeUpGate(
                symPageIsZero = rowVisible,
                suggestionsEnabled = rowVisible,
                fieldAllowsSuggestions = rowVisible,
                suggestionVisible = row is SuggestionRow.Slots,
                addWordCandidatePending = row is SuggestionRow.AddWordOnly || (row is SuggestionRow.Slots && row.left.kind == SlotKind.ADD_WORD),
            )
            val action = KeyboardSwipeUpDecision.decide(third, gate, keyboardSwipe.settings)
            val slot = when (action) {
                SwipeUpAction.Ignored -> return@runCatching
                SwipeUpAction.AddWord -> when (row) {
                    is SuggestionRow.AddWordOnly -> Slot(row.word, SlotKind.ADD_WORD)
                    is SuggestionRow.Slots -> row.left
                    SuggestionRow.Hidden -> return@runCatching
                }
                is SwipeUpAction.AcceptSlot -> (row as? SuggestionRow.Slots)?.get(action.slot.toSlotPosition()) ?: return@runCatching
            }
            if (!slot.isTappable) return@runCatching
            // spec SS3.5 point 1: latched Shift/Alt cleared before anything is committed.
            pipeline.releaseLatchedLayersForStripButton()
            onStripSlotTapped(slot)
        }.onFailure { error -> Log.e(TAG, "keyboard swipe up crashed", error) }
    }

    private fun SwipeSlot.toSlotPosition(): SlotPosition = when (this) {
        SwipeSlot.LEFT -> SlotPosition.LEFT
        SwipeSlot.CENTRE -> SlotPosition.CENTER
        SwipeSlot.RIGHT -> SlotPosition.RIGHT
    }

    /** spec SS3.5's last paragraph: an accepted left swipe deletes the last word before the cursor. */
    private fun onKeyboardSwipeLeft() {
        runCatching {
            val ic = service.currentInputConnection ?: return@runCatching
            pipeline.releaseLatchedLayersForStripButton()
            if (deleteLastWordBeforeCursor(ic)) refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "keyboard swipe left crashed", error) }
    }

    private fun interceptForTrackpad(event: KeyEvent): Boolean {
        if (!pipeline.settings.screenTrackpadEnabled) return false
        val trackpadKey = classifyTrackpadKey(event.keyCode)
        val isTriggerKey = trackpadKey != null && TrackpadPhysicalKey.matchesTrigger(trackpadKey, trackpad.activationSettings.triggerKey)
        // spec SS2.2: "A Space down that already carries Ctrl or Alt in its meta state is never a
        // trigger: Ctrl+Space and Alt+Space go straight down the normal pipeline." A Ctrl or Alt
        // tap (not held) arms PhysiBoard's own one-shot in [ModifierState] without the physical key
        // still being down, so the raw event's meta bits alone miss a one-shot- or latch-armed
        // Ctrl+Space/Alt+Space entirely; both sources are consulted here. A nav-mode-originated Ctrl
        // latch ([ModifierGlyphInput.ctrlLatchedNotNavMode] excludes it on purpose) is a different
        // feature, not "Ctrl held for a chord", so it does not disqualify the trigger.
        val glyph = pipeline.modifierGlyphInput()
        val carriesDisqualifyingMeta = trackpadKey == TrackpadPhysicalKey.SPACE &&
            (event.metaState and KeyEvent.META_CTRL_ON != 0 || event.metaState and KeyEvent.META_ALT_ON != 0 ||
                glyph.ctrlOneShotArmed || glyph.ctrlLatchedNotNavMode || glyph.altOneShotArmed || glyph.altLatched)
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
                dispatchMediaKey = ::dispatchMediaKey,
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
    // Commands. spec: keys-and-modifiers.md SS3.3, SS4.4, SS7.5, SS15 item 3, SS12.2 (a Fn Layer
    // `command` mapping): dictation, the assistant and layout switching are wired below; nav
    // mode's own exit command has no owning module yet and is accepted and ignored rather than
    // guessed at; wiring it is a change to this one function.
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
            // spec keys-and-modifiers.md SS7.5: the three layout-switch chords, once `:core:keys`
            // has already decided one fires (LayerResolver.Context.canSwitchLayout).
            KeyCommands.SWITCH_LAYOUT -> runCatching { switchToNextInputStyle() }.onFailure { error -> Log.e(TAG, "layout switch crashed", error) }
        }
    }

    /**
     * spec trackpad-caret-nav.md SS3.3, verbatim: "The keyboard's own window (its decor view) is
     * made focusable in touch mode, given focus, and a generic-motion listener is attached to it
     * whenever the editor starts, the keyboard window is shown, or the provider preference
     * changes." Attaching the listener to [StatusBarView] (the small candidates/strip view)
     * alone, as an earlier revision did, left the feature dead on the real device: nothing had
     * ever made that view -- or any view in this window -- focusable/focused, and the vendor
     * firmware's touchpad-source `MotionEvent`s go to whatever holds focus, or nowhere.
     * [KeyboardSwipeController.accepts] re-reads live settings/eligibility on every event, so
     * re-running this at each of the three named triggers is cheap and always current; it is not
     * gated on whether anything actually changed.
     */
    private fun attachKeyboardSwipeListener() {
        val decor = service.window?.window?.decorView ?: return
        decor.isFocusableInTouchMode = true
        decor.requestFocus()
        decor.setOnGenericMotionListener { _, event ->
            if (!keyboardSwipe.accepts(event)) return@setOnGenericMotionListener false
            onKeyboardSwipeEvaluated(keyboardSwipe.onGenericMotion(event))
            true
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
            barHeightDp = strip.barHeightDp,
            listener = stripListener,
        )
        statusBar = view
        // spec trackpad-caret-nav.md SS3.3: attached to the decor view, not this strip view, at
        // each of the three named triggers (see [attachKeyboardSwipeListener]); building the
        // strip is not itself one of those triggers, but the first attach still has to happen
        // somewhere before the first of them fires.
        attachKeyboardSwipeListener()
        // spec trackpad-caret-nav.md SS6: "every touch down... on the keyboard's chrome layout,
        // that is the strip and everything drawn in the keyboard window" takes a pulse, and any
        // held pulse is released when that layout leaves its window. These two hooks sit in
        // StatusBarView's own dispatchTouchEvent/onDetachedFromWindow rather than in an
        // OnTouchListener here, because a listener on the root is never consulted for a down a
        // child view consumes: tapping a suggestion slot or a strip button, which is exactly the
        // touch SS6 exists for, would leave the screen free to time out.
        view.onChromeTouchDown = touchAwakeLock::onChromeTouchDown
        view.onChromeDetached = touchAwakeLock::onChromeDetached
        refreshCandidatesStrip()
        return view
    }

    private var lastLanguageTapMs: Long? = null

    private val stripListener = object : StatusBarView.Listener {
        override fun onSlotTapped(slot: Slot) = onStripSlotTapped(slot)

        /**
         * spec status-bar.md SS5.3: enters action mode with the eye (always) and trash (only when
         * the word is already a personal word, dictionaries-languages.md SS7's [UserWordStore])
         * buttons [SuggestionRowRules.actionModeButtons] computes for this slot. spec autocorrect-
         * suggestions.md SS6.2: "Long-pressing it opens the add-substitution sheet" for the
         * add-word slot instead, since [SuggestionRowRules.actionModeButtons] never offers one.
         */
        override fun onSlotLongPressed(position: SlotPosition, slot: Slot) {
            runCatching {
                if (!slot.isTappable) return@runCatching
                if (slot.kind == SlotKind.ADD_WORD) {
                    openAddSubstitutionSheet(slot.text)
                    return@runCatching
                }
                val personal = userWordStore.sourceOf(slot.text) == WordSource.PERSONAL
                val buttons = SuggestionRowRules.actionModeButtons(slot, wordInPersonalDictionary = personal)
                if (buttons.isEmpty()) return@runCatching
                performSlotTapHaptic()
                statusBar?.enterActionMode(position, buttons)
            }.onFailure { error -> Log.e(TAG, "slot long press crashed", error) }
        }

        /** spec status-bar.md SS5.3, autocorrect-suggestions.md SS5: the action row's eye (hide) and trash (forget from the personal dictionary) buttons. */
        override fun onSlotActionButtonTapped(position: SlotPosition, slot: Slot, action: SlotActionButton) {
            runCatching {
                when (action) {
                    SlotActionButton.HIDE_SUGGESTION -> pipeline.hideSuggestion(slot.text)
                    SlotActionButton.DELETE_FROM_PERSONAL_DICTIONARY -> deletePersonalWord(slot.text)
                }
                refreshCandidatesStrip()
            }.onFailure { error -> Log.e(TAG, "slot action button crashed", error) }
        }

        override fun onButtonTapped(button: StripButton) = onStripButtonTapped(button)
        override fun onButtonLongPressed(button: StripButton) = performStripAction(button.longPress, button)

        /** spec SS6.4: "Tapping any item closes the overlay and performs the action." */
        override fun onQuickActionTapped(button: StripButton) {
            closeQuickActions()
            onStripButtonTapped(button)
        }

        /** spec SS6.4: "The close button and the hardware Back key close it." */
        override fun onQuickActionsClosed() = closeQuickActions()

        override fun onBacklightNudgeTapped() = openOwnApp()

        /** spec SS10: "tapping '✕' collapses it." */
        override fun onBacklightNudgeDismissed() {
            backlightNudgeMemory = BacklightNudgeEpisode.onDismissed(backlightNudgeMemory)
            handler.removeCallbacks(backlightNudgeTimeoutRunnable)
            statusBar?.renderBacklightNudge(BacklightNudgeVisibility.HIDDEN)
        }
    }

    /**
     * spec SS5.3: a suggestion "is committed through the same path as accepting it from the
     * keyboard"; the expansion tap has no owning module yet and is accepted as a suggestion rather
     * than dropped. dictionaries-languages.md SS7: an [SlotKind.ADD_WORD] tap additionally makes
     * the word a real personal word ([persistAddedWord]), not just typed text.
     */
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
            if (slot.kind == SlotKind.ADD_WORD) persistAddedWord(slot.text)
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

    /**
     * spec SS6.1: the tap haptic, the "latched layer released first" rule, then the action. The
     * hamburger button is special-cased: [refreshCandidatesStrip] is what SS6.4/SS17 mean by "in
     * hardware mode, on every strip refresh" the overlay closes, so calling it right after this
     * same tap opened the overlay would close it before the user ever saw it. Every other button
     * (including one tapped from inside the overlay itself, via [StatusBarView.Listener.onQuickActionTapped])
     * still refreshes as before.
     */
    private fun onStripButtonTapped(button: StripButton) {
        runCatching {
            when (button.haptic) {
                TapHaptic.SYSTEM_KEYBOARD_TAP -> performHaptic()
                TapHaptic.FIXED_25_MS -> performHaptic(STRIP_FIXED_HAPTIC_MS)
            }
            if (button.releasesLatchedLayerFirst) pipeline.releaseLatchedLayersForStripButton()
            performStripAction(button.tap, button)
            if (button != StripButton.HAMBURGER) refreshCandidatesStrip()
        }.onFailure { error -> Log.e(TAG, "${button.id} tap crashed", error) }
    }

    /**
     * spec SS6.1's actions, wired to what exists today. Dictation (dictation.md), the Ctrl+Z /
     * Ctrl+Y key events and language switching (dictionaries-languages.md SS9.1's button row) are
     * real. SPEC GAP / missing module for the rest: the Sym pages (layers-sym-alt.md) and the
     * quick-actions overlay (SS6.4) do not exist in this milestone, so those buttons render, give
     * their haptic, and do nothing visible; "Open settings" launches the app's only activity.
     */
    private fun performStripAction(action: StripAction, button: StripButton) {
        when (action) {
            StripAction.Nothing -> Unit
            StripAction.StartDictation -> onDictationTrigger()
            is StripAction.SendCtrlCombo -> sendCtrlCombo(action.letter)
            StripAction.CycleLanguage -> {
                // spec SS6.1: "a tap within 500 ms of the last accepted tap is ignored".
                val now = SystemClock.uptimeMillis()
                if (LanguageTapDebounce.accepts(lastLanguageTapMs, now)) {
                    lastLanguageTapMs = now
                    runCatching { switchToNextInputStyle() }.onFailure { error -> Log.e(TAG, "language button switch crashed", error) }
                }
            }
            StripAction.OpenSettings -> openOwnApp()
            // spec layers-sym-alt.md SS4.3: every direct-open button (clipboard 3, emoji picker 4,
            // symbols 2) opens its page and toggles; page 1 (the emoji key layer) has no button of
            // its own in the catalogue (StripButton.kt's own note) and is reached only via the Sym
            // key's cycle (SS4.2).
            is StripAction.OpenSymPage -> {
                pipeline.toggleSymPage(action.page)
                syncSymPanels()
            }
            StripAction.OpenQuickActions -> toggleQuickActions()
        }
    }

    // -----------------------------------------------------------------------------------------
    // The hamburger's quick-actions overlay. spec: status-bar.md SS6.1, SS6.4, SS17.
    // -----------------------------------------------------------------------------------------

    /** True only in this class; [StripModel.quickActionsOpen] is always false by design (its own KDoc) since it is recomputed fresh on every refresh. */
    private var quickActionsOpen = false

    private fun toggleQuickActions() {
        quickActionsOpen = !quickActionsOpen
        statusBar?.setQuickActionsOpen(quickActionsOpen)
    }

    /** spec SS6.4: the close button, any mirrored action, the hardware Back key, an app-connection change, or (via [refreshCandidatesStrip]) any other refresh. */
    private fun closeQuickActions() {
        if (!quickActionsOpen) return
        quickActionsOpen = false
        statusBar?.setQuickActionsOpen(false)
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

    /**
     * spec trackpad-caret-nav.md SS5.5's `native_ctrl` row, "with no field": [key]'s down and up,
     * synthesized with Ctrl meta, the same shape [sendCtrlCombo] already uses for the action-mode
     * undo/redo buttons but for any of the 26 letter keys a `native_ctrl` Fn Layer mapping can name,
     * not just Z/Y. [AssignableKeys.keycodeOf] (`:core:actions`) is the reverse `KeyId`-to-keycode
     * map [KeyboardPipeline]'s own KDoc says this needed (`:device:titan`'s `KeyNormalizer` only
     * goes the other way). A [key] the map cannot resolve to a real keycode is a no-op.
     */
    private fun sendNavModeCtrlCombo(key: KeyId) {
        val ic = service.currentInputConnection ?: return
        val keyCode = AssignableKeys.keycodeOf(key) ?: return
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
     * spec SS5.7, SS5.8: the pencil and a key long-press on the Sym grid overlay open "Customize
     * SYM Keyboard" already on the open page's editor; with [letter] present the picker for that
     * letter opens immediately and the screen finishes as soon as it closes. Same pattern as
     * [openOwnApp]: a launch intent for this app's own activity, since `:ime` opens no activity of
     * its own.
     */
    private fun openSymCustomization(letter: Char?) {
        runCatching {
            val intent = service.packageManager.getLaunchIntentForPackage(service.packageName) ?: return
            // spec: layers-sym-alt.md SS5.8: "records the current page in pending_restore_sym_page
            // when a page is open" -- the intent extra alone only survives while this keyboard
            // process stays alive; the settings row is what a process death between here and the
            // screen's normal finish would otherwise lose.
            if (pipeline.currentSymPage > 0) {
                settingsSource?.write { stored -> stored.copy(symPages = stored.symPages.copy(pendingRestoreSymPage = pipeline.currentSymPage)) }
            }
            intent.putExtra(SymCustomizationLink.EXTRA_INITIAL_SYM_PAGE, pipeline.currentSymPage)
            if (letter != null) {
                intent.putExtra(SymCustomizationLink.EXTRA_INITIAL_SYM_KEY_CODE, KeyEvent.KEYCODE_A + (letter.uppercaseChar() - 'A'))
                intent.putExtra(SymCustomizationLink.EXTRA_OPEN_SYM_PICKER, true)
                intent.putExtra(SymCustomizationLink.EXTRA_RETURN_AFTER_PICKER, true)
            }
            service.startActivity(intent)
        }.onFailure { error -> Log.e(TAG, "sym customization open crashed", error) }
    }

    /**
     * spec: autocorrect-suggestions.md SS6.2, SS8.5: long-pressing the add-word candidate opens
     * the "Add substitution" sheet for [word], the same package-restricted-intent pattern
     * [brobata.physiboard.ime.actions.LauncherKeysController.openAssignmentSheet] already uses for
     * its own sheet. [currentStyle]'s own language answers "the current subtype language"; SS8.5's
     * fallback ("`it` when the subtype has no language") is the settings app's sheet's own job,
     * not this call site's, so a blank language is passed through as-is.
     */
    private fun openAddSubstitutionSheet(word: String) {
        runCatching {
            val intent = Intent(AddSubstitutionSheet.ACTION_ADD_SUBSTITUTION).apply {
                setPackage(service.packageName)
                putExtra(AddSubstitutionSheet.EXTRA_WORD, word)
                putExtra(AddSubstitutionSheet.EXTRA_LANGUAGE_CODE, currentStyle.primaryLanguage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            performSlotTapHaptic()
            service.startActivity(intent)
        }.onFailure { error -> Log.e(TAG, "add substitution sheet open crashed", error) }
    }

    /**
     * spec: status-bar.md SS1's "refresh"; also trackpad-caret-nav.md SS4.6's "recomputes its items
     * on every strip refresh" for the caret badge and SS4.7's refresh-driven retry. On the typing
     * path: the model is a handful of allocations and [StatusBarView.render] returns at once when
     * nothing changed, and both are guarded so a strip bug never reaches the keystroke.
     */
    private fun refreshCandidatesStrip() {
        // spec SS6.4, SS17: "in hardware mode, on every strip refresh" the overlay closes.
        closeQuickActions()
        statusBar?.let { view ->
            runCatching {
                view.render(
                    pipeline.stripModel(
                        clipboardCount = clipboard.count, // spec expansion-clipboard-pickers-launcher.md SS3.4: the count is pushed on every refresh.
                        dictationActive = dictationController.isActive,
                        // spec: autocorrect-suggestions.md SS2 point 3, SS6.2: this must answer for the
                    // *primary* dictionary specifically. `pipeline.resources.dictionaries.isNotEmpty()`
                    // used to answer "any dictionary at all", which reports ready too early when an
                    // additional suggestion language's load lands before the primary one's own.
                    dictionaryInstalled = primaryLanguage in loadedDictionaries,
                        subtypeLocale = currentStyle.locale, // spec dictionaries-languages.md SS9.2: the language button's text follows the active input style.
                        clipboardOverlayOpen = clipboardPanel.isShown,
                    ),
                )
                view.languageLayoutName = currentStyle.layoutId
            }.onFailure { error -> Log.e(TAG, "strip refresh crashed", error) }
        }
        runCatching { refreshExpansionPopup() }.onFailure { error -> Log.e(TAG, "expansion popup crashed", error) }
        refreshCaretBadge()
        retryCursorUpdateOnRefresh()
        refreshBacklightNudge()
        refreshStatusIcon()
    }

    // -----------------------------------------------------------------------------------------
    // The system status bar's modifier/Sym/nav icon. spec: keys-and-modifiers.md SS13.1;
    // trackpad-caret-nav.md SS5.7 ("nav mode wins the icon slot"). `StatusBarModifierIcon` (core/keys)
    // decides which of the 28 states applies; this only maps that onto `InputMethodService`'s
    // status icon slot and forced-on-screen exclusion, per the same section.
    // -----------------------------------------------------------------------------------------

    /** Last icon actually shown (or [StatusBarIcon.None] for hidden), so a same-icon refresh does not re-show it (spec: "Icon changes are deduplicated"). */
    private var lastShownStatusIcon: StatusBarIcon = StatusBarIcon.None

    /**
     * SPEC GAP: SS13.1 lists 26 distinct icons, one per non-empty Shift/Ctrl/Alt combination, plus
     * a Sym icon and a nav icon. This milestone ships one drawable
     * ([R.drawable.ic_status_modifier]) for every [StatusBarIcon.Modifiers] combination, reused
     * for [StatusBarIcon.Sym] and [StatusBarIcon.Nav] too: the icon's presence (and, for nav, its
     * priority over the modifier icon) is real, but its 28 distinct pictures are not drawn.
     * Authoring 26 hand-distinguishable icons is out of this task's scope; see the report.
     */
    private fun refreshStatusIcon() {
        val glyph = pipeline.modifierGlyphInput()
        val shift = StatusBarModifierIcon.shiftState(
            value = when {
                glyph.capsLockOn -> brobata.physiboard.core.keys.ShiftValue.CAPS
                glyph.shiftOneShotArmed -> brobata.physiboard.core.keys.ShiftValue.ONE_SHOT
                else -> brobata.physiboard.core.keys.ShiftValue.OFF
            },
            physicallyPressed = glyph.shiftPhysicallyHeld,
        )
        val ctrl = StatusBarModifierIcon.latchableState(glyph.ctrlLatchedNotNavMode, glyph.ctrlOneShotArmed, glyph.ctrlPhysicallyHeld)
        val alt = StatusBarModifierIcon.latchableState(glyph.altLatched, glyph.altOneShotArmed, glyph.altPhysicallyHeld)
        val icon = StatusBarModifierIcon.choose(shift, ctrl, alt, symPageOpen = glyph.symPageOpen, navModeActive = pipeline.navModeActive)
        if (icon == lastShownStatusIcon) return
        lastShownStatusIcon = icon
        runCatching {
            if (icon == StatusBarIcon.None) service.hideStatusIcon() else service.showStatusIcon(R.drawable.ic_status_modifier)
        }.onFailure { error -> Log.e(TAG, "status icon refresh crashed", error) }
    }

    // -----------------------------------------------------------------------------------------
    // The smart-backlight-paused nudge. spec: status-bar.md SS10, SS14.
    // -----------------------------------------------------------------------------------------

    private var backlightNudgeMemory = BacklightNudgeMemory()
    private val backlightNudgeTimeoutRunnable = Runnable { onBacklightNudgeTimeout() }

    private fun refreshBacklightNudge() {
        runCatching {
            val settings = lastSettings
            val (next, visibility) = BacklightNudgeEpisode.onRefresh(
                backlightNudgeMemory,
                enabled = settings.device.smartBacklightEnabled,
                applied = settings.captures.smartBacklightApplied,
                nowMs = SystemClock.uptimeMillis(),
            )
            val justAppeared = backlightNudgeMemory.shownAtMs == null && next.shownAtMs != null
            backlightNudgeMemory = next
            if (justAppeared) {
                handler.removeCallbacks(backlightNudgeTimeoutRunnable)
                handler.postDelayed(backlightNudgeTimeoutRunnable, BacklightNudge.AUTO_COLLAPSE_MS)
            } else if (visibility == BacklightNudgeVisibility.HIDDEN) {
                handler.removeCallbacks(backlightNudgeTimeoutRunnable)
            }
            statusBar?.renderBacklightNudge(visibility)
        }.onFailure { error -> Log.e(TAG, "backlight nudge refresh crashed", error) }
    }

    /** spec SS10, SS14: "collapses on its own to a... dot after 4000 ms", independent of any refresh. */
    private fun onBacklightNudgeTimeout() {
        runCatching {
            backlightNudgeMemory = BacklightNudgeEpisode.onTimeoutElapsed(backlightNudgeMemory)
            val settings = lastSettings
            val visibility = BacklightNudge.visibility(
                enabled = settings.device.smartBacklightEnabled,
                applied = settings.captures.smartBacklightApplied,
                dismissedByUser = backlightNudgeMemory.dismissedByUser,
                collapsedToDot = backlightNudgeMemory.collapsedToDot,
            )
            statusBar?.renderBacklightNudge(visibility)
        }.onFailure { error -> Log.e(TAG, "backlight nudge timeout crashed", error) }
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

        /** spec: trackpad-caret-nav.md SS5.2, SS5.7: "70 ms haptic when nav mode turns on". */
        const val NAV_MODE_HAPTIC_MS = 70L

        /** spec: text-input.md SS2's one unified 240-character read. */
        const val TEXT_BEFORE_CURSOR_READ = 240

        /** `keyboard_layout`'s own default (`Settings.kt`'s `LanguagePrefs`); layers-sym-alt.md SS9.2's first bundled layout. Kept for reference; [shippedLayouts] now builds from `TitanLayouts.bundled()` directly. */
        const val SHIPPED_LAYOUT_ID = "qwerty"
    }
}
