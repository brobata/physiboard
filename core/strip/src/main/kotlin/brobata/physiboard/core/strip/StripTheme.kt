package brobata.physiboard.core.strip

/**
 * The theme fields the strip uses, and only those. spec: status-bar.md SS9.1's table, reduced
 * per SS19 to "the fields the strip uses; drop the soft-keyboard geometry fields and the software
 * target". Colors are signed 32-bit ARGB as SS9.1 stores them.
 */
data class StripTheme(
    val background: Int,
    val suggestion: Int,
    val button: Int,
    val accent: Int,
    val textAndIcons: Int,
    val divider: Int,
    val keyCornerRatio: Double,
    val chromeCornerRatio: Double,
    val ledInactive: Int,
    val ledActive: Int,
    val ledLocked: Int,
    val suggestionsHeightScale: Double,
    val showLeds: Boolean,
) {
    companion object {
        /** spec SS9.2, D4: the hardware target's default scale, "about 50 dp, so buttons clear 48 dp". */
        const val HARDWARE_DEFAULT_SUGGESTIONS_SCALE: Double = 1.4

        /** spec SS9.2: the default, "Slate Dark", field by field. */
        val SLATE_DARK: StripTheme = StripTheme(
            background = 0xFF000000.toInt(),
            suggestion = 0xFF15191D.toInt(),
            button = 0xFF2B3138.toInt(),
            accent = 0xFF6496FF.toInt(),
            textAndIcons = 0xFFEFEFEF.toInt(),
            divider = 0xFF2C3136.toInt(),
            keyCornerRatio = 0.10,
            chromeCornerRatio = 0.10,
            ledInactive = 0xFF303030.toInt(),
            ledActive = 0xFF6496FF.toInt(),
            ledLocked = 0xFFF76300.toInt(),
            suggestionsHeightScale = HARDWARE_DEFAULT_SUGGESTIONS_SCALE,
            showLeds = false,
        )

        /** spec SS6.1: the microphone's background between refreshes once dictation stops, "argb 100/17/17/17". */
        const val UNTHEMED_BUTTON: Int = 0x64111111
    }
}

/** spec: status-bar.md SS7, each LED's three colors. */
enum class LedLevel { INACTIVE, ACTIVE, LOCKED }

/**
 * The modifier facts the strip is told on every refresh. spec: status-bar.md SS7's LED table and
 * SS8.3's chips. A plain physical hold is deliberately absent: SS7, "a plain physical hold is
 * deliberately ignored so the row does not flash on every keypress", and SS17, "Modifier held
 * (Shift down): nothing changes in the strip".
 */
data class ModifierIndicatorInput(
    val capsLockOn: Boolean = false,
    val shiftOneShotArmed: Boolean = false,
    val ctrlLatched: Boolean = false,
    val ctrlOneShotArmed: Boolean = false,
    val altLatched: Boolean = false,
    val altOneShotArmed: Boolean = false,
    /** spec SS7: page 0 is closed, page 2 (symbols) lights "locked", any other page "active". */
    val symPage: Int = 0,
) {
    val symPageOpen: Boolean get() = symPage != 0
}

/**
 * The optional LED row. spec: status-bar.md SS7: "Six equal-width LEDs... Shift, Sym, two
 * invisible placeholders, Ctrl, Alt", each at one of three levels.
 */
data class LedRow(val shift: LedLevel, val sym: LedLevel, val ctrl: LedLevel, val alt: LedLevel) {
    /** spec SS7: the six positions in order, null for the two invisible placeholders. */
    val positions: List<LedLevel?> get() = listOf(shift, sym, null, null, ctrl, alt)

    companion object {
        /** spec SS14: "LED color animation 200 ms". */
        const val COLOR_ANIMATION_MS: Long = 200

        /** spec SS7's table, one row per LED. Ctrl's "latched (including nav mode)" is the caller's [ModifierIndicatorInput.ctrlLatched]. */
        fun from(input: ModifierIndicatorInput): LedRow = LedRow(
            shift = when {
                input.capsLockOn -> LedLevel.LOCKED
                input.shiftOneShotArmed -> LedLevel.ACTIVE
                else -> LedLevel.INACTIVE
            },
            sym = when (input.symPage) {
                0 -> LedLevel.INACTIVE
                SYM_PAGE_SYMBOLS -> LedLevel.LOCKED
                else -> LedLevel.ACTIVE
            },
            ctrl = when {
                input.ctrlLatched -> LedLevel.LOCKED
                input.ctrlOneShotArmed -> LedLevel.ACTIVE
                else -> LedLevel.INACTIVE
            },
            alt = when {
                input.altLatched -> LedLevel.LOCKED
                input.altOneShotArmed -> LedLevel.ACTIVE
                else -> LedLevel.INACTIVE
            },
        )
    }
}

/** spec: status-bar.md SS8.3: "Only Shift... and Alt... are ever chipped". */
data class ModifierChip(val modifier: ChipModifier, val level: LedLevel) {
    enum class ChipModifier { SHIFT, ALT }
}

/**
 * The modifier chips. spec: status-bar.md SS8.2: the live strip "shows no modifier chips" since
 * "keep modifier state out of the keyboard strip", and SS19 drops them from the live strip for
 * good; SS8.3 keeps them for the theme preview only, so the rule lives here in one place rather
 * than as a hidden view that is "asked to update on every refresh and forced hidden every time".
 */
object ModifierChips {
    /** spec SS8.2: in the live strip, never. */
    const val VISIBLE_IN_LIVE_STRIP: Boolean = false

    /** spec SS8.3: the preview's chips, Shift "outline glyph when active, filled when locked" and Alt; Ctrl and Sym "left out as noise". */
    fun forPreview(input: ModifierIndicatorInput): List<ModifierChip> {
        val chips = mutableListOf<ModifierChip>()
        when {
            input.capsLockOn -> chips += ModifierChip(ModifierChip.ChipModifier.SHIFT, LedLevel.LOCKED)
            input.shiftOneShotArmed -> chips += ModifierChip(ModifierChip.ChipModifier.SHIFT, LedLevel.ACTIVE)
        }
        when {
            input.altLatched -> chips += ModifierChip(ModifierChip.ChipModifier.ALT, LedLevel.LOCKED)
            input.altOneShotArmed -> chips += ModifierChip(ModifierChip.ChipModifier.ALT, LedLevel.ACTIVE)
        }
        return chips
    }
}
