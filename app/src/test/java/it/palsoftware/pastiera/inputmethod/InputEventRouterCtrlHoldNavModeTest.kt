package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InputEventRouterCtrlHoldNavModeTest {

    private lateinit var context: Context
    private lateinit var router: InputEventRouter

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.getPreferences(context).edit().clear().commit()

        val modifierStateController = ModifierStateController(300L)
        val navModeController = NavModeController(context, modifierStateController)
        router = InputEventRouter(context, navModeController)
    }

    @Test
    fun heldCtrl_defaultsToAppShortcutPassthrough() {
        val inputConnection = mockInputConnection()
        val event = ctrlKeyDown(KeyEvent.KEYCODE_D)

        val handled = router.handleCtrlModifiedKey(
            keyCode = KeyEvent.KEYCODE_D,
            event = event,
            inputConnection = inputConnection,
            ctrlKeyMap = navMapping,
            ctrlLatchFromNavMode = false,
            ctrlOneShot = false,
            ctrlPhysicallyPressed = true,
            clearCtrlOneShot = {},
            updateStatusBar = {},
            callSuper = { false },
            toggleMinimalUi = {}
        )

        assertTrue(handled)
        val sentEvent = captureSentKeyEvents(inputConnection, expectedCount = 1).single()
        assertEquals(KeyEvent.KEYCODE_D, sentEvent.keyCode)
    }

    @Test
    fun heldCtrl_whenEnabledUsesNavModeMapping() {
        SettingsManager.setNavModeCtrlHoldEnabled(context, true)
        val inputConnection = mockInputConnection()

        val handled = router.handleCtrlModifiedKey(
            keyCode = KeyEvent.KEYCODE_D,
            event = ctrlKeyDown(KeyEvent.KEYCODE_D),
            inputConnection = inputConnection,
            ctrlKeyMap = navMapping,
            ctrlLatchFromNavMode = false,
            ctrlOneShot = false,
            ctrlPhysicallyPressed = true,
            clearCtrlOneShot = {},
            updateStatusBar = {},
            callSuper = { false },
            toggleMinimalUi = {}
        )

        assertTrue(handled)
        val sentEvents = captureSentKeyEvents(inputConnection, expectedCount = 2)
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), sentEvents.map { it.action })
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_LEFT), sentEvents.map { it.keyCode })
    }

    @Test
    fun heldCtrl_whenEnabledAndNoMappingStillPassesShortcutToApp() {
        SettingsManager.setNavModeCtrlHoldEnabled(context, true)
        val inputConnection = mockInputConnection()

        val handled = router.handleCtrlModifiedKey(
            keyCode = KeyEvent.KEYCODE_B,
            event = ctrlKeyDown(KeyEvent.KEYCODE_B),
            inputConnection = inputConnection,
            ctrlKeyMap = navMapping,
            ctrlLatchFromNavMode = false,
            ctrlOneShot = false,
            ctrlPhysicallyPressed = true,
            clearCtrlOneShot = {},
            updateStatusBar = {},
            callSuper = { false },
            toggleMinimalUi = {}
        )

        assertTrue(handled)
        val sentEvent = captureSentKeyEvents(inputConnection, expectedCount = 1).single()
        assertEquals(KeyEvent.KEYCODE_B, sentEvent.keyCode)
    }

    private fun mockInputConnection(): InputConnection {
        val inputConnection = mock(InputConnection::class.java)
        `when`(inputConnection.sendKeyEvent(any(KeyEvent::class.java))).thenReturn(true)
        return inputConnection
    }

    private fun captureSentKeyEvents(inputConnection: InputConnection, expectedCount: Int): List<KeyEvent> {
        val captor = ArgumentCaptor.forClass(KeyEvent::class.java)
        verify(inputConnection, times(expectedCount)).sendKeyEvent(captor.capture())
        return captor.allValues
    }

    private fun ctrlKeyDown(keyCode: Int): KeyEvent {
        return KeyEvent(
            0L,
            0L,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        )
    }

    private companion object {
        val navMapping = mapOf(
            KeyEvent.KEYCODE_D to KeyMappingLoader.CtrlMapping("keycode", "DPAD_LEFT")
        )
    }
}
