package it.palsoftware.pastiera.inputmethod

import android.os.Looper
import android.os.PowerManager
import android.view.MotionEvent
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImeTouchScreenAwakeControllerTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        ShadowPowerManager.clearWakeLocks()
    }

    @Test
    fun `touch down sends a timed screen wake pulse`() {
        val controller = ImeTouchScreenAwakeController(context, pulseDurationMillis = 1_000L)

        controller.onTouchAction(MotionEvent.ACTION_DOWN)

        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertTrue(wakeLock.isHeld)
        assertEquals("Pastiera:ImeTouch", shadowOf(wakeLock).tag)
        shadowOf(Looper.getMainLooper()).idleFor(999, TimeUnit.MILLISECONDS)
        assertTrue(wakeLock.isHeld)
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.MILLISECONDS)
        assertFalse(wakeLock.isHeld)
    }

    @Test
    fun `each touch down renews wake pulse`() {
        val controller = ImeTouchScreenAwakeController(context, pulseDurationMillis = 1_000L)

        controller.onTouchAction(MotionEvent.ACTION_DOWN)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)
        controller.onTouchAction(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)

        assertTrue(wakeLock.isHeld)
        assertEquals(2, shadowOf(wakeLock).timesHeld)
        shadowOf(Looper.getMainLooper()).idleFor(201, TimeUnit.MILLISECONDS)
        assertFalse(wakeLock.isHeld)
    }

    @Test
    fun `non-down actions do not acquire wake lock`() {
        val controller = ImeTouchScreenAwakeController(context)

        controller.onTouchAction(MotionEvent.ACTION_MOVE)
        controller.onTouchAction(MotionEvent.ACTION_UP)

        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }

    @Test
    fun `release clears pending wake pulse`() {
        val controller = ImeTouchScreenAwakeController(context)

        controller.onTouchAction(MotionEvent.ACTION_DOWN)
        val wakeLock: PowerManager.WakeLock = ShadowPowerManager.getLatestWakeLock()
        controller.release()

        assertFalse(wakeLock.isHeld)
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        assertFalse(wakeLock.isHeld)
    }
}
