package brobata.physiboard.device.privileged.broker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executor

/**
 * Where the one-time pairing is, as the screen and the watcher notification both see it.
 * spec: broker-privileged-toolbox.md SS4.1 (the states the user sees, in order).
 */
sealed class PairingState {
    /** Nothing armed. */
    object Idle : PairingState()

    /** The watcher is discovering `_adb-tls-pairing._tcp`; "Searching for pairing service…". */
    object Searching : PairingState()

    /** The pairing port was resolved; "Pairing service found", the code can be typed. */
    data class ServiceFound(val port: Int) : PairingState()

    /** A pair is in flight; "Pairing…". */
    object Pairing : PairingState()

    /** "Paired successfully": the key is stored and the phone accepted it. */
    object Paired : PairingState()

    /** "Pairing failed" with one of the spec's messages, or the raw error text. */
    data class Failed(val message: String) : PairingState()
}

/**
 * The pairing flow: arm discovery of the pairing service, take the six-digit code from
 * whichever surface the user typed it into (the notification's inline reply or a screen), pair
 * under the broker lock, and apply the two rules that keep "paired" honest.
 *
 * The state is a [StateFlow] because the watcher notification and any open screen must show the
 * same step at the same time (SS4.1). The watcher is armed when the setup card appears, before
 * the user taps anything, and deliberately not stopped when the user leaves the screen: the
 * button's whole job is to send them to Android's Wireless debugging page, and stopping the
 * watcher then is how they arrive at "Pair device" with nothing listening (SS4.1 step 3, SS6 of
 * the ring document).
 *
 * spec: broker-privileged-toolbox.md SS4.1, SS4.3, SS4.4, SS5.2 (the end of any attempt
 * invalidates the verdict), SS7 (pairing success runs the setup pass).
 */
class PairingCoordinator(
    private val transport: AdbTransport,
    private val broker: PrivilegedBroker,
    private val worker: Executor,
    private val onPaired: () -> Unit,
) {
    private val stateFlow = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = stateFlow

    @Volatile
    private var discovery: PairingDiscovery? = null

    /** The most recently resolved pairing port, kept because the service may be killed before the user replies (SS4.1 step 4). */
    @Volatile
    var discoveredPort: Int? = null
        private set

    /** spec: SS4.1 step 3. Idempotent: a second arm while searching changes nothing. */
    @Synchronized
    fun arm() {
        if (discovery != null) return
        stateFlow.value = PairingState.Searching
        discovery = transport.startPairingDiscovery { port ->
            if (port > 0) {
                discoveredPort = port
                if (stateFlow.value is PairingState.Searching || stateFlow.value is PairingState.ServiceFound) {
                    stateFlow.value = PairingState.ServiceFound(port)
                }
            }
        }
    }

    /** spec: SS4.1 step 6 ("stops the watcher if it armed it") and the notification's "Stop" action. */
    @Synchronized
    fun disarm() {
        discovery?.stop()
        discovery = null
        if (stateFlow.value is PairingState.Searching || stateFlow.value is PairingState.ServiceFound) {
            stateFlow.value = PairingState.Idle
        }
    }

    val isArmed: Boolean get() = discovery != null

    /**
     * The user typed [code]. [port] is the one embedded in the notification action, or null to
     * use the last discovered one. Runs on the worker; the outcome lands in [state].
     */
    fun submitCode(code: String, port: Int? = null) {
        worker.execute { pairNow(code.trim(), port) }
    }

    /** [submitCode] on the calling thread, for the tests and the watcher's own worker. Never throws. */
    fun pairNow(code: String, port: Int? = null): PairingState {
        val target = port ?: discoveredPort ?: (stateFlow.value as? PairingState.ServiceFound)?.port
        if (target == null) {
            // SPEC GAP: the spec only describes a code typed into a notification whose action
            // carries the port; a screen that submits before discovery has landed is not
            // covered. Failing with a clear message is the smallest honest choice.
            return finish(PairingState.Failed(NO_SERVICE_YET))
        }
        if (code.length != PAIRING_CODE_LENGTH || code.any { !it.isDigit() }) {
            return finish(PairingState.Failed(PairResult.WRONG_CODE))
        }
        stateFlow.value = PairingState.Pairing
        // spec SS4.3: a key is minted before the code is checked, so whether one existed must be
        // read before the attempt; a failed attempt discards the key only if none existed before.
        val hadKeyBefore = transport.hasStoredKey()
        val result = try {
            broker.exclusive { transport.pair(target, code) }
        } catch (error: Exception) {
            PairResult.Failed(error.stackTraceToString())
        }
        return when (result) {
            PairResult.Accepted -> {
                disarm()
                broker.invalidate()
                val paired = finish(PairingState.Paired)
                runCatching(onPaired)
                paired
            }
            is PairResult.Failed -> {
                if (!hadKeyBefore) transport.forgetKey()
                broker.invalidate()
                finish(PairingState.Failed(result.message))
            }
        }
    }

    private fun finish(state: PairingState): PairingState {
        stateFlow.value = state
        return state
    }

    /** Back to [PairingState.Idle] after a terminal state, so the card can arm again on its next tick. spec: SS4.5. */
    fun reset() {
        if (stateFlow.value is PairingState.Paired || stateFlow.value is PairingState.Failed) stateFlow.value = PairingState.Idle
    }

    companion object {
        /** Android's Wireless debugging pairing code is always six digits. spec: SS2 row 2. */
        const val PAIRING_CODE_LENGTH = 6
        const val NO_SERVICE_YET = "No pairing service found yet. Tap \"Pair device with pairing code\" in Wireless debugging first."
    }
}
