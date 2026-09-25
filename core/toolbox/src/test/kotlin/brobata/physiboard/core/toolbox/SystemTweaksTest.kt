package brobata.physiboard.core.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SystemTweaksTest {
    @Test
    fun `T26 reads animation fast history off one-handed on`() {
        val reading = SystemTweaks.parse("0.5\nnull\n1")
        assertEquals(TweaksReading(AnimationSpeed.FAST, notificationHistoryOn = false, oneHandedOn = true), reading)
    }

    @Test
    fun `T27 an animation scale of 0_7 snaps to the nearest chip Fast`() {
        assertEquals(AnimationSpeed.FAST, AnimationSpeed.nearest(0.7f))
    }

    @Test
    fun `T28 empty output reads as null`() {
        assertNull(SystemTweaks.parse(""))
        assertNull(SystemTweaks.parse("\n\n"))
    }

    @Test
    fun `T29 turning a toggle off deletes the key rather than writing 0`() {
        assertEquals("settings delete secure ${SystemTweaks.ONE_HANDED_KEY}", SystemTweaks.toggleLine(SystemTweaks.ONE_HANDED_KEY, on = false))
        assertEquals("settings put secure ${SystemTweaks.ONE_HANDED_KEY} 1", SystemTweaks.toggleLine(SystemTweaks.ONE_HANDED_KEY, on = true))
    }

    @Test
    fun `T30 resetting all tweaks writes the three scales to 1_0 then deletes both secure keys`() {
        val expected = "settings put global window_animation_scale 1.0; settings put global transition_animation_scale 1.0; " +
            "settings put global animator_duration_scale 1.0; settings delete secure notification_history_enabled; " +
            "settings delete secure one_handed_enabled"
        assertEquals(expected, SystemTweaks.resetAllLine)
    }

    @Test
    fun `an unparseable animation scale reads as 1_0 (Normal)`() {
        assertEquals(AnimationSpeed.NORMAL, SystemTweaks.parse("garbage\n1\n1")!!.animation)
    }
}
