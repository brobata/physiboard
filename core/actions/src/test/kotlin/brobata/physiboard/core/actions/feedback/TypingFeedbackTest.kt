package brobata.physiboard.core.actions.feedback

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.ModifierKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS12, T53 to T56, and the SS11 sound rows. */
class TypingFeedbackTest {

    @Test
    fun `T53 the sound group by key`() {
        assertEquals(SoundGroup.SPACE, SoundGroup.forKey(KeyId.Control(ControlKey.SPACE)))
        assertEquals(SoundGroup.BACKSPACE, SoundGroup.forKey(KeyId.Control(ControlKey.BACKSPACE)))
        assertEquals(SoundGroup.ENTER, SoundGroup.forKey(KeyId.Control(ControlKey.ENTER)))
        for (m in listOf(ModifierKey.SHIFT, ModifierKey.CTRL, ModifierKey.ALT, ModifierKey.SYM, ModifierKey.FN)) {
            assertEquals(SoundGroup.MODIFIER, SoundGroup.forKey(KeyId.Modifier(m)))
        }
        assertEquals(SoundGroup.NORMAL, SoundGroup.forKey(KeyId.Letter('A')))
    }

    @Test
    fun `T54 no sound on a repeat, on Back, or without an editable field`() {
        val a = KeyId.Letter('A')
        assertFalse(TypingSounds.shouldPlay(TypingSoundMode.CLICK, a, repeatCount = 1, editableFieldActive = true))
        assertFalse(TypingSounds.shouldPlay(TypingSoundMode.CLICK, KeyId.Control(ControlKey.BACK), 0, true))
        assertFalse(TypingSounds.shouldPlay(TypingSoundMode.CLICK, a, 0, editableFieldActive = false))
        assertTrue(TypingSounds.shouldPlay(TypingSoundMode.CLICK, a, 0, true))
        assertTrue(TypingSounds.shouldPlay(TypingSoundMode.TYPEWRITER, KeyId.Modifier(ModifierKey.SHIFT), 0, true), "modifiers included")
        assertFalse(TypingSounds.shouldPlay(TypingSoundMode.OFF, a, 0, true))
    }

    @Test
    fun `T55 the tap haptic duration is clamped to 5 to 80`() {
        assertEquals(80, TapVibration.clampDuration(200))
        assertEquals(5, TapVibration.clampDuration(1))
        assertEquals(TapVibration.Effect.OneShot(80), TapVibration.slotTapEffect(useSystem = false, storedDurationMs = 200))
        assertEquals(TapVibration.Effect.SystemKeyboardTap, TapVibration.slotTapEffect(useSystem = true, storedDurationMs = 200))
    }

    @Test
    fun `T56 an unknown stored sound mode reads as off`() {
        assertEquals(TypingSoundMode.OFF, TypingSoundMode.fromStored("bogus"))
        assertEquals(TypingSoundMode.TYPEWRITER, TypingSoundMode.fromStored("typewriter"))
        assertEquals("typing_click_space_3", TypingSounds.resourceName(TypingSoundMode.CLICK, SoundGroup.SPACE, 3))
        assertEquals(null, TypingSounds.resourceName(TypingSoundMode.OFF, SoundGroup.SPACE, 3))
    }
}
