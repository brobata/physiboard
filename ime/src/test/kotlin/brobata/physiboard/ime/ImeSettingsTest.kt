package brobata.physiboard.ime

import brobata.physiboard.core.actions.emoji.SkinTone
import brobata.physiboard.core.keys.LongPressMode
import brobata.physiboard.core.pointer.trackpad.ActivationMode
import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.dict.LanguageCode
import brobata.physiboard.core.dict.RuleSet
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.SymPageEntry
import brobata.physiboard.core.keys.SymPageId
import brobata.physiboard.core.keys.SymPagesConfig
import brobata.physiboard.core.settings.AssistantAction
import brobata.physiboard.core.settings.BarButton
import brobata.physiboard.core.settings.CorrectionPrefs
import brobata.physiboard.core.settings.DictationPrefs
import brobata.physiboard.core.settings.EnterOverrideRow
import brobata.physiboard.core.settings.HapticStrength
import brobata.physiboard.core.settings.KeyPrefs
import brobata.physiboard.core.settings.LanguagePrefs
import brobata.physiboard.core.settings.PerAppPrefs
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.StatusBarPrefs
import brobata.physiboard.core.settings.StatusBarVisibility
import brobata.physiboard.core.settings.StripTheme
import brobata.physiboard.core.settings.SubstitutionSet
import brobata.physiboard.core.settings.SymPage
import brobata.physiboard.core.settings.SymPagePrefs
import brobata.physiboard.core.settings.SymPagesConfig as StoredSymPagesConfig
import brobata.physiboard.core.settings.ThemeLayoutOverride
import brobata.physiboard.core.settings.TrackpadPrefs
import brobata.physiboard.core.settings.TypingPrefs
import brobata.physiboard.core.speech.AssistantRequest
import brobata.physiboard.core.speech.CueStrength
import brobata.physiboard.core.speech.DictationTextSettings
import brobata.physiboard.core.strip.StripButton
import brobata.physiboard.core.strip.StripVisibilityMode
import brobata.physiboard.core.text.DashStyle
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.EnterSendMethod
import brobata.physiboard.core.text.ExtraSendShortcut
import brobata.physiboard.core.text.SmartQuoteStyle
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every stored row [ImeSettings] maps lands in the pipeline field it names, and the store's defaults reproduce the shipped [KeyboardSettings]. */
class ImeSettingsTest {

    @Test
    fun `the store's defaults are the shipped KeyboardSettings`() {
        // One difference, and it is a fix: `use_keyboard_proximity` is a single catalogue row
        // (settings-catalog.md SS2.2, baseline true) that feeds both the ranking options and the
        // autocorrect bundle; the shipped constant only ever set the former.
        // The strip is the other difference: `KeyboardSettings()` ships `:core:strip`'s own
        // Slate Dark and first-run slots, while the store's default is the catalogue's baseline
        // hardware theme (settings-catalog.md SS3.1), which is what every Titan actually has.
        // And `ctrl_space_layout_switch`: `:core:keys`'s own bare `ModifierSettings` ships its
        // code default true, while `Settings.kt`'s `LanguagePrefs` now ships every layout-switch
        // chord off by the project's default-ON rule (see its own KDoc) until each survives real
        // use on the maintainer's Titan; `ImeSettings.keyboardSettings` always applies the
        // store's row on top of the bare code default, so this is the one field left disagreeing.
        val shipped = KeyboardSettings()
        val expected = shipped.copy(
            modifier = shipped.modifier.copy(ctrlSpaceLayoutSwitch = false),
            textInput = shipped.textInput.copy(autocorrect = shipped.textInput.autocorrect.copy(useKeyboardProximity = true)),
            statusBar = ImeSettings.stripSettings(Settings()),
        )
        assertEquals(expected, ImeSettings.keyboardSettings(Settings()))
    }

