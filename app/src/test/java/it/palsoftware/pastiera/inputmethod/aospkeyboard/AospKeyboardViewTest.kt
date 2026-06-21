package it.palsoftware.pastiera.inputmethod.aospkeyboard

import android.graphics.RectF
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AospKeyboardViewTest {

    private class RecordingListener : AospKeyboardView.Listener {
        val texts = mutableListOf<String>()
        var shiftCount = 0
        var symbolCount = 0
        var languageSwitchCount = 0
        var cursorDelta = 0
        var backspaceCount = 0
        var enterCount = 0
        val soundKeyCodes = mutableListOf<Int>()
        val symbolLongPressKeyCodes = mutableListOf<Int>()
        val symbolTexts = mutableListOf<String>()

        override fun onText(text: String) { texts += text }
        override fun onBackspace() { backspaceCount++ }
        override fun onEnter() { enterCount++ }
        override fun onShift() { shiftCount++ }
        override fun onSymbols() { symbolCount++ }
        override fun onCtrl() = Unit
        override fun onLanguageSwitch() { languageSwitchCount++ }
        override fun onCursorMove(delta: Int) { cursorDelta += delta }
        override fun onKeyPressSound(keyCode: Int) { soundKeyCodes += keyCode }
        override fun onSymbolLongPress(keyCode: Int): Boolean {
            symbolLongPressKeyCodes += keyCode
            return true
        }
        override fun onSymbolText(text: String): Boolean {
            symbolTexts += text
            return true
        }
    }

    @Test
    fun germanMultitapQwertzCompact_normalizesToQwertzSoftwareGeometry() {
        val view = measuredKeyboard().apply {
            layoutName = "german_multitap_qwertz"
        }

        val labels = labels(view)

        assertTrue(!labels.contains("ä"))
        assertTrue(!labels.contains("ö"))
        assertTrue(!labels.contains("ü"))
        assertTrue(labels.containsAll(listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p")))
    }

    @Test
    fun germanMultitapQwertzExtendedIso_rendersVisibleUmlautKeys() {
        val view = measuredKeyboard().apply {
            layoutName = "german_multitap_qwertz"
            layoutStyle = AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO
        }

        val labels = labels(view)

        assertTrue(labels.containsAll(listOf("ä", "ö", "ü")))
        assertTrue(labels.containsAll(listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p")))
    }

    @Test
    fun longPressDefaultSelection_survivesMoveOutsidePopup_andCommitsFirstAlternateOnRelease() {
        val listener = RecordingListener()
        val view = measuredKeyboard().apply {
            layoutName = "german_multitap_qwertz"
            longPressTimeoutMs = 50L
            longPressAlternatesProvider = { output -> if (output == "u") listOf("ü") else emptyList() }
            this.listener = listener
        }
        val (x, y) = centerOfLabel(view, "u")

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y, 0L))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_MOVE, x, y, 70L))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, y, 80L))

        assertEquals(listOf("ü"), listener.texts)
    }

    @Test
    fun touchDown_emitsKeyPressSoundImmediately() {
        val listener = RecordingListener()
        val view = measuredKeyboard().apply {
            this.listener = listener
        }
        val (x, y) = centerOfLabel(view, "a")

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y, 0L))

        assertEquals(listOf(KeyEvent.KEYCODE_A), listener.soundKeyCodes)
    }

    @Test
    fun shiftKeyTap_notifiesListener() {
        val listener = RecordingListener()
        val view = measuredKeyboard().apply {
            this.listener = listener
        }
        val (x, y) = centerOfLabel(view, "⇧")

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y, 0L))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, y, 20L))

        assertEquals(1, listener.shiftCount)
    }

    @Test
    fun symPageShiftLongPress_doesNotOpenSymbolPicker() {
        val listener = RecordingListener()
        val view = measuredKeyboard().apply {
            symPageActive = true
            longPressTimeoutMs = 50L
            this.listener = listener
        }
        val (x, y) = centerOfLabel(view, "⇧")

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y, 0L))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, y, 70L))

        assertTrue(listener.symbolLongPressKeyCodes.isEmpty())
        assertEquals(1, listener.shiftCount)
    }

    @Test
    fun symPageCharacterLongPress_opensSymbolPicker() {
        val listener = RecordingListener()
        val view = measuredKeyboard().apply {
            symPageActive = true
            longPressTimeoutMs = 50L
            this.listener = listener
        }
        val (x, y) = centerOfLabel(view, "a")

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y, 0L))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(60, TimeUnit.MILLISECONDS)
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, y, 70L))

        assertEquals(listOf(KeyEvent.KEYCODE_A), listener.symbolLongPressKeyCodes)
        assertEquals(emptyList<String>(), listener.texts)
    }

    @Test
    fun symPageTextLabels_commitExactVirtualSymbolText() {
        val listener = RecordingListener()
        val view = measuredKeyboard().apply {
            layoutName = "german_multitap_qwertz"
            layoutStyle = AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO
            symPageActive = true
            symPageTextLabels = mapOf("ü" to "🙃")
            this.listener = listener
        }
        val (x, y) = centerOfLabel(view, "ü")

        view.dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN, x, y, 0L))
        view.dispatchTouchEvent(motion(MotionEvent.ACTION_UP, x, y, 20L))

        assertEquals(listOf("🙃"), listener.symbolTexts)
        assertEquals(emptyList<String>(), listener.texts)
    }

    @Test
    fun ctrlPreviewActive_hidesSymPageTextLabels() {
        val view = measuredKeyboard().apply {
            layoutName = "german_multitap_qwertz"
            layoutStyle = AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO
            symPageActive = true
            symPageLabels = mapOf(KeyEvent.KEYCODE_U to "❤️")
            symPageTextLabels = mapOf("ü" to "🙃")
            ctrlPreviewActive = true
            ctrlPreviewLabels = mapOf(KeyEvent.KEYCODE_U to "OK")
        }
        val uKey = keyForLabel(view, "u")
        val umlautKey = keyForLabel(view, "ü")

        assertEquals(null, symPageLabel(view, uKey))
        assertEquals("OK", previewLabel(view, uKey))
        assertEquals(null, symPageLabel(view, umlautKey))
        assertEquals(null, previewLabel(view, umlautKey))
    }

    @Test
    fun displayHint_usesCurrentLongPressProvider_evenWhenShiftedHintMatchesLabel() {
        val view = measuredKeyboard().apply {
            shifted = true
            longPressAlternatesProvider = { output -> if (output == "e") listOf("E") else emptyList() }
        }
        val key = keyForLabel(view, "e")

        assertEquals("E", displayHint(view, key))
    }

    @Test
    fun displayHint_usesAltProviderInsteadOfStaticNumberRow() {
        val view = measuredKeyboard().apply {
            longPressAlternatesProvider = { output -> if (output == "u") listOf("-") else emptyList() }
        }
        val key = keyForLabel(view, "u")

        assertEquals("-", displayHint(view, key))
    }

    private fun measuredKeyboard(): AospKeyboardView {
        val context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        val view = AospKeyboardView(context)
        parent.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, 1000, 240)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 1000, 240)
        return view
    }

    private fun motion(action: Int, x: Float, y: Float, offsetMs: Long): MotionEvent =
        MotionEvent.obtain(0L, offsetMs, action, x, y, 0)

    private fun labels(view: AospKeyboardView): List<String> = keys(view).map { key ->
        field<Any>(key, "spec").let { spec -> field<String>(spec, "label") }
    }

    private fun centerOfLabel(view: AospKeyboardView, label: String): Pair<Float, Float> {
        val hitRect = field<RectF>(keyForLabel(view, label), "hitRect")
        return hitRect.centerX() to hitRect.centerY()
    }

    private fun keyForLabel(view: AospKeyboardView, label: String): Any = keys(view).first { key ->
        val spec = field<Any>(key, "spec")
        field<String>(spec, "label") == label
    }

    private fun keys(view: AospKeyboardView): List<Any> = field(view, "keys")

    private fun displayHint(view: AospKeyboardView, key: Any): String {
        val method = AospKeyboardView::class.java.getDeclaredMethod("displayHint", key.javaClass)
        method.isAccessible = true
        return method.invoke(view, key) as String
    }

    private fun symPageLabel(view: AospKeyboardView, key: Any): String? {
        val method = AospKeyboardView::class.java.getDeclaredMethod("symPageLabelFor", key.javaClass)
        method.isAccessible = true
        return method.invoke(view, key) as String?
    }

    private fun previewLabel(view: AospKeyboardView, key: Any): String? {
        val method = AospKeyboardView::class.java.getDeclaredMethod("previewLabelFor", key.javaClass)
        method.isAccessible = true
        return method.invoke(view, key) as String?
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T {
        val declaredField = target.javaClass.getDeclaredField(name)
        declaredField.isAccessible = true
        return declaredField.get(target) as T
    }
}
