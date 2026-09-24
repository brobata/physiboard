package brobata.physiboard.core.strip

/**
 * The strip's pixel numbers for one bar height on one screen. spec: status-bar.md SS2's table:
 * "every dp value below is converted to pixels by multiplication and truncation", and the button
 * is derived from the bar in pixels (bar minus 4 dp, never under 24 dp), which is why the Titan's
 * 56 dp bar gives a 98 px button rather than a 97 px one (T25).
 */
data class StripGeometry(
    val barHeightPx: Int,
    val buttonPx: Int,
    val edgeButtonPx: Int,
    val gapPx: Int,
    /** spec SS2: "Extra inset the edge button adds on its side (button times 0.45)". */
    val edgeExtraInsetPx: Int,
    val slotPaddingHorizontalPx: Int,
    val slotPaddingVerticalPx: Int,
    val edgeCornerPx: Int,
    val buttonCornerPx: Float,
    val slotCornerPx: Float,
    val bottomCornerFallbackPx: Int,
    val borderPx: Int,
    val ledHeightPx: Int,
    val ledTopPaddingPx: Int,
    val ledGapPx: Int,
    val ledCornerPx: Int,
) {
    /**
     * How far the slots are inset on one side. spec SS2's worked example: one edge button on the
     * left is 98 + 5 + 44 = 147 px; two buttons on the right, the outer one an edge button, are
     * 2 times 98 + 5 + 5 + 44 = 250 px (T25). Every button is followed by a gap; the edge button
     * adds its extra width once.
     */
    fun sideInsetPx(buttonCount: Int, edgeButton: Boolean): Int {
        if (buttonCount <= 0) return 0
        return buttonCount * (buttonPx + gapPx) + (if (edgeButton) edgeExtraInsetPx else 0)
    }

    /** spec SS2: the LED row's own height when shown (LED height plus its top padding), so the bar can grow by exactly it. */
    val ledRowHeightPx: Int get() = ledHeightPx + ledTopPaddingPx

    companion object {
        /** spec SS14: "Bar heights 36, 48, 56, 64 dp; default 56" (D4). */
        const val DEFAULT_BAR_HEIGHT_DP: Int = 56
        val BAR_HEIGHT_OPTIONS_DP: List<Int> = listOf(36, 48, 56, 64)

        /** spec SS2: "bar minus 4 dp, never under 24 dp". */
        const val BUTTON_INSET_DP: Int = 4
        const val MIN_BUTTON_DP: Int = 24

        /** spec SS2: edge button 1.45 times the button, corner 0.9 times, extra inset 0.45 times. */
        const val EDGE_WIDTH_FACTOR: Double = 1.45
        const val EDGE_CORNER_FACTOR: Double = 0.9
        const val EDGE_EXTRA_INSET_FACTOR: Double = 0.45
        const val GAP_DP: Int = 3
        const val SLOT_PADDING_DP: Int = 12
        const val SLOT_VERTICAL_PADDING_BASE_DP: Double = 3.0
        const val BOTTOM_CORNER_FALLBACK_DP: Int = 24
        const val BORDER_DP: Int = 1
        const val LED_HEIGHT_DP: Double = 5.5
        const val LED_TOP_PADDING_DP: Double = 1.0
        const val LED_GAP_DP: Double = 1.5
        const val LED_CORNER_DP: Double = 3.0

        /** spec SS2, D1: the Titan 2 Elite is 1080 by 1200 px at density 300, "1 dp = 1.875 px". */
        const val TITAN_PX_PER_DP: Float = 1.875f
        const val TITAN_SCREEN_WIDTH_PX: Int = 1080

        /**
         * spec SS2. [suggestionsHeightScale] is the theme's scale, which on the hardware strip
         * "still drives the slot text size and vertical padding" but never the bar height (SS9.1,
         * SS19); the default hardware theme carries 1.4 (D4). [keyRounding] and [chromeRounding]
         * are the theme's two corner ratios (SS9.1).
         */
        fun forBar(
            barHeightDp: Int = DEFAULT_BAR_HEIGHT_DP,
            pxPerDp: Float = TITAN_PX_PER_DP,
            suggestionsHeightScale: Double = StripTheme.HARDWARE_DEFAULT_SUGGESTIONS_SCALE,
            keyRounding: Double = StripTheme.SLATE_DARK.keyCornerRatio,
            chromeRounding: Double = StripTheme.SLATE_DARK.chromeCornerRatio,
        ): StripGeometry {
            fun dp(value: Double): Int = (value * pxPerDp).toInt()
            val barHeightPx = dp(barHeightDp.toDouble())
            val buttonPx = maxOf(barHeightPx - dp(BUTTON_INSET_DP.toDouble()), dp(MIN_BUTTON_DP.toDouble()))
            return StripGeometry(
                barHeightPx = barHeightPx,
                buttonPx = buttonPx,
                edgeButtonPx = (buttonPx * EDGE_WIDTH_FACTOR).toInt(),
                gapPx = dp(GAP_DP.toDouble()),
                edgeExtraInsetPx = (buttonPx * EDGE_EXTRA_INSET_FACTOR).toInt(),
                slotPaddingHorizontalPx = dp(SLOT_PADDING_DP.toDouble()),
                slotPaddingVerticalPx = dp(SLOT_VERTICAL_PADDING_BASE_DP * suggestionsHeightScale),
                edgeCornerPx = (buttonPx * EDGE_CORNER_FACTOR).toInt(),
                buttonCornerPx = (buttonPx * keyRounding).toFloat(),
                slotCornerPx = (barHeightPx * chromeRounding).toFloat(),
                bottomCornerFallbackPx = dp(BOTTOM_CORNER_FALLBACK_DP.toDouble()),
                borderPx = dp(BORDER_DP.toDouble()),
                ledHeightPx = dp(LED_HEIGHT_DP),
                ledTopPaddingPx = dp(LED_TOP_PADDING_DP),
                ledGapPx = dp(LED_GAP_DP),
                ledCornerPx = dp(LED_CORNER_DP),
            )
        }
    }
}

/**
 * The slot text's auto-size bounds in sp. spec: status-bar.md SS2: maximum "14 sp times scale,
 * clamped 12..20, truncated", minimum "7 sp times scale, clamped 7..12, truncated" (T24). The
 * bounds are sp, not px, because the slot text follows the user's font size; everything else
 * on the strip is dp.
 */
data class SlotTextSizeSp(val minSp: Int, val maxSp: Int) {
    companion object {
        const val MAX_BASE_SP: Double = 14.0
        const val MIN_BASE_SP: Double = 7.0

        fun forScale(suggestionsHeightScale: Double): SlotTextSizeSp = SlotTextSizeSp(
            minSp = (MIN_BASE_SP * suggestionsHeightScale).toInt().coerceIn(7, 12),
            maxSp = (MAX_BASE_SP * suggestionsHeightScale).toInt().coerceIn(12, 20),
        )
    }
}
