package it.palsoftware.pastiera.data.variation

import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VariationRepositoryLayoutOverrideTest {

    @After
    fun tearDown() {
        File(RuntimeEnvironment.getApplication().filesDir, "variations.json").delete()
    }

    @Test
    fun qwertz_prioritizesGermanUmlautAndPercentVariations() {
        val context = RuntimeEnvironment.getApplication()

        val variations = VariationRepository.loadVariations(
            assets = context.assets,
            context = context,
            activeLayoutName = "qwertz"
        )

        assertEquals("ü", variations['u']?.first())
        assertEquals("Ü", variations['U']?.first())
        assertEquals("ö", variations['o']?.first())
        assertEquals("ä", variations['a']?.first())
        assertEquals("%", variations['p']?.first())
        assertEquals("%", variations['P']?.first())
    }

    @Test
    fun customVariationFile_isEnrichedWithBundledGermanLayoutOverrides() {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "variations.json").writeText(
            """
            {
              "variations": {
                "u": ["ù", "ú"],
                "U": ["Ù", "Ú"]
              }
            }
            """.trimIndent()
        )

        val variations = VariationRepository.loadVariations(
            assets = context.assets,
            context = context,
            activeLayoutName = "qwertz"
        )

        assertEquals("ü", variations['u']?.first())
        assertEquals("Ü", variations['U']?.first())
        assertTrue(variations['u'].orEmpty().contains("ù"))
        assertEquals("%", variations['p']?.first())
    }

    @Test
    fun saveStaticVariationBasePreset_updatesOnlyBaseStaticRow() {
        val context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "variations.json").writeText(
            """
            {
              "variations": {
                "a": ["á"]
              },
              "staticVariationsShift": ["{"],
              "staticVariationsAlt": ["<"]
            }
            """.trimIndent()
        )

        SettingsManager.saveStaticVariationBasePreset(
            context,
            SettingsManager.getDevChoiceStaticVariationBasePreset()
        )

        assertEquals(
            listOf("»", "«", ";", "!", "?", ",", ".", "–", "%"),
            VariationRepository.loadStaticVariations(context.assets, context)
        )
        assertEquals(listOf("{"), VariationRepository.loadStaticVariationsShift(context.assets, context))
        assertEquals(listOf("<"), VariationRepository.loadStaticVariationsAlt(context.assets, context))
        assertEquals(listOf("á"), VariationRepository.loadVariations(context.assets, context)['a'])
    }
}
