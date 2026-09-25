package brobata.physiboard.core.settings

/** One card of Status Bar Theme's "Choose a preset" row. spec: status-bar.md SS9.3, SS9.4 item 1. */
data class StripThemePreset(val name: String, val theme: StripTheme)

/**
 * The twenty-four built-in Status Bar Theme presets, as pure data. spec: status-bar.md SS9.3's
 * table (background, divider, normal key, special key, text and icons, LED inactive/active/locked,
 * accent) plus its per-row exceptions to the derivation rule ("All other presets derive suggestion
 * from normal key, status bar button from special key... and use 0.08 rounding").
 *
 * [StripTheme] is already reduced to the fields the strip itself uses (status-bar.md SS9.1): there
 * is no key-popup, cursor-swipe or on-screen-keyboard geometry left to carry, so a preset here sets
 * only [StripTheme.suggestion] (from the table's "normal key" column) and
 * [StripTheme.statusBarButton] (from "special key"), not the dropped fields the source table also
 * lists.
 *
 * SPEC GAP: SS9.3 also claims "The presets' locked LED colors are all distinct from each other
 * (a test asserts it)", but its own table gives Cloud Tap and Classic Cloud the same locked color
 * (FF9500) and likewise Moon Tap and Classic Midnight (FF9F0A). The table (the falsifiable,
 * structured source) is transcribed verbatim rather than silently altered to satisfy the prose
 * claim, which this module has no authority to invent replacement hex for; distinctness is
 * checked only within the pairs the table actually varies.
 *
 * SPEC GAP: SS9.3 gives [StripTheme.suggestionsHeightScale] explicitly only for the Cloud
 * Tap/Moon Tap/Classic Cloud/Classic Midnight group (0.9, "keep their geometry"); every other
 * preset's software-target geometry is described but 3.0 has no software target ([StatusBarPrefs]
 * carries one theme, hardware only). This table therefore gives every other preset the hardware
 * baseline 1.4 (status-bar.md SS9.2's "for the hardware target suggestions height scale 1.4")
 * rather than a per-preset software value SS9.3 never states for the hardware target.
 */
object StripThemePresets {
    private const val DEFAULT_ROUNDING = 0.08
    private const val HARDWARE_SUGGESTIONS_HEIGHT = 1.4
    private const val GEOMETRY_GROUP_SUGGESTIONS_HEIGHT = 0.9

    private fun preset(
        name: String,
        bg: Long,
        divider: Long,
        normal: Long,
        special: Long,
        text: Long,
        ledOff: Long,
        ledOn: Long,
        ledLock: Long,
        accent: Long,
        keyRounding: Double = DEFAULT_ROUNDING,
        chromeRounding: Double = DEFAULT_ROUNDING,
        suggestion: Long = normal,
        statusBarButton: Long = special,
        suggestionsHeightScale: Double = HARDWARE_SUGGESTIONS_HEIGHT,
    ): StripThemePreset = StripThemePreset(
        name,
        StripTheme(
            background = argb(bg),
            divider = argb(divider),
            suggestion = argb(suggestion),
            statusBarButton = argb(statusBarButton),
            textAndIcons = argb(text),
            accent = argb(accent),
            ledInactive = argb(ledOff),
            ledActive = argb(ledOn),
            ledLocked = argb(ledLock),
            keyCornerRadiusRatio = keyRounding,
            chromeCornerRadiusRatio = chromeRounding,
            suggestionsHeightScale = suggestionsHeightScale,
        ),
    )

    /** Fully opaque ARGB from a `0xRRGGBB` literal, matching status-bar.md SS9.3's hex columns. */
    private fun argb(rgb: Long): Int = (0xFF000000L or rgb).toInt()

