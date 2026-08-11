package it.palsoftware.pastiera

import android.content.Context
import it.palsoftware.pastiera.inputmethod.expansion.ExpansionPresentation
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
class SettingsManagerTextExpansionTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        SettingsManager.getPreferences(context).edit().clear().commit()
    }

    @Test
    fun snippetDefaults_matchTheActivationContract() {
        assertFalse(SettingsManager.getSnippetsEnabled(context))
        assertEquals("!", SettingsManager.getSnippetsPrefix(context))
        assertEquals(ExpansionPresentation.FLOATING_POPUP, SettingsManager.getSnippetsPresentation(context))
        val policy = SettingsManager.getSnippetsActivationPolicy(context)
        assertTrue(policy.exactOnSpace)
        assertTrue(policy.acceptWithTab)
        assertFalse(policy.acceptWithEnter)
    }

    @Test
    fun snippetsRoundTripMultilineTextAndSurroundingWhitespaceExactly() {
        val replacement = "  first line\nsecond line\n  "
        SettingsManager.saveSnippets(context, linkedMapOf("sig" to replacement))
        assertEquals(replacement, SettingsManager.getSnippets(context)["sig"])
    }

    @Test
    fun invalidStoredPresentationFallsBackToFloatingPopup() {
        SettingsManager.getPreferences(context).edit()
            .putString("snippets_presentation", "future-value")
            .commit()
        assertEquals(ExpansionPresentation.FLOATING_POPUP, SettingsManager.getSnippetsPresentation(context))
    }
}
