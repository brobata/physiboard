package brobata.physiboard.core.pointer.trackpad

import brobata.physiboard.core.pointer.caret.CursorUpdateRequestPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: trackpad-caret-nav.md SS2.5 (the hint pill and its setting) and SS4.7 (the shared cursor-update switch). */
class TrackpadHintTest {

    @Test
    fun `screen_trackpad_show_hint off hides the pill whatever the mode`() {
        assertEquals(HintPill.HIDDEN, TrackpadHint.pill(showHint = false, shiftActive = false, sticky = false))
        assertEquals(HintPill.HIDDEN, TrackpadHint.pill(showHint = false, shiftActive = true, sticky = true))
    }

    @Test
    fun `with the hint on, Shift says select, sticky adds tap to exit, hold says cursor`() {
        assertEquals(HintPill.SELECT, TrackpadHint.pill(showHint = true, shiftActive = true, sticky = true))
        assertEquals(HintPill.CURSOR_TAP_TO_EXIT, TrackpadHint.pill(showHint = true, shiftActive = false, sticky = true))
        assertEquals(HintPill.CURSOR, TrackpadHint.pill(showHint = true, shiftActive = false, sticky = false))
    }

    @Test
    fun `cursor reports are wanted while the badge or the emoji search needs them, and by neither alone off`() {
        assertTrue(CursorUpdateRequestPolicy.wantsReports(caretBadgeEnabled = true, emojiSearchNeedsCaret = false))
        assertTrue(CursorUpdateRequestPolicy.wantsReports(caretBadgeEnabled = false, emojiSearchNeedsCaret = true))
        assertFalse(CursorUpdateRequestPolicy.wantsReports(caretBadgeEnabled = false, emojiSearchNeedsCaret = false))
    }
}
