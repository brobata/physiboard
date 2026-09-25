package brobata.physiboard.core.keys

/**
 * The modifier and Sym-session state machine: the heart of this module.
 *
 * Every function here is `(ModifierState, KeyStroke, ModifierSettings) -> Result` (a few take
 * extra plain booleans the caller already knows, such as whether a field is editable). Nothing
 * mutates; a caller threads [ModifierState] from one stroke to the next.
 *
 * spec: keys-and-modifiers.md SS2 through SS6 (Shift, Ctrl, Alt, the Sym key, consumption and
 * clearing rules) and SS3 (the Fn key). Dispatch convention for callers: a [KeyStroke] whose
 * [KeyStroke.key] is [ModifierKey.SHIFT], [ModifierKey.CTRL] or [ModifierKey.ALT] goes to the
 * matching `shift*`/`ctrl*`/`alt*` function; [ModifierKey.SYM] goes to `symDown`/`symUp`;
 * [ModifierKey.FN] goes to `fnKeyDown`/`fnKeyUp`; every other key at repeat count 0 goes through
 * [onOtherKeyDown] before layer resolution, per SS5.1 ("Sym counts as non-modifier" for the
 * consecutive-tap memory) and SS5.2 ("any other key" bookkeeping). Only [Action.PassThrough]
 * means the caller should also let the platform's own default handling run; every other action
 * means the stroke was consumed.
 */
object ModifierMachine {

    /** The state produced by a transition, paired with what the caller should do about it. */
    data class Result(val state: ModifierState, val action: Action)

    // ---------------------------------------------------------------------
    // Shift. spec: keys-and-modifiers.md SS5.3, SS5.6.
    // ---------------------------------------------------------------------

