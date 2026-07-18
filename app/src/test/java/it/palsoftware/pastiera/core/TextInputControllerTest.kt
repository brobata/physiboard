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
    fun autoCapAfterEnter_requestsFreshShiftAfterNewline_whenEnabled() {
        SettingsManager.setAutoCapitalizeFirstLetter(context, true)
        SettingsManager.setAutoCapitalizeAfterPeriod(context, false)
        val inputConnection = FakeInputConnection(context, "hello\n")

        controller.handleAutoCapAfterEnter(
            keyCode = KeyEvent.KEYCODE_ENTER,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = false,
            onStatusBarUpdate = {}
        )

        assertTrue(modifierStateController.shiftOneShot)
    }

    @Test
    fun autoCapAfterEnter_keepsShiftOffAfterNewline_whenDisabled() {
        SettingsManager.setAutoCapitalizeFirstLetter(context, false)
        SettingsManager.setAutoCapitalizeAfterPeriod(context, false)
        val inputConnection = FakeInputConnection(context, "hello\n")

        controller.handleAutoCapAfterEnter(
            keyCode = KeyEvent.KEYCODE_ENTER,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = false,
            onStatusBarUpdate = {}
        )

        assertFalse(modifierStateController.shiftOneShot)
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
    fun midWordQuoteToApostrophe_enabled_waitsForFollowingLetter() {
        SettingsManager.setMidWordQuoteToApostrophe(context, true)
        val inputConnection = FakeInputConnection(context, "qu")

        val handled = controller.handlePendingMidWordQuoteToApostrophe(
            typedText = "\"",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText("\"", 1)

        assertFalse(handled)
        assertEquals("qu\"", inputConnection.text)
    }

    @Test
    fun midWordQuoteToApostrophe_enabled_replacesPendingQuoteBeforeLetter() {
        SettingsManager.setMidWordQuoteToApostrophe(context, true)
        val inputConnection = FakeInputConnection(context, "qu\"")

        val handled = controller.handlePendingMidWordQuoteToApostrophe(
            typedText = "o",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )

        assertTrue(handled)
        assertEquals("qu'o", inputConnection.text)
    }

    @Test
    fun midWordQuoteToApostrophe_enabled_keepsOpeningQuote() {
        SettingsManager.setMidWordQuoteToApostrophe(context, true)
        val inputConnection = FakeInputConnection(context, "\"")

        val handled = controller.handlePendingMidWordQuoteToApostrophe(
            typedText = "w",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText("w", 1)

        assertFalse(handled)
        assertEquals("\"w", inputConnection.text)
    }

    @Test
    fun midWordQuoteToApostrophe_enabled_keepsQuoteBeforePunctuation() {
        SettingsManager.setMidWordQuoteToApostrophe(context, true)
        val inputConnection = FakeInputConnection(context, "qu\"")

        val handled = controller.handlePendingMidWordQuoteToApostrophe(
            typedText = ",",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText(",", 1)

        assertFalse(handled)
        assertEquals("qu\",", inputConnection.text)
    }

    @Test
    fun midWordQuoteToApostrophe_enabled_supportsUnicodeLetters() {
        SettingsManager.setMidWordQuoteToApostrophe(context, true)
        val inputConnection = FakeInputConnection(context, "dé\"")

        val handled = controller.handlePendingMidWordQuoteToApostrophe(
            typedText = "à",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )

        assertTrue(handled)
        assertEquals("dé'à", inputConnection.text)
    }

    @Test
    fun midWordQuoteToApostrophe_settingDisabled_keepsQuote() {
        SettingsManager.setMidWordQuoteToApostrophe(context, false)
        val inputConnection = FakeInputConnection(context, "qu\"")

        val handled = controller.handlePendingMidWordQuoteToApostrophe(
            typedText = "o",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText("o", 1)

        assertFalse(handled)
        assertEquals("qu\"o", inputConnection.text)
    }

    @Test
    fun midWordQuoteToApostrophe_fieldDisabled_keepsQuoteEvenWhenSettingEnabled() {
        SettingsManager.setMidWordQuoteToApostrophe(context, true)
        val inputConnection = FakeInputConnection(context, "qu\"")

        val handled = controller.handlePendingMidWordQuoteToApostrophe(
            typedText = "o",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = true
        )
        if (!handled) inputConnection.commitText("o", 1)

        assertFalse(handled)
        assertEquals("qu\"o", inputConnection.text)
    }

    @Test
    fun midWordQuoteToApostrophe_enabled_replacesQuoteBeforeLetterWhenEditing() {
        SettingsManager.setMidWordQuoteToApostrophe(context, true)
        val inputConnection = FakeInputConnection(context, "qu\"", "n")

        val handled = controller.handlePendingMidWordQuoteToApostrophe(
            typedText = "o",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )

        assertTrue(handled)
        assertEquals("qu'on", inputConnection.text)
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
    fun smartQuotes_enabled_supportsMultiWordQuotes() {
        SettingsManager.setSmartQuotes(context, true)
        SettingsManager.setSmartQuotesStyle(context, SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS)
        val inputConnection = FakeInputConnection(context, "Er sagte \"Man kann das testen\"")

        val handled = controller.handleSmartQuoteReplacement(
            typedText = " ",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )

        assertTrue(handled)
        assertEquals("Er sagte »Man kann das testen« ", inputConnection.text)
    }

    @Test
    fun smartQuotes_enabled_ignoresEmbeddedQuotesInsideWords() {
        SettingsManager.setSmartQuotes(context, true)
        SettingsManager.setSmartQuotesStyle(context, SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS)
        val inputConnection = FakeInputConnection(context, "foo\"bar\"")

        val handled = controller.handleSmartQuoteReplacement(
            typedText = " ",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText(" ", 1)

        assertFalse(handled)
        assertEquals("foo\"bar\" ", inputConnection.text)
    }

    @Test
    fun smartQuotes_enabled_waitsForDelimiterAfterClosingQuote() {
        SettingsManager.setSmartQuotes(context, true)
        SettingsManager.setSmartQuotesStyle(context, SettingsManager.SMART_QUOTES_STYLE_GERMAN_GUILLEMETS)
        val inputConnection = FakeInputConnection(context, "\"Hallo\"")

        val handled = controller.handleSmartQuoteReplacement(
            typedText = "a",
            inputConnection = inputConnection,
            shouldDisableSmartPunctuation = false
        )
        if (!handled) inputConnection.commitText("a", 1)

        assertFalse(handled)
        assertEquals("\"Hallo\"a", inputConnection.text)
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
        initialText: String,
        initialAfterCursor: String = ""
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)
        private val afterCursor = StringBuilder(initialAfterCursor)

        val text: String
            get() = buffer.toString() + afterCursor.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            return buffer.takeLast(n)
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
            return afterCursor.take(n)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(deleteStart, buffer.length)
            if (afterLength > 0) {
                afterCursor.delete(0, afterLength.coerceAtMost(afterCursor.length))
            }
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text ?: "")
            return true
        }
    }
}
