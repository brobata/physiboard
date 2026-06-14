package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.AutoCorrector
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
class AutoCorrectionSubstitutionStoreTest {
    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun addCustomSubstitution_savesTriggerAtFront() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.saveCustomAutoCorrections(context, "en", mapOf("brb" to "be right back"))

        val saved = AutoCorrectionSubstitutionStore.addCustomSubstitution(
            context = context,
            languageCode = "en",
            trigger = "  OMW ",
            replacement = "on my way"
        )

        assertTrue(saved)
        val corrections = SettingsManager.getCustomAutoCorrections(context, "en")
        assertEquals(listOf("omw", "brb"), corrections.keys.toList())
        assertEquals("on my way", corrections["omw"])
    }

    @Test
    fun addCustomSubstitution_rejectsBlankValues() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(
            AutoCorrectionSubstitutionStore.addCustomSubstitution(
                context = context,
                languageCode = "en",
                trigger = "",
                replacement = "replacement"
            )
        )
        assertFalse(
            AutoCorrectionSubstitutionStore.addCustomSubstitution(
                context = context,
                languageCode = "en",
                trigger = "abbr",
                replacement = " "
            )
        )
    }

    @Test
    fun addCustomSubstitution_isAvailableImmediatelyToAutoCorrector() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("en"))

        AutoCorrectionSubstitutionStore.addCustomSubstitution(
            context = context,
            languageCode = "fr",
            trigger = "svp",
            replacement = "s'il vous plaît"
        )

        val correction = AutoCorrector.processText(
            textBeforeCursor = "svp",
            locale = "fr",
            context = context
        )
        assertEquals("s'il vous plaît", correction?.second)
        assertTrue(SettingsManager.getAutoCorrectEnabledLanguages(context).contains("fr"))
    }

    @Test
    fun addCustomSubstitution_replacesTssstWithTotallynewsubstitution() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCorrectEnabledLanguages(context, setOf("en"))

        AutoCorrectionSubstitutionStore.addCustomSubstitution(
            context = context,
            languageCode = "de",
            trigger = "tssst",
            replacement = "totallynewsubstitution"
        )

        val correction = AutoCorrector.processText(
            textBeforeCursor = "tssst ",
            locale = "de",
            context = context,
            isKnownWord = { false }
        )

        assertEquals("tssst", correction?.first)
        assertEquals("totallynewsubstitution", correction?.second)
        assertTrue(SettingsManager.getAutoCorrectEnabledLanguages(context).contains("de"))
    }
}
