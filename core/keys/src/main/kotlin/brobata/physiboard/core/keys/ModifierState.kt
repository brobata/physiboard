package brobata.physiboard.core.keys

/**
 * Shift's three-state machine. spec: keys-and-modifiers.md SS2 ("Shift is a three-state machine:
 * OFF, ONE_SHOT, CAPS").
 */
enum class ShiftValue { OFF, ONE_SHOT, CAPS }

/**
 * Everything the spec tracks about Shift between events.
 *
 * spec: keys-and-modifiers.md SS2 (the fact table), SS5.3 (transitions), SS5.6 (the layer latch).
 *
 * [downAtMs] doubles as both "the down that is currently held" (for hold-duration and the
 * intentional-hold restore on release) and "the previous Shift down" (for the down-to-down
 * double-tap window), because both readings name the same instant: the most recent Shift
 * key-down.
 */
data class ShiftState(
    val value: ShiftValue = ShiftValue.OFF,
    val pressed: Boolean = false,
    val physicallyPressed: Boolean = false,
    val downAtMs: Long? = null,
    val lastQuickReleaseAtMs: Long? = null,
    val layerLatched: Boolean = false,
)

/**
 * Everything the spec tracks about Ctrl between events.
 *
 * spec: keys-and-modifiers.md SS2, SS5.4. Ctrl has no layer latch of its own (SS5.4: "a third tap
 * on a latched Ctrl simply un-latches it").
 */
data class CtrlState(
    val oneShot: Boolean = false,
    val latched: Boolean = false,
    val latchFromNavMode: Boolean = false,
    val pressed: Boolean = false,
    val physicallyPressed: Boolean = false,
    val downAtMs: Long? = null,
    val lastReleaseAtMs: Long? = null,
)

/**
 * Everything the spec tracks about Alt between events.
 *
 * spec: keys-and-modifiers.md SS2, SS5.5, SS5.6 (the Alt layer latch mirrors Shift's).
 */
data class AltState(
    val oneShot: Boolean = false,
    val latched: Boolean = false,
    val pressed: Boolean = false,
    val physicallyPressed: Boolean = false,
    val downAtMs: Long? = null,
    val lastReleaseAtMs: Long? = null,
    val layerLatched: Boolean = false,
    val lastQuickReleaseAtMs: Long? = null,
)

/**
 * The Sym key's own session bookkeeping: whether a page toggle is still pending and whether a
 * chord has already claimed this press.
 *
 * spec: keys-and-modifiers.md SS4.1 ("toggle pending", "no chord used"); layers-sym-alt.md SS5.2,
 * SS5.3.
 */
data class SymSessionState(
    val togglePending: Boolean = false,
    val chordUsed: Boolean = false,
    val currentPageNumber: Int = 0,
    val assistantArmedAtMs: Long? = null,
    val assistantFired: Boolean = false,
)

/**
 * A snapshot of the three logical modifiers, taken at the down of whichever of Shift, Ctrl or Alt
 * is not a latched-layer tap-off, and restored on release if that press turns out to be an
 * "intentional hold" with nothing typed. spec: keys-and-modifiers.md SS5.2, SS5.3, SS5.4.
 */
data class ModifierSnapshot(
    val shiftValue: ShiftValue,
    val ctrlOneShot: Boolean,
    val ctrlLatched: Boolean,
    val ctrlLatchFromNavMode: Boolean,
    val altOneShot: Boolean,
    val altLatched: Boolean,
)

/**
 * Shared hold bookkeeping used by the intentional-hold rule for Shift, Ctrl and Alt alike.
 *
 * spec: keys-and-modifiers.md SS5.2: a single snapshot slot and a single pair of "other key
 * during hold" / "status-bar interaction during hold" flags are armed by whichever of Shift,
 * Ctrl or Alt went down last, and read back by whichever of them comes up next.
 */
