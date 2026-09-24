package brobata.physiboard.core.text

import brobata.physiboard.core.keys.Action
import brobata.physiboard.core.keys.EditEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Whole-call tests for [TextInputPipeline.handle]'s Enter path once a per-app profile is in play:
 * the real editor ops it produces (or does not), and the [TextInputResult.enterDelivery] `:ime`
 * must still act on. [EnterDecisionTest] already covers the pure branching; these prove the
 * surrounding plumbing (composing, state resets, and that the two "newline" paths really do
 * differ) that only [TextInputPipeline] itself can produce.
 */
class TextInputPipelineEnterDeliveryTest {

    private val field = FieldContext(FieldKind.NORMAL, imeAction = ImeAction.SEND)
    private val settings = TextInputSettingsBundle()
    private val resources = TextInputResources()

    private fun sendOnEnterProfile(actionAllowed: Boolean = true) =
        AppProfile(packageName = "com.whatsapp", enterBehavior = EnterBehavior.SEND_SHIFT_NEWLINE, enterActionAllowed = actionAllowed)

    private fun handle(request: TextInputRequest.Key, state: TextInputState, editor: EditorSnapshot, profile: AppProfile): TextInputResult =
        TextInputPipeline.handle(request, field, settings, resources, state, editor, appProfile = profile)

    // -----------------------------------------------------------------------------------------
    // T18/T21: an editor-action request finishes composing and commits no newline.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T18 a plain Enter under send-on-Enter finishes composing and requests the field's action, no newline`() {
        val result = handle(TextInputRequest.Key(Action.Edit(EditEffect.NEWLINE)), TextInputState(), EditorSnapshot(textBeforeCursor = "hi"), sendOnEnterProfile())

        assertEquals(listOf(EditorOp.FinishComposing), result.ops)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = false), result.enterDelivery)
    }

    @Test
    fun `T21 Ctrl+Enter under send-on-Enter asks for Ctrl to be cleared once delivered`() {
        val request = TextInputRequest.Key(Action.Edit(EditEffect.NEWLINE), ctrlActive = true)
        val result = handle(request, TextInputState(), EditorSnapshot(textBeforeCursor = "hi"), sendOnEnterProfile())

        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = true), result.enterDelivery)
    }

    // -----------------------------------------------------------------------------------------
    // T20: Shift+Enter inserts a newline the pipeline itself commits; no delivery for `:ime`.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T20 Shift+Enter under send-on-Enter commits a newline and leaves no delivery for ime`() {
        val request = TextInputRequest.Key(Action.Edit(EditEffect.NEWLINE), shiftActive = true)
        val result = handle(request, TextInputState(), EditorSnapshot(textBeforeCursor = "hi"), sendOnEnterProfile())

        assertEquals(listOf(EditorOp.FinishComposing, EditorOp.CommitText("\n")), result.ops)
        assertNull(result.enterDelivery)
    }

    // -----------------------------------------------------------------------------------------
    // T22: Discord's auto method is a real key event, not an editor-action request.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T22 Discord under auto is delivered as a plain Enter key event, not an editor action`() {
        val profile = AppProfile(packageName = "com.discord", enterBehavior = EnterBehavior.SEND_SHIFT_NEWLINE)
        val result = handle(TextInputRequest.Key(Action.Edit(EditEffect.NEWLINE)), TextInputState(), EditorSnapshot(textBeforeCursor = "hi"), profile)

        assertEquals(EnterIntent.SendPlainEnter, result.enterDelivery)
        assertEquals(listOf(EditorOp.FinishComposing), result.ops)
    }

    // -----------------------------------------------------------------------------------------
    // The per-app newline (SS3.4) commits "\n" directly with no other op: unlike the generic
    // decline path (text-input.md SS7, see handleGenericEnter), it never runs the boundary/
    // autocorrect engine, so there is never a DeleteSurrounding/replacement/Haptic op ahead of it.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a per-app newline behavior commits exactly finish-composing then the newline, nothing else`() {
        val newlineProfile = AppProfile(packageName = "com.example.newline", enterBehavior = EnterBehavior.NEWLINE)
        val result = handle(TextInputRequest.Key(Action.Edit(EditEffect.NEWLINE)), TextInputState(), EditorSnapshot(textBeforeCursor = "hi"), newlineProfile)

        assertEquals(listOf(EditorOp.FinishComposing, EditorOp.CommitText("\n")), result.ops)
        assertNull(result.enterDelivery)
    }

    // -----------------------------------------------------------------------------------------
    // Backward compatibility: no profile at all still behaves like a plain newline (today's
    // pre-existing behaviour, decline path).
    // -----------------------------------------------------------------------------------------

    @Test
    fun `no app profile at all keeps Enter as a plain committed newline`() {
        // A field that declares no action, so the default profile's step-e path also declines
        // (see EnterDecisionTest's T27/T28): otherwise this would exercise step e's own editor
        // action request instead of the pre-existing decline path this test is about.
        val noActionField = FieldContext(FieldKind.NORMAL, imeAction = ImeAction.NONE)
        val result = TextInputPipeline.handle(TextInputRequest.Key(Action.Edit(EditEffect.NEWLINE)), noActionField, settings, resources, TextInputState(), EditorSnapshot(textBeforeCursor = "hi"))

        assertEquals(listOf(EditorOp.FinishComposing, EditorOp.CommitText("\n")), result.ops)
        assertNull(result.enterDelivery)
    }
}
