package brobata.physiboard.ime

import android.view.KeyEvent
import brobata.physiboard.core.actions.launcher.LauncherKeyDecision
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.CtrlMapping
import brobata.physiboard.core.keys.CtrlMappingTable
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.keys.EditEffect
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.toolbox.AccessibilityServiceList
import brobata.physiboard.device.titan.TitanLayouts
import brobata.physiboard.ime.access.PhysiBoardAccessibilityService
import brobata.physiboard.ime.access.relayIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * keys-and-modifiers.md SS15.1: a key the accessibility service hands over from a window with no
 * text box, and no input connection at all (the camera). The keyboard's own no-field decisions
 * run; what needs a connection declines the key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoFieldAccessibilityRelayTest {

    private val commands = mutableListOf<String>()
    private var clock = 1_000L

    private fun pipeline(settings: Settings = Settings(), map: Map<KeyId, CtrlMapping> = emptyMap()): KeyboardPipeline {
        val layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), settings, ctrlMappings = CtrlMappingTable(map))
        return KeyboardPipeline(layout = layout, settings = ImeSettings.keyboardSettings(settings), onCommand = { commands += it }).apply {
            onStartInput(FieldContext(FieldKind.NOT_EDITABLE), textBeforeCursor = null)
        }
    }

    private fun KeyboardPipeline.key(key: KeyId, edge: KeyEdge = KeyEdge.DOWN, ctrl: Boolean = false, connected: Boolean = false, repeat: Int = 0) =
        onKeyStroke(KeyStroke(key, edge, repeat, clock.also { clock += 60 }, ModifierFlags(ctrl = ctrl)), EditorSnapshot(textBeforeCursor = null, nowMs = clock), hasInputConnection = connected)

    @Test
    fun `Sym then Space opens the quick launcher with no text box and no connection`() {
        val p = pipeline()
        assertFalse(p.fieldReallyEditable)
        val sym = p.key(KeyId.Modifier(ModifierKey.SYM))
        assertTrue("Sym arms the shortcut mode", sym.consumed)
        assertNotNull(sym.powerModeArmedAtMs)
        p.key(KeyId.Modifier(ModifierKey.SYM), KeyEdge.UP)
        val space = p.key(KeyId.Control(ControlKey.SPACE))
        assertTrue(space.consumed)
        assertEquals("expansion-clipboard-pickers-launcher.md SS6.2 D: the run waits for the release", null, space.launcherKey)
        val released = p.key(KeyId.Control(ControlKey.SPACE), KeyEdge.UP)
        assertTrue("the release is consumed, as its press was", released.consumed)
        assertTrue("the default Space assignment runs: ${released.launcherKey}", released.launcherKey is LauncherKeyDecision.Run)
    }

    @Test
    fun `holding Sym then Space with no text box opens the sheet for Space and swallows the rest of the press`() {
        val p = pipeline()
        p.key(KeyId.Modifier(ModifierKey.SYM))
        p.key(KeyId.Modifier(ModifierKey.SYM), KeyEdge.UP)
        val downAt = clock
        assertTrue(p.key(KeyId.Control(ControlKey.SPACE)).consumed)
        val deadline = p.launcherHoldDeadlineMs
        assertNotNull(deadline)
        assertEquals(downAt + p.layout.longPress.clampedThresholdMs, deadline)
        val sheet = p.onLauncherHoldTick(deadline!!)
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(62, byHold = true), sheet)
        clock = deadline + 50
        val rep = p.key(KeyId.Control(ControlKey.SPACE), repeat = 3)
        assertTrue("a repeat is swallowed, never launched", rep.consumed && rep.launcherKey == null && rep.ops.isEmpty())
        val released = p.key(KeyId.Control(ControlKey.SPACE), KeyEdge.UP)
        assertTrue(released.consumed)
        assertEquals(null, released.launcherKey)
    }

    @Test
    fun `a field that finished leaves no box behind, so Sym arms the shortcut mode again`() {
        val p = pipeline()
        p.onStartInput(FieldContext(FieldKind.NORMAL), textBeforeCursor = "")
        assertTrue(p.fieldReallyEditable)
        p.onFinishInput(nowMs = clock)
        assertFalse(p.fieldReallyEditable)
        clock += 5_000
        assertNotNull(p.key(KeyId.Modifier(ModifierKey.SYM)).powerModeArmedAtMs)
    }

    @Test
    fun `a plain letter with nothing armed goes on to the app`() {
        assertFalse(pipeline().key(KeyId.Letter('E')).consumed)
    }

    @Test
    fun `a held-Fn chord runs an Fn layer command with no text box when Ctrl-hold is on`() {
        val settings = Settings().let { it.copy(keys = it.keys.copy(navModeCtrlHoldEnabled = true)) }
        val p = pipeline(settings, mapOf(KeyId.Letter('H') to CtrlMapping.Command("device.home")))
        val result = p.key(KeyId.Letter('H'), ctrl = true)
        assertTrue(result.consumed)
        assertEquals(listOf("device.home"), commands)
    }

    @Test
    fun `without Ctrl-hold the chord passes, exactly as it does in a text box`() {
        val p = pipeline(map = mapOf(KeyId.Letter('H') to CtrlMapping.Command("device.home")))
        assertFalse(p.key(KeyId.Letter('H'), ctrl = true).consumed)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `an Fn layer mapping that needs a connection declines the key when there is none`() {
        val settings = Settings().let { it.copy(keys = it.keys.copy(navModeCtrlHoldEnabled = true)) }
        val p = pipeline(settings, mapOf(KeyId.Letter('E') to CtrlMapping.Keycode(KeyId.Control(ControlKey.DPAD_UP))))
        assertFalse(p.key(KeyId.Letter('E'), ctrl = true, connected = false).consumed)
        assertTrue("with a connection (a launcher) the same chord moves", p.key(KeyId.Letter('E'), ctrl = true, connected = true).consumed)
    }

    @Test
    fun `a media mapping needs no connection, so it is claimed and carries its media key`() {
        val settings = Settings().let { it.copy(keys = it.keys.copy(navModeCtrlHoldEnabled = true)) }
        val p = pipeline(settings, mapOf(KeyId.Letter('P') to CtrlMapping.NamedAction("media_play_pause")))
        val result = p.key(KeyId.Letter('P'), ctrl = true, connected = false)
        assertTrue(result.consumed)
        assertEquals(listOf(EditorOp.DispatchMediaKey(EditEffect.MEDIA_PLAY_PAUSE)), result.ops)
    }

    @Test
    fun `the service's copy of a key event is recognised as the same event`() {
        val original = KeyEvent(500L, 520L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE, 0, 0, 1, 57)
        assertEquals(original.relayIdentity(), KeyEvent(original).relayIdentity())
        assertFalse(original.relayIdentity() == KeyEvent.changeAction(original, KeyEvent.ACTION_UP).relayIdentity())
    }

    @Test
    fun `the broker's component names the service the manifest declares`() {
        assertEquals(PhysiBoardAccessibilityService::class.java.name, AccessibilityServiceList.SERVICE_CLASS)
    }
}
