package brobata.physiboard.inputmethod

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymEditShortcutsTest {
    @Test
    fun editKeysMapToTheirActions() {
        assertEquals(android.R.id.copy, SymEditShortcuts.actionFor(KeyEvent.KEYCODE_C))
        assertEquals(android.R.id.paste, SymEditShortcuts.actionFor(KeyEvent.KEYCODE_V))
        assertEquals(android.R.id.cut, SymEditShortcuts.actionFor(KeyEvent.KEYCODE_X))
        assertEquals(android.R.id.selectAll, SymEditShortcuts.actionFor(KeyEvent.KEYCODE_A))
    }

    @Test
    fun otherKeysStayChords() {
        assertNull(SymEditShortcuts.actionFor(KeyEvent.KEYCODE_B))
        assertNull(SymEditShortcuts.actionFor(KeyEvent.KEYCODE_SPACE))
    }
}