data class HoldBookkeeping(
    val snapshot: ModifierSnapshot? = null,
    val heldKey: ModifierKey? = null,
    val otherKeyDuringHold: Boolean = false,
    val externalInteractionDuringHold: Boolean = false,
)

/**
 * The Fn burst counter. spec: keys-and-modifiers.md SS3.3.
 */
data class FnBurstState(
    val count: Int = 0,
    val blocked: Boolean = false,
    val lastEventAtMs: Long? = null,
)

/**
 * The complete state the modifier and Sym-session machinery carries from one [KeyStroke] to the
 * next. This is the "state" half of the `(state, input) -> (state, output)` functions in
 * [ModifierMachine].
 */
data class ModifierState(
    val shift: ShiftState = ShiftState(),
    val ctrl: CtrlState = CtrlState(),
    val alt: AltState = AltState(),
    val sym: SymSessionState = SymSessionState(),
    val fnBurst: FnBurstState = FnBurstState(),
    val holdBookkeeping: HoldBookkeeping = HoldBookkeeping(),
    val lastKeyWasModifier: ModifierKey? = null,
) {
    /** spec: keys-and-modifiers.md SS7.2 ("Alt is active when the event has Alt meta, or Alt is latched, or Alt is one-shot"). */
    fun isAltActive(metaAlt: Boolean): Boolean = metaAlt || alt.latched || alt.oneShot

    /** spec: keys-and-modifiers.md SS7.3 ("Ctrl resolution runs when the event has Ctrl meta, or Ctrl is latched, or Ctrl is one-shot"). */
    fun isCtrlActive(metaCtrl: Boolean): Boolean = metaCtrl || ctrl.latched || ctrl.oneShot

    /** spec: keys-and-modifiers.md SS7.3 ("physical combo = Ctrl meta on the event or Ctrl pressed/physically pressed"). */
    fun isCtrlPhysicalCombo(metaCtrl: Boolean): Boolean = metaCtrl || ctrl.pressed || ctrl.physicallyPressed

    /**
     * spec: keys-and-modifiers.md SS7.4 step 1 ("uppercase when Shift is one-shot, or the Shift
     * layer latch is set, or caps lock is on and Shift meta is not set, or Shift meta is set.
     * (Caps lock with Shift held gives lowercase.)"). Caps lock is the one case where holding
     * Shift flips the result to lowercase instead of adding to it, so it is handled as an
     * override rather than one more disjunct.
     */
    fun shiftForcesUppercase(metaShift: Boolean): Boolean =
        if (shift.value == ShiftValue.CAPS) !metaShift else shift.value == ShiftValue.ONE_SHOT || shift.layerLatched || metaShift
}

/**
 * The tunable numbers and switches the modifier and Sym-session machinery reads.
 *
 * spec: keys-and-modifiers.md SS2 (the two timing constants), SS18 (the settings table);
 * layers-sym-alt.md SS12. `shift_tap_latches`, `ctrl_tap_latches`, `alt_tap_latches` and the two
 * `*_latch_stays_on_space` preferences are dropped for 3.0 (SS22 Keep/Drop: "no UI since the
 * Modifiers screen was deleted; defaults are the only tested path"), so this type has no field
 * for them; the transitions below always take the shipped-default branch.
 */
data class ModifierSettings(
    val doubleTapWindowMs: Long = 500,
    val holdThresholdMs: Long = 300,
    val clearAltOnSpace: Boolean = true,
    val navModeCtrlHoldEnabled: Boolean = false,
    val layoutAwareCtrlShortcuts: Boolean = false,
    val altCtrlSpeechShortcutEnabled: Boolean = true,
    val fnLongPressSpeechEnabled: Boolean = false,
    val fnBurstResetMs: Long = 200,
    val fnBurstTriggerCount: Int = 5,
    val symLongPressAssistantEnabled: Boolean = false,
    val symIsTrackpadTrigger: Boolean = false,
    val symAssistantHoldMs: Long = 600,
    val symEditShortcutsEnabled: Boolean = true,
    val symAutoCloseEnabled: Boolean = true,
)
