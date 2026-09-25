package brobata.physiboard.device.privileged.toolbox

import brobata.physiboard.core.toolbox.BloatCatalog
import brobata.physiboard.core.toolbox.BloatState
import brobata.physiboard.core.toolbox.JournalAction
import brobata.physiboard.core.toolbox.JournalPriorState
import brobata.physiboard.device.privileged.FakeShell
import brobata.physiboard.device.privileged.broker.ShellResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BloatRemoverTest {
    private val pkg = BloatCatalog.entries.first().packageName

    @Test
    fun `T10 disabling an active package journals its prior state before the command runs`() {
        val shell = FakeShell()
        val store = InMemoryToolboxStateStore()
        val remover = BloatRemover(shell, store, DeviceProfile { true })

        val outcome = remover.disable(pkg, BloatState.ACTIVE)

        assertEquals(BloatMutationOutcome.Success, outcome)
        assertEquals(listOf("pm disable-user --user 0 $pkg"), shell.lines)
        val record = store.journal().single()
        assertEquals(pkg, record.packageName)
        assertEquals(JournalPriorState.ACTIVE, record.prev)
        assertEquals(JournalAction.DISABLED, record.action)
    }

    @Test
    fun `T11 a failed disable leaves no journal record and reports the broker error`() {
        val line = "pm disable-user --user 0 $pkg"
        val shell = FakeShell().apply { responses[line] = ShellResult.Failed("IllegalStateException: not A_CNXN") }
        val store = InMemoryToolboxStateStore()

        val outcome = BloatRemover(shell, store, DeviceProfile { true }).disable(pkg, BloatState.ACTIVE)

        assertEquals(BloatMutationOutcome.Failed("IllegalStateException: not A_CNXN"), outcome)
        assertTrue(store.journal().isEmpty())
    }

    @Test
    fun `T12 disabling the same package twice keeps one record with the later timestamp`() {
        val shell = FakeShell()
        val store = InMemoryToolboxStateStore()
        var now = 1L
        val remover = BloatRemover(shell, store, DeviceProfile { true }, clock = { now })

        remover.disable(pkg, BloatState.ACTIVE)
        now = 2L
        remover.disable(pkg, BloatState.ACTIVE)

        val record = store.journal().single()
        assertEquals(2L, record.atMs)
    }

    @Test
    fun `T15 mutating on a non-Titan profile is refused before any journal write`() {
        val shell = FakeShell()
        val store = InMemoryToolboxStateStore()
        val outcome = BloatRemover(shell, store, DeviceProfile { false }).disable(pkg, BloatState.ACTIVE)

        assertEquals(BloatMutationOutcome.Refused("This catalog is for the Titan 2 Elite only"), outcome)
        assertTrue(store.journal().isEmpty())
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `T16 a name with shell metacharacters is refused as not a package name`() {
        val shell = FakeShell()
        val outcome = BloatRemover(shell, InMemoryToolboxStateStore(), DeviceProfile { true }).disable("com.agui.game; rm -rf /", BloatState.ACTIVE)
        assertEquals(BloatMutationOutcome.Refused("Not a package name"), outcome)
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `census parses the phone's markers into per-package state`() {
        val shell = FakeShell().apply { responses[brobata.physiboard.core.toolbox.BloatCensus.COMMAND] = ShellResult.Ok("__E__\npackage:$pkg\n__D__\n__U__\npackage:$pkg\n") }
        val outcome = BloatRemover(shell, InMemoryToolboxStateStore(), DeviceProfile { true }).census() as BloatCensusOutcome.Loaded
        assertEquals(BloatState.ACTIVE, outcome.result.states[pkg])
    }

    @Test
    fun `census reports NotPaired without sending a line`() {
        val shell = FakeShell.notPaired()
        val outcome = BloatRemover(shell, InMemoryToolboxStateStore(), DeviceProfile { true }).census()
        assertEquals(BloatCensusOutcome.NotPaired, outcome)
        assertTrue(shell.lines.isEmpty())
    }

    @Test
    fun `restore sends install-existing then enable and forgets the journal record on success`() {
        val shell = FakeShell()
        val store = InMemoryToolboxStateStore()
        store.updateJournal { listOf(brobata.physiboard.core.toolbox.JournalRecord(pkg, JournalPriorState.DISABLED, JournalAction.DISABLED, 1L)) }

        val outcome = BloatRemover(shell, store, DeviceProfile { true }).restore(pkg)

        assertEquals(BloatMutationOutcome.Success, outcome)
        assertEquals(listOf("cmd package install-existing --user 0 $pkg; pm enable --user 0 $pkg"), shell.lines)
        assertTrue(store.journal().isEmpty())
    }

    @Test
    fun `restore all walks every journal record`() {
        val other = BloatCatalog.entries[1].packageName
        val shell = FakeShell()
        val store = InMemoryToolboxStateStore()
        store.updateJournal {
            listOf(
                brobata.physiboard.core.toolbox.JournalRecord(pkg, JournalPriorState.DISABLED, JournalAction.DISABLED, 1L),
                brobata.physiboard.core.toolbox.JournalRecord(other, JournalPriorState.ACTIVE, JournalAction.UNINSTALLED, 2L),
            )
        }
        val outcomes = BloatRemover(shell, store, DeviceProfile { true }).restoreAll()
        assertEquals(2, outcomes.size)
        assertTrue(outcomes.values.all { it == BloatMutationOutcome.Success })
        assertTrue(store.journal().isEmpty())
    }
}
