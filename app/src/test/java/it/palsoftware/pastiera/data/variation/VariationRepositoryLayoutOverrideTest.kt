package it.palsoftware.pastiera.data.variation

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
    fun germanMultitapQwertz_prioritizesGermanUmlautVariations() {
        val context = RuntimeEnvironment.getApplication()

        val variations = VariationRepository.loadVariations(
            assets = context.assets,
            context = context,
            activeLayoutName = "german_multitap_qwertz"
        )

        assertEquals("ü", variations['u']?.first())
        assertEquals("Ü", variations['U']?.first())
        assertEquals("ö", variations['o']?.first())
        assertEquals("ä", variations['a']?.first())
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
            activeLayoutName = "german_multitap_qwertz"
        )

        assertEquals("ü", variations['u']?.first())
        assertEquals("Ü", variations['U']?.first())
        assertTrue(variations['u'].orEmpty().contains("ù"))
    }
}
