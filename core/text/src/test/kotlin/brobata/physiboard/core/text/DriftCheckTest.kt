package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: rebuild-from-scratch.md "The editor is not a reliable narrator" point 1. */
class DriftCheckTest {

    @Test
    fun `an empty tracked word always agrees`() {
        assertEquals(DriftCheck.Agreed("hello "), DriftCheck.evaluate("", "hello "))
    }

    @Test
    fun `no read at all is unavailable`() {
        assertEquals(DriftCheck.Unavailable, DriftCheck.evaluate("hello", null))
    }

    @Test
    fun `a window ending with the tracked word agrees and strips it from the context`() {
        assertEquals(DriftCheck.Agreed("say "), DriftCheck.evaluate("hello", "say hello"))
    }

    @Test
    fun `a window that does not end with the tracked word has drifted`() {
        assertEquals(DriftCheck.Disagreed, DriftCheck.evaluate("hello", "say goodbye"))
    }

    @Test
    fun `a curly apostrophe in the editor is not mistaken for drift against the straightened record`() {
        // CurrentWordTracker folds every apostrophe variant to the straight one, but a real commit
        // types back exactly the key the user pressed; comparing raw would call this drift.
        assertEquals(DriftCheck.Agreed("we "), DriftCheck.evaluate("we'll", "we we’ll"))
    }
}
