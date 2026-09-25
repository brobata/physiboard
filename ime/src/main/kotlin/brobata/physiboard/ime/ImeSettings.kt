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
import brobata.physiboard.core.keys.LayerResolver
import brobata.physiboard.core.keys.LayoutDescription
import brobata.physiboard.core.keys.LongPressSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadActivationSettings
import brobata.physiboard.core.pointer.trackpad.TrackpadGestureSettings
import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.speech.DictationSettings
import brobata.physiboard.core.speech.DictationTextSettings
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

    fun keyboardSettings(s: Settings): KeyboardSettings {
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
            ),
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

    /** spec: keys-and-modifiers.md SS8.2, SS8.3: the long-press mode and threshold ride on the layout description. */
    fun layout(base: LayoutDescription, s: Settings): LayoutDescription =
        base.copy(longPress = LongPressSettings(mode = s.keys.longPressMode, thresholdMs = s.keys.longPressThresholdMs))

    /** spec: per-app-behavior.md SS4.1: every exact-typing package is a profile with raw mode on. */
    fun appProfiles(s: Settings): List<AppProfile> =
        s.perApp.exactTypingPackages.sorted().map { AppProfile(packageName = it, exactTypingEnabled = true) }

    fun enterOverrides(s: Settings): List<EnterOverride> = s.perApp.enterOverrides.map {
        EnterOverride(packageName = it.packageName, behavior = it.behavior, sendMethod = it.sendMethod, extraSendShortcut = it.extraSendShortcut)
    }

    fun trackpadActivation(s: Settings): TrackpadActivationSettings =
        TrackpadActivationSettings(triggerKey = s.trackpad.triggerKey, activationMode = s.trackpad.activation)

    fun trackpadGesture(s: Settings): TrackpadGestureSettings =
        TrackpadGestureSettings(horizontalStepPx = s.trackpad.stepPx.toFloat())

    /** spec: dictation.md SS6.3, SS13: the pause and the segmented-session switch; [androidApiLevel] is the caller's `Build.VERSION.SDK_INT`. */
    fun dictationSettings(s: Settings, androidApiLevel: Int): DictationSettings =
        DictationSettings(pauseMs = s.dictation.endSilenceMs.toLong(), segmentedSessionEnabled = s.dictation.continuousSession, androidApiLevel = androidApiLevel)

    /** spec: dictation.md SS7.1: dictation capitalises by the same two auto-cap rows as typing; `capitalizationAllowed` stays the field's own answer. */
    fun dictationTextSettings(current: DictationTextSettings, s: Settings): DictationTextSettings =
        current.copy(capitalizeFirstLetter = s.typing.capitalizeAtTextStart, capitalizeAfterSentenceEnd = s.typing.capitalizeAfterSentenceEnd)
}
