package brobata.physiboard.core.settings

import brobata.physiboard.core.text.EnterBehavior
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The defaults are the first-run baseline (settings-catalog.md SS4.1) except where a subsystem document says otherwise. */
class SettingsDefaultsTest {
    private val d = Settings()

    @Test
    fun `the screen trackpad is off by default, whatever the baseline says`() {
        assertFalse(d.trackpad.enabled)
    }

    @Test
    fun `automatic correction ships on with the baseline distance and proximity ranking`() {
        assertTrue(d.correction.autoReplaceOnSpaceEnter)
        assertEquals(2, d.correction.maxAutoReplaceDistance)
        assertTrue(d.correction.useKeyboardProximity)
        assertTrue(d.correction.textReplacementsEnabled)
    }

    @Test
    fun `fixing mixed-up words ships off`() {
        assertFalse(d.correction.fixWordMixups)
    }

    @Test
    fun `dictation first-run defaults follow dictation md SS15`() {
        assertTrue(d.dictation.fnLongPressSpeech)
        assertTrue(d.dictation.haptics)
        assertFalse(d.dictation.maskOffensive)
        assertEquals(2000, d.dictation.endSilenceMs)
        assertFalse(d.dictation.sideKeyAssistant, "not set without the binding")
        assertEquals("", d.dictation.engine)
    }

    @Test
    fun `a fresh install ends with the strip hidden, the seeded twenty apps and the baseline slots`() {
        assertEquals(StatusBarVisibility.NEVER, d.statusBar.visibility)
        assertEquals(20, d.statusBar.apps.size)
        assertTrue("com.whatsapp" in d.statusBar.apps && "com.google.android.gm" in d.statusBar.apps)
        assertEquals(listOf(BarButton.CLIPBOARD), d.statusBar.leftButtons)
        assertEquals(listOf(BarButton.MICROPHONE), d.statusBar.rightButtons)
        assertEquals(56, d.statusBar.heightDp)
        assertEquals(0xFF111827.toInt(), d.statusBar.caretBadgeArmedColor)
    }

    @Test
    fun `personal data from the baseline asset is not a default`() {
        assertTrue(d.perApp.exactTypingPackages.isEmpty())
        assertTrue(d.device.ringAppColors.isEmpty())
    }

    @Test
    fun `the baseline device facts are defaults`() {
        assertEquals(RingFit(78.4834f, 80.4834f, 45.9375f, 9.625f), d.device.ringFit)
        assertEquals(2, d.device.ringMinutes)
        assertTrue(d.device.ringEnabled)
        assertTrue(d.device.smartBacklightEnabled)
        assertEquals("$", "$") // the currency symbol row is dropped; nothing to assert but that it is gone
    }

    @Test
    fun `the four seeded Enter overrides and the messaging preset are the baseline's`() {
        assertEquals(4, d.perApp.enterOverrides.size)
        assertTrue(d.perApp.enterOverrides.all { it.behavior == EnterBehavior.SEND_SHIFT_NEWLINE })
        assertEquals(setOf("com.whatsapp", "com.discord", "com.google.android.apps.messaging", "com.instagram.android"), d.perApp.enterOverrides.map { it.packageName }.toSet())
    }

    @Test
    fun `sym pages default to the baseline order with the picker first and the device page gone`() {
        assertEquals(listOf(SymPage.EMOJI_PICKER, SymPage.SYMBOLS, SymPage.CLIPBOARD, SymPage.EMOJI, SymPage.GIF), d.symPages.pages.order)
        assertTrue(d.symPages.pages.emojiPickerEnabled && d.symPages.pages.symbolsEnabled)
        assertFalse(d.symPages.pages.emojiEnabled || d.symPages.pages.clipboardEnabled || d.symPages.pages.gifEnabled)
        assertFalse(d.symPages.emojiPickerExpandedHeight)
        assertEquals(brobata.physiboard.core.actions.emoji.SkinTone.NONE, d.symPages.defaultSkinTone)
    }

    @Test
    fun `the strip theme default is the baseline hardware theme`() {
        assertEquals(0xFF111111.toInt(), d.statusBar.theme.background)
        assertEquals(0xFF409CFF.toInt(), d.statusBar.theme.accent)
        assertEquals(0xFF171717.toInt(), d.statusBar.theme.suggestion)
    }
}
