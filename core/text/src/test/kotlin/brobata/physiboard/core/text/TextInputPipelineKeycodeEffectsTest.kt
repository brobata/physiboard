package brobata.physiboard.core.text

import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.EditEffect
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * spec: keys-and-modifiers.md SS7.3's mapping table (`keycode`, `page_start`/`page_end`,
 * `copy`/`paste`/`cut`/`undo`, and the three media actions): before this test, every one of these
 * effects fell through [TextInputPipeline]'s catch-all to [EditorOp.PassThroughKey], which let the
 * *raw* physical key reach the app instead of the DPAD/Tab/Escape/context-menu/media action the
 * Fn Layer map (or an in-field Ctrl mapping) resolved to. See [TextInputPipeline.handleEdit].
 */
class TextInputPipelineKeycodeEffectsTest {

    private val field = FieldContext(FieldKind.NORMAL)
    private val settings = TextInputSettingsBundle()
    private val resources = TextInputResources()
    private val state = TextInputState()
    private val editor = EditorSnapshot(textBeforeCursor = "hello")

    private fun edit(effect: EditEffect, extendSelection: Boolean = false, shiftActive: Boolean = false) =
        TextInputPipeline.handle(
            TextInputRequest.Key(Action.Edit(effect, extendSelection), shiftActive = shiftActive),
            field, settings, resources, state, editor,
        )

    @Test
    fun `a DPAD keycode effect sends a real key, not a pass-through`() {
        val result = edit(EditEffect.CURSOR_UP)
        assertEquals(listOf(EditorOp.SendKey(EditEffect.CURSOR_UP, withShift = false)), result.ops)
    }

    @Test
    fun `Tab and Escape also become a real key send`() {
        assertEquals(listOf(EditorOp.SendKey(EditEffect.TAB)), edit(EditEffect.TAB).ops)
        assertEquals(listOf(EditorOp.SendKey(EditEffect.ESCAPE)), edit(EditEffect.ESCAPE).ops)
    }

    @Test
    fun `a selection-aware nav key carries Shift when Shift is active`() {
        val result = edit(EditEffect.PAGE_DOWN, shiftActive = true)
        assertEquals(listOf(EditorOp.SendKey(EditEffect.PAGE_DOWN, withShift = true)), result.ops)
    }

    @Test
    fun `Tab does not carry Shift even when Shift is active, it is not one of the eight nav keys`() {
        val result = edit(EditEffect.TAB, shiftActive = true)
        assertEquals(listOf(EditorOp.SendKey(EditEffect.TAB, withShift = false)), result.ops)
    }

    @Test
    fun `page_start sends Ctrl+Home, distinct from the plain Home of line_home`() {
        assertEquals(listOf(EditorOp.SendKey(EditEffect.PAGE_START, withShift = false, withCtrl = true)), edit(EditEffect.PAGE_START).ops)
        assertEquals(listOf(EditorOp.SendKey(EditEffect.LINE_HOME, withShift = false, withCtrl = false)), edit(EditEffect.LINE_HOME).ops)
    }

    @Test
    fun `copy paste cut and undo perform the editor's context-menu action`() {
        assertEquals(listOf(EditorOp.PerformEditorAction(EditEffect.COPY)), edit(EditEffect.COPY).ops)
        assertEquals(listOf(EditorOp.PerformEditorAction(EditEffect.PASTE)), edit(EditEffect.PASTE).ops)
        assertEquals(listOf(EditorOp.PerformEditorAction(EditEffect.CUT)), edit(EditEffect.CUT).ops)
        assertEquals(listOf(EditorOp.PerformEditorAction(EditEffect.UNDO)), edit(EditEffect.UNDO).ops)
    }

    @Test
    fun `a media effect dispatches through the audio service, not the editor`() {
        assertEquals(listOf(EditorOp.DispatchMediaKey(EditEffect.MEDIA_PLAY_PAUSE)), edit(EditEffect.MEDIA_PLAY_PAUSE).ops)
    }
}
