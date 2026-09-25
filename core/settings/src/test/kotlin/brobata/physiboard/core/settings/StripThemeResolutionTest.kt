package brobata.physiboard.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: status-bar.md SS9.2; settings-catalog.md SS2.6's scoring table. */
class StripThemeResolutionTest {

    private val chosen = StripTheme()
    private val exactLocale = StripTheme(background = 1)
    private val languageOnly = StripTheme(background = 2)
    private val layoutOnly = StripTheme(background = 3)

    @Test
    fun `no overrides resolves to the chosen theme`() {
        assertEquals(chosen, StripThemeResolution.resolve(chosen, emptyList(), "it-IT", "qwerty"))
    }

    @Test
    fun `an exact locale match outscores a language only match, per the 16-vs-8 scoring`() {
        val overrides = listOf(
            ThemeLayoutOverride(locale = "it", theme = languageOnly),
            ThemeLayoutOverride(locale = "it-IT", theme = exactLocale),
        )
        assertEquals(exactLocale, StripThemeResolution.resolve(chosen, overrides, "it-IT", "qwerty"))
    }

    @Test
    fun `locale underscores and hyphens compare equal`() {
        val overrides = listOf(ThemeLayoutOverride(locale = "it_IT", theme = exactLocale))
        assertEquals(exactLocale, StripThemeResolution.resolve(chosen, overrides, "it-IT", "qwerty"))
    }

    @Test
    fun `a layout only match wins when nothing else matches`() {
        val overrides = listOf(ThemeLayoutOverride(layout = "qwertz", theme = layoutOnly))
        assertEquals(layoutOnly, StripThemeResolution.resolve(chosen, overrides, "it-IT", "qwertz"))
        assertEquals(chosen, StripThemeResolution.resolve(chosen, overrides, "it-IT", "qwerty"))
    }

    @Test
    fun `locale plus layout together outscores locale alone`() {
        val localeOnly = ThemeLayoutOverride(locale = "de-DE", theme = StripTheme(background = 10))
        val localeAndLayout = ThemeLayoutOverride(locale = "de-DE", layout = "qwertz", theme = StripTheme(background = 20))
        val result = StripThemeResolution.resolve(chosen, listOf(localeOnly, localeAndLayout), "de-DE", "qwertz")
        assertEquals(StripTheme(background = 20), result)
    }

    @Test
    fun `the first entry wins a tie`() {
        val first = StripTheme(background = 100)
        val second = StripTheme(background = 200)
        val overrides = listOf(ThemeLayoutOverride(layout = "qwerty", theme = first), ThemeLayoutOverride(layout = "qwerty", theme = second))
        assertEquals(first, StripThemeResolution.resolve(chosen, overrides, "it-IT", "qwerty"))
    }
}
