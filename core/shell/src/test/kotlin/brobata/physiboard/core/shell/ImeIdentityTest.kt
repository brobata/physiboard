package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: app-shell.md SS1, SS8.2, SS29 T20. */
class ImeIdentityTest {

    private val pkg = "brobata.physiboard"
    private val service = "brobata.physiboard.inputmethod.PhysicalKeyboardInputMethodService"

    @Test
    fun `T20 the long and short id forms both match, blank and another id do not`() {
        assertTrue(ImeIdentity.matches("brobata.physiboard/brobata.physiboard.inputmethod.PhysicalKeyboardInputMethodService", pkg, service))
        assertTrue(ImeIdentity.matches("brobata.physiboard/.inputmethod.PhysicalKeyboardInputMethodService", pkg, service))
        assertFalse(ImeIdentity.matches("", pkg, service))
        assertFalse(ImeIdentity.matches(null, pkg, service))
        assertFalse(ImeIdentity.matches("other/x", pkg, service))
    }

    @Test
    fun `not enabled short-circuits the probe to not selected`() {
        val result = ImeProbe.evaluate(
            enabledInList = false,
            packageName = pkg,
            serviceClassName = service,
            outcome = SecureSettingOutcome.Read(ImeIdentity.longId(pkg, service)),
            currentSubtypeExists = true,
            enabledInputMethodCount = 1,
        )
        assertEquals(ImeProbeResult(enabled = false, selected = false), result)
    }

    @Test
    fun `a security exception falls back to the sole-enabled-ime rule for the shared variant`() {
        val soleEnabled = ImeProbe.evaluate(
            enabledInList = true,
            packageName = pkg,
            serviceClassName = service,
            outcome = SecureSettingOutcome.SecurityException,
            currentSubtypeExists = true,
            enabledInputMethodCount = 1,
        )
        assertTrue(soleEnabled.selected)

        val twoEnabled = ImeProbe.evaluate(
            enabledInList = true,
            packageName = pkg,
            serviceClassName = service,
            outcome = SecureSettingOutcome.SecurityException,
            currentSubtypeExists = true,
            enabledInputMethodCount = 2,
        )
        assertFalse(twoEnabled.selected)
    }
}
