package brobata.physiboard.device.privileged.broker

import brobata.physiboard.device.privileged.FakeAdbTransport
import brobata.physiboard.device.privileged.FakeClock
import brobata.physiboard.device.privileged.InMemoryDiagnosticsStore
import brobata.physiboard.device.privileged.NOT_PAIRED_MESSAGE
import brobata.physiboard.device.privileged.NO_SERVICE_MESSAGE
import brobata.physiboard.device.privileged.VerdictRecord
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: broker-privileged-toolbox.md SS5, SS6; test cases T34 to T41, T50. */
class PrivilegedBrokerTest {

    private val transport = FakeAdbTransport()
    private val diagnostics = InMemoryDiagnosticsStore()
    private val clock = FakeClock()
    private val broker = PrivilegedBroker(transport, diagnostics, clock)

    // Verdicts. spec: SS5.1. -----------------------------------------------------------------

    @Test
    fun `T34 - no key is NOT_PAIRED without discovery`() {
        transport.hasKey = false
        assertEquals(BrokerVerdict.NOT_PAIRED, broker.verifyBlocking())
        assertEquals(0, transport.discoveries.get())
    }

    @Test
    fun `T35 - key stored but adb_wifi_enabled 0 is WIRELESS_DEBUGGING_OFF without discovery`() {
        transport.wirelessDebuggingOn = false
        assertEquals(BrokerVerdict.WIRELESS_DEBUGGING_OFF, broker.verifyBlocking())
        assertEquals(0, transport.discoveries.get())
        assertTrue(BrokerVerdict.WIRELESS_DEBUGGING_OFF.isPairedButUnreachable, "the pairing is intact; the user is sent to the switch")
        assertFalse(BrokerVerdict.NOT_PAIRED.isPairedButUnreachable)
    }

    @Test
    fun `T36 - key, debugging on, discovery null is NO_SERVICE`() {
        transport.port = null
        assertEquals(BrokerVerdict.NO_SERVICE, broker.verifyBlocking())
        assertEquals(1, transport.discoveries.get())
    }

    @Test
    fun `T37 - key, debugging on, port found, connect refused is REJECTED`() {
        transport.failing += BrokerRules.VERIFY_LINE
        assertEquals(BrokerVerdict.REJECTED, broker.verifyBlocking())
        assertEquals("IllegalStateException: not A_CNXN", broker.lastError)
    }

    @Test
    fun `T38 - key, debugging on, port found, echo succeeds is OK`() {
        transport.responses[BrokerRules.VERIFY_LINE] = "physiboard_verify\n"
        assertEquals(BrokerVerdict.OK, broker.verifyBlocking())
        assertEquals(listOf(BrokerRules.VERIFY_LINE), transport.shellLines)
        assertEquals(BrokerVerdict.OK, broker.verdict.value)
        assertEquals(VerdictRecord(BrokerVerdict.OK, clock.nowMs), diagnostics.brokerVerdict())
    }

    @Test
    fun `a stored key that cannot be read makes the app read as unpaired`() {
        transport.keyUnreadable = true
        assertEquals(BrokerVerdict.NOT_PAIRED, broker.verifyBlocking())
        assertFalse(transport.hasKey, "the unreadable entry is removed (SS4.4)")
        assertFalse(broker.isPaired())
    }

    // Freshness. spec: SS5.2. -----------------------------------------------------------------

    @Test
    fun `T39 - a verdict cached 5 s ago is returned without a new check`() {
        broker.verifyBlocking()
        clock.nowMs += 5_000
        broker.verifyBlocking()
        assertEquals(1, broker.checkCount)
    }

    @Test
    fun `T40 - a verdict cached 11 s ago triggers a new check`() {
        broker.verifyBlocking()
        clock.nowMs += 11_000
        broker.verifyBlocking()
        assertEquals(2, broker.checkCount)
    }

    @Test
    fun `T41 - invalidate then refresh checks again regardless of age`() {
        broker.verifyBlocking()
        broker.invalidate()
        broker.verifyBlocking()
        assertEquals(2, broker.checkCount)
    }

    @Test
    fun `a forced refresh checks again inside the fresh window`() {
        broker.verifyBlocking()
        broker.verifyBlocking(force = true)
        assertEquals(2, broker.checkCount)
    }