    /**
     * [canSwitchLayout] is the caller's "another input subtype exists" fact (SS7.5): with it, an
     * `alt_shift_layout_switch` chord fires here when Alt is held (physically or by the event's
     * meta bit) as Shift goes down; the mirror order lives in [altDown].
     */
    fun shiftDown(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings, canSwitchLayout: Boolean = false): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.SHIFT)) { "not a Shift stroke: ${stroke.key}" }
        if (stroke.repeatCount > 0) return Result(state, Action.PassThrough)
        // spec SS5.1: "the state machine ignores a down while already pressed". Both Shift keys
        // normalise to the one SHIFT key, so holding one and pressing the other arrives here as a
        // second down and must not read as a double tap (review A7).
        if (state.shift.pressed) return Result(state, Action.PassThrough)
        if (canSwitchLayout && settings.altShiftLayoutSwitch && (stroke.meta.alt || state.alt.physicallyPressed)) {
            return Result(altShiftChordCleared(state, ModifierKey.SHIFT), Action.RunCommand(KeyCommands.SWITCH_LAYOUT))
        }

        if (state.shift.layerLatched) {
            val cleared = state.copy(
                shift = ShiftState(),
                holdBookkeeping = state.holdBookkeeping.copy(snapshot = null),
                lastKeyWasModifier = ModifierKey.SHIFT,
            )
            return Result(cleared, Action.StateOnly)
        }

        val snapshot = snapshotOf(state)
        val consecutive = state.lastKeyWasModifier == ModifierKey.SHIFT
        val previousDownAt = state.shift.downAtMs
        val isDoubleTap = consecutive && previousDownAt != null &&
            (stroke.timeMs - previousDownAt) < settings.doubleTapWindowMs
        val current = state.shift.value
        val newValue = when {
            isDoubleTap -> if (current != ShiftValue.CAPS) ShiftValue.CAPS else ShiftValue.OFF
            current == ShiftValue.OFF -> ShiftValue.ONE_SHOT
            else -> ShiftValue.OFF
        }

        val newShift = state.shift.copy(value = newValue, pressed = true, physicallyPressed = true, downAtMs = stroke.timeMs)
        val newState = state.copy(
            shift = newShift,
            holdBookkeeping = HoldBookkeeping(snapshot = snapshot, heldKey = ModifierKey.SHIFT),
            lastKeyWasModifier = ModifierKey.SHIFT,
        )
        return Result(newState, Action.PassThrough)
    }

    fun shiftUp(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.SHIFT)) { "not a Shift stroke: ${stroke.key}" }
        if (!state.shift.pressed) return Result(state, Action.PassThrough)

        val downAt = state.shift.downAtMs ?: stroke.timeMs
        val duration = stroke.timeMs - downAt
        val otherKeyDuringHold = state.holdBookkeeping.heldKey == ModifierKey.SHIFT && state.holdBookkeeping.otherKeyDuringHold
        val externalInteraction = state.holdBookkeeping.heldKey == ModifierKey.SHIFT && state.holdBookkeeping.externalInteractionDuringHold
        val intentionalHold = externalInteraction || (duration > settings.holdThresholdMs && !otherKeyDuringHold)

        if (intentionalHold) {
            val snapshot = state.holdBookkeeping.snapshot
            val restored = snapshot?.shiftValue ?: state.shift.value
            val newShift = state.shift.copy(
                // Review A4's rule holds here too, as it does for Ctrl ("in both cases", SS5.4).
                value = if (otherKeyDuringHold && restored == ShiftValue.ONE_SHOT) ShiftValue.OFF else restored,
                pressed = false,
                physicallyPressed = false,
                layerLatched = false,
            )
            return Result(state.copy(shift = newShift), Action.PassThrough)
        }

        // SPEC AMENDMENT (review A3): SS5.3 sets the Shift layer latch from its own
        // release-to-release timer (at most 500 ms) while caps lock uses the down-to-down window
        // (under 500 ms). The two can disagree (down 0, up 250, down 520, up 560: caps OFF but the
        // layer latched), which uppercases every letter with no badge to explain it. The layer
        // latch is now set exactly when the quick release closes the double tap that latched
        // caps, which is what SS5.6 describes ("a normal double tap sets both") and what SS22
        // suggests ("fold them into the logical latch"). The separate release timer is gone.
        val isQuickTap = duration < settings.holdThresholdMs && !otherKeyDuringHold && !externalInteraction
        val closesDoubleTap = isQuickTap && state.shift.value == ShiftValue.CAPS

        // SPEC AMENDMENT (review A4): SS5.3 gives Shift no "other key during the hold clears the
        // one-shot" rule; SS5.4 gives Ctrl one. Without it, Shift held over a Backspace or Space
        // (keys that consume no one-shot, SS6.1) leaves ONE_SHOT armed and capitalises the next
        // letter. Shift gets Ctrl's rule: the down armed it, the chord used it, the release must
        // not leave Shift waiting for one more key. Caps lock survives, as Ctrl's latch does.
        val value = if (otherKeyDuringHold && state.shift.value == ShiftValue.ONE_SHOT) ShiftValue.OFF else state.shift.value

        val newShift = state.shift.copy(
            value = value,
            pressed = false,
            physicallyPressed = false,
            layerLatched = state.shift.layerLatched || closesDoubleTap,
        )
        return Result(state.copy(shift = newShift), Action.PassThrough)
    }

    // ---------------------------------------------------------------------
    // Ctrl. spec: keys-and-modifiers.md SS5.4.
    // ---------------------------------------------------------------------

    fun ctrlDown(
        state: ModifierState,
        stroke: KeyStroke,
        settings: ModifierSettings,
        navModeInputViewActive: Boolean = false,
    ): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.CTRL)) { "not a Ctrl stroke: ${stroke.key}" }
        if (stroke.repeatCount > 0) return Result(state, Action.PassThrough)
        if (state.ctrl.pressed) return Result(state, Action.PassThrough)

        val snapshot = snapshotOf(state)
        val consecutive = state.lastKeyWasModifier == ModifierKey.CTRL
        val lastRelease = if (consecutive) state.ctrl.lastReleaseAtMs else null

        var latched = state.ctrl.latched
        var oneShot = state.ctrl.oneShot
        var latchFromNavMode = state.ctrl.latchFromNavMode
        var action: Action = Action.PassThrough

        when {
            state.ctrl.latched && state.ctrl.latchFromNavMode && !navModeInputViewActive -> {
                latched = false; oneShot = false; latchFromNavMode = false
                action = Action.RunCommand(KeyCommands.EXIT_NAV_MODE)
            }
            state.ctrl.latched && !state.ctrl.latchFromNavMode -> {
                latched = false; oneShot = false
            }
            state.ctrl.latched -> { // from nav mode, input view active: spec calls this "should not happen"
                latched = false; oneShot = false; latchFromNavMode = false
            }
            state.ctrl.oneShot -> {
                val doubleTap = lastRelease != null && (stroke.timeMs - lastRelease) < settings.doubleTapWindowMs
                latched = doubleTap
                oneShot = false
            }
            lastRelease != null && (stroke.timeMs - lastRelease) < settings.doubleTapWindowMs -> latched = true
            else -> oneShot = true
        }

        val newCtrl = state.ctrl.copy(
            latched = latched, oneShot = oneShot, latchFromNavMode = latchFromNavMode,
            pressed = true, physicallyPressed = true, downAtMs = stroke.timeMs, lastReleaseAtMs = lastRelease,
        )
        val newState = state.copy(
            ctrl = newCtrl,
            holdBookkeeping = HoldBookkeeping(snapshot = snapshot, heldKey = ModifierKey.CTRL),
            lastKeyWasModifier = ModifierKey.CTRL,
        )
        return Result(newState, action)
    }

    fun ctrlUp(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.CTRL)) { "not a Ctrl stroke: ${stroke.key}" }
        if (!state.ctrl.pressed) return Result(state, Action.PassThrough)

        val downAt = state.ctrl.downAtMs ?: stroke.timeMs
        val duration = stroke.timeMs - downAt
        val otherKeyDuringHold = state.holdBookkeeping.heldKey == ModifierKey.CTRL && state.holdBookkeeping.otherKeyDuringHold
        val externalInteraction = state.holdBookkeeping.heldKey == ModifierKey.CTRL && state.holdBookkeeping.externalInteractionDuringHold
        val intentionalHold = externalInteraction || (duration > settings.holdThresholdMs && !otherKeyDuringHold)

        var ctrl = if (intentionalHold) {
            val snapshot = state.holdBookkeeping.snapshot
            state.ctrl.copy(
                oneShot = snapshot?.ctrlOneShot ?: state.ctrl.oneShot,
                latched = snapshot?.ctrlLatched ?: state.ctrl.latched,
                latchFromNavMode = snapshot?.ctrlLatchFromNavMode ?: state.ctrl.latchFromNavMode,
                pressed = false, physicallyPressed = false,
            )
        } else {
            state.ctrl.copy(pressed = false, physicallyPressed = false, lastReleaseAtMs = stroke.timeMs)
        }

        // spec: "if another key was pressed during the hold and Ctrl is one-shot but not latched, the one-shot clears"
        if (otherKeyDuringHold && ctrl.oneShot && !ctrl.latched) {
            ctrl = ctrl.copy(oneShot = false)
        }

        return Result(state.copy(ctrl = ctrl), Action.PassThrough)
    }

    // ---------------------------------------------------------------------
    // Alt. spec: keys-and-modifiers.md SS5.5, SS5.6.
    // ---------------------------------------------------------------------

    /** [canSwitchLayout] as on [shiftDown]: the Alt+Shift chord in its "Shift first" order. */
    fun altDown(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings, canSwitchLayout: Boolean = false): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.ALT)) { "not an Alt stroke: ${stroke.key}" }
        if (stroke.repeatCount > 0) return Result(state, Action.Ignored)
        if (!state.alt.pressed && canSwitchLayout && settings.altShiftLayoutSwitch && (stroke.meta.shift || state.shift.physicallyPressed)) {
            return Result(altShiftChordCleared(state, ModifierKey.ALT), Action.RunCommand(KeyCommands.SWITCH_LAYOUT))
        }

        if (state.alt.layerLatched) {
            val cleared = state.copy(
                alt = AltState(),
                holdBookkeeping = state.holdBookkeeping.copy(snapshot = null),
                lastKeyWasModifier = ModifierKey.ALT,
            )
            return Result(cleared, Action.StateOnly)
        }

        // SPEC AMENDMENT (review A6): the Alt+Ctrl dictation chord of SS5.4/SS5.5 is dropped.
        // SS22 marks it undecided: on the Titan "Ctrl" is a held Fn, so the chord could only fire
        // from Alt plus an Fn hold, and that same Fn hold is the burst that already toggles
        // dictation (SS3.3), toggling it straight back off. An Alt or Ctrl down with the other's
        // meta bit is now an ordinary press of that key.

        val symPageWasOpen = state.sym.currentPageNumber != 0
        val stateWithPageClosed = if (symPageWasOpen) state.copy(sym = state.sym.copy(currentPageNumber = 0)) else state

        if (state.alt.pressed) return Result(stateWithPageClosed, Action.Ignored)

        val snapshot = snapshotOf(stateWithPageClosed)
        val consecutive = state.lastKeyWasModifier == ModifierKey.ALT
        val lastRelease = if (consecutive) state.alt.lastReleaseAtMs else null

        var latched = state.alt.latched
        var oneShot = state.alt.oneShot
        when {
            state.alt.latched -> { latched = false; oneShot = false }
            state.alt.oneShot -> {
                val doubleTap = lastRelease != null && (stroke.timeMs - lastRelease) < settings.doubleTapWindowMs
                latched = doubleTap
                oneShot = false
            }
            lastRelease != null && (stroke.timeMs - lastRelease) < settings.doubleTapWindowMs -> latched = true
            else -> oneShot = true
        }

        val newAlt = state.alt.copy(
            latched = latched, oneShot = oneShot, pressed = true, physicallyPressed = true,
            downAtMs = stroke.timeMs, lastReleaseAtMs = lastRelease,
        )
        val newState = stateWithPageClosed.copy(
            alt = newAlt,
            holdBookkeeping = HoldBookkeeping(snapshot = snapshot, heldKey = ModifierKey.ALT),
            lastKeyWasModifier = ModifierKey.ALT,
        )
        return Result(newState, Action.Ignored)
    }

    fun altUp(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.ALT)) { "not an Alt stroke: ${stroke.key}" }
        if (!state.alt.pressed) return Result(state, Action.PassThrough)

        val downAt = state.alt.downAtMs ?: stroke.timeMs
        val duration = stroke.timeMs - downAt
        val otherKeyDuringHold = state.holdBookkeeping.heldKey == ModifierKey.ALT && state.holdBookkeeping.otherKeyDuringHold
        val externalInteraction = state.holdBookkeeping.heldKey == ModifierKey.ALT && state.holdBookkeeping.externalInteractionDuringHold
        val intentionalHold = externalInteraction || (duration > settings.holdThresholdMs && !otherKeyDuringHold)

        if (intentionalHold) {
            val snapshot = state.holdBookkeeping.snapshot
            val newAlt = state.alt.copy(
                oneShot = snapshot?.altOneShot ?: state.alt.oneShot,
                latched = snapshot?.altLatched ?: state.alt.latched,
                pressed = false, physicallyPressed = false, layerLatched = false,
            )
            return Result(state.copy(alt = newAlt), Action.PassThrough)
        }

        // SPEC AMENDMENT (review A3, the Alt mirror of shiftUp): the Alt layer latch follows the
        // Alt latch the down-side double tap produced, with no release timer of its own.
        val isQuickTap = duration < settings.holdThresholdMs && !otherKeyDuringHold && !externalInteraction
        val closesDoubleTap = isQuickTap && state.alt.latched

        val newAlt = state.alt.copy(
            pressed = false, physicallyPressed = false, lastReleaseAtMs = stroke.timeMs,
            layerLatched = state.alt.layerLatched || closesDoubleTap,
        )
        return Result(state.copy(alt = newAlt), Action.PassThrough)
    }

    /** spec SS7.5, Alt+Shift: "Alt and Shift state fully cleared; next input subtype; toast; consumed". */
    private fun altShiftChordCleared(state: ModifierState, chordKey: ModifierKey): ModifierState = state.copy(
        shift = ShiftState(),
        alt = AltState(),
        holdBookkeeping = HoldBookkeeping(),
        lastKeyWasModifier = chordKey,
    )

    // ---------------------------------------------------------------------
    // Sym. spec: keys-and-modifiers.md SS4; layers-sym-alt.md SS5.
    // ---------------------------------------------------------------------

    fun symDown(
        state: ModifierState,
        stroke: KeyStroke,
        settings: ModifierSettings,
        hasEditableField: Boolean,
    ): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.SYM)) { "not a Sym stroke: ${stroke.key}" }
        if (stroke.repeatCount > 0) return Result(state, Action.Ignored)

        // spec SS4.1: Sym pressed while Alt is physically held clears all Alt state first.
        var newState = if (stroke.meta.alt) state.copy(alt = AltState()) else state

        if (!hasEditableField) {
            // spec SS15: without a field the down goes to launcher/system logic, not the pages.
            return Result(newState, Action.Ignored)
        }

        val armAssistant = settings.symLongPressAssistantEnabled && !settings.symIsTrackpadTrigger
        newState = newState.copy(
            sym = newState.sym.copy(
                togglePending = true,
                chordUsed = false,
                assistantArmedAtMs = if (armAssistant) stroke.timeMs else null,
                assistantFired = false,
            ),
        )
        return Result(newState, Action.Ignored)
    }

    fun symUp(
        state: ModifierState,
        stroke: KeyStroke,
        hasEditableField: Boolean,
        pages: SymPagesConfig,
    ): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.SYM)) { "not a Sym stroke: ${stroke.key}" }

        if (!hasEditableField) {
            val cleared = state.copy(sym = state.sym.copy(togglePending = false, chordUsed = false, assistantArmedAtMs = null))
            return Result(cleared, Action.Ignored)
        }
        if (state.sym.assistantFired) {
            val cleared = state.copy(sym = SymSessionState(currentPageNumber = state.sym.currentPageNumber))
            return Result(cleared, Action.Ignored)
        }
        if (state.sym.togglePending && !state.sym.chordUsed) {
            val nextPage = pages.nextPage(state.sym.currentPageNumber)
            return Result(state.copy(sym = SymSessionState(currentPageNumber = nextPage)), Action.StateOnly)
        }
        val cleared = state.copy(sym = state.sym.copy(togglePending = false, chordUsed = false, assistantArmedAtMs = null))
        return Result(cleared, Action.Ignored)
    }

    /** Marks the current Sym press as a chord, so its release will not cycle a page. spec: layers-sym-alt.md SS5.2, SS5.3. */
    fun symChordUsed(state: ModifierState): ModifierState =
        state.copy(sym = state.sym.copy(chordUsed = true, assistantArmedAtMs = null))

    /**
     * Whether the 600 ms assistant-hold timer has fired. A caller with no timer calls this on
     * every later stroke, or on its own poll tick; the check is a pure function of elapsed time.
     * spec: keys-and-modifiers.md SS4.4; layers-sym-alt.md SS5.6.
     */
    fun symAssistantTimerFired(state: ModifierState, nowMs: Long, settings: ModifierSettings): Result {
        val armedAt = state.sym.assistantArmedAtMs ?: return Result(state, Action.Ignored)
        if (nowMs - armedAt < settings.symAssistantHoldMs) return Result(state, Action.Ignored)
        val newState = state.copy(sym = state.sym.copy(togglePending = false, assistantArmedAtMs = null, assistantFired = true))
        return Result(newState, Action.RunCommand(KeyCommands.LAUNCH_ASSISTANT))
    }

    // ---------------------------------------------------------------------
    // Fn. spec: keys-and-modifiers.md SS3.
    // ---------------------------------------------------------------------

    /**
     * One Fn-origin key-down, already recognised as such by the device module (scancode
     * recognition is a Titan fact, keys-and-modifiers.md SS3.2, and stays out of this module).
     * spec: SS3.3 (burst detection) and SS3.4 (Fn masquerading as Ctrl when the feature is off).
     */
    fun fnKeyDown(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.FN)) { "not an Fn stroke: ${stroke.key}" }
        return if (settings.fnLongPressSpeechEnabled) fnBurstDown(state, stroke, settings) else fnAsCtrlDown(state, stroke)
    }

    /** spec: keys-and-modifiers.md SS3.3, last paragraph (handled for non-Titan hardware only). */
    fun fnKeyUp(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings): Result {
        require(stroke.key == KeyId.Modifier(ModifierKey.FN)) { "not an Fn stroke: ${stroke.key}" }
        if (!settings.fnLongPressSpeechEnabled) return Result(state, Action.PassThrough)
        val hadTriggered = state.fnBurst.blocked && state.fnBurst.count >= settings.fnBurstTriggerCount
        val cleared = state.copy(fnBurst = FnBurstState())
        return Result(cleared, if (hadTriggered) Action.Ignored else Action.PassThrough)
    }

    private fun fnBurstDown(state: ModifierState, stroke: KeyStroke, settings: ModifierSettings): Result {
        val expired = state.fnBurst.lastEventAtMs?.let { stroke.timeMs - it >= settings.fnBurstResetMs } ?: false
        val burst = if (expired) FnBurstState() else state.fnBurst

        if (burst.blocked) {
            return Result(state.copy(fnBurst = burst.copy(lastEventAtMs = stroke.timeMs)), Action.StateOnly)
        }

        val newCount = burst.count + 1
        return if (newCount >= settings.fnBurstTriggerCount) {
            val clearedState = state.copy(
                ctrl = CtrlState(),
                alt = AltState(),
                fnBurst = FnBurstState(count = newCount, blocked = true, lastEventAtMs = stroke.timeMs),
            )
            Result(clearedState, Action.RunCommand(KeyCommands.TOGGLE_DICTATION))
        } else {
            Result(state.copy(fnBurst = burst.copy(count = newCount, lastEventAtMs = stroke.timeMs)), Action.StateOnly)
        }
    }

    private fun fnAsCtrlDown(state: ModifierState, stroke: KeyStroke): Result {
        // spec SS3.4: repeat 0 never arrives for Fn on the Titan (D4); the first repeat is the press.
        if (stroke.repeatCount != 1 || state.ctrl.pressed) return Result(state, Action.PassThrough)
        val newCtrl = state.ctrl.copy(pressed = true, physicallyPressed = true, oneShot = true, downAtMs = stroke.timeMs)
        return Result(state.copy(ctrl = newCtrl, lastKeyWasModifier = ModifierKey.CTRL), Action.PassThrough)
    }

    // ---------------------------------------------------------------------
    // Shared bookkeeping and reset. spec: keys-and-modifiers.md SS5.1, SS5.2, SS6.5.
    // ---------------------------------------------------------------------

    /**
     * Bookkeeping every non-modifier key-down at repeat 0 performs before layer resolution: it
     * marks that a modifier hold (if any) saw another key and blocks an in-progress Fn burst.
     * (SS5.2's "both release-to-release timers reset" has nothing left to reset: the layer
     * latches follow the down-side double tap since review A3.)
     *
     * spec: keys-and-modifiers.md SS5.2 ("for any other key with repeat count 0..."), SS5.1
     * ("cleared by every non-modifier down (Sym counts as non-modifier here)"), SS3.3 ("any
     * non-Fn key-down while the count is above 0 sets the burst to blocked").
     *
     * SPEC GAP: the spec does not say explicitly whether Sym and Fn strokes themselves count as
     * "any other key" for this bookkeeping, only that Sym counts as non-modifier for the
     * consecutive-tap memory. This module applies the full bookkeeping to every key that is not
     * Shift, Ctrl or Alt, which is the most direct reading of "any other key"; callers dispatch
     * Fn strokes to [fnKeyDown]/[fnKeyUp] instead of this function so an Fn key cannot block its
     * own burst.
     */
    fun onOtherKeyDown(state: ModifierState, stroke: KeyStroke): ModifierState {
        if (stroke.repeatCount > 0) return state
        val fnBurst = if (state.fnBurst.count > 0) state.fnBurst.copy(blocked = true) else state.fnBurst
        return state.copy(
            holdBookkeeping = state.holdBookkeeping.copy(otherKeyDuringHold = true),
            fnBurst = fnBurst,
            lastKeyWasModifier = null,
        )
    }

    /** Records that something outside the key stream (a status-bar button) happened during the current modifier hold. */
    fun markExternalInteractionDuringHold(state: ModifierState): ModifierState =
        state.copy(holdBookkeeping = state.holdBookkeeping.copy(externalInteractionDuringHold = true))

    /**
     * Clears all modifier and Sym-session state. spec: keys-and-modifiers.md SS6.5. Triggered by
     * a field change, the keyboard window hiding, or Ctrl+Space; the caller decides when. Passing
     * [preserveNavModeLatch] implements "preserve nav mode": a Ctrl latch (from any source)
     * survives as a nav-mode latch instead of being cleared.
     */
    fun fullReset(state: ModifierState, preserveNavModeLatch: Boolean): ModifierState {
        val ctrl = if (preserveNavModeLatch && (state.ctrl.latched || state.ctrl.latchFromNavMode)) {
            CtrlState(latched = true, latchFromNavMode = true)
        } else {
            CtrlState()
        }
        return ModifierState(ctrl = ctrl)
    }

    private fun snapshotOf(state: ModifierState): ModifierSnapshot = ModifierSnapshot(
        shiftValue = state.shift.value,
        ctrlOneShot = state.ctrl.oneShot,
        ctrlLatched = state.ctrl.latched,
        ctrlLatchFromNavMode = state.ctrl.latchFromNavMode,
        altOneShot = state.alt.oneShot,
        altLatched = state.alt.latched,
    )
}
