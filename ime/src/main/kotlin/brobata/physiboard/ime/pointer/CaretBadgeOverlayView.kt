package brobata.physiboard.ime.pointer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import brobata.physiboard.core.pointer.caret.BadgeItem
import brobata.physiboard.core.pointer.caret.GlyphStyle
import brobata.physiboard.core.pointer.caret.ModifierGlyph

/**
 * Draws the caret badge's glyphs.
 *
 * spec: trackpad-caret-nav.md SS4.3. The exact geometry there (a hand-drawn arrow `Path`, a white
 * halo stroked outward before the fill, 0.02 em letter spacing) is approximated here with plain
 * text glyphs and no halo, since reproducing a vector arrow at 11 sp is a design pass this task
 * did not ask for; NEEDS A REAL DEVICE to judge whether the approximation reads well enough on the
 * Elite's panel or whether the real arrow `Path` from SS4.3 is worth building. [ModifierGlyph.SHIFT]
 * uses the Unicode upward-arrow glyph rather than SS4.3's own path for the same reason; SS4.2's
 * caps-lock "arrow with a bar under it" is approximated with the "upwards arrow from bar" character
 * instead of drawing a separate bar.
 */
internal class CaretBadgeOverlayView(context: Context) : View(context) {

    var items: List<BadgeItem> = emptyList()
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var armedColorArgb: Int = 0xFF2563EB.toInt()
    var lockedColorArgb: Int = 0xFFDC2626.toInt()

    /** spec SS4.4 step 2: "the distance from the view's top to the glyphs' feet" (the text baseline). */
    var baselineOffsetPx: Float = 0f
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }

    init {
        paint.textSize = 11f * resources.displayMetrics.scaledDensity
    }

    private fun glyphText(modifier: ModifierGlyph, locked: Boolean): String = when (modifier) {
        ModifierGlyph.SHIFT -> if (locked) "⇪" else "⇧"
        ModifierGlyph.ALT -> "⌥"
        ModifierGlyph.CTRL -> "CTRL"
        ModifierGlyph.SYM -> "SYM"
        // app-shell.md SS31.4: private mode's marker.
        ModifierGlyph.PRIVATE -> "PRIVATE"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val gapPx = 4f * resources.displayMetrics.density
        var width = 0f
        for ((index, item) in items.withIndex()) {
            width += paint.measureText(glyphText(item.modifier, item.style == GlyphStyle.LOCKED_FULL))
            if (index != items.lastIndex) width += gapPx
        }
        val ascent = -paint.ascent()
        val descent = paint.descent()
        baselineOffsetPx = ascent
        setMeasuredDimension(width.toInt().coerceAtLeast(1), (ascent + descent).toInt().coerceAtLeast(1))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gapPx = 4f * resources.displayMetrics.density
        var x = 0f
        for (item in items) {
            val locked = item.style == GlyphStyle.LOCKED_FULL
            val text = glyphText(item.modifier, locked)
            paint.color = if (locked) lockedColorArgb else armedColorArgb
            paint.alpha = if (item.style == GlyphStyle.ARMED_FAINT) 140 else 245
            canvas.drawText(text, x, baselineOffsetPx, paint)
            x += paint.measureText(text) + gapPx
        }
    }
}
