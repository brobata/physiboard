package brobata.physiboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewNotesTest {

    @Test
    fun boldLeadWithTrailingPeriodBecomesTitle() {
        val notes = WhatsNewNotes.parse(
            """
            - **Settings had two of several things.** The Extras button opened the same
              page as All settings.
            """.trimIndent()
        )
        assertEquals(1, notes.size)
        assertEquals("Settings had two of several things", notes[0].title)
        assertEquals("The Extras button opened the same page as All settings.", notes[0].body)
    }

    @Test
    fun emDashSeparatorIsAccepted() {
        val notes = WhatsNewNotes.parse("- **Turn the accent row off again** — it had no switch.")
        assertEquals("Turn the accent row off again", notes[0].title)
        assertEquals("it had no switch.", notes[0].body)
    }

    @Test
    fun emDashInsideTheBodyDoesNotSplitIt() {
        val notes = WhatsNewNotes.parse(
            "- **Accent row.** *Show variations* is back — off stays off, including after a reset."
        )
        assertEquals("Accent row", notes[0].title)
        assertEquals("Show variations is back — off stays off, including after a reset.", notes[0].body)
    }

    @Test
    fun preambleParagraphBecomesUntitledFirstEntry() {
        val notes = WhatsNewNotes.parse(
            """
            **You will have to pick PhysiBoard again.** Android treats a renamed keyboard
            as a new one.

            - **First bullet.** Body.
            """.trimIndent()
        )
        assertEquals(2, notes.size)
        assertEquals("", notes[0].title)
        assertEquals(
            "You will have to pick PhysiBoard again. Android treats a renamed keyboard as a new one.",
            notes[0].body
        )
        assertEquals("First bullet", notes[1].title)
    }

    @Test
    fun inlineMarksAreStrippedButAsterisksInProseSurvive() {
        val notes = WhatsNewNotes.parse("- **Sym+C / Sym+V.** Use `Sym+A` for *select all*; 2*3 stays.")
        assertEquals("Use Sym+A for select all; 2*3 stays.", notes[0].body)
    }

    @Test
    fun emptyInputYieldsNoNotes() {
        assertEquals(emptyList<WhatsNewNotes.Note>(), WhatsNewNotes.parse("\n\n"))
    }
}
