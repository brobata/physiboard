package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: app-shell.md SS10.2, SS10.3, SS10.4, SS10.6. */
class KeyboardEventCaptureTest {

    private fun event(
        keyCode: Int = 29,
        action: String = "KEY_DOWN",
        outputKeyCode: Int? = null,
    ) = KeyboardEventRecord(
        origin = "ime_service",
        action = action,
        keyCode = keyCode,
        scanCode = 30,
        deviceId = 1,
        source = 0x101,
        flags = 0,
        repeatCount = 0,
        metaState = 0,
        unicodeRaw = 97,
        unicodeEffective = 97,
        layout = "qwerty",
        shift = false,
        ctrl = false,
        alt = false,
        altLatch = false,
        altOneShot = false,
        shiftLatch = false,
        ctrlLatch = false,
        symPage = "none",
        eventUptimeMs = 1000L,
        outputKeyCode = outputKeyCode,
    )

    @Test
    fun `key names cover letters, space, enter, delete, back, dpads, tab, home, end, page, escape, sym, and fall back to the number`() {
        assertEquals("KEYCODE_A", KeyEventNaming.name(29))
        assertEquals("KEYCODE_Z", KeyEventNaming.name(54))
        assertEquals("KEYCODE_0", KeyEventNaming.name(7))
        assertEquals("KEYCODE_SPACE", KeyEventNaming.name(62))
        assertEquals("KEYCODE_ENTER", KeyEventNaming.name(66))
        assertEquals("KEYCODE_DEL", KeyEventNaming.name(67))
        assertEquals("KEYCODE_BACK", KeyEventNaming.name(4))
        assertEquals("KEYCODE_DPAD_UP", KeyEventNaming.name(19))
        assertEquals("KEYCODE_DPAD_DOWN", KeyEventNaming.name(20))
        assertEquals("KEYCODE_DPAD_LEFT", KeyEventNaming.name(21))
        assertEquals("KEYCODE_DPAD_RIGHT", KeyEventNaming.name(22))
        assertEquals("KEYCODE_TAB", KeyEventNaming.name(61))
        assertEquals("KEYCODE_MOVE_HOME", KeyEventNaming.name(122))
        assertEquals("KEYCODE_MOVE_END", KeyEventNaming.name(123))
        assertEquals("KEYCODE_PAGE_UP", KeyEventNaming.name(92))
        assertEquals("KEYCODE_PAGE_DOWN", KeyEventNaming.name(93))
        assertEquals("KEYCODE_ESCAPE", KeyEventNaming.name(111))
        assertEquals("KEYCODE_FORWARD_DEL", KeyEventNaming.name(112))
        assertEquals("KEYCODE_SYM", KeyEventNaming.name(63))
        assertEquals("KEYCODE_999", KeyEventNaming.name(999))
    }

    @Test
    fun `unicode zero is n slash a, a control character escapes, a printable one shows the glyph`() {
        assertEquals("0(n/a)", UnicodeToken.render(0))
        assertEquals("97('a')", UnicodeToken.render(97))
        assertEquals("9('\\u0009')", UnicodeToken.render(9)) // tab is a control character
    }

    @Test
    fun `the debug capture store forwards a key event only while a listener is registered`() {
        val store = DebugCaptureStore()
        var seen: KeyboardEventRecord? = null
        store.reportKeyboardEvent(event())
        assertNull(seen)

        store.setKeyboardEventListener { seen = it }
        store.reportKeyboardEvent(event())
        assertEquals("KEYCODE_A", seen?.keyName)

        store.setKeyboardEventListener(null)
        seen = null
        store.reportKeyboardEvent(event())
        assertNull(seen)
    }

    @Test
    fun `a second registration replaces the first, since there is only ever one listener`() {
        val store = DebugCaptureStore()
        var firstCalls = 0
        var secondCalls = 0
        store.setKeyboardEventListener { firstCalls++ }
        store.setKeyboardEventListener { secondCalls++ }
        store.reportKeyboardEvent(event())
        assertEquals(0, firstCalls)
        assertEquals(1, secondCalls)
    }

    @Test
    fun `ignore BACK keeps the last non-BACK event on the panel, but a BACK event still replaces a blank panel`() {
        val typed = event(keyCode = 29)
        val back = event(keyCode = 4)
        assertEquals(typed, KeyboardEventRecording.displayedEvent(current = typed, incoming = back, ignoreBack = true))
        assertEquals(back, KeyboardEventRecording.displayedEvent(current = null, incoming = back, ignoreBack = true))
        assertEquals(back, KeyboardEventRecording.displayedEvent(current = typed, incoming = back, ignoreBack = false))
    }

    @Test
    fun `wall clock is now minus the uptime age when the event carries one, else now`() {
        assertEquals(9_800L, KeyboardEventRecording.wallClockAtMs(nowMs = 10_000L, nowUptimeMs = 5_000L, eventUptimeMs = 4_800L))
        assertEquals(10_000L, KeyboardEventRecording.wallClockAtMs(nowMs = 10_000L, nowUptimeMs = 5_000L, eventUptimeMs = null))
    }

    @Test
    fun `delta from the previous recorded event is clamped at 0`() {
        assertEquals(0L, KeyboardEventRecording.deltaMs(previousAtMs = null, atMs = 100L))
        assertEquals(50L, KeyboardEventRecording.deltaMs(previousAtMs = 100L, atMs = 150L))
        assertEquals(0L, KeyboardEventRecording.deltaMs(previousAtMs = 150L, atMs = 100L))
    }

    @Test
    fun `the events export row carries start on the first row and a delta token after`() {
        val first = RecordedKeyboardEvent(atMs = 1000L, deltaMs = 0L, event = event())
        val second = RecordedKeyboardEvent(atMs = 1120L, deltaMs = 120L, event = event(action = "KEY_UP"))
        val firstLine = KeyboardEventExport.eventLine(first, "2026-01-01T00:00:01.000+00:00", isFirst = true)
        val secondLine = KeyboardEventExport.eventLine(second, "2026-01-01T00:00:01.120+00:00", isFirst = false)
        assertEquals(true, firstLine.contains("| start |"))
        assertEquals(true, secondLine.contains("| +120ms |"))
        assertEquals(true, firstLine.contains("key=KEYCODE_A(29)"))
    }

    @Test
    fun `a translated key adds the output field to the events row and the right column`() {
        val translated = event(outputKeyCode = 66)
        val line = KeyboardEventExport.eventLine(RecordedKeyboardEvent(1L, 0L, translated), "t", isFirst = true)
        assertEquals(true, line.endsWith("output=KEYCODE_ENTER(66)"))
        assertEquals(true, KeyboardEventExport.rightColumn(translated).last() == "Output: KEYCODE_ENTER(66)")
    }

    @Test
    fun `modifier chips list only the modifiers the event carries, in Shift, Ctrl, Alt order`() {
        val e = event().copy(shift = true, alt = true)
        assertEquals(listOf("Shift", "Alt"), KeyboardEventExport.modifierChips(e))
    }
}
