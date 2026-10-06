package brobata.physiboard.ime

import brobata.physiboard.core.actions.launcher.LauncherKeyDecision
import brobata.physiboard.core.actions.launcher.LauncherKeyRouter
import brobata.physiboard.core.actions.launcher.LauncherKeySettings
import brobata.physiboard.core.actions.launcher.LauncherShortcuts
import brobata.physiboard.core.actions.launcher.PowerShortcutMode
import brobata.physiboard.core.actions.launcher.PowerShortcutState
import brobata.physiboard.core.actions.snippets.ExpansionGate
import brobata.physiboard.core.actions.snippets.ExpansionKeyResult
import brobata.physiboard.core.actions.snippets.ExpansionState
import brobata.physiboard.core.actions.snippets.SnippetExpansion
import brobata.physiboard.core.actions.snippets.SnippetMatch
import brobata.physiboard.core.actions.snippets.SnippetPresentation
import brobata.physiboard.core.actions.snippets.SnippetSettings
import brobata.physiboard.core.dict.Bigram
import brobata.physiboard.core.dict.NgramPrefix
import brobata.physiboard.core.dict.NgramStore
import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.AccidentalPressFilter
import brobata.physiboard.core.keys.AccidentalPressFilterState
import brobata.physiboard.core.keys.AccidentalPressSettings
import brobata.physiboard.core.keys.BounceFilter
import brobata.physiboard.core.keys.BounceFilterState
import brobata.physiboard.core.keys.BounceKeySettings
import brobata.physiboard.core.keys.FilterVerdict
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.EditEffect
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.LayerResolver
import brobata.physiboard.core.keys.LayoutDescription
import brobata.physiboard.core.keys.LongPressMode
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.keys.ModifierMachine
import brobata.physiboard.core.keys.ModifierSettings
import brobata.physiboard.core.keys.ModifierState
import brobata.physiboard.core.keys.ShiftValue
import brobata.physiboard.core.text.ShiftArmSource
import brobata.physiboard.core.keys.TypingSessionState
import brobata.physiboard.core.pointer.caret.ModifierGlyphInput
import brobata.physiboard.core.pointer.navmode.NavModeEntry
import brobata.physiboard.core.pointer.navmode.NavModeMap
import brobata.physiboard.core.pointer.navmode.NavModeTransition
import brobata.physiboard.core.strip.DipDecision
import brobata.physiboard.core.strip.DipEffect
import brobata.physiboard.core.strip.DipState
import brobata.physiboard.core.strip.ModifierIndicatorInput
import brobata.physiboard.core.strip.StripDip
import brobata.physiboard.core.strip.StripInputs
import brobata.physiboard.core.strip.StripModel
import brobata.physiboard.core.strip.StripSettings
import brobata.physiboard.core.text.LengthChangeAllowance
import brobata.physiboard.core.text.AddWordCandidate
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.AutoCapitalization
import brobata.physiboard.core.text.AutocorrectSettings
import brobata.physiboard.core.text.BoundaryDebugInfo
import brobata.physiboard.core.text.CapDecision
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.EditorTrust
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.EnterDecision
import brobata.physiboard.core.text.EnterIntent
import brobata.physiboard.core.text.ExtraSendShortcut
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.RankedSuggestion
import brobata.physiboard.core.text.RankingOptions
import brobata.physiboard.core.text.NextWordSuggestions
import brobata.physiboard.core.text.SuggestionRanking
import brobata.physiboard.core.text.TextInputPipeline
import brobata.physiboard.core.text.TextInputRequest
import brobata.physiboard.core.text.TextInputResources
import brobata.physiboard.core.text.TextInputSettingsBundle
import brobata.physiboard.core.text.TextInputState

/**
 * Every tunable [KeyboardPipeline] needs, bundled so callers pass one value. There is no
 * `:settings` module yet (docs/plans/rebuild-from-scratch.md build order step 6), so every field
 * here is a shipped default; wiring a real settings store later means constructing this from that
 * store instead of the defaults, not changing anything below.
 *
 * [textInput] deliberately does not use [TextInputSettingsBundle]'s own bare constructor defaults
 * for the automatic-correction gate. `:core:text`'s own defaults are the settings-catalog.md
 * "Code default" column: what a key reads as when it is literally absent from an empty
 * preferences store. A real Titan 2 Elite never runs with an empty store: every device applies
 * the factory baseline asset (`assets/common/default_settings.json`, settings-catalog.md SS4)
 * before the keyboard ever starts, and that baseline's `auto_replace_on_space_enter` is `true`
 * (SS4.1), not the code default's `false`. Since 3.0 has no settings module yet to apply that
 * baseline for real, the value shipped here has to BE the baseline directly, or automatic
 * correction (autocorrect-suggestions.md SS7.2 step 9, SS9) can never run on any build of this
 * milestone regardless of anything else being wired correctly: `BoundaryEngine.evaluate` returns
 * [brobata.physiboard.core.text.BoundaryOutcome.CommitPlain] unconditionally at its
 * `autoReplaceOnSpaceEnter` gate before it ever reaches a dictionary lookup. `maxAutoReplaceDistance`
 * and `useKeyboardProximity` are carried along from the same baseline entries (2 and `true`;
 * settings-catalog.md SS4.1) for the same reason: they are not the failure this fixes on their
 * own (an adjacent transposition like "wierd" is already within the code-default distance of 1),
 * but shipping the code default for one baseline row and not its neighbours would just move the
 * same "shipped default silently disagrees with the only device this milestone targets" mistake
 * one row down.
 *
 * [modifier] overrides [ModifierSettings]'s own bare `fnLongPressSpeechEnabled = false` for the
 * identical reason: settings-catalog.md SS2.8 lists `fn_long_press_speech`'s code default as
 * `false` but its baseline as `true` ("the first-run defaults turn it on"), and dictation.md
 * SS2.1 names holding Fn as the primary trigger on this phone. Shipping the code default here
 * would leave the one dictation trigger this milestone wires (see [DictationController]) armed
 * in code but silent on every device that has no settings store to flip it back on.
 *
 * [screenTrackpadEnabled] deliberately does the OPPOSITE of [modifier] and [textInput] above: it
 * ships the settings-catalog.md code default (`false`, SS row for `screen_trackpad_enabled`) and
 * NOT the baseline (`true`). Every other field in this class ships the baseline because being
 * silently absent would leave a real device behaving like a stripped-down 2.x install; the
 * trackpad is the opposite case. It intercepts Space, the single most-pressed key on the
 * keyboard, ahead of everything else in the key pipeline (trackpad-caret-nav.md SS2.2), and a
 * hold that is mistaken for a deliberate one swallows that keystroke for good with no replay
 * (SS2.3's "the swallowed down is not replayed when the hold succeeds"). Shipping the baseline
 * here, as an earlier revision of this class did, means there is no settings store yet to ever
 * turn it back off if that misfires -- which is exactly what reached the maintainer as a daily
 * driver where every Space press lost its keystroke and dragged the caret across the word instead
 * (see [brobata.physiboard.ime.KeyboardSession]'s own trackpad section). A feature a settings
 * screen cannot yet switch off must not default to "on" here; [KeyboardSession] gates the whole
 * interception on this flag so the fix is a real no-op, not a smaller window for the same bug.
 */
data class KeyboardSettings(
    val modifier: ModifierSettings = ModifierSettings(fnLongPressSpeechEnabled = true),
    val resolver: LayerResolver.LayerResolverSettings = LayerResolver.LayerResolverSettings(),
    val textInput: TextInputSettingsBundle = TextInputSettingsBundle(
        // The shipped keyboard is English (DEFAULT_SUBTYPE_LOCALE): spec autocorrect-suggestions.md SS9 step 8.
        lengthChangeAllowance = LengthChangeAllowance.ENGLISH,
        autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 2),
        rankingOptions = RankingOptions(useKeyboardProximity = true),
    ),
    val screenTrackpadEnabled: Boolean = false,
    /**
     * spec: status-bar.md SS15. Shipped defaults, same reasoning as [textInput]: the visibility
     * mode is `ALWAYS` (D9, "the shipped first-run default is 'Always'"), the slots are SS6.3's
     * first-run baseline, the dip list is seeded with Teams (SS12.2). A settings store constructs
     * this from its own values later; nothing in the strip reads a preference.
     */
    val statusBar: StripSettings = StripSettings(),
    /**
     * spec: expansion-clipboard-pickers-launcher.md SS2.8. [SnippetSettings.enabled] ships false
     * by the project's default-ON rule (expansion intercepts Space), see its own KDoc.
     */
    val expansion: SnippetSettings = SnippetSettings(),
    /** spec SS6.6: power shortcuts on, home-screen shortcuts off. */
    val launcherKeys: LauncherKeySettings = LauncherKeySettings(),
    /** spec SS6.1, D6: a fresh install has the quick launcher on Space; the store's first-read rule reproduces this (ImeSettings). */
    val launcherShortcuts: LauncherShortcuts = LauncherShortcuts().applyDefault(defaultAlreadyAssigned = false).shortcuts,
    /**
     * `nav_mode_enabled`. spec: keys-and-modifiers.md SS15 point 3: with no field, a Ctrl key is
     * nav mode's "when `nav_mode_enabled` (default true) or nav mode is active". Consulted by
     * [KeyboardPipeline.navModeCtrlIsOurConcern], the gate [KeyboardPipeline.onKeyStroke]'s own
     * no-field Ctrl branch uses before handing the stroke to [NavModeEntry].
     */
    val navModeEnabled: Boolean = true,
    /** spec: keys-and-modifiers.md SS10, the `bounce_keys_*` rows; run at the very top of [KeyboardPipeline.onKeyStroke], ahead of everything else. */
    val bounceKeys: BounceKeySettings = BounceKeySettings(),
    /** spec: keys-and-modifiers.md SS11, `overlapping_keys_enabled`. */
    val overlappingKeys: AccidentalPressSettings = AccidentalPressSettings(),
)

/**
 * What [KeyboardSession] must still do to the real `InputConnection` after one pipeline call.
 * [enterDelivery] is non-null only for the Enter deliveries per-app-behavior.md SS3.4 hands to
 * `:ime` to perform for real (see [brobata.physiboard.core.text.TextInputResult.enterDelivery]);
 * [consumed] for those is provisional until [KeyboardSession] learns whether the real call was
 * delivered (SS3.4, "Handled if and only if the request was delivered").
 */
