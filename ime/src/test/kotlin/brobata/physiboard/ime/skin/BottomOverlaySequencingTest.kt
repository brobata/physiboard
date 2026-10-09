package brobata.physiboard.ime.skin

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.provider.Settings
import android.view.WindowManager
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.ime.actions.BottomOverlay
import brobata.physiboard.ime.actions.SkinTonePanelController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.util.concurrent.TimeUnit

/**
 * The panels' open and close motion never leaves a window behind (app-shell.md SS22.5): a panel
 * hidden before its first frame, one closed on its animation, and one closed while the service
 * tears down are all gone from the window manager, and a closing panel stops taking touches at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BottomOverlaySequencingTest {

    class HarnessIme : InputMethodService()

    private lateinit var service: InputMethodService
    private val forms = listOf("é", "è", "ê")

    @Before
    fun setUp() {
        ShadowSettings.setCanDrawOverlays(true)
        service = Robolectric.buildService(HarnessIme::class.java).create().get()
        Settings.Global.putFloat(service.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private fun windows() = Shadow.extract<ShadowWindowManagerImpl>(service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).views

    private fun show(bar: SkinTonePanelController) = bar.show(forms, listOf(null, null, null), StripTheme.SLATE_DARK, 0, {}, {})

    @Test
    fun `a panel hidden before its first frame is removed`() {
        val bar = SkinTonePanelController(service)
        show(bar)
        bar.hide()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertEquals(0, windows().size)
    }

    @Test
    fun `a panel replaced again and again leaves only the last window`() {
        val bar = SkinTonePanelController(service)
        repeat(4) { show(bar) }
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertEquals(1, windows().size)
    }

    @Test
    fun `a closing panel takes no touches and its window goes when the motion ends`() {
        val bar = SkinTonePanelController(service)
        show(bar)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        val view = windows().single()
        bar.hide()
        assertFalse(bar.isShown)
        val flags = (view.layoutParams as WindowManager.LayoutParams).flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        assertEquals(0, windows().size)
    }

    @Test
    fun `teardown removes at once`() {
        val bar = SkinTonePanelController(service)
        show(bar)
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        BottomOverlay.teardown = true
        try {
            bar.hide()
            assertEquals(0, windows().size)
        } finally {
            BottomOverlay.teardown = false
        }
    }
}
