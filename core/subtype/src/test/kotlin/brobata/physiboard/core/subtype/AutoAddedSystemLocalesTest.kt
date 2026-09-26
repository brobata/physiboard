package brobata.physiboard.core.subtype

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: dictionaries-languages.md SS8.5. */
class AutoAddedSystemLocalesTest {

    @Test
    fun `a system language with no dictionary and no base subtype is auto-added with its mapped layout`() {
        val result = AutoAddedSystemLocales.reconcile(
            systemLocales = listOf("hy_AM"),
            languagesWithDictionary = setOf("en"),
            inputStyles = emptyList(),
            autoAddedLocales = emptySet(),
            layoutFor = { "qwerty" },
        )
        assertEquals(listOf("hy_AM:qwerty"), result.inputStyles)
        assertEquals(setOf("hy_AM"), result.autoAddedLocales)
    }

    @Test
    fun `a system language already covered by a dictionary variant is not added`() {
        val result = AutoAddedSystemLocales.reconcile(
            systemLocales = listOf("en_GB"),
            languagesWithDictionary = setOf("en"),
            inputStyles = emptyList(),
            autoAddedLocales = emptySet(),
            layoutFor = { "qwerty" },
        )
        assertTrue(result.inputStyles.isEmpty())
    }

    @Test
    fun `a base locale is never auto-added even with no dictionary`() {
        val result = AutoAddedSystemLocales.reconcile(
            systemLocales = listOf("sr_RS"),
            languagesWithDictionary = emptySet(),
            inputStyles = emptyList(),
            autoAddedLocales = emptySet(),
            layoutFor = { "serbian_cyrillic" },
        )
        assertTrue(result.inputStyles.isEmpty())
    }

    @Test
    fun `an auto-added locale is removed once it is no longer a system language`() {
        val result = AutoAddedSystemLocales.reconcile(
            systemLocales = emptyList(),
            languagesWithDictionary = emptySet(),
            inputStyles = listOf("hy_AM:qwerty"),
            autoAddedLocales = setOf("hy_AM"),
            layoutFor = { "qwerty" },
        )
        assertTrue(result.inputStyles.isEmpty())
        assertTrue(result.autoAddedLocales.isEmpty())
    }

    @Test
    fun `a user-added style for a locale that is no longer a system language is left alone`() {
        val result = AutoAddedSystemLocales.reconcile(
            systemLocales = emptyList(),
            languagesWithDictionary = emptySet(),
            inputStyles = listOf("hy_AM:qwerty"),
            autoAddedLocales = emptySet(),
            layoutFor = { "qwerty" },
        )
        assertEquals(listOf("hy_AM:qwerty"), result.inputStyles)
    }

    @Test
    fun `a locale already present is not duplicated`() {
        val result = AutoAddedSystemLocales.reconcile(
            systemLocales = listOf("hy_AM"),
            languagesWithDictionary = emptySet(),
            inputStyles = listOf("hy_AM:qwerty"),
            autoAddedLocales = emptySet(),
            layoutFor = { "qwerty" },
        )
        assertEquals(listOf("hy_AM:qwerty"), result.inputStyles)
    }
}
