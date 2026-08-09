package it.palsoftware.pastiera.inputmethod

import android.graphics.Rect
import android.inputmethodservice.InputMethodService

internal object ImeInsetsPolicy {
    fun applyCandidatesOnlyContentInsets(
        insets: InputMethodService.Insets,
        candidatesOnly: Boolean,
        touchableWidth: Int = 0,
        touchableHeight: Int = 0
    ) {
        if (candidatesOnly) {
            insets.contentTopInsets = insets.visibleTopInsets
            val touchableRegion = insets.touchableRegion
            val touchableBounds = candidatesTouchableBounds(
                contentTop = insets.contentTopInsets,
                width = touchableWidth,
                height = touchableHeight
            )
            if (touchableRegion != null && touchableBounds != null) {
                insets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
                touchableRegion.set(touchableBounds)
            } else {
                insets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT
            }
        }
    }

    internal fun candidatesTouchableBounds(
        contentTop: Int,
        width: Int,
        height: Int
    ): Rect? = if (width > 0 && height > contentTop) {
        Rect(0, contentTop, width, height)
    } else {
        null
    }
}
