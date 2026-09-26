package brobata.physiboard.core.shell

/**
 * Resolves an Android key code to the name the Diagnostics screen shows (app-shell.md SS10.3):
 * "the Android key-code name for letters, space, enter, delete, back, the DPADs, TAB, MOVE_HOME,
 * MOVE_END, PAGE_UP, PAGE_DOWN, ESCAPE, FORWARD_DEL; keycode 63 is named KEYCODE_SYM; anything
 * else is KEYCODE_<number>". Pure lookup table so this module needs no `android.view.KeyEvent`
 * import; the numeric codes below are Android's own stable public constants.
 */
object KeyEventNaming {
    const val KEYCODE_BACK = 4
    const val KEYCODE_SYM = 63

    private val NAMES: Map<Int, String> = buildMap {
        for (i in 0..25) put(29 + i, "KEYCODE_${'A' + i}")
        for (i in 0..9) put(7 + i, "KEYCODE_$i")
        put(19, "KEYCODE_DPAD_UP")
        put(20, "KEYCODE_DPAD_DOWN")
        put(21, "KEYCODE_DPAD_LEFT")
        put(22, "KEYCODE_DPAD_RIGHT")
        put(23, "KEYCODE_DPAD_CENTER")
        put(61, "KEYCODE_TAB")
        put(62, "KEYCODE_SPACE")
        put(66, "KEYCODE_ENTER")
        put(67, "KEYCODE_DEL")
        put(92, "KEYCODE_PAGE_UP")
        put(93, "KEYCODE_PAGE_DOWN")
        put(111, "KEYCODE_ESCAPE")
        put(112, "KEYCODE_FORWARD_DEL")
        put(122, "KEYCODE_MOVE_HOME")
        put(123, "KEYCODE_MOVE_END")
        put(KEYCODE_BACK, "KEYCODE_BACK")
        put(KEYCODE_SYM, "KEYCODE_SYM")
    }

    /** spec SS10.3: a code this table does not name reads as `KEYCODE_<number>`. */
    fun name(keyCode: Int): String = NAMES[keyCode] ?: "KEYCODE_$keyCode"
}

/**
 * Renders one unicode code point the way the panel and the export print it (app-shell.md SS10.3):
 * `<n>('c')`, zero as `0(n/a)`, and a control character escaped as `\uXXXX` instead of the literal
 * character.
 */
object UnicodeToken {
    fun render(code: Int): String {
        if (code == 0) return "0(n/a)"
        val ch = code.toChar()
        val display = if (Character.isISOControl(ch)) "\\u%04x".format(code) else ch.toString()
        return "$code('$display')"
    }
}

/**
 * One physical (or synthetic trackpad-gesture) key event as the keyboard reported it, before any
 * wall-clock stamp or recording bookkeeping is added. spec: app-shell.md SS10.2, SS10.3, SS10.6
 * `[events]`. [outputKeyCode] is set only when the keyboard translated the key into a different
 * one (SS10.3: "drawn in amber"); null means the key was not translated.
 */
data class KeyboardEventRecord(
    /** `ime_service`, `ime_router`, `ime_decor`, `bounce_keys` or `accidental_keys` (SS10.2). */
    val origin: String,
    /** `KEY_DOWN`, `KEY_UP`, or `GESTURE_<phase>` for a synthetic trackpad report (SS10.3). */
    val action: String,
    val keyCode: Int,
    val scanCode: Int,
    val deviceId: Int,
    val source: Int,
    val flags: Int,
    val repeatCount: Int,
    val metaState: Int,
    val unicodeRaw: Int,
    val unicodeEffective: Int,
    val layout: String,
    val shift: Boolean,
    val ctrl: Boolean,
    val alt: Boolean,
    val altLatch: Boolean,
    val altOneShot: Boolean,
    val shiftLatch: Boolean,
    val ctrlLatch: Boolean,
    val symPage: String,
    val eventUptimeMs: Long,
    val outputKeyCode: Int? = null,
) {
    val keyName: String get() = KeyEventNaming.name(keyCode)
    val outputKeyName: String? get() = outputKeyCode?.let { KeyEventNaming.name(it) }

    /** spec SS10.3 "Ignore BACK": whether this is the key the chip cares about. */
    val isBack: Boolean get() = keyCode == KeyEventNaming.KEYCODE_BACK
}

/** spec: SS10.4. One event as recorded: its wall-clock stamp and the delta from the previous recorded event, clamped at 0. */
data class RecordedKeyboardEvent(val atMs: Long, val deltaMs: Long, val event: KeyboardEventRecord)

