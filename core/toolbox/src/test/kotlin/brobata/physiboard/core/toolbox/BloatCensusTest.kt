package brobata.physiboard.core.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals

class BloatCensusTest {
    @Test
    fun `T9 census splits enabled disabled and everything into per-package state`() {
        val raw = "__E__\npackage:a\npackage:b\n__D__\npackage:c\n__U__\npackage:a\npackage:b\npackage:c\npackage:d\n"
        val catalog = listOf("a", "c", "d", "e").map { BloatEntry(it, it, BloatTier.VENDOR, "") }
        val result = BloatCensus.parse(raw, catalog)
        assertEquals(BloatState.ACTIVE, result.states["a"])
        assertEquals(BloatState.DISABLED, result.states["c"])
        assertEquals(BloatState.UNINSTALLED, result.states["d"])
        assertEquals(BloatState.ABSENT, result.states["e"])
    }

    @Test
    fun `unrecognized vendor packages are listed sorted and never include catalogued or protected ones`() {
        val raw = "__E__\npackage:com.agui.zzz\npackage:com.agui.aaa\npackage:com.agui.keyboard\n__D__\n__U__\npackage:com.agui.zzz\npackage:com.agui.aaa\npackage:com.agui.keyboard\n"
        val result = BloatCensus.parse(raw, emptyList())
        assertEquals(listOf("com.agui.aaa", "com.agui.zzz"), result.unrecognizedVendorPackages)
    }
}
