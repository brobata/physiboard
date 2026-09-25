package brobata.physiboard.core.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BloatCatalogTest {
    private val deniedNames = listOf(
        "com.agui.shortcutsettings", "com.agui.settings", "com.agui.update", "com.agui.spacebarkey",
        "com.agui.esim.service", "com.agui.keyboard", "com.agui.overlay.kika", "com.agold.networkmanager.service",
        "com.agold.networkmanager.ui", "com.agui.systemui.fixed_status_bar_icon_size",
        "com.agui.internal.fixed_status_bar_icon_size", "com.iqqijni.bbkeyboard",
    )

    @Test
    fun `T1 removable is false for every denylisted name`() {
        assertEquals(12, deniedNames.size)
        deniedNames.forEach { assertFalse(BloatCatalog.isRemovable(it), it) }
    }

    @Test
    fun `T2 removable is false for pattern-denied packages`() {
        listOf(
            "com.google.android.projection.gearhead.agui.overlay",
            "com.agui.google.android.wifi.resources.overlay",
            "com.android.systemui",
            "com.android.phone",
            "com.google.android.gms",
            "com.android.dialer",
            "com.android.mms",
        ).forEach { assertFalse(BloatCatalog.isRemovable(it), it) }
    }

    @Test
    fun `T3 removable is false for anything outside the catalog`() {
        listOf("com.whatsapp", "brobata.physiboard", "", "com.agui.somethingInvented").forEach {
            assertFalse(BloatCatalog.isRemovable(it), it)
        }
    }

    @Test
    fun `T4 removable is true for every catalog entry and none is also denied`() {
        BloatCatalog.entries.forEach { entry ->
            assertTrue(BloatCatalog.isRemovable(entry.packageName), entry.packageName)
            assertFalse(BloatCatalog.isProtected(entry.packageName), entry.packageName)
        }
    }

    @Test
    fun `T5 the catalog is 28 unique entries ordered by tier then label`() {
        assertEquals(28, BloatCatalog.entries.size)
        assertEquals(BloatCatalog.entries.size, BloatCatalog.entries.map { it.packageName }.toSet().size)
        val tierOrder = BloatCatalog.entries.map { it.tier }
        assertEquals(tierOrder.sortedBy { it.ordinal }, tierOrder)
        BloatTier.entries.forEach { tier ->
            val labels = BloatCatalog.entries.filter { it.tier == tier }.map { it.label }
            assertEquals(labels.sortedBy { it.lowercase() }, labels, "tier $tier not alphabetical")
        }
    }

    @Test
    fun `T6 the background killers preset is exactly the 6 packages from SS12_3`() {
        val expected = setOf(
            "com.debug.loggerui", "com.agui.systemmanager", "com.agui.aguigrabageclear",
            "com.agui.frozen", "com.agui.appblock", "com.agold.autopoweronoff",
        )
        val actual = BloatCatalog.entriesForPreset(BloatPresetTag.BACKGROUND_KILLERS).map { it.packageName }.toSet()
        assertEquals(expected, actual)
    }

    @Test
    fun `T7 vendor namespace recognizes the vendor prefixes only`() {
        assertTrue(BloatCatalog.isVendorNamespace("com.agui.x"))
        assertTrue(BloatCatalog.isVendorNamespace("com.devices116"))
        assertTrue(BloatCatalog.isVendorNamespace("com.tiqiaa.icontrol"))
        assertFalse(BloatCatalog.isVendorNamespace("com.whatsapp"))
    }

    @Test
    fun `T8 catalogued is true for protected-pattern packages and false for an unknown one`() {
        assertTrue(BloatCatalog.isCatalogued("com.agui.overlay.foo"))
        assertTrue(BloatCatalog.isCatalogued("com.android.fmradio"))
        assertFalse(BloatCatalog.isCatalogued("com.agui.newthing"))
    }

    @Test
    fun `package name format rejects shell metacharacters`() {
        assertTrue(PackageNameFormat.isValid("com.google.android.apps.bard"))
        assertTrue(PackageNameFormat.isValid("brobata.physiboard"))
        assertFalse(PackageNameFormat.isValid("x; rm"))
        assertFalse(PackageNameFormat.isValid("com.agui.game; rm -rf /"))
    }

    @Test
    fun `T15 mutate on a non-Titan profile is refused before any name check`() {
        val gate = BloatMutationChecker.check("com.agui.game", isTitanElite = false)
        assertEquals(BloatMutationGate.Refused(BloatMutationMessages.WRONG_DEVICE), gate)
    }

    @Test
    fun `T16 mutate with shell metacharacters is refused as not a package name`() {
        val gate = BloatMutationChecker.check("com.agui.game; rm -rf /", isTitanElite = true)
        assertEquals(BloatMutationGate.Refused(BloatMutationMessages.NOT_A_PACKAGE_NAME), gate)
    }

    @Test
    fun `a protected package is refused even on a Titan Elite`() {
        val gate = BloatMutationChecker.check("com.agui.keyboard", isTitanElite = true)
        assertEquals(BloatMutationGate.Refused(BloatMutationMessages.PROTECTED), gate)
    }

    @Test
    fun `a catalogued removable package is allowed`() {
        val gate = BloatMutationChecker.check(BloatCatalog.entries.first().packageName, isTitanElite = true)
        assertEquals(BloatMutationGate.Allowed, gate)
    }
}
