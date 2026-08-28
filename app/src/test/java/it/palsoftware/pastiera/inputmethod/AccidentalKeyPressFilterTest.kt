package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccidentalKeyPressFilterTest {
    private val filter = AccidentalKeyPressFilter()
    private val overlapOn = AccidentalKeyPressFilter.Configuration(
        overlapRule = AccidentalKeyPressFilter.OverlapRule.ALL
    )
    private val overlapOff = AccidentalKeyPressFilter.Configuration(
        overlapRule = AccidentalKeyPressFilter.OverlapRule.NONE
    )

    @Test
    fun aSecondKeyGoingDownWhileTheFirstIsHeldIsSuppressed() {
        assertNull(down(KeyEvent.KEYCODE_A, scanCode = 30, configuration = overlapOn))
        val suppressed = down(KeyEvent.KEYCODE_S, scanCode = 31, configuration = overlapOn)

        assertNotNull(suppressed)
        assertEquals(AccidentalKeyPressFilter.Reason.OVERLAPPING_KEY, suppressed?.reason)
    }

    @Test
    fun theSuppressedKeysOwnKeyUpIsSwallowedSoTheyStayBalanced() {
        down(KeyEvent.KEYCODE_A, scanCode = 30, configuration = overlapOn)
        down(KeyEvent.KEYCODE_S, scanCode = 31, configuration = overlapOn)

        val result = filter.onKeyUp(KeyEvent.KEYCODE_S, event(KeyEvent.KEYCODE_S, 31, KeyEvent.ACTION_UP))
        assertEquals(
            AccidentalKeyPressFilter.Reason.OVERLAPPING_KEY,
            (result as? AccidentalKeyPressFilter.KeyUpResult.Suppressed)?.event?.reason
        )
        // The key that was actually held passes its up through untouched.
        assertNull(filter.onKeyUp(KeyEvent.KEYCODE_A, event(KeyEvent.KEYCODE_A, 30, KeyEvent.ACTION_UP)))
    }

    @Test
    fun overlapIsAllowedOnceTheFirstKeyIsReleased() {
        down(KeyEvent.KEYCODE_A, scanCode = 30, configuration = overlapOn)
        filter.onKeyUp(KeyEvent.KEYCODE_A, event(KeyEvent.KEYCODE_A, 30, KeyEvent.ACTION_UP))

        assertNull(down(KeyEvent.KEYCODE_S, scanCode = 31, configuration = overlapOn))
    }

    @Test
    fun withTheRuleOffOverlappingKeysBothGoThrough() {
        assertNull(down(KeyEvent.KEYCODE_A, scanCode = 30, configuration = overlapOff))
        assertNull(down(KeyEvent.KEYCODE_S, scanCode = 31, configuration = overlapOff))
    }

    @Test
    fun modifiersAreNeverSuppressed() {
        down(KeyEvent.KEYCODE_A, scanCode = 30, configuration = overlapOn)

        assertNull(
            down(
                KeyEvent.KEYCODE_SHIFT_LEFT,
                scanCode = 42,
                configuration = overlapOn,
                isModifier = true
            )
        )
    }

    @Test
    fun autoRepeatOfAHeldKeyIsNotTreatedAsAnOverlap() {
        down(KeyEvent.KEYCODE_A, scanCode = 30, configuration = overlapOn)

        assertNull(
            filter.shouldConsumeKeyDown(
                keyCode = KeyEvent.KEYCODE_A,
                event = event(KeyEvent.KEYCODE_A, 30, KeyEvent.ACTION_DOWN, repeat = 3),
                isModifier = false,
                configuration = overlapOn
            )
        )
    }

    @Test
    fun keysOnDifferentDevicesDoNotOverlapEachOther() {
        down(KeyEvent.KEYCODE_A, scanCode = 30, configuration = overlapOn, deviceId = 1)

        assertNull(down(KeyEvent.KEYCODE_S, scanCode = 31, configuration = overlapOn, deviceId = 2))
    }

    @Test
    fun resetClearsHeldState() {
        down(KeyEvent.KEYCODE_A, scanCode = 30, configuration = overlapOn)
        filter.reset()

        assertNull(down(KeyEvent.KEYCODE_S, scanCode = 31, configuration = overlapOn))
    }

    private fun down(
        keyCode: Int,
        scanCode: Int,
        configuration: AccidentalKeyPressFilter.Configuration,
        isModifier: Boolean = false,
        deviceId: Int = 1
    ) = filter.shouldConsumeKeyDown(
        keyCode = keyCode,
        event = event(keyCode, scanCode, KeyEvent.ACTION_DOWN, deviceId = deviceId),
        isModifier = isModifier,
        configuration = configuration
    )

    private fun event(
        keyCode: Int,
        scanCode: Int,
        action: Int,
        repeat: Int = 0,
        deviceId: Int = 1
    ) = KeyEvent(0L, 0L, action, keyCode, repeat, 0, deviceId, scanCode)
}
