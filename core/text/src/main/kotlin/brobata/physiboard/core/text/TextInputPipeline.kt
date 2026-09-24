package brobata.physiboard.core.text

import brobata.physiboard.core.dict.DictionaryIndex
import brobata.physiboard.core.dict.RuleSet
import brobata.physiboard.core.dict.UserWordStore
import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.EditEffect

/**
 * One request into [TextInputPipeline]. [Key] wraps a decision already resolved by `:core:keys`;
 * [AcceptSuggestion] is the one path that is not a keystroke at all (a strip tap or a trackpad
 * gesture, autocorrect-suggestions.md SS5), so it is modelled as a sibling case here rather than
 * forced into [Action]'s vocabulary, which has no notion of the suggestion strip.
 */
sealed class TextInputRequest {
    /**
     * [shiftHeld] and [altActive] carry the physical-modifier facts [Backspace] needs
     * (`shift_backspace_delete`, `alt_backspace_delete`) that are not otherwise recoverable from
     * an already-resolved [Action]; a caller not driving those two settings can leave them false.
     *
     * [ctrlActive] and [shiftActive] are a separate pair, for [EnterDecision] only: the fuller
     * per-app-behavior.md SS3.5 step 4 "Ctrl active"/"Shift active" definitions (event meta, held,
     * latched, one-shot, or the nav-mode latch for Ctrl; event meta or the Shift layer latch for
     * Shift, its one-shot already consumed earlier in the same step), which is not the same fact
     * [shiftHeld] carries for [Backspace]. A caller not driving per-app Enter behaviour can leave
     * both false.
     */
    data class Key(
        val action: Action,
        val shiftHeld: Boolean = false,
        val altActive: Boolean = false,
        val ctrlActive: Boolean = false,
        val shiftActive: Boolean = false,
        val navModeActive: Boolean = false,
    ) : TextInputRequest()

    /** [word] is the candidate as shown on the slot; casing is reapplied here, not carried in. */
    data class AcceptSuggestion(val word: String) : TextInputRequest()
}

/**
 * Every tracker this module carries from one keystroke to the next. A field start (not a mere
 * restart) should call [forNewField] before the first request for that field.
 */
data class TextInputState(
    val currentWord: CurrentWordTracker = CurrentWordTracker.empty(),
    val autoCap: AutoCapState = AutoCapState.initial(),
    val deferredSpace: DeferredSpaceDebt = DeferredSpaceDebt.none(),
    val autoSpacePending: Boolean = false,
    val doubleSpaceTimer: DoubleSpaceTimer = DoubleSpaceTimer(),
    val autocorrectMemory: AutocorrectMemory = AutocorrectMemory(),
    val selectionAnchor: SelectionAnchorState? = null,
) {
    fun forNewField(): TextInputState = TextInputState(autoCap = autoCap.forNewField())
}

/**
 * What the editor says is around the cursor for one request. spec: text-input.md SS19 ("unify":
 * one read per keystroke, 240 before, 64 after, in place of 2.x's eleven different window sizes).
 * [textBeforeCursor] is that unified window, or null when the app would not answer the read at
 * all (SS2: every feature that needs it simply does not run). [fullText] is supplied only for the
 * selection and word-motion primitives, which need the whole document (or nothing: SS19 drops the
 * 1000-character fallback reads, so a caller that could not read the whole document should leave
 * this null rather than pass a partial one).
 */
data class EditorSnapshot(
    val textBeforeCursor: String?,
    val fullText: TextWindow? = null,
    val isPrimaryLanguageFrench: Boolean = false,
    val nowMs: Long = 0L,
) {
    val lineBeforeCursor: String? get() = textBeforeCursor?.substringAfterLast('\n')
}

/** The dictionaries, rule sets and personal store [BoundaryEngine] needs; primary dictionary first. */
data class TextInputResources(
    val ruleSets: List<RuleSet> = emptyList(),
    val dictionaries: List<DictionaryIndex> = emptyList(),
    val userWords: UserWordStore = UserWordStore.empty(),
)

/** Every setting this module reads, bundled so [TextInputPipeline.handle] takes one settings value. */
data class TextInputSettingsBundle(
    val autoCap: AutoCapSettings = AutoCapSettings(),
    val spacing: SpacingSettings = SpacingSettings(),
    val backspace: BackspaceSettings = BackspaceSettings(),
    val autocorrect: AutocorrectSettings = AutocorrectSettings(),
    val rankingOptions: RankingOptions = RankingOptions(),
    /** English allows a length change of up to 2 in the automatic-correction safe-shape gate; every other language, 0. spec: autocorrect-suggestions.md SS9. */
    val lengthChangeAllowance: Int = 0,
)

/**
 * The result of one [TextInputPipeline.handle] call: the batch edit to apply, the state to carry
 * into the next call, and, when auto-capitalization was (re)evaluated this call, the decision the
 * caller must still apply to `:core:keys`' own Shift one-shot (this module never touches it
 * directly, spec text-input.md SS9).
 */
data class TextInputResult(
    val ops: List<EditorOp>,
    val state: TextInputState,
    val capDecision: CapDecision? = null,
    /**
     * Non-null only when [handleEnter]'s call to [EnterDecision] resolved to something `:ime` must
     * still perform against the real `InputConnection` (an editor-action request, a plain Enter or
     * Ctrl+Enter key event, or a swallow that still needs its Ctrl state cleared). spec: per-app-
     * behavior.md SS3.4. A plain newline or a decline to the generic path are settled by [ops]
     * alone, exactly like every other request this pipeline handles, so those never set this.
     */
    val enterDelivery: EnterIntent? = null,
)

