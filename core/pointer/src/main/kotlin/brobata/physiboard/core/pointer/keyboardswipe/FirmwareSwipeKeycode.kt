package brobata.physiboard.core.pointer.keyboardswipe

/**
 * spec: trackpad-caret-nav.md SS3.6. The vendor firmware can deliver a keyboard-surface swipe as
 * a plain key event (keycode 322 or 404, D5); this module has no `android.*` import so it cannot
 * read Android's `KeyEvent` itself, and the two keycode constants live in `:device:titan`
 * (`HardwareCodes.VendorKeyCodes`, spec D10) since they are a hardware fact, not a layer decision.
 * `:ime` passes the raw integer keycode from the event it already has in hand.
 */
enum class FirmwareSwipeResult {
    /** Not one of the two firmware swipe keycodes; the caller should ignore this decision entirely. */
    NOT_RECOGNIZED,

    /** spec SS3.6: "the last word before the cursor is deleted; consumed when something was deleted, otherwise falls through". */
    DELETE_LAST_WORD,

    /** spec SS3.6: "consumed and ignored, recorded in the key log as swipe_to_delete_ignored_<provider>". */
    CONSUMED_IGNORED,
}

object FirmwareSwipeKeycode {

    /** spec SS3.6's table, given the two settings and whether [keycode] is one of the firmware's swipe keycodes. */
    fun decide(keycode: Int, isFirmwareSwipeKeycode: Boolean, swipeToDelete: Boolean, provider: SwipeToDeleteProvider): FirmwareSwipeResult {
        if (!isFirmwareSwipeKeycode) return FirmwareSwipeResult.NOT_RECOGNIZED
        return if (swipeToDelete && provider == SwipeToDeleteProvider.TITAN2_KEYCODE) {
            FirmwareSwipeResult.DELETE_LAST_WORD
        } else {
            FirmwareSwipeResult.CONSUMED_IGNORED
        }
    }
}
