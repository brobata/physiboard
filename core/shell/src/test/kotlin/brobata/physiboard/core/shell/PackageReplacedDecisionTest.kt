package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: app-shell.md SS17, SS29 T21. Not wired to a receiver in 3.0 (SS30 Keep/Drop); the predicate is still pinned down. */
class PackageReplacedDecisionTest {

    private val pkg = "brobata.physiboard"
    private val service = "brobata.physiboard.inputmethod.PhysicalKeyboardInputMethodService"

    @Test
    fun `T21 a stale component posts the notice, the current one does not`() {
        assertTrue(
            PackageReplacedDecision.shouldNotify(
                "brobata.physiboard/it.palsoftware.pastiera.inputmethod.PhysicalKeyboardInputMethodService",
                pkg,
                service,
            ),
        )
        assertFalse(PackageReplacedDecision.shouldNotify(ImeIdentity.longId(pkg, service), pkg, service))
    }
}
