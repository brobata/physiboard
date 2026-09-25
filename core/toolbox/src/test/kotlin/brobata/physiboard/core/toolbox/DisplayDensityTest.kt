package brobata.physiboard.core.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayDensityTest {
    @Test
    fun `T17 a reading with no override reports the physical value as current`() {
        val reading = DisplayDensity.parse("Physical density: 300")
        assertEquals(DensityReading(300, 300, overridden = false), reading)
    }

    @Test
    fun `T18 a reading with an override reports it as current`() {
        val reading = DisplayDensity.parse("Physical density: 300\nOverride density: 260")
        assertEquals(DensityReading(300, 260, overridden = true), reading)
    }

    @Test
    fun `T19 the safe range for 300 physical is 180 to 420`() {
        assertEquals(180..420, DisplayDensity.safeRange(300))
    }

    @Test
    fun `T20 applying outside the safe range is refused and arms nothing`() {
        val plan = DisplayDensity.planApply(300, 170)
        assertEquals(DensityApplyPlan.Refused(DisplayDensity.OUTSIDE_SAFE_RANGE), plan)
    }

    @Test
    fun `T21 applying inside the range arms a reset revert`() {
        val plan = DisplayDensity.planApply(300, 260) as DensityApplyPlan.Plan
        assertEquals("wm density 260", plan.shellLine)
        assertEquals(PendingRevert("display_density", "wm density 260", "wm density reset"), plan.pendingRevert)
    }

    @Test
    fun `T24 revert now sends the recorded revert`() {
        val pending = PendingRevert("display_density", "wm density 260", "wm density reset")
        assertEquals("wm density reset", DisplayDensity.revertLine(pending))
    }

    @Test
    fun `T25 revert now with nothing pending sends nothing`() {
        assertNull(DisplayDensity.revertLine(null))
    }

    @Test
    fun `pending revert JSON round-trips and rejects garbage`() {
        val record = PendingRevert("display_density", "wm density 260", "wm density reset")
        val encoded = PendingRevertCodec.encode(record)
        assertEquals(record, PendingRevertCodec.decode(encoded))
        assertNull(PendingRevertCodec.decode("not json"))
        assertNull(PendingRevertCodec.decode(null))
    }

    @Test
    fun `snapToStep rounds to the nearest multiple of 5`() {
        assertEquals(260, DisplayDensity.snapToStep(261))
        assertEquals(265, DisplayDensity.snapToStep(263))
    }

    @Test
    fun `isInSafeRange agrees with safeRange`() {
        assertTrue(DisplayDensity.isInSafeRange(300, 180))
        assertTrue(DisplayDensity.isInSafeRange(300, 420))
        assertFalse(DisplayDensity.isInSafeRange(300, 179))
        assertFalse(DisplayDensity.isInSafeRange(300, 421))
    }
}
