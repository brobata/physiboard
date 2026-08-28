package brobata.physiboard

import android.content.Context
import brobata.physiboard.inputmethod.expansion.ExpansionPresentation
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
        assertFalse(policy.acceptPrefixWithSpace)
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

    @Test
    fun prefixSpaceOptionRoundTrips() {
        // Once paired with an emoji/symbol policy to prove the two round-tripped independently.
        // Emoji and symbol shortcodes were removed in 2.0, leaving snippets as the only policy.
        val snippetPolicy = SettingsManager.getSnippetsActivationPolicy(context).copy(
            acceptPrefixWithSpace = true
        )

        SettingsManager.setSnippetsActivationPolicy(context, snippetPolicy)

        assertEquals(snippetPolicy, SettingsManager.getSnippetsActivationPolicy(context))
    }
}
