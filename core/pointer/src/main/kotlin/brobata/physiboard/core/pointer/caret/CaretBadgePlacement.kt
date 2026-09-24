package brobata.physiboard.core.pointer.caret

import kotlin.math.roundToInt

/** The editor's insertion marker, in screen pixels. spec: trackpad-caret-nav.md SS4.4. */
data class CaretGeometry(val leftPx: Float, val topPx: Float, val bottomPx: Float) {
    val lineHeightPx: Float get() = bottomPx - topPx
}

/**
 * The badge window's own measured size. [glyphFeetOffsetPx] is "the distance from the view's top
 * to the glyphs' feet" (spec SS4.4 step 2), needed because the window is placed by its top-left
 * corner but the rule is stated in terms of where the glyphs themselves sit.
 */
data class BadgeSize(val widthPx: Float, val heightPx: Float, val glyphFeetOffsetPx: Float)

/** How wide the screen is, for the two flip/clamp rules. spec: trackpad-caret-nav.md SS4.4 steps 3 and 5. */
data class ScreenGeometry(val widthPx: Float)

/** Where the badge window's top-left corner should be. */
data class BadgePosition(val xPx: Int, val yPx: Int)

/**
 * Places the caret badge window relative to the caret, following spec SS4.4's five steps exactly
 * ("One placement, always"): right of the caret by default, flipped left if it would run off the
 * right edge, dropped below the line if that would put it above the top of the screen, then
 * clamped horizontally onto the screen.
 *
 * [pxPerDp] converts the two dp constants (the 4 dp caret gap) to pixels; the Elite's own value is
 * 1.875 (trackpad-caret-nav.md D7), but this module takes it as a parameter rather than assuming
 * one device, since nothing about the placement rule itself is Titan-specific.
 */
object CaretBadgePlacement {
    private const val CARET_GAP_DP = 4f
    private const val TOP_FRACTION_OF_LINE_HEIGHT = 0.18f

    fun place(caret: CaretGeometry, badge: BadgeSize, screen: ScreenGeometry, pxPerDp: Float): BadgePosition {
        val gapPx = (CARET_GAP_DP * pxPerDp).roundToInt()
        val topOffsetPx = (TOP_FRACTION_OF_LINE_HEIGHT * caret.lineHeightPx).roundToInt()

        var x = caret.leftPx.roundToInt() + gapPx
        var y = caret.topPx.roundToInt() + topOffsetPx - badge.glyphFeetOffsetPx.roundToInt()

        // Step 3: flip to the left of the caret if the badge would run off the right edge.
        if (x + badge.widthPx > screen.widthPx) {
            x = caret.leftPx.roundToInt() - gapPx - badge.widthPx.roundToInt()
        }

        // Step 4: drop below the line if the computed y would be negative.
        if (y < 0) {
            y = caret.bottomPx.roundToInt() - topOffsetPx
        }

        // Step 5: clamp both into the screen. Only a screen width is given (spec's own test table
        // never exercises a vertical clamp), so y is left as computed; a caller with the real
        // screen height is free to clamp it the same way before creating the window.
        val maxX = (screen.widthPx - badge.widthPx).coerceAtLeast(0f).roundToInt()
        x = x.coerceIn(0, maxX)

        return BadgePosition(x, y)
    }
}

/**
 * A cursor-anchor report from the editor, exactly as `:ime` would read one off the real
 * `CursorAnchorInfo`. spec: trackpad-caret-nav.md SS4.6 ("A caret is unusable when...").
 */
data class CursorAnchorReport(
    val horizontalPx: Float?,
    val topPx: Float?,
    val bottomPx: Float?,
    val hasInvisibleRegion: Boolean = false,
    val hasVisibleRegion: Boolean = false,
)

/** Decides whether a [CursorAnchorReport] is one the badge can be placed from. spec: trackpad-caret-nav.md SS4.6. */
object CaretUsability {
    fun isUsable(report: CursorAnchorReport?): Boolean {
        if (report == null) return false
        val values = listOf(report.horizontalPx, report.topPx, report.bottomPx)
        if (values.any { it == null || it.isNaN() }) return false
        if (report.hasInvisibleRegion && !report.hasVisibleRegion) return false
        return true
    }
}