    @Test
    fun `the layout-switch chord rows and nav mode land in the modifier bundle and the pipeline settings`() {
        val on = ImeSettings.keyboardSettings(Settings(languages = LanguagePrefs(altShiftLayoutSwitch = true, altEnterLayoutSwitch = true, ctrlSpaceLayoutSwitch = true)))
        assertTrue(on.modifier.altShiftLayoutSwitch && on.modifier.altEnterLayoutSwitch && on.modifier.ctrlSpaceLayoutSwitch)
        val off = ImeSettings.keyboardSettings(Settings(languages = LanguagePrefs(altShiftLayoutSwitch = false, altEnterLayoutSwitch = false, ctrlSpaceLayoutSwitch = false), keys = KeyPrefs(navModeEnabled = false)))
        assertFalse(off.modifier.altShiftLayoutSwitch || off.modifier.altEnterLayoutSwitch || off.modifier.ctrlSpaceLayoutSwitch)
        assertFalse(off.navModeEnabled)
        assertTrue(ImeSettings.keyboardSettings(Settings()).navModeEnabled)
    }

    @Test
    fun `status bar rows land in the strip settings, and the row stays hidden whatever was stored`() {
        val s = Settings(
            statusBar = StatusBarPrefs(
                visibility = StatusBarVisibility.APPS, apps = setOf("com.whatsapp"), heightDp = 48,
                leftButtons = listOf(BarButton.HAMBURGER, BarButton.UNDO), rightButtons = listOf(BarButton.EMOJI), roundedCornerInsets = false,
            ),
            perApp = PerAppPrefs(nudgePackages = setOf("com.example.chat")),
        )
        val strip = ImeSettings.stripSettings(s)
        assertEquals(StripVisibilityMode.NEVER, strip.visibility, "the suggestion row is hidden for good")
        assertEquals(StripVisibilityMode.NEVER, ImeSettings.stripSettings(Settings(statusBar = StatusBarPrefs(visibility = StatusBarVisibility.ALWAYS))).visibility)
        assertEquals(setOf("com.whatsapp"), strip.apps)
        assertEquals(48, strip.barHeightDp)
        assertEquals(listOf(StripButton.HAMBURGER, StripButton.UNDO), strip.slots.left)
        assertEquals(listOf(StripButton.EMOJI), strip.slots.right)
        assertEquals(setOf("com.example.chat"), strip.dipApps)
        assertFalse(strip.roundedCorners)
        assertTrue(ImeSettings.stripSettings(Settings()).roundedCorners)
        assertEquals(setOf("com.microsoft.teams"), ImeSettings.stripSettings(Settings()).dipApps)
    }

