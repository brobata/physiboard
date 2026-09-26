package brobata.physiboard.ime

import brobata.physiboard.core.keys.EditEffect
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.pointer.navmode.NavModeTransition
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * spec: trackpad-caret-nav.md SS5.2 (entry/exit) and SS5.5 (the Fn Layer map with no field),
 * keys-and-modifiers.md SS15 point 3. Before this wiring, `:core:pointer`'s `NavModeEntry` and
 * `NavModeMap` existed and were fully tested in isolation but were never called from `:ime`: a
 * physical Ctrl double tap outside a text field did nothing, and even a latched nav mode ran no
 * mapping at all (every letter fell through to "no field" pass-through, keys-and-modifiers.md
 * SS15's own routing table never having a caller).
 */
class KeyboardPipelineNavModeTest {

    private val layout = TitanLayouts.titan2EliteQwerty()
    private val noEditor = EditorSnapshot(textBeforeCursor = null)

    private fun ctrl(edge: KeyEdge, timeMs: Long) = KeyStroke(KeyId.Modifier(ModifierKey.CTRL), edge, 0, timeMs)
    private fun letter(c: Char, timeMs: Long = 0) = KeyStroke(KeyId.Letter(c), KeyEdge.DOWN, 0, timeMs)

    @Test
    fun `a Ctrl double tap with no field latches nav mode and reports the transition`() {
        val pipeline = KeyboardPipeline(layout = layout) // starts with no field, nav_mode_enabled true by default

        pipeline.onKeyStroke(ctrl(KeyEdge.DOWN, 0), noEditor)
        pipeline.onKeyStroke(ctrl(KeyEdge.UP, 50), noEditor)
        val second = pipeline.onKeyStroke(ctrl(KeyEdge.DOWN, 400), noEditor)

        assertTrue(second.consumed)
        assertEquals(NavModeTransition.ENTERED, second.navModeTransition)
    }

    @Test
    fun `a Ctrl down while nav mode is latched exits it and reports the transition`() {
        val pipeline = KeyboardPipeline(layout = layout)
        pipeline.onKeyStroke(ctrl(KeyEdge.DOWN, 0), noEditor)
        pipeline.onKeyStroke(ctrl(KeyEdge.UP, 50), noEditor)
        pipeline.onKeyStroke(ctrl(KeyEdge.DOWN, 400), noEditor) // latched
        pipeline.onKeyStroke(ctrl(KeyEdge.UP, 450), noEditor)

        val exit = pipeline.onKeyStroke(ctrl(KeyEdge.DOWN, 1000), noEditor)

        assertTrue(exit.consumed)
        assertEquals(NavModeTransition.EXITED, exit.navModeTransition)
    }

    @Test
    fun `with nav mode off and never latched, a Ctrl tap is not this feature's concern`() {
        val pipeline = KeyboardPipeline(layout = layout, settings = KeyboardSettings(navModeEnabled = false))

        val result = pipeline.onKeyStroke(ctrl(KeyEdge.DOWN, 0), noEditor)

        // spec: keys-and-modifiers.md SS14, "everything, when no text field is focused, except the
        // nav-mode and shortcut keys": an ordinary Ctrl tap passes to the app.
        assertNull(result.navModeTransition)
        assertFalse(result.consumed)
    }

    private fun latchNavMode(pipeline: KeyboardPipeline) {
        pipeline.onKeyStroke(ctrl(KeyEdge.DOWN, 0), noEditor)
        pipeline.onKeyStroke(ctrl(KeyEdge.UP, 50), noEditor)
        pipeline.onKeyStroke(ctrl(KeyEdge.DOWN, 400), noEditor)
        pipeline.onKeyStroke(ctrl(KeyEdge.UP, 450), noEditor)
    }

    @Test
    fun `T56 - with nav mode on, E sends the DPAD_UP keycode`() {
        val pipeline = KeyboardPipeline(layout = layout)
        latchNavMode(pipeline)

        val result = pipeline.onKeyStroke(letter('E', 1000), noEditor)

        assertTrue(result.consumed)
        assertEquals(listOf(EditorOp.SendKey(EditEffect.CURSOR_UP)), result.ops)
    }

    @Test
    fun `T57 - with nav mode on, Z performs undo`() {
        val pipeline = KeyboardPipeline(layout = layout)
        latchNavMode(pipeline)

        val result = pipeline.onKeyStroke(letter('Z', 1000), noEditor)

        assertTrue(result.consumed)
        assertEquals(listOf(EditorOp.PerformEditorAction(EditEffect.UNDO)), result.ops)
    }

    @Test
    fun `T59 - with nav mode on, G has no mapping and is not consumed`() {
        val pipeline = KeyboardPipeline(layout = layout)
        latchNavMode(pipeline)

        val result = pipeline.onKeyStroke(letter('G', 1000), noEditor)

        assertFalse(result.consumed)
    }

    @Test
    fun `T60 - with nav mode on, Enter sends DPAD_CENTER`() {
        val pipeline = KeyboardPipeline(layout = layout)
        latchNavMode(pipeline)

        val result = pipeline.onKeyStroke(KeyStroke(KeyId.Control(brobata.physiboard.core.keys.ControlKey.ENTER), KeyEdge.DOWN, 0, 1000), noEditor)

        assertTrue(result.consumed)
        assertEquals(listOf(EditorOp.SendKey(EditEffect.CURSOR_CENTER)), result.ops)
    }

    @Test
    fun `any key-up while nav mode is active is consumed`() {
        val pipeline = KeyboardPipeline(layout = layout)
        latchNavMode(pipeline)

        val result = pipeline.onKeyStroke(KeyStroke(KeyId.Letter('E'), KeyEdge.UP, 0, 1000), noEditor)

        assertTrue(result.consumed)
        assertEquals(emptyList(), result.ops)
    }
}
