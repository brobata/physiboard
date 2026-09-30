package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StripOverlapTest {
    @Test
    fun `a caret below the strip's top edge means the app drew its box under the strip`() {
        // Teams: a 1200px-tall screen, the strip's band starting at 1090, the compose box's caret
        // reported at 1150 -- inside the band (2026-09-29).
        assertTrue(StripOverlap.fieldDrawsUnderStrip(caretBottomPx = 1150f, stripTopPx = 1090, stripRendered = true))
    }

    @Test
    fun `a caret above the strip is an app that made room, and the strip stays`() {
        assertFalse(StripOverlap.fieldDrawsUnderStrip(caretBottomPx = 600f, stripTopPx = 1090, stripRendered = true))
        assertFalse(StripOverlap.fieldDrawsUnderStrip(caretBottomPx = 1090f, stripTopPx = 1090, stripRendered = true))
    }

    @Test
    fun `a caret the editor could not report is not evidence of anything`() {
        assertFalse(StripOverlap.fieldDrawsUnderStrip(Float.NaN, stripTopPx = 1090, stripRendered = true))
        assertFalse(StripOverlap.fieldDrawsUnderStrip(Float.POSITIVE_INFINITY, stripTopPx = 1090, stripRendered = true))
    }

    @Test
    fun `a strip that is not on screen is covering nothing`() {
        assertFalse(StripOverlap.fieldDrawsUnderStrip(caretBottomPx = 1150f, stripTopPx = 1090, stripRendered = false))
    }

    @Test
    fun `the footprint collapses for a field drawn under the strip, even with suggestions to show`() {
        val shown = StripVisibility.footprint(
            mode = StripVisibilityMode.ALWAYS, apps = emptySet(), packageName = "com.microsoft.teams",
            symPageOpen = false, navModeLatched = false, fieldOffersSuggestions = true,
        )
        assertTrue(shown == StripFootprint.SHOWN)
        val under = StripVisibility.footprint(
            mode = StripVisibilityMode.ALWAYS, apps = emptySet(), packageName = "com.microsoft.teams",
            symPageOpen = false, navModeLatched = false, fieldOffersSuggestions = true,
            fieldDrawsUnderStrip = true,
        )
        assertTrue(under == StripFootprint.COLLAPSED)
    }

    @Test
    fun `a Sym page cannot reopen the strip over the app's own message box`() {
        val under = StripVisibility.footprint(
            mode = StripVisibilityMode.ALWAYS, apps = emptySet(), packageName = "com.microsoft.teams",
            symPageOpen = true, navModeLatched = false, fieldDrawsUnderStrip = true,
        )
        assertTrue(under == StripFootprint.COLLAPSED)
    }
}