/** spec: SS10.2, "it registers as the ONE listener for key events reported by the keyboard service." */
fun interface KeyboardEventListener {
    fun onKeyboardEvent(event: KeyboardEventRecord)
}

/**
 * Pure rules the Diagnostics screen needs around a live stream of [KeyboardEventRecord]s: the
 * "Ignore BACK" display rule and the wall-clock/delta bookkeeping "Record" uses. Kept separate
 * from [DebugCaptureStore] because none of this is a capacity-bounded buffer; the recorded event
 * list is the screen's own state (app-shell.md SS11's table has no keyboard-event buffer).
 */
object KeyboardEventRecording {

    /**
     * spec SS10.3: "a BACK key event does not replace the panel; the last non-BACK event stays
     * visible... Recording is not affected by the chip." Only the displayed event, never whether
     * an event is recorded, goes through this rule.
     */
    fun displayedEvent(current: KeyboardEventRecord?, incoming: KeyboardEventRecord, ignoreBack: Boolean): KeyboardEventRecord =
        if (ignoreBack && incoming.isBack && current != null) current else incoming

    /** spec SS10.4: "a wall-clock timestamp derived from the event's uptime timestamp when it has one (now minus the uptime age), else now." */
    fun wallClockAtMs(nowMs: Long, nowUptimeMs: Long, eventUptimeMs: Long?): Long =
        if (eventUptimeMs == null) nowMs else nowMs - (nowUptimeMs - eventUptimeMs)

    /** spec SS10.4: "a delta in ms from the previous recorded event, clamped at 0." */
    fun deltaMs(previousAtMs: Long?, atMs: Long): Long = if (previousAtMs == null) 0L else maxOf(0L, atMs - previousAtMs)
}

/**
 * Renders the panel (SS10.3) and the `[events]` export rows (SS10.6) from a [KeyboardEventRecord].
 * Pure text so both are JVM-testable against the spec's exact field lists.
 */
object KeyboardEventExport {

    /** spec SS10.3: left column, "n/a" when absent is the caller's job (no event to render here). */
    fun leftColumn(e: KeyboardEventRecord): List<String> = listOf(
        e.keyName,
        "Action: ${e.action}",
        "KeyCode: ${e.keyCode}",
        "Origin: ${e.origin}",
        "Layout: ${e.layout}",
    )

    /** spec SS10.3: right column; the output line is present only when the key was translated. */
    fun rightColumn(e: KeyboardEventRecord): List<String> = listOfNotNull(
        "ScanCode: ${e.scanCode}",
        "Unicode: raw=${UnicodeToken.render(e.unicodeRaw)} effective=${UnicodeToken.render(e.unicodeEffective)}",
        e.outputKeyCode?.let { "Output: ${e.outputKeyName}($it)" },
    )

    /** spec SS10.3: the chips shown below the two columns, in Shift/Ctrl/Alt order. */
    fun modifierChips(e: KeyboardEventRecord): List<String> = buildList {
        if (e.shift) add("Shift")
        if (e.ctrl) add("Ctrl")
        if (e.alt) add("Alt")
    }

    /** spec SS10.6 `[events]`: one `|`-separated row. [isFirst] selects the "start" token over a "+<delta>ms" one. */
    fun eventLine(recorded: RecordedKeyboardEvent, formattedTime: String, isFirst: Boolean): String {
        val e = recorded.event
        val deltaToken = if (isFirst) "start" else "+${recorded.deltaMs}ms"
        val output = e.outputKeyCode?.let { " output=${e.outputKeyName}($it)" }.orEmpty()
        return "$formattedTime | $deltaToken | ${e.action} | origin=${e.origin} key=${e.keyName}(${e.keyCode}) " +
            "scan=${e.scanCode} deviceId=${e.deviceId} source=${e.source} flags=${e.flags} repeat=${e.repeatCount} " +
            "meta=${e.metaState}(0x${e.metaState.toString(16)}) unicode_raw=${e.unicodeRaw} unicode_effective=${e.unicodeEffective} " +
            "alt=${e.alt} shift=${e.shift} ctrl=${e.ctrl} altLatch=${e.altLatch} altOneShot=${e.altOneShot} " +
            "shiftLatch=${e.shiftLatch} ctrlLatch=${e.ctrlLatch} symPage=${e.symPage} layout=${e.layout} " +
            "eventUptimeMs=${e.eventUptimeMs} sourceHex=0x${e.source.toString(16)} flagsHex=0x${e.flags.toString(16)}$output"
    }
}