data class PipelineResult(
    val ops: List<EditorOp>,
    val consumed: Boolean,
    val enterDelivery: EnterIntent? = null,
    /**
     * The key was handed to the app unconsumed and the app is expected to edit the field with it
     * (a plain Backspace or Delete, a Ctrl+Backspace under reduced trust, a forwarded Ctrl+X/V/Z).
     * Dictation's c440844 invariant needs that fact as much as an edit this keyboard made itself.
     */
    val appMayEditField: Boolean = false,
    /** spec expansion-clipboard-pickers-launcher.md SS6.2: an assigned key fired (or an unassigned one asks for the sheet); `:ime` performs it. */
    val launcherKey: LauncherKeyDecision? = null,
    /** spec SS6.2 B: the Sym-armed mode just armed at this time; `:ime` schedules the toast and the disarm. */
    val powerModeArmedAtMs: Long? = null,
    /**
     * Non-null only for a Ctrl stroke with no field that [NavModeEntry] just latched or unlatched
     * (trackpad-caret-nav.md SS5.2, SS5.7): `:ime` plays the 70 ms entry haptic and shows or hides
     * the system status icon from this, since [KeyboardPipeline] itself has no haptic or status-bar
     * API to call.
     */
    val navModeTransition: NavModeTransition? = null,
    /**
     * spec app-shell.md SS11, autocorrect-suggestions.md SS7.2: non-null only when this keystroke
     * ran a word-boundary evaluation, so [KeyboardSession] can forward it to the debug capture.
     */
    val autocorrectDebug: BoundaryDebugInfo? = null,
    /**
     * spec trackpad-caret-nav.md SS5.5's `native_ctrl` row, non-null only for the "with no field"
     * case: [key]'s down and up should be synthesized with Ctrl meta and sent through the input
     * connection. Unlike the in-field physical-Ctrl-combo case (`LayerResolver.resolveCtrlActive`'s
     * own [Action.ForwardAsCtrlCombo], answered with plain [PipelineResult.NOT_CONSUMED] since the
     * real event already carries Ctrl's meta bit), nav mode's Ctrl is a latch here, not a physical
     * hold, so the raw stroke carries no such bit; letting it fall through unconsumed would deliver
     * a bare letter instead of the combo the user's own mapping asked for. `:ime` is where the
     * synthesis happens ([brobata.physiboard.device.titan.KeyNormalizer] is forward-only, and
     * `:core:actions`' [brobata.physiboard.core.actions.launcher.AssignableKeys.keycodeOf] is the
     * one reverse `KeyId`-to-keycode map this project already has).
     */
    val forwardAsCtrlCombo: KeyId? = null,
    /**
     * The key went down with Alt active in any form (held, one-shot or latched), so a single
     * character put down by the last of [ops] came from the Alt layer and must not reach the app
     * as a bare one-character commit. Only the last op: the autocorrect hand-off's first commit of
     * the character still goes out plainly, which matters only where autocorrect runs, never in an
     * exact-typing app. Chrome records every hardware key down before the keyboard sees it
     * and, when the keyboard then commits exactly the character the device's own key map gives
     * that recorded event, throws the commit away and replays the recorded key instead. On the
     * Titan that map says Alt+M is "." just as the Alt layer does, so a held Alt+M committed as
     * "." reached the page as the key M with Alt down, which a terminal reads as Meta-m and
     * types nothing (PersaLink, 2026-10-06). [EditorBridge.applyEditorOps] types such a
     * character as the plain key presses that produce it, which Chrome passes on as they are.
     * Also set for a long press in Alt mode ([checkLongPressTick]).
     */
    val altLayerStroke: Boolean = false,
) {
    companion object {
        val NOT_CONSUMED: PipelineResult = PipelineResult(emptyList(), consumed = false)
        val CONSUMED_NO_OP: PipelineResult = PipelineResult(emptyList(), consumed = true)
    }
}

/**
 * Orchestrates one hardware key event across `:device:titan`, `:core:keys` and `:core:text`.
 *
 * This class carries no `android.*` import and decides no typing rule of its own; every branch
 * below either routes to the pure module that owns the decision or threads that module's own
 * state forward. Splitting it out of [KeyboardSession] is what lets the ordering below run under
 * a plain JUnit test (see `KeyboardPipelineTest`), matching the house rule that a JVM test must be
 * able to reach anything this module decides.
 *
 * [layout] is the Titan 2 Elite's layout, [resources] the dictionaries/rule sets/personal words
 * `:core:text` scores suggestions against (empty by default: no loader for `.pbd` files exists
 * yet), and [onCommand] the hook a future module (dictation, layout switching, nav mode, the
 * assistant) attaches to receive an [Action.RunCommand] id. None of those modules exist yet in
 * this milestone, so the default does nothing; see [KeyboardSession.handleCommand].
 */
