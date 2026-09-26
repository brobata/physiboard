package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: status-bar.md SS5.6. */
class SuggestionAccessibilityTest {

    @Test
    fun `SS5_6 an announcement is scheduled only when enabled and at least one slot is non-blank`() {
        assertFalse(SuggestionAccessibility.shouldSchedule(liveAnnouncementsEnabled = false, slotTexts = listOf("a", "", "")))
        assertFalse(SuggestionAccessibility.shouldSchedule(liveAnnouncementsEnabled = true, slotTexts = listOf("", "", "")))
        assertTrue(SuggestionAccessibility.shouldSchedule(liveAnnouncementsEnabled = true, slotTexts = listOf("", "a", "")))
    }

    @Test
    fun `SS5_6 the announcement joins the non-blank slots with a comma and space`() {
        assertEquals("a, b", SuggestionAccessibility.announcementText(listOf("a", "b", "")))
        assertEquals("word", SuggestionAccessibility.announcementText(listOf("", "word", "")))
    }

    @Test
    fun `SS5_6 the delay never goes negative`() {
        assertEquals(500, SuggestionAccessibility.delayMs(500))
        assertEquals(0, SuggestionAccessibility.delayMs(-40))
    }

    @Test
    fun `SS5_6 repeating the same announcement is a no-op`() {
        assertFalse(SuggestionAccessibility.isNewAnnouncement("a, b", lastAnnounced = "a, b"))
        assertTrue(SuggestionAccessibility.isNewAnnouncement("a, b", lastAnnounced = "a"))
        assertTrue(SuggestionAccessibility.isNewAnnouncement("a, b", lastAnnounced = null))
    }

    @Test
    fun `SS5_6 the language button's state description names the language and the layout`() {
        assertEquals("Language EN, layout qwerty", SuggestionAccessibility.languageStateDescription("EN", "qwerty"))
    }
}
