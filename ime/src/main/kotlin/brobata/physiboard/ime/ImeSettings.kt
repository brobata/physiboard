package brobata.physiboard.ime

import brobata.physiboard.core.actions.commands.SourceVisibility
import brobata.physiboard.core.actions.launcher.CommandCustomizations
import brobata.physiboard.core.actions.launcher.LauncherBehavior
import brobata.physiboard.core.actions.launcher.LauncherKeySettings
import brobata.physiboard.core.actions.launcher.LauncherShortcuts
import brobata.physiboard.core.actions.launcher.QuickLauncherSettings
import brobata.physiboard.core.actions.snippets.SnippetPresentation
import brobata.physiboard.core.actions.snippets.SnippetRules
import brobata.physiboard.core.actions.snippets.SnippetSettings
import brobata.physiboard.core.dict.ActiveLanguages
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.RuleSet
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.LayerResolver
import brobata.physiboard.core.keys.LayoutDescription
import brobata.physiboard.core.keys.LongPressSettings
import brobata.physiboard.core.keys.SymPageEntry
import brobata.physiboard.core.keys.SymPageMap
import brobata.physiboard.core.pointer.caret.CaretBadgeSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadActivationSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadGestureSettings
import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.settings.AssistantAction
import brobata.physiboard.core.settings.BarButton
import brobata.physiboard.core.settings.HapticStrength
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.StatusBarVisibility
import brobata.physiboard.core.settings.StripThemeResolution
import brobata.physiboard.core.speech.AssistantRequest
import brobata.physiboard.core.speech.CueStrength
import brobata.physiboard.core.speech.DictationSettings
import brobata.physiboard.core.speech.DictationTextSettings
import brobata.physiboard.core.strip.ButtonSlots
import brobata.physiboard.core.strip.StripButton
import brobata.physiboard.core.strip.StripSettings
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.core.strip.StripVisibilityMode
import brobata.physiboard.core.text.LengthChangeAllowance
import brobata.physiboard.core.text.AppProfile
import brobata.physiboard.core.text.AutoCapSettings
import brobata.physiboard.core.text.AutocorrectSettings
import brobata.physiboard.core.text.BackspaceSettings
import brobata.physiboard.core.text.EnterOverride
import brobata.physiboard.core.text.RankingOptions
import brobata.physiboard.core.text.SpacingSettings

/**
 * Turns one stored [Settings] value into the per-module settings bundles the pipeline already
 * takes. This is the whole "wiring a real settings store later means constructing this from that
 * store instead of the defaults" step promised in [KeyboardSettings]' KDoc: nothing below decides
 * a typing rule, it only names which stored row feeds which pipeline field. Pure so a JUnit test
 * can check every row lands (`ImeSettingsTest`).
 *
 * Fields the store does not carry keep the pipeline's own default: each bundle starts from the
 * current shipped value and copies the stored rows over it, so a field added to a `:core` bundle
 * later (or by the strip work in flight) is not silently reset here.
 */
internal object ImeSettings {

