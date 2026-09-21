package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: autocorrect-suggestions.md SS5, "Test cases" T17. */
class CasingRulesTest {

    @Test
    fun `T17 casing follows the typed word`() {
        assertEquals("apple", CasingRules.forTypedWord("app", "apple"))
        assertEquals("Apple", CasingRules.forTypedWord("App", "apple"))
        assertEquals("APPLE", CasingRules.forTypedWord("APP", "apple"))
        assertEquals("McCartney", CasingRules.forTypedWord("mcc", "McCartney"))
        assertEquals("MCCARTNEY", CasingRules.forTypedWord("MCC", "McCartney"))
        assertEquals("L'amico", CasingRules.forTypedWord("L'am", "l'amico"))
        assertEquals("123", CasingRules.forTypedWord("12", "123"))
    }

    @Test
    fun `T17 forced completion still capitalizes with the auto-capitalize override`() {
        assertEquals("Apple", CasingRules.forTappedSuggestion("app", "apple", autoCapitalizeOverride = true))
    }
}
