package brobata.physiboard.core.pointer.navmode

import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.CtrlMapping
import brobata.physiboard.core.keys.CtrlMappingTable
import brobata.physiboard.core.keys.EditEffect
import brobata.physiboard.core.keys.KeyId
import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: trackpad-caret-nav.md SS10, test cases T56-T61 (the no-field Fn Layer map resolution). */
class NavModeMapTest {

    @Test
    fun `T56 - E maps to the DPAD_UP keycode`() {
        val decision = NavModeMap.resolveLetterKeyDown(KeyId.Letter('E'), NavModeDefaultMap.TABLE, hasInputConnection = true)
        assertEquals(NavModeKeyDecision(Action.Edit(EditEffect.CURSOR_UP), consumed = true), decision)
    }

    @Test
    fun `T57 - Z maps to the undo context-menu action`() {
        val decision = NavModeMap.resolveLetterKeyDown(KeyId.Letter('Z'), NavModeDefaultMap.TABLE, hasInputConnection = true)
        assertEquals(NavModeKeyDecision(Action.Edit(EditEffect.UNDO), consumed = true), decision)
    }

    @Test
    fun `T58 - a command mapping is handled even with no input connection`() {
        val map = CtrlMappingTable(mapOf(KeyId.Letter('K') to CtrlMapping.Command("device.home")))
        val decision = NavModeMap.resolveLetterKeyDown(KeyId.Letter('K'), map, hasInputConnection = false)
        assertEquals(NavModeKeyDecision(Action.RunCommand("device.home"), consumed = true), decision)
    }

    @Test
    fun `T59 - G has no default mapping and is not handled`() {
        val decision = NavModeMap.resolveLetterKeyDown(KeyId.Letter('G'), NavModeDefaultMap.TABLE, hasInputConnection = true)
        assertEquals(NavModeKeyDecision.NOT_HANDLED, decision)
    }

    @Test
    fun `T60 - Enter with a connection sends DPAD_CENTER`() {
        assertEquals(NavModeKeyDecision(Action.Edit(EditEffect.CURSOR_CENTER), consumed = true), NavModeMap.resolveEnter(hasInputConnection = true))
    }

    @Test
    fun `T60b - Enter with no connection is not handled`() {
        assertEquals(NavModeKeyDecision.NOT_HANDLED, NavModeMap.resolveEnter(hasInputConnection = false))
    }

    @Test
    fun `T61 - a native_ctrl mapping forwards the letter as a Ctrl combo`() {
        val map = CtrlMappingTable(mapOf(KeyId.Letter('I') to CtrlMapping.NativeCtrl))
        val decision = NavModeMap.resolveLetterKeyDown(KeyId.Letter('I'), map, hasInputConnection = true)
        assertEquals(NavModeKeyDecision(Action.ForwardAsCtrlCombo(KeyId.Letter('I')), consumed = true), decision)
    }

    @Test
    fun `page_start and page_end are never handled with no field, even with a connection`() {
        val map = CtrlMappingTable(mapOf(KeyId.Letter('Q') to CtrlMapping.NamedAction("page_start")))
        val decision = NavModeMap.resolveLetterKeyDown(KeyId.Letter('Q'), map, hasInputConnection = true)
        assertEquals(NavModeKeyDecision.NOT_HANDLED, decision)
    }

    @Test
    fun `a media action is handled with no input connection at all`() {
        val map = CtrlMappingTable(mapOf(KeyId.Letter('Q') to CtrlMapping.NamedAction("media_play_pause")))
        val decision = NavModeMap.resolveLetterKeyDown(KeyId.Letter('Q'), map, hasInputConnection = false)
        assertEquals(NavModeKeyDecision(Action.Edit(EditEffect.MEDIA_PLAY_PAUSE), consumed = true), decision)
    }

    @Test
    fun `the default map gives P no mapping, per the SS11 Keep-Drop note on the dead toggle_minimal_ui default`() {
        assertEquals(CtrlMapping.None, NavModeDefaultMap.TABLE.mappingFor(KeyId.Letter('P')))
    }

    @Test
    fun `the default map keeps the arrow cluster on both hands`() {
        assertEquals(CtrlMapping.Keycode(KeyId.Control(ControlKey.DPAD_LEFT)), NavModeDefaultMap.TABLE.mappingFor(KeyId.Letter('S')))
        assertEquals(CtrlMapping.Keycode(KeyId.Control(ControlKey.DPAD_LEFT)), NavModeDefaultMap.TABLE.mappingFor(KeyId.Letter('J')))
    }
}
