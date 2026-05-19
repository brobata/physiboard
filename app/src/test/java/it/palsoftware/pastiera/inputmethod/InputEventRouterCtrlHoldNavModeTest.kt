package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
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
    private lateinit var modifierStateController: ModifierStateController

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.getPreferences(context).edit().clear().commit()

        modifierStateController = ModifierStateController(300L)
        val navModeController = NavModeController(context, modifierStateController)
        router = InputEventRouter(context, navModeController)
    }

    @Test
    fun heldCtrl_defaultsToAppShortcutPassthrough() {
        LayoutMappingRepository.loadLayout(context.assets, "qwerty", context)
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
    fun heldCtrl_whenLayoutAwareEnabled_usesActiveLayoutForPassthroughShortcutKey() {
        SettingsManager.setLayoutAwareCtrlShortcutsEnabled(context, true)
        LayoutMappingRepository.loadLayout(context.assets, "qwertz", context)
        val inputConnection = mockInputConnection()

        val handled = router.handleCtrlModifiedKey(
            keyCode = KeyEvent.KEYCODE_Y,
            event = ctrlKeyDown(KeyEvent.KEYCODE_Y),
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
        assertEquals(KeyEvent.KEYCODE_Z, sentEvent.keyCode)
    }

    @Test
    fun heldCtrl_whenLayoutAwareDisabled_passesOriginalKeycodeThrough() {
        LayoutMappingRepository.loadLayout(context.assets, "qwertz", context)
        val inputConnection = mockInputConnection()

        val handled = router.handleCtrlModifiedKey(
            keyCode = KeyEvent.KEYCODE_Z,
            event = ctrlKeyDown(KeyEvent.KEYCODE_Z),
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
        assertEquals(KeyEvent.KEYCODE_Z, sentEvent.keyCode)
    }

    @Test
    fun heldCtrl_whenLayoutAwareEnabled_usesActiveLayoutForQwertzYKeyPassthrough() {
        SettingsManager.setLayoutAwareCtrlShortcutsEnabled(context, true)
        LayoutMappingRepository.loadLayout(context.assets, "qwertz", context)
        val inputConnection = mockInputConnection()

        val handled = router.handleCtrlModifiedKey(
            keyCode = KeyEvent.KEYCODE_Z,
            event = ctrlKeyDown(KeyEvent.KEYCODE_Z),
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
        assertEquals(KeyEvent.KEYCODE_Y, sentEvent.keyCode)
    }

    @Test
    fun ctrlOneShot_whenLayoutAwareEnabled_usesActiveLayoutForConfiguredMappingLookup() {
        SettingsManager.setLayoutAwareCtrlShortcutsEnabled(context, true)
        LayoutMappingRepository.loadLayout(context.assets, "qwertz", context)
        val inputConnection = mockInputConnection()
        val mapping = mapOf(
            KeyEvent.KEYCODE_Z to KeyMappingLoader.CtrlMapping("action", "undo")
        )

        val handled = router.handleCtrlModifiedKey(
            keyCode = KeyEvent.KEYCODE_Y,
            event = keyDown(KeyEvent.KEYCODE_Y),
            inputConnection = inputConnection,
            ctrlKeyMap = mapping,
            ctrlLatchFromNavMode = false,
            ctrlOneShot = true,
            ctrlPhysicallyPressed = false,
            clearCtrlOneShot = {},
            updateStatusBar = {},
            callSuper = { false },
            toggleMinimalUi = {}
        )

        assertTrue(handled)
        verify(inputConnection, times(1)).performContextMenuAction(android.R.id.undo)
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
    fun latchedNavMode_performsActionMappings() {
        modifierStateController.ctrlLatchActive = true
        modifierStateController.ctrlLatchFromNavMode = true
        val inputConnection = mockInputConnection()
        val mapping = mapOf(
            KeyEvent.KEYCODE_Z to KeyMappingLoader.CtrlMapping("action", "undo")
        )

        val handled = router.handleKeyDownWithNoEditableField(
            keyCode = KeyEvent.KEYCODE_Z,
            event = keyDown(KeyEvent.KEYCODE_Z),
            ctrlKeyMap = mapping,
            callbacks = InputEventRouter.NoEditableFieldCallbacks(
                isShortcutKey = { false },
                isLauncherPackage = { false },
                handleLauncherShortcut = { false },
                handlePowerShortcut = { false },
                togglePowerShortcutMode = { _, _ -> },
                callSuper = { false },
                currentInputConnection = { inputConnection }
            ),
            ctrlLatchActive = true,
            editorInfo = null,
            currentPackageName = null,
            powerShortcutsEnabled = false
        )

        assertTrue(handled)
        verify(inputConnection, times(1)).performContextMenuAction(android.R.id.undo)
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

    @Test
    fun heldCtrl_whenMappingIsNativeCtrl_passesShortcutToApp() {
        SettingsManager.setNavModeCtrlHoldEnabled(context, true)
        val inputConnection = mockInputConnection()
        val mapping = mapOf(
            KeyEvent.KEYCODE_I to KeyMappingLoader.CtrlMapping("native_ctrl", "")
        )

        val handled = router.handleCtrlModifiedKey(
            keyCode = KeyEvent.KEYCODE_I,
            event = ctrlKeyDown(KeyEvent.KEYCODE_I),
            inputConnection = inputConnection,
            ctrlKeyMap = mapping,
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
        assertEquals(KeyEvent.KEYCODE_I, sentEvent.keyCode)
        assertTrue(sentEvent.isCtrlPressed)
    }

    @Test
    fun latchedNavMode_whenMappingIsNativeCtrl_sendsCtrlCombo() {
        modifierStateController.ctrlLatchActive = true
        modifierStateController.ctrlLatchFromNavMode = true
        val inputConnection = mockInputConnection()
        val mapping = mapOf(
            KeyEvent.KEYCODE_I to KeyMappingLoader.CtrlMapping("native_ctrl", "")
        )

        val handled = router.handleKeyDownWithNoEditableField(
            keyCode = KeyEvent.KEYCODE_I,
            event = keyDown(KeyEvent.KEYCODE_I),
            ctrlKeyMap = mapping,
            callbacks = InputEventRouter.NoEditableFieldCallbacks(
                isShortcutKey = { false },
                isLauncherPackage = { false },
                handleLauncherShortcut = { false },
                handlePowerShortcut = { false },
                togglePowerShortcutMode = { _, _ -> },
                callSuper = { false },
                currentInputConnection = { inputConnection }
            ),
            ctrlLatchActive = true,
            editorInfo = null,
            currentPackageName = null,
            powerShortcutsEnabled = false
        )

        assertTrue(handled)
        val sentEvents = captureSentKeyEvents(inputConnection, expectedCount = 2)
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), sentEvents.map { it.action })
        assertEquals(listOf(KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_I), sentEvents.map { it.keyCode })
        assertTrue(sentEvents.all { it.isCtrlPressed })
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

    private fun keyDown(keyCode: Int): KeyEvent {
        return KeyEvent(
            0L,
            0L,
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            0
        )
    }

    private companion object {
        val navMapping = mapOf(
            KeyEvent.KEYCODE_D to KeyMappingLoader.CtrlMapping("keycode", "DPAD_LEFT")
        )
    }
}