    /**
     * [subtypeLocale] is the active input style's locale, which the strip's per-layout theme
     * overrides score against (status-bar.md SS9.2); `:ime` passes its one bundled language until
     * a subtype module exists.
     */
    fun keyboardSettings(s: Settings, subtypeLocale: String = DEFAULT_SUBTYPE_LOCALE): KeyboardSettings {
        val base = KeyboardSettings()
        return base.copy(
            modifier = base.modifier.copy(
                clearAltOnSpace = s.typing.clearAltOnSpace,
                navModeCtrlHoldEnabled = s.keys.navModeCtrlHoldEnabled,
                layoutAwareCtrlShortcuts = s.keys.layoutAwareCtrlShortcuts,
                fnLongPressSpeechEnabled = s.dictation.fnLongPressSpeech,
                symLongPressAssistantEnabled = s.dictation.symLongPressAssistant,
                // spec: trackpad-caret-nav.md SS2.2 / keys-and-modifiers.md: the assistant hold yields to a Sym trackpad trigger only while the trackpad is on.
                symIsTrackpadTrigger = s.trackpad.enabled && s.trackpad.triggerKey == TriggerKey.SYM,
                symEditShortcutsEnabled = s.keys.symEditShortcuts,
                symAutoCloseEnabled = s.symPages.autoClose,
                // spec: keys-and-modifiers.md SS7.5, the three layout-switch chords; each still needs another subtype to exist (KeyboardPipeline.anotherSubtypeAvailable).
                altShiftLayoutSwitch = s.languages.altShiftLayoutSwitch,
                altEnterLayoutSwitch = s.languages.altEnterLayoutSwitch,
                ctrlSpaceLayoutSwitch = s.languages.ctrlSpaceLayoutSwitch,
            ),
            navModeEnabled = s.keys.navModeEnabled,
            statusBar = stripSettings(s, subtypeLocale),
            resolver = LayerResolver.LayerResolverSettings(
                shiftBackspaceDelete = s.typing.shiftBackspaceDeletesForward,
                altBackspaceDelete = s.typing.altBackspaceDeletesForward,
                backspaceAtStartDelete = s.typing.backspaceAtStartDeletesForward,
                swipeToDeleteEnabled = s.typing.swipeToDelete,
            ),
            textInput = base.textInput.copy(
                autoCap = AutoCapSettings(
                    capitalizeAtTextStart = s.typing.capitalizeAtTextStart,
                    capitalizeAfterSentenceEnd = s.typing.capitalizeAfterSentenceEnd,
                    capitalizeRestrictedFields = s.typing.capitalizeRestrictedFields,
                ),
                spacing = SpacingSettings(
                    doubleSpaceToPeriod = s.typing.doubleSpaceToPeriod,
                    removeBeforeList = s.typing.removeSpaceBefore,
                    beforeNextTextList = s.typing.spaceBeforeNextText,
                    commaSpace = s.typing.commaSpace,
                    spacedHyphenToDash = s.typing.spacedHyphenToDash,
                    dashStyle = s.typing.dashStyle,
                    smartQuotes = s.typing.smartQuotes,
                    smartQuoteStyle = s.typing.smartQuoteStyle,
                    frenchPunctuationSpacing = s.typing.frenchPunctuationSpacing,
                    frenchPunctuationOnlyFrench = s.typing.frenchPunctuationOnlyFrench,
                ),
                backspace = BackspaceSettings(
                    shiftBackspaceDeletesForward = s.typing.shiftBackspaceDeletesForward,
                    altBackspaceDeletesForward = s.typing.altBackspaceDeletesForward,
                    backspaceAtStartDeletesForward = s.typing.backspaceAtStartDeletesForward,
                ),
                lengthChangeAllowance = LengthChangeAllowance.forLanguage(subtypeLocale),
                autocorrect = AutocorrectSettings(
                    suggestionsEnabled = s.correction.suggestionsEnabled,
                    autoCorrectEnabled = s.correction.textReplacementsEnabled,
                    autoReplaceOnSpaceEnter = s.correction.autoReplaceOnSpaceEnter,
                    maxAutoReplaceDistance = s.correction.maxAutoReplaceDistance,
                    useKeyboardProximity = s.correction.useKeyboardProximity,
                    accentMatchingEnabled = s.correction.accentMatching,
                ),
                rankingOptions = RankingOptions(
                    useKeyboardProximity = s.correction.useKeyboardProximity,
                    accentMatchingEnabled = s.correction.accentMatching,
                ),
            ),
            screenTrackpadEnabled = s.trackpad.enabled,
            expansion = snippetSettings(s),
            launcherKeys = LauncherKeySettings(symShortcutsEnabled = s.launcher.symShortcutsEnabled, homeScreenShortcutsEnabled = s.launcher.homeScreenShortcutsEnabled),
            launcherShortcuts = launcherShortcuts(s),
        )
    }

    /** spec: expansion-clipboard-pickers-launcher.md SS2.8, the eight `snippets_*` rows; the store's map is re-sanitised on the way in (SS2.1's load rules). */
    fun snippetSettings(s: Settings): SnippetSettings = SnippetSettings(
        enabled = s.expansion.snippetsEnabled,
        prefix = SnippetRules.effectivePrefix(s.expansion.snippetPrefix),
        snippets = SnippetRules.toSnippets(s.expansion.snippets),
        presentation = SnippetPresentation.fromStored(s.expansion.presentation.storedValue),
        expandExactOnSpace = s.expansion.expandExactOnSpace,
        acceptPrefixWithSpace = s.expansion.acceptPrefixWithSpace,
        acceptWithTab = s.expansion.acceptWithTab,
        acceptWithEnter = s.expansion.acceptWithEnter,
    )

