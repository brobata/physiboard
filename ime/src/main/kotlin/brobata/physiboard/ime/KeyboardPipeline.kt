package brobata.physiboard.ime

import brobata.physiboard.core.keys.Action
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
import brobata.physiboard.core.text.AutoCapitalization
import brobata.physiboard.core.text.CapDecision
import brobata.physiboard.core.text.DeferredSpace
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.RankedSuggestion
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
 */
data class KeyboardSettings(
    val modifier: ModifierSettings = ModifierSettings(),
    val resolver: LayerResolver.LayerResolverSettings = LayerResolver.LayerResolverSettings(),
    val textInput: TextInputSettingsBundle = TextInputSettingsBundle(),
)

/** What [KeyboardSession] must still do to the real `InputConnection` after one pipeline call. */
data class PipelineResult(val ops: List<EditorOp>, val consumed: Boolean) {
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

    val fieldContext: FieldContext get() = activeField

    /** When a long press is armed, the wall-clock time (same basis as [KeyStroke.timeMs]) it fires at. spec: keys-and-modifiers.md SS8.3. */
    val pendingLongPressDeadlineMs: Long?
        get() = typingState.pendingLongPress?.let { it.armedAtMs + it.thresholdMs }

    // -----------------------------------------------------------------------------------------
    // Field lifecycle. spec: status-bar.md SS13 ("modifier state is reset, nav mode preserved");
    // text-input.md SS3 (field classification, done by the caller from the platform's editor
    // info: `:ime`'s job, not this class's, see KeyboardSession.classifyField).
    // -----------------------------------------------------------------------------------------

    /** A field started (fresh or restarting). spec: text-input.md SS3, SS9.1 ("field start" trigger). */
    fun onStartInput(field: FieldContext) {
        activeField = field
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
        val resynced = textBeforeCursor?.let { textInputState.currentWord.syncedFrom(it) } ?: textInputState.currentWord.reset()
        textInputState = textInputState.copy(
            currentWord = resynced,
            deferredSpace = DeferredSpace.cancelled(),
        )
        val (capState, decision) = AutoCapitalization.evaluate(textInputState.autoCap, activeField, settings.textInput.autoCap, textBeforeCursor)
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
        return applyAction(
            resolution.action,
            shiftHeld = effectiveStroke.meta.shift,
            altActive = modifierState.isAltActive(effectiveStroke.meta.alt),
            editor,
        )
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
        return toPipelineResult(result.ops)
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
    private fun applyAction(action: Action, shiftHeld: Boolean, altActive: Boolean, editor: EditorSnapshot): PipelineResult {
        dispatchCommands(action)
        return when (action) {
            Action.PassThrough -> PipelineResult.NOT_CONSUMED
            Action.Ignored, Action.StateOnly -> PipelineResult.CONSUMED_NO_OP
            is Action.ForwardAsCtrlCombo -> PipelineResult.NOT_CONSUMED
            is Action.RunCommand -> PipelineResult.CONSUMED_NO_OP
            is Action.Commit, is Action.Edit, is Action.ReplaceRecent -> textPipelineStep(action, shiftHeld, altActive, editor)
            is Action.Multiple -> {
                val textActions = action.actions.filter { it is Action.Commit || it is Action.Edit || it is Action.ReplaceRecent }
                if (textActions.isEmpty()) PipelineResult.CONSUMED_NO_OP else textPipelineStep(Action.Multiple(textActions), shiftHeld, altActive, editor)
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

    private fun textPipelineStep(action: Action, shiftHeld: Boolean, altActive: Boolean, editor: EditorSnapshot): PipelineResult {
        val request = TextInputRequest.Key(action, shiftHeld = shiftHeld, altActive = altActive)
        val result = TextInputPipeline.handle(request, activeField, settings.textInput, resources, textInputState, editor)
        textInputState = result.state
        result.capDecision?.let(::applyCapDecision)
        return toPipelineResult(result.ops)
    }

    /** spec: text-input.md EditorOp.PassThroughKey KDoc: as the sole op it means "no text change; the caller decides", which for every producer in `:core:text` means "let the physical key through". */
    private fun toPipelineResult(ops: List<EditorOp>): PipelineResult =
        if (ops.size == 1 && ops[0] == EditorOp.PassThroughKey) PipelineResult.NOT_CONSUMED else PipelineResult(ops, consumed = true)

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
