package brobata.physiboard.inputmethod

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.View

/**
 * Draws the active modifiers as small glyphs beside the text cursor, with no panel behind them.
 *
 * Everything is one colour. The two distinctions that matter are carried by shape and weight
 * instead: a locked modifier gets a bar under it, the way a caps-lock key has always been drawn, and
 * a modifier that is merely being held is drawn faint because it goes away the moment the key comes
 * up. That keeps one click and two clicks apart without needing a second hue.
 *
 * Shift is drawn as a path rather than set as text because Android's shift and caps-lock characters
 * (U+21E7, U+21EA) are hairline outlines: legible in a document, mush at this size over an app.
 *
 * Everything is stroked in a light halo before it is filled. The badge floats over an app whose
 * background could be any colour, and a bare glyph vanishes the moment that colour is close to its
 * own; the halo separates it from whatever is behind without putting a slab there.
 */
class CaretBadgeView(context: Context) : View(context) {

    /**
     * One modifier's report.
     *
     * @param locked stays on until it is turned off, rather than expiring by itself.
     * @param faint only being held down, so it will go away on its own in a moment.
     */
    data class Item(
        val shape: Shape,
        val label: String?,
        val locked: Boolean,
        val faint: Boolean
    )

    enum class Shape {
        ARROW,

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
    private val glyphHeight = sp(11f)
    private val gap = dp(4f)
    private val haloWidth = dp(1.1f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = haloWidth * 2f
        strokeJoin = Paint.Join.ROUND
        color = HALO_COLOR
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT
        textSize = glyphHeight
        letterSpacing = 0.02f
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

    /** Room under the baseline for a lock bar, which hangs below everything else. */
    private val lockBarHeight = glyphHeight * 0.11f
    private val lockBarGap = glyphHeight * 0.14f
    private val lockBarSpace = lockBarGap + lockBarHeight

    private val arrowWidth = glyphHeight * 0.68f
    private val path = Path()

    /**
     * Distance from the view's top edge to the middle of the ink.
     *
     * The view is taller than the glyphs - it carries the font's descent, the lock bar and the
     * halo's overhang - so centring the view on a line of text would leave the glyphs sitting
     * visibly high. The caller centres by this instead.
     */
    val glyphCenterOffset: Float get() = haloWidth * 2f + ascent - glyphHeight / 2f

    private fun glyphPaint(item: Item) =
        if (item.shape == Shape.ALT && altHasGlyph) symbol else text

    private fun haloPaint(item: Item) =
        if (item.shape == Shape.ALT && altHasGlyph) symbolHalo else textHalo

    private fun labelOf(item: Item) =
        if (item.shape == Shape.ALT) {
            if (altHasGlyph) ALT_SYMBOL else ALT_FALLBACK
        } else {
            item.label.orEmpty()
        }

    private fun itemWidth(item: Item): Float =
        if (item.shape == Shape.ARROW) arrowWidth else glyphPaint(item).measureText(labelOf(item))

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
            (ascent + descent + lockBarSpace + pad * 2).toInt().coerceAtLeast(1)
        )
    }

    override fun onDraw(canvas: Canvas) {
        var x = haloWidth * 2f
        val baseline = haloWidth * 2f + ascent
        for (item in items) {
            val width = itemWidth(item)
            val alpha = if (item.faint) FAINT_ALPHA else FULL_ALPHA
            if (item.shape == Shape.ARROW) {
                // Hung from the baseline so the arrow's foot and the words' feet agree.
                drawArrow(canvas, x, baseline - glyphHeight, alpha)
            } else {
                drawGlyph(canvas, item, x, baseline, alpha)
            }
            if (item.locked) drawLockBar(canvas, x, baseline, width, alpha)
            x += width + gap
        }
    }

    private fun drawGlyph(canvas: Canvas, item: Item, x: Float, baseline: Float, alpha: Int) {
        val label = labelOf(item)
        val paint = glyphPaint(item)
        canvas.drawText(label, x, baseline, haloPaint(item))
        paint.color = tint(alpha)
        canvas.drawText(label, x, baseline, paint)
    }

    private fun drawArrow(canvas: Canvas, x: Float, top: Float, alpha: Int) {
        val w = arrowWidth
        val h = glyphHeight
        val headBottom = top + h * 0.46f
        path.reset()
        path.moveTo(x + w / 2f, top)
        path.lineTo(x + w, headBottom)
        path.lineTo(x + w * 0.70f, headBottom)
        path.lineTo(x + w * 0.70f, top + h)
        path.lineTo(x + w * 0.30f, top + h)
        path.lineTo(x + w * 0.30f, headBottom)
        path.lineTo(x, headBottom)
        path.close()
        canvas.drawPath(path, halo)
        fill.color = tint(alpha)
        canvas.drawPath(path, fill)
    }

    /** Underlines a locked modifier, the way a caps-lock key carries a bar under its arrow. */
    private fun drawLockBar(canvas: Canvas, x: Float, baseline: Float, width: Float, alpha: Int) {
        val top = baseline + lockBarGap
        path.reset()
        path.addRect(x, top, x + width, top + lockBarHeight, Path.Direction.CW)
        canvas.drawPath(path, halo)
        fill.color = tint(alpha)
        canvas.drawPath(path, fill)
    }

    private fun tint(alpha: Int) = Color.argb(
        alpha, Color.red(GLYPH_COLOR), Color.green(GLYPH_COLOR), Color.blue(GLYPH_COLOR)
    )

    private fun dp(value: Float) = value * density
    private fun sp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, context.resources.displayMetrics
    )

    private companion object {
        /**
         * One colour for every modifier, and a true mid-grey so that it reads on a dark app and a
         * light one without knowing which it is sitting on. A darker slate looked right on white
         * and disappeared into a dark theme, leaving only the halo behind.
         */
        val GLYPH_COLOR = Color.rgb(0x7C, 0x84, 0x90)

        /**
         * A light halo rather than a dark one: the glyphs are mid-tone, so a pale outline lifts them
         * off a dark app and is simply not noticed against a light one.
         */
        val HALO_COLOR = Color.argb(225, 255, 255, 255)

        const val FULL_ALPHA = 230
        const val FAINT_ALPHA = 115

        /** U+2325 OPTION KEY. */
        const val ALT_SYMBOL = "⌥"
        const val ALT_FALLBACK = "ALT"
    }
}
