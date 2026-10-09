package brobata.physiboard.core.keys

/**
 * One modifier reduced to the three states the status-bar icon distinguishes. spec:
 * keys-and-modifiers.md SS13.1: "locked = caps lock (Shift) or latched (Ctrl, Alt); active =
 * physically pressed or one-shot."
 */
enum class ModifierIconState { OFF, ACTIVE, LOCKED }

/**
 * The one picture the keyboard's status-bar slot shows. spec: keys-and-modifiers.md SS13.1, which
 * also gives the precedence when several states are on at once: the order of this enum, first
 * wins. [NONE] hides the icon.
 */
enum class StatusBarIcon {
    /** A dictation session exists (dictation.md SS9). */
    DICTATION,

    /** Nav mode is latched (trackpad-caret-nav.md SS5.7). */
    NAV,

    /** Ctrl latched (not by nav mode): every letter is a command until it is unlatched. */
    CTRL_LOCKED,

    /** Ctrl one-shot or physically held: the next letter is a command. */
    CTRL,

    /** Alt latched: every key types its Alt-layer character. */
    ALT_LOCKED,

    /** Alt one-shot or physically held. */
    ALT,

    /** Caps lock. */
    CAPS_LOCK,

    /** Shift one-shot or physically held. */
    SHIFT,

    /** A Sym page is open and nothing above applies. */
    SYM,

    /** Nothing to show. */
    NONE,
    ;

    /** True for the states that only exist because of a modifier, Sym or nav, so the keyboard-shown hold of SS13.1 is about them (dictation holds on its own). */
    val isModifierState: Boolean get() = this != NONE && this != DICTATION
}

/**
 * Picks the status-bar icon from the current modifier, Sym, nav and dictation facts. Pure so the
 * precedence gets a JVM test with no status icon API in sight (`:ime` maps the answer onto a
 * drawable and calls `InputMethodService.showStatusIcon`/`hideStatusIcon`).
 *
 * spec: keys-and-modifiers.md SS13.1. Precedence: dictation, nav, Ctrl, Alt, Shift, Sym; within a
 * modifier, locked before active. Ctrl outranks Alt and Alt outranks Shift because that is the
 * order of surprise for the next key: Ctrl turns a letter into a command, Alt into a symbol, Shift
 * only changes its case. Sym comes last because an open Sym page is on screen anyway.
 */
object StatusBarModifierIcon {

    fun choose(
        shift: ModifierIconState,
        ctrl: ModifierIconState,
        alt: ModifierIconState,
        symPageOpen: Boolean,
        navModeActive: Boolean,
        dictationListening: Boolean = false,
    ): StatusBarIcon = when {
        dictationListening -> StatusBarIcon.DICTATION
        navModeActive -> StatusBarIcon.NAV
        ctrl == ModifierIconState.LOCKED -> StatusBarIcon.CTRL_LOCKED
        ctrl == ModifierIconState.ACTIVE -> StatusBarIcon.CTRL
        alt == ModifierIconState.LOCKED -> StatusBarIcon.ALT_LOCKED
        alt == ModifierIconState.ACTIVE -> StatusBarIcon.ALT
        shift == ModifierIconState.LOCKED -> StatusBarIcon.CAPS_LOCK
        shift == ModifierIconState.ACTIVE -> StatusBarIcon.SHIFT
        symPageOpen -> StatusBarIcon.SYM
        else -> StatusBarIcon.NONE
    }

    /** spec SS13.1: "active = physically pressed or one-shot"; "locked = caps lock (Shift) or latched (Ctrl, Alt)". Caps lock wins over a stray one-shot/held fact for the same modifier, since [ShiftValue.CAPS] already excludes [ShiftValue.ONE_SHOT]. */
    fun shiftState(value: ShiftValue, physicallyPressed: Boolean): ModifierIconState = when {
        value == ShiftValue.CAPS -> ModifierIconState.LOCKED
        value == ShiftValue.ONE_SHOT || physicallyPressed -> ModifierIconState.ACTIVE
        else -> ModifierIconState.OFF
    }

    /** spec SS13.1, applied to Ctrl or Alt: locked (latched) beats active (one-shot or held), which beats off. A latch created by nav mode is never passed here (that state's icon is [StatusBarIcon.NAV]). */
    fun latchableState(latched: Boolean, oneShot: Boolean, physicallyPressed: Boolean): ModifierIconState = when {
        latched -> ModifierIconState.LOCKED
        oneShot || physicallyPressed -> ModifierIconState.ACTIVE
        else -> ModifierIconState.OFF
    }
}
