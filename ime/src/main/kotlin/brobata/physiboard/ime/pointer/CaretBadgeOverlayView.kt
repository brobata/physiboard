package brobata.physiboard.ime.pointer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import brobata.physiboard.core.pointer.caret.BadgeItem
import brobata.physiboard.core.pointer.caret.GlyphStyle
import brobata.physiboard.core.pointer.caret.ModifierGlyph
import brobata.physiboard.design.DesignTokens
import brobata.physiboard.design.PhysiFonts

/**
 * Draws the caret badge's glyphs.
 *
 * spec: trackpad-caret-nav.md SS4.3. The words are JetBrains Mono bold with the white halo stroked
 * outward before the fill; the rest of that geometry (a hand-drawn arrow `Path`, 0.02 em letter
 * spacing) is approximated here with plain text glyphs, since reproducing a vector arrow at 11 sp is a design pass this task
 * did not ask for; NEEDS A REAL DEVICE to judge whether the approximation reads well enough on the
 * Elite's panel or whether the real arrow `Path` from SS4.3 is worth building. [ModifierGlyph.SHIFT]
 * uses the Unicode upward-arrow glyph rather than SS4.3's own path for the same reason; SS4.2's
 * caps-lock "arrow with a bar under it" is approximated with the "upwards arrow from bar" character
 * instead of drawing a separate bar.
 */
internal class CaretBadgeOverlayView(context: Context) : View(context) {

    var items: List<BadgeItem> = emptyList()
        set(value) {
            if (field == value) return
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
        typeface = PhysiFonts.get(context, PhysiFonts.Face.MONO_BOLD)
    }

    /**
     * spec SS4.3's halo: each glyph is first stroked outward in a light colour, so the badge
     * reads over dark and light text fields alike.
     */
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = paint.typeface
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f * 1.1f * resources.displayMetrics.density
        color = 0xFFFFFFFF.toInt()
    }

    init {
        paint.textSize = DesignTokens.Type.BADGE_SP * resources.displayMetrics.scaledDensity
        halo.textSize = paint.textSize
    }

    /** Room on every side for the halo, so it is never clipped by the view's own bounds. */
    private val haloPad: Float get() = halo.strokeWidth / 2f

    private fun glyphText(modifier: ModifierGlyph, locked: Boolean): String = when (modifier) {
        ModifierGlyph.SHIFT -> if (locked) "⇪" else "⇧"
        ModifierGlyph.ALT -> "⌥"
        ModifierGlyph.CTRL -> "CTRL"
        ModifierGlyph.SYM -> "SYM"
        // app-shell.md SS31.4: private mode's marker.
        ModifierGlyph.PRIVATE -> "PRIVATE"
        // layers-sym-alt.md SS4.7: the Fill page has something for this field.
        ModifierGlyph.FILL -> "FILL"
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
        baselineOffsetPx = ascent + haloPad
        setMeasuredDimension((width + 2 * haloPad).toInt().coerceAtLeast(1), (ascent + descent + 2 * haloPad).toInt().coerceAtLeast(1))
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
            halo.alpha = if (item.style == GlyphStyle.ARMED_FAINT) 128 else 225
            canvas.drawText(text, x + haloPad, baselineOffsetPx, halo)
            canvas.drawText(text, x + haloPad, baselineOffsetPx, paint)
            x += paint.measureText(text) + gapPx
        }
    }
}
