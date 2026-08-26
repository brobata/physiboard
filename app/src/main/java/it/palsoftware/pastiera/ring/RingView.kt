package it.palsoftware.pastiera.ring

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View

/**
 * A black screen with a glowing ring around the camera hole and, lower down, the icons of the
 * apps that are waiting. Everything not drawn stays black, which on an AMOLED panel means off.
 */
class RingView(context: Context) : View(context) {

    var ring: RingGeometry.Ring? = null
        set(value) { field = value; invalidate() }

    var color: Int = NotificationRingPolicy.DEFAULT_COLOR
        set(value) { field = value; invalidate() }

    var icons: List<Drawable> = emptyList()
        set(value) { field = value; invalidate() }

    /** 0..1, driven by the breathing animation. */
    var breath: Float = 1f
        set(value) { field = value; invalidate() }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val density = context.resources.displayMetrics.density

    init {
        setBackgroundColor(Color.BLACK)
        // The blur is a software effect; the view is tiny in draw cost either way.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val r = ring ?: return
        val alpha = (0.35f + 0.65f * breath)

        glow.color = color
        glow.alpha = (90 * alpha).toInt()
        glow.strokeWidth = r.stroke * 2.5f
        glow.maskFilter = BlurMaskFilter(r.stroke * 2f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(r.cx, r.cy, r.radius, glow)

        stroke.color = color
        stroke.alpha = (255 * alpha).toInt()
        stroke.strokeWidth = r.stroke
        canvas.drawCircle(r.cx, r.cy, r.radius, stroke)

        if (icons.isEmpty()) return
        val size = (22 * density).toInt()
        val gap = (14 * density).toInt()
        val total = icons.size * size + (icons.size - 1) * gap
        var x = (width - total) / 2
        val y = (height * 0.42f).toInt()
        icons.forEach { icon ->
            icon.setBounds(x, y, x + size, y + size)
            icon.alpha = (200 * alpha).toInt()
            icon.draw(canvas)
            x += size + gap
        }
    }
}
