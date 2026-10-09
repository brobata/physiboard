package brobata.physiboard.ime

import brobata.physiboard.core.keys.StatusBarIcon

/** Who is keeping the keyboard "shown" for the system. keys-and-modifiers.md SS13.1, dictation.md SS6.8. */
internal enum class ShownHoldOwner {
    /** A dictation session: without the visible binding the microphone is silenced (D22). */
    DICTATION,

    /** A modifier, Sym or nav state on the status bar icon (keys-and-modifiers.md SS13.1). */
    MODIFIER_ICON,
}

/**
 * The keyboard-shown hold, shared by its owners and counted per owner, so one owner letting go
 * never drops the hold another still needs: a modifier clearing in the middle of a dictation
 * session must not cost that session its microphone, and a session ending with caps lock on must
 * not take the caps-lock icon with it. Pure: [KeyboardSession] makes the real show and hide calls
 * from what these return. Not thread-safe; the main thread owns it.
 */
internal class KeyboardShownHold {
    private val owners = mutableSetOf<ShownHoldOwner>()

    val isHeld: Boolean get() = owners.isNotEmpty()

    fun holds(owner: ShownHoldOwner): Boolean = owner in owners

    /** Adds [owner]; true when it did not already hold. Acquiring twice is the same as once. */
    fun acquire(owner: ShownHoldOwner): Boolean = owners.add(owner)

    /**
     * Removes [owner]. True only when it held and was the last owner, which is the one case the
     * caller may tell the system to hide; false for an owner that did not hold, or when another
     * owner still does.
     */
    fun release(owner: ShownHoldOwner): Boolean = owners.remove(owner) && owners.isEmpty()

    /** The service is going away; nothing is held any more. */
    fun clear() = owners.clear()
}

/**
 * When the status bar icon's state should take or give up its share of [KeyboardShownHold].
 * keys-and-modifiers.md SS13.1.
 *
 * - A modifier, Sym or nav icon takes the hold at once (the icon has to appear with the key).
 * - Its going away schedules the release [RELEASE_DELAY_MS] later rather than releasing now, so
 *   typing a capital with Shift (on, off, on, off) does not ask the system to show and hide the
 *   keyboard on every letter.
 * - Back releases the hold at once and leaves it released for the icon that was showing, so a
 *   Back the system may spend on closing the keyboard is spent at most once per state; any other
 *   icon, or the next field, takes it again.
 *
 * Pure; [KeyboardSession] carries out the decisions.
 */
internal class ModifierHoldPolicy {
    enum class Decision {
        /** Take the hold and ask the system to show. */
        ACQUIRE,
        /** Already held: ask the system to show again (a new field). */
        REASSERT,
        /** Drop the hold [RELEASE_DELAY_MS] from now unless something cancels it. */
        SCHEDULE_RELEASE,
        /** A pending release is no longer wanted. */
        CANCEL_RELEASE,
        /** Drop the hold now. */
        RELEASE,
        NONE,
    }

    var held: Boolean = false
        private set

    /** The icon Back released the hold under; the hold is not taken again while that same icon shows. */
    private var suppressedFor: StatusBarIcon? = null

    /** The icon changed to [icon]. */
    fun onIcon(icon: StatusBarIcon): Decision {
        if (suppressedFor != null && icon != suppressedFor) suppressedFor = null
        val wanted = icon.isModifierState
        return when {
            wanted && held -> Decision.CANCEL_RELEASE
            wanted && suppressedFor == null -> {
                held = true
                Decision.ACQUIRE
            }
            !wanted && held -> Decision.SCHEDULE_RELEASE
            else -> Decision.NONE
        }
    }

    /** The scheduled release fell due; [icon] is what shows now. */
    fun onReleaseDue(icon: StatusBarIcon): Decision {
        if (!held || icon.isModifierState) return Decision.NONE
        held = false
        return Decision.RELEASE
    }

    /** Back went down while [icon] shows. */
    fun onBack(icon: StatusBarIcon): Decision {
        if (!held) return Decision.NONE
        held = false
        suppressedFor = icon
        return Decision.RELEASE
    }

    /** A field started with [icon] showing: Back's suppression ends, a hold is asserted for the new field. */
    fun onFieldStarted(icon: StatusBarIcon): Decision {
        suppressedFor = null
        if (held) return if (icon.isModifierState) Decision.REASSERT else Decision.SCHEDULE_RELEASE
        return onIcon(icon)
    }

    /** The service is going away. */
    fun reset() {
        held = false
        suppressedFor = null
    }

    companion object {
        const val RELEASE_DELAY_MS = 750L
    }
}
