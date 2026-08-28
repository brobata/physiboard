package brobata.physiboard.inputmethod

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View

/**
 * Draws the active modifiers as a row of coloured glyphs, with no panel behind them.
 *
 * The colour carries the meaning - blue for one click, red for two, grey for a plain hold - so each
 * modifier is drawn in its own colour rather than the row taking a single style. Holding Shift while
 * Alt is locked genuinely is two different states, and one background could only report one of them.
 *
 * Shift is drawn as a path rather than set as text because Android's shift and caps-lock characters
 * (U+21E7, U+21EA) are hairline outlines: legible in a document, mush at this size over an app.
 *
 * Everything is stroked in a light halo before it is filled. The badge floats over an app whose
 * background could be any colour, and a bare coloured glyph vanishes the moment that colour is close
 * to its own; the halo separates it from whatever is behind without putting a slab there.
 */
class CaretBadgeView(context: Context) : View(context) {

    /** One modifier's report: how to draw it, and the colour that says how long it will last. */
    data class Item(val shape: Shape, val label: String?, val color: Int)

    enum class Shape {
        ARROW,
        ARROW_LOCK,

        /** The option/alt key symbol, with a spelled-out fallback if the font has no glyph for it. */
        ALT,
        TEXT
    }

    var items: List<Item> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    private val density = context.resources.displayMetrics.density
    private val glyphHeight = sp(13f)
    private val gap = dp(4f)
    private val haloWidth = dp(1.6f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = haloWidth * 2f
        strokeJoin = Paint.Join.ROUND
        color = HALO_COLOR
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = glyphHeight
        letterSpacing = 0.04f
    }
    private val textHalo = Paint(text).apply {
        style = Paint.Style.STROKE
        strokeWidth = haloWidth * 2f
        strokeJoin = Paint.Join.ROUND
        color = HALO_COLOR
    }

    // Symbol glyphs are drawn small inside their em box, so they need a larger point size to carry
    // the same visual weight as the words beside them.
    private val symbol = Paint(text).apply { textSize = glyphHeight * 1.3f }
    private val symbolHalo = Paint(textHalo).apply { textSize = glyphHeight * 1.3f }

    /**
     * U+2325 is not guaranteed to be in the system font, and a missing glyph draws as tofu - worse
     * than the word it replaced. Resolved once, here, where the font is actually known.
     */
    private val altHasGlyph = symbol.hasGlyph(ALT_SYMBOL)

    // Everything shares one baseline, so an arrow's foot lines up with the foot of the word beside
    // it. The view is sized from whichever paint reaches highest above that baseline - the symbol is
    // set larger than the words, and sizing to the words alone would shear its top off.
    private val ascent = maxOf(glyphHeight, -text.fontMetrics.ascent, -symbol.fontMetrics.ascent)
    private val descent = maxOf(text.fontMetrics.descent, symbol.fontMetrics.descent)

    private val arrowWidth = glyphHeight * 0.68f
    private val path = Path()

    private fun altPaint() = if (altHasGlyph) symbol else text
    private fun altLabel() = if (altHasGlyph) ALT_SYMBOL else ALT_FALLBACK

    private fun itemWidth(item: Item): Float = when (item.shape) {
        Shape.TEXT -> text.measureText(item.label.orEmpty())
        Shape.ALT -> altPaint().measureText(altLabel())
        else -> arrowWidth
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = 0f
        items.forEachIndexed { index, item ->
            if (index > 0) width += gap
            width += itemWidth(item)
        }
        // The halo is stroked outward from the glyph edge, so the view has to be that much wider
        // and taller or it is clipped off at the bounds.
        val pad = haloWidth * 2f
        setMeasuredDimension(
            (width + pad * 2).toInt().coerceAtLeast(1),
            (ascent + descent + pad * 2).toInt().coerceAtLeast(1)
        )
    }

    override fun onDraw(canvas: Canvas) {
        var x = haloWidth * 2f
        val baseline = haloWidth * 2f + ascent
        for (item in items) {
            when (item.shape) {
                Shape.TEXT ->
                    drawGlyph(canvas, item, x, baseline, item.label.orEmpty(), text, textHalo)
                Shape.ALT -> drawGlyph(
                    canvas, item, x, baseline, altLabel(), altPaint(),
                    if (altHasGlyph) symbolHalo else textHalo
                )
                // Hung from the baseline so the arrow's foot and the words' feet agree.
                else -> drawArrow(
                    canvas, item, x, baseline - glyphHeight,
                    locked = item.shape == Shape.ARROW_LOCK
                )
            }
            x += itemWidth(item) + gap
        }
    }

    private fun drawGlyph(
        canvas: Canvas,
        item: Item,
        x: Float,
        baseline: Float,
        label: String,
        paint: Paint,
        halo: Paint
    ) {
        canvas.drawText(label, x, baseline, halo)
        paint.color = item.color
        canvas.drawText(label, x, baseline, paint)
    }

    private fun drawArrow(canvas: Canvas, item: Item, x: Float, top: Float, locked: Boolean) {
        val w = arrowWidth
        val h = glyphHeight
        // A solid arrowhead over a stem. When locked, the stem is cut short to make room for the
        // bar underneath - the same shape a caps-lock key has carried for forty years.
        val stemBottom = if (locked) top + h * 0.74f else top + h
        val headBottom = top + h * 0.46f
        path.reset()
        path.moveTo(x + w / 2f, top)
        path.lineTo(x + w, headBottom)
        path.lineTo(x + w * 0.70f, headBottom)
        path.lineTo(x + w * 0.70f, stemBottom)
        path.lineTo(x + w * 0.30f, stemBottom)
        path.lineTo(x + w * 0.30f, headBottom)
        path.lineTo(x, headBottom)
        path.close()
        if (locked) {
            path.addRect(x + w * 0.12f, top + h * 0.87f, x + w * 0.88f, top + h, Path.Direction.CW)
        }
        canvas.drawPath(path, halo)
        fill.color = item.color
        canvas.drawPath(path, fill)
    }

    private fun dp(value: Float) = value * density
    private fun sp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics
    )

    private companion object {
        /**
         * A light halo rather than a dark one: the coloured glyphs are mid-tone, so a pale outline
         * lifts them off a dark app and is simply not noticed against a light one.
         */
        val HALO_COLOR = Color.argb(235, 255, 255, 255)

        /** U+2325 OPTION KEY. */
        const val ALT_SYMBOL = "\u2325"
        const val ALT_FALLBACK = "ALT"
    }
}
