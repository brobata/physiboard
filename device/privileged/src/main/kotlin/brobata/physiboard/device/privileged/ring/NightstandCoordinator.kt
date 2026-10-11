package brobata.physiboard.device.privileged.ring

import brobata.physiboard.device.privileged.DeviceStateStore
import brobata.physiboard.device.titan.Nightstand
import brobata.physiboard.device.titan.NightstandAttempt
import brobata.physiboard.device.titan.NightstandFacts
import java.util.concurrent.Executor

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

    /** spec: SS5.11.2 "Starting": screen off with no ring screen up is the moment to start; with one up it was the power key, and the clock is put away. */
    fun onScreenOff() {
        val wasShowing = ring.isRingOnScreenOrPending()
        ring.onScreenOff()
        cancelRetries()
        if (!wasShowing) worker.execute { attempt(0) }
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
        cancelRetries()
        worker.execute { attempt(0) }
    }

    fun onPowerDisconnected() = cancelRetries()

    private fun cancelRetries() {
        cancelRetry?.invoke()
        cancelRetry = null
    }

    /** Check number [checkIndex] (0 at the event itself). Runs on the worker. */
    private fun attempt(checkIndex: Int) {
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
            NightstandAttempt.RETRY_LATER -> {
                val delay = Nightstand.retryDelayMs(checkIndex) ?: return
                cancelRetry = retries.schedule(delay) { worker.execute { attempt(checkIndex + 1) } }
            }
            NightstandAttempt.NONE -> Unit
        }
    }
}
