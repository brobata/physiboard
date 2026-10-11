package brobata.physiboard.device.privileged.ring

import brobata.physiboard.device.privileged.DeviceStateStore
import brobata.physiboard.device.titan.Nightstand
import brobata.physiboard.device.titan.NightstandAttempt
import brobata.physiboard.device.titan.NightstandFacts
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/** The phone facts the charging clock's start reads. spec: device-backlight-ring.md SS5.11.2. */
interface NightstandProbe {
    /** Any charger, including a full battery still on the cable (not "charging" in Android's battery-status sense). */
    fun isPluggedIn(): Boolean

    fun isKeyguardLocked(): Boolean

    fun isInteractive(): Boolean
}

/** The notifications still in the shade, read once at the charging clock's start. */
fun interface ActiveNotificationsSource {
    fun active(): List<ActiveRingNotification>
}

/**
 * "Stay on while charging": turns the screen-off, unlock and charger broadcasts into charging-clock
 * starts. The decisions are [Nightstand]'s and the put-away memory is the ring session's
 * ([RingCoordinator]); this class only runs them in order, on the ring's worker, with one retry
 * alarm at a time.
 *
 * All the public calls arrive on the main thread from the listener's receiver.
 *
 * spec: device-backlight-ring.md SS5.11.2.
 */
class NightstandCoordinator(
    private val store: DeviceStateStore,
    private val ring: RingCoordinator,
    private val probe: NightstandProbe,
    private val active: ActiveNotificationsSource,
    private val pocket: PocketProbe,
    private val retries: DelayedRunner,
    private val worker: Executor,
    private val ownPackageName: String,
) {
    @Volatile
    private var cancelRetry: (() -> Unit)? = null

    /** Bumped by every event; a check from an older chain finds it changed and stops, so only one chain is ever live. */
    private val chain = AtomicInteger()

    /**
     * spec: SS5.11.2 "Starting": screen off with no ring screen up is the moment to start. With a
     * ring screen holding the screen on it was the power key, and the clock is put away. With a
     * ring screen up that had already let go (its timer ran out, the phone's timeout ended it),
     * the check waits for that screen to be gone: the first check of the chain, 6 s on.
     */
    fun onScreenOff() {
        val held = ring.isScreenHeld()
        val ringUp = ring.isRingOnScreenOrPending()
        ring.onScreenOff()
        val current = cancelRetries()
        when {
            held -> Unit
            ringUp -> scheduleCheck(0, current)
            else -> worker.execute { attempt(0, current) }
        }
    }

    fun onScreenOn() {
        cancelRetries()
        ring.onScreenOn()
    }

    fun onUnlocked() {
        cancelRetries()
        ring.onUnlocked()
    }

    /** Plugging in is a fresh start: put-away is forgotten and, if the screen is already dark and locked, the clock comes up. */
    fun onPowerConnected() {
        ring.onPowerConnected()
        val current = cancelRetries()
        worker.execute { attempt(0, current) }
    }

    fun onPowerDisconnected() {
        cancelRetries()
    }

    /** Ends the live chain and returns the new chain's number. */
    private fun cancelRetries(): Int {
        val next = chain.incrementAndGet()
        cancelRetry?.invoke()
        cancelRetry = null
        return next
    }

    /** Schedules check [checkIndex] + 1 after the gap [Nightstand.retryDelayMs] gives for [checkIndex]; nothing once the chain is over. */
    private fun scheduleCheck(checkIndex: Int, chainId: Int) {
        val delay = Nightstand.retryDelayMs(checkIndex) ?: return
        if (chainId != chain.get()) return
        cancelRetry = retries.schedule(delay) { worker.execute { attempt(checkIndex + 1, chainId) } }
    }

    /** Check number [checkIndex] (0 at the event itself) of chain [chainId]. Runs on the worker. */
    private fun attempt(checkIndex: Int, chainId: Int) {
        if (chainId != chain.get()) return
        val prefs = store.snapshot().device
        val featureOn = Nightstand.isEnabled(prefs.ringEnabled, prefs.ringClock, prefs.ringStayOnCharging)
        // Cheapest first: with the switch off nothing else is read.
        if (!featureOn) return
        val facts = NightstandFacts(
            featureOn = true,
            pluggedIn = probe.isPluggedIn(),
            screenInteractive = probe.isInteractive(),
            keyguardLocked = probe.isKeyguardLocked(),
            putAway = ring.isPutAway(),
            ringOnScreen = ring.isRingOnScreenOrPending(),
        )
        when (Nightstand.attempt(facts)) {
            NightstandAttempt.START -> {
                // Face down on the nightstand: stay dark, as the ring does in a pocket (SS5.5).
                if (pocket.isCovered()) return
                ring.startNightstand(active.active(), ownPackageName)
            }
            NightstandAttempt.RETRY_LATER -> scheduleCheck(checkIndex, chainId)
            NightstandAttempt.NONE -> Unit
        }
    }
}
