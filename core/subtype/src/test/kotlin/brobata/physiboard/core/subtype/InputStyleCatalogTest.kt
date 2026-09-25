package brobata.physiboard.core.subtype

import brobata.physiboard.core.keys.CtrlMappingTable
import brobata.physiboard.core.keys.DeviceLayerMap
import brobata.physiboard.core.keys.LayoutDescription
import brobata.physiboard.core.keys.LayoutMap
import brobata.physiboard.core.keys.LongPressSettings
import brobata.physiboard.core.keys.SymPageMap
import brobata.physiboard.core.settings.LanguagePrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * spec: dictionaries-languages.md SS8 (input styles), SS9 (switching); keys-and-modifiers.md
 * SS7.5's 2026-09-24 amendment (the single-layout case).
 */
class InputStyleCatalogTest {

    private val qwerty = LayoutDescription(
        baseLayout = LayoutMap(),
        deviceLayer = DeviceLayerMap(),
        emojiPage = SymPageMap(),
        symbolsPage = SymPageMap(),
        ctrlMappings = CtrlMappingTable(),
    )
    // A distinct value from [qwerty] (a different long-press threshold), so a test asserting
    // which of the two [layoutFor] returned cannot pass by accident on data-class equality alone.
    private val qwertz = qwerty.copy(longPress = LongPressSettings(thresholdMs = 999))
    private val shipped = listOf(ShippedLayout(layoutId = "qwerty", defaultLocale = "en", layout = qwerty))

    @Test
    fun `the shipped layout is available on its own with no other settings`() {
        val styles = InputStyleCatalog.availableStyles(shipped, LanguagePrefs())
        assertEquals(listOf(InputStyle("en", "qwerty", shipped = true)), styles)
    }

    @Test
    fun `a hidden shipped style drops out of the available list`() {
        val prefs = LanguagePrefs(hiddenSystemInputStyles = listOf("en:qwerty"))
        assertTrue(InputStyleCatalog.availableStyles(shipped, prefs).isEmpty())
    }

    @Test
    fun `hiding compares locale and layout independent of underscore-hyphen and case`() {
        val prefs = LanguagePrefs(hiddenSystemInputStyles = listOf("EN:QWERTY"))
        assertTrue(InputStyleCatalog.availableStyles(shipped, prefs).isEmpty())
    }

    @Test
    fun `custom input styles are appended after the shipped row in the stored order`() {
        val prefs = LanguagePrefs(inputStyles = listOf("es_ES:qwerty", "fr_FR:qwerty:extra"))
        val styles = InputStyleCatalog.availableStyles(shipped, prefs)
        assertEquals(listOf("en:qwerty", "es_ES:qwerty", "fr_FR:qwerty"), styles.map { it.key })
        assertFalse(styles[1].shipped)
    }

    @Test
    fun `a custom entry duplicating the shipped key by locale and layout is dropped, first occurrence wins`() {
        val prefs = LanguagePrefs(inputStyles = listOf("en_US:qwerty", "en:qwerty", "de_DE:qwerty"))
        val styles = InputStyleCatalog.availableStyles(shipped, prefs)
        // "en:qwerty" (this device's shipped key) collides with the shipped row and is dropped;
        // "en_US:qwerty" does not collide (a different locale string) and survives.
        assertEquals(listOf("en:qwerty", "en_US:qwerty", "de_DE:qwerty"), styles.map { it.key })
    }

    @Test
    fun `a malformed custom entry is skipped`() {
        val prefs = LanguagePrefs(inputStyles = listOf("no-colon", "  :qwerty", "de: ", "pl_PL:qwerty"))
        val styles = InputStyleCatalog.availableStyles(shipped, prefs)
        assertEquals(listOf("en:qwerty", "pl_PL:qwerty"), styles.map { it.key })
    }

