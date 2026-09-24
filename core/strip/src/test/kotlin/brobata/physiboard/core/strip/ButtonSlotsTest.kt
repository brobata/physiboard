package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: status-bar.md SS6.3, SS17, SS18 rows T26 to T29 (the mirror keys of T26/T27 are SS19-dropped, so only the list rules remain). */
class ButtonSlotsTest {

    @Test
    fun `T26 an unknown id reads as none in place and writes back as none`() {
        val slots = ButtonSlots.fromIds(left = listOf("clipboard", "bogus", "none"), right = null)
        assertEquals(listOf(StripButton.CLIPBOARD, StripButton.NONE, StripButton.NONE), slots.left)
        assertEquals(listOf("clipboard", "none", "none"), slots.ids(StripSide.LEFT))
        assertEquals(listOf(StripButton.CLIPBOARD), slots.drawn(StripSide.LEFT))
    }

    @Test
    fun `T27 an absent list falls back to that side's default`() {
        val slots = ButtonSlots.fromIds(left = null, right = listOf("microphone", "emoji"))
        assertEquals(ButtonSlots.DEFAULT.left, slots.left)
        assertEquals(listOf(StripButton.MICROPHONE, StripButton.EMOJI), slots.right)
    }

    @Test
    fun `T28 choosing the microphone for R1 clears it from the hidden R2`() {
        val slots = ButtonSlots(left = listOf(StripButton.HAMBURGER), right = listOf(StripButton.EMOJI, StripButton.MICROPHONE))
        val chosen = slots.choose(StripSide.RIGHT, 0, StripButton.MICROPHONE)
        assertEquals(listOf(StripButton.MICROPHONE, StripButton.NONE), chosen.right)
        assertEquals(listOf(StripButton.HAMBURGER), chosen.left)
    }

    @Test
    fun `SS6_3 choosing a button clears it from the other side too`() {
        val slots = ButtonSlots(left = listOf(StripButton.MICROPHONE), right = listOf(StripButton.EMOJI))
        val chosen = slots.choose(StripSide.RIGHT, 0, StripButton.MICROPHONE)
        assertEquals(listOf(StripButton.NONE), chosen.left)
        assertEquals(listOf(StripButton.MICROPHONE), chosen.right)
    }

    @Test
    fun `SS6_3 choosing none clears nothing else and a far index grows the list`() {
        val slots = ButtonSlots(left = listOf(StripButton.CLIPBOARD), right = listOf(StripButton.MICROPHONE))
        val chosen = slots.choose(StripSide.LEFT, 2, StripButton.NONE)
        assertEquals(listOf(StripButton.CLIPBOARD, StripButton.NONE, StripButton.NONE), chosen.left)
        assertEquals(listOf(StripButton.MICROPHONE), chosen.right)
    }

    @Test
    fun `T29 reset restores hamburger left and emoji plus microphone right`() {
        assertEquals(ButtonSlots(listOf(StripButton.HAMBURGER), listOf(StripButton.EMOJI, StripButton.MICROPHONE)), ButtonSlots.reset())
    }

    @Test
    fun `SS17 fresh install and Reset disagree`() {
        assertEquals(listOf(StripButton.CLIPBOARD), ButtonSlots.FIRST_RUN_BASELINE.left)
        assertEquals(listOf(StripButton.MICROPHONE), ButtonSlots.FIRST_RUN_BASELINE.drawn(StripSide.RIGHT))
        assertEquals(listOf(StripButton.HAMBURGER), ButtonSlots.reset().left)
    }

    @Test
    fun `SS19 the dropped software keyboard mode id reads as none`() {
        assertEquals(StripButton.NONE, StripButton.fromId("software_keyboard_mode"))
        assertEquals(StripButton.NONE, StripButton.fromId(null))
    }
}
