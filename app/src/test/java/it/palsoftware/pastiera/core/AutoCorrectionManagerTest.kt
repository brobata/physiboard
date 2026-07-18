package it.palsoftware.pastiera.core

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AutoCorrector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoCorrectionManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AutoSpaceTracker.clear()
    }

    @Test
    fun boundaryKeyAppliesTwoLetterCustomSubstitutionFromEnabledLanguage() {
        SettingsManager.saveCustomAutoCorrections(context, "fr", mapOf("ct" to "c'était"))
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("fr"))
        AutoCorrector.loadCorrections(context.assets, context)
        val inputConnection = FakeInputConnection(context, "Ct")

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            isKnownWord = { it.equals("ct", ignoreCase = true) },
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("C'était ", inputConnection.text)
    }

    @Test
    fun boundaryKeyAppliesBundledFrenchTwoLetterSubstitution() {
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("fr"))
        AutoCorrector.loadCorrections(context.assets, context)
        val inputConnection = FakeInputConnection(context, "Ct")

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE),
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            isKnownWord = { it.equals("ct", ignoreCase = true) },
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("C'était ", inputConnection.text)
    }

    @Test
    fun boundaryKeyAppliesFrenchSpacingAfterSubstitution() {
        SettingsManager.saveCustomAutoCorrections(context, "fr", mapOf("ct" to "c'était"))
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("fr"))
        SettingsManager.setFrenchPunctuationSpacing(context, true)
        AutoCorrector.loadCorrections(context.assets, context)
        val inputConnection = FakeInputConnection(context, "Ct")

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            event = null,
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            boundaryCharOverride = '?',
            isKnownWord = { it.equals("ct", ignoreCase = true) },
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("C'était ?", inputConnection.text)
    }

    @Test
    fun commaSpaceAddsTrailingSpaceWithoutAutoCorrect() {
        SettingsManager.setCommaSpace(context, true)
        val inputConnection = FakeInputConnection(context, "Hi")

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_COMMA,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COMMA),
            inputConnection = inputConnection,
            isAutoCorrectEnabled = false,
            commitBoundary = true,
            boundaryCharOverride = ',',
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("Hi, ", inputConnection.text)
    }

    @Test
    fun commaSpaceReplacesPrecedingSpace() {
        SettingsManager.setCommaSpace(context, true)
        val inputConnection = FakeInputConnection(context, "Hi ")

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_COMMA,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COMMA),
            inputConnection = inputConnection,
            isAutoCorrectEnabled = false,
            commitBoundary = true,
            boundaryCharOverride = ',',
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("Hi, ", inputConnection.text)
    }

    @Test
    fun commaSpaceDoesNotDuplicateAlreadyCommittedComma() {
        SettingsManager.setCommaSpace(context, true)
        val inputConnection = FakeInputConnection(context, "Hi,")

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_COMMA,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COMMA),
            inputConnection = inputConnection,
            isAutoCorrectEnabled = false,
            commitBoundary = true,
            boundaryCharOverride = ',',
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("Hi, ", inputConnection.text)
    }

    @Test
    fun commaSpaceCleansSpaceBeforeAlreadyCommittedComma() {
        SettingsManager.setCommaSpace(context, true)
        val inputConnection = FakeInputConnection(context, "Hi ,")

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_COMMA,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COMMA),
            inputConnection = inputConnection,
            isAutoCorrectEnabled = false,
            commitBoundary = true,
            boundaryCharOverride = ',',
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("Hi, ", inputConnection.text)
    }

    @Test
    fun commaSpaceDoesNotAffectPeriod() {
        SettingsManager.setCommaSpace(context, true)
        val inputConnection = FakeInputConnection(context, "Hi")

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_PERIOD,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PERIOD),
            inputConnection = inputConnection,
            isAutoCorrectEnabled = false,
            commitBoundary = true,
            boundaryCharOverride = '.',
            onStatusBarUpdate = {}
        )

        assertEquals(false, handled)
        assertEquals("Hi", inputConnection.text)
    }

    @Test
    fun pendingAutoSpaceBeforeColonThenParenKeepsSmileySpacingByDefault() {
        val inputConnection = FakeInputConnection(context, "text ")
        AutoSpaceTracker.markAutoSpace()

        val colonHandled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            event = null,
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            boundaryCharOverride = ':',
            onStatusBarUpdate = {}
        )
        if (!colonHandled) {
            inputConnection.commitText(":", 1)
        }
        val parenHandled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            event = null,
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            boundaryCharOverride = ')',
            onStatusBarUpdate = {}
        )
        if (!parenHandled) {
            inputConnection.commitText(")", 1)
        }

        assertEquals(false, colonHandled)
        assertEquals("text :)", inputConnection.text)
    }

    @Test
    fun pendingAutoSpaceBeforeColonThenParenCanUseUserEnabledColonSpacing() {
        SettingsManager.setAutoSpacePunctuation(
            context,
            SettingsManager.getAutoSpacePunctuation(context) + ":"
        )
        val inputConnection = FakeInputConnection(context, "text ")
        AutoSpaceTracker.markAutoSpace()

        val colonHandled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            event = null,
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            boundaryCharOverride = ':',
            onStatusBarUpdate = {}
        )
        val parenHandled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            event = null,
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            boundaryCharOverride = ')',
            onStatusBarUpdate = {}
        )
        if (!parenHandled) {
            inputConnection.commitText(")", 1)
        }

        assertTrue(colonHandled)
        assertEquals("text: )", inputConnection.text)
    }

    @Test
    fun pendingAutoSpaceBeforeSelectedClosingParen_movesSpaceAfterParen() {
        SettingsManager.setAutoSpacePunctuation(context, ")")
        val inputConnection = FakeInputConnection(context, "da wären wir ")
        AutoSpaceTracker.markAutoSpace()

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            event = null,
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            boundaryCharOverride = ')',
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("da wären wir) ", inputConnection.text)
        assertEquals(false, AutoSpaceTracker.isPending())
    }

    @Test
    fun pendingAutoSpaceBeforeOpeningQuote_keepsSpaceBeforeQuote() {
        SettingsManager.setAutoSpacePunctuation(context, "\"")
        val inputConnection = FakeInputConnection(context, "Ceci est un guillemet ")
        AutoSpaceTracker.markAutoSpace()

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            event = null,
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            boundaryCharOverride = '"',
            onStatusBarUpdate = {}
        )
        if (!handled) {
            inputConnection.commitText("\"", 1)
        }

        assertEquals(false, handled)
        assertEquals("Ceci est un guillemet \"", inputConnection.text)
        assertEquals(false, AutoSpaceTracker.isPending())
    }

    @Test
    fun pendingAutoSpaceBeforeClosingQuote_movesSpaceAfterQuote() {
        SettingsManager.setAutoSpacePunctuation(context, "\"")
        val inputConnection = FakeInputConnection(context, "\"bonjour ")
        AutoSpaceTracker.markAutoSpace()

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            event = null,
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            boundaryCharOverride = '"',
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("\"bonjour\" ", inputConnection.text)
        assertEquals(false, AutoSpaceTracker.isPending())
    }

    @Test
    fun attachedStrayQuote_doesNotTurnFollowingOpeningQuoteIntoClosingQuote() {
        SettingsManager.setAutoSpacePunctuation(context, "\"")
        val inputConnection = FakeInputConnection(context, "Erster Versuch\" Zweiter Versuch ")
        AutoSpaceTracker.markAutoSpace()

        val handled = AutoCorrectionManager(context).handleBoundaryKey(
            keyCode = KeyEvent.KEYCODE_UNKNOWN,
            event = null,
            inputConnection = inputConnection,
            isAutoCorrectEnabled = true,
            commitBoundary = true,
            boundaryCharOverride = '"',
            onStatusBarUpdate = {}
        )
        if (!handled) {
            inputConnection.commitText("\"", 1)
        }

        assertEquals(false, handled)
        assertEquals("Erster Versuch\" Zweiter Versuch \"", inputConnection.text)
        assertEquals(false, AutoSpaceTracker.isPending())
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

        override fun finishComposingText(): Boolean = true
    }
}
