package brobata.physiboard.device.privileged.ring

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.device.privileged.DirectExecutor
import brobata.physiboard.device.privileged.FakeClock
import brobata.physiboard.device.privileged.FakeTimer
import brobata.physiboard.device.privileged.InMemoryDeviceStateStore
import brobata.physiboard.device.privileged.backlight.FakeMasterSwitch
import brobata.physiboard.device.privileged.setup.FakePermissionProbe
import brobata.physiboard.device.titan.NotificationRingCandidate
import brobata.physiboard.device.titan.NotificationRingColor
import brobata.physiboard.device.titan.RingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: device-backlight-ring.md SS5.2, SS5.4, SS5.7, SS5.8; test cases T42 to T44 against the real orchestration. */
class RingCoordinatorTest {

    private class RecordingLauncher : RingLauncher {
        val launches = mutableListOf<List<RingSource>>()
        var demos = 0
        override fun launch(sources: List<RingSource>) { launches += sources }
        override fun launchDemo() { demos++ }
    }

    private class RecordingSurface : RingSurface {
        val changes = mutableListOf<List<RingSource>>()
        var finished = 0
        override fun onSourcesChanged(sources: List<RingSource>) { changes += sources }
        override fun finishRing() { finished++ }
    }

    private val store = InMemoryDeviceStateStore(Settings())
    private val switch = FakeMasterSwitch(value = 1)
    private val permissions = FakePermissionProbe(writeSecureSettings = true)
    private val backlight = RingBacklight(store, switch, permissions, FakeTimer(), FakeClock())
    private val launcher = RecordingLauncher()
    private var screenInteractive = false
    private var pocketCovered = false
    private var pocketChecks = 0
    private val clock = FakeClock()
    private val coordinator = RingCoordinator(
        store = store,
        backlight = backlight,
        launcher = launcher,
        screen = { screenInteractive },
        pocket = { pocketChecks++; pocketCovered },
        worker = DirectExecutor,
        surfaceExecutor = DirectExecutor,
        clock = clock,
    )

    private fun message(pkg: String) = NotificationRingCandidate(pkg, isOngoingOrForegroundService = false, isGroupSummary = false, isClearable = true, priority = 0)

    private fun post(pkg: String, key: String = "$pkg|1", color: Int = 0xFF2F80ED.toInt()) =
        coordinator.onNotificationPosted(message(pkg), color, key, ownPackageName = "brobata.physiboard.dev3")

    @Test
    fun `a qualifying notification on a dark, uncovered screen darkens the keyboard and launches the ring`() {
        post("com.a")
        assertEquals(1, launcher.launches.size)
        assertEquals(listOf("com.a"), launcher.launches[0].map { it.packageName })
        assertEquals(0, switch.value, "the keyboard is darkened before the launch (SS5.2 step 7)")
        assertEquals(1, pocketChecks)
    }

    @Test
    fun `the ring is disabled - nothing happens`() {
        store.update { it.copy(device = it.device.copy(ringEnabled = false)) }
        post("com.a")
        assertTrue(launcher.launches.isEmpty())
        assertEquals(1, switch.value)
    }

    @Test
    fun `the app's own notifications never ring, whatever package the build runs as`() {
        post("brobata.physiboard.dev3")
        post("brobata.physiboard")
        assertTrue(launcher.launches.isEmpty())
    }

    @Test
    fun `the policy's skips apply - an ongoing notification never rings`() {
        coordinator.onNotificationPosted(message("com.a").copy(isOngoingOrForegroundService = true), 0, "k", "own")
        assertTrue(launcher.launches.isEmpty())
    }

    @Test
    fun `the screen being on skips the ring without a pocket check`() {
        screenInteractive = true
        post("com.a")
        assertTrue(launcher.launches.isEmpty())
        assertEquals(0, pocketChecks)
        assertEquals(1, switch.value)
    }

    @Test
    fun `a covered phone skips the ring and leaves the keyboard alone`() {
        pocketCovered = true
        post("com.a")
        assertTrue(launcher.launches.isEmpty())
        assertEquals(1, switch.value)
    }

