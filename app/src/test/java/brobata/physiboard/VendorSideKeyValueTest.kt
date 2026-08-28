package brobata.physiboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The side-key restore path reads values back out of `Settings.System` — writable by any app
 * holding WRITE_SETTINGS — and can hand them to the adb broker, which runs at shell privilege.
 * Only plain component names may make that trip.
 */
class VendorSideKeyValueTest {

    @Test
    fun `accepts the vendor's real values`() {
        assertTrue(VendorSideKeyManager.isSafeValue("com.google.android.apps.bard"))
        assertTrue(
            VendorSideKeyManager.isSafeValue(
                "com.google.android.apps.bard.shellapp.BardEntryPointActivity"
            )
        )
        assertTrue(VendorSideKeyManager.isSafeValue("brobata.physiboard"))
        assertTrue(VendorSideKeyManager.isSafeValue("shortcut_function_home"))
        assertTrue(VendorSideKeyManager.isSafeValue("1"))
        // Nested classes are legal component names.
        assertTrue(VendorSideKeyManager.isSafeValue("com.example.Outer\$Inner"))
    }

    @Test
    fun `refuses shell metacharacters`() {
        listOf(
            "com.x; rm -rf /sdcard",
            "com.x && id",
            "com.x | sh",
            "com.x\$(id)",
            "com.x`id`",
            "com.x\nsettings put system foo bar",
            "com.x > /data/local/tmp/pwn",
            "com.x 'quoted'",
            "com.x & background"
        ).forEach { payload ->
            assertFalse("should refuse: $payload", VendorSideKeyManager.isSafeValue(payload))
        }
    }

    @Test
    fun `refuses empty, absent and oversized values`() {
        assertFalse(VendorSideKeyManager.isSafeValue(null))
        assertFalse(VendorSideKeyManager.isSafeValue(""))
        assertFalse(VendorSideKeyManager.isSafeValue(" "))
        // A leading dot is not a component name either.
        assertFalse(VendorSideKeyManager.isSafeValue(".com.example"))
        assertFalse(VendorSideKeyManager.isSafeValue("a".repeat(257)))
    }
}
