package brobata.physiboard.device.privileged.ring

import android.app.Application
import android.app.KeyguardManager
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.view.ViewGroup
import android.view.WindowManager
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.device.privileged.InMemoryDeviceStateStore
import brobata.physiboard.device.privileged.PrivilegedServices
import brobata.physiboard.device.privileged.PrivilegedServicesOwner
import brobata.physiboard.device.titan.Nightstand
import brobata.physiboard.device.titan.NotificationRingBrightness
import brobata.physiboard.device.titan.RingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration

/** A host for the activity: the process's one [PrivilegedServices] over an in-memory store. */
class RingTestApplication : Application(), PrivilegedServicesOwner {
    val store = InMemoryDeviceStateStore(Settings())
    override val privileged: PrivilegedServices by lazy { PrivilegedServices(this, store) }
}

/**
 * The ring activity's window as Android sees it: the clock on Try it, the minute tick, and the
 * charging clock's hold, brightness and letting go. What the Titan's own lock screen does with
 * the window needs the phone.
 *
 * spec: device-backlight-ring.md SS5.11; test cases T67 to T71.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = RingTestApplication::class)
class NotificationRingActivityTest {

    private val app get() = RuntimeEnvironment.getApplication() as RingTestApplication

    @Before
    fun setUp() {
        app.store.update { it.copy(device = it.device.copy(ringEnabled = true, ringClock = true, ringStayOnCharging = true)) }
        AndroidSettings.System.putString(app.contentResolver, AndroidSettings.System.TIME_12_24, "24")
    }

    @Suppress("DEPRECATION")
    private fun setPlugged(plugged: Boolean) {
        app.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PLUGGED, if (plugged) BatteryManager.BATTERY_PLUGGED_AC else 0))
    }

    private fun setLocked(locked: Boolean) {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(locked)
    }

    private fun launch(intent: Intent): ActivityController<NotificationRingActivity> =
        Robolectric.buildActivity(NotificationRingActivity::class.java, intent).setup()

    private fun ringView(activity: NotificationRingActivity): RingView =
        activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as RingView

    private fun keepsScreenOn(activity: NotificationRingActivity): Boolean =
        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    private fun registeredActions(): Set<String> =
        shadowOf(app).registeredReceivers.flatMap { wrapper -> wrapper.intentFilter.actionsIterator().asSequence().toList() }.toSet()

    @Test
    fun `T67 - Try it with the clock on shows the time in the phone's 24-hour style and follows the minute tick`() {
        val controller = launch(NotificationRingActivity.intent(app, NotificationRingActivity.DEMO_KEY, app.packageName, AndroidRingLauncher.DEMO_COLOR_ARGB, demo = true))
        val activity = controller.get()
        val clock = checkNotNull(ringView(activity).clock) { "no clock drawn" }
        assertTrue("24-hour time: ${clock.time}", Regex("""\d\d:\d\d""").matches(clock.time))
        assertTrue(clock.date.isNotBlank())
        assertTrue(Intent.ACTION_TIME_TICK in registeredActions())
        assertTrue(keepsScreenOn(activity))
        assertEquals(NotificationRingBrightness.NORMAL, activity.window.attributes.screenBrightness, 0f)
        // The demo still ends on its own after 8 s.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(8_100))
        assertTrue(activity.isFinishing)
        controller.destroy()
        assertFalse("the minute tick is let go with the screen", Intent.ACTION_TIME_TICK in registeredActions())
    }

    @Test
    fun `T68 - with the clock off nothing listens for the minute and no clock is drawn`() {
        app.store.update { it.copy(device = it.device.copy(ringClock = false)) }
        val activity = launch(NotificationRingActivity.intent(app, NotificationRingActivity.DEMO_KEY, app.packageName, AndroidRingLauncher.DEMO_COLOR_ARGB, demo = true)).get()
        assertNull(ringView(activity).clock)
        assertFalse(Intent.ACTION_TIME_TICK in registeredActions())
    }

    @Test
    fun `T69 - the charging clock over a locked phone holds the screen at the lowest brightness, with no ring`() {
        setPlugged(true)
        setLocked(true)
        val activity = launch(NotificationRingActivity.nightstandIntent(app)).get()
        assertFalse(activity.isFinishing)
        assertTrue(keepsScreenOn(activity))
        assertEquals(Nightstand.BRIGHTNESS, activity.window.attributes.screenBrightness, 0f)
        assertFalse(ringView(activity).showRing)
        assertNotNull(ringView(activity).clock)
        // Nothing waiting and nothing new: no expiry ends it, however long it stays up.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofHours(8))
        assertFalse(activity.isFinishing)
        assertTrue(keepsScreenOn(activity))
    }

    @Test
    fun `T70 - never over a phone that is not locked`() {
        setPlugged(true)
        setLocked(false)
        val activity = launch(NotificationRingActivity.nightstandIntent(app)).get()
        assertTrue(activity.isFinishing)
    }

    /** Seeds the ring session the way the charging clock's start does, with one waiting notification posted at [postedAtMs]. */
    private fun seed(postedAtMs: Long) {
        val waiting = ActiveRingNotification(
            brobata.physiboard.device.titan.NotificationRingCandidate("com.example", false, false, true, 0),
            0xFF2F80ED.toInt(),
            "waiting",
            postedAtMs,
        )
        app.privileged.ring.startNightstand(listOf(waiting), app.packageName)
        val deadline = System.currentTimeMillis() + 5_000
        while (app.privileged.ring.state.sources.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(1, app.privileged.ring.state.sources.size)
    }

    @Test
    fun `T70 - never over a phone that is not locked, even with notifications waiting`() {
        setPlugged(true)
        setLocked(false)
        seed(postedAtMs = 1L)
        val activity = launch(NotificationRingActivity.nightstandIntent(app)).get()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `T75 - a charging clock seeded with an old notification holds its glow still, and still after unplugging`() {
        setPlugged(true)
        setLocked(true)
        seed(postedAtMs = 1L)
        val activity = launch(NotificationRingActivity.nightstandIntent(app)).get()
        assertTrue(ringView(activity).showRing)
        assertEquals(NotificationRingActivity.STILL_BREATH, ringView(activity).breath, 0f)
        assertEquals(Nightstand.BRIGHTNESS, activity.window.attributes.screenBrightness, 0f)
        setPlugged(false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(900))
        assertEquals("unplugging does not start the breathing", NotificationRingActivity.STILL_BREATH, ringView(activity).breath, 0f)
    }

    @Test
    fun `T76 - a notification that arrived while the charging clock was on its way starts the ring's minutes`() {
        setPlugged(true)
        setLocked(true)
        val intent = NotificationRingActivity.nightstandIntent(app)
        seed(postedAtMs = System.currentTimeMillis() + 60_000)
        val activity = launch(intent).get()
        assertEquals(NotificationRingBrightness.NORMAL, activity.window.attributes.screenBrightness, 0f)
    }

    @Test
    fun `T77 - the power key on a held clock lets go of the session at once, not at a late onDestroy`() {
        setPlugged(true)
        setLocked(true)
        val activity = launch(NotificationRingActivity.nightstandIntent(app)).get()
        assertTrue(app.privileged.ring.isScreenHeld())
        app.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
        assertFalse(app.privileged.ring.isScreenHeld())
        assertFalse(app.privileged.ring.isRingOnScreenOrPending())
        assertTrue("still put away", app.privileged.ring.isPutAway())
    }

    @Test
    fun `T71 - a notification brightens and breathes for the ring's minutes, then the clock goes back to the floor, and unplugging lets go`() {
        setPlugged(true)
        setLocked(true)
        val activity = launch(NotificationRingActivity.nightstandIntent(app)).get()
        activity.onSourcesChanged(listOf(RingSource("k", "com.example", 0xFF2F80ED.toInt(), 1L)))
        assertTrue(ringView(activity).showRing)
        assertEquals(NotificationRingBrightness.NORMAL, activity.window.attributes.screenBrightness, 0f)
        // The ring's 10 minutes pass: still held, back to the floor, glow still showing.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(11))
        assertFalse(activity.isFinishing)
        assertTrue(keepsScreenOn(activity))
        assertEquals(Nightstand.BRIGHTNESS, activity.window.attributes.screenBrightness, 0f)
        // The notification is dismissed elsewhere: the glow goes, the clock stays.
        activity.finishRing()
        assertFalse(activity.isFinishing)
        assertFalse(ringView(activity).showRing)
        // Unplugged: the screen is no longer held; the phone's own timeout ends it, black to black.
        setPlugged(false)
        app.sendBroadcast(Intent(Intent.ACTION_POWER_DISCONNECTED))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(keepsScreenOn(activity))
        assertFalse(activity.isFinishing)
        assertEquals("letting go never brightens the clock", Nightstand.BRIGHTNESS, activity.window.attributes.screenBrightness, 0f)
    }
}
