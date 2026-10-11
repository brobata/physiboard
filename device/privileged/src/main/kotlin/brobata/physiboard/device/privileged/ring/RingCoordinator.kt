package brobata.physiboard.device.privileged.ring

import brobata.physiboard.core.settings.DevicePrefs
import brobata.physiboard.device.privileged.DeviceStateStore
import brobata.physiboard.device.titan.NightstandMemory
import brobata.physiboard.device.titan.NotificationRingCandidate
import brobata.physiboard.device.titan.NotificationRingColor
import brobata.physiboard.device.titan.NotificationRingLifecycle
import brobata.physiboard.device.titan.NotificationRingPolicy
import brobata.physiboard.device.titan.RingArrivalDecision
import brobata.physiboard.device.titan.RingSessionState
import brobata.physiboard.device.titan.RingSource
import java.util.concurrent.Executor

/** The ring on screen, as the coordinator talks to it. Both calls arrive on the surface executor (the main thread on Android). */
interface RingSurface {
    /** spec: device-backlight-ring.md SS5.2 point 4: recolour, refresh icons, restart the timer. */
    fun onSourcesChanged(sources: List<RingSource>)

    /** spec: SS5.2 ("when none remain, the ring finishes"). */
    fun finishRing()
}

/** Puts a ring on a dark, locked screen. spec: SS5.6. */
interface RingLauncher {
    fun launch(sources: List<RingSource>)

    /** The charging clock, by the same route as the ring. spec: SS5.11.2. */
    fun launchNightstand()

    /** spec: SS5.7.6 ("Try it"). */
    fun launchDemo()
}

/** "The display is interactive (screen on)". spec: SS5.2 point 5. */
fun interface ScreenProbe {
    fun isInteractive(): Boolean
}

/** The one proximity reading of the pocket check. spec: SS5.5. */
fun interface PocketProbe {
    fun isCovered(): Boolean
}

/** One notification still in the shade, as the charging clock's start reads it. spec: SS5.11.2. */
data class ActiveRingNotification(
    val candidate: NotificationRingCandidate,
    val declaredColorArgb: Int,
    val notificationKey: String,
    val postedAtMs: Long,
)

/**
 * The notification ring's session: which notifications are waiting, whether a ring is on
 * screen, and what each posted or removed notification does to it. The decisions are
 * [NotificationRingLifecycle]'s and [NotificationRingPolicy]'s; this class runs them in order
 * against the real listener, launcher, sensor and backlight.
 *
 * All notification handling runs on one worker in arrival order, so two notifications landing
 * together cannot both see "no ring" and each start one. Surface callbacks go through
 * [surfaceExecutor] (the main thread on Android; direct in tests).
 *
 * spec: device-backlight-ring.md SS5.2 (the ordered steps), SS5.4 (colour), SS5.7 (ownership),
 * SS5.8 (the keyboard); T42 to T44.
 */
