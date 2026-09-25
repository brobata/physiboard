package brobata.physiboard.core.pointer.trackpad

/** What the hint pill says, or that it is not drawn. spec: trackpad-caret-nav.md SS2.5. */
enum class HintPill { HIDDEN, CURSOR, CURSOR_TAP_TO_EXIT, SELECT }

/**
 * The hint pill decision. spec: trackpad-caret-nav.md SS2.5: "With `screen_trackpad_show_hint`
 * on (default true) a small pill is drawn inside the overlay"; its text follows Shift (select)
 * and, in sticky mode, adds the tap-to-exit reminder since "Tap on the pill: sticky mode only:
 * closes the trackpad. In hold mode the pill is inert."
 */
object TrackpadHint {
    fun pill(showHint: Boolean, shiftActive: Boolean, sticky: Boolean): HintPill = when {
        !showHint -> HintPill.HIDDEN
        shiftActive -> HintPill.SELECT
        sticky -> HintPill.CURSOR_TAP_TO_EXIT
        else -> HintPill.CURSOR
    }
}
