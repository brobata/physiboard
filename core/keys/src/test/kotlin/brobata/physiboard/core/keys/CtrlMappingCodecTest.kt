package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: trackpad-caret-nav.md SS5.4, SS5.8; keys-and-modifiers.md SS12. */
class CtrlMappingCodecTest {

    @Test
    fun `the default Fn Layer map round trips through encode and decode`() {
        val table = CtrlMappingTable(
            mapOf(
                KeyId.Letter('Q') to CtrlMapping.Keycode(KeyId.Control(ControlKey.ESCAPE)),
                KeyId.Letter('A') to CtrlMapping.NamedAction("select_all"),
                KeyId.Letter('B') to CtrlMapping.Command("pastiera.toggle_software_keyboard_mode"),
                KeyId.Letter('O') to CtrlMapping.NativeCtrl,
                KeyId.Letter('G') to CtrlMapping.None,
            ),
        )
        val decoded = CtrlMappingCodec.decode(CtrlMappingCodec.encode(table))
        assertEquals(CtrlMapping.Keycode(KeyId.Control(ControlKey.ESCAPE)), decoded.mappingFor(KeyId.Letter('Q')))
        assertEquals(CtrlMapping.NamedAction("select_all"), decoded.mappingFor(KeyId.Letter('A')))
        assertEquals(CtrlMapping.Command("pastiera.toggle_software_keyboard_mode"), decoded.mappingFor(KeyId.Letter('B')))
        assertEquals(CtrlMapping.NativeCtrl, decoded.mappingFor(KeyId.Letter('O')))
        assertEquals(CtrlMapping.None, decoded.mappingFor(KeyId.Letter('G')))
    }

    @Test
    fun `an unknown key name or an out of vocabulary keycode value is skipped, per SS5-4`() {
        val text = """{"mappings":{"KEYCODE_1":{"type":"keycode","keycode":"ESCAPE"},"KEYCODE_Q":{"type":"keycode","keycode":"NOT_A_KEYCODE"}}}"""
        val decoded = CtrlMappingCodec.decode(text)
        assertEquals(CtrlMapping.None, decoded.mappingFor(KeyId.Letter('Q')))
    }

    @Test
    fun `a blank or unparsable file decodes to every key unmapped`() {
        assertEquals(CtrlMappingTable(), CtrlMappingCodec.decode(null))
        assertEquals(CtrlMappingTable(), CtrlMappingCodec.decode(""))
        assertEquals(CtrlMappingTable(), CtrlMappingCodec.decode("not json"))
    }
}