/**
 * The single entry point for turning one resolved decision into editor operations, chaining the
 * primitives in the exact order the spec gives rather than leaving that order to be
 * reconstructed at the `:ime` call site. Every ordering below cites the section it encodes:
 *
 * - An ordinary letter: text-input.md SS5.5 (deferred-space check, then commit).
 * - Space: text-input.md SS6.1 (unrestricted field) or SS6.2 (restricted field) — double-space
 *   period, then spaced-hyphen-to-dash, then smart quotes, then the boundary hand-off with its
 *   trailing-space guarantee, in that order, each one stopping the chain if it fires.
 * - Enter: text-input.md SS7 — cancel the deferred-space debt and consume a pending one-shot,
 *   then the boundary hand-off with `'\n'`, then commit the newline.
 * - Backspace: text-input.md SS8 — cancel deferred-space/auto-space first, then the forward-
 *   delete alternatives (skipped with a selection), then undo, then fall through.
 * - Alt-layer punctuation or a digit: text-input.md SS5.2 (deferred-space, French spacing, comma
 *   space, "Remove before" replacement, in that order, each an alternative to a plain commit)
 *   followed by SS5.4's follow-up (deferred-space-list marking, apostrophe continuation, or the
 *   boundary hand-off for boundary punctuation).
 * - Accepting a suggestion: autocorrect-suggestions.md SS5 and SS6.11 (word-span walk, casing,
 *   trailing space).
 *
 * Every rule chained here already has its own primitive and its own tests (spec: this module's
 * house rules); this object only owns the *order*, which is exactly the part a JVM test on the
 * primitives alone cannot catch (see [brobata.physiboard.core.text.TextInputPipelineTest] for the
 * whole-sequence tests that motivated this file).
 */
object TextInputPipeline {

    fun handle(
        request: TextInputRequest,
        field: FieldContext,
        settings: TextInputSettingsBundle,
        resources: TextInputResources,
        state: TextInputState,
        editor: EditorSnapshot,
        /**
         * spec: rebuild-from-scratch.md "The editor is not a reliable narrator" point 2: how far
         * [editor]'s reads for this field may be trusted, supplied by the caller alongside [field]
         * rather than assumed. Defaulting to [EditorTrust.FULL] means every existing caller that
         * does not yet resolve a per-app profile ([AppProfile]) keeps today's behaviour exactly.
         */
        trust: EditorTrust = EditorTrust.FULL,
        /**
         * spec: rebuild-from-scratch.md "The editor is not a reliable narrator" point 4, "The app
         * is an input to the pipeline, not a lookup inside it": the current app's already-resolved
         * settings, handed in like [trust] rather than looked up from inside this module. Only
         * [handleEnter] reads it (via [EnterDecision]); defaulting to [AppProfile.default] with
         * `null` keeps every existing caller's Enter behaviour exactly as it was before this
         * profile existed (a plain newline, or the field's own action when it declares one).
         */
        appProfile: AppProfile = AppProfile.default(null),
    ): TextInputResult = when (request) {
        is TextInputRequest.Key -> handleAction(
            request.action, request.shiftHeld, request.altActive, request.ctrlActive, request.shiftActive, request.navModeActive,
            field, settings, resources, state, editor, trust, appProfile,
        )
        is TextInputRequest.AcceptSuggestion -> handleAcceptSuggestion(request.word, field, settings, state, editor)
    }

    /** [trust]-gated view of [EditorSnapshot.textBeforeCursor]: null whenever [EditorTrust.contextRulesAllowed] is false, so a rule that needs surrounding context (sentence-end capitalisation, boundary correction, the spacing rules that inspect what precedes) sees exactly what an unreadable field would give it, rather than a guess from a possibly-stale answer. spec: rebuild-from-scratch.md "The editor is not a reliable narrator" point 2. */
    private fun EditorSnapshot.contextTextBeforeCursor(trust: EditorTrust): String? =
        if (trust.contextRulesAllowed) textBeforeCursor else null

    /**
     * Runs [BoundaryEngine.evaluate] only when [trust] allows a context rule to run at all and the
     * editor's own account still agrees with the word this pipeline is tracking ([DriftCheck]).
     * spec: rebuild-from-scratch.md "The editor is not a reliable narrator" points 1 and 2: a
     * stale, unavailable or disagreeing read skips the correction outright, exactly like
     * [BoundaryOutcome.CommitPlain]; the caller still commits the boundary character itself.
     */
    private fun evaluateBoundarySafely(
        trackedWord: String,
        editor: EditorSnapshot,
        trust: EditorTrust,
        boundaryChar: Char,
        resources: TextInputResources,
        settings: TextInputSettingsBundle,
        memory: AutocorrectMemory,
    ): Pair<AutocorrectMemory, BoundaryOutcome> {
        val editorWindow = editor.contextTextBeforeCursor(trust)?.takeLast(32)
        return when (DriftCheck.evaluate(trackedWord, editorWindow)) {
            is DriftCheck.Agreed -> BoundaryEngine.evaluate(
                trackedWord, editorWindow!!, boundaryChar, resources.ruleSets, resources.dictionaries, resources.userWords,
                settings.autocorrect, settings.rankingOptions, settings.lengthChangeAllowance, memory,
            )
            DriftCheck.Unavailable, DriftCheck.Disagreed -> memory.afterBoundaryWithoutReplacement() to BoundaryOutcome.CommitPlain
        }
    }

    // ---------------------------------------------------------------------------------------
    // Dispatch
    // ---------------------------------------------------------------------------------------