    val ALL: List<StripThemePreset> = listOf(
        preset("Slate Dark", 0x000000, 0x2C3136, 0x15191D, 0x2B3138, 0xEFEFEF, 0x303030, 0x6496FF, 0xF76300, 0x6496FF, keyRounding = 0.10, chromeRounding = 0.10),
        preset("Slate Light", 0xF8FAFC, 0xC7CDD4, 0xFFFFFF, 0xE0E6EE, 0x171A1F, 0xD1D5DB, 0x276EF1, 0xD65A00, 0x276EF1, keyRounding = 0.10, chromeRounding = 0.10),
        preset(
            "Cloud Tap", 0xE1E3E7, 0xD4D7DD, 0xFFFFFF, 0xFFFFFF, 0x050505, 0xC2C6CE, 0x0A84FF, 0xFF9500, 0x0A84FF,
            keyRounding = 0.18186983, chromeRounding = 0.35, suggestion = 0xDDE0E5, statusBarButton = 0xFFFFFF,
            suggestionsHeightScale = GEOMETRY_GROUP_SUGGESTIONS_HEIGHT,
        ),
        preset(
            "Moon Tap", 0x111111, 0x303030, 0x3A3A3C, 0x3A3A3C, 0xF8F8F8, 0x303030, 0x409CFF, 0xFF9F0A, 0x409CFF,
            keyRounding = 0.18186983, chromeRounding = 0.35, suggestion = 0x171717, statusBarButton = 0x1C1C1E,
            suggestionsHeightScale = GEOMETRY_GROUP_SUGGESTIONS_HEIGHT,
        ),
        preset(
            "Classic Cloud", 0xCCD2DC, 0x9EA5AF, 0xFFFFFF, 0xAFB6C2, 0x000000, 0xAEB5C0, 0x007AFF, 0xFF9500, 0x007AFF,
            keyRounding = 0.118, chromeRounding = 0.09, suggestion = 0xCCD2DC, statusBarButton = 0xAFB6C2,
            suggestionsHeightScale = GEOMETRY_GROUP_SUGGESTIONS_HEIGHT,
        ),
        preset(
            "Classic Midnight", 0x1C1C1E, 0x4A4A4D, 0x3A3A3C, 0x2C2C2E, 0xFFFFFF, 0x404044, 0x0A84FF, 0xFF9F0A, 0x0A84FF,
            keyRounding = 0.118, chromeRounding = 0.09, suggestion = 0x202124, statusBarButton = 0x2C2C2E,
            suggestionsHeightScale = GEOMETRY_GROUP_SUGGESTIONS_HEIGHT,
        ),
        preset("ePaper", 0xF2F2F2, 0xB8B8B8, 0xFAFAFA, 0xDDDDDD, 0x111111, 0xB0B0B0, 0x555555, 0x111111, 0x3F8C96),
        preset("High Contrast", 0x000000, 0xFFFFFF, 0x0D0D0D, 0x000000, 0xFFFFFF, 0x555555, 0x00E5FF, 0xFFEA00, 0xFFEA00),
        preset("Warm", 0x241F1A, 0x6F6255, 0x352E27, 0x5B4734, 0xFFF1DD, 0x665A4E, 0xE0B05D, 0xE06A4B, 0xE0B05D),
        preset("Solarized Dark", 0x002B36, 0x586E75, 0x073642, 0x16424D, 0xEEE8D5, 0x586E75, 0x2AA198, 0xB58900, 0x2AA198),
        preset("Solarized Light", 0xFDF6E3, 0x93A1A1, 0xFFFBEC, 0xEEE8D5, 0x073642, 0xB8B7AA, 0x268BD2, 0xCB4B16, 0x268BD2),
        preset("Monokai", 0x272822, 0x75715E, 0x3E3D32, 0x49483E, 0xF8F8F2, 0x75715E, 0xA6E22E, 0xFFD866, 0x66D9EF),
        preset("Dracula", 0x282A36, 0x6272A4, 0x343746, 0x44475A, 0xF8F8F2, 0x6272A4, 0xFF79C6, 0xF1FA8C, 0xBD93F9),
        preset("Nord", 0x2E3440, 0x4C566A, 0x3B4252, 0x434C5E, 0xECEFF4, 0x4C566A, 0x88C0D0, 0xEBCB8B, 0x88C0D0),
        preset("Volcanic Dusk", 0x1B141A, 0x5D3B4F, 0x2A2028, 0x723650, 0xFFEDF5, 0x66515F, 0xFF5D9E, 0xFFB000, 0xFF5D9E),
        preset("Terminal (Amber)", 0x0F172A, 0x334155, 0x1E293B, 0x334155, 0xF1F5F9, 0x475569, 0xF59E0B, 0xB45309, 0xF59E0B),
        preset("Terminal (Green)", 0x000000, 0x334155, 0x1E293B, 0x334155, 0x33FF88, 0x475569, 0x33FF88, 0x16A34A, 0x33FF88),
        preset("Synthwave", 0x1A1033, 0x6D28D9, 0x241748, 0x3B1E6B, 0xF5D0FE, 0x4C1D95, 0xFF2E97, 0x22D3EE, 0xFF2E97),
        preset("Vapourwave", 0x2B1B4D, 0x7C4DBE, 0x3C2569, 0x553383, 0xEAD9FF, 0x5B3A8C, 0x00F0FF, 0xFF71CE, 0xFF71CE),
        preset("Hazard", 0x141414, 0x4A4A00, 0x1F1F1F, 0x2E2E00, 0xFFE81A, 0x3D3D0A, 0xFFE81A, 0xFF6B00, 0xFFE81A),
        preset("Blueprint", 0x0B3D91, 0x3D6FC4, 0x11499E, 0x1A56AE, 0xDCE9FF, 0x2E5FB0, 0xFFFFFF, 0x7FD4FF, 0x7FD4FF),
        preset("Forest Floor", 0x14200F, 0x3E5B33, 0x1D2E17, 0x2C4423, 0xE8F3DF, 0x3A5230, 0x9CCC65, 0xD4A017, 0x9CCC65),
        preset("Rose Gold", 0x2A1A1E, 0x7A4A55, 0x3A252B, 0x4E3038, 0xFFE4E8, 0x5C3B44, 0xE8A0A8, 0xD98C6A, 0xE8A0A8),
        preset("Ink and Paper", 0xFFFFFF, 0x000000, 0xFFFFFF, 0xF0F0F0, 0x000000, 0xBBBBBB, 0x000000, 0x6E6E6E, 0x000000),
    )
}
