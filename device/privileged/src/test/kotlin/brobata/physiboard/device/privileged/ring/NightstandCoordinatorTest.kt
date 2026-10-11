package brobata.physiboard.device.privileged.ring

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.device.privileged.DirectExecutor
import brobata.physiboard.device.privileged.FakeClock
import brobata.physiboard.device.privileged.FakeTimer
import brobata.physiboard.device.privileged.InMemoryDeviceStateStore
import brobata.physiboard.device.privileged.backlight.FakeMasterSwitch
import brobata.physiboard.device.privileged.setup.FakePermissionProbe
import brobata.physiboard.device.titan.Nightstand
import brobata.physiboard.device.titan.NotificationRingCandidate
import brobata.physiboard.device.titan.RingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: device-backlight-ring.md SS5.11.2 against the real ring session; test cases T59 to T66. */
class NightstandCoordinatorTest {

    private class RecordingLauncher : RingLauncher {
        val launches = mutableListOf<List<RingSource>>()
        var nightstands = 0
        override fun launch(sources: List<RingSource>) { launches += sources }
        override fun launchDemo() = Unit
        override fun launchNightstand() { nightstands++ }
    }

    private class Surface : RingSurface {
        val changes = mutableListOf<List<RingSource>>()
        var finished = 0
        override fun onSourcesChanged(sources: List<RingSource>) { changes += sources }
        override fun finishRing() { finished++ }
    }