    private fun handleAction(
        action: Action,
        shiftHeld: Boolean,
        altActive: Boolean,
        ctrlActive: Boolean,
        shiftActive: Boolean,
        navModeActive: Boolean,
        field: FieldContext,
        settings: TextInputSettingsBundle,
        resources: TextInputResources,
        state: TextInputState,
        editor: EditorSnapshot,
        trust: EditorTrust,
        appProfile: AppProfile,
    ): TextInputResult = when (action) {
        is Action.Commit -> handleCommit(action.text, field, settings, resources, state, editor, trust)
        is Action.Edit -> handleEdit(
            action.effect, action.extendSelection, shiftHeld, altActive, ctrlActive, shiftActive, navModeActive,
            field, settings, resources, state, editor, trust, appProfile,
        )
        is Action.ReplaceRecent -> handleReplaceRecent(action.deleteCount, action.text, field, settings, resources, state, editor, trust)
        is Action.Multiple -> action.actions.fold(TextInputResult(emptyList(), state)) { acc, next ->
            val step = handleAction(next, shiftHeld, altActive, ctrlActive, shiftActive, navModeActive, field, settings, resources, acc.state, editor, trust, appProfile)
            TextInputResult(acc.ops + step.ops, step.state, step.capDecision ?: acc.capDecision, step.enterDelivery ?: acc.enterDelivery)
        }
        // Sym pages/chords, Ctrl combos, commands, status refreshes and plain pass-through carry
        // no text-level behaviour of their own (spec: text-input.md SS5.3, "committed as plain
        // text with no spacing or capitalization logic at all"); the caller applies the key or
        // command itself.
        else -> TextInputResult(listOf(EditorOp.PassThroughKey), state)
    }

    private fun handleCommit(text: String, field: FieldContext, settings: TextInputSettingsBundle, resources: TextInputResources, state: TextInputState, editor: EditorSnapshot, trust: EditorTrust): TextInputResult {
        if (text == " ") return handleSpace(field, settings, resources, state, editor, trust)
        if (text.length == 1 && text[0].isLetter()) return handleLetter(text[0], field, settings, state, editor, trust)
        if (text.length == 1) return handleAltCharacter(text[0], field, settings, resources, state, editor, trust)
        // A multi-character commit (a Sym-page string, an emoji, dictation, expansion, clipboard):
        // bypasses every smart feature. spec: SS5.3, SS17 ("Text expansion, dictation result,
        // clipboard paste: commit as plain text; deferred debt untouched, auto-space flag untouched").
        return TextInputResult(listOf(EditorOp.CommitText(text)), state.copy(currentWord = CurrentWordTracker.empty()))
    }

    private fun handleEdit(
        effect: EditEffect,
        extendSelection: Boolean,
        shiftHeld: Boolean,
        altActive: Boolean,
        ctrlActive: Boolean,
        shiftActive: Boolean,
        navModeActive: Boolean,
        field: FieldContext,
        settings: TextInputSettingsBundle,
        resources: TextInputResources,
        state: TextInputState,
        editor: EditorSnapshot,
        trust: EditorTrust,
        appProfile: AppProfile,
    ): TextInputResult = when (effect) {
        EditEffect.DELETE_CHAR_BACKWARD -> handleBackspace(settings, state, editor, shiftHeld, altActive)
        EditEffect.DELETE_SELECTION_OR_WORD_BACKWARD, EditEffect.DELETE_WORD_BACKWARD -> handleDeleteWordBackward(editor, state)
        EditEffect.NEWLINE -> handleEnter(field, settings, resources, state, editor, trust, appProfile, ctrlActive, shiftActive, navModeActive)
        EditEffect.SELECT_ALL -> editor.fullText?.let { TextInputResult(listOf(SelectAll.apply(it.text)), state) } ?: TextInputResult(emptyList(), state)
        EditEffect.MOVE_WORD_LEFT -> handleWordMove(MoveDirection.LEFT, extendSelection, state, editor)
        EditEffect.MOVE_WORD_RIGHT -> handleWordMove(MoveDirection.RIGHT, extendSelection, state, editor)
        EditEffect.EXPAND_SELECTION_LEFT -> handleExpand(MoveDirection.LEFT, wordWise = false, state, editor)
        EditEffect.EXPAND_SELECTION_RIGHT -> handleExpand(MoveDirection.RIGHT, wordWise = false, state, editor)
        EditEffect.EXPAND_SELECTION_WORD_LEFT -> handleExpand(MoveDirection.LEFT, wordWise = true, state, editor)
        EditEffect.EXPAND_SELECTION_WORD_RIGHT -> handleExpand(MoveDirection.RIGHT, wordWise = true, state, editor)
        else -> TextInputResult(listOf(EditorOp.PassThroughKey), state)
    }

    // ---------------------------------------------------------------------------------------
    // An ordinary letter. spec: text-input.md SS5.1, SS5.5.
    // ---------------------------------------------------------------------------------------

