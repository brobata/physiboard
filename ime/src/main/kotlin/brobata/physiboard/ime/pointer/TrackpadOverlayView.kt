package brobata.physiboard.ime.pointer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import brobata.physiboard.core.pointer.trackpad.CursorStep
import brobata.physiboard.core.pointer.trackpad.TouchPhase
import brobata.physiboard.core.pointer.trackpad.TouchSample
import brobata.physiboard.core.pointer.trackpad.TrackpadAccumulator
import brobata.physiboard.core.pointer.trackpad.TrackpadGesture
import brobata.physiboard.core.pointer.trackpad.TrackpadGestureSettings
import brobata.physiboard.design.DesignTokens
import brobata.physiboard.design.PhysiFonts
import brobata.physiboard.ime.skin.PanelSkin

/**
 * The full-screen touch surface of the screen trackpad overlay.
 *
 * spec: trackpad-caret-nav.md SS2.4 (the window's own properties are [TrackpadOverlayController]'s
 * job, not this view's), SS2.5 (the hint pill), SS2.6 (finger movement). All the movement maths
 * lives in `:core:pointer`'s [TrackpadGesture]; this view only turns a real [MotionEvent] into a
 * [TouchSample] and forwards whatever [CursorStep]s come back. The feature is single-finger (SS2
 * never mentions a second pointer), so only the event's own primary pointer coordinates are read.
 */
internal class TrackpadOverlayView(
    context: Context,
    private val settings: TrackpadGestureSettings,
    private val isShiftActive: () -> Boolean,
    private val onSteps: (List<CursorStep>) -> Unit,
    private val onPillTapped: () -> Unit,
) : View(context) {

    /** spec SS2.5: chosen when the overlay opens and re-chosen on every finger down. */
    var hintText: String? = null
        set(value) {
            field = value
            invalidate()
        }

    private var accumulator = TrackpadAccumulator()
    private var previousSample: TouchSample? = null
    private var pillBounds: RectF? = null

    /**
     * The hint pill wears the design system (docs/design/design-system.md, "Panels"): a pane in the
     * system's light or dark scheme, a hairline in the accent, the words in mono.
     */
    private val scheme = PanelSkin.scheme(context)
    private val pillBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, Color.red(scheme.pane), Color.green(scheme.pane), Color.blue(scheme.pane))
    }
    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = scheme.accent
        strokeWidth = DesignTokens.BORDER_DP * resources.displayMetrics.density
    }
    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = scheme.text
        textAlign = Paint.Align.CENTER
        typeface = PhysiFonts.get(context, PhysiFonts.Face.MONO_MEDIUM)
    }

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        setWillNotDraw(false)
        pillTextPaint.textSize = DesignTokens.Type.LABEL_SP * resources.displayMetrics.scaledDensity
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val bounds = pillBounds
            if (bounds != null && bounds.contains(event.x, event.y)) {
                onPillTapped()
                return true
            }
        }
        val phase = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> TouchPhase.DOWN
            MotionEvent.ACTION_MOVE -> TouchPhase.MOVE
            MotionEvent.ACTION_UP -> TouchPhase.UP
            else -> TouchPhase.CANCEL
        }
        val sample = TouchSample(phase, event.x, event.y, event.eventTime, isShiftActive())
        val result = TrackpadGesture.step(accumulator, previousSample, sample, settings)
        accumulator = result.accumulator
        previousSample = sample
        if (result.steps.isNotEmpty()) onSteps(result.steps)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val text = hintText
        if (text == null) {
            pillBounds = null
            return
        }
        val density = resources.displayMetrics.density
        val cx = width / 2f
        val topMarginPx = 56f * density
        val paddingH = DesignTokens.Space.M * density
        val paddingV = DesignTokens.Space.S * density
        val textWidth = pillTextPaint.measureText(text)
        val textHeight = pillTextPaint.descent() - pillTextPaint.ascent()
        val left = cx - textWidth / 2f - paddingH
        val right = cx + textWidth / 2f + paddingH
        val top = topMarginPx
        val bottom = topMarginPx + textHeight + paddingV * 2
        val corner = DesignTokens.Radius.PANE * density
        canvas.drawRoundRect(left, top, right, bottom, corner, corner, pillBackgroundPaint)
        val inset = pillBorderPaint.strokeWidth / 2f
        canvas.drawRoundRect(left + inset, top + inset, right - inset, bottom - inset, corner, corner, pillBorderPaint)
        canvas.drawText(text, cx, bottom - paddingV - pillTextPaint.descent(), pillTextPaint)
        pillBounds = RectF(left, top, right, bottom)
    }
}
