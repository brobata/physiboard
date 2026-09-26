package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: dictionaries-languages.md SS3 and SS16's T1-T4, T11. */
class DictionaryTierResolverTest {

    @Test
    fun `T1 empty tiers resolve to nothing`() {
        assertNull(DictionaryTierResolver.resolve(imported = false, downloaded = false, bundled = false))
    }

    @Test
    fun `T2 a download resolves to the downloaded origin`() {
        assertEquals(
            DictionaryOrigin.DOWNLOADED,
            DictionaryTierResolver.resolve(imported = false, downloaded = true, bundled = false),
        )
    }

    @Test
    fun `T3 an import outranks a download`() {
        assertEquals(
            DictionaryOrigin.IMPORTED,
            DictionaryTierResolver.resolve(imported = true, downloaded = true, bundled = false),
        )
    }

    @Test
    fun `an import outranks a bundled asset too`() {
        assertEquals(
            DictionaryOrigin.IMPORTED,
            DictionaryTierResolver.resolve(imported = true, downloaded = false, bundled = true),
        )
    }

    @Test
    fun `a download outranks a bundled asset`() {
        assertEquals(
            DictionaryOrigin.DOWNLOADED,
            DictionaryTierResolver.resolve(imported = false, downloaded = true, bundled = true),
        )
    }

    @Test
    fun `the bundled asset resolves only when nothing else does`() {
        assertEquals(
            DictionaryOrigin.BUNDLED,
            DictionaryTierResolver.resolve(imported = false, downloaded = false, bundled = true),
        )
    }

    @Test
    fun `hasDictionary matches whether resolve found anything, per SS3`() {
        assertTrue(DictionaryTierResolver.hasDictionary(imported = false, downloaded = false, bundled = true))
        assertFalse(DictionaryTierResolver.hasDictionary(imported = false, downloaded = false, bundled = false))
    }
}
