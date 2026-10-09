package brobata.physiboard.core.text

import brobata.physiboard.core.text.FieldFocusRescue.Box
import brobata.physiboard.core.text.FieldFocusRescue.Expected
import brobata.physiboard.core.text.FieldFocusRescue.Selection
import brobata.physiboard.core.text.FieldFocusRescue.Target
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: per-app-behavior.md SS16.2, focusing the box the keyboard is typing into. */
class FieldFocusRescueTest {

    private val messages = "com.google.android.apps.messaging"

    private fun box(
        focused: Boolean = false,
        pkg: String = messages,
        text: String? = null,
        hint: String? = null,
        password: Boolean = false,
        visible: Boolean = true,
        enabled: Boolean = true,
    ) = Box(focused = focused, visible = visible, enabled = enabled, packageName = pkg, password = password, text = text, hint = hint)

    private fun expected(text: String? = "", hint: String? = null, password: Boolean = false) = Expected(messages, password, text, hint)

    @Test
    fun `asks once, on the first real key of an editable field, only with the service and the switch on`() {
        assertTrue(FieldFocusRescue.shouldAsk(true, true, true, alreadyAskedThisField = false, isInitialKeyDown = true, isModifier = false))
        assertFalse(FieldFocusRescue.shouldAsk(true, true, true, alreadyAskedThisField = true, isInitialKeyDown = true, isModifier = false))
        assertFalse(FieldFocusRescue.shouldAsk(true, true, true, false, isInitialKeyDown = false, isModifier = false), "a repeat or a release")
        assertFalse(FieldFocusRescue.shouldAsk(true, true, true, false, true, isModifier = true), "Shift, Alt, Sym, Fn")
        assertFalse(FieldFocusRescue.shouldAsk(featureOn = false, true, true, false, true, false))
        assertFalse(FieldFocusRescue.shouldAsk(true, serviceConnected = false, true, false, true, false))
        assertFalse(FieldFocusRescue.shouldAsk(true, true, fieldReallyEditable = false, false, true, false))
    }

    @Test
    fun `the one compose box of the app is focused`() {
        assertEquals(Target.Focus(1), FieldFocusRescue.choose(listOf(box(pkg = "com.android.systemui"), box(text = "hi")), expected(text = "hi")))
    }

    @Test
    fun `a box that already has focus is the user's choice and is left alone`() {
        val boxes = listOf(box(text = "search"), box(focused = true, text = "other"))
        assertEquals(Target.AlreadyFocused, FieldFocusRescue.choose(boxes, expected(text = "search")))
    }

    @Test
    fun `boxes of another app, off screen or disabled never count`() {
        val boxes = listOf(box(pkg = "com.other"), box(visible = false), box(enabled = false))
        assertEquals(Target.Nothing(FieldFocusRescue.REASON_NO_BOX), FieldFocusRescue.choose(boxes, expected()))
    }

    @Test
    fun `several boxes are told apart by the text the keyboard sees`() {
        val boxes = listOf(box(text = "To: Sam"), box(text = "on my way"))
        assertEquals(Target.Focus(1), FieldFocusRescue.choose(boxes, expected(text = "on my way")))
    }

    @Test
    fun `an empty field matches a box showing nothing or its hint`() {
        val boxes = listOf(box(text = "Sam"), box(text = "Text message", hint = "Text message"))
        assertEquals(Target.Focus(1), FieldFocusRescue.choose(boxes, expected(text = "", hint = "Text message")))
    }

    @Test
    fun `the hint decides between boxes with the same text`() {
        val boxes = listOf(box(text = null, hint = "Search"), box(text = null, hint = "Message"))
        assertEquals(Target.Focus(1), FieldFocusRescue.choose(boxes, expected(text = "", hint = "Message")))
    }

    @Test
    fun `boxes that cannot be told apart are never guessed at`() {
        val boxes = listOf(box(text = "a"), box(text = "a"))
        assertEquals(Target.Nothing(FieldFocusRescue.REASON_AMBIGUOUS), FieldFocusRescue.choose(boxes, expected(text = "a")))
        assertEquals(Target.Nothing(FieldFocusRescue.REASON_AMBIGUOUS), FieldFocusRescue.choose(boxes, expected(text = null)))
    }

    @Test
    fun `a password field's text is never compared`() {
        val boxes = listOf(box(password = true), box(password = true, hint = "PIN"))
        assertEquals(Target.Nothing(FieldFocusRescue.REASON_AMBIGUOUS), FieldFocusRescue.choose(boxes, expected(text = "", password = true)))
        assertEquals(Target.Focus(0), FieldFocusRescue.choose(listOf(box(password = true)), expected(text = null, password = true)))
    }

    @Test
    fun `any view holding input focus is the user's box, editable or not (a terminal)`() {
        assertEquals(Target.AlreadyFocused, FieldFocusRescue.choose(listOf(box(text = "")), expected(text = ""), someViewHasInputFocus = true))
    }

    private fun restore(before: Selection?, after: Selection?, keyboard: Selection?, password: Boolean = false, lenBefore: Int = 10, lenAfter: Int = 10) =
        FieldFocusRescue.selectionToRestore(before, after, keyboard, password, lenBefore, lenAfter)

    @Test
    fun `a cursor that focusing sent to the start, or a select-all, goes back`() {
        assertEquals(Selection(5, 5), restore(Selection(5, 5), Selection(0, 0), Selection(4, 4)))
        assertEquals(Selection(5, 5), restore(Selection(5, 5), Selection(0, 10), null))
    }

    @Test
    fun `without the box's own selection, the keyboard's is the target`() {
        assertEquals(Selection(4, 4), restore(Selection(-1, -1), Selection(0, 0), Selection(4, 4)))
        assertEquals(Selection(4, 4), restore(null, Selection(0, 0), Selection(4, 4)))
    }

    @Test
    fun `a cursor focusing did not reset is left where it is`() {
        assertNull(restore(Selection(5, 5), Selection(6, 6), Selection(4, 4)), "the user typed: the cursor moved on with them")
        assertNull(restore(Selection(5, 5), Selection(5, 5), null))
    }

    @Test
    fun `nothing moves when the text changed meanwhile, when nothing is known, or in a password box`() {
        assertNull(restore(Selection(5, 5), Selection(0, 0), null, lenBefore = 10, lenAfter = 11))
        assertNull(restore(null, Selection(0, 0), null))
        assertNull(restore(Selection(5, 5), Selection(0, 0), Selection(5, 5), password = true))
        assertNull(restore(Selection(0, 0), Selection(0, 0), null), "already where it was")
    }
}
