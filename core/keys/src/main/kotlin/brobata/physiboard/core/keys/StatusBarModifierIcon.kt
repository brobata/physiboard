package brobata.physiboard.core.keys

/**
 * One modifier reduced to the three states the status-bar icon distinguishes. spec:
 * keys-and-modifiers.md SS13.1: "Each of Shift, Ctrl, Alt is reduced to off / active / locked:
 * locked = caps lock (Shift) or latched (Ctrl, Alt); active = physically pressed or one-shot."
 */
enum class ModifierIconState { OFF, ACTIVE, LOCKED }

/**
 * Which of the status bar's icon slots applies right now. spec: keys-and-modifiers.md SS13.1
 * ("The 26 non-empty combinations select one of 26 icons... all-off shows no icon, unless a Sym
 * page is open, in which case a Sym icon is shown"), trackpad-caret-nav.md SS5.7 ("The same
 * icon slot otherwise shows the modifier icon... nav mode wins") and dictation.md SS9 (a
 * listening session wins over all of them).
 */
sealed class StatusBarIcon {
    /** All three modifiers off and no Sym page open: `InputMethodService.hideStatusIcon`. */
    data object None : StatusBarIcon()

    /** Dictation is listening (dictation.md SS9): the one sign that needs no keyboard window and no overlay permission; wins the slot. */
    data object Dictation : StatusBarIcon()

    /** Nav mode is latched: wins over every other state this slot could show. */
    data object Nav : StatusBarIcon()

    /** No modifier is active or locked, but a Sym page is open. */
    data object Sym : StatusBarIcon()

    /** At least one modifier is active or locked and nav mode is not on. */
    data class Modifiers(val shift: ModifierIconState, val ctrl: ModifierIconState, val alt: ModifierIconState) : StatusBarIcon()
}

/**
 * Picks the one status-bar icon slot from the current modifier/Sym/nav facts. Pure so the 26
 * modifier combinations, the Sym fallback and the nav override each get a JVM test with no status
 * icon API in sight (`:ime` only maps whatever this returns onto a drawable resource id and calls
 * `InputMethodService.showStatusIcon`/`hideStatusIcon`).
 *
 * spec: keys-and-modifiers.md SS13.1.
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
        dictationListening -> StatusBarIcon.Dictation
        navModeActive -> StatusBarIcon.Nav
        shift != ModifierIconState.OFF || ctrl != ModifierIconState.OFF || alt != ModifierIconState.OFF -> StatusBarIcon.Modifiers(shift, ctrl, alt)
        symPageOpen -> StatusBarIcon.Sym
        else -> StatusBarIcon.None
    }

    /** spec SS13.1: "active = physically pressed or one-shot"; "locked = caps lock (Shift) or latched (Ctrl, Alt)". Caps lock wins over a stray one-shot/held fact for the same modifier, since [ShiftValue.CAPS] already excludes [ShiftValue.ONE_SHOT]. */
    fun shiftState(value: ShiftValue, physicallyPressed: Boolean): ModifierIconState = when {
        value == ShiftValue.CAPS -> ModifierIconState.LOCKED
        value == ShiftValue.ONE_SHOT || physicallyPressed -> ModifierIconState.ACTIVE
        else -> ModifierIconState.OFF
    }

    /** spec SS13.1, applied to Ctrl or Alt: locked (latched) beats active (one-shot or held), which beats off. A latch created by nav mode is never passed here (SS13.1's icon for that state is [StatusBarIcon.Nav], not a locked Ctrl). */
    fun latchableState(latched: Boolean, oneShot: Boolean, physicallyPressed: Boolean): ModifierIconState = when {
        latched -> ModifierIconState.LOCKED
        oneShot || physicallyPressed -> ModifierIconState.ACTIVE
        else -> ModifierIconState.OFF
    }
}
