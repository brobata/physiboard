package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QuickLauncherActivityTest {

    @Test
    fun backClosesOnKeyUpSoReleaseIsNotForwardedToUnderlyingApp() {
        val activity = Robolectric.buildActivity(QuickLauncherActivity::class.java)
            .setup()
            .get()
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)

        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_BACK, down))
        assertFalse(activity.isFinishing)

        assertTrue(activity.onKeyUp(KeyEvent.KEYCODE_BACK, up))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun heldSymPlusQuickLauncherShortcutTogglesOpenLauncherClosedOnKeyUp() {
        SettingsManager.setQuickLauncherShortcut(
            RuntimeEnvironment.getApplication(),
            KeyEvent.KEYCODE_SPACE
        )
        val activity = Robolectric.buildActivity(QuickLauncherActivity::class.java)
            .setup()
            .get()
        val down = KeyEvent(
            1_000L,
            1_020L,
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_SPACE,
            0,
            KeyEvent.META_SYM_ON
        )
        val up = KeyEvent(
            1_000L,
            1_040L,
            KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_SPACE,
            0,
            KeyEvent.META_SYM_ON
        )

        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_SPACE, down))
        assertFalse(activity.isFinishing)

        assertTrue(activity.onKeyUp(KeyEvent.KEYCODE_SPACE, up))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun plainSymKeyIsNotConsumedByQuickLauncherActivity() {
        val activity = Robolectric.buildActivity(QuickLauncherActivity::class.java)
            .setup()
            .get()
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SYM)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SYM)

        assertFalse(activity.onKeyDown(KeyEvent.KEYCODE_SYM, down))
        assertFalse(activity.onKeyUp(KeyEvent.KEYCODE_SYM, up))
        assertFalse(activity.isFinishing)
    }

    @Test
    fun normalLetterStillAddsToSearchQuery() {
        val activity = Robolectric.buildActivity(QuickLauncherActivity::class.java)
            .setup()
            .get()
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)

        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_A, event))
    }

    @Test
    fun altModifiedLetterStillUsesQuickLauncherLayoutSearch() {
        val activity = Robolectric.buildActivity(QuickLauncherActivity::class.java)
            .setup()
            .get()
        val event = KeyEvent(
            1_000L,
            1_020L,
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_A,
            0,
            KeyEvent.META_ALT_ON
        )

        assertTrue(activity.onKeyDown(KeyEvent.KEYCODE_A, event))
    }
}
