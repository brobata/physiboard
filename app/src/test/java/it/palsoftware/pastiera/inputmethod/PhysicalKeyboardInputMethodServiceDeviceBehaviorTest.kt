package it.palsoftware.pastiera.inputmethod

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.SymPagesConfig
import it.palsoftware.pastiera.core.InputContextState
import it.palsoftware.pastiera.core.ModifierStateController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import java.lang.reflect.Field
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhysicalKeyboardInputMethodServiceDeviceBehaviorTest {

    private lateinit var service: PhysicalKeyboardInputMethodService
    private lateinit var recorder: RecordingInputConnection
    private lateinit var inputConnection: InputConnection
    private lateinit var editorInfo: EditorInfo

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setSymPagesConfig(context, SymPagesConfig())
        SettingsManager.resetSymMappings(context)
        SettingsManager.resetSymMappingsPage2(context)
        SettingsManager.setStaticVariationBarLayerStickyEnabled(context, true)

        service = Robolectric.buildService(PhysicalKeyboardInputMethodService::class.java)
            .create()
            .get()

        recorder = RecordingInputConnection()
        inputConnection = recorder.asProxy()
        editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            packageName = "it.palsoftware.pastiera.test"
        }

        // Inject active editable-field context so onKeyDown/onKeyUp run the same path as on device.
        setField(service, "mInputConnection", inputConnection)
        setField(service, "mStartedInputConnection", inputConnection)
        setField(service, "mInputEditorInfo", editorInfo)
        setField(service, "isInputViewActive", true)
        setField(service, "inputContextState", InputContextState.fromEditorInfo(editorInfo))
    }

    @Test
    fun ctrlHoldShortcutRelease_doesNotLeaveCtrlActive() {
        val t0 = 1_000L

        service.onKeyDown(
            KeyEvent.KEYCODE_CTRL_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, t0, t0)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_A,
                downTime = t0,
                eventTime = t0 + 120L,
                metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_A,
            keyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_A,
                downTime = t0,
                eventTime = t0 + 150L,
                metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_CTRL_LEFT,
            keyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_CTRL_LEFT,
                downTime = t0,
                eventTime = t0 + 420L
            )
        )

        val modifier = modifierController()
        assertFalse(modifier.ctrlPressed)
        assertFalse(modifier.ctrlPhysicallyPressed)
        assertFalse(modifier.ctrlOneShot)
        assertFalse(modifier.ctrlLatchActive)
    }

    @Test
    fun altDoubleTapLatch_thenSingleTapFullyDeactivates() {
        val t0 = 2_000L

        tapAlt(t0)          // one-shot
        tapAlt(t0 + 120L)   // latch (+ visual altLayerLatched on quick double tap)
        tapAlt(t0 + 240L)   // user expects off; device reports still active

        val modifier = modifierController()
        val altLayerLatched = getField<Boolean>(service, "altLayerLatched")

        assertFalse(modifier.altOneShot)
        assertFalse(modifier.altLatchActive)
        assertFalse(altLayerLatched)
    }

    @Test
    fun shiftDoubleTapLatch_thenSingleTapFullyDeactivates() {
        val t0 = 2_500L

        tapShift(t0)          // one-shot
        tapShift(t0 + 120L)   // latch (+ visual shiftLayerLatched on quick double tap)
        tapShift(t0 + 240L)   // user expects off

        val modifier = modifierController()
        val shiftLayerLatched = getField<Boolean>(service, "shiftLayerLatched")

        assertFalse(modifier.shiftOneShot)
        assertFalse(modifier.capsLockEnabled)
        assertFalse(shiftLayerLatched)
    }

    @Test
    fun deviceSanity_ctrlLatch_canBeDisabledByAnotherCtrlTap() {
        val t0 = 3_000L
        tapCtrl(t0)
        tapCtrl(t0 + 120L) // latch
        tapCtrl(t0 + 260L) // toggle off

        val modifier = modifierController()
        assertFalse(modifier.ctrlOneShot)
        assertFalse(modifier.ctrlLatchActive)
    }

    @Test
    fun deviceSanity_ctrlStickyThenA_triggersShortcutPath() {
        val t0 = 3_600L
        tapCtrl(t0) // one-shot

        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, t0 + 60L, t0 + 60L)
        )

        assertTrue(
            recorder.contextMenuActions.contains(android.R.id.selectAll) ||
                recorder.sentKeyEvents.isNotEmpty()
        )
    }

    @Test
    fun deviceSanity_altHoldThenA_doesNotStickAlt() {
        val t0 = 3_800L
        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, t0, t0)
        )
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_A,
                downTime = t0,
                eventTime = t0 + 80L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_A,
            keyEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_A,
                downTime = t0,
                eventTime = t0 + 110L,
                metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            )
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT, t0, t0 + 300L)
        )

        val modifier = modifierController()
        assertFalse(modifier.altPressed)
        assertFalse(modifier.altPhysicallyPressed)
        assertFalse(modifier.altLatchActive)
    }

    @Test
    fun deviceSanity_symATogglesEmojiThenSymbols_exactMappings() {
        val t0 = 4_000L

        tapSym(t0)
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, t0 + 20L, t0 + 20L)
        )
        assertTrue("commits=${recorder.committedTexts}", recorder.committedTexts.contains("😢"))

        tapSym(t0 + 200L) // re-open SYM (emoji page)
        tapSym(t0 + 260L) // next page -> symbols
        service.onKeyDown(
            KeyEvent.KEYCODE_A,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A, t0 + 300L, t0 + 300L)
        )
        assertTrue("commits=${recorder.committedTexts}", recorder.committedTexts.contains("="))
    }

    private fun tapAlt(start: Long) {
        service.onKeyDown(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ALT_LEFT, start, start)
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_ALT_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ALT_LEFT, start, start + 30L)
        )
    }

    private fun tapShift(start: Long) {
        service.onKeyDown(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, start, start)
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, start, start + 30L)
        )
    }

    private fun tapCtrl(start: Long) {
        service.onKeyDown(
            KeyEvent.KEYCODE_CTRL_LEFT,
            keyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, start, start)
        )
        service.onKeyUp(
            KeyEvent.KEYCODE_CTRL_LEFT,
            keyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, start, start + 30L)
        )
    }

    private fun tapSym(start: Long) {
        service.onKeyDown(
            63,
            keyEvent(KeyEvent.ACTION_DOWN, 63, start, start)
        )
        service.onKeyUp(
            63,
            keyEvent(KeyEvent.ACTION_UP, 63, start, start + 20L)
        )
    }

    private fun keyEvent(
        action: Int,
        keyCode: Int,
        downTime: Long,
        eventTime: Long,
        metaState: Int = 0
    ): KeyEvent {
        return KeyEvent(downTime, eventTime, action, keyCode, 0, metaState)
    }

    private fun modifierController(): ModifierStateController {
        return getField(service, "modifierStateController")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getField(target: Any, fieldName: String): T {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val field = cls.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target) as T
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        error("Field not found: $fieldName")
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val field: Field = cls.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        error("Field not found: $fieldName")
    }

    private class RecordingInputConnection {
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
                        val text = args?.getOrNull(0)?.toString()
                        if (text != null) committedTexts += text
                        true
                    }
                    "sendKeyEvent" -> {
                        val event = args?.getOrNull(0) as? KeyEvent
                        if (event != null) sentKeyEvents += event
                        true
                    }
                    "performContextMenuAction" -> {
                        val id = args?.getOrNull(0) as? Int
                        if (id != null) contextMenuActions += id
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
