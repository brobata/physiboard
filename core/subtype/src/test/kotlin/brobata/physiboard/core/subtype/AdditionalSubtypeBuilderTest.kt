package brobata.physiboard.core.subtype

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: dictionaries-languages.md SS8.3 and SS16's T23-T26. */
class AdditionalSubtypeBuilderTest {

    @Test
    fun `T23 a custom entry matching a base locale and its own mapped layout registers nothing`() {
        val specs = AdditionalSubtypeBuilder.build(listOf("en_US:qwerty"))
        assertTrue(specs.isEmpty())
    }

    @Test
    fun `T24 a custom entry on a base locale with a different layout registers one subtype`() {
        val specs = AdditionalSubtypeBuilder.build(listOf("en_US:vietnamese_telex_qwerty"))
        val spec = specs.single()
        assertEquals("en_US", spec.locale)
        assertEquals("vietnamese_telex_qwerty", spec.layoutId)
        assertTrue("KeyboardLayoutSet=vietnamese_telex_qwerty" in spec.extraValue)
        assertTrue("AsciiCapable" in spec.extraValue)
        assertTrue("EmojiCapable" in spec.extraValue)
        assertTrue("isAdditionalSubtype" in spec.extraValue)
    }

    @Test
    fun `T25 an unknown layout, an empty entry and a base-redundant entry are all skipped`() {
        val specs = AdditionalSubtypeBuilder.build(listOf("xx_YY:qwerty", "fr_FR:nosuchlayout", "", "de_DE:qwertz"))
        val spec = specs.single()
        assertEquals("xx_YY", spec.locale)
        assertEquals("qwerty", spec.layoutId)
    }

    @Test
    fun `T26 the same locale and layout produce identical non-zero ids each time`() {
        val a = AdditionalSubtypeBuilder.stableId("de_DE", "qwertz")
        val b = AdditionalSubtypeBuilder.stableId("de_DE", "qwertz")
        assertEquals(a, b)
        assertTrue(a != 0)
    }

    @Test
    fun `an extra third part is appended to the extra value`() {
        val specs = AdditionalSubtypeBuilder.build(listOf("en_US:vietnamese_telex_qwerty:x-pastiera"))
        assertTrue(specs.single().extraValue.endsWith(",x-pastiera"))
    }

    @Test
    fun `a locale that fails the format check is skipped`() {
        assertTrue(AdditionalSubtypeBuilder.build(listOf("!!:qwerty")).isEmpty())
    }
}