    @Test
    fun `the stored theme reaches the strip field by field, status_bar_button as the button fill and both corner ratios`() {
        val theme = StripTheme(background = 1, suggestion = 2, statusBarButton = 3, accent = 4, textAndIcons = 5, divider = 6, ledInactive = 7, ledActive = 8, ledLocked = 9, keyCornerRadiusRatio = 0.25, chromeCornerRadiusRatio = 0.5, suggestionsHeightScale = 1.1, showLeds = true)
        val t = ImeSettings.stripSettings(Settings(statusBar = StatusBarPrefs(theme = theme))).theme
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9), listOf(t.background, t.suggestion, t.button, t.accent, t.textAndIcons, t.divider, t.ledInactive, t.ledActive, t.ledLocked))
        assertEquals(0.25, t.keyCornerRatio)
        assertEquals(0.5, t.chromeCornerRatio)
        assertEquals(1.1, t.suggestionsHeightScale)
        assertTrue(t.showLeds)
    }

    @Test
    fun `a per-layout theme override beats the chosen theme when keyboard_layout matches it`() {
        val override = ThemeLayoutOverride(layout = "qwertz", theme = StripTheme(accent = 0x11223344))
        val base = Settings(statusBar = StatusBarPrefs(theme = StripTheme(accent = 0x55667788), layoutOverrides = listOf(override)))
        assertEquals(0x55667788, ImeSettings.stripSettings(base).theme.accent)
        assertEquals(0x11223344, ImeSettings.stripSettings(base.copy(languages = LanguagePrefs(keyboardLayout = "qwertz"))).theme.accent)
    }

    @Test
    fun `a custom Sym page replaces the shipped page entirely and an empty or unusable one leaves it alone`() {
        val shipped = TitanLayouts.titan2EliteQwerty()
        val custom = ImeSettings.layout(shipped, Settings(symPages = SymPagePrefs(customEmojiPage = mapOf("KEYCODE_Q" to "🦊", "KEYCODE_1" to "x", "KEYCODE_z" to "🐙"))))
        assertEquals(SymPageEntry("🦊"), custom.emojiPage[KeyId.Letter('Q')])
        assertEquals(SymPageEntry("🐙"), custom.emojiPage[KeyId.Letter('Z')])
        assertEquals(2, custom.emojiPage.entries.size)
        assertEquals(shipped.symbolsPage, custom.symbolsPage)
        val symbols = ImeSettings.layout(shipped, Settings(symPages = SymPagePrefs(customSymbolsPage = mapOf("KEYCODE_A" to "§"))))
        assertEquals(SymPageEntry("§"), symbols.symbolsPage[KeyId.Letter('A')])
        assertEquals(shipped.emojiPage, symbols.emojiPage)
        val unusable = ImeSettings.layout(shipped, Settings(symPages = SymPagePrefs(customEmojiPage = mapOf("KEYCODE_1" to "x", "KEYCODE_Q" to ""))))
        assertEquals(shipped.emojiPage, unusable.emojiPage)
    }

    @Test
    fun `the default skin tone tones the Sym pages' emoji that take one and leaves the rest`() {
        val shipped = TitanLayouts.titan2EliteQwerty()
        val toned = ImeSettings.layout(shipped, Settings(symPages = SymPagePrefs(defaultSkinTone = SkinTone.MEDIUM)))
        assertEquals("👍", shipped.emojiPage[KeyId.Letter('Y')]?.lowercase)
        assertEquals(SymPageEntry("👍🏽"), toned.emojiPage[KeyId.Letter('Y')])
        assertEquals(shipped.emojiPage[KeyId.Letter('U')], toned.emojiPage[KeyId.Letter('U')])
        assertEquals(shipped.emojiPage[KeyId.Letter('Q')], toned.emojiPage[KeyId.Letter('Q')])
        assertEquals(shipped.symbolsPage, toned.symbolsPage)
        // A tone the user put on a custom key is theirs.
        val custom = ImeSettings.layout(shipped, Settings(symPages = SymPagePrefs(customEmojiPage = mapOf("KEYCODE_A" to "👋🏻", "KEYCODE_B" to "🧑‍🤝‍🧑"), defaultSkinTone = SkinTone.DARK)))
        assertEquals(SymPageEntry("👋🏻"), custom.emojiPage[KeyId.Letter('A')])
        assertEquals(SymPageEntry("🧑🏿‍🤝‍🧑🏿"), custom.emojiPage[KeyId.Letter('B')])
        assertEquals(shipped.emojiPage, ImeSettings.layout(shipped, Settings()).emojiPage)
    }

    @Test
    fun `sym_pages_config's enabled flags and cycle order overlay the shipped default`() {
        val shipped = TitanLayouts.titan2EliteQwerty()
        val stored = StoredSymPagesConfig(
            emojiEnabled = true,
            symbolsEnabled = false,
            clipboardEnabled = true,
            emojiPickerEnabled = false,
            order = listOf(SymPage.CLIPBOARD, SymPage.EMOJI, SymPage.SYMBOLS, SymPage.EMOJI_PICKER),
        )
        val layout = ImeSettings.layout(shipped, Settings(symPages = SymPagePrefs(pages = stored)))
        assertEquals(
            SymPagesConfig(
                emojiEnabled = true,
                symbolsEnabled = false,
                clipboardEnabled = true,
                emojiPickerEnabled = false,
                order = listOf(SymPageId.CLIPBOARD, SymPageId.EMOJI, SymPageId.SYMBOLS, SymPageId.EMOJI_PICKER),
            ),
            layout.symPagesConfig,
        )
        assertFalse(layout.symPagesConfig == shipped.symPagesConfig)
    }

    @Test
    fun `the GIF page's switch and place reach the cycle, and sym_double_tap_chooser reaches the modifier bundle`() {
        val stored = StoredSymPagesConfig(gifEnabled = true, order = listOf(SymPage.GIF, SymPage.EMOJI_PICKER, SymPage.SYMBOLS, SymPage.CLIPBOARD, SymPage.EMOJI))
        val config = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), Settings(symPages = SymPagePrefs(pages = stored))).symPagesConfig
        assertTrue(config.gifEnabled)
        assertEquals(listOf(0, 6, 4, 2), config.cycle)
        assertTrue(ImeSettings.keyboardSettings(Settings()).modifier.symDoubleTapChooser)
        assertFalse(ImeSettings.keyboardSettings(Settings(symPages = SymPagePrefs(doubleTapChooser = false))).modifier.symDoubleTapChooser)
    }

    @Test
    fun `rule sets follow auto_correct_enabled_languages with the user's substitutions overlaid, __name stripped`() {
        val bundled = mapOf("en" to RuleSet("en", null, mapOf("dont" to "don't")))
        val s = Settings(
            correction = CorrectionPrefs(
                textReplacementLanguages = listOf("en", "xx"),
                customSubstitutions = mapOf(
                    "en" to SubstitutionSet(rules = mapOf("teh" to "the", "__name" to "English")),
                    "xx" to SubstitutionSet(displayName = "Recipes", rules = mapOf("tbsp" to "tablespoon")),
                    "fr" to SubstitutionSet(rules = mapOf("etre" to "être")),
                ),
            ),
        )
        val sets = ImeSettings.ruleSets(s, systemLanguage = "de", bundled = bundled)
        assertEquals(listOf("en", "xx"), sets.map { it.code })
        assertEquals(mapOf("dont" to "don't", "teh" to "the"), sets[0].rules)
        assertEquals("Recipes", sets[1].displayName)
        assertEquals(mapOf("tbsp" to "tablespoon"), sets[1].rules)
    }

    @Test
    fun `with no chosen languages the system language is searched if bundled, otherwise en`() {
        val bundled = mapOf("en" to RuleSet("en", null, mapOf("dont" to "don't")), "fr" to RuleSet("fr", null, mapOf("etre" to "être")))
        assertEquals(listOf("fr"), ImeSettings.ruleSets(Settings(), systemLanguage = "fr", bundled = bundled).map { it.code })
        assertEquals(listOf("en"), ImeSettings.ruleSets(Settings(), systemLanguage = "ja", bundled = bundled).map { it.code })
        val custom = Settings(correction = CorrectionPrefs(customSubstitutions = mapOf("en" to SubstitutionSet(rules = mapOf("teh" to "the")))))
        assertEquals(mapOf("teh" to "the"), ImeSettings.ruleSets(custom, systemLanguage = "ja").single().rules)
        assertTrue(ImeSettings.ruleSets(Settings(), systemLanguage = "ja").isEmpty())
    }

    @Test
    fun `T27 - extra suggestion languages are found by the language-only key, deduplicated, the primary and x-pastiera dropped`() {
        val s = Settings(languages = LanguagePrefs(keyboardLayout = "qwertz", suggestionLocales = mapOf("de:qwertz" to listOf("fr-FR", "en_US", "fr-FR", "x-pastiera", "de"))))
        val de = LanguageCode.of("de")!!
        assertEquals(listOf("fr", "en"), ImeSettings.extraSuggestionLanguages(s, de, "de_DE").map { it.value })
        assertTrue(ImeSettings.extraSuggestionLanguages(s, LanguageCode.of("en")!!, "en_US").isEmpty())
        assertTrue(ImeSettings.extraSuggestionLanguages(s.copy(languages = s.languages.copy(keyboardLayout = "qwerty")), de, "de_DE").isEmpty())
        val exact = Settings(languages = LanguagePrefs(keyboardLayout = "qwerty", suggestionLocales = mapOf("en-GB:qwerty" to listOf("it"), "en:qwerty" to listOf("es"))))
        assertEquals(listOf("it"), ImeSettings.extraSuggestionLanguages(exact, LanguageCode.of("en")!!, "en_GB").map { it.value })
        assertEquals(listOf("es"), ImeSettings.extraSuggestionLanguages(exact, LanguageCode.of("en")!!, "en_US").map { it.value })
    }

    @Test
    fun `the caret badge rows, the trackpad hint and the assistant action reach their bundles`() {
        val s = Settings(statusBar = StatusBarPrefs(caretModifierBadge = false, caretBadgeArmedColor = 0x11, caretBadgeLockedColor = 0x22), trackpad = TrackpadPrefs(showHint = false), dictation = DictationPrefs(assistantAction = AssistantAction.HANDS_FREE))
        val badge = ImeSettings.caretBadge(s)
        assertFalse(badge.enabled)
        assertEquals(0x11, badge.armedColorArgb)
        assertEquals(0x22, badge.lockedColorArgb)
        assertTrue(ImeSettings.caretBadge(Settings()).enabled)
        assertFalse(ImeSettings.trackpadGesture(s).showHint)
        assertTrue(ImeSettings.trackpadGesture(Settings()).showHint)
        assertEquals(AssistantRequest.HANDS_FREE, ImeSettings.assistantRequest(s))
        assertEquals(null, ImeSettings.assistantRequest(Settings()))
    }

    @Test
    fun `masking, automatic punctuation and the cues reach the dictation bundle`() {
        val d = ImeSettings.dictationSettings(Settings(dictation = DictationPrefs(maskOffensive = true, autoPunctuation = false, haptics = false, hapticStrength = HapticStrength.LIGHT)), androidApiLevel = 33)
        assertTrue(d.maskOffensive)
        assertFalse(d.autoPunctuation)
        assertFalse(d.hapticsEnabled)
        assertEquals(CueStrength.LIGHT, d.hapticStrength)
        val defaults = ImeSettings.dictationSettings(Settings(), androidApiLevel = 33)
        assertFalse(defaults.maskOffensive)
        assertTrue(defaults.autoPunctuation && defaults.hapticsEnabled)
        assertEquals(CueStrength.STRONG, defaults.hapticStrength)
    }

    @Test
    fun `the stored speech engine id reaches the dictation bundle`() {
        assertEquals("", ImeSettings.dictationSettings(Settings(), androidApiLevel = 33).engineId)
        val chosen = ImeSettings.dictationSettings(Settings(dictation = DictationPrefs(engine = "com.example/.Service")), androidApiLevel = 33)
        assertEquals("com.example/.Service", chosen.engineId)
    }

    @Test
    fun `the trackpad gate follows the store and is off by default`() {
        assertFalse(ImeSettings.keyboardSettings(Settings()).screenTrackpadEnabled)
        assertTrue(ImeSettings.keyboardSettings(Settings(trackpad = TrackpadPrefs(enabled = true))).screenTrackpadEnabled)
    }

    @Test
    fun `typing rows land in the auto-cap, spacing, backspace and resolver bundles`() {
        val s = Settings(
            typing = TypingPrefs(
                capitalizeAtTextStart = false, capitalizeAfterSentenceEnd = false, capitalizeRestrictedFields = true,
                doubleSpaceToPeriod = false, clearAltOnSpace = false, shiftBackspaceDeletesForward = true, altBackspaceDeletesForward = true,
                backspaceAtStartDeletesForward = true, removeSpaceBefore = ".,", spaceBeforeNextText = "?!", commaSpace = true,
                spacedHyphenToDash = true, dashStyle = DashStyle.EM_DASH, smartQuotes = true, smartQuoteStyle = SmartQuoteStyle.ENGLISH_CURLY,
                frenchPunctuationSpacing = true, frenchPunctuationOnlyFrench = true, swipeToDelete = true,
            ),
        )
        val k = ImeSettings.keyboardSettings(s)
        assertFalse(k.textInput.autoCap.capitalizeAtTextStart)
        assertFalse(k.textInput.autoCap.capitalizeAfterSentenceEnd)
        assertTrue(k.textInput.autoCap.capitalizeRestrictedFields)
        assertFalse(k.textInput.spacing.doubleSpaceToPeriod)
        assertEquals(".,", k.textInput.spacing.removeBeforeList)
        assertEquals("?!", k.textInput.spacing.beforeNextTextList)
        assertTrue(k.textInput.spacing.commaSpace && k.textInput.spacing.spacedHyphenToDash && k.textInput.spacing.smartQuotes)
        assertEquals(DashStyle.EM_DASH, k.textInput.spacing.dashStyle)
        assertEquals(SmartQuoteStyle.ENGLISH_CURLY, k.textInput.spacing.smartQuoteStyle)
        assertTrue(k.textInput.spacing.frenchPunctuationSpacing && k.textInput.spacing.frenchPunctuationOnlyFrench)
        assertTrue(k.textInput.backspace.shiftBackspaceDeletesForward && k.textInput.backspace.altBackspaceDeletesForward && k.textInput.backspace.backspaceAtStartDeletesForward)
        assertTrue(k.resolver.shiftBackspaceDelete && k.resolver.altBackspaceDelete && k.resolver.backspaceAtStartDelete && k.resolver.swipeToDeleteEnabled)
        assertFalse(k.modifier.clearAltOnSpace)
    }

    @Test
    fun `correction rows land in the autocorrect and ranking bundles`() {
        val s = Settings(correction = CorrectionPrefs(textReplacementsEnabled = false, autoReplaceOnSpaceEnter = false, maxAutoReplaceDistance = 3, suggestionsEnabled = false, accentMatching = false, useKeyboardProximity = false, fixWordMixups = true))
        val k = ImeSettings.keyboardSettings(s)
        assertFalse(k.textInput.autocorrect.autoCorrectEnabled)
        assertFalse(k.textInput.autocorrect.autoReplaceOnSpaceEnter)
        assertEquals(3, k.textInput.autocorrect.maxAutoReplaceDistance)
        assertFalse(k.textInput.autocorrect.suggestionsEnabled)
        assertFalse(k.textInput.autocorrect.accentMatchingEnabled)
        assertFalse(k.textInput.autocorrect.useKeyboardProximity)
        assertTrue(k.textInput.autocorrect.fixWordMixups)
        assertFalse(k.textInput.rankingOptions.useKeyboardProximity)
        assertFalse(k.textInput.rankingOptions.accentMatchingEnabled)
    }

    @Test
    fun `key, dictation and Sym rows land in the modifier bundle`() {
        val s = Settings(
            keys = KeyPrefs(navModeCtrlHoldEnabled = true, layoutAwareCtrlShortcuts = true, symEditShortcuts = false),
            dictation = DictationPrefs(fnLongPressSpeech = false, symLongPressAssistant = true),
            symPages = SymPagePrefs(autoClose = false),
            trackpad = TrackpadPrefs(enabled = true, triggerKey = TriggerKey.SYM),
        )
        val m = ImeSettings.keyboardSettings(s).modifier
        assertTrue(m.navModeCtrlHoldEnabled && m.layoutAwareCtrlShortcuts && m.symLongPressAssistantEnabled)
        assertFalse(m.symEditShortcutsEnabled)
        assertFalse(m.fnLongPressSpeechEnabled)
        assertFalse(m.symAutoCloseEnabled)
        assertTrue(m.symIsTrackpadTrigger)
        assertFalse(ImeSettings.keyboardSettings(s.copy(trackpad = TrackpadPrefs(enabled = false, triggerKey = TriggerKey.SYM))).modifier.symIsTrackpadTrigger)
    }

    /** spec: keys-and-modifiers.md SS10, SS11, SS18: the bounce and overlap filter rows, previously stored but read by nothing. */
    @Test
    fun `the bounce and overlap filter rows land in their own bundles`() {
        val s = Settings(
            keys = KeyPrefs(
                bounceKeysEnabled = true, bounceKeysDelayMs = 120, bounceKeysCharacterKeysEnabled = false,
                bounceKeysModifierKeysEnabled = true, bounceKeysSpaceEnabled = false, bounceKeysEnterEnabled = false,
                bounceKeysBackspaceEnabled = false, overlappingKeysEnabled = true,
            ),
        )
        val k = ImeSettings.keyboardSettings(s)
        assertTrue(k.bounceKeys.enabled)
        assertEquals(120L, k.bounceKeys.delayMs)
        assertFalse(k.bounceKeys.characterKeysEnabled)
        assertTrue(k.bounceKeys.modifierKeysEnabled)
        assertFalse(k.bounceKeys.spaceEnabled)
        assertFalse(k.bounceKeys.enterEnabled)
        assertFalse(k.bounceKeys.backspaceEnabled)
        assertTrue(k.overlappingKeys.enabled)
    }

    @Test
    fun `the long-press mode and threshold ride on the layout`() {
        val layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), Settings(keys = KeyPrefs(longPressMode = LongPressMode.VARIATIONS, longPressThresholdMs = 700)))
        assertEquals(LongPressMode.VARIATIONS, layout.longPress.mode)
        assertEquals(700, layout.longPress.thresholdMs)
    }

    /** spec: trackpad-caret-nav.md SS5.4, SS5.9: a loaded Fn Layer map overrides the shipped default; with none loaded, the base layout's own default survives. */
    @Test
    fun `a loaded ctrl mapping table overrides the layout's shipped Fn Layer map`() {
        val base = TitanLayouts.titan2EliteQwerty()
        val custom = brobata.physiboard.core.keys.CtrlMappingTable(
            mapOf(brobata.physiboard.core.keys.KeyId.Letter('Q') to brobata.physiboard.core.keys.CtrlMapping.NamedAction("copy")),
        )
        val withOverride = ImeSettings.layout(base, Settings(), ctrlMappings = custom)
        assertEquals(custom, withOverride.ctrlMappings)
        val withoutOverride = ImeSettings.layout(base, Settings())
        assertEquals(base.ctrlMappings, withoutOverride.ctrlMappings)
    }

    @Test
    fun `exact-typing packages become raw-mode profiles and Enter rows become overrides`() {
        val s = Settings(
            perApp = PerAppPrefs(
                exactTypingPackages = setOf("com.termux", "org.chromium.webapk.x"),
                enterOverrides = listOf(EnterOverrideRow("com.discord", EnterBehavior.NEWLINE_CTRL_SEND, EnterSendMethod.PLAIN_ENTER, ExtraSendShortcut.SYM_ENTER)),
            ),
        )
        val profiles = ImeSettings.appProfiles(s)
        assertEquals(listOf("com.termux", "org.chromium.webapk.x"), profiles.map { it.packageName })
        assertTrue(profiles.all { it.exactTypingEnabled })
        val overrides = ImeSettings.enterOverrides(s)
        assertEquals(1, overrides.size)
        assertEquals("com.discord", overrides[0].packageName)
        assertEquals(EnterBehavior.NEWLINE_CTRL_SEND, overrides[0].behavior)
        assertEquals(EnterSendMethod.PLAIN_ENTER, overrides[0].sendMethod)
        assertEquals(ExtraSendShortcut.SYM_ENTER, overrides[0].extraSendShortcut)
    }

    @Test
    fun `trackpad tuning lands in the activation and gesture bundles`() {
        val s = Settings(trackpad = TrackpadPrefs(triggerKey = TriggerKey.SHIFT_EITHER, activation = ActivationMode.DOUBLE_TAP, stepPx = 48))
        assertEquals(TriggerKey.SHIFT_EITHER, ImeSettings.trackpadActivation(s).triggerKey)
        assertEquals(ActivationMode.DOUBLE_TAP, ImeSettings.trackpadActivation(s).activationMode)
        assertEquals(48f, ImeSettings.trackpadGesture(s).horizontalStepPx)
    }

    @Test
    fun `dictation rows land in the session and text bundles without touching the field's own answer`() {
        val s = Settings(dictation = DictationPrefs(stopAfterSilenceMs = 15000, preferOffline = false, pauseMedia = false, stopOnTyping = false), typing = TypingPrefs(capitalizeAtTextStart = false))
        val d = ImeSettings.dictationSettings(s, androidApiLevel = 34)
        assertEquals(15000L, d.stopAfterSilenceMs)
        assertFalse(d.preferOffline)
        assertFalse(d.pauseMedia)
        assertFalse(d.stopOnTyping)
        assertFalse(d.privateMode, "layered on by the session, not the store mapping")
        assertEquals(34, d.androidApiLevel)
        val t = ImeSettings.dictationTextSettings(DictationTextSettings(capitalizationAllowed = false), s)
        assertFalse(t.capitalizeFirstLetter)
        assertTrue(t.capitalizeAfterSentenceEnd)
        assertFalse(t.capitalizationAllowed)
    }
}
