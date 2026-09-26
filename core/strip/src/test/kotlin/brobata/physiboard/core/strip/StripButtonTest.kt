package brobata.physiboard.core.strip

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: status-bar.md SS6.1, SS6.4, SS14, SS17, SS18 rows T36 and T37. */
class StripButtonTest {

    @Test
    fun `SS6_1 every catalogue row's tap and long press`() {
        assertEquals(StripAction.Nothing, StripButton.NONE.tap)
        assertEquals(StripAction.OpenSymPage(3), StripButton.CLIPBOARD.tap)
        assertEquals(StripAction.StartDictation, StripButton.MICROPHONE.tap)
        assertEquals(StripAction.OpenSymPage(4), StripButton.EMOJI.tap)
        assertEquals(StripAction.CycleLanguage, StripButton.LANGUAGE.tap)
        assertEquals(StripAction.OpenSettings, StripButton.LANGUAGE.longPress)
        assertEquals(StripAction.OpenQuickActions, StripButton.HAMBURGER.tap)
        assertEquals(StripAction.OpenSettings, StripButton.SETTINGS.tap)
        assertEquals(StripAction.OpenSymPage(2), StripButton.SYMBOLS.tap)
        assertEquals(StripAction.SendCtrlCombo('Z'), StripButton.UNDO.tap)
        assertEquals(StripAction.SendCtrlCombo('Y'), StripButton.REDO.tap)
        StripButton.entries.filter { it != StripButton.LANGUAGE }.forEach { assertEquals(StripAction.Nothing, it.longPress, "${it.id} has no long press") }
    }

    @Test
    fun `SS6_1 clipboard and microphone release a latched layer first, nothing else does`() {
        assertEquals(setOf(StripButton.CLIPBOARD, StripButton.MICROPHONE), StripButton.entries.filter { it.releasesLatchedLayerFirst }.toSet())
    }

    @Test
    fun `SS6_1 undo and redo give the 25 ms haptic, every other tap the system one`() {
        assertEquals(setOf(StripButton.UNDO, StripButton.REDO), StripButton.entries.filter { it.haptic == TapHaptic.FIXED_25_MS }.toSet())
    }

    @Test
    fun `SS6_1 ids round-trip and the catalogue has the ten SS19 keeps`() {
        StripButton.entries.forEach { assertEquals(it, StripButton.fromId(it.id)) }
        assertEquals(10, StripButton.assignable.size)
        assertEquals(StripButton.NONE, StripButton.assignable.first())
    }

    @Test
    fun `T36 locale en_US, de and empty read EN, DE and the no-subtype marks`() {
        assertEquals("EN", LanguageLabel.of("en_US"))
        assertEquals("DE", LanguageLabel.of("de"))
        assertEquals("??", LanguageLabel.of(""))
        assertEquals("??", LanguageLabel.of(null))
        assertEquals("EN", LanguageLabel.of("en-GB"))
    }

    @Test
    fun `T37 microphone level -10, -5, 0 dB gives (128,0,0), (159,12,12), (255,50,50)`() {
        assertEquals(0xFF800000.toInt(), MicrophoneLevel.color(-10.0))
        assertEquals(0xFF9F0C0C.toInt(), MicrophoneLevel.color(-5.0))
        assertEquals(0xFFFF3232.toInt(), MicrophoneLevel.color(0.0))
        assertEquals(0.0, MicrophoneLevel.intensity(-10.0))
        assertEquals(0.25, MicrophoneLevel.intensity(-5.0))
        assertEquals(1.0, MicrophoneLevel.intensity(0.0))
        assertEquals(1.0, MicrophoneLevel.intensity(5.0), "clamped above")
        assertEquals(0.0, MicrophoneLevel.intensity(-20.0), "clamped below")
    }

    @Test
    fun `SS17 a language tap within 500 ms of the last accepted one is ignored`() {
        assertTrue(LanguageTapDebounce.accepts(lastAcceptedMs = null, nowMs = 0))
        assertFalse(LanguageTapDebounce.accepts(lastAcceptedMs = 0, nowMs = 499))
        assertTrue(LanguageTapDebounce.accepts(lastAcceptedMs = 0, nowMs = 500))
        assertTrue(LanguageTapDebounce.isDisabled(lastAcceptedMs = 0, nowMs = 299))
        assertFalse(LanguageTapDebounce.isDisabled(lastAcceptedMs = 0, nowMs = 300))
    }

    @Test
    fun `SS6_1 the clipboard badge hides at zero and flashes on a new positive count`() {
        assertNull(ClipboardBadge.text(0))
        assertEquals("3", ClipboardBadge.text(3))
        assertTrue(ClipboardBadge.flashes(previousCount = 2, newCount = 3))
        assertFalse(ClipboardBadge.flashes(previousCount = 3, newCount = 3))
        assertFalse(ClipboardBadge.flashes(previousCount = 3, newCount = 0))
        assertEquals("Empty", ClipboardBadge.accessibilityState(0))
        assertEquals("2 items", ClipboardBadge.accessibilityState(2))
        assertEquals(350L, ClipboardBadge.FLASH_MS)
    }

    @Test
    fun `SS6_4 the quick-actions row is one close plus the fixed items in order`() {
        assertEquals(
            listOf(StripButton.SYMBOLS, StripButton.EMOJI, StripButton.MICROPHONE, StripButton.CLIPBOARD, StripButton.UNDO, StripButton.REDO, StripButton.LANGUAGE, StripButton.SETTINGS),
            QuickActions.ITEMS,
        )
        assertEquals(9, QuickActions.BUTTON_COUNT)
        assertTrue(QuickActions.CLOSES_ON_EVERY_REFRESH, "SS17: the overlay closes on any key press in hardware mode")
    }

    @Test
    fun `SS6_4 the quick-actions row keeps buttons at least 28 dp tall on every bar height`() {
        assertEquals(8, QuickActionsGeometry.verticalPaddingDp(56))
        assertEquals(8, QuickActionsGeometry.verticalPaddingDp(64))
        assertEquals(8, QuickActionsGeometry.verticalPaddingDp(48))
        assertEquals(4, QuickActionsGeometry.verticalPaddingDp(36))
        StripGeometry.BAR_HEIGHT_OPTIONS_DP.forEach { barHeightDp ->
            val padding = QuickActionsGeometry.verticalPaddingDp(barHeightDp)
            assertTrue(barHeightDp - 2 * padding >= QuickActionsGeometry.MIN_BUTTON_HEIGHT_DP, "bar $barHeightDp dp")
        }
    }
}
