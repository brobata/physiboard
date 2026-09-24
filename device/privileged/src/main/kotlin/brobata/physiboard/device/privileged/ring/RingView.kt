package brobata.physiboard.device.privileged.ring

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import brobata.physiboard.device.titan.RingGeometry

/**
 * Draws the ring: black everywhere (which on this AMOLED panel means off, D3), a blurred glow
 * and a crisp ring at the fitted geometry, breathing with [breath], and up to three app icons
 * lower on the screen when [showIcons] is on.
 *
 * spec: device-backlight-ring.md SS5.7.1 ("Drawing, every frame").
 */
class RingView(context: Context) : View(context) {

    var geometry: RingGeometry? = null
        set(value) {
            field = value
            invalidate()
        }

    var colorArgb: Int = Color.GREEN
        set(value) {
            field = value
            invalidate()
        }

    /** 0 to 1 and back, from the activity's animator. */
    var breath: Float = 1f
        set(value) {
            field = value
            invalidate()
        }

    var icons: List<Drawable> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var showIcons: Boolean = false

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        // A blur mask filter only renders on a software layer.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val ring = geometry ?: return
        val alphaFactor = ALPHA_FLOOR + ALPHA_RANGE * breath

        glowPaint.color = colorArgb
        glowPaint.alpha = (GLOW_ALPHA * alphaFactor).toInt()
        glowPaint.strokeWidth = GLOW_STROKE_FACTOR * ring.strokeWidth
        glowPaint.maskFilter = BlurMaskFilter(GLOW_BLUR_FACTOR * ring.strokeWidth, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(ring.centerX, ring.centerY, ring.radius, glowPaint)

        ringPaint.color = colorArgb
        ringPaint.alpha = (RING_ALPHA * alphaFactor).toInt()
        ringPaint.strokeWidth = ring.strokeWidth
        canvas.drawCircle(ring.centerX, ring.centerY, ring.radius, ringPaint)

        if (!showIcons || icons.isEmpty()) return
        val density = resources.displayMetrics.density
        val size = (ICON_SIZE_DP * density).toInt()
        val gap = (ICON_GAP_DP * density).toInt()
        val total = icons.size * size + (icons.size - 1) * gap
        var left = (width - total) / 2
        val top = (height * ICON_TOP_FRACTION).toInt()
        val iconAlpha = (ICON_ALPHA * alphaFactor).toInt()
        for (icon in icons) {
            icon.setBounds(left, top, left + size, top + size)
            icon.alpha = iconAlpha
            icon.draw(canvas)
            left += size + gap
        }
    }

    private companion object {
        /** a = 0.35 + 0.65 b. */
        const val ALPHA_FLOOR = 0.35f
        const val ALPHA_RANGE = 0.65f
        const val GLOW_ALPHA = 90f
        const val RING_ALPHA = 255f
        const val ICON_ALPHA = 200f
        const val GLOW_STROKE_FACTOR = 2.5f
        const val GLOW_BLUR_FACTOR = 2f
        const val ICON_SIZE_DP = 22f
        const val ICON_GAP_DP = 14f
        const val ICON_TOP_FRACTION = 0.42f
    }
}