    /**
     * spec SS6.1 "Default assignment": a blank `launcher_shortcuts` row is a store never written,
     * so Space gets the quick launcher on this read; a written document (even `{}`, the user
     * having removed it) is left alone, which is what `quick_launcher_default_assigned` guarded in 2.x.
     */
    fun launcherShortcuts(s: Settings): LauncherShortcuts {
        val parsed = LauncherShortcuts.parse(s.launcher.assignedKeysJson)
        return parsed.applyDefault(defaultAlreadyAssigned = s.launcher.assignedKeysJson.isNotBlank()).shortcuts
    }

    /** spec SS7.7 minus the dropped rows. */
    fun quickLauncherSettings(s: Settings): QuickLauncherSettings = QuickLauncherSettings(
        behavior = if (s.launcher.behavior == brobata.physiboard.core.settings.LauncherBehavior.NIAGARA) LauncherBehavior.NIAGARA else LauncherBehavior.PHYSIBOARD,
        openUniqueMatch = s.launcher.openUniqueMatch,
        limitResults = s.launcher.limitResults,
        respectKeyboardLayout = s.launcher.respectKeyboardLayout,
        typoTolerantRanking = s.launcher.typoTolerantRanking,
    )

    fun commandCustomizations(s: Settings): CommandCustomizations = CommandCustomizations.parse(s.launcher.commandCustomizationsJson)

    fun sourceVisibility(s: Settings): SourceVisibility = SourceVisibility.parse(s.launcher.commandSurfaceSourcesJson)

    /**
     * spec: keys-and-modifiers.md SS8.2, SS8.3: the long-press mode and threshold ride on the
     * layout description; layers-sym-alt.md SS4.4: a custom Sym page "replaces the shipped page
     * entirely (keys absent from the custom map have no character on that page) and the page's
     * uppercase map is emptied", letter keys only, and one with no usable entry "is treated as
     * absent".
     *
     * SPEC GAP: `keyboard_layout` (the active physical layout id) has no reader here because the
     * Titan 2 Elite QWERTY is the only [LayoutDescription] 3.0 ships (`TitanLayouts`); when a
     * second layout exists this is where [base] is chosen by `s.languages.keyboardLayout`, with
     * `layoutAutoByLocale` deciding between that row and the subtype's own mapping
     * (dictionaries-languages.md SS10).
     */
    fun layout(base: LayoutDescription, s: Settings): LayoutDescription = base.copy(
        emojiPage = customSymPage(s.symPages.customEmojiPage) ?: base.emojiPage,
        symbolsPage = customSymPage(s.symPages.customSymbolsPage) ?: base.symbolsPage,
        longPress = LongPressSettings(mode = s.keys.longPressMode, thresholdMs = s.keys.longPressThresholdMs),
    )

    private fun customSymPage(stored: Map<String, String>): SymPageMap? {
        val entries = stored.mapNotNull { (keycode, text) ->
            val letter = keycode.removePrefix("KEYCODE_").singleOrNull()?.uppercaseChar() ?: return@mapNotNull null
            if (letter !in 'A'..'Z' || text.isEmpty()) return@mapNotNull null
            (KeyId.Letter(letter) as KeyId) to SymPageEntry(lowercase = text)
        }.toMap()
        return if (entries.isEmpty()) null else SymPageMap(entries)
    }

