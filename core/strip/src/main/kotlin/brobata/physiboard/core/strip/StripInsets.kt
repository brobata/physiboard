package brobata.physiboard.core.strip

/** A pixel rectangle, left/top inclusive, right/bottom exclusive, as Android's `Rect` is. Kept here so `:core:strip` stays free of `android.*`. */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** spec: status-bar.md SS11: the touchable area is either the platform's "content" band or a rectangle PhysiBoard supplies. */
enum class TouchableArea { CONTENT, REGION }

/**
 * What the app is told about the strip. spec: status-bar.md SS11. [touchable] null means "leave
 * the platform's own answer alone" (T16, "touchable mode unchanged"), which is also what
 * [contentTopPx] equal to the input means.
 */
data class InsetDecision(val contentTopPx: Int, val touchable: TouchableArea?, val touchableRect: PxRect?)

/**
 * The inset policy: "the app is told to make room for exactly the strip's visible band and
 * nothing more". spec: status-bar.md SS11, SS19 ("Required so the strip never steals taps and
 * the app resizes exactly").
 */
object StripInsets {
    /**
     * spec SS11 and T15 to T17. In candidates-only mode the content inset becomes the visible
     * inset, and the touchable area a rectangle "from the content top down to the bottom of the
     * window and across its full width"; "if the window has no width yet or its height is not
     * greater than the content top, the touchable area falls back to 'content'". In 3.0 the input
     * view never shows (no soft keyboard), so [candidatesOnly] is false only while the platform
     * has nothing of ours on screen at all.
     */
    fun decide(
        candidatesOnly: Boolean,
        contentTopPx: Int,
        visibleTopPx: Int,
        windowWidthPx: Int,
        windowHeightPx: Int,
        stripTopPx: Int? = null,
    ): InsetDecision {
        if (!candidatesOnly) return InsetDecision(contentTopPx, touchable = null, touchableRect = null)
        // The platform's visible inset is a snapshot of its own candidates frame, which on the
        // Titan (Android 16) can still read "hidden" while the strip is already drawn, so the
        // app was told to make room for nothing and the strip floated over its text box. When
        // the strip is on screen its own top edge is the truth (spec SS11: "exactly the strip's
        // visible band"); [stripTopPx] is null whenever it is not.
        val newContentTop = stripTopPx ?: visibleTopPx
        if (windowWidthPx <= 0 || windowHeightPx <= newContentTop) {
            return InsetDecision(newContentTop, TouchableArea.CONTENT, touchableRect = null)
        }
        return InsetDecision(newContentTop, TouchableArea.REGION, PxRect(0, newContentTop, windowWidthPx, windowHeightPx))
    }
}