internal class KeyboardPipeline(
    var layout: LayoutDescription,
    var resources: TextInputResources = TextInputResources(),
    var settings: KeyboardSettings = KeyboardSettings(),
    private val onCommand: (String) -> Unit = {},
) {
    private var modifierState = ModifierState()
    private var typingState = TypingSessionState()
    private var textInputState = TextInputState()
    /**
     * spec: autocorrect-suggestions.md SS4. The in-memory overlay a just-learned pair is visible
     * through immediately, before `:ime` finishes persisting it to `user_ngrams.db`; [onBigramLearned]
     * is `:ime`'s hook to queue that write off the main thread.
     */
    private var ngramStore: NgramStore = NgramStore.empty()

    /** spec: SS4, "the next word is a sentence start" until a soft boundary completes a word. */
    private var nextWordPrefixKey: String = NgramStore.SENTENCE_START

    /** The pair the last boundary learned (prefix, word), so a mix-up fix of that word one boundary later can take it back. */
    private var lastLearnedPair: Pair<String, String>? = null

    /** `:ime` supplies the loaded rows once at start and after every reload; see [KeyboardSession]. */
    fun onNgramStoreLoaded(loaded: List<Bigram>) {
        ngramStore = ngramStore.mergedWith(loaded)
    }

    /** spec: SS4. `:ime`'s seam to persist a newly learned pair off the main thread; a no-op until set. */
    var onBigramLearned: (locale: String, prefix: String, nextWord: String) -> Unit = { _, _, _ -> }

    /** `:ime`'s seam to persist taking back one learn of a pair ([NgramStore.unlearn]); a no-op until set. */
    var onBigramUnlearned: (locale: String, prefix: String, nextWord: String) -> Unit = { _, _, _ -> }

    /**
     * spec: SS4, "deleting a user word forgets it as a next word under every prefix" (SS5:
     * "forgets it as a next word everywhere"): the strip's delete button on a personal word.
     */
    fun forgetWordAsNextWordEverywhere(word: String) {
        ngramStore = ngramStore.forgetEverywhere(word)
    }

    /** spec: keys-and-modifiers.md SS10, SS10.3: "Filter memory clears on every start of input." */
    private var bounceFilterState = BounceFilterState()
    /** spec: keys-and-modifiers.md SS11: "state resets on start and finish of input and on any input device change." Input-device-change resets are `:ime`'s to add; no such callback exists yet in this milestone. */
    private var accidentalPressFilterState = AccidentalPressFilterState()
    private var activeField = FieldContext(FieldKind.NOT_EDITABLE)

    /**
     * Set by `:ime` from the editor's own caret reports: the app is drawing its text box under the
     * strip, so the strip collapses out of its way. See [brobata.physiboard.core.strip.StripOverlap].
     * Cleared whenever a field starts, so one app's verdict never carries into the next.
     */
    var fieldDrawsUnderStrip: Boolean = false
    private var activeTrust = EditorTrust.FULL
    private var activeAppProfile = AppProfile.default(null)

    /** spec expansion-clipboard-pickers-launcher.md SS2: the open snippet matches, cleared whenever the editor changes (SS2.4). */
    private var expansion = ExpansionState.EMPTY

    /** spec SS6.2 B: the Sym-armed power shortcut mode, which only exists with no editable field. */
    private var powerMode = PowerShortcutState.IDLE

    /** spec SS6.2 A: whether the foreground package answers HOME; `:ime` resolves it, this class only routes on it. */
    var foregroundIsHome: Boolean = false

    /**
     * spec: keys-and-modifiers.md SS7.5: the layout-switch chords fire "only when another input
     * subtype exists to switch to"; with one installed "the chord does not fire". `:ime` supplies
     * the fact from `:core:subtype`'s own catalog (`InputStyleCatalog.anotherStyleAvailable`, set
     * in `KeyboardSession.applySettings`); every chord's own switch (`ModifierSettings`) is
     * checked on top of it.
     */
    var anotherSubtypeAvailable: Boolean = false

    val fieldContext: FieldContext get() = activeField

    /** spec layers-sym-alt.md SS5.2: the open Sym page (0 for none); `:ime` shows the clipboard and emoji panels for pages 3 and 4. */
    val currentSymPage: Int get() = modifierState.sym.currentPageNumber

    /**
     * spec: layers-sym-alt.md SS5.8: "the page is reopened if it is in the enabled cycle,
     * otherwise the first enabled page is opened, otherwise none." [requestedPageNumber] is
     * `restore_sym_page` as read at field start (0 when nothing was pending); [KeyboardSession]
     * calls this once, from [onStartInput]'s caller, and clears the stored value afterward.
     */
    fun restoreSymPage(requestedPageNumber: Int) {
        if (requestedPageNumber <= 0) return
        val resolved = layout.symPagesConfig.restorePage(requestedPageNumber)
        modifierState = modifierState.copy(sym = modifierState.sym.copy(currentPageNumber = resolved))
    }

    /** spec: trackpad-caret-nav.md SS5.7: whether the status bar should show the nav mode icon instead of the modifier icon. */
    val navModeActive: Boolean get() = modifierState.ctrl.latchFromNavMode

    /** spec layers-sym-alt.md SS4.3: a strip button opens its page "regardless of whether it is enabled in the cycle, and each one toggles". */
    fun toggleSymPage(page: Int) {
        val next = if (modifierState.sym.currentPageNumber == page) 0 else page
        modifierState = modifierState.copy(sym = modifierState.sym.copy(currentPageNumber = next))
    }

    /** spec SS3.5, SS4.3: the panels' own close buttons "ask the Sym session to close the page". */
    fun closeSymPage() {
        if (modifierState.sym.currentPageNumber != 0) modifierState = modifierState.copy(sym = modifierState.sym.copy(currentPageNumber = 0))
    }

    /**
     * The caret badge's own small view of the modifier state. spec: trackpad-caret-nav.md SS4.2's
     * table, one field per row; [ModifierGlyphInput] is `:core:pointer`'s type on purpose (its own
     * KDoc), so this is the one place that translates `:core:keys`' richer [ModifierState] into it.
     */
    fun modifierGlyphInput(): ModifierGlyphInput = ModifierGlyphInput(
        capsLockOn = modifierState.shift.value == ShiftValue.CAPS,
        shiftOneShotArmed = modifierState.shift.value == ShiftValue.ONE_SHOT,
        shiftPhysicallyHeld = modifierState.shift.physicallyPressed,
        altLatched = modifierState.alt.latched,
        altOneShotArmed = modifierState.alt.oneShot,
        altPhysicallyHeld = modifierState.alt.physicallyPressed,
        ctrlLatchedNotNavMode = modifierState.ctrl.latched && !modifierState.ctrl.latchFromNavMode,
        ctrlOneShotArmed = modifierState.ctrl.oneShot,
        ctrlPhysicallyHeld = modifierState.ctrl.physicallyPressed,
        symPageOpen = modifierState.sym.currentPageNumber != 0,
    )

    /**
     * spec: trackpad-caret-nav.md SS2.6, "whether Shift is on is re-read on every move event":
     * physically held, one-shot armed, or the visual Shift layer latched. Exposed for
     * [brobata.physiboard.ime.pointer.TrackpadOverlayController], the one caller outside this
     * class that needs a live answer to "is Shift active" without seeing [ModifierState] itself.
     */
    fun isTrackpadShiftActive(): Boolean =
        modifierState.shift.physicallyPressed || modifierState.shift.value == ShiftValue.ONE_SHOT || modifierState.shift.layerLatched

    private val ENTER_KEY = KeyId.Control(ControlKey.ENTER)
    private val SYM_KEY = KeyId.Modifier(ModifierKey.SYM)
    private val CTRL_KEY = KeyId.Modifier(ModifierKey.CTRL)

    /** spec: text-input.md SS9.3, "200 characters before". */
    private val AUTO_CAP_SUPPRESSION_CONTEXT_CHARS = 200

    /** When a long press is armed, the wall-clock time (same basis as [KeyStroke.timeMs]) it fires at. spec: keys-and-modifiers.md SS8.3. */
    val pendingLongPressDeadlineMs: Long?
        get() = typingState.pendingLongPress?.let { it.armedAtMs + it.thresholdMs }

    // -----------------------------------------------------------------------------------------
    // Field lifecycle. spec: status-bar.md SS13 ("modifier state is reset, nav mode preserved");
    // text-input.md SS3 (field classification, done by the caller from the platform's editor
    // info: `:ime`'s job, not this class's, see KeyboardSession.classifyField).
    // -----------------------------------------------------------------------------------------

    /**
     * A field started (fresh or restarting). spec: text-input.md SS3, SS9.1 ("field start"
     * trigger). [trust] is the per-app profile's editor-trust default, and [appProfile] the same
     * profile in full, including its already-resolved Enter fields (spec: rebuild-from-scratch.md
     * "The editor is not a reliable narrator" point 4, "the per-app profile it selects is passed
     * in like any other setting"); [KeyboardSession] resolves both before calling this.
     */
    fun onStartInput(
        field: FieldContext,
        trust: EditorTrust = EditorTrust.FULL,
        appProfile: AppProfile = AppProfile.default(null),
        textBeforeCursor: String? = null,
    ) {
        activeField = field
        activeTrust = trust
        activeAppProfile = appProfile
        // spec SS6.2 B: the Sym power-shortcut mode only exists "with no editable field"; focusing
        // a really editable one while it is still armed cancels its pending nav-mode restore
        // rather than letting the 5000 ms timer (or a later key) re-latch nav mode into the field
        // the user is now actively typing in -- turning the next few letters into arrows/clipboard
        // commands instead of text. The restore is dropped outright, not deferred: the user is
        // already typing, so there is no later "outside a field" moment left to apply it to.
        if (field.isReallyEditable && powerMode.isArmed) powerMode = PowerShortcutState.IDLE
        textInputState = textInputState.forNewField()
        typingState = TypingSessionState()
        modifierState = ModifierMachine.fullReset(modifierState, preserveNavModeLatch = true)
        expansion = ExpansionState.EMPTY
        // spec: keys-and-modifiers.md SS10.1 ("Filter memory clears on every start of input"), SS11 ("state resets on start... of input").
        bounceFilterState = BounceFilterState()
        accidentalPressFilterState = AccidentalPressFilterState()
        if (AutoCapitalization.evaluateFieldStartCapsLock(field)) {
            applyCapDecision(CapDecision.EnableCapsLock)
            return
        }
        // spec: text-input.md SS9.2, "capitalize at text start": the first letter of a fresh
        // field is decided here, from the editor's opening text, because an app that never
        // reports a selection change before the first key (Messages on the Titan, 2026-09-25)
        // otherwise gets no evaluation until after that letter has already been typed.
        val capContext = if (trust.contextRulesAllowed) textBeforeCursor else null
        val (capState, decision) = AutoCapitalization.evaluate(textInputState.autoCap, field, settings.textInput.autoCap, capContext)
        textInputState = textInputState.copy(autoCap = capState)
        applyCapDecision(decision)
    }

    /**
     * The same field's input restarted, which web fields and WebViews do freely mid-word. spec:
     * text-input.md line 85 (a restart reclassifies the field), line 463 (a restart resets a Shift
     * one-shot and re-evaluates auto-cap) and line 497 (restart-scoped state is kept). Unlike
     * [onStartInput] this keeps the tracked word, resynced from [textBeforeCursor], and every other
     * fact that describes text still before the cursor, so autocorrect and Backspace-undo survive
     * a restart instead of dying at the first one. The one exception is the mix-up fix of the
     * previous word, which waits for a word typed after the restart
     * ([TextInputState.afterInputRestart]).
     */
    fun onRestartInput(
        field: FieldContext,
        trust: EditorTrust = EditorTrust.FULL,
        appProfile: AppProfile = AppProfile.default(null),
        textBeforeCursor: String?,
    ) {
        activeField = field
        activeTrust = trust
        activeAppProfile = appProfile
        val restarted = textInputState.afterInputRestart(textBeforeCursor)
        applyCapDecision(CapDecision.ClearOneShot)
        val capContext = if (activeTrust.contextRulesAllowed) textBeforeCursor else null
        val (capState, decision) = AutoCapitalization.evaluate(
            textInputState.autoCap, activeField, settings.textInput.autoCap, capContext,
            suppressionContext = autoCapSuppressionContext(capContext),
        )
        textInputState = restarted.copy(autoCap = capState)
        applyCapDecision(decision)
    }

    /** spec: status-bar.md SS13, "field finishes": modifiers reset, nav mode preserved. */
    fun onFinishInput() {
        typingState = TypingSessionState()
        modifierState = ModifierMachine.fullReset(modifierState, preserveNavModeLatch = true)
        expansion = ExpansionState.EMPTY
        // spec: keys-and-modifiers.md SS11, "state resets on start and finish of input".
        accidentalPressFilterState = AccidentalPressFilterState()
    }

    /**
     * A selection/cursor change that did not come from this pipeline's own last edit. spec:
     * text-input.md SS2 ("every cursor change that is not the one-character forward step caused
     * by its own last commit clears the deferred-space state, refreshes suggestions, ...").
     * Distinguishing "our own edit" from an external move is `:ime`'s job (it is the one that
     * knows whether it just called the `InputConnection`), so [KeyboardSession] only calls this
     * for a move it did not cause itself.
     */
    fun onExternalSelectionChange(textBeforeCursor: String?, selectionCollapsed: Boolean = true) {
        // Resyncing the keyboard's own record to what the editor just reported is always allowed:
        // that is the drift-recovery path itself (rebuild-from-scratch.md "The editor is not a
        // reliable narrator" point 1), not a guess a reduced [activeTrust] should suppress.
        textInputState = textInputState.afterExternalCursorMove(textBeforeCursor)
        // Sentence-end capitalisation, in contrast, needs surrounding context to be right rather
        // than merely present, so it follows [activeTrust] like every other context rule (point 2).
        val capContext = if (activeTrust.contextRulesAllowed) textBeforeCursor else null
        val (capState, decision) = AutoCapitalization.evaluate(
            textInputState.autoCap, activeField, settings.textInput.autoCap, capContext,
            suppressionContext = autoCapSuppressionContext(capContext),
        )
        textInputState = textInputState.copy(autoCap = capState)
        applyCapDecision(decision)
        // spec expansion-clipboard-pickers-launcher.md SS2.4: the lookup runs "after every selection
        // change that leaves a collapsed caret" and clears "when the selection stops being collapsed".
        expansion = SnippetExpansion.refresh(expansion, textBeforeCursor, settings.expansion, expansionGate(selectionCollapsed = selectionCollapsed))
    }

    // -----------------------------------------------------------------------------------------
    // Text expansion. spec: expansion-clipboard-pickers-launcher.md SS2. The engine is
    // `:core:actions`'; this class supplies the field, modifier and text facts it needs.
    // -----------------------------------------------------------------------------------------

    private fun expansionGate(selectionCollapsed: Boolean, editorConnected: Boolean = true): ExpansionGate = ExpansionGate(
        fieldReallyEditable = activeField.isReallyEditable,
        fieldRestricted = activeField.isRestricted,
        selectionCollapsed = selectionCollapsed,
        editorConnected = editorConnected,
    )

    /** spec SS2.6: "no modifier active in any form (Ctrl, Alt, Shift or Meta held, latched, one-shot, or reported by the event)". */
    private fun anyModifierActive(stroke: KeyStroke): Boolean =
        stroke.meta.shift || stroke.meta.ctrl || stroke.meta.alt ||
            modifierState.shift.value != ShiftValue.OFF || modifierState.shift.pressed || modifierState.shift.layerLatched ||
            modifierState.ctrl.latched || modifierState.ctrl.oneShot || modifierState.ctrl.pressed || modifierState.ctrl.physicallyPressed ||
            modifierState.alt.latched || modifierState.alt.oneShot || modifierState.alt.pressed || modifierState.alt.layerLatched

    /** spec SS2.4: the coalesced lookup `:ime` schedules 24 ms after a key release; answers the rows the popup should show. */
    fun refreshExpansion(textBeforeCursor: String?, selectionCollapsed: Boolean = true): List<SnippetMatch> {
        expansion = SnippetExpansion.refresh(expansion, textBeforeCursor, settings.expansion, expansionGate(selectionCollapsed, editorConnected = textBeforeCursor != null))
        return expansionPopupRows()
    }

    /** spec SS2.5, the floating popup's rows (empty in every other presentation). */
    fun expansionPopupRows(): List<SnippetMatch> =
        if (settings.expansion.presentation == SnippetPresentation.FLOATING_POPUP) SnippetExpansion.visibleRows(expansion, settings.expansion.presentation) else emptyList()

    val expansionHighlight: Int get() = expansion.highlight

    /** spec SS2.5, the suggestion bar presentation: "the first three matches replace the three suggestion slots". */
    private fun expansionBarRows(): List<SnippetMatch> =
        if (settings.expansion.presentation == SnippetPresentation.SUGGESTION_BAR) SnippetExpansion.visibleRows(expansion, settings.expansion.presentation) else emptyList()

    /** True while the strip's slots are showing expansion matches, so a slot tap commits a match instead of a suggestion. */
    val expansionOwnsStripSlots: Boolean get() = expansionBarRows().isNotEmpty()

    /** The bar's rows in match order, so `:ime` can map a tapped slot back to the match it shows. */
    fun expansionPopupRowsForBar(): List<SnippetMatch> = expansionBarRows()

    /** spec SS2.5: "A row tap commits that match with no trailing space." */
    fun onExpansionRowTapped(index: Int, editor: EditorSnapshot): PipelineResult {
        val result = SnippetExpansion.onRowTapped(expansion, index, editor.textBeforeCursor)
        expansion = result.state
        val commit = (result as? ExpansionKeyResult.Consumed)?.commit ?: return PipelineResult.CONSUMED_NO_OP
        return expansionCommitResult(commit.deleteCount, commit.text, editor)
    }

    /** spec SS2.6 "Commit mechanics": finish any composition, delete the token, commit the replacement as finished text, then reset the suggestion context. */
    private fun expansionCommitResult(deleteCount: Int, text: String, editor: EditorSnapshot): PipelineResult {
        val before = editor.textBeforeCursor.orEmpty()
        val after = before.dropLast(deleteCount.coerceAtMost(before.length)) + text
        textInputState = textInputState.afterExternalCursorMove(after)
        return PipelineResult(listOf(EditorOp.FinishComposing, EditorOp.ReplaceBeforeCursor(deleteCount, text)), consumed = true)
    }

    /**
     * spec SS2.4: "Immediately, before the key is acted on, when Space, Tab, Enter or the d-pad
     * center key goes down" (T11), and SS2.6's key table. Answers null when expansion leaves the
     * key alone.
     */
    private fun tryExpansion(stroke: KeyStroke, editor: EditorSnapshot): PipelineResult? {
        if (!settings.expansion.enabled || !SnippetExpansion.isExpansionKey(stroke.key) || stroke.repeatCount > 0) return null
        val gate = expansionGate(selectionCollapsed = !(editor.fullText?.hasSelection ?: false), editorConnected = editor.textBeforeCursor != null)
        val result = SnippetExpansion.onKeyDown(expansion, stroke.key, anyModifierActive(stroke), editor.textBeforeCursor, settings.expansion, gate)
        expansion = result.state
        return when (result) {
            is ExpansionKeyResult.NotConsumed -> null
            is ExpansionKeyResult.Consumed -> result.commit?.let { expansionCommitResult(it.deleteCount, it.text, editor) } ?: PipelineResult.CONSUMED_NO_OP
        }
    }

    // -----------------------------------------------------------------------------------------
    // Launcher keys. spec: expansion-clipboard-pickers-launcher.md SS6.2. The router and the
    // armed mode are `:core:actions`'; `:ime` runs what they decide.
    // -----------------------------------------------------------------------------------------

    val powerShortcutArmedAtMs: Long? get() = powerMode.armedAtMs

    /** spec SS6.2 B: the 5000 ms disarm, or any later check; restores nav mode if the arming suspended it. */
    fun onPowerShortcutTimeout(nowMs: Long) {
        val (next, effect) = PowerShortcutMode.onTimeout(powerMode, nowMs)
        powerMode = next
        if (effect.restoreNavMode) restoreNavModeLatch()
    }

    private fun suspendNavModeLatch() {
        modifierState = modifierState.copy(ctrl = modifierState.ctrl.copy(latched = false, latchFromNavMode = false))
    }

    private fun restoreNavModeLatch() {
        modifierState = modifierState.copy(ctrl = modifierState.ctrl.copy(latched = true, latchFromNavMode = true))
    }

    /** spec SS6.2 B: Sym down (repeat 0) with no editable field arms or disarms the mode. */
    private fun powerModeOnSymDown(stroke: KeyStroke): PipelineResult? {
        if (activeField.isReallyEditable || stroke.edge != KeyEdge.DOWN || stroke.repeatCount > 0) return null
        val (next, effect) = PowerShortcutMode.onSymDown(powerMode, stroke.timeMs, settings.launcherKeys.symShortcutsEnabled, navModeActive = modifierState.ctrl.latchFromNavMode)
        powerMode = next
        if (effect.suspendNavMode) suspendNavModeLatch()
        if (effect.restoreNavMode) restoreNavModeLatch()
        if (!effect.consumed) return null
        return PipelineResult(emptyList(), consumed = true, powerModeArmedAtMs = if (effect.scheduleToast) stroke.timeMs else null)
    }

    /** spec SS6.2 A and B, with no editable field: the armed mode's key, or a bare key on the home screen. */
    private fun launcherOutsideTextField(stroke: KeyStroke): PipelineResult? {
        if (stroke.repeatCount > 0) return null
        val ctrlLatch = modifierState.ctrl.latched || modifierState.ctrl.latchFromNavMode
        val (next, effect) = PowerShortcutMode.onKeyDown(powerMode, stroke.key, stroke.timeMs)
        powerMode = next
        if (effect.restoreNavMode) restoreNavModeLatch()
        val fromArmedMode = effect.fireKey != null
        val decision = LauncherKeyRouter.outsideTextField(stroke.key, settings.launcherShortcuts, settings.launcherKeys, ctrlLatch, foregroundIsHome, fromArmedMode, symPhysicallyHeld = stroke.meta.sym)
        if (decision == LauncherKeyDecision.FallThrough) return if (effect.consumed) PipelineResult.CONSUMED_NO_OP else null
        return PipelineResult(emptyList(), consumed = true, launcherKey = decision)
    }

    /**
     * spec SS6.2 C: in a text field, an assigned key with Sym held or a Sym tap pending fires
     * and the chord counts as used (layers-sym-alt.md SS5.3 step 2, after the edit shortcuts of
     * step 1 and ahead of the chord symbol of step 3).
     */
    private fun launcherInTextField(stroke: KeyStroke): PipelineResult? {
        val symHeldOrPending = stroke.meta.sym || (modifierState.sym.togglePending && !modifierState.sym.chordUsed)
        if (!symHeldOrPending) return null
        if (settings.modifier.symEditShortcutsEnabled && !stroke.meta.alt && stroke.key in SYM_EDIT_SHORTCUT_KEYS) return null
        val ctrlLatch = modifierState.ctrl.latched || modifierState.ctrl.latchFromNavMode
        val decision = LauncherKeyRouter.inTextField(stroke.key, settings.launcherShortcuts, settings.launcherKeys, symHeldOrPending, stroke.isInitialPress, ctrlLatch)
        if (decision !is LauncherKeyDecision.Run) return null
        modifierState = ModifierMachine.symChordUsed(modifierState)
        return PipelineResult(emptyList(), consumed = true, launcherKey = decision)
    }

    /**
     * spec: per-app-behavior.md SS3.5 step 4a, SS3.8: "Sym is being held (a Sym chord is pending)
     * and the app's extra shortcut is `sym_enter`: the Sym chord is marked as used ... and the
     * configured send method fires." Checked after [launcherInTextField] (E6: a QuickLauncher
     * binding on Sym+Enter wins) and only for Enter, so every other Sym chord consumer keeps
     * first refusal.
     */
    private fun trySymEnterSend(stroke: KeyStroke): PipelineResult? {
        if (stroke.key != ENTER_KEY || !stroke.isInitialPress) return null
        val symHeldOrPending = stroke.meta.sym || (modifierState.sym.togglePending && !modifierState.sym.chordUsed)
        if (!symHeldOrPending || activeAppProfile.extraSendShortcut != ExtraSendShortcut.SYM_ENTER) return null
        modifierState = ModifierMachine.symChordUsed(modifierState)
        return toPipelineResult(emptyList(), EnterDecision.decideSymEnterSend(activeAppProfile, activeField))
    }

    private val SYM_EDIT_SHORTCUT_KEYS = setOf(KeyId.Letter('C'), KeyId.Letter('V'), KeyId.Letter('X'), KeyId.Letter('A'))

    // -----------------------------------------------------------------------------------------
    // One key event. spec: docs/plans/rebuild-from-scratch.md "Keypress data flow".
    // -----------------------------------------------------------------------------------------

    /**
     * Whether [stroke] can produce an op that needs the whole document: a selection or word
     * motion (text-input.md SS10, all Ctrl combos or navigation keys) or a Backspace that must know
     * whether a selection exists (SS8). Every plain letter, Space, Enter, modifier press and key-up
     * is answered from the 240-character window alone (SS19 "unify"), so the O(document)
     * extracted-text request is not paid on every keystroke of a long note.
     */
    fun needsWholeDocument(stroke: KeyStroke): Boolean {
        if (stroke.edge != KeyEdge.DOWN) return false
        return when (val key = stroke.key) {
            is KeyId.Modifier -> false
            is KeyId.Control -> key.key != ControlKey.SPACE && key.key != ControlKey.ENTER || modifierState.isCtrlActive(stroke.meta.ctrl)
            // A letter under a still-held Sym is a chord (keys-and-modifiers.md, Sym+A selects
            // all), not a letter; the same fields LayerResolver reads to decide that.
            is KeyId.Letter, is KeyId.Digit, is KeyId.Punctuation ->
                modifierState.isCtrlActive(stroke.meta.ctrl) || (modifierState.sym.togglePending && !modifierState.sym.chordUsed)
        }
    }

    /**
     * spec: keys-and-modifiers.md SS1.3 steps 1-2, SS1.4 steps 1 and 3: the accidental-press
     * filter, then the bounce filter, each on both key-down and key-up. Returns a consumed no-op
     * the moment either filter rejects the stroke, or null when both accept it and the rest of
     * [onKeyStroke] should run as usual.
     */
    private fun applyKeyFilters(stroke: KeyStroke): PipelineResult? {
        val (afterAccidental, accidentalVerdict) = if (stroke.edge == KeyEdge.DOWN) {
            AccidentalPressFilter.onKeyDown(accidentalPressFilterState, stroke, settings.overlappingKeys)
        } else {
            AccidentalPressFilter.onKeyUp(accidentalPressFilterState, stroke)
        }
        accidentalPressFilterState = afterAccidental
        if (accidentalVerdict is FilterVerdict.Reject) return PipelineResult.CONSUMED_NO_OP

        val (afterBounce, bounceVerdict) = if (stroke.edge == KeyEdge.DOWN) {
            BounceFilter.onKeyDown(bounceFilterState, stroke, settings.bounceKeys)
        } else {
            BounceFilter.onKeyUp(bounceFilterState, stroke)
        }
        bounceFilterState = afterBounce
        return if (bounceVerdict is FilterVerdict.Reject) PipelineResult.CONSUMED_NO_OP else null
    }

    fun onKeyStroke(stroke: KeyStroke, editor: EditorSnapshot): PipelineResult {
        // spec: keys-and-modifiers.md SS1.3 steps 1-2: the accidental-press filter, then the
        // bounce filter, "on the raw event, before anything else" -- ahead of every other stage,
        // modifier keys included (SS10's own category table lists Shift/Ctrl/Alt/Sym).
        applyKeyFilters(stroke)?.let { return it }

        // spec: trackpad-caret-nav.md SS5.2 ("Entering and leaving"), keys-and-modifiers.md SS15
        // point 3: with no field, Ctrl's own double-tap-to-latch dance belongs to nav mode, not the
        // plain modifier machine, so this is checked ahead of the generic modifier branch below.
        if (stroke.key == CTRL_KEY && !activeField.isReallyEditable && navModeCtrlIsOurConcern()) {
            return onNavModeCtrlStroke(stroke)
        }

        if (stroke.key is KeyId.Modifier) {
            val armed = if (stroke.key == SYM_KEY) powerModeOnSymDown(stroke) else null
            val action = dispatchModifier(stroke, editor)
            val result = applyAction(action, shiftHeld = stroke.meta.shift, altActive = modifierState.isAltActive(stroke.meta.alt), editor)
            return if (armed != null) result.copy(consumed = true, powerModeArmedAtMs = armed.powerModeArmedAtMs) else result
        }

        if (stroke.edge == KeyEdge.UP) {
            // spec: keys-and-modifiers.md SS15 point 3, key-up: "Any key-up while nav mode is active is consumed."
            if (!activeField.isReallyEditable && modifierState.ctrl.latchFromNavMode) return PipelineResult.CONSUMED_NO_OP
            val resolution = LayerResolver.resolveKeyUp(modifierState, typingState, stroke)
            modifierState = resolution.state
            typingState = resolution.typing
            return applyAction(resolution.action, shiftHeld = stroke.meta.shift, altActive = modifierState.isAltActive(stroke.meta.alt), editor)
        }

        // spec: keys-and-modifiers.md SS5.2, "for any other key with repeat count 0"; dispatch
        // convention documented on ModifierMachine.onOtherKeyDown.
        modifierState = ModifierMachine.onOtherKeyDown(modifierState, stroke)

        // spec: trackpad-caret-nav.md SS5.5, keys-and-modifiers.md SS15 point 3: with no field and
        // nav mode active, the Fn Layer map owns the key ahead of the launcher paths below (which
        // all require "without a Ctrl latch"), so this runs first.
        if (!activeField.isReallyEditable && modifierState.ctrl.latchFromNavMode) {
            onNavModeMappedKeyDown(stroke, editor)?.let { return it }
        }

        // spec expansion-clipboard-pickers-launcher.md SS6.2 A/B: with no editable field the
        // launcher paths own the 29 keys before anything else can pass them to the app.
        if (!activeField.isReallyEditable) launcherOutsideTextField(stroke)?.let { return it }
        // spec SS6.2 C: Sym plus an assigned key in a text field, ahead of the Sym chord symbol.
        if (activeField.isReallyEditable) launcherInTextField(stroke)?.let { return it }
        // spec per-app-behavior.md SS3.5 step 4a, SS3.8: Sym+Enter as an extra send, after the
        // launcher-shortcut check above (E6) and ahead of the Sym chord symbol lookup below.
        if (activeField.isReallyEditable) trySymEnterSend(stroke)?.let { return it }
        // spec SS2.4: expansion evaluates Space, Tab, Enter and the d-pad center "immediately,
        // before the key is acted on", so it must run before `:core:keys` turns Space into a commit.
        if (activeField.isReallyEditable) tryExpansion(stroke, editor)?.let { return it }

        // *** The milestone-2 sequencing rule (docs/plans/rebuild-from-scratch.md, "What
        // milestone 2 established"): a letter's case is resolved by `:core:keys` before
        // `:core:text` ever sees it, so a deferred space firing on this same keystroke must be
        // known BEFORE `:core:keys` resolves the stroke, not after. See [withDeferredSpaceForcedCase]. ***
        val effectiveStroke = withDeferredSpaceForcedCase(stroke, editor)

        val isNumericField = activeField.kind == FieldKind.NUMBER_OR_PHONE
        val context = LayerResolver.Context(
            hasEditableField = activeField.isReallyEditable,
            isNumericField = isNumericField,
            hasSelection = editor.fullText?.hasSelection ?: false,
            hasTextBeforeCaret = editor.textBeforeCursor?.isNotEmpty() ?: true,
            canSwitchLayout = anotherSubtypeAvailable,
        )
        // Read before resolution, which spends a one-shot Alt on this very key.
        val altLayerStroke = modifierState.isAltActive(effectiveStroke.meta.alt)
        val resolution = LayerResolver.resolveKeyDown(
            modifierState, typingState, effectiveStroke, layout, settings.modifier, settings.resolver, context,
        )
        modifierState = resolution.state
        typingState = resolution.typing
        // `:core:keys` now answers an ordinary Space, Enter or Backspace with the real commit/edit
        // action itself (text-input.md SS5-SS8; see LayerResolver.withBaselineControlAction), so
        // this adapter has nothing left to decide here: whatever LayerResolver.resolveKeyDown
        // returned is exactly what `:core:text` (or the app, for a genuine PassThrough) should see.
        // The one exception is [redirectEnterForPerAppBehavior]'s own narrow override, see its KDoc.
        val ctrlActive = modifierState.isCtrlActive(effectiveStroke.meta.ctrl)
        val shiftActive = effectiveStroke.meta.shift || modifierState.shift.layerLatched
        val result = applyAction(
            redirectEnterForPerAppBehavior(effectiveStroke, resolution.action),
            shiftHeld = effectiveStroke.meta.shift,
            altActive = modifierState.isAltActive(effectiveStroke.meta.alt),
            editor,
            ctrlActive = ctrlActive,
            shiftActive = shiftActive,
            isRepeat = effectiveStroke.repeatCount > 0,
        )
        // A key the app will delete or paste with is as much an edit of the field as one this
        // keyboard made itself (dictation's c440844 invariant, see PipelineResult.appMayEditField).
        val appEdits = !result.consumed && AppliedEditAccounting.appEditsWithPassThrough(effectiveStroke.key, ctrlActive)
        return result.copy(appMayEditField = result.appMayEditField || appEdits, altLayerStroke = altLayerStroke)
    }

    // -----------------------------------------------------------------------------------------
    // Nav mode with no field. spec: trackpad-caret-nav.md SS5.2 (entry/exit), SS5.5 (the Fn Layer
    // map). `:core:pointer`'s NavModeEntry/NavModeMap own the decision; this only threads the
    // keyboard's own state and the loaded Fn Layer map ([layout.ctrlMappings]) into them.
    // -----------------------------------------------------------------------------------------

    /** spec: keys-and-modifiers.md SS15 point 3's own gate: nav mode owns a no-field Ctrl stroke only "when `nav_mode_enabled` (default true) or nav mode is active". */
    private fun navModeCtrlIsOurConcern(): Boolean = settings.navModeEnabled || modifierState.ctrl.latchFromNavMode

    /** spec SS5.2: the no-field Ctrl double-tap dance (first tap, latch, un-latch); every event is consumed. */
    private fun onNavModeCtrlStroke(stroke: KeyStroke): PipelineResult {
        val result = if (stroke.edge == KeyEdge.DOWN) {
            NavModeEntry.onCtrlDown(modifierState, stroke, settings.modifier)
        } else {
            NavModeEntry.onCtrlUp(modifierState, stroke, settings.modifier)
        }
        modifierState = result.state
        return PipelineResult(
            emptyList(),
            consumed = result.consumed,
            navModeTransition = result.transition.takeIf { it != NavModeTransition.NONE },
        )
    }

    /**
     * spec SS5.5, "with no field" column: Enter is DPAD_CENTER (not part of the 26-letter map);
     * any other key is looked up in [layout.ctrlMappings], the same table the in-field Ctrl
     * mapping (`LayerResolver.resolveCtrlActive`) already reads, so a mapping means the same thing
     * on both surfaces (keys-and-modifiers.md SS12). Null means the map does not claim the key, so
     * the caller falls through to the launcher-shortcut steps SS15 lists next.
     *
     * By the time a real stroke reaches here `:ime` has already required a live `InputConnection`
     * to call this pipeline at all (see [brobata.physiboard.ime.KeyboardSession.processKeyStroke]),
     * so [hasInputConnection] is always true; [NavModeMap]'s own "no connection" branches exist for
     * its unit tests and for callers this milestone does not have (a command with no field and no
     * connection at all).
     *
     * A `native_ctrl` mapping resolves to [Action.ForwardAsCtrlCombo]; unlike the in-field
     * physical-combo case (where the real event already carries Ctrl's meta bit and
     * [applyAction]'s plain [PipelineResult.NOT_CONSUMED] is correct), nav mode's Ctrl here is a
     * latch, not a physical hold, so the raw stroke never carries that bit. Handled below by
     * setting [PipelineResult.forwardAsCtrlCombo] instead of routing through [applyAction], so
     * `:ime` synthesizes the real combo rather than letting a bare letter through.
     */
    private fun onNavModeMappedKeyDown(stroke: KeyStroke, editor: EditorSnapshot): PipelineResult? {
        val decision = if (stroke.key == ENTER_KEY) {
            NavModeMap.resolveEnter(hasInputConnection = true)
        } else {
            NavModeMap.resolveLetterKeyDown(stroke.key, layout.ctrlMappings, hasInputConnection = true)
        }
        if (!decision.consumed) return null
        val action = decision.action
        // A `native_ctrl` mapping's Ctrl is nav mode's own latch, not a physical hold, so the raw
        // stroke carries no Ctrl meta bit for the app to see: [applyAction]'s ordinary
        // [Action.ForwardAsCtrlCombo] handling (`PipelineResult.NOT_CONSUMED`, correct only when
        // the physical event already is the combo) would deliver a bare letter instead. This
        // consumes the key itself and asks `:ime` to synthesize the real combo instead.
        if (action is Action.ForwardAsCtrlCombo) return PipelineResult.CONSUMED_NO_OP.copy(forwardAsCtrlCombo = action.key)
        return applyAction(action, shiftHeld = stroke.meta.shift, altActive = false, editor)
    }

    /**
     * spec: per-app-behavior.md SS3.5 step 4: a per-app Enter behaviour must be consulted before
     * Ctrl's own "no mapping: pass to app" answer (keys-and-modifiers.md SS7.3, this is
     * [LayerResolver.resolveCtrlActive]'s [Action.PassThrough]/[Action.ForwardAsCtrlCombo] for an
     * unmapped Enter) gets the last word, since SS3.7's Ctrl+Enter-sends behaviours must reach
     * `:core:text`'s decision instead of leaving as a raw key event Android delivers untouched
     * (D4: Fn arrives as a held Ctrl on the Titan, so a send-on-Enter app's daily Fn+Enter would
     * otherwise never be recognised as a send at all).
     *
     * Left alone whenever [activeAppProfile] has no wanted behaviour for the current app
     * ([EnterBehavior.APP_DEFAULT]), so every app with no per-app opinion keeps exactly the
     * behaviour `:core:keys`'s own `LayerResolverTest` already tests ("Ctrl held with no mapping
     * still leaves Enter passed through, not turned into a newline"): per-app-behavior.md's own
     * generic step (SS3.5 step 4e) never mentions Ctrl at all, so there is nothing for this
     * redirect to do for an unconfigured app.
     */
    private fun redirectEnterForPerAppBehavior(stroke: KeyStroke, action: Action): Action {
        if (stroke.key != ENTER_KEY || activeAppProfile.enterBehavior == EnterBehavior.APP_DEFAULT) return action
        val bypassedCoreText = action == Action.PassThrough || action is Action.ForwardAsCtrlCombo
        return if (bypassedCoreText) Action.Edit(EditEffect.NEWLINE) else action
    }

    /**
     * spec: text-input.md SS5.5 + docs/plans/rebuild-from-scratch.md's milestone-2 sequencing
     * rule. When `:core:text` is still holding a deferred-space debt (`space_after_punctuation`,
     * text-input.md SS6.6) and the incoming key is a physical letter, the letter that pays that
     * debt and the space it pays with commit in the *same* keystroke; there is no separate Space
     * keystroke in between during which the ordinary "arm now, consume next" auto-cap protocol
     * could run. So this asks `:core:text`'s own [AutoCapitalization.evaluate] here, against the
     * text as it will read once the withheld space lands, and if it would arm a one-shot, marks
     * *this* stroke's `meta.shift` true before `:core:keys` ever sees it. `:core:keys` still
     * makes the actual case decision ([ModifierState.shiftForcesUppercase] reads `meta.shift` as
     * one of its own three disjuncts); this function only supplies the fact `:core:keys` cannot
     * otherwise have. A synthetic `meta.shift` is used instead of arming a real Shift one-shot in
     * [modifierState] so nothing needs cleaning up if the stroke turns out not to resolve through
     * the plain-letter path (Ctrl/Alt/Sym-active branches never consume a one-shot, so seeding one
     * there would leak into the next keystroke); the plain-letter path is also the only one whose
     * output depends on case, so a physical letter key is the only key this ever touches. Residual
     * edge case, accepted rather than resolved: if Ctrl is also active on that same letter, this
     * synthetic `meta.shift` also feeds `resolveCtrlActive`'s own Shift-aware branches (selection
     * extension). Unreachable with the shipped defaults (`space_after_punctuation` ships empty, so
     * no debt is ever owed until a settings surface exists to populate it), and deciding it
     * properly would mean `:ime` re-implementing which branch a stroke resolves through, which is
     * exactly what this function is designed not to do.
     */
    private fun withDeferredSpaceForcedCase(stroke: KeyStroke, editor: EditorSnapshot): KeyStroke {
        if (stroke.key !is KeyId.Letter) return stroke
        if (!textInputState.deferredSpace.owed) return stroke
        val projected = editor.textBeforeCursor?.plus(" ") ?: return stroke
        val (_, decision) = AutoCapitalization.evaluate(textInputState.autoCap, activeField, settings.textInput.autoCap, projected)
        return if (decision == CapDecision.ArmOneShot) stroke.copy(meta = stroke.meta.copy(shift = true)) else stroke
    }

    // -----------------------------------------------------------------------------------------
    // Long press. spec: keys-and-modifiers.md SS8.2, SS8.3. `:core:keys` decides eligibility and
    // the replacement; `:ime` only owns the real clock that decides when to ask.
    // -----------------------------------------------------------------------------------------

    fun checkLongPressTick(nowMs: Long, editor: EditorSnapshot): PipelineResult? {
        val resolution = LayerResolver.resolveLongPressTick(modifierState, typingState, nowMs, layout) ?: return null
        modifierState = resolution.state
        typingState = resolution.typing
        val result = applyAction(resolution.action, shiftHeld = false, altActive = false, editor)
        // A long press in Alt mode types the Alt-layer character in place of the letter: the same
        // delivery as Alt+key, or a "." held out of M could match a held Alt+M Chrome still remembers.
        return if (layout.longPress.mode == LongPressMode.ALT) result.copy(altLayerStroke = true) else result
    }

    // -----------------------------------------------------------------------------------------
    // Suggestions. spec: autocorrect-suggestions.md SS3; status-bar.md SS5.1 (slot mapping is the
    // themed strip's job, out of scope here, see KeyboardSession/CandidatesStripView).
    // -----------------------------------------------------------------------------------------

    /**
     * spec: status-bar.md SS5.3, autocorrect-suggestions.md SS5: a word the "eye" button hid,
     * scoped to the word being typed ("removes the word from the current list"). Keyed to
     * [TextInputState.currentWord]'s own text so a fresh word never inherits a hide from the one
     * before it; [hideSuggestion] is the only writer.
     *
     * SPEC GAP: autocorrect-suggestions.md SS5 also has hiding "forget[] the bigram" for a
     * next-word suggestion, and describes back-filling from "starter words". Neither a next-word
     * predictor nor a starter-word list exists anywhere in 3.0 yet (that whole subsystem is out of
     * this task's scope); this hides the word from the current-word completion list only, and the
     * "back-fill" happens for free because [suggestions] asks the ranker for one extra candidate
     * per hidden word and re-takes the top three.
     */
    private var hiddenSuggestionsWord: String = ""
    private val hiddenSuggestions = mutableSetOf<String>()

    fun suggestions(): List<RankedSuggestion> {
        if (!activeField.suggestionsAllowed) return emptyList()
        if (!settings.textInput.autocorrect.suggestionsEnabled) return emptyList()
        val word = textInputState.currentWord.word
        if (word.isEmpty()) return emptyList()
        if (!word.equals(hiddenSuggestionsWord, ignoreCase = true)) {
            hiddenSuggestionsWord = word
            hiddenSuggestions.clear()
        }
        if (hiddenSuggestions.isEmpty()) {
            return SuggestionRanking.suggest(word, resources.dictionaries, resources.userWords, settings.textInput.rankingOptions)
        }
        val expanded = SuggestionRanking.suggest(word, resources.dictionaries, resources.userWords, settings.textInput.rankingOptions, limit = 3 + hiddenSuggestions.size)
        return expanded.filterNot { candidate -> hiddenSuggestions.any { it.equals(candidate.word, ignoreCase = true) } }.take(3)
    }

    /**
     * spec: status-bar.md SS5.3: the suggestion action mode's "eye" button. See [hiddenSuggestions]'
     * own KDoc for what "hide" means in 3.0 today. spec autocorrect-suggestions.md SS5: "for a
     * next-word suggestion it also forgets the bigram", which this reaches through [onBigramForgotten]
     * exactly the way a learn reaches [onBigramLearned].
     */
    fun hideSuggestion(word: String) {
        if (textInputState.currentWord.word.isEmpty()) {
            val locale = resources.dictionaries.firstOrNull()?.language?.value ?: ImeSettings.DEFAULT_SUBTYPE_LOCALE
            ngramStore = ngramStore.forget(locale, nextWordPrefixKey, word)
            onBigramForgotten(locale, nextWordPrefixKey, word)
            return
        }
        if (!word.equals(hiddenSuggestionsWord, ignoreCase = true)) {
            hiddenSuggestionsWord = word
            hiddenSuggestions.clear()
        }
        hiddenSuggestions.add(word)
    }

    /** spec: SS4, SS5's next-word hide; a no-op until `:ime` sets it. */
    var onBigramForgotten: (locale: String, prefix: String, nextWord: String) -> Unit = { _, _, _ -> }

    /** spec: autocorrect-suggestions.md SS5, SS6.11. A strip tap, kept out of the typing path. */
    fun onAcceptSuggestion(word: String, editor: EditorSnapshot): PipelineResult {
        val result = TextInputPipeline.handle(TextInputRequest.AcceptSuggestion(word), activeField, settings.textInput, resources, textInputState, editor)
        textInputState = result.state
        result.capDecision?.let(::applyCapDecision)
        return toPipelineResult(result.ops, result.enterDelivery, result.autocorrectDebug)
    }

    // -----------------------------------------------------------------------------------------
    // The strip. spec: status-bar.md SS1 (the refresh snapshot), SS12 (the per-app dip), SS13
    // (window hidden). `:core:strip` owns every rule; this class only supplies the modifier,
    // field and suggestion facts it already holds, and keeps the dip's own small state.
    // -----------------------------------------------------------------------------------------

    private var dip = DipState()

    /** The package the current field belongs to, or null when no field is open (SS3.5, "No package (no field) counts as not listed"). */
    private val currentPackageName: String? get() = activeAppProfile.packageName.ifEmpty { null }

    /**
     * spec: status-bar.md SS1. Everything the strip needs that this pipeline knows: the ranked
     * suggestions, the field's own suggestion permission, the modifier facts SS7 lights LEDs from
     * (never a plain physical hold, SS17), nav mode (SS3.4), and the app. What only `:ime` knows
     * (clipboard count, dictation, the loaded dictionary, the subtype) arrives as parameters.
     *
     * spec: autocorrect-suggestions.md SS6.2's live-typing clause: [AddWordCandidate.forCurrentWord]
     * offers the word being typed once it is unknown; the two clauses that extend the candidate
     * past an automatic correction or an undo are not carried yet (`:core:text`'s boundary/undo
     * results do not thread a candidate into [TextInputState] today). SPEC GAP: "no dictionary is
     * installed for the current language (checked by the language code of the current subtype)"
     * (SS5.2) needs the subtype module; [dictionaryInstalled] is the caller's answer for the one
     * language this milestone loads, reused here as "the primary dictionary is loaded" (SS6.2).
     */
    fun stripModel(clipboardCount: Int, dictationActive: Boolean, dictionaryInstalled: Boolean, subtypeLocale: String?, clipboardOverlayOpen: Boolean = false): StripModel {
        val inputs = StripInputs(
            packageName = currentPackageName,
            // spec: autocorrect-suggestions.md SS4: once the current word is empty, the strip
            // shows next-word predictions (learned bigrams, then starter words) instead of going
            // blank; typing any letter makes the current word non-empty again, which already
            // switches back to ordinary current-word suggestions (SS4's own rule).
            suggestions = if (textInputState.currentWord.word.isEmpty()) nextWordSuggestions() else suggestions().map { it.word },
            addWordCandidate = if (activeField.suggestionsAllowed && settings.textInput.autocorrect.suggestionsEnabled) {
                AddWordCandidate.forCurrentWord(
                    word = textInputState.currentWord.word,
                    primaryDictionaryLoaded = dictionaryInstalled,
                    dictionaries = resources.dictionaries,
                    userWords = resources.userWords,
                )
            } else {
                null
            },
            // spec expansion-clipboard-pickers-launcher.md SS2.5, the suggestion bar presentation.
            expansionSuggestions = expansionBarRows().map { it.label },
            clipboardOverlayOpen = clipboardOverlayOpen,
            suggestionsEnabled = settings.textInput.autocorrect.suggestionsEnabled,
            // The strip's own footprint also steps aside where the app said it wants no
            // suggestions: Teams' message box and a web terminal both declare it, and both drew
            // themselves under the strip or left it empty (2026-09-28). Autocorrect is untouched
            // by this; it follows `suggestionsAllowed`, which this deliberately does not change.
            fieldAllowsSuggestions = activeField.suggestionsAllowed && !activeField.appDisablesSuggestions,
            fieldDrawsUnderStrip = fieldDrawsUnderStrip,
            dictionaryInstalled = dictionaryInstalled,
            modifiers = ModifierIndicatorInput(
                capsLockOn = modifierState.shift.value == ShiftValue.CAPS,
                shiftOneShotArmed = modifierState.shift.value == ShiftValue.ONE_SHOT,
                ctrlLatched = modifierState.ctrl.latched,
                ctrlOneShotArmed = modifierState.ctrl.oneShot,
                altLatched = modifierState.alt.latched,
                altOneShotArmed = modifierState.alt.oneShot,
                symPage = modifierState.sym.currentPageNumber,
            ),
            navModeLatched = modifierState.ctrl.latchFromNavMode,
            clipboardCount = clipboardCount,
            dictationActive = dictationActive,
            subtypeLocale = subtypeLocale,
        )
        return StripModel.build(inputs, settings.statusBar)
    }

    /**
     * spec: status-bar.md SS6.1: the clipboard and microphone buttons release "a latched Shift or
     * Alt layer" before acting. Only the layer latches go; a one-shot, caps lock, Ctrl and a
     * physically held key are not layers and are left alone.
     */
    fun releaseLatchedLayersForStripButton() {
        modifierState = modifierState.copy(
            shift = modifierState.shift.copy(layerLatched = false),
            alt = modifierState.alt.copy(latched = false, layerLatched = false),
        )
    }

    /**
     * spec: status-bar.md SS12.2, one refused show request for the current field's app at
     * [nowMs]. [StripDip] decides; this only remembers the outcome and names the app.
     */
    fun onShowRequestRefused(nowMs: Long, stripRendered: Boolean, configurationChange: Boolean): DipDecision {
        val decision = StripDip.onShowRefused(dip, nowMs, currentPackageName, settings.statusBar.dipApps, stripRendered, configurationChange)
        dip = decision.state
        return decision
    }

    /** spec SS12.2 step 3: the hold's timer fired; answers the re-show once, never twice. */
    /** True while a dip is still settling, so `:ime` can retry a re-show that landed a moment early. */
    fun dipIsInFlight(nowMs: Long): Boolean = StripDip.isInFlight(dip, nowMs)

    fun onDipHoldElapsed(nowMs: Long): List<DipEffect> {
        val (next, effects) = StripDip.onHoldElapsed(dip, nowMs)
        dip = next
        return effects
    }

    /** spec SS12.2: while the hold is on, "every other request to show the candidates view is refused, including PhysiBoard's own re-show". */
    fun refusesCandidatesShow(nowMs: Long): Boolean = StripDip.refusesShowRequest(dip, nowMs)

    /** spec SS12.2, SS17: while the dip is in flight a real hide is indistinguishable from the blink and is treated as the blink. */
    fun isDipInFlight(nowMs: Long): Boolean = StripDip.isInFlight(dip, nowMs)

    /**
     * spec: status-bar.md SS13, "When Android hides the window and no dip is in flight...
     * modifier state is reset (nav mode preserved), and the suggestion context is reset." Returns
     * false, having touched nothing, while a dip is in flight (SS12.2: "the field, the modifiers
     * and the suggestion context survive the blink"). The suggestion context reset is
     * [TextInputState.afterExternalCursorMove] with no text: the tracked word and every
     * "text right before the cursor as this pipeline left it" fact go; auto-cap's field-level
     * state stays because the field itself did not change.
     */
    /**
     * spec: status-bar.md SS13 ("When the window is shown again...") read with text-input.md
     * line 463 (auto-cap is re-evaluated whenever the field's state is re-established). Android
     * can deliver the previous field's window-hidden event after the new field's start, and
     * [onWindowHidden] resets the modifiers, which threw away the start-of-text capital armed by
     * [onStartInput] (Messages on the Titan, 2026-09-25: the first letter of every message came
     * out lower-case). Re-deciding from the editor's text is idempotent: it arms only where the
     * rules say, and clears only a one-shot auto-cap itself armed.
     */
    fun onWindowShown(textBeforeCursor: String?) {
        val capContext = if (activeTrust.contextRulesAllowed) textBeforeCursor else null
        val (capState, decision) = AutoCapitalization.evaluate(
            textInputState.autoCap, activeField, settings.textInput.autoCap, capContext,
            suppressionContext = autoCapSuppressionContext(capContext),
        )
        textInputState = textInputState.copy(autoCap = capState)
        applyCapDecision(decision)
    }

    fun onWindowHidden(nowMs: Long): Boolean {
        if (StripDip.skipsWindowHidden(dip, nowMs)) return false
        typingState = TypingSessionState()
        modifierState = ModifierMachine.fullReset(modifierState, preserveNavModeLatch = true)
        textInputState = textInputState.afterExternalCursorMove(null)
        expansion = ExpansionState.EMPTY
        powerMode = PowerShortcutState.IDLE
        return true
    }

    // -----------------------------------------------------------------------------------------
    // Shared plumbing.
    // -----------------------------------------------------------------------------------------

    /** spec SS7.5, the Alt+Shift row: "(either order, repeat 0, editable field)" plus the another-subtype fact. */
    private val chordCanSwitchLayout: Boolean get() = anotherSubtypeAvailable && activeField.isReallyEditable

    /**
     * spec: text-input.md SS9.3, "if the user taps Shift while auto-cap has it armed (turning it
     * off), the keyboard records the current cursor context ... as suppressed". [ModifierMachine]
     * decides the Shift transition itself; this only notices the one transition auto-cap cares
     * about (a one-shot *it* armed going to OFF on a plain Shift down, not a double tap promoting
     * to Caps Lock) and records the suppression before returning the transition unchanged.
     */
    private fun shiftDownWithAutoCapDisarm(stroke: KeyStroke, editor: EditorSnapshot): ModifierMachine.Result {
        val ownedByAutoCap = modifierState.shift.value == ShiftValue.ONE_SHOT &&
            textInputState.autoCap.armSource == ShiftArmSource.AUTO_CAP
        val result = ModifierMachine.shiftDown(modifierState, stroke, settings.modifier, canSwitchLayout = chordCanSwitchLayout)
        if (ownedByAutoCap && result.state.shift.value == ShiftValue.OFF) {
            val context = autoCapSuppressionContext(editor.textBeforeCursor)
            textInputState = textInputState.copy(
                autoCap = if (context != null) {
                    textInputState.autoCap.onUserDisarmed(context)
                } else {
                    // Field unreadable: nothing to key the suppression on, but the user still
                    // disarmed it, so at least stop crediting auto-cap with an armed one-shot.
                    textInputState.autoCap.consumedUnconditionally()
                },
            )
        }
        return result
    }

    private fun dispatchModifier(stroke: KeyStroke, editor: EditorSnapshot): Action {
        val key = (stroke.key as KeyId.Modifier).key
        val down = stroke.edge == KeyEdge.DOWN
        val result = when (key) {
            // spec SS7.5: the Alt+Shift chord needs an editable field and another subtype.
            ModifierKey.SHIFT -> if (down) shiftDownWithAutoCapDisarm(stroke, editor) else ModifierMachine.shiftUp(modifierState, stroke, settings.modifier)
            ModifierKey.CTRL -> if (down) ModifierMachine.ctrlDown(modifierState, stroke, settings.modifier) else ModifierMachine.ctrlUp(modifierState, stroke, settings.modifier)
            ModifierKey.ALT -> if (down) ModifierMachine.altDown(modifierState, stroke, settings.modifier, canSwitchLayout = chordCanSwitchLayout) else ModifierMachine.altUp(modifierState, stroke, settings.modifier)
            ModifierKey.SYM -> if (down) {
                ModifierMachine.symDown(modifierState, stroke, settings.modifier, hasEditableField = activeField.isReallyEditable)
            } else {
                ModifierMachine.symUp(modifierState, stroke, hasEditableField = activeField.isReallyEditable, pages = layout.symPagesConfig)
            }
            ModifierKey.FN -> if (down) ModifierMachine.fnKeyDown(modifierState, stroke, settings.modifier) else ModifierMachine.fnKeyUp(modifierState, stroke, settings.modifier)
        }
        modifierState = result.state
        return result.action
    }

    /**
     * spec: keys-and-modifiers.md SS13 ("every modifier change refreshes a status snapshot") for
     * [Action.StateOnly]/[Action.Ignored]; SS14 for [Action.PassThrough]; SS7.3 step 1 for
     * [Action.ForwardAsCtrlCombo] (the physical event, meta bits and all, is what the app should
     * see, so it is simply not consumed rather than replayed). [Action.RunCommand] and any
     * [Action.Multiple] wrapping one are handed to [onCommand] and otherwise treated as consumed
     * no-ops; only [Action.Commit], [Action.Edit] and [Action.ReplaceRecent] (alone or inside a
     * [Action.Multiple]) carry text and go through [TextInputPipeline].
     */
    private fun applyAction(
        action: Action,
        shiftHeld: Boolean,
        altActive: Boolean,
        editor: EditorSnapshot,
        ctrlActive: Boolean = false,
        shiftActive: Boolean = false,
        isRepeat: Boolean = false,
    ): PipelineResult {
        dispatchCommands(action)
        return when (action) {
            Action.PassThrough -> PipelineResult.NOT_CONSUMED
            Action.Ignored, Action.StateOnly -> PipelineResult.CONSUMED_NO_OP
            is Action.ForwardAsCtrlCombo -> PipelineResult.NOT_CONSUMED
            is Action.RunCommand -> PipelineResult.CONSUMED_NO_OP
            is Action.Commit, is Action.Edit, is Action.ReplaceRecent -> textPipelineStep(action, shiftHeld, altActive, ctrlActive, shiftActive, isRepeat, editor)
            is Action.Multiple -> {
                val textActions = action.actions.filter { it is Action.Commit || it is Action.Edit || it is Action.ReplaceRecent }
                if (textActions.isEmpty()) {
                    PipelineResult.CONSUMED_NO_OP
                } else {
                    textPipelineStep(Action.Multiple(textActions), shiftHeld, altActive, ctrlActive, shiftActive, isRepeat, editor)
                }
            }
        }
    }

    private fun dispatchCommands(action: Action) {
        when (action) {
            is Action.RunCommand -> onCommand(action.commandId)
            is Action.Multiple -> action.actions.forEach(::dispatchCommands)
            else -> Unit
        }
    }

    private fun textPipelineStep(action: Action, shiftHeld: Boolean, altActive: Boolean, ctrlActive: Boolean, shiftActive: Boolean, isRepeat: Boolean, editor: EditorSnapshot): PipelineResult {
        // spec: per-app-behavior.md SS3.5 step 4e, SS3.10; nav mode is latched Ctrl (see
        // navModeActive's own KDoc above), which is exactly what EnterDecision.decide needs.
        val request = TextInputRequest.Key(action, shiftHeld = shiftHeld, altActive = altActive, ctrlActive = ctrlActive, shiftActive = shiftActive, navModeActive = navModeActive, isRepeat = isRepeat)
        val result = TextInputPipeline.handle(request, activeField, settings.textInput, resources, textInputState, editor, activeTrust, activeAppProfile)
        textInputState = result.state
        result.capDecision?.let(::applyCapDecision)
        result.autocorrectDebug?.let(::learnNextWord)
        return toPipelineResult(result.ops, result.enterDelivery, result.autocorrectDebug)
    }

    /**
     * spec: autocorrect-suggestions.md SS4: "Every completed word is learned as 'previous word ->
     * this word'"; "A period, Enter or any other hard boundary ... resets the context: the next
     * word is a sentence start." [debug] is the same record [KeyboardSession] forwards to the
     * debug capture; its `before`/`after` already carry exactly "the completed word (the
     * replacement if one happened)" SS7.3 asks this to learn, so this reuses it rather than
     * re-deriving the same fact a second way. A blank `before` (the `empty_word`,
     * `auto_replace_disabled` and `no_input_connection` attempts) means no word boundary
     * completed a word at all, so nothing is learned and the context is left exactly as it was.
     */
    private fun learnNextWord(debug: BoundaryDebugInfo) {
        if (debug.before.isBlank()) return
        val completedWord = debug.after.ifBlank { debug.before }
        val locale = resources.dictionaries.firstOrNull()?.language?.value ?: ImeSettings.DEFAULT_SUBTYPE_LOCALE
        val now = System.currentTimeMillis()
        val fixedPrevious = debug.previousWordAfter
        if (fixedPrevious != null) {
            // SS10's mix-up fix rewrote the word before this one, which the previous boundary
            // already learned as typed ("wagged -> it's"), and the prefix in hand is that typed
            // word: take back that one learn, learn the fixed word in its place, and learn this
            // word after the fixed one ("wagged -> its", "its -> tail"), never after the slip.
            val last = lastLearnedPair
            val typedPrevious = debug.previousWordBefore
            if (last != null && typedPrevious != null && NgramPrefix.of(last.second) == NgramPrefix.of(typedPrevious)) {
                ngramStore = ngramStore.unlearn(locale, last.first, last.second)
                onBigramUnlearned(locale, last.first, last.second)
                ngramStore = ngramStore.learn(locale, last.first, fixedPrevious, now)
                onBigramLearned(locale, last.first, fixedPrevious)
            }
            nextWordPrefixKey = NgramPrefix.of(fixedPrevious)
        }
        ngramStore = ngramStore.learn(locale, nextWordPrefixKey, completedWord, now)
        onBigramLearned(locale, nextWordPrefixKey, completedWord)
        lastLearnedPair = nextWordPrefixKey to completedWord
        nextWordPrefixKey = if (BoundaryDebugInfo.isSoftBoundary(debug.boundaryChar)) NgramPrefix.of(completedWord) else NgramStore.SENTENCE_START
    }

    /**
     * spec: SS4. Predictions for the word about to be typed, shown once the current word is empty
     * (a soft boundary just fired, or the cursor sits on empty space): learned bigrams for the
     * current context first, then starter words. Casing follows the same modifier facts the strip
     * already reads for other purposes (SS4: "shown lowercase, or with a leading capital when Caps
     * Lock, Shift, one-shot Shift or the latched shift layer is active").
     */
    fun nextWordSuggestions(): List<String> {
        if (!activeField.suggestionsAllowed) return emptyList()
        if (!settings.textInput.autocorrect.suggestionsEnabled) return emptyList()
        if (textInputState.currentWord.word.isNotEmpty()) return emptyList()
        val locale = resources.dictionaries.firstOrNull()?.language?.value ?: ImeSettings.DEFAULT_SUBTYPE_LOCALE
        val predictions = NextWordSuggestions.of(ngramStore, locale, nextWordPrefixKey, resources.dictionaries.firstOrNull(), resources.userWords)
        val glyph = modifierGlyphInput()
        val capitalize = glyph.capsLockOn || glyph.shiftOneShotArmed || glyph.shiftPhysicallyHeld
        return predictions.map { if (capitalize) it.replaceFirstChar(Char::uppercaseChar) else it.lowercase() }
    }

    /** spec: text-input.md EditorOp.PassThroughKey KDoc: as the sole op it means "no text change; the caller decides", which for every producer in `:core:text` means "let the physical key through". [enterDelivery] carries per-app-behavior.md SS3.4's real `InputConnection` call forward to [KeyboardSession], which alone knows whether it was actually delivered; [consumed] is provisional in that case (see [PipelineResult]'s own KDoc). */
    private fun toPipelineResult(ops: List<EditorOp>, enterDelivery: EnterIntent?, autocorrectDebug: BoundaryDebugInfo? = null): PipelineResult = when {
        enterDelivery != null -> PipelineResult(ops, consumed = true, enterDelivery = enterDelivery, autocorrectDebug = autocorrectDebug)
        ops.size == 1 && ops[0] == EditorOp.PassThroughKey -> PipelineResult.NOT_CONSUMED.copy(autocorrectDebug = autocorrectDebug)
        else -> PipelineResult(ops, consumed = true, autocorrectDebug = autocorrectDebug)
    }

    /**
     * spec: per-app-behavior.md SS3.4, "clear the Ctrl state (latch, one-shot, nav-mode latch ...)"
     * once a Ctrl-triggered Enter delivery succeeds (or a swallow with an active Ctrl state is
     * reported). Only the latch/one-shot bits are cleared, never `pressed`/`physicallyPressed`:
     * those track a real, still-held key (D4: Fn never sends a key-up), which this Enter delivery
     * has no business erasing. Nav mode's own "cancel the notification, refresh nav mode" follow-up
     * (SS3.4) has no owning module yet, matching [KeyboardSession.handleCommand]'s own note.
     */
    fun clearCtrlStateAfterEnterSend() {
        modifierState = modifierState.copy(ctrl = modifierState.ctrl.copy(oneShot = false, latched = false, latchFromNavMode = false))
    }

    /**
     * spec: text-input.md SS9.3, "records the current cursor context (200 characters before,
     * 1 after)". Only the before-cursor half is available at every call site this pipeline has
     * (the unified 240-before read, text-input.md SS19); the trailing character is not threaded
     * through the field-start/restart/external-move paths that call this, so the key is built
     * from the before-cursor text alone. That is a strictly coarser key than the spec's (it can
     * only under-suppress, by treating two contexts the spec would call distinct as one, never
     * the reverse), so it never suppresses auto-cap somewhere the spec would still arm it.
     */
    private fun autoCapSuppressionContext(textBeforeCursor: String?): String? =
        textBeforeCursor?.takeLast(AUTO_CAP_SUPPRESSION_CONTEXT_CHARS)

    /** spec: text-input.md SS9.3. `:core:text` decides the auto-cap outcome; only applying it to `:core:keys`' own Shift state is this module's job (TextInputResult's own KDoc). */
    private fun applyCapDecision(decision: CapDecision) {
        modifierState = when (decision) {
            CapDecision.ArmOneShot -> modifierState.copy(shift = modifierState.shift.copy(value = ShiftValue.ONE_SHOT))
            CapDecision.ClearOneShot -> if (modifierState.shift.value == ShiftValue.ONE_SHOT) {
                modifierState.copy(shift = modifierState.shift.copy(value = ShiftValue.OFF))
            } else {
                modifierState
            }
            CapDecision.EnableCapsLock -> modifierState.copy(shift = modifierState.shift.copy(value = ShiftValue.CAPS))
            CapDecision.Leave -> modifierState
        }
    }
}
