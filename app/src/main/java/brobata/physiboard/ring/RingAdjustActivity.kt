package brobata.physiboard.ring

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import brobata.physiboard.R
import brobata.physiboard.SettingsManager

/**
 * Fit the ring to the actual lens. The system only reports the cutout's bounding box, and the
 * hole is smaller than the box and not necessarily centred in it, so the last few pixels are
 * the user's to set: drag the ring, resize it, look at the phone, save.
 *
 * Keyboard-first like the rest of the app: arrows move by a pixel, +/- resize, Enter saves.
 */
class RingAdjustActivity : Activity() {

    private companion object {
        /** Legible on white; the real ring keeps the app's colour. */
        const val ADJUST_COLOR = 0xFFD32F2F.toInt()
    }

    private lateinit var view: RingView
    private var ring: RingGeometry.Ring = RingGeometry.titanFallback(300, 0f, 0f)
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val root = FrameLayout(this)
        view = RingView(this)
        // White, so the lens reads as a dark hole and the ring can be laid over it by eye.
        view.canvasColor = Color.WHITE
        view.color = ADJUST_COLOR
        root.addView(view)

        val density = resources.displayMetrics.density
        val hint = TextView(this).apply {
            setText(R.string.ring_adjust_hint)
            setTextColor(Color.argb(200, 0, 0, 0))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
        }
        root.addView(hint, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER })

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (24 * density).toInt())
        }
        fun button(label: Int, onClick: () -> Unit) = Button(this).apply {
            setText(label)
            setOnClickListener { onClick() }
        }
        bar.addView(button(R.string.ring_adjust_smaller) { resize(-1f) })
        bar.addView(button(R.string.ring_adjust_larger) { resize(1f) })
        bar.addView(button(R.string.ring_adjust_thinner) { thicken(-1f) })
        bar.addView(button(R.string.ring_adjust_thicker) { thicken(1f) })
        bar.addView(button(R.string.ring_adjust_reset) { reset() })
        bar.addView(button(R.string.ring_adjust_done) { save() })
        root.addView(bar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })

        setContentView(root)

        view.setOnApplyWindowInsetsListener { _, insets ->
            val auto = insets.displayCutout?.boundingRects?.firstOrNull()?.let {
                val dpi = resources.displayMetrics.densityDpi
                if (RingGeometry.isTitanCutout(it.left, it.top, it.right, it.bottom, dpi)) {
                    RingGeometry.titanFitted(dpi)
                } else {
                    RingGeometry.aroundCutout(it.left, it.top, it.right, it.bottom, gapPx(), strokePx())
                }
            } ?: RingGeometry.titanFallback(resources.displayMetrics.densityDpi, gapPx(), strokePx())
            ring = SettingsManager.getNotificationRingOverride(this)
                ?.let { if (it.stroke > 0f) it else it.copy(stroke = strokePx()) }
                ?: auto
            view.ring = ring
            insets
        }
        view.setOnTouchListener { _, event -> onDrag(event) }
    }

    private fun gapPx() = 3f * resources.displayMetrics.density
    private fun strokePx() = 3f * resources.displayMetrics.density

    private fun onDrag(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y; dragging = true }
            MotionEvent.ACTION_MOVE -> if (dragging) {
                move(event.x - lastX, event.y - lastY)
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private fun move(dx: Float, dy: Float) {
        ring = ring.copy(cx = ring.cx + dx, cy = ring.cy + dy)
        view.ring = ring
    }

    private fun resize(delta: Float) {
        ring = ring.copy(radius = (ring.radius + delta).coerceAtLeast(ring.stroke))
        view.ring = ring
    }

    private fun thicken(delta: Float) {
        ring = ring.copy(stroke = (ring.stroke + delta).coerceIn(1f, 40f))
        view.ring = ring
    }

    private fun reset() {
        SettingsManager.clearNotificationRingOverride(this)
        view.requestApplyInsets()
    }

    private fun save() {
        SettingsManager.setNotificationRingOverride(this, ring)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> { move(-1f, 0f); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { move(1f, 0f); true }
        KeyEvent.KEYCODE_DPAD_UP -> { move(0f, -1f); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { move(0f, 1f); true }
        KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_NUMPAD_ADD -> { resize(1f); true }
        KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> { resize(-1f); true }
        KeyEvent.KEYCODE_LEFT_BRACKET -> { thicken(-1f); true }
        KeyEvent.KEYCODE_RIGHT_BRACKET -> { thicken(1f); true }
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { save(); true }
        else -> super.onKeyDown(keyCode, event)
    }
}