class RingCoordinator(
    private val store: DeviceStateStore,
    private val backlight: RingBacklight,
    private val launcher: RingLauncher,
    private val screen: ScreenProbe,
    private val pocket: PocketProbe,
    private val worker: Executor,
    private val surfaceExecutor: Executor,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Time since boot for the launch-pending window (SystemClock.elapsedRealtime on the phone); a wall-clock change must not stretch or end it. */
    private val elapsedClock: () -> Long = clock,
) {
    @Volatile
    var state: RingSessionState = RingSessionState()
        private set

    @Volatile
    private var surface: RingSurface? = null

    /** The ring screen currently holds keep-screen-on (its own report). spec: SS5.11.2 "The power key". */
    @Volatile
    private var surfaceHolding = false

    /** spec: SS5.11.2: put-away and the launch on its way. Guarded by [memoryLock]: events arrive on the main thread, launches on the worker. */
    private var memory = NightstandMemory()
    private val memoryLock = Any()

    /** A ring screen is up, or a launch was sent less than 15 s ago and its screen has not appeared yet. */
    fun isRingOnScreenOrPending(): Boolean = surface != null || synchronized(memoryLock) { memory.launchPending(elapsedClock()) }

    /** spec: SS5.11.2: the user pressed power on a ring screen. */
    fun isPutAway(): Boolean = synchronized(memoryLock) { memory.putAway }

    /** The ring screen tells the session whether it holds keep-screen-on right now. */
    fun onSurfaceHolding(surface: RingSurface, holding: Boolean) {
        if (this.surface === surface) surfaceHolding = holding
    }

    /** A ring screen is up and holding the screen on: a screen-off now can only be the power key. */
    fun isScreenHeld(): Boolean = surface != null && surfaceHolding

    /**
     * spec: SS5.11.2 "The power key": the held ring screen itself saw the screen go off. Reported
     * by the screen before it finishes, so put-away never depends on which of the two screen-off
     * receivers runs first.
     */
    fun onHeldScreenOff(surface: RingSurface) {
        if (this.surface !== surface) return
        synchronized(memoryLock) { memory = memory.onScreenOff(screenHeld = true) }
    }

    /** spec: SS5.11.2. Called on the main thread by the listener's screen receiver. */
    fun onScreenOff() {
        val held = isScreenHeld()
        synchronized(memoryLock) { memory = memory.onScreenOff(held) }
    }

    fun onScreenOn() {
        val onScreen = surface != null
        synchronized(memoryLock) { memory = memory.onScreenOn(onScreen, elapsedClock()) }
    }

    fun onUnlocked() = synchronized(memoryLock) { memory = memory.onUnlocked() }

    fun onPowerConnected() = synchronized(memoryLock) { memory = memory.onPowerConnected() }

    /**
     * spec: SS5.11.2: puts the charging clock up, seeded with the qualifying notifications still
     * in the shade so its glow and icons say what is waiting. The caller ([NightstandCoordinator])
     * has already decided it should start; this re-checks only that nothing beat it to the screen.
     */
    fun startNightstand(active: List<ActiveRingNotification>, ownPackageName: String) {
        worker.execute {
            if (isRingOnScreenOrPending()) return@execute
            val prefs = store.snapshot().device
            val seed = active
                .filter { it.candidate.packageName != ownPackageName && NotificationRingPolicy.evaluate(it.candidate) == null }
                .sortedBy { it.postedAtMs }
                .map { RingSource(it.notificationKey, it.candidate.packageName, resolveColor(it.candidate.packageName, it.declaredColorArgb, prefs), it.postedAtMs) }
            state = RingSessionState(seed)
            synchronized(memoryLock) { memory = memory.onLaunched(elapsedClock()) }
            backlight.suppress()
            launcher.launchNightstand()
        }
    }

    /** spec: SS5.2, "For every posted notification, in order". [ownPackageName] is the running app's own, never a literal (the sideload build has another id). */
    fun onNotificationPosted(candidate: NotificationRingCandidate, declaredColorArgb: Int, notificationKey: String, ownPackageName: String) {
        worker.execute { handlePosted(candidate, declaredColorArgb, notificationKey, ownPackageName) }
    }

    /** spec: SS5.2, "For every removed notification". */
    fun onNotificationRemoved(notificationKey: String) {
        worker.execute { handleRemoved(notificationKey) }
    }

    /**
     * The ring activity was created. Takes ownership of the keyboard restore (SS5.7 step 1) and
     * returns the sources it should show; an empty list means "finish at once" (SS5.7 step 4).
     */
    fun onRingCreated(surface: RingSurface): List<RingSource> {
        this.surface = surface
        surfaceHolding = false
        synchronized(memoryLock) { memory = memory.onRingCreated() }
        backlight.takeOwnership()
        return state.sources
    }

    /** The ring activity's one teardown: restores the keyboard and clears "current ring". spec: SS5.7 "Ending". */
    fun onRingDestroyed(surface: RingSurface) {
        if (this.surface !== surface) return
        this.surface = null
        surfaceHolding = false
        state = RingSessionState()
        backlight.restore()
    }

    /** spec: SS5.7.6: no policy, no pocket check, no keyboard. */
    fun startDemo() = launcher.launchDemo()

    private fun handlePosted(candidate: NotificationRingCandidate, declaredColorArgb: Int, notificationKey: String, ownPackageName: String) {
        val settings = store.snapshot()
        if (!settings.device.ringEnabled) return
        // The policy's own-app rule names the release package; the running one may be the sideload build.
        if (candidate.packageName == ownPackageName) return
        if (NotificationRingPolicy.evaluate(candidate) != null) return
        val source = RingSource(notificationKey, candidate.packageName, resolveColor(candidate.packageName, declaredColorArgb, settings.device), clock())
        val ringShowing = isRingOnScreenOrPending()
        // Sources held with no screen up and no launch on its way are left over from a launch the
        // system declined (SS9); without this every later notification would "join" a ring that
        // never appears.
        if (!ringShowing && state.sources.isNotEmpty()) state = RingSessionState()
        val interactive = !ringShowing && screen.isInteractive()
        val covered = !ringShowing && !interactive && pocket.isCovered()
        when (val decision = NotificationRingLifecycle.onNotificationPosted(state, source, interactive, covered, ringOnScreen = ringShowing)) {
            is RingArrivalDecision.UpdateExisting -> {
                state = RingSessionState(decision.sources)
                val shown = decision.sources
                surfaceExecutor.execute { surface?.onSourcesChanged(shown) }
            }
            is RingArrivalDecision.Start -> {
                state = RingSessionState(decision.sources)
                synchronized(memoryLock) { memory = memory.onLaunched(elapsedClock()) }
                backlight.suppress()
                launcher.launch(decision.sources)
            }
            is RingArrivalDecision.Skip -> Unit
        }
    }

    private fun handleRemoved(notificationKey: String) {
        if (state.sources.isEmpty()) return
        val next = NotificationRingLifecycle.onNotificationRemoved(state, notificationKey)
        if (next == state) return
        state = next
        val shown = next.sources
        surfaceExecutor.execute {
            val current = surface ?: return@execute
            if (NotificationRingLifecycle.isFinished(next)) current.finishRing() else current.onSourcesChanged(shown)
        }
    }

    companion object {
        /**
         * spec: SS5.4 points 1 to 4 with the user's own default colour in place of the built-in
         * one. [NotificationRingColor.resolve] carries the rule with a fixed default; this keeps
         * the same order and substitutes `notification_ring_default_color` when it is set.
         */
        fun resolveColor(packageName: String, declaredColorArgb: Int, prefs: DevicePrefs): Int {
            prefs.ringAppColors[packageName]?.let { return it }
            val default = prefs.ringDefaultColor ?: NotificationRingColor.DEFAULT_COLOR_ARGB
            if (declaredColorArgb == 0) return default
            if (NotificationRingColor.relativeLuminance(declaredColorArgb) < NotificationRingColor.DARK_LUMINANCE_THRESHOLD) return default
            return declaredColorArgb
        }
    }
}
