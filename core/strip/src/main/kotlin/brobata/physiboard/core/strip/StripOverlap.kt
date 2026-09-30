package brobata.physiboard.core.strip

/**
 * Whether the app in front draws its text box in the space the strip occupies, so the strip must
 * give that space up for the whole field.
 *
 * An app that sets `adjust=nothing` never resizes or pans for a keyboard and ignores the insets
 * the IME reports, so it anchors its message box to the bottom of a full-height window -- exactly
 * where a bottom-anchored candidates view already is. Teams is the maintainer's case: the compose
 * box sat under the strip with only its rounded top edge showing (2026-09-29).
 *
 * [StripDip]'s blink cannot settle this. It hides the strip for 200 ms so the app "will lay out
 * clear of it", which needs an app that reads insets at all; this class never moves, so the strip
 * returned to the same pixels. Deciding from the app's own caret was tried first and does not
 * work either: Teams sends no `onUpdateCursorAnchorInfo` reports at all, so there is nothing to
 * decide from. What is left is the list the user already keeps, which is what the "Text box under
 * the bar" screen has always been for -- it simply used to drive the blink instead of this.
 *
 * In these apps the choice is the bar or the text box, not both, and the text box wins.
 */
object StripOverlap {
    /**
     * True when [packageName] is one the user has listed and a real field is open, so the strip
     * collapses. A field that is not really editable has no text box to be under, and an app that
     * reports no package matches nothing.
     */
    fun appDrawsUnderStrip(packageName: String?, listedApps: Set<String>, fieldIsEditable: Boolean): Boolean =
        fieldIsEditable && packageName != null && packageName in listedApps
}
