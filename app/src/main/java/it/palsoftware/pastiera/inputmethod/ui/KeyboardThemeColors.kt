package it.palsoftware.pastiera.inputmethod.ui

data class KeyboardThemeColors(
    val background: Int,
    val divider: Int,
    val normalKey: Int,
    val specialKey: Int,
    val textAndIcons: Int,
    val ledInactive: Int,
    val ledActive: Int,
    val ledLocked: Int,
    val accent: Int,
    val cursorSwipe: Int = accent,
    val keyPopup: Int = specialKey,
    val keyPopupSelected: Int = accent,
    val suggestion: Int = normalKey,
    val statusBarButton: Int = specialKey,
    val keyCornerRadiusRatio: Float = 0.08f,
    val chromeCornerRadiusRatio: Float = 0.08f
)
