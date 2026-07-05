package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AltSymManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs = context.getSharedPreferences("alt_sym_manager_tests", Context.MODE_PRIVATE)
        prefs.edit().clear().putLong("long_press_threshold", 50L).commit()
        SettingsManager.setLongPressModifier(context, "variations")
        SettingsManager.saveVariations(
            context,
            variations = mapOf("u" to listOf("ü"))
        )
    }

    @After
    fun tearDown() {
        SettingsManager.resetVariationsToDefault(context)
        SettingsManager.setLongPressModifier(context, "alt")
    }

    @Test
    fun variationsLongPress_replacesFirstCharacterUsingComposingRegion() {
        val recorder = RecordingInputConnection()
        val inputConnection = recorder.asProxy()
        val manager = AltSymManager(context.assets, prefs, context)

        val consumed = manager.handleKeyWithAltMapping(
            keyCode = KeyEvent.KEYCODE_U,
            inputConnection = inputConnection,
            event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_U),
            capsLockEnabled = false,
            shiftOneShot = false,
            layoutChar = 'u'
        )

        assertTrue(consumed)
        assertEquals("u", recorder.text)

        shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)

        assertEquals("ü", recorder.text)
        assertEquals(listOf(0 to 1), recorder.composingRegions)
        assertEquals(emptyList<Pair<Int, Int>>(), recorder.deleteCalls)
    }

    private class RecordingInputConnection {
        var text: String = ""
        private var selectionStart: Int = 0
        private var selectionEnd: Int = 0
        private var composingStart: Int = -1
        private var composingEnd: Int = -1
        val composingRegions = mutableListOf<Pair<Int, Int>>()
        val deleteCalls = mutableListOf<Pair<Int, Int>>()

        fun asProxy(): InputConnection {
            return Proxy.newProxyInstance(
                InputConnection::class.java.classLoader,
                arrayOf(InputConnection::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "commitText" -> {
                        val committed = args?.getOrNull(0)?.toString().orEmpty()
                        if (composingStart >= 0 && composingEnd >= composingStart) {
                            text = text.replaceRange(composingStart, composingEnd, committed)
                            selectionStart = composingStart + committed.length
                            selectionEnd = selectionStart
                            composingStart = -1
                            composingEnd = -1
                        } else {
                            text = text.substring(0, selectionStart) +
                                committed +
                                text.substring(selectionEnd)
                            selectionStart += committed.length
                            selectionEnd = selectionStart
                        }
                        true
                    }
                    "setComposingRegion" -> {
                        composingStart = args?.getOrNull(0) as Int
                        composingEnd = args.getOrNull(1) as Int
                        composingRegions += composingStart to composingEnd
                        true
                    }
                    "deleteSurroundingText" -> {
                        val before = args?.getOrNull(0) as Int
                        val after = args.getOrNull(1) as Int
                        deleteCalls += before to after
                        true
                    }
                    "finishComposingText" -> {
                        composingStart = -1
                        composingEnd = -1
                        true
                    }
                    "beginBatchEdit", "endBatchEdit" -> true
                    "getExtractedText" -> ExtractedText().apply {
                        this.text = this@RecordingInputConnection.text
                        selectionStart = this@RecordingInputConnection.selectionStart
                        selectionEnd = this@RecordingInputConnection.selectionEnd
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
