package it.palsoftware.pastiera

import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.ui.KeyboardThemeColors

internal data class KeyboardThemePreset(
    val name: String,
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
    val chromeCornerRadiusRatio: Float = 0.08f,
    val keyHeightScale: Float = 1f,
    val keyWidthScale: Float = 1f,
    val rowGapScale: Float = 0f,
    val distributeHorizontalSpacing: Boolean = true,
    val ortholinear: Boolean = false,
    val showLeds: Boolean = true
)

internal data class KeyboardThemeOption(
    val key: String,
    val preset: KeyboardThemePreset,
    val resetPreset: KeyboardThemePreset,
    val userSaved: Boolean = false
)

internal fun KeyboardThemePreset.toAospThemeOverride(): AospKeyboardView.ThemeOverride =
    AospKeyboardView.ThemeOverride(
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = accent,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        keyHeightScale = keyHeightScale,
        keyWidthScale = keyWidthScale,
        rowGapScale = rowGapScale,
        distributeHorizontalSpacing = distributeHorizontalSpacing,
        ortholinear = ortholinear
    )

internal fun KeyboardThemePreset.toKeyboardThemeColors(): KeyboardThemeColors =
    KeyboardThemeColors(
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = accent,
        cursorSwipe = cursorSwipe,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        suggestion = suggestion,
        statusBarButton = statusBarButton,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        chromeCornerRadiusRatio = chromeCornerRadiusRatio
    )

internal fun KeyboardThemePreset.toSettingsTheme(): SettingsManager.KeyboardThemeSettings =
    SettingsManager.KeyboardThemeSettings(
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = accent,
        cursorSwipe = cursorSwipe,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        suggestion = suggestion,
        statusBarButton = statusBarButton,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        chromeCornerRadiusRatio = chromeCornerRadiusRatio,
        keyHeightScale = keyHeightScale,
        keyWidthScale = keyWidthScale,
        rowGapScale = rowGapScale,
        distributeHorizontalSpacing = distributeHorizontalSpacing,
        ortholinear = ortholinear,
        showLeds = showLeds
    )

internal fun SettingsManager.KeyboardThemeSettings.toKeyboardThemePreset(name: String): KeyboardThemePreset =
    KeyboardThemePreset(
        name = name,
        background = background,
        divider = divider,
        normalKey = normalKey,
        specialKey = specialKey,
        textAndIcons = textAndIcons,
        ledInactive = ledInactive,
        ledActive = ledActive,
        ledLocked = ledLocked,
        accent = accent,
        cursorSwipe = cursorSwipe,
        keyPopup = keyPopup,
        keyPopupSelected = keyPopupSelected,
        suggestion = suggestion,
        statusBarButton = statusBarButton,
        keyCornerRadiusRatio = keyCornerRadiusRatio,
        chromeCornerRadiusRatio = chromeCornerRadiusRatio,
        keyHeightScale = keyHeightScale,
        keyWidthScale = keyWidthScale,
        rowGapScale = rowGapScale,
        distributeHorizontalSpacing = distributeHorizontalSpacing,
        ortholinear = ortholinear,
        showLeds = showLeds
    )

internal fun SettingsManager.NamedKeyboardTheme.toKeyboardThemeOption(): KeyboardThemeOption {
    val preset = theme.toKeyboardThemePreset(name)
    return KeyboardThemeOption(
        key = "saved:$name",
        preset = preset,
        resetPreset = preset,
        userSaved = true
    )
}

internal fun keyboardThemeSwatches(): List<Int> = keyboardThemePresets()
    .flatMap { preset ->
        listOf(
            preset.background,
            preset.divider,
            preset.normalKey,
            preset.specialKey,
            preset.textAndIcons,
            preset.ledInactive,
            preset.ledActive,
            preset.ledLocked,
            preset.cursorSwipe,
            preset.keyPopup,
            preset.keyPopupSelected,
            preset.suggestion,
            preset.statusBarButton
        )
    }
    .distinct()

internal fun keyboardThemePresets(): List<KeyboardThemePreset> = listOf(
    KeyboardThemePreset("Pastiera Dark", 0xFF000000.toInt(), 0xFF2C3136.toInt(), 0xFF15191D.toInt(), 0xFF2B3138.toInt(), 0xFFEFEFEF.toInt(), 0xFF303030.toInt(), 0xFF6496FF.toInt(), 0xFFF76300.toInt(), 0xFF6496FF.toInt()),
    KeyboardThemePreset("Pastiera Light", 0xFFF8FAFC.toInt(), 0xFFC7CDD4.toInt(), 0xFFFFFFFF.toInt(), 0xFFE0E6EE.toInt(), 0xFF171A1F.toInt(), 0xFFD1D5DB.toInt(), 0xFF276EF1.toInt(), 0xFFD65A00.toInt(), 0xFF276EF1.toInt()),
    KeyboardThemePreset("ePaper", 0xFFF2F2F2.toInt(), 0xFFB8B8B8.toInt(), 0xFFFAFAFA.toInt(), 0xFFDDDDDD.toInt(), 0xFF111111.toInt(), 0xFFB0B0B0.toInt(), 0xFF555555.toInt(), 0xFF111111.toInt(), 0xFF3F8C96.toInt()),
    KeyboardThemePreset("High Contrast", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF0D0D0D.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF555555.toInt(), 0xFF00E5FF.toInt(), 0xFFFFEA00.toInt(), 0xFFFFEA00.toInt()),
    KeyboardThemePreset("Warm", 0xFF241F1A.toInt(), 0xFF6F6255.toInt(), 0xFF352E27.toInt(), 0xFF5B4734.toInt(), 0xFFFFF1DD.toInt(), 0xFF665A4E.toInt(), 0xFFE0B05D.toInt(), 0xFFE06A4B.toInt(), 0xFFE0B05D.toInt()),
    KeyboardThemePreset("Aegina Coast", 0xFF0C2028.toInt(), 0xFF2D6470.toInt(), 0xFF173540.toInt(), 0xFF2C6F7B.toInt(), 0xFFE9FBFF.toInt(), 0xFF45636A.toInt(), 0xFF55D6E8.toInt(), 0xFFFFD166.toInt(), 0xFF55D6E8.toInt()),
    KeyboardThemePreset("Volcanic Dusk", 0xFF1B141A.toInt(), 0xFF5D3B4F.toInt(), 0xFF2A2028.toInt(), 0xFF723650.toInt(), 0xFFFFEDF5.toInt(), 0xFF66515F.toInt(), 0xFFFF5D9E.toInt(), 0xFFFFB000.toInt(), 0xFFFF5D9E.toInt())
)
