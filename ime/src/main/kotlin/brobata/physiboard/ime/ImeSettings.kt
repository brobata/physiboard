package brobata.physiboard.ime

import brobata.physiboard.core.actions.commands.SourceVisibility
import brobata.physiboard.core.actions.emoji.SkinTone
import brobata.physiboard.core.actions.emoji.SkinTones
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
import brobata.physiboard.core.keys.CtrlMappingTable
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.LayerResolver
import brobata.physiboard.core.keys.LayoutDescription
import brobata.physiboard.core.keys.LongPressSettings
import brobata.physiboard.core.keys.SymPageEntry
import brobata.physiboard.core.keys.SymPageId
import brobata.physiboard.core.keys.SymPageMap
import brobata.physiboard.core.keys.SymPagesConfig
import brobata.physiboard.core.keys.Variations
import brobata.physiboard.core.pointer.caret.CaretBadgeSettings
import brobata.physiboard.core.pointer.keyboardswipe.KeyboardSwipeSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadActivationSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadGestureSettings
import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.settings.AssistantAction
import brobata.physiboard.core.settings.BarButton
import brobata.physiboard.core.settings.HapticStrength
import brobata.physiboard.core.settings.Settings
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
                // layers-sym-alt.md SS5.10: two quick Sym taps open the page chooser.
                symDoubleTapChooser = s.symPages.doubleTapChooser,
                // spec: keys-and-modifiers.md SS7.5, the three layout-switch chords; each still needs another subtype to exist (KeyboardPipeline.anotherSubtypeAvailable).
                altShiftLayoutSwitch = s.languages.altShiftLayoutSwitch,
                altEnterLayoutSwitch = s.languages.altEnterLayoutSwitch,
                ctrlSpaceLayoutSwitch = s.languages.ctrlSpaceLayoutSwitch,
            ),
            navModeEnabled = s.keys.navModeEnabled,
            bounceKeys = brobata.physiboard.core.keys.BounceKeySettings(
                enabled = s.keys.bounceKeysEnabled,
                delayMs = s.keys.bounceKeysDelayMs,
                characterKeysEnabled = s.keys.bounceKeysCharacterKeysEnabled,
                modifierKeysEnabled = s.keys.bounceKeysModifierKeysEnabled,
                spaceEnabled = s.keys.bounceKeysSpaceEnabled,
                enterEnabled = s.keys.bounceKeysEnterEnabled,
                backspaceEnabled = s.keys.bounceKeysBackspaceEnabled,
            ),
            overlappingKeys = brobata.physiboard.core.keys.AccidentalPressSettings(enabled = s.keys.overlappingKeysEnabled),
            statusBar = stripSettings(s, subtypeLocale),
            resolver = LayerResolver.LayerResolverSettings(
                shiftBackspaceDelete = s.typing.shiftBackspaceDeletesForward,
                altBackspace = s.typing.altBackspace,
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
                    altBackspace = s.typing.altBackspace,
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
                    fixWordMixups = s.correction.fixWordMixups,
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
        staticTopHighlight = s.launcher.quickLauncherStaticTopHighlight,
        staticTopHighlightColor = s.launcher.quickLauncherStaticTopHighlightColor,
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
     * [base] is the current input style's own layout, resolved by the caller through
     * `:core:subtype`'s `InputStyleCatalog.layoutFor` (dictionaries-languages.md SS10); this
     * function only overlays the customizations every layout gets regardless of which style is
     * active.
     *
     * [ctrlMappings] is the Fn Layer map (keys-and-modifiers.md SS12, trackpad-caret-nav.md SS5.4,
     * SS5.9): the user's `ctrl_key_mappings.json`, or the shipped asset when there is none. Loading
     * that file is real I/O (`:ime`'s `CtrlMappingFileLoader`), so this pure function only takes
     * the already-decoded table; null keeps [base]'s own shipped default, which is what every
     * existing caller that has not loaded a file yet still gets.
     *
     * [subtypeLocale] is the active input style's locale: layers-sym-alt.md SS8.2 orders the
     * built-in accent table for its language, and `custom_variations` lays the user's own lists
     * over it.
     */
    fun layout(base: LayoutDescription, s: Settings, ctrlMappings: CtrlMappingTable? = null, subtypeLocale: String? = null): LayoutDescription = base.copy(
        emojiPage = toned(customSymPage(s.symPages.customEmojiPage) ?: base.emojiPage, s.symPages.defaultSkinTone),
        symbolsPage = toned(customSymPage(s.symPages.customSymbolsPage) ?: base.symbolsPage, s.symPages.defaultSkinTone),
        symPagesConfig = symPagesConfig(s.symPages.pages),
        longPress = LongPressSettings(mode = s.keys.longPressMode, thresholdMs = s.keys.longPressThresholdMs),
        ctrlMappings = ctrlMappings ?: base.ctrlMappings,
        variations = Variations.effective(subtypeLocale, Variations.overridesFromStored(s.keys.customVariations)),
        customPages = customPages(s),
    )

    /**
     * spec: layers-sym-alt.md SS4.6: the user's own pages 7 to 9, each a key layer built the way
     * a custom Emoji or Symbols page is (SS4.4, letter keys only, no uppercase map) and toned the
     * same way; a page with no entry is left out and has no characters.
     */
    private fun customPages(s: Settings): Map<SymPageId, SymPageMap> =
        SymPageId.CUSTOM.zip(s.symPages.customPages).mapNotNull { (id, page) ->
            customSymPage(page.mappings)?.let { id to toned(it, s.symPages.defaultSkinTone) }
        }.toMap()

    /**
     * spec: layers-sym-alt.md SS5.10, SS4.6: the chooser names of the user's own pages that are
     * set up (switched on, or holding at least one key), for [brobata.physiboard.core.keys.SymPageChooser.entries].
     */
    fun customPageNames(s: Settings): Map<SymPageId, String> {
        val enabled = listOf(s.symPages.pages.custom1Enabled, s.symPages.pages.custom2Enabled, s.symPages.pages.custom3Enabled)
        return SymPageId.CUSTOM.indices.mapNotNull { index ->
            val page = s.symPages.customPages.getOrNull(index) ?: return@mapNotNull null
            if (!enabled[index] && page.mappings.values.none { it.isNotEmpty() }) return@mapNotNull null
            SymPageId.CUSTOM[index] to page.name
        }.toMap()
    }

    /**
     * spec: layers-sym-alt.md SS4.1, SS4.2: `sym_pages_config`'s enabled flags and cycle order
     * overlay the device's shipped default, so the Sym key cycles the pages the user chose
     * instead of always the hard-coded `:device:titan` order. [stored]'s order already went
     * through [brobata.physiboard.core.settings.SettingsCodec]'s own dedup/append-missing pass.
     */
    private fun symPagesConfig(stored: brobata.physiboard.core.settings.SymPagesConfig): SymPagesConfig = SymPagesConfig(
        emojiEnabled = stored.emojiEnabled,
        symbolsEnabled = stored.symbolsEnabled,
        clipboardEnabled = stored.clipboardEnabled,
        emojiPickerEnabled = stored.emojiPickerEnabled,
        gifEnabled = stored.gifEnabled,
        custom1Enabled = stored.custom1Enabled,
        custom2Enabled = stored.custom2Enabled,
        custom3Enabled = stored.custom3Enabled,
        fillEnabled = stored.fillEnabled,
        order = stored.order.mapNotNull(::symPageId),
    )

    private fun symPageId(page: brobata.physiboard.core.settings.SymPage): SymPageId? = when (page) {
        brobata.physiboard.core.settings.SymPage.EMOJI -> SymPageId.EMOJI
        brobata.physiboard.core.settings.SymPage.SYMBOLS -> SymPageId.SYMBOLS
        brobata.physiboard.core.settings.SymPage.CLIPBOARD -> SymPageId.CLIPBOARD
        brobata.physiboard.core.settings.SymPage.EMOJI_PICKER -> SymPageId.EMOJI_PICKER
        brobata.physiboard.core.settings.SymPage.GIF -> SymPageId.GIF
        brobata.physiboard.core.settings.SymPage.CUSTOM_1 -> SymPageId.CUSTOM_1
        brobata.physiboard.core.settings.SymPage.CUSTOM_2 -> SymPageId.CUSTOM_2
        brobata.physiboard.core.settings.SymPage.CUSTOM_3 -> SymPageId.CUSTOM_3
        brobata.physiboard.core.settings.SymPage.FILL -> SymPageId.FILL
    }

    /**
     * spec: expansion-clipboard-pickers-launcher.md SS4.7: the default skin tone reaches every
     * emoji a Sym page key, a Sym chord or a Sym long press inserts, and the grid that shows them,
     * by toning the page map once here, when the settings change, rather than on each keystroke.
     * An entry that already carries a tone, or does not take one, is kept as it is.
     */
    private fun toned(page: SymPageMap, tone: SkinTone): SymPageMap {
        if (tone == SkinTone.NONE) return page
        return SymPageMap(
            page.entries.mapValues { (_, entry) ->
                SymPageEntry(SkinTones.withDefault(entry.lowercase, tone), entry.uppercase?.let { SkinTones.withDefault(it, tone) })
            },
        )
    }

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
            // The suggestion row is hidden for good (2026-10-05, the maintainer's call): apps keep
            // the whole screen, autocorrect does the work, and Sym still opens its pages. The
            // stored `status_bar_visibility` is kept for import/export but no longer read.
            visibility = StripVisibilityMode.NEVER,
            apps = bar.apps,
            barHeightDp = bar.heightDp,
            slots = ButtonSlots(left = bar.leftButtons.map(::stripButton), right = bar.rightButtons.map(::stripButton)),
            dipApps = s.perApp.nudgePackages,
            roundedCorners = bar.roundedCornerInsets,
            hideWhereNothingToSuggest = bar.hideWhereNothingToSuggest,
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
     * [RuleSetAssetLoader] with `en`, `it` and `fr`. `es`, `de` and `pl` remain empty per SS8.1
     * ("empty" is itself the spec's own shipped content for those three). `x-pastiera` (2.x's
     * hidden "Recipes" set) is dropped for 3.0 per SS18's "fix or drop" item: [SettingsCodec]
     * already strips it from an imported `auto_correct_enabled_languages` value, so no bundled
     * `x-pastiera` asset ships and no code path re-adds it.
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

    /** spec: trackpad-caret-nav.md SS3.7, the keyboard-surface swipe (a different gesture than [trackpadGesture]'s screen trackpad). */
    fun keyboardSwipeSettings(s: Settings): KeyboardSwipeSettings = KeyboardSwipeSettings(
        gesturesEnabled = s.keyboardSwipe.gesturesEnabled,
        provider = s.keyboardSwipe.provider,
        legacyThresholdPx = s.keyboardSwipe.swipeThresholdPx,
        suggestionThresholdPx = s.keyboardSwipe.suggestionSwipeThresholdPx,
        deleteThresholdPx = s.keyboardSwipe.deleteSwipeThresholdPx,
        swipeToDelete = s.typing.swipeToDelete,
        swipeToDeleteProvider = s.keyboardSwipe.swipeToDeleteProvider,
        addWordEnabled = s.keyboardSwipe.gestureAddWordEnabled,
        addWordFullWidthEnabled = s.keyboardSwipe.gestureAddWordFullWidthEnabled,
    )

    /**
     * spec: dictation.md SS13: the silence limit, the three session switches; SS4.2, SS4.3:
     * masking, automatic punctuation, prefer-offline and the stored `dictation_engine` id the
     * recognizer resolution reads; SS8.1: the cues and their strength. [androidApiLevel] is the
     * caller's `Build.VERSION.SDK_INT`. Private mode is layered on by the caller, which owns it.
     */
    fun dictationSettings(s: Settings, androidApiLevel: Int): DictationSettings = DictationSettings(
        stopAfterSilenceMs = s.dictation.stopAfterSilenceMs.toLong(),
        preferOffline = s.dictation.preferOffline,
        pauseMedia = s.dictation.pauseMedia,
        stopOnTyping = s.dictation.stopOnTyping,
        androidApiLevel = androidApiLevel,
        maskOffensive = s.dictation.maskOffensive,
        autoPunctuation = s.dictation.autoPunctuation,
        hapticsEnabled = s.dictation.haptics,
        hapticStrength = when (s.dictation.hapticStrength) {
            HapticStrength.LIGHT -> CueStrength.LIGHT
            HapticStrength.STANDARD -> CueStrength.STANDARD
            HapticStrength.STRONG -> CueStrength.STRONG
        },
        engineId = s.dictation.engine,
    )

    /** spec: dictation.md SS7.1: dictation capitalises by the same two auto-cap rows as typing; `capitalizationAllowed` stays the field's own answer. */
    fun dictationTextSettings(current: DictationTextSettings, s: Settings, languageTag: String? = null): DictationTextSettings =
        current.copy(
            capitalizeFirstLetter = s.typing.capitalizeAtTextStart,
            capitalizeAfterSentenceEnd = s.typing.capitalizeAfterSentenceEnd,
            // dictation.md SS7.5: German nouns carry their own capital; the engine's cannot be told from it.
            undoEngineSegmentCapitals = DictationTextSettings.undoEngineCapitalsFor(languageTag),
        )

    /** The one bundled language's locale, what `:ime` scores theme overrides and extra-language lookups against until a subtype module exists. */
    const val DEFAULT_SUBTYPE_LOCALE: String = "en"

    /** spec: autocorrect-suggestions.md SS8.1's bundled codes minus `x-pastiera`, the ones the absent-value rule may pick and [RuleSetAssetLoader] tries to load. */
    val BUNDLED_RULE_SET_CODES: Set<String> = setOf("it", "en", "es", "fr", "de", "pl")

    /** spec: autocorrect-suggestions.md SS8.1: `__name` is the display name, never a rule. */
    private const val RESERVED_NAME_KEY = "__name"

    /** spec: dictionaries-languages.md SS8.4, the legacy alias `qwertz` lookups also try. */
    private const val LEGACY_QWERTZ_LAYOUT = "german_multitap_qwertz"
}
