package it.palsoftware.pastiera.core

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.View
import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TextInputControllerTest {

    private lateinit var context: Context
    private lateinit var modifierStateController: ModifierStateController
    private lateinit var controller: TextInputController

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        modifierStateController = ModifierStateController(500L)
        controller = TextInputController(
            context = context,
            modifierStateController = modifierStateController,
            doubleTapThreshold = 500L
        )
    }

    @Test
    fun doubleSpaceToPeriod_enabled_replacesVirtualKeyboardSecondSpace() {
        SettingsManager.setDoubleSpaceToPeriod(context, true)
        val inputConnection = FakeInputConnection(context, "hello")

        val firstHandled = controller.handleDoubleSpaceToPeriod(
            keyCode = KeyEvent.KEYCODE_SPACE,
            inputConnection = inputConnection,
            shouldDisableDoubleSpaceToPeriod = false,
            shouldDisableAutoCapitalize = true,
            onStatusBarUpdate = {}
        )
        if (!firstHandled) inputConnection.commitText(" ", 1)

        val secondHandled = controller.handleDoubleSpaceToPeriod(
            keyCode = KeyEvent.KEYCODE_SPACE,
            inputConnection = inputConnection,
            shouldDisableDoubleSpaceToPeriod = false,
            shouldDisableAutoCapitalize = true,
            onStatusBarUpdate = {}
        )

        assertFalse(firstHandled)
        assertTrue(secondHandled)
        assertEquals("hello. ", inputConnection.text)
    }

    @Test
    fun doubleSpaceToPeriod_settingDisabled_keepsVirtualKeyboardSpaces() {
        SettingsManager.setDoubleSpaceToPeriod(context, false)
        val inputConnection = FakeInputConnection(context, "hello")

        repeat(2) {
            val handled = controller.handleDoubleSpaceToPeriod(
                keyCode = KeyEvent.KEYCODE_SPACE,
                inputConnection = inputConnection,
                shouldDisableDoubleSpaceToPeriod = false,
                shouldDisableAutoCapitalize = true,
                onStatusBarUpdate = {}
            )
            if (!handled) inputConnection.commitText(" ", 1)
        }

        assertEquals("hello  ", inputConnection.text)
    }

    @Test
    fun doubleSpaceToPeriod_fieldDisabled_keepsVirtualKeyboardSpacesEvenWhenSettingEnabled() {
        SettingsManager.setDoubleSpaceToPeriod(context, true)
        val inputConnection = FakeInputConnection(context, "hello")

        repeat(2) {
            val handled = controller.handleDoubleSpaceToPeriod(
                keyCode = KeyEvent.KEYCODE_SPACE,
                inputConnection = inputConnection,
                shouldDisableDoubleSpaceToPeriod = true,
                shouldDisableAutoCapitalize = true,
                onStatusBarUpdate = {}
            )
            if (!handled) inputConnection.commitText(" ", 1)
        }

        assertEquals("hello  ", inputConnection.text)
    }

    @Test
    fun spacedHyphenToEnDash_enabled_replacesMidSentenceHyphenWhenSpaceIsPressed() {
        SettingsManager.setSpacedHyphenToEnDash(context, true)
        val inputConnection = FakeInputConnection(context, "hello -")

        val handled = controller.handleSpacedHyphenToEnDash(
            keyCode = KeyEvent.KEYCODE_SPACE,
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )

        assertTrue(handled)
        assertEquals("hello – ", inputConnection.text)
    }

    @Test
    fun spacedHyphenToEnDash_enabled_canUseEmDash() {
        SettingsManager.setSpacedHyphenToEnDash(context, true)
        SettingsManager.setSpacedHyphenDashStyle(context, SettingsManager.DASH_STYLE_EM)
        val inputConnection = FakeInputConnection(context, "hello -")

        val handled = controller.handleSpacedHyphenToEnDash(
            keyCode = KeyEvent.KEYCODE_SPACE,
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )

        assertTrue(handled)
        assertEquals("hello — ", inputConnection.text)
    }

    @Test
    fun spacedHyphenToEnDash_enabled_doesNotReplaceLineStartHyphen() {
        SettingsManager.setSpacedHyphenToEnDash(context, true)
        val inputConnection = FakeInputConnection(context, "  -")

        val handled = controller.handleSpacedHyphenToEnDash(
            keyCode = KeyEvent.KEYCODE_SPACE,
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText(" ", 1)

        assertFalse(handled)
        assertEquals("  - ", inputConnection.text)
    }

    @Test
    fun spacedHyphenToEnDash_settingDisabled_keepsSpacedHyphen() {
        SettingsManager.setSpacedHyphenToEnDash(context, false)
        val inputConnection = FakeInputConnection(context, "hello -")

        val handled = controller.handleSpacedHyphenToEnDash(
            keyCode = KeyEvent.KEYCODE_SPACE,
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText(" ", 1)

        assertFalse(handled)
        assertEquals("hello - ", inputConnection.text)
    }

    @Test
    fun spacedHyphenToEnDash_fieldDisabled_keepsSpacedHyphenEvenWhenSettingEnabled() {
        SettingsManager.setSpacedHyphenToEnDash(context, true)
        val inputConnection = FakeInputConnection(context, "hello -")

        val handled = controller.handleSpacedHyphenToEnDash(
            keyCode = KeyEvent.KEYCODE_SPACE,
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = true
        )
        if (!handled) inputConnection.commitText(" ", 1)

        assertFalse(handled)
        assertEquals("hello - ", inputConnection.text)
    }

    @Test
    fun smartQuotes_enabled_commitsConfiguredOpeningAndClosingQuotes() {
        SettingsManager.setSmartQuotes(context, true)
        SettingsManager.setSmartQuotesStyle(context, SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS)
        val inputConnection = FakeInputConnection(context, "\"Hallo\"")

        val handled = controller.handleSmartQuoteReplacement(
            typedText = " ",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )

        assertTrue(handled)
        assertEquals("»Hallo« ", inputConnection.text)
    }

    @Test
    fun smartQuotes_enabled_supportsAllConfiguredStyles() {
        val styles = listOf(
            SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS to "»Hallo« ",
            SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS to "«Hallo» ",
            SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED to "« Hallo » ",
            SettingsManager.SMART_QUOTES_STYLE_GERMAN_LOW_HIGH to "„Hallo“ ",
            SettingsManager.SMART_QUOTES_STYLE_ENGLISH_CURLY to "“Hallo” "
        )

        SettingsManager.setSmartQuotes(context, true)
        styles.forEach { (style, expected) ->
            SettingsManager.setSmartQuotesStyle(context, style)
            val inputConnection = FakeInputConnection(context, "\"Hallo\"")

            val handled = controller.handleSmartQuoteReplacement(
                typedText = " ",
                inputConnection = inputConnection,
                shouldDisableSmartPunctuation = false
            )

            assertTrue("Expected style $style to be handled", handled)
            assertEquals(expected, inputConnection.text)
        }
    }

    @Test
    fun smartQuotes_enabled_replacesBeforeHyphenAfterClosingQuote() {
        SettingsManager.setSmartQuotes(context, true)
        SettingsManager.setSmartQuotesStyle(context, SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS)
        val inputConnection = FakeInputConnection(context, "Sogenannter \"Hooligang\"")

        val handled = controller.handleSmartQuoteReplacement(
            typedText = "-",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )

        assertTrue(handled)
        assertEquals("Sogenannter »Hooligang«-", inputConnection.text)
    }

    @Test
    fun smartQuotes_enabled_supportsNarrowSpacedGuillemets() {
        SettingsManager.setSmartQuotes(context, true)
        SettingsManager.setSmartQuotesStyle(context, SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED)
        val inputConnection = FakeInputConnection(context, "\"Bonjour\"")

        val handled = controller.handleSmartQuoteReplacement(
            typedText = " ",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )

        assertTrue(handled)
        assertEquals("« Bonjour » ", inputConnection.text)
    }

    @Test
    fun smartQuotes_enabled_waitsForFollowingCharacterAfterClosingQuote() {
        SettingsManager.setSmartQuotes(context, true)
        SettingsManager.setSmartQuotesStyle(context, SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS)
        val inputConnection = FakeInputConnection(context, "\"Hallo")

        val handled = controller.handleSmartQuoteReplacement(
            typedText = "\"",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText("\"", 1)

        assertFalse(handled)
        assertEquals("\"Hallo\"", inputConnection.text)
    }

    @Test
    fun smartQuotes_settingDisabled_keepsPlainQuote() {
        SettingsManager.setSmartQuotes(context, false)
        val inputConnection = FakeInputConnection(context, "\"Hallo\"")

        val handled = controller.handleSmartQuoteReplacement(
            typedText = " ",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText(" ", 1)

        assertFalse(handled)
        assertEquals("\"Hallo\" ", inputConnection.text)
    }

    @Test
    fun smartQuotes_fieldDisabled_keepsPlainQuoteEvenWhenSettingEnabled() {
        SettingsManager.setSmartQuotes(context, true)
        SettingsManager.setSmartQuotesStyle(context, SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS)
        val inputConnection = FakeInputConnection(context, "\"Hallo\"")

        val handled = controller.handleSmartQuoteReplacement(
            typedText = " ",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = true
        )
        if (!handled) inputConnection.commitText(" ", 1)

        assertFalse(handled)
        assertEquals("\"Hallo\" ", inputConnection.text)
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)

        val text: String
            get() = buffer.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            return buffer.takeLast(n)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(deleteStart, buffer.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text ?: "")
            return true
        }
    }
}