    @Test
    fun `next cycles forward through the stored order and wraps`() {
        val styles = listOf(InputStyle("en", "qwerty", true), InputStyle("es", "qwerty", false), InputStyle("fr", "qwerty", false))
        assertEquals("es:qwerty", InputStyleCatalog.next(styles, "en:qwerty")?.key)
        assertEquals("fr:qwerty", InputStyleCatalog.next(styles, "es:qwerty")?.key)
        assertEquals("en:qwerty", InputStyleCatalog.next(styles, "fr:qwerty")?.key)
    }

    @Test
    fun `next answers the first row when the current key is not in the list`() {
        val styles = listOf(InputStyle("en", "qwerty", true), InputStyle("es", "qwerty", false))
        assertEquals("en:qwerty", InputStyleCatalog.next(styles, "de:qwerty")?.key)
    }

    @Test
    fun `next on an empty list is null`() {
        assertNull(InputStyleCatalog.next(emptyList(), "en:qwerty"))
    }

    @Test
    fun `current finds a match or falls back to the first row`() {
        val styles = listOf(InputStyle("en", "qwerty", true), InputStyle("es", "qwerty", false))
        assertEquals("es:qwerty", InputStyleCatalog.current(styles, "es:qwerty")?.key)
        assertEquals("en:qwerty", InputStyleCatalog.current(styles, "deleted:qwerty")?.key)
        assertNull(InputStyleCatalog.current(emptyList(), "en:qwerty"))
    }

    @Test
    fun `another style is available only with a second row (the 2026-09-24 amendment)`() {
        val one = listOf(InputStyle("en", "qwerty", true))
        val two = one + InputStyle("es", "qwerty", false)
        assertFalse(InputStyleCatalog.anotherStyleAvailable(one))
        assertTrue(InputStyleCatalog.anotherStyleAvailable(two))
        assertFalse(InputStyleCatalog.anotherStyleAvailable(emptyList()))
    }

    @Test
    fun `startup style keeps the first row when automatic layout mode is off`() {
        val styles = listOf(InputStyle("en", "qwerty", true), InputStyle("es", "qwerty", false))
        assertEquals("en:qwerty", InputStyleCatalog.startupStyle(styles, "es_ES", autoByLocale = false)?.key)
    }

    @Test
    fun `startup style matches the system locale's language when automatic mode is on`() {
        val styles = listOf(InputStyle("en", "qwerty", true), InputStyle("es", "qwerty", false))
        assertEquals("es:qwerty", InputStyleCatalog.startupStyle(styles, "es_ES", autoByLocale = true)?.key)
    }

    @Test
    fun `startup style falls back to the first row when no style matches the system locale`() {
        val styles = listOf(InputStyle("en", "qwerty", true), InputStyle("es", "qwerty", false))
        assertEquals("en:qwerty", InputStyleCatalog.startupStyle(styles, "de_DE", autoByLocale = true)?.key)
    }

    @Test
    fun `layoutFor resolves a matching shipped layout and falls back to the first when the style names an unshipped one`() {
        val shippedTwo = shipped + ShippedLayout(layoutId = "qwertz", defaultLocale = "de", layout = qwertz)
        assertEquals(qwertz, InputStyleCatalog.layoutFor(InputStyle("de", "qwertz", false), shippedTwo))
        assertEquals(qwerty, InputStyleCatalog.layoutFor(InputStyle("fr", "azerty", false), shipped))
        assertNull(InputStyleCatalog.layoutFor(InputStyle("en", "qwerty", true), emptyList()))
    }

    @Test
    fun `the switch toast has no extra segment when the style loads no extra language`() {
        assertEquals("qwerty | EN", InputStyleCatalog.switchToastText(InputStyle("en", "qwerty", true)))
    }

    @Test
    fun `the switch toast lists extra languages comma-separated after the primary`() {
        val text = InputStyleCatalog.switchToastText(InputStyle("de", "qwertz", false), listOf("fr", "en"))
        assertEquals("qwertz | DE | FR, EN", text)
    }
}
