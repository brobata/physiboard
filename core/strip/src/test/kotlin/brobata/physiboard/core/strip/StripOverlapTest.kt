package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StripOverlapTest {
    private val listed = setOf("com.microsoft.teams")

    @Test
    fun `a listed app with a real field takes the strip's space`() {
        assertTrue(StripOverlap.appDrawsUnderStrip("com.microsoft.teams", listed, fieldIsEditable = true))
    }

    @Test
    fun `an app nobody listed keeps its suggestions`() {
        assertFalse(StripOverlap.appDrawsUnderStrip("com.google.android.apps.messaging", listed, fieldIsEditable = true))
    }

    @Test
    fun `no field means no text box to be under`() {
        assertFalse(StripOverlap.appDrawsUnderStrip("com.microsoft.teams", listed, fieldIsEditable = false))
    }

    @Test
    fun `an app that reports no package matches nothing`() {
        assertFalse(StripOverlap.appDrawsUnderStrip(null, listed, fieldIsEditable = true))
    }

    @Test
    fun `the footprint collapses for such an app even with suggestions to show`() {
        assertEquals(
            StripFootprint.SHOWN,
            StripVisibility.footprint(
                mode = StripVisibilityMode.ALWAYS, apps = emptySet(), packageName = "com.microsoft.teams",
                symPageOpen = false, navModeLatched = false, fieldOffersSuggestions = true,
            ),
        )
        assertEquals(
            StripFootprint.COLLAPSED,
            StripVisibility.footprint(
                mode = StripVisibilityMode.ALWAYS, apps = emptySet(), packageName = "com.microsoft.teams",
                symPageOpen = false, navModeLatched = false, fieldOffersSuggestions = true,
                fieldDrawsUnderStrip = true,
            ),
        )
    }

    @Test
    fun `a Sym page cannot reopen the strip over the app's own message box`() {
        assertEquals(
            StripFootprint.COLLAPSED,
            StripVisibility.footprint(
                mode = StripVisibilityMode.ALWAYS, apps = emptySet(), packageName = "com.microsoft.teams",
                symPageOpen = true, navModeLatched = false, fieldDrawsUnderStrip = true,
            ),
        )
    }
}
