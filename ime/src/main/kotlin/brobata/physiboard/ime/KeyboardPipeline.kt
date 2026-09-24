package brobata.physiboard.ime

import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.EditEffect
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.LayerResolver
import brobata.physiboard.core.keys.LayoutDescription
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.keys.ModifierMachine
import brobata.physiboard.core.keys.ModifierSettings
import brobata.physiboard.core.keys.ModifierState
import brobata.physiboard.core.keys.ShiftValue
import brobata.physiboard.core.keys.TypingSessionState
import brobata.physiboard.core.pointer.caret.ModifierGlyphInput
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.AutoCapitalization
import brobata.physiboard.core.text.AutocorrectSettings
import brobata.physiboard.core.text.CapDecision
import brobata.physiboard.core.text.DeferredSpace
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.EditorTrust
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.EnterIntent
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.RankedSuggestion
import brobata.physiboard.core.text.RankingOptions
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
        autocorrect = AutocorrectSettings(autoReplaceOnSpaceEnter = true, maxAutoReplaceDistance = 2),
        rankingOptions = RankingOptions(useKeyboardProximity = true),
    ),
    val screenTrackpadEnabled: Boolean = false,
)

/**
 * What [KeyboardSession] must still do to the real `InputConnection` after one pipeline call.
 * [enterDelivery] is non-null only for the Enter deliveries per-app-behavior.md SS3.4 hands to
 * `:ime` to perform for real (see [brobata.physiboard.core.text.TextInputResult.enterDelivery]);
 * [consumed] for those is provisional until [KeyboardSession] learns whether the real call was
 * delivered (SS3.4, "Handled if and only if the request was delivered").
 */
