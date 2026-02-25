package it.palsoftware.pastiera.inputmethod

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.layout.TapMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MultiTapControllerTest {

    private lateinit var controller: MultiTapController
    private lateinit var recorder: RecordingInputConnection
    private lateinit var inputConnection: InputConnection

    private val multiTapMapping = LayoutMapping(
        lowercase = "a",
        uppercase = "A",
        multiTapEnabled = true,
        taps = listOf(
            TapMapping(lowercase = "a", uppercase = "A"),
            TapMapping(lowercase = "ä", uppercase = "Ä")
        )
    )

    @Before
    fun setUp() {
        controller = MultiTapController(
            handler = Handler(Looper.getMainLooper()),
            timeoutMs = 400L
        )
        recorder = RecordingInputConnection()
        inputConnection = recorder.asProxy()
    }

    @Test
    fun `first tap commits first variant and starts cycle`() {
        val result = controller.handleTap(
            keyCode = KeyEvent.KEYCODE_A,
            mapping = multiTapMapping,
            useUppercase = false,
            inputConnection = inputConnection
        )

        assertTrue(result.handled)
        assertEquals("a", result.committedText)
        assertFalse(result.replacedInWindow)
        assertEquals(listOf("a"), recorder.committedTexts)
        assertTrue(controller.state.active)
        assertEquals(KeyEvent.KEYCODE_A, controller.state.lastKeyCode)
        assertEquals(0, controller.state.tapIndex)
    }

    @Test
    fun `second tap within timeout replaces previous character and cycles variant`() {
        controller.handleTap(
            keyCode = KeyEvent.KEYCODE_A,
            mapping = multiTapMapping,
            useUppercase = false,
            inputConnection = inputConnection
        )

        val result = controller.handleTap(
            keyCode = KeyEvent.KEYCODE_A,
            mapping = multiTapMapping,
            useUppercase = false,
            inputConnection = inputConnection
        )

        assertTrue(result.handled)
        assertEquals("ä", result.committedText)
        assertTrue(result.replacedInWindow)
        assertEquals(1, recorder.deleteSurroundingTextCalls)
        assertEquals(listOf(1 to 0), recorder.deleteSurroundingTextArgs)
        assertEquals(listOf("a", "ä"), recorder.committedTexts)
        assertEquals(1, controller.state.tapIndex)
    }

    @Test
    fun `tap after timeout starts new cycle instead of replacing`() {
        controller.handleTap(
            keyCode = KeyEvent.KEYCODE_A,
            mapping = multiTapMapping,
            useUppercase = false,
            inputConnection = inputConnection
        )

        shadowOf(Looper.getMainLooper()).idleFor(450, TimeUnit.MILLISECONDS)

        val result = controller.handleTap(
            keyCode = KeyEvent.KEYCODE_A,
            mapping = multiTapMapping,
            useUppercase = false,
            inputConnection = inputConnection
        )

        assertTrue(result.handled)
        assertEquals("a", result.committedText)
        assertFalse(result.replacedInWindow)
        assertEquals(0, recorder.deleteSurroundingTextCalls)
        assertEquals(listOf("a", "a"), recorder.committedTexts)
        assertEquals(0, controller.state.tapIndex)
    }

    @Test
    fun `non multitap mapping is ignored`() {
        val plainMapping = LayoutMapping(lowercase = "x", uppercase = "X")

        val result = controller.handleTap(
            keyCode = KeyEvent.KEYCODE_X,
            mapping = plainMapping,
            useUppercase = false,
            inputConnection = inputConnection
        )

        assertFalse(result.handled)
        assertEquals(null, result.committedText)
        assertFalse(controller.state.active)
        assertTrue(recorder.committedTexts.isEmpty())
    }

    private class RecordingInputConnection {
        val committedTexts = mutableListOf<String>()
        var deleteSurroundingTextCalls = 0
        val deleteSurroundingTextArgs = mutableListOf<Pair<Int, Int>>()

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
                    "deleteSurroundingText" -> {
                        deleteSurroundingTextCalls++
                        val before = args?.getOrNull(0) as? Int ?: 0
                        val after = args.getOrNull(1) as? Int ?: 0
                        deleteSurroundingTextArgs += before to after
                        true
                    }
                    "beginBatchEdit", "endBatchEdit", "finishComposingText" -> true
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