    /**
     * spec: status-bar.md SS3 (visibility and its app list), SS6.3 (the two slot lists), SS9.2
     * (the theme, "the most specific matching override... beats the chosen theme", scored by
     * [StripThemeResolution] against the subtype's locale and the `keyboard_layout` row), SS12.2
     * (`app_keyboard_nudge_packages`) and SS4 (`titan2_elite_rounded_corner_insets`). An unknown
     * button id is already impossible here (the store is typed), so the slot lists map one to one.
     */
    fun stripSettings(s: Settings, subtypeLocale: String = DEFAULT_SUBTYPE_LOCALE): StripSettings {
        val bar = s.statusBar
        val theme = StripThemeResolution.resolve(bar.theme, bar.layoutOverrides, subtypeLocale, s.languages.keyboardLayout)
        return StripSettings(
            visibility = when (bar.visibility) {
                StatusBarVisibility.ALWAYS -> StripVisibilityMode.ALWAYS
                StatusBarVisibility.NEVER -> StripVisibilityMode.NEVER
                StatusBarVisibility.APPS -> StripVisibilityMode.APPS
            },
            apps = bar.apps,
            barHeightDp = bar.heightDp,
            slots = ButtonSlots(left = bar.leftButtons.map(::stripButton), right = bar.rightButtons.map(::stripButton)),
            dipApps = s.perApp.nudgePackages,
            roundedCorners = bar.roundedCornerInsets,
            theme = StripTheme(
                background = theme.background,
                suggestion = theme.suggestion,
                button = theme.statusBarButton,
                accent = theme.accent,
                textAndIcons = theme.textAndIcons,
                divider = theme.divider,
                keyCornerRatio = theme.keyCornerRadiusRatio,
                chromeCornerRatio = theme.chromeCornerRadiusRatio,
                ledInactive = theme.ledInactive,
                ledActive = theme.ledActive,
                ledLocked = theme.ledLocked,
                suggestionsHeightScale = theme.suggestionsHeightScale,
                showLeds = theme.showLeds,
            ),
        )
    }

    private fun stripButton(button: BarButton): StripButton = StripButton.fromId(button.id)

    /**
     * spec: autocorrect-suggestions.md SS8.1, SS8.2: which rule sets are searched. The codes are
     * `auto_correct_enabled_languages`, or when that is empty "the system language if it is one
     * of it, en, es, fr, de, pl, otherwise en"; each code's set is the bundled set overlaid with
     * the user's `auto_correct_custom_<code>` rules, and a code outside the bundled seven with
     * custom rules is its own set. [bundled] is keyed by code; `:ime` fills it from
     * [RuleSetAssetLoader], today only with `en` (`it`, `es`, `fr`, `de`, `pl` are documented
     * gaps: the spec's own bundled rule text is not something a clean-room author can reproduce
     * from first knowledge the way the English rules could be).
     */
    fun ruleSets(s: Settings, systemLanguage: String, bundled: Map<String, RuleSet> = emptyMap()): List<RuleSet> {
        val chosen = s.correction.textReplacementLanguages.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val codes = chosen.ifEmpty { listOf(if (systemLanguage.lowercase() in BUNDLED_RULE_SET_CODES) systemLanguage.lowercase() else "en") }.distinct()
        return codes.mapNotNull { code ->
            val custom = s.correction.customSubstitutions[code]
            val base = bundled[code]
            when {
                base != null && custom != null -> base.overlaidWith(custom.rules.filterKeys { it != RESERVED_NAME_KEY })
                base != null -> base
                custom != null -> RuleSet(code, custom.displayName.ifEmpty { null }, custom.rules.filterKeys { it != RESERVED_NAME_KEY })
                else -> null
            }
        }
    }

    /**
     * spec: dictionaries-languages.md SS8.4: the extra suggestion languages for the active input
     * style. Lookup for locale `L` (hyphenated, language `l`) and layout `Y`: `L:Y`, then `l:Y`,
     * then for `qwertz` the legacy aliases; tags are trimmed, deduplicated, `x-pastiera` dropped
     * and the primary excluded ([ActiveLanguages.of]). [layout] is the `keyboard_layout` row, "not
     * the subtype's own layout".
     */
    fun extraSuggestionLanguages(s: Settings, primary: LanguageCode, subtypeLocale: String): List<LanguageCode> {
        val locale = subtypeLocale.replace('_', '-')
        val language = locale.substringBefore('-')
        val layout = s.languages.keyboardLayout
        val keys = buildList {
            add("$locale:$layout")
            add("$language:$layout")
            if (layout == "qwertz") {
                add("$locale:$LEGACY_QWERTZ_LAYOUT")
                add("$language:$LEGACY_QWERTZ_LAYOUT")
            }
        }
        val stored = s.languages.suggestionLocales.mapKeys { it.key.replace('_', '-') }
        val tags = keys.firstNotNullOfOrNull { key -> stored.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value } ?: emptyList()
        return ActiveLanguages.of(primary, tags).extra
    }

