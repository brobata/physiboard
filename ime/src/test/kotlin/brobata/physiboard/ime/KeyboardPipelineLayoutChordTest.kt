package brobata.physiboard.ime

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyCommands
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierFlags
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.settings.LanguagePrefs
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.TextWindow
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The stored layout-switch rows change what the pipeline does with the chords. spec:
 * keys-and-modifiers.md SS7.5 and its 2026-09-24 amendment: every chord needs another subtype
 * ([KeyboardPipeline.anotherSubtypeAvailable]) on top of its own switch.
 */
class KeyboardPipelineLayoutChordTest {

    private val space = KeyId.Control(ControlKey.SPACE)
    private val enter = KeyId.Control(ControlKey.ENTER)
    private val shift = KeyId.Modifier(ModifierKey.SHIFT)
    private val alt = KeyId.Modifier(ModifierKey.ALT)

    private fun pipeline(languages: LanguagePrefs, commands: MutableList<String>, anotherSubtype: Boolean = true): KeyboardPipeline {
        val p = KeyboardPipeline(layout = TitanLayouts.titan2EliteQwerty(), settings = ImeSettings.keyboardSettings(Settings(languages = languages)), onCommand = commands::add)
        p.anotherSubtypeAvailable = anotherSubtype
        p.onStartInput(FieldContext(FieldKind.NORMAL))
        return p
    }

    private fun snapshot(text: String = "hi") = EditorSnapshot(textBeforeCursor = text, fullText = TextWindow(text, text.length, text.length), nowMs = 100)
    private fun down(key: KeyId, at: Long = 100, meta: ModifierFlags = ModifierFlags()) = KeyStroke(key, KeyEdge.DOWN, 0, at, meta)

    @Test
    fun `ctrl_space_layout_switch on with another subtype runs the switch command`() {
        val commands = mutableListOf<String>()
        val result = pipeline(LanguagePrefs(ctrlSpaceLayoutSwitch = true), commands).onKeyStroke(down(space, meta = ModifierFlags(ctrl = true)), snapshot())
        assertTrue(result.consumed)
        assertEquals(listOf(KeyCommands.SWITCH_LAYOUT), commands)
    }

    @Test
    fun `ctrl_space_layout_switch off leaves Fn+Space to the app as the Ctrl combo it is`() {
        val commands = mutableListOf<String>()
        val result = pipeline(LanguagePrefs(ctrlSpaceLayoutSwitch = false), commands).onKeyStroke(down(space, meta = ModifierFlags(ctrl = true)), snapshot())
        assertFalse(result.consumed)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `with one subtype installed no chord fires whatever the rows say (the 2026-09-24 amendment)`() {
        val commands = mutableListOf<String>()
        val p = pipeline(LanguagePrefs(ctrlSpaceLayoutSwitch = true, altEnterLayoutSwitch = true, altShiftLayoutSwitch = true), commands, anotherSubtype = false)
        p.onKeyStroke(down(space, meta = ModifierFlags(ctrl = true)), snapshot())
        p.onKeyStroke(down(enter, meta = ModifierFlags(alt = true)), snapshot())
        p.onKeyStroke(down(alt), snapshot())
        p.onKeyStroke(down(shift, at = 150, meta = ModifierFlags(alt = true)), snapshot())
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `alt_enter_layout_switch on with another subtype runs the switch and consumes the Enter`() {
        val commands = mutableListOf<String>()
        val result = pipeline(LanguagePrefs(altEnterLayoutSwitch = true), commands).onKeyStroke(down(enter, meta = ModifierFlags(alt = true)), snapshot())
        assertTrue(result.consumed)
        assertEquals(listOf(KeyCommands.SWITCH_LAYOUT), commands)
    }

    @Test
    fun `alt_shift_layout_switch on with another subtype runs the switch on Shift down while Alt is held`() {
        val commands = mutableListOf<String>()
        val p = pipeline(LanguagePrefs(altShiftLayoutSwitch = true), commands)
        p.onKeyStroke(down(alt), snapshot())
        val result = p.onKeyStroke(down(shift, at = 150, meta = ModifierFlags(alt = true)), snapshot())
        assertTrue(result.consumed)
        assertEquals(listOf(KeyCommands.SWITCH_LAYOUT), commands)
    }
}