    @Test
    fun `T42 - source B while showing A recolours to B, icons A then B, then A removed leaves B, ring still up`() {
        post("com.a", color = 0xFFEF4444.toInt())
        val surface = RecordingSurface()
        coordinator.onRingCreated(surface)
        post("com.b", color = 0xFF2F80ED.toInt())
        assertEquals(1, surface.changes.size)
        assertEquals(listOf("com.a", "com.b"), surface.changes[0].map { it.packageName })
        assertEquals(0xFF2F80ED.toInt(), surface.changes[0].last().colorArgb)
        assertEquals(1, launcher.launches.size, "one ring for all waiting apps")
        coordinator.onNotificationRemoved("com.a|1")
        assertEquals(listOf("com.b"), surface.changes[1].map { it.packageName })
        assertEquals(0, surface.finished)
    }

    @Test
    fun `T43 - ring showing A, A removed - the ring finishes`() {
        post("com.a")
        val surface = RecordingSurface()
        coordinator.onRingCreated(surface)
        coordinator.onNotificationRemoved("com.a|1")
        assertEquals(1, surface.finished)
        assertTrue(coordinator.state.sources.isEmpty())
    }

    @Test
    fun `T44 - four distinct packages added - the surface is told all four, the icon rule keeps the last 3 (titan's)`() {
        post("com.a")
        val surface = RecordingSurface()
        coordinator.onRingCreated(surface)
        post("com.b")
        post("com.c")
        post("com.d")
        assertEquals(listOf("com.a", "com.b", "com.c", "com.d"), surface.changes.last().map { it.packageName })
        assertEquals(listOf("com.b", "com.c", "com.d"), brobata.physiboard.device.titan.NotificationRingLifecycle.icons(coordinator.state))
    }

    @Test
    fun `the activity's creation takes ownership and returns the sources, its teardown restores the keyboard and clears the session`() {
        post("com.a")
        val surface = RecordingSurface()
        assertEquals(listOf("com.a"), coordinator.onRingCreated(surface).map { it.packageName })
        assertEquals(0, switch.value)
        coordinator.onRingDestroyed(surface)
        assertEquals(1, switch.value)
        assertTrue(coordinator.state.sources.isEmpty())
        assertFalse(store.snapshot().captures.ringBacklightPrevCaptured)
    }

    @Test
    fun `a stale surface's teardown does not touch a newer ring`() {
        post("com.a")
        val old = RecordingSurface()
        coordinator.onRingCreated(old)
        val current = RecordingSurface()
        coordinator.onRingCreated(current)
        coordinator.onRingDestroyed(old)
        assertEquals(listOf("com.a"), coordinator.state.sources.map { it.packageName })
        assertEquals(0, switch.value)
    }

    @Test
    fun `a removal with no ring up is ignored`() {
        coordinator.onNotificationRemoved("nothing")
        assertTrue(coordinator.state.sources.isEmpty())
    }

    @Test
    fun `the demo launches directly without the policy, the pocket check or the keyboard`() {
        screenInteractive = true
        coordinator.startDemo()
        assertEquals(1, launcher.demos)
        assertEquals(0, pocketChecks)
        assertEquals(1, switch.value)
    }

    // Colour. spec: SS5.4; T21, T22 with the user's default. -------------------------------------

    @Test
    fun `T21 - a visible declared colour is itself`() {
        assertEquals(0xFF1E88E5.toInt(), RingCoordinator.resolveColor("com.a", 0xFF1E88E5.toInt(), store.snapshot().device))
    }

    @Test
    fun `T22 - colour 0 and a near-black colour both fall back to the default, the user's when set`() {
        val prefs = store.snapshot().device
        assertEquals(NotificationRingColor.DEFAULT_COLOR_ARGB, RingCoordinator.resolveColor("com.a", 0, prefs))
        assertEquals(NotificationRingColor.DEFAULT_COLOR_ARGB, RingCoordinator.resolveColor("com.a", 0xFF101010.toInt(), prefs))
        val custom = prefs.copy(ringDefaultColor = 0xFFA855F7.toInt())
        assertEquals(0xFFA855F7.toInt(), RingCoordinator.resolveColor("com.a", 0, custom))
        assertEquals(0xFFA855F7.toInt(), RingCoordinator.resolveColor("com.a", 0xFF101010.toInt(), custom))
    }

    @Test
    fun `a per-app colour wins over everything`() {
        val prefs = store.snapshot().device.copy(ringAppColors = mapOf("com.a" to 0xFFF472B6.toInt()))
        assertEquals(0xFFF472B6.toInt(), RingCoordinator.resolveColor("com.a", 0xFF1E88E5.toInt(), prefs))
    }
}