    @Test
    fun `a persisted verdict seeds the display but is never trusted as fresh`() {
        diagnostics.recordBrokerVerdict(VerdictRecord(BrokerVerdict.OK, clock.nowMs))
        val seeded = PrivilegedBroker(transport, diagnostics, clock)
        assertEquals(BrokerVerdict.OK, seeded.verdict.value)
        transport.port = null
        assertEquals(BrokerVerdict.NO_SERVICE, seeded.verifyBlocking())
    }

    @Test
    fun `a request arriving while a check is in flight waits for that check instead of starting its own`() {
        transport.discoveryDelayMs = 150
        val pool = Executors.newFixedThreadPool(2)
        val first = pool.submit<BrokerVerdict> { broker.verifyBlocking() }
        Thread.sleep(40)
        val second = pool.submit<BrokerVerdict> { broker.verifyBlocking() }
        assertEquals(BrokerVerdict.OK, first.get(5, TimeUnit.SECONDS))
        assertEquals(BrokerVerdict.OK, second.get(5, TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals(1, broker.checkCount)
        assertEquals(0, transport.overlappingDiscoveries.get())
    }

    // Running a line. spec: SS6. --------------------------------------------------------------

    @Test
    fun `run with no key fails with the not-paired message and never discovers`() {
        transport.hasKey = false
        val result = broker.run("echo hi")
        assertEquals(ShellResult.Failed(NOT_PAIRED_MESSAGE), result)
        assertEquals(NOT_PAIRED_MESSAGE, broker.lastError)
        assertEquals(0, transport.discoveries.get())
    }

    @Test
    fun `run with no service fails with the spec's message after discovery`() {
        transport.port = null
        assertEquals(ShellResult.Failed(NO_SERVICE_MESSAGE), broker.run("echo hi"))
        assertEquals(1, transport.discoveries.get())
    }

    @Test
    fun `run rediscovers the port on every call`() {
        broker.run("echo one")
        broker.run("echo two")
        assertEquals(2, transport.discoveries.get(), "nothing about the port is cached (SS6)")
    }

    @Test
    fun `run remembers the output as the last result and clears the last error`() {
        transport.port = null
        broker.run("echo one")
        assertNotNull(broker.lastError)
        transport.port = 40_002
        transport.responses["echo one"] = "one\n"
        assertEquals(ShellResult.Ok("one\n"), broker.run("echo one"))
        assertEquals("one\n", broker.lastResult)
        assertNull(broker.lastError)
    }

    @Test
    fun `run never throws and records the exception as simple name and message`() {
        transport.failing += "echo boom"
        val result = assertIs<ShellResult.Failed>(broker.run("echo boom"))
        assertEquals("IllegalStateException: not A_CNXN", result.message)
        assertEquals("IllegalStateException: not A_CNXN", broker.lastError)
    }

    @Test
    fun `two privileged steps started together are serialised, both succeed, no discovery overlaps`() {
        transport.discoveryDelayMs = 60
        val pool = Executors.newFixedThreadPool(4)
        val results = (1..4).map { n -> pool.submit<ShellResult> { broker.run("echo $n") } }.map { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertTrue(results.all { it.isOk })
        assertEquals(4, transport.discoveries.get())
        assertEquals(0, transport.overlappingDiscoveries.get(), "overlapping discoveries would both fail silently (D3)")
    }

    @Test
    fun `forgetting the pairing removes the key and clears everything the broker remembered`() {
        broker.run("echo hi")
        broker.verifyBlocking()
        broker.forgetPairing()
        assertFalse(transport.hasKey)
        assertNull(broker.lastResult)
        assertNull(broker.lastError)
        broker.verifyBlocking()
        assertEquals(2, broker.checkCount, "the cached verdict was invalidated")
    }

    // The blocker. spec: SS7. ----------------------------------------------------------------

    @Test
    fun `T50 - blocker is null with key and debugging on, wireless_debugging_off with the switch off, not_paired with no key`() {
        assertNull(BrokerRules.blocker(hasStoredKey = true, wirelessDebuggingOn = true))
        assertEquals(BrokerBlocker.WIRELESS_DEBUGGING_OFF, BrokerRules.blocker(hasStoredKey = true, wirelessDebuggingOn = false))
        assertEquals("wireless_debugging_off", BrokerRules.blocker(hasStoredKey = true, wirelessDebuggingOn = false)?.reason)
        assertEquals(BrokerBlocker.NOT_PAIRED, BrokerRules.blocker(hasStoredKey = false, wirelessDebuggingOn = true))
        assertEquals("not_paired", broker.let { transport.hasKey = false; it.blocker()?.reason })
    }
}