    /** Records every scheduled retry; [fire] runs the latest. */
    private class Retries : DelayedRunner {
        val delays = mutableListOf<Long>()
        private var pending: (() -> Unit)? = null
        var cancels = 0
        override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
            delays += delayMs
            pending = action
            return { cancels++; if (pending === action) pending = null }
        }
        fun fire() { val action = pending; pending = null; action?.invoke() }
        val isPending get() = pending != null
    }

    private val store = InMemoryDeviceStateStore(
        Settings().let { it.copy(device = it.device.copy(ringEnabled = true, ringClock = true, ringStayOnCharging = true)) },
    )
    private val switch = FakeMasterSwitch(value = 1)
    private val backlight = RingBacklight(store, switch, FakePermissionProbe(writeSecureSettings = true), FakeTimer(), FakeClock())
    private val launcher = RecordingLauncher()
    private val clock = FakeClock(1_000L)
    private var plugged = true
    private var locked = true
    private var interactive = false
    private var covered = false
    private var shade: List<ActiveRingNotification> = emptyList()
    private val retries = Retries()
    private val ring = RingCoordinator(
        store = store,
        backlight = backlight,
        launcher = launcher,
        screen = { interactive },
        pocket = { covered },
        worker = DirectExecutor,
        surfaceExecutor = DirectExecutor,
        clock = clock,
    )
    private val nightstand = NightstandCoordinator(
        store = store,
        ring = ring,
        probe = object : NightstandProbe {
            override fun isPluggedIn() = plugged
            override fun isKeyguardLocked() = locked
            override fun isInteractive() = interactive
        },
        active = { shade },
        pocket = { covered },
        retries = retries,
        worker = DirectExecutor,
        ownPackageName = "brobata.physiboard.dev3",
    )

    private fun waiting(pkg: String, key: String, at: Long, ongoing: Boolean = false) = ActiveRingNotification(
        NotificationRingCandidate(pkg, isOngoingOrForegroundService = ongoing, isGroupSummary = false, isClearable = true, priority = 0),
        declaredColorArgb = 0xFF2F80ED.toInt(),
        notificationKey = key,
        postedAtMs = at,
    )

    /** The screen goes off with nothing up: the clock should start. The launch's screen then appears. */
    private fun clockUp(): Surface {
        nightstand.onScreenOff()
        val surface = Surface()
        ring.onRingCreated(surface)
        interactive = true
        nightstand.onScreenOn()
        return surface
    }

    @Test
    fun `T59 - screen off on the charger, locked, starts the charging clock and darkens the keyboard`() {
        nightstand.onScreenOff()
        assertEquals(1, launcher.nightstands)
        assertEquals(0, switch.value)
        assertTrue(ring.state.sources.isEmpty(), "nothing waiting: the clock alone")
    }

    @Test
    fun `T60 - the clock is seeded with the qualifying notifications still in the shade, oldest first`() {
        shade = listOf(
            waiting("com.b", "b", at = 20),
            waiting("com.a", "a", at = 10),
            waiting("com.music", "m", at = 5, ongoing = true),
            waiting("brobata.physiboard.dev3", "own", at = 1),
        )
        nightstand.onScreenOff()
        assertEquals(listOf("com.a", "com.b"), ring.state.sources.map { it.packageName })
    }

    @Test
    fun `T61 - off any of the three switches, unplugged, face down or with the screen on - no clock`() {
        store.update { it.copy(device = it.device.copy(ringStayOnCharging = false)) }
        nightstand.onScreenOff()
        store.update { it.copy(device = it.device.copy(ringStayOnCharging = true, ringClock = false)) }
        nightstand.onScreenOff()
        store.update { it.copy(device = it.device.copy(ringClock = true)) }
        plugged = false
        nightstand.onScreenOff()
        plugged = true
        covered = true
        nightstand.onScreenOff()
        covered = false
        interactive = true
        nightstand.onScreenOff()
        assertEquals(0, launcher.nightstands)
        assertEquals(1, switch.value)
    }

    @Test
    fun `T62 - not locked yet - checked again on the retry chain, started once it locks`() {
        locked = false
        nightstand.onScreenOff()
        assertEquals(0, launcher.nightstands)
        assertEquals(listOf(6_000L), retries.delays)
        retries.fire()
        assertEquals(listOf(6_000L, 10_000L), retries.delays)
        locked = true
        retries.fire()
        assertEquals(1, launcher.nightstands)
        assertTrue(!retries.isPending)
    }

    @Test
    fun `T63 - never locked - the chain ends after the last check`() {
        locked = false
        nightstand.onScreenOff()
        repeat(10) { retries.fire() }
        assertEquals(Nightstand.RETRY_DELAYS_MS, retries.delays)
        assertEquals(0, launcher.nightstands)
    }

    @Test
    fun `T64 - the user waking the phone cancels the pending check`() {
        locked = false
        nightstand.onScreenOff()
        interactive = true
        nightstand.onScreenOn()
        assertEquals(1, retries.cancels)
        assertTrue(!retries.isPending)
    }

    @Test
    fun `T65 - power pressed on the clock keeps it dark, and it comes back after the next wake and sleep`() {
        val surface = clockUp()
        // Power: the screen goes off while the clock is up.
        interactive = false
        nightstand.onScreenOff()
        ring.onRingDestroyed(surface)
        assertEquals(1, launcher.nightstands, "put away: no relaunch")
        assertTrue(ring.isPutAway())
        // A plain screen-off a moment later (still put away) starts nothing either.
        nightstand.onScreenOff()
        assertEquals(1, launcher.nightstands)
        // The user wakes the phone, then turns it off again: the nightstand behaviour.
        clock.nowMs += 60_000
        interactive = true
        nightstand.onScreenOn()
        interactive = false
        nightstand.onScreenOff()
        assertEquals(2, launcher.nightstands)
    }

    @Test
    fun `T66 - plugging in forgets put-away and starts the clock on a dark, locked phone`() {
        val surface = clockUp()
        interactive = false
        nightstand.onScreenOff()
        ring.onRingDestroyed(surface)
        nightstand.onPowerConnected()
        assertEquals(2, launcher.nightstands)
    }

    @Test
    fun `a notification landing on the bare charging clock joins it instead of being skipped`() {
        val surface = clockUp()
        ring.onNotificationPosted(waiting("com.a", "a", 1).candidate, 0xFF2F80ED.toInt(), "a", "brobata.physiboard.dev3")
        assertEquals(listOf("com.a"), surface.changes.single().map { it.packageName })
        assertEquals(0, launcher.launches.size)
        ring.onNotificationRemoved("a")
        assertEquals(1, surface.finished, "the surface is told; the charging hold decides whether it actually finishes")
    }

    @Test
    fun `a second screen-off while the launch is on its way does not launch twice`() {
        nightstand.onScreenOff()
        nightstand.onScreenOff()
        assertEquals(1, launcher.nightstands)
        assertNull(retries.delays.firstOrNull())
    }
}
