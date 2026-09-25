package brobata.physiboard.device.privileged.toolbox

import brobata.physiboard.core.toolbox.PendingRevert
import brobata.physiboard.device.privileged.FakeShell
import brobata.physiboard.device.privileged.broker.ShellResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DisplayDensityControllerTest {
    @Test
    fun `T17 reads the physical density when nothing is overridden`() {
        val shell = FakeShell().apply { responses["wm density"] = ShellResult.Ok("Physical density: 300") }
        val outcome = DisplayDensityController(shell, InMemoryToolboxStateStore()).read() as DensityReadOutcome.Loaded
        assertEquals(300, outcome.reading.physicalDpi)
        assertEquals(300, outcome.reading.currentDpi)
    }

    @Test
    fun `T21 applying arms the pending revert before the write and clears it on failure`() {
        val shell = FakeShell()
        val store = InMemoryToolboxStateStore()
        val outcome = DisplayDensityController(shell, store).apply(300, 260)

        assertEquals(DensityApplyOutcome.Applied, outcome)
        assertEquals(PendingRevert("display_density", "wm density 260", "wm density reset"), store.pendingRevert())
        assertEquals(listOf("wm density 260"), shell.lines)
    }

    @Test
    fun `T22 a failed apply removes the pending record`() {
        val shell = FakeShell().apply { responses["wm density 260"] = ShellResult.Failed("boom") }
        val store = InMemoryToolboxStateStore()
        val outcome = DisplayDensityController(shell, store).apply(300, 260)
        assertEquals(DensityApplyOutcome.Failed("boom"), outcome)
        assertNull(store.pendingRevert())
    }

    @Test
    fun `T20 applying outside the safe range never touches the shell`() {
        val shell = FakeShell()
        val outcome = DisplayDensityController(shell, InMemoryToolboxStateStore()).apply(300, 170)
        assertEquals(DensityApplyOutcome.Refused("Outside the safe range"), outcome)
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `T23 keep clears the pending record`() {
        val store = InMemoryToolboxStateStore()
        store.setPendingRevert(PendingRevert("display_density", "wm density 260", "wm density reset"))
        DisplayDensityController(FakeShell(), store).keep()
        assertNull(store.pendingRevert())
    }

    @Test
    fun `T24 revert now sends the recorded revert and clears the record on success`() {
        val shell = FakeShell()
        val store = InMemoryToolboxStateStore()
        store.setPendingRevert(PendingRevert("display_density", "wm density 260", "wm density reset"))

        assertTrue(DisplayDensityController(shell, store).revertNow())
        assertEquals(listOf("wm density reset"), shell.lines)
        assertNull(store.pendingRevert())
    }

    @Test
    fun `T25 revert now with nothing pending sends nothing`() {
        val shell = FakeShell()
        assertTrue(DisplayDensityController(shell, InMemoryToolboxStateStore()).revertNow())
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `back to stock clears any pending record on success`() {
        val shell = FakeShell()
        val store = InMemoryToolboxStateStore()
        store.setPendingRevert(PendingRevert("display_density", "wm density 260", "wm density reset"))
        assertTrue(DisplayDensityController(shell, store).backToStock())
        assertNull(store.pendingRevert())
    }

    @Test
    fun `pending revert survives to IME start and is sent once`() {
        val shell = FakeShell()
        val store = InMemoryToolboxStateStore()
        store.setPendingRevert(PendingRevert("display_density", "wm density 260", "wm density reset"))
        DisplayDensityController(shell, store).checkPendingRevertAtStart()
        assertEquals(listOf("wm density reset"), shell.lines)
        assertNull(store.pendingRevert())
    }

    @Test
    fun `checkPendingRevertAtStart is a no-op without a pending record`() {
        val shell = FakeShell()
        DisplayDensityController(shell, InMemoryToolboxStateStore()).checkPendingRevertAtStart()
        assertTrue(shell.lines.isEmpty())
    }
}
