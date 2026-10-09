package brobata.physiboard.app.settings.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchCatalogTest {
    /** Every route a search result may open: the static destinations SettingsNavHost registers. */
    private val registered: Set<String> = setOf(
        Routes.TYPING, Routes.KEYS, Routes.APPS, Routes.LOOK, Routes.BACKUP, Routes.HELP, Routes.T2E_TOOLS,
        Routes.SCREEN_TRACKPAD, Routes.FN_LAYER, Routes.ACCESSIBILITY_SERVICE, Routes.PUNCTUATION_SPACING, Routes.AUTO_CORRECTION, Routes.VOICE,
        Routes.THEME, Routes.CUSTOMIZE_COLORS, Routes.SOUND_HAPTICS, Routes.ENTER_KEY_BEHAVIOUR, Routes.QUICK_LAUNCHER,
        Routes.INPUT_LANGUAGES, Routes.TEXT_EXPANSION, Routes.TEST_FIELD, Routes.LONG_PRESS, Routes.CUSTOMIZE_VARIATIONS,
        Routes.SMART_BACKLIGHT, Routes.REMOVE_BLOAT, Routes.SCREEN_DENSITY, Routes.SYSTEM_TWEAKS, Routes.NOTIFICATION_RING,
        Routes.KEY_MAPPING, Routes.STATUS, Routes.ABOUT, Routes.DIAGNOSTICS, Routes.APP_LANGUAGE, Routes.MANAGE_SNIPPETS,
        Routes.CUSTOM_SUBSTITUTIONS, Routes.ASSIGNED_LAUNCHER_KEYS, Routes.QUICK_LAUNCHER_ENTRIES, Routes.CUSTOMIZE_ENTRIES,
        Routes.CLIPBOARD_HISTORY, Routes.PRIVACY, Routes.PERSONAL_DICTIONARY, Routes.INSTALLED_DICTIONARIES, Routes.INPUT_STYLES,
        Routes.SAVED_THEMES, Routes.THEME_LAYOUT_OVERRIDES, Routes.KEYBOARD_LAYOUT, Routes.CUSTOMIZE_SYM_KEYBOARD,
        Routes.appPicker(PerAppListKind.EXACT_TYPING), Routes.appPicker(PerAppListKind.ENTER_OVERRIDES),
    )

    @Test
    fun `every result opens a screen that exists`() {
        val dangling = SearchCatalog.entries.filter { it.route !in registered }
        assertEquals(emptyList(), dangling)
    }

    @Test
    fun `titles are unique, since a result list keys its rows by title`() {
        val titles = SearchCatalog.entries.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `nothing points at the suggestion strip's settings`() {
        val strip = Regex("""\b(strip|leds?|suggestion row|suggestions while typing|hamburger|status bar)\b""", RegexOption.IGNORE_CASE)
        val hits = SearchCatalog.entries.filter { e -> strip.containsMatchIn(e.title + " " + e.keywords) }
        assertEquals(emptyList(), hits)
    }

    @Test
    fun `the words people use find the new places`() {
        assertTrue(SearchCatalog.search("backup").any { it.route == Routes.BACKUP })
        assertTrue(SearchCatalog.search("caret").any { it.route == Routes.LOOK })
        assertTrue(SearchCatalog.search("trackpad").any { it.route == Routes.SCREEN_TRACKPAD && it.screenTitle == "Keys & shortcuts" })
        assertTrue(SearchCatalog.search("smart features").any { it.route == Routes.TYPING })
        assertTrue(SearchCatalog.search("camera").any { it.route == Routes.ACCESSIBILITY_SERVICE })
        assertTrue(SearchCatalog.search("cursor").any { it.title == "Focus the text box when I start typing" })
        assertEquals(emptyList(), SearchCatalog.search("   "))
    }
}
