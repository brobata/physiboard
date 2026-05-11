package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.KeyEvent
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InputEventRouterShortcutKeysTest {

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
    fun noEditableField_routesSpecialShortcutKeysToPowerShortcuts() {
        val callbacks = RecordingCallbacks(
            shortcutKeys = specialShortcutKeys,
            powerShortcutResult = true
        )

        specialShortcutKeys.forEach { keyCode ->
            val handled = router.handleKeyDownWithNoEditableField(
                keyCode = keyCode,
                event = keyDown(keyCode),
                ctrlKeyMap = emptyMap(),
                callbacks = callbacks.asRouterCallbacks(),
                ctrlLatchActive = false,
                editorInfo = null,
                currentPackageName = null,
                powerShortcutsEnabled = true
            )

            assertTrue(handled)
        }

        assertEquals(specialShortcutKeys, callbacks.powerShortcutKeys)
        assertEquals(emptyList<Int>(), callbacks.launcherShortcutKeys)
        assertEquals(0, callbacks.callSuperCalls)
    }

    @Test
    fun noEditableField_routesSpecialShortcutKeysToLauncherShortcuts() {
        SettingsManager.setLauncherShortcutsEnabled(context, true)
        val callbacks = RecordingCallbacks(
            shortcutKeys = specialShortcutKeys,
            launcherShortcutResult = true,
            launcherPackageResult = true
        )

        specialShortcutKeys.forEach { keyCode ->
            val handled = router.handleKeyDownWithNoEditableField(
                keyCode = keyCode,
                event = keyDown(keyCode),
                ctrlKeyMap = emptyMap(),
                callbacks = callbacks.asRouterCallbacks(),
                ctrlLatchActive = false,
                editorInfo = null,
                currentPackageName = "com.example.launcher",
                powerShortcutsEnabled = false
            )

            assertTrue(handled)
        }

        assertEquals(specialShortcutKeys, callbacks.launcherShortcutKeys)
        assertEquals(emptyList<Int>(), callbacks.powerShortcutKeys)
        assertEquals(0, callbacks.callSuperCalls)
    }

    private class RecordingCallbacks(
        private val shortcutKeys: List<Int>,
        private val launcherShortcutResult: Boolean = false,
        private val powerShortcutResult: Boolean = false,
        private val launcherPackageResult: Boolean = false
    ) {
        val launcherShortcutKeys = mutableListOf<Int>()
        val powerShortcutKeys = mutableListOf<Int>()
        var callSuperCalls = 0

        fun asRouterCallbacks(): InputEventRouter.NoEditableFieldCallbacks {
            return InputEventRouter.NoEditableFieldCallbacks(
                isShortcutKey = { keyCode -> keyCode in shortcutKeys },
                isLauncherPackage = { launcherPackageResult },
                handleLauncherShortcut = { keyCode ->
                    launcherShortcutKeys += keyCode
                    launcherShortcutResult
                },
                handlePowerShortcut = { keyCode ->
                    powerShortcutKeys += keyCode
                    powerShortcutResult
                },
                togglePowerShortcutMode = { _, _ -> },
                callSuper = {
                    callSuperCalls++
                    false
                },
                currentInputConnection = { null }
            )
        }
    }

    private companion object {
        val specialShortcutKeys = listOf(
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DEL
        )

        fun keyDown(keyCode: Int): KeyEvent {
            return KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        }
    }
}
