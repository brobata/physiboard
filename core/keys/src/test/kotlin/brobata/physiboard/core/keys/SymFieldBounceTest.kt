package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: layers-sym-alt.md SS5.2, a text box that went away. */
class SymFieldBounceTest {
    private val messages = "com.google.android.apps.messaging"

    @Test
    fun `the screenshot case - the box goes with the emoji page open, Sym is for the box, and the page comes back with it`() {
        // onFinishInput sees the page open; the box-less start that follows sees it already closed.
        var loss = SymFieldBounce.onEditorLost(null, messages, openPage = 4, nowMs = 1_000)
        loss = SymFieldBounce.onEditorLost(loss, messages, openPage = 0, nowMs = 1_050)
        assertEquals(FieldLoss(messages, 1_000, 4), loss)
        assertTrue(SymFieldBounce.symWantsTheField(loss, messages, nowMs = 2_400))
        assertEquals(4, SymFieldBounce.pageToRestore(loss, messages, nowMs = 8_000))
    }

    @Test
    fun `another app, or the window passed, leaves Sym to the launcher shortcuts`() {
        val loss = SymFieldBounce.onEditorLost(null, messages, openPage = 2, nowMs = 1_000)
        assertFalse(SymFieldBounce.symWantsTheField(loss, "com.teslacoilsw.launcher", nowMs = 2_000))
        assertFalse(SymFieldBounce.symWantsTheField(loss, messages, nowMs = 1_000 + SymFieldBounce.WINDOW_MS + 1))
        assertEquals(0, SymFieldBounce.pageToRestore(loss, "com.whatsapp", nowMs = 2_000))
        assertEquals(0, SymFieldBounce.pageToRestore(loss, messages, nowMs = 1_000 + SymFieldBounce.WINDOW_MS + 1))
    }

    @Test
    fun `a box left with no page open (Back to the conversation list) leaves Sym the launcher key`() {
        val loss = SymFieldBounce.onEditorLost(null, messages, openPage = 0, nowMs = 0)
        assertEquals(0, SymFieldBounce.pageToRestore(loss, messages, nowMs = 100))
        assertFalse(SymFieldBounce.symWantsTheField(loss, messages, nowMs = 100))
    }

    @Test
    fun `an unknown package records nothing`() {
        assertNull(SymFieldBounce.onEditorLost(null, null, openPage = 4, nowMs = 0))
        assertNull(SymFieldBounce.onEditorLost(null, "", openPage = 4, nowMs = 0))
        assertFalse(SymFieldBounce.symWantsTheField(null, messages, nowMs = 0))
    }
}
