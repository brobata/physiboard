package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeIdentityTest {

    @Test
    fun serviceClassName_usesRealImeServiceClass() {
        assertEquals(
            PhysicalKeyboardInputMethodService::class.java.name,
            ImeIdentity.serviceClassName
        )
    }

    @Test
    fun imeIdForPackage_usesFixedServiceNamespace_forNightlyPackage() {
        val nightlyPackage = "it.palsoftware.pastiera.nightly"
        val expected =
            "$nightlyPackage/${PhysicalKeyboardInputMethodService::class.java.name}"

        assertEquals(expected, ImeIdentity.imeIdForPackage(nightlyPackage))
    }

    @Test
    fun matchesImeId_acceptsFullAndShortFormats() {
        val appPackage = BuildConfig.APPLICATION_ID
        val full = ImeIdentity.imeIdForPackage(appPackage)
        val short = "$appPackage/.inputmethod.PhysicalKeyboardInputMethodService"

        assertTrue(ImeIdentity.matchesImeId(full, appPackage))
        assertTrue(ImeIdentity.matchesImeId(short, appPackage))
        assertFalse(
            ImeIdentity.matchesImeId(
                "$appPackage/${PhysicalKeyboardInputMethodService::class.java.name}.Typo",
                appPackage
            )
        )
    }
}
