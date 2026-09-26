package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: dictionaries-languages.md SS11 ("The app's own interface language"). */
class AppLocaleTest {

    @Test
    fun `blank or absent app_language_tag resolves to system default`() {
        assertNull(AppLocale.resolve(""))
        assertNull(AppLocale.resolve("   "))
    }

    @Test
    fun `an unknown tag also falls back to system default rather than crashing`() {
        assertNull(AppLocale.resolve("zz"))
    }

    @Test
    fun `a known tag resolves unchanged`() {
        assertEquals("de", AppLocale.resolve("de"))
        assertEquals("de", AppLocale.resolve(" de "))
    }

    @Test
    fun `the ten options are in the spec's fixed order`() {
        assertEquals(listOf("en", "it", "de", "es", "fr", "pl", "ru", "uk", "vi", "hy"), AppLocale.SUPPORTED_TAGS)
    }

    @Test
    fun `a language's label is its own native name when the UI already speaks it`() {
        assertEquals("Deutsch", AppLocale.optionLabel("de", uiLanguageTag = "de"))
    }

    @Test
    fun `a language's label adds the UI-language name when it differs, native name first`() {
        val label = AppLocale.optionLabel("de", uiLanguageTag = "en")
        assertEquals("Deutsch - German", label)
    }

    @Test
    fun `english labelled from an english UI is just the native name, no dash`() {
        assertEquals("English", AppLocale.optionLabel("en", uiLanguageTag = "en"))
    }
}
