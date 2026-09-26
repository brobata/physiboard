package brobata.physiboard.core.subtype

import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks [BundledLayoutCatalog] against [BundledLayoutIds.ALL]: same eighteen ids, one row each. */
class BundledLayoutCatalogTest {

    @Test
    fun `every bundled layout id has exactly one catalogue row`() {
        assertEquals(BundledLayoutIds.ALL, BundledLayoutCatalog.ALL.map { it.id }.toSet())
        assertEquals(BundledLayoutIds.ALL.size, BundledLayoutCatalog.ALL.size)
    }

    @Test
    fun `an unknown id falls back to itself rather than crashing`() {
        val info = BundledLayoutCatalog.infoFor("not_a_real_layout")
        assertEquals("not_a_real_layout", info.id)
        assertEquals("not_a_real_layout", info.displayName)
    }

    @Test
    fun `qwerty is named Standard's own display text`() {
        assertEquals("QWERTY", BundledLayoutCatalog.infoFor("qwerty").displayName)
    }
}
