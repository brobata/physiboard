package it.palsoftware.pastiera.inputmethod.subtype

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdditionalSubtypeUtilsLayoutTest {

    @Test
    fun germanLocales_resolveToGermanMultitapQwertz() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("german_multitap_qwertz", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "de", context))
        assertEquals("german_multitap_qwertz", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "de_DE", context))
        assertEquals("german_multitap_qwertz", AdditionalSubtypeUtils.getLayoutForLocale(context.assets, "de-AT", context))
    }

    @Test
    fun additionalSubtypes_skipBaseLocaleDuplicateLayout() {
        val context = RuntimeEnvironment.getApplication()

        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "en_US:qwerty",
            context.assets,
            context
        )

        assertEquals(0, subtypes.size)
    }

    @Test
    fun additionalSubtypes_keepSameLocaleDifferentLayout() {
        val context = RuntimeEnvironment.getApplication()

        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "en_US:vietnamese_telex_qwerty",
            context.assets,
            context
        )

        assertEquals(1, subtypes.size)
        assertEquals("vietnamese_telex_qwerty", AdditionalSubtypeUtils.getKeyboardLayoutFromSubtype(subtypes[0]))
    }

    @Test
    fun subtypeMatching_usesLocaleAndLayout() {
        val context = RuntimeEnvironment.getApplication()
        val subtypes = AdditionalSubtypeUtils.createAdditionalSubtypesArray(
            "en_US:vietnamese_telex_qwerty",
            context.assets,
            context
        )

        assertEquals(
            true,
            AdditionalSubtypeUtils.matchesLocaleAndKeyboardLayoutSet(
                subtypes[0],
                "en_US",
                "vietnamese_telex_qwerty"
            )
        )
        assertEquals(
            false,
            AdditionalSubtypeUtils.matchesLocaleAndKeyboardLayoutSet(
                subtypes[0],
                "en_US",
                "qwerty"
            )
        )
    }
}
