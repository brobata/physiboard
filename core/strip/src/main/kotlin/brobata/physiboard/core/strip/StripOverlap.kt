package brobata.physiboard.core.strip

/**
 * Whether the focused field is being drawn underneath the strip.
 *
 * An app that sets `adjust=nothing` never resizes or pans for a keyboard and ignores the insets
 * the IME reports, so it anchors its message box to the bottom of a full-height window -- exactly
 * where a bottom-anchored candidates view already is. Teams is the maintainer's case: the compose
 * box sat under the strip with only its top edge showing (2026-09-29).
 *
 * [StripDip]'s blink cannot fix that. It hides the strip for 200 ms so the app "will lay out clear
 * of it", which needs an app that reads insets at all; this class of app never moves, so the strip
 * returns to the same pixels. The app's own caret is the only honest evidence, and the editor
 * reports it through `onUpdateCursorAnchorInfo`: when the insertion marker is inside the strip's
 * band, the box is under the strip, whatever the app claimed about resizing.
 *
 * Deciding from the caret rather than from a list of package names means an app that changes its
 * mind in an update is handled without anyone editing a list.
 */
object StripOverlap {
    /**
     * True when the caret sits at or below [stripTopPx], so the strip is covering the field.
     *
     * [caretBottomPx] and [stripTopPx] must be in the same space (screen). A caret the editor
     * could not report arrives as NaN and is not evidence of anything, so it answers false, as
     * does a strip that is not on screen to cover anything.
     */
    fun fieldDrawsUnderStrip(caretBottomPx: Float, stripTopPx: Int, stripRendered: Boolean): Boolean {
        if (!stripRendered) return false
        if (caretBottomPx.isNaN() || caretBottomPx.isInfinite()) return false
        return caretBottomPx > stripTopPx
    }
}
