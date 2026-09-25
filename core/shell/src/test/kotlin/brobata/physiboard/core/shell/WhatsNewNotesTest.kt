package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: app-shell.md SS5.2, SS3, SS29 T12-T17, T25. */
class WhatsNewNotesTest {

    @Test
    fun `T12 a bold-lead bullet splits into title and body`() {
        val notes = WhatsNewNotes.parse("- **Settings had two of several things.** The Extras button opened the same page as All settings.")
        assertEquals(1, notes.size)
        assertEquals("Settings had two of several things", notes[0].title)
        assertEquals("The Extras button opened the same page as All settings.", notes[0].body)
    }

    @Test
    fun `T13 a bold lead followed by an em dash keeps the dash out of the body`() {
        val notes = WhatsNewNotes.parse("- **Turn the accent row off again** — it had no switch.")
        assertEquals("Turn the accent row off again", notes[0].title)
        assertEquals("it had no switch.", notes[0].body)
    }

    @Test
    fun `T14 an inner em dash inside the body survives`() {
        val notes = WhatsNewNotes.parse("- **Accent row** — Show variations is back — off stays off, including after a reset.")
        assertEquals("Accent row", notes[0].title)
        assertEquals("Show variations is back — off stays off, including after a reset.", notes[0].body)
    }

    @Test
    fun `T15 a preamble becomes one untitled note ahead of the bullets`() {
        val notes = WhatsNewNotes.parse("Line one.\nLine two.\n\n- **First bullet** body")
        assertEquals(2, notes.size)
        assertEquals("", notes[0].title)
        assertEquals("Line one. Line two.", notes[0].body)
        assertEquals("First bullet", notes[1].title)
        assertEquals("body", notes[1].body)
    }

    @Test
    fun `T16 backticks and code are stripped, and 2 star 3 survives`() {
        val notes = WhatsNewNotes.parse("- **Sym+C / Sym+V.** Use `Sym+A` for *select all*; 2*3 stays.")
        assertEquals("Sym+C / Sym+V", notes[0].title)
        assertEquals("Use Sym+A for select all; 2*3 stays.", notes[0].body)
    }

    @Test
    fun `T17 blank input yields no notes`() {
        assertTrue(WhatsNewNotes.parse("\n\n").isEmpty())
        assertTrue(WhatsNewNotes.parse("").isEmpty())
    }

    @Test
    fun `T25 the what's-new card is due only once per version change while tutorial is complete`() {
        assertEquals(false, WhatsNewDue.isDue(tutorialCompleted = false, lastSeenWhatsNewVersion = "2.0.6", currentVersionName = "2.0.7"))
        assertEquals(true, WhatsNewDue.isDue(tutorialCompleted = true, lastSeenWhatsNewVersion = "2.0.6", currentVersionName = "2.0.7"))
        assertEquals(false, WhatsNewDue.isDue(tutorialCompleted = true, lastSeenWhatsNewVersion = "2.0.7", currentVersionName = "2.0.7"))
        assertEquals(false, WhatsNewDue.isDue(tutorialCompleted = true, lastSeenWhatsNewVersion = "2.0.6", currentVersionName = ""))
    }
}
