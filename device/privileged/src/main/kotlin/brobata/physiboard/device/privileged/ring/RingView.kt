package brobata.physiboard.device.privileged.ring

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import brobata.physiboard.device.titan.RingGeometry

/**
 * Draws the ring: black everywhere (which on this AMOLED panel means off, D3), a blurred glow
 * and a crisp ring at the fitted geometry, breathing with [breath], and up to three app icons
 * lower on the screen when [showIcons] is on. With [clock] set it also draws the clock block:
 * the time, the date and [clockIcons], all dim grey, moved by the clock's burn-in offset.
 *
 * spec: device-backlight-ring.md SS5.7.1 ("Drawing, every frame"), SS5.11.1 (the clock).
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

    /** False on the charging clock with nothing waiting: then there is no ring at all, only the clock. */
    var showRing: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** What the clock shows; null draws no clock. [offsetXDp], [offsetYDp] are the burn-in shift (SS5.11.3). */
    data class ClockFace(val time: String, val date: String, val offsetXDp: Int, val offsetYDp: Int)

    var clock: ClockFace? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Already monochrome (tinted or desaturated by the activity); drawn under the date. */
    var clockIcons: List<Drawable> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(TIME_ALPHA, CLOCK_GREY_LEVEL, CLOCK_GREY_LEVEL, CLOCK_GREY_LEVEL)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, THIN_WEIGHT, false)
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(DATE_ALPHA, CLOCK_GREY_LEVEL, CLOCK_GREY_LEVEL, CLOCK_GREY_LEVEL)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    init {
        // A blur mask filter only renders on a software layer.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        clock?.let { drawClock(canvas, it) }
        val ring = geometry ?: return
        if (!showRing) return
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

    /**
     * The clock block, centred horizontally with its time line at 58 % of the height: well clear
     * of the camera hole's ring in the top-left corner (about the top 15 % of the Titan's square
     * screen, glow included) and of the ring's own icon row at 42 %, which the clock replaces.
     * spec: SS5.11.1.
     */
    private fun drawClock(canvas: Canvas, face: ClockFace) {
        val density = resources.displayMetrics.density
        val centreX = width / 2f + face.offsetXDp * density
        val timeBaseline = height * CLOCK_TIME_BASELINE_FRACTION + face.offsetYDp * density
        timePaint.textSize = TIME_SIZE_DP * density
        canvas.drawText(face.time, centreX, timeBaseline, timePaint)
        datePaint.textSize = DATE_SIZE_DP * density
        val dateBaseline = timeBaseline + DATE_GAP_DP * density
        canvas.drawText(face.date, centreX, dateBaseline, datePaint)
        if (clockIcons.isEmpty()) return
        val size = (CLOCK_ICON_SIZE_DP * density).toInt()
        val gap = (CLOCK_ICON_GAP_DP * density).toInt()
        val total = clockIcons.size * size + (clockIcons.size - 1) * gap
        var left = (centreX - total / 2f).toInt()
        val top = (dateBaseline + CLOCK_ICON_GAP_ABOVE_DP * density).toInt()
        for (icon in clockIcons) {
            icon.setBounds(left, top, left + size, top + size)
            icon.alpha = CLOCK_ICON_ALPHA
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

        /** Greyish white, dim: no colour on the screen but the ring's own. */
        const val CLOCK_GREY_LEVEL = 0xC8
        const val TIME_ALPHA = 170
        const val DATE_ALPHA = 140
        const val CLOCK_ICON_ALPHA = 140
        const val THIN_WEIGHT = 100
        const val TIME_SIZE_DP = 72f
        const val DATE_SIZE_DP = 16f
        const val DATE_GAP_DP = 32f
        const val CLOCK_TIME_BASELINE_FRACTION = 0.58f
        const val CLOCK_ICON_SIZE_DP = 18f
        const val CLOCK_ICON_GAP_DP = 10f
        const val CLOCK_ICON_GAP_ABOVE_DP = 18f
    }
}
