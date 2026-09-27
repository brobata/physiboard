package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: status-bar.md SS3.5, SS17, SS18 rows T1 to T7 (T5 and T7 as far as they are pure decisions). */
class StripVisibilityTest {

    private val messages = "com.google.android.apps.messaging"

    @Test
    fun `T1 ALWAYS shows for any package and for none`() {
        assertTrue(StripVisibility.isShownForApp(StripVisibilityMode.ALWAYS, emptySet(), "any"))
        assertTrue(StripVisibility.isShownForApp(StripVisibilityMode.ALWAYS, emptySet(), null))
    }

    @Test
    fun `T2 NEVER hides even a listed package`() {
        assertFalse(StripVisibility.isShownForApp(StripVisibilityMode.NEVER, setOf("a"), "a"))
    }

    @Test
    fun `T3 APPS shows a listed package`() {
        assertTrue(StripVisibility.isShownForApp(StripVisibilityMode.APPS, setOf(messages), messages))
    }

    @Test
    fun `T4 APPS hides an unlisted package and a null package`() {
        assertFalse(StripVisibility.isShownForApp(StripVisibilityMode.APPS, setOf(messages), "com.android.launcher3"))
        assertFalse(StripVisibility.isShownForApp(StripVisibilityMode.APPS, setOf(messages), null))
    }

    @Test
    fun `T5 the seeded list holds WhatsApp and Gmail and removing one leaves the other`() {
        assertTrue("com.whatsapp" in StripVisibility.SEEDED_APPS)
        assertTrue("com.google.android.gm" in StripVisibility.SEEDED_APPS)
        assertEquals(20, StripVisibility.SEEDED_APPS.size, "SS14: seeded status bar apps 20")
        val without = StripVisibility.SEEDED_APPS - "com.whatsapp"
        assertFalse(StripVisibility.isShownForApp(StripVisibilityMode.APPS, without, "com.whatsapp"))
        assertTrue(StripVisibility.isShownForApp(StripVisibilityMode.APPS, without, "com.google.android.gm"))
    }

    @Test
    fun `T6 the legacy boolean decides only when the mode is absent`() {
        assertEquals(StripVisibilityMode.ALWAYS, StripVisibility.resolveMode(null, legacyShowStatusBar = true))
        assertEquals(StripVisibilityMode.NEVER, StripVisibility.resolveMode(null, legacyShowStatusBar = false))
        assertEquals(StripVisibilityMode.ALWAYS, StripVisibility.resolveMode(null, legacyShowStatusBar = null))
        assertEquals(StripVisibilityMode.APPS, StripVisibility.resolveMode("APPS", legacyShowStatusBar = false), "a stored mode beats the boolean")
        assertEquals(StripVisibilityMode.ALWAYS, StripVisibility.resolveMode("bogus", legacyShowStatusBar = null), "silence is not a request to hide")
    }

    @Test
    fun `T7 APPS with a added shows a, removed hides a, and the boolean written for NEVER is false`() {
        val added = setOf("a")
        assertTrue(StripVisibility.isShownForApp(StripVisibilityMode.APPS, added, "a"))
        assertFalse(StripVisibility.isShownForApp(StripVisibilityMode.APPS, added - "a", "a"))
        assertFalse(StripVisibility.legacyBooleanFor(StripVisibilityMode.NEVER))
        assertTrue(StripVisibility.legacyBooleanFor(StripVisibilityMode.APPS))
        assertTrue(StripVisibility.legacyBooleanFor(StripVisibilityMode.ALWAYS))
    }

    @Test
    fun `SS17 a Sym page shows on a strip the mode hides, and it collapses again when the page closes`() {
        val hidden = StripVisibility.footprint(StripVisibilityMode.NEVER, emptySet(), "a", symPageOpen = false, navModeLatched = false)
        assertEquals(StripFootprint.COLLAPSED, hidden)
        val withPage = StripVisibility.footprint(StripVisibilityMode.NEVER, emptySet(), "a", symPageOpen = true, navModeLatched = false)
        assertEquals(StripFootprint.SHOWN, withPage)
    }

    @Test
    fun `SS3_4 nav mode latched collapses the strip while the window stays`() {
        val footprint = StripVisibility.footprint(StripVisibilityMode.ALWAYS, emptySet(), "a", symPageOpen = false, navModeLatched = true)
        assertEquals(StripFootprint.COLLAPSED, footprint)
    }

    @Test
    fun `SS17 mode APPS with no field focused is hidden`() {
        val footprint = StripVisibility.footprint(StripVisibilityMode.APPS, StripVisibility.SEEDED_APPS, null, symPageOpen = false, navModeLatched = false)
        assertEquals(StripFootprint.COLLAPSED, footprint)
    }

    /** The maintainer's terminal: an empty black band across the bottom, because no slot can ever fill there. */
    @Test
    fun `a field that offers no suggestions collapses the strip, unless a Sym page is open`() {
        fun call(offers: Boolean, sym: Boolean = false, hide: Boolean = true) = StripVisibility.footprint(
            StripVisibilityMode.ALWAYS, emptySet(), "com.android.chrome",
            symPageOpen = sym, navModeLatched = false, fieldOffersSuggestions = offers, hideWhereNothingToSuggest = hide,
        )
        assertEquals(StripFootprint.COLLAPSED, call(offers = false))
        assertEquals(StripFootprint.SHOWN, call(offers = false, sym = true))
        assertEquals(StripFootprint.SHOWN, call(offers = false, hide = false))
        assertEquals(StripFootprint.SHOWN, call(offers = true))
    }
}
