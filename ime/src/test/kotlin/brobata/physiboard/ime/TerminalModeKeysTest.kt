package brobata.physiboard.ime

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Terminal mode (per-app-behavior.md SS4.6): the keys a terminal needs reach it as keys. Ctrl in
 * any form is a real Ctrl combo, never an editor command (^A, ^Z, ^C, ^D, ^E); Esc, Tab, the
 * arrows, Home/End and PgUp/PgDn reach the app as themselves. Pipeline-level: what the keyboard
 * decides, with the real Titan layout and its shipped Fn Layer map (Ctrl+A select all, Ctrl+Z
 * undo, Ctrl+E up arrow...).
 */
class TerminalModeKeysTest {

    private val settings = Settings()
    private val terminal = FieldContext(FieldKind.RAW_MODE_APP, isMultiLine = true, appDisablesSuggestions = true)
    private val ordinary = FieldContext(FieldKind.NORMAL, isMultiLine = true)

    private class Keys(field: FieldContext, settings: Settings) {
        val pipeline = KeyboardPipeline(layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), settings), settings = ImeSettings.keyboardSettings(settings))
        private var clock = 1_000L

        init {
            pipeline.onStartInput(field, textBeforeCursor = "")
        }

        fun stroke(key: KeyId, edge: KeyEdge = KeyEdge.DOWN, meta: ModifierFlags = ModifierFlags()): PipelineResult {
            clock += 60
            return pipeline.onKeyStroke(KeyStroke(key, edge, 0, clock, meta), EditorSnapshot(textBeforeCursor = if (edge == KeyEdge.DOWN) "" else null, nowMs = clock))
        }

        fun tapCtrl() {
            stroke(CTRL, KeyEdge.DOWN, ModifierFlags(ctrl = true))
            stroke(CTRL, KeyEdge.UP)
        }
    }

    @Test
    fun `a tapped Ctrl then A reaches the terminal as Ctrl+A, not select all`() {
        val keys = Keys(terminal, settings)
        keys.tapCtrl()
        val a = keys.stroke(KeyId.Letter('A'))
        assertTrue(a.consumed, "the bare A must not reach the app")
        assertTrue(a.ops.isEmpty(), "no editor op: ${a.ops}")
        assertEquals(KeyId.Letter('A'), a.forwardAsCtrlCombo)
        // The one-shot is spent: the next A is a letter again.
        val next = keys.stroke(KeyId.Letter('A'))
        assertNull(next.forwardAsCtrlCombo)
        assertEquals(listOf<EditorOp>(EditorOp.CommitText("a")), next.ops)
    }

    @Test
    fun `outside terminal mode the same tapped Ctrl+A still selects all`() {
        val keys = Keys(ordinary, settings)
        keys.tapCtrl()
        val a = keys.stroke(KeyId.Letter('A'))
        assertNull(a.forwardAsCtrlCombo)
    }

    @Test
    fun `Ctrl+Z, Ctrl+C, Ctrl+D and Ctrl+E from a tapped Ctrl are all real combos in the terminal`() {
        for (letter in "ZCDE") {
            val keys = Keys(terminal, settings)
            keys.tapCtrl()
            val result = keys.stroke(KeyId.Letter(letter))
            assertEquals(KeyId.Letter(letter), result.forwardAsCtrlCombo, "Ctrl+$letter")
            assertTrue(result.ops.isEmpty(), "Ctrl+$letter ops: ${result.ops}")
        }
    }

    @Test
    fun `a latched Ctrl sends a combo for every key until it is tapped off`() {
        val keys = Keys(terminal, settings)
        keys.tapCtrl()
        keys.tapCtrl()
        assertEquals(KeyId.Letter('A'), keys.stroke(KeyId.Letter('A')).forwardAsCtrlCombo)
        assertEquals(KeyId.Letter('E'), keys.stroke(KeyId.Letter('E')).forwardAsCtrlCombo)
        keys.tapCtrl()
        assertNull(keys.stroke(KeyId.Letter('A')).forwardAsCtrlCombo)
    }

    @Test
    fun `a held Ctrl (Fn) chord passes the physical event through with its Ctrl bit`() {
        val keys = Keys(terminal, settings)
        val c = keys.stroke(KeyId.Letter('C'), meta = ModifierFlags(ctrl = true))
        assertFalse(c.consumed)
        assertTrue(c.ops.isEmpty())
        assertNull(c.forwardAsCtrlCombo)
    }

    @Test
    fun `a tapped Ctrl with Backspace is Ctrl+Backspace for the terminal, not the keyboard's word delete`() {
        val keys = Keys(terminal, settings)
        keys.tapCtrl()
        val backspace = keys.stroke(KeyId.Control(ControlKey.BACKSPACE))
        assertEquals(KeyId.Control(ControlKey.BACKSPACE), backspace.forwardAsCtrlCombo)
        assertTrue(backspace.ops.isEmpty())
    }

    @Test
    fun `Esc, Tab, arrows, Home, End, PgUp and PgDn reach the terminal as keys`() {
        val keys = Keys(terminal, settings)
        for (control in listOf(
            ControlKey.ESCAPE, ControlKey.TAB, ControlKey.DPAD_UP, ControlKey.DPAD_DOWN, ControlKey.DPAD_LEFT,
            ControlKey.DPAD_RIGHT, ControlKey.MOVE_HOME, ControlKey.MOVE_END, ControlKey.PAGE_UP, ControlKey.PAGE_DOWN,
        )) {
            val down = keys.stroke(KeyId.Control(control))
            val up = keys.stroke(KeyId.Control(control), KeyEdge.UP)
            assertFalse(down.consumed, "$control down must reach the app")
            assertTrue(down.ops.isEmpty(), "$control ops: ${down.ops}")
            assertFalse(up.consumed, "$control up must reach the app")
        }
    }

    @Test
    fun `Ctrl with an arrow from a tapped Ctrl is the arrow with Ctrl, for word jumps in the shell`() {
        val keys = Keys(terminal, settings)
        keys.tapCtrl()
        assertEquals(KeyId.Control(ControlKey.DPAD_LEFT), keys.stroke(KeyId.Control(ControlKey.DPAD_LEFT)).forwardAsCtrlCombo)
    }

    @Test
    fun `a latched Ctrl never takes Back or the volume keys from the system`() {
        val keys = Keys(terminal, settings)
        keys.tapCtrl()
        keys.tapCtrl()
        for (control in listOf(ControlKey.BACK, ControlKey.VOLUME_DOWN, ControlKey.VOLUME_UP)) {
            val down = keys.stroke(KeyId.Control(control))
            assertFalse(down.consumed, "$control must reach the system")
            assertNull(down.forwardAsCtrlCombo, "$control")
        }
        assertEquals(KeyId.Letter('C'), keys.stroke(KeyId.Letter('C')).forwardAsCtrlCombo, "the latch is still on")
    }

    private companion object {
        val CTRL = KeyId.Modifier(ModifierKey.CTRL)
    }
}
