package brobata.physiboard.device.privileged.broker

import brobata.physiboard.device.privileged.DirectExecutor
import brobata.physiboard.device.privileged.FakeAdbTransport
import brobata.physiboard.device.privileged.FakeClock
import brobata.physiboard.device.privileged.InMemoryDiagnosticsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** spec: broker-privileged-toolbox.md SS4.1, SS4.3; test cases T42, T43. */
class PairingCoordinatorTest {

    private val transport = FakeAdbTransport(hasKey = false)
    private val clock = FakeClock()
    private val broker = PrivilegedBroker(transport, InMemoryDiagnosticsStore(), clock)
    private var setupRuns = 0
    private val pairing = PairingCoordinator(transport, broker, DirectExecutor) { setupRuns++ }

    @Test
    fun `arming starts discovery and a resolved port moves the state to ServiceFound`() {
        pairing.arm()
        assertEquals(PairingState.Searching, pairing.state.value)
        transport.pairingListener!!(37_123)
        assertEquals(PairingState.ServiceFound(37_123), pairing.state.value)
        assertEquals(37_123, pairing.discoveredPort)
    }

    @Test
    fun `arming twice does not start a second discovery`() {
        pairing.arm()
        val first = transport.pairingListener
        pairing.arm()
        assertTrue(first === transport.pairingListener)
    }

    @Test
    fun `a successful pairing stops discovery, invalidates the verdict, and runs the setup pass`() {
        broker.verifyBlocking()
        pairing.arm()
        transport.pairingListener!!(37_123)
        val state = pairing.pairNow("123456")
        assertEquals(PairingState.Paired, state)
        assertEquals(listOf(37_123 to "123456"), transport.pairCalls)
        assertEquals(1, transport.pairingDiscoveryStops)
        assertFalse(pairing.isArmed)
        assertEquals(1, setupRuns, "pairing_succeeded runs the pass immediately (SS4.1 step 5)")
        transport.hasKey = true
        broker.verifyBlocking()
        assertEquals(2, broker.checkCount, "the cached verdict was invalidated")
    }

    @Test
    fun `T42 - a pairing attempt with no prior key that fails removes the key entry`() {
        transport.pairResult = PairResult.Failed(PairResult.WRONG_CODE)
        val state = pairing.pairNow("123456", port = 37_123)
        assertEquals(PairingState.Failed(PairResult.WRONG_CODE), state)
        assertFalse(transport.hasKey, "the key minted for the attempt is discarded (SS4.3)")
        assertEquals(0, setupRuns)
    }

    @Test
    fun `T43 - a pairing attempt with a prior key that fails keeps the key`() {
        transport.hasKey = true
        transport.pairResult = PairResult.Failed(PairResult.PORT_REFUSED)
        val state = pairing.pairNow("123456", port = 37_123)
        assertEquals(PairingState.Failed(PairResult.PORT_REFUSED), state)
        assertTrue(transport.hasKey, "it may back a pairing that still works (SS4.3)")
    }

    @Test
    fun `the port embedded in the notification action wins over the discovered one`() {
        pairing.arm()
        transport.pairingListener!!(37_123)
        pairing.pairNow("654321", port = 38_000)
        assertEquals(listOf(38_000 to "654321"), transport.pairCalls)
    }

    @Test
    fun `a code submitted before any service was found fails without a pair attempt`() {
        val state = assertIs<PairingState.Failed>(pairing.pairNow("123456"))
        assertEquals(PairingCoordinator.NO_SERVICE_YET, state.message)
        assertTrue(transport.pairCalls.isEmpty())
    }

    @Test
    fun `a code that is not six digits is refused as a wrong code without a pair attempt`() {
        assertEquals(PairingState.Failed(PairResult.WRONG_CODE), pairing.pairNow("12ab", port = 37_123))
        assertEquals(PairingState.Failed(PairResult.WRONG_CODE), pairing.pairNow("1234567", port = 37_123))
        assertTrue(transport.pairCalls.isEmpty())
    }

    @Test
    fun `a transport that throws during pairing is reported with its trace, never propagated`() {
        val throwing = object : AdbTransport by transport {
            override fun pair(port: Int, code: String): PairResult = throw IllegalStateException("Unable to create PairingContext.")
        }
        val coordinator = PairingCoordinator(throwing, broker, DirectExecutor) { setupRuns++ }
        val state = assertIs<PairingState.Failed>(coordinator.pairNow("123456", port = 1))
        assertTrue(state.message.contains("Unable to create PairingContext."))
    }

    @Test
    fun `disarm stops discovery and returns to Idle, reset clears a terminal state`() {
        pairing.arm()
        pairing.disarm()
        assertEquals(PairingState.Idle, pairing.state.value)
        assertEquals(1, transport.pairingDiscoveryStops)
        pairing.pairNow("123456", port = 1)
        assertEquals(PairingState.Paired, pairing.state.value)
        pairing.reset()
        assertEquals(PairingState.Idle, pairing.state.value)
    }
}
