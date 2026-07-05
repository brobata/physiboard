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