    private fun handleLetter(ch: Char, field: FieldContext, settings: TextInputSettingsBundle, state: TextInputState, editor: EditorSnapshot, trust: EditorTrust): TextInputResult {
        return when (val debt = DeferredSpace.onNextCommit(state.deferredSpace, ch.toString())) {
            is DeferredSpaceOutcome.InsertSpaceBefore -> {
                val ops = listOf(EditorOp.CommitText(" "), EditorOp.CommitText(ch.toString()))
                var newState = state.copy(
                    deferredSpace = DeferredSpaceDebt.none(),
                    autoSpacePending = true,
                    currentWord = state.currentWord.reset().onCharacterCommitted(ch),
                )
                // spec SS5.5: "the auto-cap rules are re-evaluated so the letter after '? ' gets
                // Shift one-shot" describes THIS letter: `:core:keys` already resolved its case
                // from this same projected text before this call ever ran (the milestone-2
                // sequencing rule, KeyboardPipeline.withDeferredSpaceForcedCase). An ArmOneShot
                // decision here therefore belongs to the letter just committed, not the one after
                // it; propagating it unchanged would arm a second, never-consumed one-shot that
                // survives to capitalize whatever the user types next too ("? W" then a letter
                // would give "? WX" instead of "? Wx"). It is downgraded to ClearOneShot (a safe
                // no-op when nothing else is armed) so this module's armSource bookkeeping and the
                // Shift state `:ime` maintains both agree nothing is left owed once this letter
                // lands.
                val projected = editor.contextTextBeforeCursor(trust)?.let { it + " " }
                val (capState, decision) = AutoCapitalization.evaluate(newState.autoCap, field, settings.autoCap, projected)
                val consumedByThisLetter = decision == CapDecision.ArmOneShot
                newState = newState.copy(autoCap = if (consumedByThisLetter) capState.withArmSource(null) else capState)
                TextInputResult(ops, newState, if (consumedByThisLetter) CapDecision.ClearOneShot else decision)
            }
            is DeferredSpaceOutcome.Kept -> TextInputResult(
                listOf(EditorOp.CommitText(ch.toString())),
                state.copy(deferredSpace = debt.debt, currentWord = state.currentWord.onCharacterCommitted(ch)),
            )
            DeferredSpaceOutcome.Cancelled -> TextInputResult(
                listOf(EditorOp.CommitText(ch.toString())),
                state.copy(deferredSpace = DeferredSpaceDebt.none(), currentWord = state.currentWord.onCharacterCommitted(ch)),
            )
            is DeferredSpaceOutcome.Unaffected -> TextInputResult(
                listOf(EditorOp.CommitText(ch.toString())),
                state.copy(currentWord = state.currentWord.onCharacterCommitted(ch)),
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // Alt-layer punctuation or a digit. spec: text-input.md SS5.2 (pre-commit order), SS5.4
    // (the follow-up). D1: every punctuation mark and digit on the Titan comes from the Alt
    // layer, which is how this function is reached for a single non-letter character.
    // ---------------------------------------------------------------------------------------

    private fun handleAltCharacter(ch: Char, field: FieldContext, settings: TextInputSettingsBundle, resources: TextInputResources, state: TextInputState, editor: EditorSnapshot, trust: EditorTrust): TextInputResult {
        val textBefore = editor.contextTextBeforeCursor(trust)

        when (val debt = DeferredSpace.onNextCommit(state.deferredSpace, ch.toString())) {
            is DeferredSpaceOutcome.InsertSpaceBefore -> {
                val ops = listOf(EditorOp.CommitText(" "), EditorOp.CommitText(ch.toString()))
                return altFollowUp(ch, ops, state.copy(deferredSpace = DeferredSpaceDebt.none(), autoSpacePending = true), field, settings, resources, editor, trust, allowBoundaryHandoff = false)
            }
            is DeferredSpaceOutcome.Kept -> {
                val ops = listOf(EditorOp.CommitText(ch.toString()))
                return altFollowUp(ch, ops, state.copy(deferredSpace = debt.debt), field, settings, resources, editor, trust, allowBoundaryHandoff = false)
            }
            else -> Unit
        }

        if (textBefore != null && field.frenchSpacingAllowed && ch in "?!;:" && FrenchSpacing.isEnabled(settings.spacing, editor.isPrimaryLanguageFrench)) {
            val ops = FrenchSpacing.apply(textBefore, ch)
            // SPEC GAP: SS5.4's follow-up reads as unconditional ("after an Alt-layer character is
            // committed, either way in SS5.2"), which would suggest the boundary hand-off (step 4)
            // still runs after French spacing or comma space fire. SS14 only says this explicitly
            // for "Remove before" ("the engine is not consulted"). Absent a worked example for
            // French spacing or comma space plus a misspelling in the same keystroke, this treats
            // all three SS5.2 alternatives the same way: each one replaces the plain commit and
            // skips the boundary hand-off, rather than re-deriving a delete/re-commit sequence for
            // a boundary character the punctuation rule has already reshaped (a narrow no-break
            // space, or a cleaned-up comma).
            if (ops != null) return altFollowUp(ch, ops, state.copy(autoSpacePending = false), field, settings, resources, editor, trust, allowBoundaryHandoff = false)
        }

        // spec: rebuild-from-scratch.md "The editor is not a reliable narrator" point 2: comma
        // space inspects what precedes just like the other SS5.2 alternatives above, so it needs
        // the same `textBefore != null` gate (previously missing here: it fell back to an empty
        // string and ran on a guess rather than switching off, see the fix's own report).
        if (ch == ',' && settings.spacing.commaSpace && textBefore != null) {
            val ops = CommaSpace.apply(textBefore)
            return altFollowUp(ch, ops, state.copy(autoSpacePending = true), field, settings, resources, editor, trust, allowBoundaryHandoff = false)
        }

        if (state.autoSpacePending && textBefore != null) {
            val two = textBefore.takeLast(2)
            val hasOpenQuote = ch == '"' && editor.lineBeforeCursor?.let { QuoteScan.hasUnclosedOpeningQuote(it) } == true
            val ops = AutoSpaceReplacement.apply(true, ch, two, settings.spacing.removeBeforeList, hasOpenQuote)
            if (ops != null) return altFollowUp(ch, ops, state.copy(autoSpacePending = false), field, settings, resources, editor, trust, allowBoundaryHandoff = false)
        }

        // None of the SS5.2 alternatives applied: clear the auto-space flag and commit plainly,
        // then run the full SS5.4 follow-up, including the boundary hand-off.
        val plainOps = listOf(EditorOp.CommitText(ch.toString()))
        return altFollowUp(ch, plainOps, state.copy(autoSpacePending = false), field, settings, resources, editor, trust, allowBoundaryHandoff = true)
    }

    /** spec: text-input.md SS5.4. [precedingOps] is whatever SS5.2 already decided to commit for [ch]. */
    private fun altFollowUp(
        ch: Char,
        precedingOps: List<EditorOp>,
        stateAfterCommit: TextInputState,
        field: FieldContext,
        settings: TextInputSettingsBundle,
        resources: TextInputResources,
        editor: EditorSnapshot,
        trust: EditorTrust,
        allowBoundaryHandoff: Boolean,
    ): TextInputResult {
        var newState = stateAfterCommit
        if (ch in settings.spacing.beforeNextTextList) {
            newState = newState.copy(deferredSpace = DeferredSpace.onPunctuationInList())
        }

        val prevChar = editor.contextTextBeforeCursor(trust)?.lastOrNull()
        if (WordChars.isApostrophe(ch) && prevChar != null && prevChar.isLetterOrDigit()) {
            newState = newState.copy(currentWord = newState.currentWord.onCharacterCommitted(ch))
            return TextInputResult(precedingOps, newState)
        }

        if (allowBoundaryHandoff && ch in WordChars.BOUNDARY_PUNCTUATION) {
            val trackedWord = newState.currentWord.word
            val (memory, outcome) = evaluateBoundarySafely(trackedWord, editor, trust, ch, resources, settings, newState.autocorrectMemory)
            newState = newState.copy(autocorrectMemory = memory, currentWord = CurrentWordTracker.empty())
            return when (outcome) {
                is BoundaryOutcome.Replaced -> {
                    // spec: text-input.md SS14 (this module's single boundary path). ch is already
                    // committed as the field's last character, so the word plus that boundary is
                    // deleted, the replacement committed, then the boundary re-committed.
                    val ops = precedingOps + listOf(
                        EditorOp.DeleteSurrounding(outcome.original.length + 1, 0),
                        EditorOp.CommitText(outcome.replacement),
                        EditorOp.Haptic,
                        EditorOp.CommitText(ch.toString()),
                    )
                    TextInputResult(ops, newState)
                }
                BoundaryOutcome.CommitPlain -> TextInputResult(precedingOps, newState)
            }
        }

        newState = newState.copy(currentWord = CurrentWordTracker.empty())
        return TextInputResult(precedingOps, newState)
    }

    /**
     * A long-press-Alt or multi-tap replacement (spec: keys-and-modifiers.md SS8.3, SS9; this
     * module's boundary is [Action.ReplaceRecent]). Runs the SS5.4 follow-up on the replacement
     * character. SPEC GAP: unlike [handleAltCharacter], this does not re-run the SS5.2 pre-commit
     * alternatives (deferred space, French spacing, comma space, "Remove before") ahead of the
     * delete-and-replace, since none of the spec's worked examples combine a long-press
     * replacement with a smart-punctuation rule in the same keystroke; it only clears a pending
     * auto-space flag, matching SS6.3's plain "Backspace clears the flag" family of rules.
     */
    private fun handleReplaceRecent(deleteCount: Int, text: String, field: FieldContext, settings: TextInputSettingsBundle, resources: TextInputResources, state: TextInputState, editor: EditorSnapshot, trust: EditorTrust): TextInputResult {
        val ops = listOf(EditorOp.ReplaceBeforeCursor(deleteCount, text))
        val ch = text.singleOrNull()
        val baseState = state.copy(autoSpacePending = false)
        if (ch == null || ch.isLetter()) {
            val tracker = if (ch != null) baseState.currentWord.onCharacterReplaced(ch) else CurrentWordTracker.empty()
            return TextInputResult(ops, baseState.copy(currentWord = tracker))
        }
        return altFollowUp(ch, ops, baseState, field, settings, resources, editor, trust, allowBoundaryHandoff = true)
    }

    // ---------------------------------------------------------------------------------------
    // Space. spec: text-input.md SS6.1 (unrestricted), SS6.2 (restricted).
    // ---------------------------------------------------------------------------------------

    private fun handleSpace(field: FieldContext, settings: TextInputSettingsBundle, resources: TextInputResources, state: TextInputState, editor: EditorSnapshot, trust: EditorTrust): TextInputResult {
        val textBefore = editor.contextTextBeforeCursor(trust)
        val isSecondPress = state.doubleSpaceTimer.isSecondPress(editor.nowMs)
        var newState = state.copy(
            doubleSpaceTimer = state.doubleSpaceTimer.recordSpaceDown(editor.nowMs),
            deferredSpace = DeferredSpace.cancelled(),
        )

        if (field.isRestricted) return handleRestrictedSpace(field, settings, resources, newState, editor, trust)

        // SS6.1 step 1: double-space period.
        if (textBefore != null && field.doubleSpacePeriodAllowed) {
            when (val outcome = DoubleSpacePeriod.apply(settings.spacing.doubleSpaceToPeriod, textBefore, isSecondPress)) {
                is DoubleSpacePeriodOutcome.Fires -> {
                    newState = newState.copy(autoSpacePending = false)
                    return finishWithCapReevaluation(outcome.ops, newState, field, settings, textBefore)
                }
                DoubleSpacePeriodOutcome.BlockedBySentenceEnd -> {
                    // spec quirk (SS6.7, T3): this second space is *not* suppressed by the
                    // trailing-space rule; it is committed as an ordinary space and the chain
                    // continues no further (steps 2-5 are for a space that is still undecided).
                    return finishWithCapReevaluation(listOf(EditorOp.CommitText(" ")), newState.copy(autoSpacePending = true), field, settings, textBefore)
                }
                DoubleSpacePeriodOutcome.NotDue -> Unit
            }
        }

        // SS6.1 step 2: spaced hyphen to dash.
        if (textBefore != null && field.hyphenToDashAllowed) {
            SpacedHyphenDash.apply(textBefore, settings.spacing.dashStyle)?.let { ops ->
                return finishWithCapReevaluation(ops, newState.copy(autoSpacePending = false), field, settings, textBefore)
            }
        }

        // SS6.1 step 3: smart quotes (mid-word quote to apostrophe is dropped for 3.0, SS19).
        if (textBefore != null && field.smartQuotesAllowed && settings.spacing.smartQuotes) {
            SmartQuotes.apply(textBefore, ' ', settings.spacing.smartQuoteStyle)?.let { ops ->
                return finishWithCapReevaluation(ops, newState.copy(autoSpacePending = false), field, settings, textBefore)
            }
        }

        // SS6.1 step 5: the boundary hand-off, with its trailing-space guarantee.
        val trackedWord = newState.currentWord.word
        val (memory, outcome) = evaluateBoundarySafely(trackedWord, editor, trust, ' ', resources, settings, newState.autocorrectMemory)
        newState = newState.copy(autocorrectMemory = memory, currentWord = CurrentWordTracker.empty())

        val ops = mutableListOf<EditorOp>()
        var replacementEndsInApostrophe = false
        var textNowEndsWithSpace = textBefore?.endsWith(" ") == true
        when (outcome) {
            is BoundaryOutcome.Replaced -> {
                ops += EditorOp.DeleteSurrounding(outcome.original.length, 0)
                ops += EditorOp.CommitText(outcome.replacement)
                ops += EditorOp.Haptic
                replacementEndsInApostrophe = WordChars.isApostrophe(outcome.replacement.lastOrNull() ?: ' ')
                textNowEndsWithSpace = outcome.replacement.endsWith(" ")
            }
            BoundaryOutcome.CommitPlain -> Unit
        }
        if (!replacementEndsInApostrophe && !textNowEndsWithSpace) {
            ops += EditorOp.CommitText(" ")
        }
        newState = newState.copy(autoSpacePending = !replacementEndsInApostrophe)
        return finishWithCapReevaluation(ops, newState, field, settings, textBefore)
    }

    /** spec: text-input.md SS6.2. */
    private fun handleRestrictedSpace(field: FieldContext, settings: TextInputSettingsBundle, resources: TextInputResources, state: TextInputState, editor: EditorSnapshot, trust: EditorTrust): TextInputResult {
        if (!settings.autocorrect.autoCorrectEnabled) {
            return TextInputResult(listOf(EditorOp.PassThroughKey), state)
        }
        val trackedWord = state.currentWord.word
        val (memory, outcome) = evaluateBoundarySafely(trackedWord, editor, trust, ' ', resources, settings, state.autocorrectMemory)
        val newState = state.copy(autocorrectMemory = memory, currentWord = CurrentWordTracker.empty())
        return when (outcome) {
            is BoundaryOutcome.Replaced -> {
                val endsApostrophe = WordChars.isApostrophe(outcome.replacement.lastOrNull() ?: ' ')
                val ops = mutableListOf<EditorOp>(EditorOp.DeleteSurrounding(outcome.original.length, 0), EditorOp.CommitText(outcome.replacement), EditorOp.Haptic)
                if (!endsApostrophe) ops += EditorOp.CommitText(" ")
                TextInputResult(ops, newState.copy(autoSpacePending = !endsApostrophe))
            }
            BoundaryOutcome.CommitPlain -> TextInputResult(listOf(EditorOp.PassThroughKey), newState)
        }
    }

    private fun finishWithCapReevaluation(ops: List<EditorOp>, state: TextInputState, field: FieldContext, settings: TextInputSettingsBundle, textBefore: String?): TextInputResult {
        val projected = textBefore?.let { projectText(it, ops) }
        val (capState, decision) = AutoCapitalization.evaluate(state.autoCap, field, settings.autoCap, projected)
        return TextInputResult(ops, state.copy(autoCap = capState), decision)
    }

    // ---------------------------------------------------------------------------------------
    // Enter. spec: text-input.md SS7. The editor-action and IME-action branches depend on
    // per-app Enter behaviour (per-app-behavior.md, out of this module's scope); a caller whose
    // Enter resolves to one of those should not call this function at all. This handles the
    // "Enter becomes a newline" case, run through the same boundary hand-off as Space.
    // ---------------------------------------------------------------------------------------

    /**
     * spec: per-app-behavior.md SS3.5 step 4: consults [EnterDecision] before anything else about
     * this Enter is decided. [EnterIntent.Decline] is the only branch that still runs
     * [handleGenericEnter] (today's whole pre-existing body, unchanged): every other branch is one
     * of SS3.4's delivery mechanisms, none of which run the autocorrect/boundary engine at all
     * (only the generic decline path does, text-input.md SS7).
     */
    private fun handleEnter(
        field: FieldContext,
        settings: TextInputSettingsBundle,
        resources: TextInputResources,
        state: TextInputState,
        editor: EditorSnapshot,
        trust: EditorTrust,
        appProfile: AppProfile,
        ctrlActive: Boolean,
        shiftActive: Boolean,
        navModeActive: Boolean,
    ): TextInputResult = when (val intent = EnterDecision.decide(appProfile, field, ctrlActive, shiftActive, navModeActive)) {
        EnterIntent.Decline -> handleGenericEnter(field, settings, resources, state, editor, trust)
        EnterIntent.InsertNewline -> handlePerAppNewline(field, settings, state, editor, trust)
        is EnterIntent.RequestEditorAction -> handleEditorActionDelivery(intent, field, settings, state, editor, trust)
        EnterIntent.SendPlainEnter, EnterIntent.SendCtrlEnter -> handleKeyEventDelivery(intent, state)
        is EnterIntent.Swallow -> handleSwallowDelivery(intent, state)
    }

    /**
     * The pre-existing "Enter becomes a newline" path (text-input.md SS7): cancel the deferred-
     * space debt and the auto-cap bookkeeping unconditionally, run the boundary/autocorrect engine
     * with `'\n'` as the boundary character exactly like Space does, then commit the newline.
     * Reached only when [EnterDecision] declines (no per-app opinion, or nav mode owns Enter).
     */
    private fun handleGenericEnter(field: FieldContext, settings: TextInputSettingsBundle, resources: TextInputResources, state: TextInputState, editor: EditorSnapshot, trust: EditorTrust): TextInputResult {
        var newState = state.copy(deferredSpace = DeferredSpace.cancelled(), autoCap = state.autoCap.consumedUnconditionally())
        val textBefore = editor.contextTextBeforeCursor(trust)
        val trackedWord = newState.currentWord.word

        val (memory, outcome) = evaluateBoundarySafely(trackedWord, editor, trust, '\n', resources, settings, newState.autocorrectMemory)
        newState = newState.copy(autocorrectMemory = memory, currentWord = CurrentWordTracker.empty(), autoSpacePending = false)

        val ops = mutableListOf<EditorOp>(EditorOp.FinishComposing)
        when (outcome) {
            is BoundaryOutcome.Replaced -> {
                ops += EditorOp.DeleteSurrounding(outcome.original.length, 0)
                ops += EditorOp.CommitText(outcome.replacement)
                ops += EditorOp.Haptic
            }
            BoundaryOutcome.CommitPlain -> Unit
        }
        ops += EditorOp.CommitText("\n")

        val projected = textBefore?.let { projectText(it, ops.filter { op -> op !is EditorOp.FinishComposing }) }
        val (capState, decision) = AutoCapitalization.evaluate(newState.autoCap, field, settings.autoCap, projected)
        return TextInputResult(ops, newState.copy(autoCap = capState), decision)
    }

    /** spec: per-app-behavior.md SS3.4 "Newline": finish composing, commit "\n", auto-cap, reset the suggestion context. No autocorrect/boundary engine, unlike [handleGenericEnter]. */
    private fun handlePerAppNewline(field: FieldContext, settings: TextInputSettingsBundle, state: TextInputState, editor: EditorSnapshot, trust: EditorTrust): TextInputResult {
        val newState = cancelledEnterState(state)
        val ops = listOf(EditorOp.FinishComposing, EditorOp.CommitText("\n"))
        val projected = editor.contextTextBeforeCursor(trust)?.let { it + "\n" }
        val (capState, decision) = AutoCapitalization.evaluate(newState.autoCap, field, settings.autoCap, projected)
        return TextInputResult(ops, newState.copy(autoCap = capState), decision)
    }

    /**
     * spec: per-app-behavior.md SS3.4 "Editor action": finish composing, run the after-Enter
     * auto-cap check, then hand [intent] to `:ime` as [TextInputResult.enterDelivery]. Used both
     * for an explicit per-app send and for [EnterDecision]'s step-e generic request; SS3.4
     * describes one mechanism for both.
     */
    private fun handleEditorActionDelivery(
        intent: EnterIntent.RequestEditorAction,
        field: FieldContext,
        settings: TextInputSettingsBundle,
        state: TextInputState,
        editor: EditorSnapshot,
        trust: EditorTrust,
    ): TextInputResult {
        val newState = cancelledEnterState(state)
        val projected = editor.contextTextBeforeCursor(trust)?.let { it + "\n" }
        val (capState, decision) = AutoCapitalization.evaluate(newState.autoCap, field, settings.autoCap, projected)
        return TextInputResult(listOf(EditorOp.FinishComposing), newState.copy(autoCap = capState), decision, enterDelivery = intent)
    }

    /** spec: per-app-behavior.md SS3.4 "Plain Enter"/"Ctrl+Enter": finish composing, hand [intent] to `:ime`. Neither mechanism runs the auto-cap check (unlike [handleEditorActionDelivery]). */
    private fun handleKeyEventDelivery(intent: EnterIntent, state: TextInputState): TextInputResult {
        val newState = cancelledEnterState(state)
        return TextInputResult(listOf(EditorOp.FinishComposing), newState, capDecision = null, enterDelivery = intent)
    }

    /** spec: per-app-behavior.md SS3.4 "Unsupported send": nothing is inserted, nothing is sent; only the universal deferred-space/one-shot cancellation applies (text-input.md SS7), not the word/auto-space reset the other mechanisms make. */
    private fun handleSwallowDelivery(intent: EnterIntent.Swallow, state: TextInputState): TextInputResult {
        val newState = state.copy(deferredSpace = DeferredSpace.cancelled(), autoCap = state.autoCap.consumedUnconditionally())
        return TextInputResult(emptyList(), newState, capDecision = null, enterDelivery = intent)
    }

    /** spec: text-input.md SS7, "Before any Enter handling the deferred-space debt is cancelled and a Shift one-shot is consumed": common to every per-app delivery mechanism except [handleSwallowDelivery] (SS3.4 keeps that one to exactly "nothing is inserted, nothing is sent"). */
    private fun cancelledEnterState(state: TextInputState): TextInputState = state.copy(
        deferredSpace = DeferredSpace.cancelled(),
        autoCap = state.autoCap.consumedUnconditionally(),
        currentWord = CurrentWordTracker.empty(),
        autoSpacePending = false,
    )

    // ---------------------------------------------------------------------------------------
    // Backspace. spec: text-input.md SS8.
    // ---------------------------------------------------------------------------------------

    private fun handleBackspace(settings: TextInputSettingsBundle, state: TextInputState, editor: EditorSnapshot, shiftHeld: Boolean, altActive: Boolean): TextInputResult {
        val baseState = state.copy(deferredSpace = DeferredSpace.cancelled(), autoSpacePending = false)
        val hasSelection = editor.fullText?.hasSelection ?: false
        val charsBeforeCursor = editor.textBeforeCursor?.length ?: 1
        val undoWindow = baseState.autocorrectMemory.lastReplacement?.let { last -> editor.textBeforeCursor?.takeLast(last.replacement.length + 2) }

        return when (val decision = Backspace.decide(hasSelection, shiftHeld, altActive, settings.backspace, charsBeforeCursor, baseState.autocorrectMemory, undoWindow)) {
            Backspace.Decision.DeleteForward -> TextInputResult(listOf(EditorOp.DeleteSurrounding(0, 1)), baseState)
            is Backspace.Decision.Undo -> TextInputResult(
                decision.result.ops,
                baseState.copy(autocorrectMemory = decision.result.memory, currentWord = CurrentWordTracker.empty().syncedFrom(decision.result.addWordCandidate)),
            )
            Backspace.Decision.FallThrough -> TextInputResult(
                listOf(EditorOp.PassThroughKey),
                baseState.copy(currentWord = baseState.currentWord.onBackspace()),
            )
        }
    }

    private fun handleDeleteWordBackward(editor: EditorSnapshot, state: TextInputState): TextInputResult {
        val newState = state.copy(deferredSpace = DeferredSpace.cancelled(), autoSpacePending = false, currentWord = CurrentWordTracker.empty())
        val hasSelection = editor.fullText?.hasSelection ?: false
        val ops = if (hasSelection) {
            listOf(EditorOp.CommitText(""))
        } else {
            val count = DeleteWordBackward.countToDelete(editor.textBeforeCursor.orEmpty().takeLast(100))
            listOf(EditorOp.DeleteSurrounding(count, 0))
        }
        return TextInputResult(ops, newState)
    }

    // ---------------------------------------------------------------------------------------
    // Selection and cursor motions. spec: text-input.md SS10. The 1000-character fallback reads
    // are dropped for 3.0 (SS19); without a whole-document read, these simply do nothing.
    // ---------------------------------------------------------------------------------------

    private fun handleWordMove(direction: MoveDirection, extendSelection: Boolean, state: TextInputState, editor: EditorSnapshot): TextInputResult {
        val window = editor.fullText ?: return TextInputResult(emptyList(), state)
        if (extendSelection) return handleExpand(direction, wordWise = true, state, editor)
        val op = if (direction == MoveDirection.LEFT) WordMotion.moveLeft(window) else WordMotion.moveRight(window)
        return TextInputResult(listOf(op), state.copy(selectionAnchor = null))
    }

    private fun handleExpand(direction: MoveDirection, wordWise: Boolean, state: TextInputState, editor: EditorSnapshot): TextInputResult {
        val window = editor.fullText ?: return TextInputResult(emptyList(), state)
        val result = if (wordWise) {
            SelectionExpansion.expandWord(state.selectionAnchor, window, direction)
        } else {
            SelectionExpansion.expandCharacter(state.selectionAnchor, window, direction)
        }
        return TextInputResult(listOf(result.op), state.copy(selectionAnchor = result.newState))
    }

    // ---------------------------------------------------------------------------------------
    // Accepting a suggestion. spec: autocorrect-suggestions.md SS5, SS6.11.
    // ---------------------------------------------------------------------------------------

    private fun handleAcceptSuggestion(word: String, field: FieldContext, settings: TextInputSettingsBundle, state: TextInputState, editor: EditorSnapshot): TextInputResult {
        val window = editor.fullText
        val spanStart: Int
        val spanEnd: Int
        val typedSpan: String
        if (window != null) {
            val cursor = window.cursorOrSelectionStart
            val left = WordChars.wordEndingAt(window.text, cursor, maxLength = 64)
            val right = WordChars.wordStartingAt(window.text, cursor, maxLength = 64)
            spanStart = cursor - left.length
            spanEnd = cursor + right.length
            typedSpan = left + right
        } else {
            spanStart = 0
            spanEnd = 0
            typedSpan = word
        }

        val textBeforeSpan = window?.text?.substring(0, spanStart)
        val (_, capAtSpan) = AutoCapitalization.evaluate(state.autoCap, field, settings.autoCap, textBeforeSpan)
        val autoCapOverride = settings.autoCap.capitalizeAtTextStart && capAtSpan == CapDecision.ArmOneShot

        val recased = CasingRules.forTappedSuggestion(typedSpan.ifEmpty { word }, word, autoCapOverride)
        val nextChar = window?.text?.getOrNull(spanEnd)
        val appendSpace = !recased.endsWith("'") && nextChar?.isWhitespace() != true

        val ops = buildList {
            if (window != null) add(EditorOp.DeleteSurrounding(window.cursorOrSelectionStart - spanStart, spanEnd - window.cursorOrSelectionStart))
            add(EditorOp.CommitText(recased))
            if (appendSpace) add(EditorOp.CommitText(" "))
            add(EditorOp.Haptic)
        }

        var newState = state.copy(currentWord = CurrentWordTracker.empty(), autoSpacePending = appendSpace)
        if (autoCapOverride) newState = newState.copy(autoCap = newState.autoCap.consumedUnconditionally())
        return TextInputResult(ops, newState)
    }

    // ---------------------------------------------------------------------------------------

    /** A best-effort text projection used only to decide the *next* auto-cap evaluation; never the source of truth for the real field. */
    private fun projectText(before: String, ops: List<EditorOp>): String {
        var text = before
        for (op in ops) {
            when (op) {
                is EditorOp.CommitText -> text += op.text
                is EditorOp.DeleteSurrounding -> text = text.dropLast(minOf(op.before, text.length))
                is EditorOp.ReplaceBeforeCursor -> text = text.dropLast(minOf(op.count, text.length)) + op.text
                else -> Unit
            }
        }
        return text
    }
}