data class PipelineResult(val ops: List<EditorOp>, val consumed: Boolean, val enterDelivery: EnterIntent? = null) {
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
    private val layout: LayoutDescription,
    var resources: TextInputResources = TextInputResources(),
    var settings: KeyboardSettings = KeyboardSettings(),
    private val onCommand: (String) -> Unit = {},
) {
    private var modifierState = ModifierState()
    private var typingState = TypingSessionState()
    private var textInputState = TextInputState()
    private var activeField = FieldContext(FieldKind.NOT_EDITABLE)
    private var activeTrust = EditorTrust.FULL
    private var activeAppProfile = AppProfile.default(null)

    val fieldContext: FieldContext get() = activeField

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
    fun onStartInput(field: FieldContext, trust: EditorTrust = EditorTrust.FULL, appProfile: AppProfile = AppProfile.default(null)) {
        activeField = field
        activeTrust = trust
        activeAppProfile = appProfile
        textInputState = textInputState.forNewField()
        typingState = TypingSessionState()
        modifierState = ModifierMachine.fullReset(modifierState, preserveNavModeLatch = true)
        if (AutoCapitalization.evaluateFieldStartCapsLock(field)) {
            applyCapDecision(CapDecision.EnableCapsLock)
        }
    }

    /** spec: status-bar.md SS13, "field finishes": modifiers reset, nav mode preserved. */
    fun onFinishInput() {
        typingState = TypingSessionState()
        modifierState = ModifierMachine.fullReset(modifierState, preserveNavModeLatch = true)
    }

    /**
     * A selection/cursor change that did not come from this pipeline's own last edit. spec:
     * text-input.md SS2 ("every cursor change that is not the one-character forward step caused
     * by its own last commit clears the deferred-space state, refreshes suggestions, ...").
     * Distinguishing "our own edit" from an external move is `:ime`'s job (it is the one that
     * knows whether it just called the `InputConnection`), so [KeyboardSession] only calls this
     * for a move it did not cause itself.
     */
    fun onExternalSelectionChange(textBeforeCursor: String?) {
        // Resyncing the keyboard's own record to what the editor just reported is always allowed:
        // that is the drift-recovery path itself (rebuild-from-scratch.md "The editor is not a
        // reliable narrator" point 1), not a guess a reduced [activeTrust] should suppress.
        val resynced = textBeforeCursor?.let { textInputState.currentWord.syncedFrom(it) } ?: textInputState.currentWord.reset()
        textInputState = textInputState.copy(
            currentWord = resynced,
            deferredSpace = DeferredSpace.cancelled(),
            // The cursor moved for a reason this pipeline did not cause, so a sentence-ending mark
            // it remembered committing (TextInputState.justCommittedSentenceEnd) is no longer
            // "immediately before the cursor" and must not survive to arm a later, unrelated Space.
            justCommittedSentenceEnd = false,
        )
        // Sentence-end capitalisation, in contrast, needs surrounding context to be right rather
        // than merely present, so it follows [activeTrust] like every other context rule (point 2).
        val capContext = if (activeTrust.contextRulesAllowed) textBeforeCursor else null
        val (capState, decision) = AutoCapitalization.evaluate(textInputState.autoCap, activeField, settings.textInput.autoCap, capContext)
        textInputState = textInputState.copy(autoCap = capState)
        applyCapDecision(decision)
    }

    // -----------------------------------------------------------------------------------------
    // One key event. spec: docs/plans/rebuild-from-scratch.md "Keypress data flow".
    // -----------------------------------------------------------------------------------------

    fun onKeyStroke(stroke: KeyStroke, editor: EditorSnapshot): PipelineResult {
        if (stroke.key is KeyId.Modifier) {
            val action = dispatchModifier(stroke)
            return applyAction(action, shiftHeld = stroke.meta.shift, altActive = modifierState.isAltActive(stroke.meta.alt), editor)
        }

        if (stroke.edge == KeyEdge.UP) {
            val resolution = LayerResolver.resolveKeyUp(modifierState, typingState, stroke)
            modifierState = resolution.state
            typingState = resolution.typing
            return applyAction(resolution.action, shiftHeld = stroke.meta.shift, altActive = modifierState.isAltActive(stroke.meta.alt), editor)
        }

        // spec: keys-and-modifiers.md SS5.2, "for any other key with repeat count 0"; dispatch
        // convention documented on ModifierMachine.onOtherKeyDown.
        modifierState = ModifierMachine.onOtherKeyDown(modifierState, stroke)

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
        )
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
        return applyAction(
            redirectEnterForPerAppBehavior(effectiveStroke, resolution.action),
            shiftHeld = effectiveStroke.meta.shift,
            altActive = modifierState.isAltActive(effectiveStroke.meta.alt),
            editor,
            ctrlActive = ctrlActive,
            shiftActive = shiftActive,
        )
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
        val resolution = LayerResolver.resolveLongPressTick(typingState, nowMs, layout) ?: return null
        modifierState = resolution.state
        typingState = resolution.typing
        return applyAction(resolution.action, shiftHeld = false, altActive = false, editor)
    }

    // -----------------------------------------------------------------------------------------
    // Suggestions. spec: autocorrect-suggestions.md SS3; status-bar.md SS5.1 (slot mapping is the
    // themed strip's job, out of scope here, see KeyboardSession/CandidatesStripView).
    // -----------------------------------------------------------------------------------------

    fun suggestions(): List<RankedSuggestion> {
        if (!activeField.suggestionsAllowed) return emptyList()
        if (!settings.textInput.autocorrect.suggestionsEnabled) return emptyList()
        val word = textInputState.currentWord.word
        if (word.isEmpty()) return emptyList()
        return SuggestionRanking.suggest(word, resources.dictionaries, resources.userWords, settings.textInput.rankingOptions)
    }

    /** spec: autocorrect-suggestions.md SS5, SS6.11. A strip tap, kept out of the typing path. */
    fun onAcceptSuggestion(word: String, editor: EditorSnapshot): PipelineResult {
        val result = TextInputPipeline.handle(TextInputRequest.AcceptSuggestion(word), activeField, settings.textInput, resources, textInputState, editor)
        textInputState = result.state
        result.capDecision?.let(::applyCapDecision)
        return toPipelineResult(result.ops, result.enterDelivery)
    }

    // -----------------------------------------------------------------------------------------
    // Shared plumbing.
    // -----------------------------------------------------------------------------------------

    private fun dispatchModifier(stroke: KeyStroke): Action {
        val key = (stroke.key as KeyId.Modifier).key
        val down = stroke.edge == KeyEdge.DOWN
        val result = when (key) {
            ModifierKey.SHIFT -> if (down) ModifierMachine.shiftDown(modifierState, stroke, settings.modifier) else ModifierMachine.shiftUp(modifierState, stroke, settings.modifier)
            ModifierKey.CTRL -> if (down) ModifierMachine.ctrlDown(modifierState, stroke, settings.modifier) else ModifierMachine.ctrlUp(modifierState, stroke, settings.modifier)
            ModifierKey.ALT -> if (down) ModifierMachine.altDown(modifierState, stroke, settings.modifier) else ModifierMachine.altUp(modifierState, stroke, settings.modifier)
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
    ): PipelineResult {
        dispatchCommands(action)
        return when (action) {
            Action.PassThrough -> PipelineResult.NOT_CONSUMED
            Action.Ignored, Action.StateOnly -> PipelineResult.CONSUMED_NO_OP
            is Action.ForwardAsCtrlCombo -> PipelineResult.NOT_CONSUMED
            is Action.RunCommand -> PipelineResult.CONSUMED_NO_OP
            is Action.Commit, is Action.Edit, is Action.ReplaceRecent -> textPipelineStep(action, shiftHeld, altActive, ctrlActive, shiftActive, editor)
            is Action.Multiple -> {
                val textActions = action.actions.filter { it is Action.Commit || it is Action.Edit || it is Action.ReplaceRecent }
                if (textActions.isEmpty()) {
                    PipelineResult.CONSUMED_NO_OP
                } else {
                    textPipelineStep(Action.Multiple(textActions), shiftHeld, altActive, ctrlActive, shiftActive, editor)
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

    private fun textPipelineStep(action: Action, shiftHeld: Boolean, altActive: Boolean, ctrlActive: Boolean, shiftActive: Boolean, editor: EditorSnapshot): PipelineResult {
        // spec: per-app-behavior.md SS3.5 step 4e, SS3.10; nav mode has no owning module yet (see
        // EnterDecision.decide's own KDoc), so this is always "nav mode is not active".
        val request = TextInputRequest.Key(action, shiftHeld = shiftHeld, altActive = altActive, ctrlActive = ctrlActive, shiftActive = shiftActive, navModeActive = false)
        val result = TextInputPipeline.handle(request, activeField, settings.textInput, resources, textInputState, editor, activeTrust, activeAppProfile)
        textInputState = result.state
        result.capDecision?.let(::applyCapDecision)
        return toPipelineResult(result.ops, result.enterDelivery)
    }

    /** spec: text-input.md EditorOp.PassThroughKey KDoc: as the sole op it means "no text change; the caller decides", which for every producer in `:core:text` means "let the physical key through". [enterDelivery] carries per-app-behavior.md SS3.4's real `InputConnection` call forward to [KeyboardSession], which alone knows whether it was actually delivered; [consumed] is provisional in that case (see [PipelineResult]'s own KDoc). */
    private fun toPipelineResult(ops: List<EditorOp>, enterDelivery: EnterIntent?): PipelineResult = when {
        enterDelivery != null -> PipelineResult(ops, consumed = true, enterDelivery = enterDelivery)
        ops.size == 1 && ops[0] == EditorOp.PassThroughKey -> PipelineResult.NOT_CONSUMED
        else -> PipelineResult(ops, consumed = true)
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
