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
}