    /** spec: trackpad-caret-nav.md SS4.8, the badge's three rows. */
    fun caretBadge(s: Settings): CaretBadgeSettings =
        CaretBadgeSettings(enabled = s.statusBar.caretModifierBadge, armedColorArgb = s.statusBar.caretBadgeArmedColor, lockedColorArgb = s.statusBar.caretBadgeLockedColor)

    /** spec: dictation.md SS11.2 step 1: `assistant_action`'s `auto` is "no preference". */
    fun assistantRequest(s: Settings): AssistantRequest? = when (s.dictation.assistantAction) {
        AssistantAction.AUTO -> null
        AssistantAction.VOICE_COMMAND -> AssistantRequest.VOICE_COMMAND
        AssistantAction.HANDS_FREE -> AssistantRequest.HANDS_FREE
        AssistantAction.ASSIST -> AssistantRequest.ASSIST
    }

    /** spec: per-app-behavior.md SS4.1: every exact-typing package is a profile with raw mode on. */
    fun appProfiles(s: Settings): List<AppProfile> =
        s.perApp.exactTypingPackages.sorted().map { AppProfile(packageName = it, exactTypingEnabled = true) }

    fun enterOverrides(s: Settings): List<EnterOverride> = s.perApp.enterOverrides.map {
        EnterOverride(packageName = it.packageName, behavior = it.behavior, sendMethod = it.sendMethod, extraSendShortcut = it.extraSendShortcut)
    }

    fun trackpadActivation(s: Settings): TrackpadActivationSettings =
        TrackpadActivationSettings(triggerKey = s.trackpad.triggerKey, activationMode = s.trackpad.activation)

    fun trackpadGesture(s: Settings): TrackpadGestureSettings =
        TrackpadGestureSettings(horizontalStepPx = s.trackpad.stepPx.toFloat(), showHint = s.trackpad.showHint)

    /**
     * spec: dictation.md SS6.3, SS13: the pause and the segmented-session switch; SS4.2: masking
     * and automatic punctuation; SS8.1: the cues and their strength. [androidApiLevel] is the
     * caller's `Build.VERSION.SDK_INT`.
     */
    fun dictationSettings(s: Settings, androidApiLevel: Int): DictationSettings = DictationSettings(
        pauseMs = s.dictation.endSilenceMs.toLong(),
        segmentedSessionEnabled = s.dictation.continuousSession,
        androidApiLevel = androidApiLevel,
        maskOffensive = s.dictation.maskOffensive,
        autoPunctuation = s.dictation.autoPunctuation,
        hapticsEnabled = s.dictation.haptics,
        hapticStrength = when (s.dictation.hapticStrength) {
            HapticStrength.LIGHT -> CueStrength.LIGHT
            HapticStrength.STANDARD -> CueStrength.STANDARD
            HapticStrength.STRONG -> CueStrength.STRONG
        },
    )

    /** spec: dictation.md SS7.1: dictation capitalises by the same two auto-cap rows as typing; `capitalizationAllowed` stays the field's own answer. */
    fun dictationTextSettings(current: DictationTextSettings, s: Settings): DictationTextSettings =
        current.copy(capitalizeFirstLetter = s.typing.capitalizeAtTextStart, capitalizeAfterSentenceEnd = s.typing.capitalizeAfterSentenceEnd)

    /** The one bundled language's locale, what `:ime` scores theme overrides and extra-language lookups against until a subtype module exists. */
    const val DEFAULT_SUBTYPE_LOCALE: String = "en"

    /** spec: autocorrect-suggestions.md SS8.1's bundled codes minus `x-pastiera`, the ones the absent-value rule may pick and [RuleSetAssetLoader] tries to load. */
    val BUNDLED_RULE_SET_CODES: Set<String> = setOf("it", "en", "es", "fr", "de", "pl")

    /** spec: autocorrect-suggestions.md SS8.1: `__name` is the display name, never a rule. */
    private const val RESERVED_NAME_KEY = "__name"

    /** spec: dictionaries-languages.md SS8.4, the legacy alias `qwertz` lookups also try. */
    private const val LEGACY_QWERTZ_LAYOUT = "german_multitap_qwertz"
}
