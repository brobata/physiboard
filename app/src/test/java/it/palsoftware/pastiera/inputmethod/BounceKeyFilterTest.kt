package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BounceKeyFilterTest {
    private lateinit var context: Context
    private lateinit var filter: BounceKeyFilter

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.getPreferences(context)
            .edit()
            .clear()
            .commit()
        filter = BounceKeyFilter()
    }

    @Test
    fun sameKeyDownWithinDelay_isSuppressed_andMatchingKeyUpIsConsumed() {
        SettingsManager.setBounceKeysEnabled(context, true)
        SettingsManager.setBounceKeysDelayMs(context, 100L)

        assertNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_A, keyEvent(KeyEvent.ACTION_DOWN, 1_000)))
        assertNull(filter.shouldConsumeKeyUp(KeyEvent.KEYCODE_A, keyEvent(KeyEvent.ACTION_UP, 1_010)))

        val suppressedDown = filter.shouldConsumeKeyDown(
            context,
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, 1_040)
        )

        assertNotNull(suppressedDown)
        assertEquals(BounceKeyFilter.Category.CHARACTER, suppressedDown?.category)
        assertNotNull(filter.shouldConsumeKeyUp(KeyEvent.KEYCODE_A, keyEvent(KeyEvent.ACTION_UP, 1_050)))
    }

    @Test
    fun sameKeyDownAfterDelay_isAccepted() {
        SettingsManager.setBounceKeysEnabled(context, true)
        SettingsManager.setBounceKeysDelayMs(context, 100L)

        assertNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_A, keyEvent(KeyEvent.ACTION_DOWN, 1_000)))
        assertNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_A, keyEvent(KeyEvent.ACTION_DOWN, 1_120)))
    }

    @Test
    fun androidRepeatEvents_areNotSuppressedByBounceFilter() {
        SettingsManager.setBounceKeysEnabled(context, true)
        SettingsManager.setBounceKeysDelayMs(context, 100L)

        assertNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_A, keyEvent(KeyEvent.ACTION_DOWN, 1_000)))
        assertNull(
            filter.shouldConsumeKeyDown(
                context,
                KeyEvent.KEYCODE_A,
                keyEvent(KeyEvent.ACTION_DOWN, 1_020, repeatCount = 1)
            )
        )
    }

    @Test
    fun punctuationKeys_areCharacterKeys() {
        SettingsManager.setBounceKeysEnabled(context, true)
        SettingsManager.setBounceKeysDelayMs(context, 100L)

        listOf(
            KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_SLASH
        ).forEachIndexed { index, keyCode ->
            val baseTime = 1_000L + index * 500L
            assertEquals(BounceKeyFilter.Category.CHARACTER, BounceKeyFilter.categoryFor(keyCode))
            assertNull(filter.shouldConsumeKeyDown(context, keyCode, keyEvent(KeyEvent.ACTION_DOWN, baseTime, keyCode = keyCode)))
            assertNull(filter.shouldConsumeKeyUp(keyCode, keyEvent(KeyEvent.ACTION_UP, baseTime + 10L, keyCode = keyCode)))
            assertNotNull(filter.shouldConsumeKeyDown(context, keyCode, keyEvent(KeyEvent.ACTION_DOWN, baseTime + 40L, keyCode = keyCode)))
        }
    }

    @Test
    fun modifierKeys_areIgnoredByDefault() {
        SettingsManager.setBounceKeysEnabled(context, true)
        SettingsManager.setBounceKeysDelayMs(context, 100L)

        assertNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_SHIFT_LEFT, keyEvent(KeyEvent.ACTION_DOWN, 1_000)))
        assertNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_SHIFT_LEFT, keyEvent(KeyEvent.ACTION_DOWN, 1_020)))
    }

    @Test
    fun modifierKeys_canBeEnabled() {
        SettingsManager.setBounceKeysEnabled(context, true)
        SettingsManager.setBounceKeysModifierKeysEnabled(context, true)
        SettingsManager.setBounceKeysDelayMs(context, 100L)

        assertNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_SHIFT_LEFT, keyEvent(KeyEvent.ACTION_DOWN, 1_000)))
        assertNotNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_SHIFT_LEFT, keyEvent(KeyEvent.ACTION_DOWN, 1_020)))
    }

    @Test
    fun suppressedDownWhileAcceptedDownIsActive_doesNotConsumeRealKeyUp() {
        SettingsManager.setBounceKeysEnabled(context, true)
        SettingsManager.setBounceKeysModifierKeysEnabled(context, true)
        SettingsManager.setBounceKeysDelayMs(context, 100L)

        assertNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_SHIFT_LEFT, keyEvent(KeyEvent.ACTION_DOWN, 1_000)))
        assertNotNull(filter.shouldConsumeKeyDown(context, KeyEvent.KEYCODE_SHIFT_LEFT, keyEvent(KeyEvent.ACTION_DOWN, 1_020)))

        assertNull(filter.shouldConsumeKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT, keyEvent(KeyEvent.ACTION_UP, 1_050)))
    }

    private fun keyEvent(
        action: Int,
        eventTime: Long,
        repeatCount: Int = 0,
        scanCode: Int = 30,
        deviceId: Int = 7,
        keyCode: Int = KeyEvent.KEYCODE_A
    ): KeyEvent {
        return KeyEvent(
            eventTime,
            eventTime,
            action,
            keyCode,
            repeatCount,
            0,
            deviceId,
            scanCode,
            0,
            0
        )
    }
}
