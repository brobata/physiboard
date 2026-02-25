package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.core.SymLayoutController
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SymPagesConfig
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InputEventRouterModifierE2ETest {

    private lateinit var context: Context
    private lateinit var modifierStateController: ModifierStateController
    private lateinit var router: InputEventRouter
    private lateinit var altSymManager: AltSymManager
    private lateinit var symLayoutController: SymLayoutController
    private lateinit var variationStateController: VariationStateController
    private lateinit var ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>
    private lateinit var inputConnectionRecorder: RecordingInputConnection
    private lateinit var inputConnection: InputConnection

    private val doubleTapThreshold = 300L

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.setSymPagesConfig(context, SymPagesConfig())
        SettingsManager.resetSymMappings(context)
        SettingsManager.resetSymMappingsPage2(context)
        val prefs = context.getSharedPreferences("router_e2e_modifier_tests", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        modifierStateController = ModifierStateController(doubleTapThreshold)
        val navModeController = NavModeController(context, modifierStateController)
        router = InputEventRouter(context, navModeController)

        altSymManager = AltSymManager(context.assets, prefs, context)
        altSymManager.reloadSymMappings()
        altSymManager.reloadSymMappings2()
        symLayoutController = SymLayoutController(context, prefs, altSymManager)
        variationStateController = VariationStateController(emptyMap())
        ctrlKeyMap = KeyMappingLoader.loadCtrlKeyMappings(context.assets, context)

        inputConnectionRecorder = RecordingInputConnection()
        inputConnection = inputConnectionRecorder.asProxy()
    }

    @Test
    fun stickyAltThenA_consumesOneShot_andCleansStateOnRelease() {
        val callbacks = TestCallbacks(modifierStateController)

        val altDownResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_ALT_LEFT,
            event = keyDown(KeyEvent.KEYCODE_ALT_LEFT),
            callbacks = callbacks
        )
        assertTrue(altDownResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(modifierStateController.altOneShot)
        assertTrue(modifierStateController.altPressed)

        modifierStateController.handleAltKeyUp(KeyEvent.KEYCODE_ALT_LEFT)
        assertTrue(modifierStateController.altOneShot)
        assertFalse(modifierStateController.altPressed)
        assertFalse(modifierStateController.altPhysicallyPressed)

        val aResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )
        assertTrue(aResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(1, callbacks.clearAltOneShotCalls)
        assertFalse(modifierStateController.altOneShot)
        assertTrue(inputConnectionRecorder.commitTextCalls > 0)
    }

    @Test
    fun holdAltThenA_consumesDuringHold_andCleansPressedFlagsOnAltRelease() {
        val callbacks = TestCallbacks(modifierStateController)

        val altDownResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_ALT_LEFT,
            event = keyDown(KeyEvent.KEYCODE_ALT_LEFT),
            callbacks = callbacks
        )
        assertTrue(altDownResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(modifierStateController.altPressed)
        assertTrue(modifierStateController.altOneShot)

        val aWhileHeldResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A, metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON),
            callbacks = callbacks
        )
        assertTrue(aWhileHeldResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(1, callbacks.clearAltOneShotCalls)

        modifierStateController.handleAltKeyUp(KeyEvent.KEYCODE_ALT_LEFT)
        assertFalse(modifierStateController.altPressed)
        assertFalse(modifierStateController.altPhysicallyPressed)
        assertFalse(modifierStateController.altOneShot)
    }

    @Test
    fun stickyCtrlThenA_consumesOneShot_andPerformsMappedAction() {
        val callbacks = TestCallbacks(modifierStateController)

        val ctrlDownResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
            event = keyDown(KeyEvent.KEYCODE_CTRL_LEFT),
            callbacks = callbacks
        )
        assertTrue(ctrlDownResult is InputEventRouter.EditableFieldRoutingResult.CallSuper)
        assertTrue(modifierStateController.ctrlOneShot)
        assertTrue(modifierStateController.ctrlPressed)

        modifierStateController.handleCtrlKeyUp(KeyEvent.KEYCODE_CTRL_LEFT)
        assertTrue(modifierStateController.ctrlOneShot)
        assertFalse(modifierStateController.ctrlPressed)
        assertFalse(modifierStateController.ctrlPhysicallyPressed)

        val aResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )
        assertTrue(aResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(1, callbacks.clearCtrlOneShotCalls)
        assertFalse(modifierStateController.ctrlOneShot)
    }

    @Test
    fun holdCtrlThenAThenRelease_routerPathBypassesOneShotConsumeButKeepsPressedCleanup() {
        val callbacks = TestCallbacks(modifierStateController)

        val ctrlDownResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
            event = keyDown(KeyEvent.KEYCODE_CTRL_LEFT),
            callbacks = callbacks
        )
        assertTrue(ctrlDownResult is InputEventRouter.EditableFieldRoutingResult.CallSuper)
        assertTrue(modifierStateController.ctrlPressed)
        assertTrue(modifierStateController.ctrlOneShot)

        val aWhileHeldResult = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A, metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
            callbacks = callbacks
        )
        assertTrue(aWhileHeldResult is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals(0, callbacks.clearCtrlOneShotCalls)
        assertTrue(inputConnectionRecorder.sentKeyEvents.isNotEmpty())

        modifierStateController.handleCtrlKeyUp(KeyEvent.KEYCODE_CTRL_LEFT)
        assertFalse(modifierStateController.ctrlPressed)
        assertFalse(modifierStateController.ctrlPhysicallyPressed)

        // Router-level path does not perform the service's release cleanup for stale one-shot state.
        // The service-level E2E test covers the actual runtime regression/fix.
        assertFalse(modifierStateController.ctrlLatchActive)
    }

    @Test
    fun symEmojiPage_defaultLayout_mapsA_andConsumes() {
        val callbacks = TestCallbacks(modifierStateController)
        if (!altSymManager.getSymMappings().containsKey(KeyEvent.KEYCODE_A)) {
            SettingsManager.saveSymMappings(context, mapOf(KeyEvent.KEYCODE_A to "😢"))
            altSymManager.reloadSymMappings()
        }
        symLayoutController.toggleSymPage() // opens first text page (emoji in default config)
        assertTrue(symLayoutController.isSymActive())

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(inputConnectionRecorder.committedTexts.isNotEmpty())
        assertTrue(
            "commits=${inputConnectionRecorder.committedTexts}",
            inputConnectionRecorder.committedTexts.contains("😢")
        )
    }

    @Test
    fun symSymbolsPage_defaultLayout_mapsA_andConsumes() {
        val callbacks = TestCallbacks(modifierStateController)
        if (!altSymManager.getSymMappings2().containsKey(KeyEvent.KEYCODE_A)) {
            SettingsManager.saveSymMappingsPage2(context, mapOf(KeyEvent.KEYCODE_A to "="))
            altSymManager.reloadSymMappings2()
        }
        symLayoutController.openSymbolsPage()
        assertTrue(symLayoutController.isSymActive())

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertTrue(inputConnectionRecorder.committedTexts.isNotEmpty())
        assertTrue(
            "commits=${inputConnectionRecorder.committedTexts}",
            inputConnectionRecorder.committedTexts.contains("=")
        )
    }

    @Test
    fun symEmojiPage_customMapping_isUsed() {
        val callbacks = TestCallbacks(modifierStateController)
        SettingsManager.saveSymMappings(context, mapOf(KeyEvent.KEYCODE_A to "🧪"))
        altSymManager.reloadSymMappings()
        symLayoutController.toggleSymPage()

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("🧪", inputConnectionRecorder.committedTexts.last())
    }

    @Test
    fun symSymbolsPage_customMapping_isUsed() {
        val callbacks = TestCallbacks(modifierStateController)
        SettingsManager.saveSymMappingsPage2(context, mapOf(KeyEvent.KEYCODE_A to "#"))
        altSymManager.reloadSymMappings2()
        symLayoutController.openSymbolsPage()

        val result = routeKeyDown(
            keyCode = KeyEvent.KEYCODE_A,
            event = keyDown(KeyEvent.KEYCODE_A),
            callbacks = callbacks
        )

        assertTrue(result is InputEventRouter.EditableFieldRoutingResult.Consume)
        assertEquals("#", inputConnectionRecorder.committedTexts.last())
    }

    private fun routeKeyDown(
        keyCode: Int,
        event: KeyEvent,
        callbacks: TestCallbacks
    ): InputEventRouter.EditableFieldRoutingResult {
        return router.routeEditableFieldKeyDown(
            keyCode = keyCode,
            event = event,
            params = buildParams(),
            controllers = InputEventRouter.EditableFieldKeyDownControllers(
                modifierStateController = modifierStateController,
                symLayoutController = symLayoutController,
                altSymManager = altSymManager,
                variationStateController = variationStateController
            ),
            callbacks = callbacks.asRouterCallbacks()
        )
    }

    private fun buildParams(): InputEventRouter.EditableFieldKeyDownHandlingParams {
        return InputEventRouter.EditableFieldKeyDownHandlingParams(
            inputConnection = inputConnection,
            isNumericField = false,
            isInputViewActive = true,
            shiftPressed = modifierStateController.shiftPressed,
            ctrlPressed = modifierStateController.ctrlPressed,
            altPressed = modifierStateController.altPressed,
            ctrlLatchActive = modifierStateController.ctrlLatchActive,
            altLatchActive = modifierStateController.altLatchActive,
            ctrlLatchFromNavMode = modifierStateController.ctrlLatchFromNavMode,
            ctrlKeyMap = ctrlKeyMap,
            ctrlOneShot = modifierStateController.ctrlOneShot,
            altOneShot = modifierStateController.altOneShot,
            clearAltOnSpaceEnabled = false,
            shiftOneShot = modifierStateController.shiftOneShot,
            capsLockEnabled = modifierStateController.capsLockEnabled,
            cursorUpdateDelayMs = 0L
        )
    }

    private fun keyDown(keyCode: Int, metaState: Int = 0): KeyEvent {
        return KeyEvent(
            0L,
            System.currentTimeMillis(),
            KeyEvent.ACTION_DOWN,
            keyCode,
            0,
            metaState
        )
    }

    private class TestCallbacks(
        private val modifierStateController: ModifierStateController
    ) {
        var updateStatusBarCalls = 0
        var refreshStatusBarCalls = 0
        var clearAltOneShotCalls = 0
        var clearCtrlOneShotCalls = 0

        fun asRouterCallbacks(): InputEventRouter.EditableFieldKeyDownHandlingCallbacks {
            return InputEventRouter.EditableFieldKeyDownHandlingCallbacks(
                updateStatusBar = { updateStatusBarCalls++ },
                refreshStatusBar = { refreshStatusBarCalls++ },
                disableShiftOneShot = { modifierStateController.shiftOneShot = false },
                clearAltOneShot = {
                    clearAltOneShotCalls++
                    modifierStateController.altOneShot = false
                },
                clearCtrlOneShot = {
                    clearCtrlOneShotCalls++
                    modifierStateController.ctrlOneShot = false
                },
                getCharacterFromLayout = { _, _, _ -> null },
                isAlphabeticKey = { key -> key in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z },
                callSuper = { false },
                callSuperWithKey = { _, _ -> false },
                startSpeechRecognition = { },
                getMapping = { null },
                handleMultiTapCommit = { _, _, _, _, _ -> false },
                isLongPressSuppressed = { false },
                toggleMinimalUi = { }
            )
        }
    }

    private class RecordingInputConnection {
        var commitTextCalls = 0
        val committedTexts = mutableListOf<String>()
        val sentKeyEvents = mutableListOf<KeyEvent>()
        val contextMenuActions = mutableListOf<Int>()

        fun asProxy(): InputConnection {
            return Proxy.newProxyInstance(
                InputConnection::class.java.classLoader,
                arrayOf(InputConnection::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "commitText" -> {
                        commitTextCalls++
                        val text = args?.getOrNull(0)?.toString()
                        if (text != null) {
                            committedTexts += text
                        }
                        true
                    }
                    "sendKeyEvent" -> {
                        val event = args?.getOrNull(0) as? KeyEvent
                        if (event != null) {
                            sentKeyEvents += event
                        }
                        true
                    }
                    "performContextMenuAction" -> {
                        val id = args?.getOrNull(0) as? Int
                        if (id != null) {
                            contextMenuActions += id
                        }
                        true
                    }
                    "getTextBeforeCursor" -> ""
                    "getExtractedText" -> ExtractedText().apply {
                        selectionStart = 0
                        selectionEnd = 0
                    }
                    else -> defaultValue(method.returnType)
                }
            } as InputConnection
        }

        private fun defaultValue(type: Class<*>): Any? {
            return when {
                type == Boolean::class.javaPrimitiveType -> false
                type == Int::class.javaPrimitiveType -> 0
                type == Long::class.javaPrimitiveType -> 0L
                type == Float::class.javaPrimitiveType -> 0f
                type == Double::class.javaPrimitiveType -> 0.0
                type == Short::class.javaPrimitiveType -> 0.toShort()
                type == Byte::class.javaPrimitiveType -> 0.toByte()
                type == Char::class.javaPrimitiveType -> 0.toChar()
                else -> null
            }
        }
    }
}
