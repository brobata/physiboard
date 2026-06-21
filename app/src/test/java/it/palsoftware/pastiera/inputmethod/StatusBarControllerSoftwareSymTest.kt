package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardSymLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StatusBarControllerSoftwareSymTest {

    @Test
    fun softwareSymExtraLettersExposeOwnEmojiDefaults() {
        val rows = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
        val mappings = mapOf(
            KeyEvent.KEYCODE_A to "😢",
            KeyEvent.KEYCODE_O to "😡",
            KeyEvent.KEYCODE_U to "❤️"
        )
        val content = SoftwareKeyboardSymLabels.buildContentByChar(page = 1, rows, mappings)

        assertEquals("🙃", content['ü'])
        assertEquals("🧐", content['ö'])
        assertEquals("🍎", content['ä'])
        assertNotEquals(mappings[KeyEvent.KEYCODE_U], content['ü'])
        assertNotEquals(mappings[KeyEvent.KEYCODE_O], content['ö'])
        assertNotEquals(mappings[KeyEvent.KEYCODE_A], content['ä'])
        assertTrue(listOf(content['ü'], content['ö'], content['ä']).distinct().size == 3)
    }

    @Test
    fun softwareSymExtraLettersExposeOwnSymbolDefaults() {
        val rows = listOf("qwertzuiopü+", "asdfghjklöä#", "<yxcvbnm,.-")
        val content = SoftwareKeyboardSymLabels.buildContentByChar(page = 2, rows, emptyMap())

        assertEquals("€", content['ü'])
        assertEquals("@", content['ö'])
        assertEquals("#", content['ä'])
        assertEquals("+", content['+'])
        assertEquals("<", content['<'])
    }
}
