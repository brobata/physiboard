package it.palsoftware.pastiera.inputmethod.ui

import android.view.KeyEvent
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EmojiPickerViewSearchInputTest {

    @Test
    fun searchKeyDownUsesLayoutResolvedTextBeforeRawUnicode() {
        val view = EmojiPickerView(RuntimeEnvironment.getApplication())
        view.setPrivateField("isSearchPanelVisible", true)
        view.setPrivateField("searchInputCaptureEnabled", true)

        val handled = view.handleSearchKeyDown(
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_Y,
                unicodeChar = 'y'.code
            ),
            ctrlActive = false,
            resolveTypedText = { "z" }
        )

        assertTrue(handled)
        assertEquals("z", view.searchText())
    }

    @Test
    fun searchKeyDownFallsBackToRawUnicodeWhenNoResolverTextExists() {
        val view = EmojiPickerView(RuntimeEnvironment.getApplication())
        view.setPrivateField("isSearchPanelVisible", true)
        view.setPrivateField("searchInputCaptureEnabled", true)

        val handled = view.handleSearchKeyDown(
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_Y,
                unicodeChar = 'y'.code
            ),
            ctrlActive = false,
            resolveTypedText = { null }
        )

        assertTrue(handled)
        assertEquals("y", view.searchText())
    }

    @Test
    fun searchInputConnectionCtrlASelectsInnerSearchText() {
        val view = EmojiPickerView(RuntimeEnvironment.getApplication())
        view.setPrivateField("isSearchPanelVisible", true)
        view.setPrivateField("searchInputCaptureEnabled", true)
        view.searchField().setText("zy")

        val handled = view.createSearchInputConnection()?.sendKeyEvent(
            KeyEvent(
                0L,
                0L,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_A,
                0,
                KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            )
        ) == true

        val searchField = view.searchField()
        assertTrue(handled)
        assertEquals(0, searchField.selectionStart)
        assertEquals(2, searchField.selectionEnd)
    }

    @Test
    fun searchKeyDownReplacesSelectedSearchText() {
        val view = EmojiPickerView(RuntimeEnvironment.getApplication())
        view.setPrivateField("isSearchPanelVisible", true)
        view.setPrivateField("searchInputCaptureEnabled", true)
        view.searchField().setText("old")
        view.searchField().selectAll()

        val handled = view.handleSearchKeyDown(
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_N,
                unicodeChar = 'n'.code
            )
        )

        assertTrue(handled)
        assertEquals("n", view.searchText())
        assertEquals(1, view.searchField().selectionStart)
    }

    @Test
    fun searchKeyDownReplacesPendingCtrlASelectionWhenEditTextSelectionCollapsed() {
        val view = EmojiPickerView(RuntimeEnvironment.getApplication())
        view.setPrivateField("isSearchPanelVisible", true)
        view.setPrivateField("searchInputCaptureEnabled", true)
        val searchField = view.searchField()
        searchField.setText("old")

        view.createSearchInputConnection()?.sendKeyEvent(
            KeyEvent(
                0L,
                0L,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_A,
                0,
                KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            )
        )
        searchField.setSelection(searchField.text.length)

        val handled = view.handleSearchKeyDown(
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_N,
                unicodeChar = 'n'.code
            )
        )

        assertTrue(handled)
        assertEquals("n", view.searchText())
        assertEquals(1, searchField.selectionStart)
    }

    @Test
    fun searchBackspaceDeletesSelectedSearchText() {
        val view = EmojiPickerView(RuntimeEnvironment.getApplication())
        view.setPrivateField("isSearchPanelVisible", true)
        view.setPrivateField("searchInputCaptureEnabled", true)
        view.searchField().setText("old")
        view.searchField().selectAll()

        val handled = view.handleSearchKeyDown(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
        )

        assertTrue(handled)
        assertEquals("", view.searchText())
    }

    private fun keyEvent(action: Int, keyCode: Int, unicodeChar: Int): KeyEvent {
        return object : KeyEvent(0L, 0L, action, keyCode, 0) {
            override fun getUnicodeChar(): Int = unicodeChar
            override fun getUnicodeChar(metaState: Int): Int = unicodeChar
        }
    }

    private fun EmojiPickerView.searchText(): String {
        return searchField().text?.toString().orEmpty()
    }

    private fun EmojiPickerView.searchField(): EditText {
        return privateField("searchField").get(this) as EditText
    }

    private fun Any.setPrivateField(name: String, value: Any?) {
        privateField(name).set(this, value)
    }

    private fun Any.privateField(name: String) =
        javaClass.getDeclaredField(name).apply { isAccessible = true }
}
