package brobata.physiboard.core.strip

/**
 * Every strip preference with its shipped default. spec: status-bar.md SS15 and SS19. There is
 * no `:core:settings` store wired to the strip yet, so a caller constructs this from defaults or
 * from its own store; nothing here reads a preference.
 *
 * [slots] ships SS6.3's first-run baseline (clipboard left, microphone right) rather than the
 * preference default (hamburger / emoji, microphone), for the reason `KeyboardSettings` gives
 * for every baseline it ships: a real Titan never runs with an empty store, and SS6.3's own
 * summary is "a fresh install shows clipboard on the left and the microphone on the right".
 */
data class StripSettings(
    val visibility: StripVisibilityMode = StripVisibilityMode.ALWAYS,
    val apps: Set<String> = StripVisibility.SEEDED_APPS,
    val barHeightDp: Int = StripGeometry.DEFAULT_BAR_HEIGHT_DP,
    val slots: ButtonSlots = ButtonSlots.FIRST_RUN_BASELINE,
    /** spec SS12.2: `app_keyboard_nudge_packages`. */
    val dipApps: Set<String> = StripDip.SEEDED_APPS,
    /** spec SS4, D3: `titan2_elite_rounded_corner_insets`, true on the Elite, the only device 3.0 ships to. */
    val roundedCorners: Boolean = true,
    /**
     * Collapse the strip in a field that allows no suggestions, where its slots can never fill.
     * On by default: a terminal showed an empty band taking a tenth of the screen
     * (2026-09-26). Switching it off keeps the strip's buttons available everywhere.
     */
    val hideWhereNothingToSuggest: Boolean = true,
    val theme: StripTheme = StripTheme.SLATE_DARK,
)

/**
 * What the keyboard knows at one refresh, as the strip needs it. spec: status-bar.md SS1,
 * "Refresh: PhysiBoard rebuilds a snapshot of its state (modifiers, Sym page, suggestions,
 * clipboard count, the app being typed into)... and hands it to the strip."
 */
data class StripInputs(
    val packageName: String?,
    val suggestions: List<String>,
    val addWordCandidate: String? = null,
    val expansionSuggestions: List<String> = emptyList(),
    val suggestionsEnabled: Boolean = true,
    val fieldAllowsSuggestions: Boolean = true,
    /** The app is drawing its text box under the strip; see [StripOverlap]. */
    val fieldDrawsUnderStrip: Boolean = false,
    val dictionaryInstalled: Boolean = true,
    val clipboardOverlayOpen: Boolean = false,
    val modifiers: ModifierIndicatorInput = ModifierIndicatorInput(),
    val navModeLatched: Boolean = false,
    val clipboardCount: Int = 0,
    val dictationActive: Boolean = false,
    val subtypeLocale: String? = null,
)

/**
 * The strip, described. `:ime` draws exactly this and nothing else, and redraws only when it
 * changes (spec SS1: "only redrawn when the snapshot... actually changed since the last draw";
 * SS5.5), which a plain equality check on this value gives for free.
 */
data class StripModel(
    val footprint: StripFootprint,
    val row: SuggestionRow,
    val leftButtons: List<StripButton>,
    val rightButtons: List<StripButton>,
    val languageLabel: String,
    val clipboardCount: Int,
    val dictationActive: Boolean,
    /** spec SS7: the LED row, present only when the theme's `show_leds` is on. */
    val leds: LedRow?,
    /**
     * spec SS6.4 and SS17: the quick-actions overlay "is closed... in hardware mode, on every
     * strip refresh", so a freshly built model never has it open. [QuickActions.CLOSES_ON_EVERY_REFRESH]
     * records the 2.x fact; this field is where it takes effect.
     */
    val quickActionsOpen: Boolean = false,
) {
    /** spec SS6.2: "The first left button and the last right button are edge buttons when the rounded-corner setting is on". */
    fun isEdgeButton(side: StripSide, index: Int, roundedCorners: Boolean): Boolean {
        if (!roundedCorners) return false
        return when (side) {
            StripSide.LEFT -> index == 0 && leftButtons.isNotEmpty()
            StripSide.RIGHT -> index == rightButtons.lastIndex && rightButtons.isNotEmpty()
        }
    }

    companion object {
        /** spec: status-bar.md SS3.5, SS5.1, SS5.2, SS6.1, SS6.4, SS7, all combined into one snapshot. */
        fun build(inputs: StripInputs, settings: StripSettings): StripModel {
            val expansionActive = inputs.expansionSuggestions.isNotEmpty()
            val rowVisible = SuggestionRowRules.rowVisible(
                suggestionsEnabled = inputs.suggestionsEnabled,
                fieldAllowsSuggestions = inputs.fieldAllowsSuggestions,
                symPageOpen = inputs.modifiers.symPageOpen,
                clipboardOverlayOpen = inputs.clipboardOverlayOpen,
                dictionaryInstalled = inputs.dictionaryInstalled,
                expansionActive = expansionActive,
            )
            val row = when {
                !rowVisible -> SuggestionRow.Hidden
                expansionActive -> SuggestionRowRules.mapExpansion(inputs.expansionSuggestions)
                else -> SuggestionRowRules.map(inputs.suggestions, inputs.addWordCandidate)
            }
            return StripModel(
                footprint = StripVisibility.footprint(
                    mode = settings.visibility,
                    apps = settings.apps,
                    packageName = inputs.packageName,
                    symPageOpen = inputs.modifiers.symPageOpen,
                    navModeLatched = inputs.navModeLatched,
                    fieldOffersSuggestions = inputs.fieldAllowsSuggestions,
                    hideWhereNothingToSuggest = settings.hideWhereNothingToSuggest,
                    fieldDrawsUnderStrip = inputs.fieldDrawsUnderStrip,
                ),
                row = row,
                leftButtons = settings.slots.drawn(StripSide.LEFT),
                rightButtons = settings.slots.drawn(StripSide.RIGHT),
                languageLabel = LanguageLabel.of(inputs.subtypeLocale),
                clipboardCount = inputs.clipboardCount,
                dictationActive = inputs.dictationActive,
                leds = if (settings.theme.showLeds) LedRow.from(inputs.modifiers) else null,
                quickActionsOpen = false,
            )
        }
    }
}
