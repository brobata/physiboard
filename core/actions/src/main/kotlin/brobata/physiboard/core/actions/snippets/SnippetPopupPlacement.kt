package brobata.physiboard.core.actions.snippets

/**
 * Where the floating popup sits. spec: expansion-clipboard-pickers-launcher.md SS2.5, SS11. With
 * no keyboard window to stand on, the popup is placed by the caret: just above the line being
 * typed, or just below it when there is no room above; with no caret report, at the top of the
 * screen, clear of a compose box at the bottom. The answer is the gap between the popup's bottom
 * edge and the screen's bottom edge, which is how a bottom-anchored overlay is positioned. All
 * values are screen pixels; the caret's are null when the app reports no usable caret.
 */
object SnippetPopupPlacement {
    fun bottomMarginPx(
        caretTopPx: Float?,
        caretBottomPx: Float?,
        screenHeightPx: Int,
        popupHeightPx: Int,
        gapPx: Int,
        topLimitPx: Int,
    ): Int {
        val atTop = screenHeightPx - topLimitPx - popupHeightPx
        if (caretTopPx == null || caretBottomPx == null) return atTop.coerceAtLeast(0)
        val aboveTop = caretTopPx - gapPx - popupHeightPx
        if (aboveTop >= topLimitPx) return (screenHeightPx - (caretTopPx - gapPx)).toInt().coerceAtLeast(0)
        val belowBottom = caretBottomPx + gapPx + popupHeightPx
        return (screenHeightPx - belowBottom).toInt().coerceIn(0, atTop.coerceAtLeast(0))
    }
}
