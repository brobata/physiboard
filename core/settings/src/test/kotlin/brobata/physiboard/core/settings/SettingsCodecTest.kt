package brobata.physiboard.core.settings

import brobata.physiboard.core.actions.feedback.TypingSoundMode
import brobata.physiboard.core.keys.LongPressMode
import brobata.physiboard.core.pointer.keyboardswipe.SwipeToDeleteProvider
import brobata.physiboard.core.pointer.keyboardswipe.TrackpadGestureProvider
import brobata.physiboard.core.pointer.trackpad.ActivationMode
import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.text.DashStyle
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.EnterSendMethod
import brobata.physiboard.core.text.ExtraSendShortcut
import brobata.physiboard.core.text.MessagingPreset
import brobata.physiboard.core.text.SmartQuoteStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsCodecTest {

    /** Every field set to something other than its default, so a field missing from either direction of the codec fails the round trip. */
    private fun everythingChanged(): Settings = Settings(
        typing = TypingPrefs(
            capitalizeAtTextStart = false, capitalizeAfterSentenceEnd = false, capitalizeRestrictedFields = true,
            doubleSpaceToPeriod = false, clearAltOnSpace = false, shiftBackspaceDeletesForward = true,
            altBackspaceDeletesForward = true, backspaceAtStartDeletesForward = true, removeSpaceBefore = ".,?",
            spaceBeforeNextText = "!?", commaSpace = true, spacedHyphenToDash = true, dashStyle = DashStyle.EM_DASH,
            smartQuotes = true, smartQuoteStyle = SmartQuoteStyle.ENGLISH_CURLY, frenchPunctuationSpacing = true,
            frenchPunctuationOnlyFrench = true, swipeToDelete = true,
        ),
        correction = CorrectionPrefs(
            textReplacementsEnabled = false, textReplacementLanguages = listOf("en", "fr"),
            customSubstitutions = mapOf("en" to SubstitutionSet("English", mapOf("teh" to "the", "adn" to "and"))),
            autoReplaceOnSpaceEnter = false, maxAutoReplaceDistance = 3, suggestionsEnabled = false,
            accentMatching = false, useKeyboardProximity = false, fixWordMixups = true,
        ),
        languages = LanguagePrefs(
            keyboardLayout = "qwertz", layoutAutoByLocale = false, altShiftLayoutSwitch = false, altEnterLayoutSwitch = true,
            ctrlSpaceLayoutSwitch = false, toastOnLayoutSwitch = false, inputStyles = listOf("de_DE:qwertz", "en_US:qwerty"),
            suggestionLocales = mapOf("de-DE:qwertz" to listOf("en-US")), hiddenSystemInputStyles = listOf("it_IT:qwerty"),
            appLanguageTag = "de",
        ),
        keys = KeyPrefs(
            longPressMode = LongPressMode.VARIATIONS, longPressThresholdMs = 700,
            variationChooser = false, customVariations = mapOf("a" to listOf("ą", "à"), "E" to emptyList()), navModeEnabled = false,
            navModeCtrlHoldEnabled = true, layoutAwareCtrlShortcuts = true, symEditShortcuts = false,
            navModeMappingsUpdatedAtMs = 1700000000000L, navModeDefaultMappingsVersion = 3, bounceKeysEnabled = true, bounceKeysDelayMs = 120,
            bounceKeysCharacterKeysEnabled = false, bounceKeysModifierKeysEnabled = true, bounceKeysSpaceEnabled = false,
            bounceKeysEnterEnabled = false, bounceKeysBackspaceEnabled = false, overlappingKeysEnabled = true,
        ),
        symPages = SymPagePrefs(
            pages = SymPagesConfig(emojiEnabled = true, symbolsEnabled = false, clipboardEnabled = true, emojiPickerEnabled = false, gifEnabled = true,
                custom2Enabled = true,
                order = listOf(SymPage.EMOJI, SymPage.CUSTOM_2, SymPage.GIF, SymPage.CLIPBOARD, SymPage.SYMBOLS, SymPage.EMOJI_PICKER, SymPage.CUSTOM_3, SymPage.CUSTOM_1, SymPage.FILL), fillEnabled = false),
            customEmojiPage = mapOf("KEYCODE_Q" to "😀"), customSymbolsPage = mapOf("KEYCODE_W" to "€"),
            customPages = listOf(CustomSymPage(), CustomSymPage("Polski", mapOf("KEYCODE_A" to "ą", "KEYCODE_S" to "你好")), CustomSymPage()),
            autoClose = false, autoCloseOnTouch = false, emojiPickerExpandedHeight = true, doubleTapChooser = false,
            defaultSkinTone = brobata.physiboard.core.actions.emoji.SkinTone.MEDIUM_DARK,
            restoreSymPage = 2, pendingRestoreSymPage = 1,
        ),
        statusBar = StatusBarPrefs(
            visibility = StatusBarVisibility.APPS, apps = setOf("com.example.a", "com.example.b"), heightDp = 64,
            leftButtons = listOf(BarButton.HAMBURGER, BarButton.UNDO), rightButtons = listOf(BarButton.EMOJI, BarButton.MICROPHONE),
            caretModifierBadge = false, caretBadgeArmedColor = 0x11223344, caretBadgeLockedColor = 0x55667788,
            theme = StripTheme.SLATE_DARK.copy(showLeds = true),
            savedThemes = listOf(NamedTheme("Mine", StripTheme.SLATE_DARK.copy(accent = 1))),
            roundedCornerInsets = false,
            accessibilityLiveAnnouncementsEnabled = true, accessibilitySuggestionsAnnouncementDelayMs = 750,
            overlayDebugLoggingEnabled = true,
        ),
        perApp = PerAppPrefs(
            exactTypingPackages = setOf("com.termux"), nudgePackages = setOf("com.example.nudge"), enterBehaviorEnabled = false,
            enterPreset = MessagingPreset.NEWLINE_CTRL_SEND,
            enterOverrides = listOf(EnterOverrideRow("com.example.chat", EnterBehavior.NEWLINE_CTRL_SEND, EnterSendMethod.CTRL_ENTER, ExtraSendShortcut.SYM_ENTER)),
        ),
        dictation = DictationPrefs(
            fnLongPressSpeech = false, haptics = false, hapticStrength = HapticStrength.LIGHT, stopAfterSilenceMs = 15000,
            maskOffensive = true, engine = "ondevice", autoPunctuation = false, preferOffline = false, pauseMedia = false, stopOnTyping = false,
            symLongPressAssistant = true, sideKeyAssistant = true, assistantAction = AssistantAction.HANDS_FREE,
        ),
        trackpad = TrackpadPrefs(enabled = true, triggerKey = TriggerKey.SHIFT_EITHER, activation = ActivationMode.DOUBLE_TAP, stepPx = 48, showHint = false),
        keyboardSwipe = KeyboardSwipePrefs(
            gesturesEnabled = true, provider = TrackpadGestureProvider.SHIZUKU, swipeThresholdPx = 300f,
            suggestionSwipeThresholdPx = 200f, deleteSwipeThresholdPx = 250f, gestureAddWordEnabled = false,
            gestureAddWordFullWidthEnabled = false, swipeToDeleteProvider = SwipeToDeleteProvider.TITAN2_KEYCODE,
        ),
        device = DevicePrefs(
            smartBacklightEnabled = false, ringEnabled = false, ringMinutes = 30, ringBrightness = RingBrightness.BRIGHT,
            ringShowIcons = true, ringKeyboardDark = false, ringDefaultColor = 0xFF123456.toInt(),
            ringAppColors = mapOf("com.example.a" to 0xFF00FF00.toInt()), ringFit = RingFit(1f, 2f, 3f, 4f),
        ),
        expansion = ExpansionPrefs(
            snippetsEnabled = true, snippetPrefix = "#", snippets = mapOf("sig" to "Best regards"), presentation = SnippetPresentation.SUGGESTION_BAR,
            expandExactOnSpace = false, acceptPrefixWithSpace = true, acceptWithTab = false, acceptWithEnter = true,
            clipboardHistoryEnabled = false, clipboardRetentionMinutes = 60,
        ),
        launcher = LauncherPrefs(
            behavior = LauncherBehavior.NIAGARA, openUniqueMatch = true, limitResults = true, respectKeyboardLayout = false,
            typoTolerantRanking = false, symShortcutsEnabled = false, homeScreenShortcutsEnabled = true,
            assignedKeysJson = """{"62":{"type":"quick_launcher"}}""", commandCustomizationsJson = """{"app:x":{"favorite":true}}""",
            quickLauncherStaticTopHighlight = true, quickLauncherStaticTopHighlightColor = 0x7A34A853,
        ),
        feedback = FeedbackPrefs(
            tapHapticUseSystem = false, tapHapticDurationMs = 40,
            typingSoundMode = TypingSoundMode.TYPEWRITER, typingSoundOutputMode = TypingSoundOutputMode.NOTIFICATION,
        ),
        privacy = PrivacyPrefs(privateMode = true, cleanLinks = false),
        shell = ShellState(tutorialCompleted = true, lastSeenWhatsNewVersion = "3.0.0", dismissedReleases = listOf("v3.0.1", "v3.0.2"), untestedDeviceNoticeSeen = true),
        captures = DeviceCaptures(
            fnCtrlPrevCaptured = true, fnCtrlPrevEnable = 1, fnCtrlPrevFunction = 7, sideKeyOriginalCaptured = true,
            sideKeyOriginalPackage = "com.example.assistant", sideKeyOriginalActivity = "com.example.assistant.Main",
            qsBacklightPrevCaptured = true, qsBacklightPrev = 0, ringBacklightPrevCaptured = true, ringBacklightPrev = 1, smartBacklightApplied = true,
        ),
    )

    @Test
    fun `defaults round-trip through the flat map`() {
        val d = Settings()
        assertEquals(d, SettingsCodec.fromMap(SettingsCodec.toMap(d)))
    }

    @Test
    fun `every field round-trips through the flat map`() {
        val s = everythingChanged()
        assertNotEquals(Settings(), s)
        assertEquals(s, SettingsCodec.fromMap(SettingsCodec.toMap(s)))
    }

    @Test
    fun `the map carries the schema version`() {
        val map = SettingsCodec.toMap(Settings())
        assertEquals(Settings.SCHEMA_VERSION, SettingsCodec.schemaVersionOf(map))
        assertNull(SettingsCodec.schemaVersionOf(emptyMap()))
    }

    @Test
    fun `an empty map is the defaults`() {
        assertEquals(Settings(), SettingsCodec.fromMap(emptyMap()))
    }

    @Test
    fun `unknown keys are ignored`() {
        assertEquals(Settings(), SettingsCodec.fromMap(mapOf("future_row" to "1", "clicks_button_mode" to "x")))
    }

    @Test
    fun `malformed values read as the field default`() {
        val s = SettingsCodec.fromMap(
            mapOf(
                SettingsKeys.AUTO_CAP_FIRST to "yes",
                SettingsKeys.STATUS_BAR_HEIGHT to "tall",
                SettingsKeys.THEME to "{not json",
                SettingsKeys.ENTER_OVERRIDES to "42",
                SettingsKeys.SYM_PAGES_CONFIG to "[]",
                SettingsKeys.RING_APP_COLORS to "null",
                SettingsKeys.STATUS_BAR_APPS to "[1, 2",
            ),
        )
        assertEquals(Settings(), s)
    }

    @Test
    fun `ranges are clamped on read, spec SS12 test 32`() {
        val s = SettingsCodec.fromMap(
            mapOf(
                SettingsKeys.TRACKPAD_STEP to "200",
                SettingsKeys.DICTATION_STOP_AFTER_SILENCE to "90000",
                SettingsKeys.RING_MINUTES to "2",
                SettingsKeys.MAX_AUTO_REPLACE_DISTANCE to "9",
                SettingsKeys.LONG_PRESS_THRESHOLD to "10",
                SettingsKeys.TAP_HAPTIC_DURATION to "500",
            ),
        )
        assertEquals(64, s.trackpad.stepPx)
        assertEquals(60000, s.dictation.stopAfterSilenceMs)
        assertEquals(2, s.device.ringMinutes)
        assertEquals(3, s.correction.maxAutoReplaceDistance)
        assertEquals(50, s.keys.longPressThresholdMs)
        assertEquals(80, s.feedback.tapHapticDurationMs)
    }

    @Test
    fun `enum rows fall back the way the catalogue says, spec SS12 test 31`() {
        val s = SettingsCodec.fromMap(
            mapOf(
                SettingsKeys.DASH_STYLE to "dash",
                SettingsKeys.SMART_QUOTES_STYLE to "welsh",
                SettingsKeys.TRACKPAD_TRIGGER to "bogus",
                SettingsKeys.TRACKPAD_ACTIVATION to "bogus",
                SettingsKeys.LONG_PRESS_MODE to "nope",
                SettingsKeys.RING_BRIGHTNESS to "blinding",
                SettingsKeys.SNIPPETS_PRESENTATION to "toast",
                SettingsKeys.SNIPPETS_PREFIX to "ab",
                SettingsKeys.ENTER_PRESET to "enter_newline_only",
            ),
        )
        assertEquals(DashStyle.EN_DASH, s.typing.dashStyle)
        assertEquals(SmartQuoteStyle.GERMAN_GUILLEMETS, s.typing.smartQuoteStyle)
        assertEquals(TriggerKey.SPACE, s.trackpad.triggerKey)
        assertEquals(ActivationMode.HOLD, s.trackpad.activation)
        assertEquals(LongPressMode.ALT, s.keys.longPressMode)
        assertEquals(RingBrightness.NORMAL, s.device.ringBrightness)
        assertEquals(SnippetPresentation.FLOATING_POPUP, s.expansion.presentation)
        assertEquals("!", s.expansion.snippetPrefix)
        assertEquals(MessagingPreset.APP_DEFAULT, s.perApp.enterPreset)
    }

    @Test
    fun `keyboard swipe provider fields fall back on an unknown value, spec trackpad SS3_7`() {
        val s = SettingsCodec.fromMap(
            mapOf(SettingsKeys.KEYBOARD_SWIPE_PROVIDER to "bogus", SettingsKeys.SWIPE_TO_DELETE_PROVIDER to "bogus"),
        )
        assertEquals(TrackpadGestureProvider.NATIVE_IME, s.keyboardSwipe.provider)
        assertEquals(SwipeToDeleteProvider.NATIVE_IME, s.keyboardSwipe.swipeToDeleteProvider)
    }

    @Test
    fun `an unset suggestion or delete swipe threshold falls back to the legacy value, spec trackpad SS3_7`() {
        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.KEYBOARD_SWIPE_THRESHOLD to "300"))
        assertEquals(300f, s.keyboardSwipe.swipeThresholdPx)
        assertNull(s.keyboardSwipe.suggestionSwipeThresholdPx)
        assertNull(s.keyboardSwipe.deleteSwipeThresholdPx)
    }

    @Test
    fun `keyboard swipe thresholds are clamped 120 to 750 on read, spec trackpad SS3_7`() {
        val s = SettingsCodec.fromMap(
            mapOf(
                SettingsKeys.KEYBOARD_SWIPE_THRESHOLD to "10000",
                SettingsKeys.KEYBOARD_SWIPE_SUGGESTION_THRESHOLD to "1",
                SettingsKeys.KEYBOARD_SWIPE_DELETE_THRESHOLD to "1",
            ),
        )
        assertEquals(750f, s.keyboardSwipe.swipeThresholdPx)
        assertEquals(120f, s.keyboardSwipe.suggestionSwipeThresholdPx)
        assertEquals(120f, s.keyboardSwipe.deleteSwipeThresholdPx)
    }

    @Test
    fun `typing sound mode and output mode fall back on an unknown value, spec expansion SS9_1`() {
        val s = SettingsCodec.fromMap(
            mapOf(SettingsKeys.TYPING_SOUND_MODE to "bogus", SettingsKeys.TYPING_SOUND_OUTPUT_MODE to "bogus"),
        )
        assertEquals(TypingSoundMode.OFF, s.feedback.typingSoundMode)
        assertEquals(TypingSoundOutputMode.MEDIA, s.feedback.typingSoundOutputMode)
    }

    @Test
    fun `typing sound mode and output mode round trip when set`() {
        val s = SettingsCodec.fromMap(
            mapOf(SettingsKeys.TYPING_SOUND_MODE to "click", SettingsKeys.TYPING_SOUND_OUTPUT_MODE to "system"),
        )
        assertEquals(TypingSoundMode.CLICK, s.feedback.typingSoundMode)
        assertEquals(TypingSoundOutputMode.SYSTEM, s.feedback.typingSoundOutputMode)
    }

    @Test
    fun `the punctuation lists keep only the canonical characters in canonical order`() {
        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.AUTO_SPACE_PUNCTUATION to "?x.,", SettingsKeys.SPACE_AFTER_PUNCTUATION to "zz"))
        assertEquals(".,?", s.typing.removeSpaceBefore)
        assertEquals("", s.typing.spaceBeforeNextText)
    }

    @Test
    fun `sym pages without an order derive it from emojiFirst and skip the device page, spec SS12 test 20`() {
        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.SYM_PAGES_CONFIG to """{"emojiFirst": false, "deviceEnabled": true}"""))
        assertEquals(listOf(SymPage.SYMBOLS, SymPage.CLIPBOARD, SymPage.EMOJI, SymPage.EMOJI_PICKER, SymPage.GIF, SymPage.CUSTOM_1, SymPage.CUSTOM_2, SymPage.CUSTOM_3, SymPage.FILL), s.symPages.pages.order)
        val withDevice = SettingsCodec.fromMap(mapOf(SettingsKeys.SYM_PAGES_CONFIG to """{"symPageOrder": ["device", "symbols"]}"""))
        assertEquals(listOf(SymPage.SYMBOLS, SymPage.EMOJI, SymPage.CLIPBOARD, SymPage.EMOJI_PICKER, SymPage.GIF, SymPage.CUSTOM_1, SymPage.CUSTOM_2, SymPage.CUSTOM_3, SymPage.FILL), withDevice.symPages.pages.order)
    }

    @Test
    fun `a config written before the GIF page reads with gif last and switched off, layers-sym-alt SS4-1`() {
        val old = """{"emojiEnabled":false,"symbolsEnabled":true,"clipboardEnabled":true,"emojiPickerEnabled":true,"symPageOrder":["clipboard","emoji_picker","symbols","emoji"]}"""
        val pages = SettingsCodec.fromMap(mapOf(SettingsKeys.SYM_PAGES_CONFIG to old)).symPages.pages
        assertEquals(listOf(SymPage.CLIPBOARD, SymPage.EMOJI_PICKER, SymPage.SYMBOLS, SymPage.EMOJI, SymPage.GIF, SymPage.CUSTOM_1, SymPage.CUSTOM_2, SymPage.CUSTOM_3, SymPage.FILL), pages.order)
        assertEquals(false, pages.gifEnabled)
        assertEquals(true, pages.clipboardEnabled)
        val placed = SettingsCodec.fromMap(mapOf(SettingsKeys.SYM_PAGES_CONFIG to """{"gifEnabled":true,"symPageOrder":["gif"," symbols ","gif"]}""")).symPages.pages
        assertEquals(true, placed.gifEnabled)
        assertEquals(SymPage.GIF, placed.order.first(), "a stored place for gif is kept and a duplicate collapses")
        assertEquals(1, placed.order.count { it == SymPage.GIF })
    }

    @Test
    fun `long press accents - the chooser defaults on and the user's lists round-trip, layers-sym-alt SS8-3`() {
        val d = SettingsCodec.fromMap(emptyMap()).keys
        assertEquals(true, d.variationChooser)
        assertEquals(emptyMap(), d.customVariations)
        val lists = mapOf("a" to listOf("ą", "à", "中文"), "Z" to emptyList())
        val map = SettingsCodec.toMap(Settings().let { it.copy(keys = it.keys.copy(customVariations = lists, variationChooser = false)) })
        assertEquals("false", map[SettingsKeys.LONG_PRESS_VARIATION_CHOOSER])
        val back = SettingsCodec.fromMap(map).keys
        assertEquals(lists, back.customVariations)
        assertEquals(false, back.variationChooser)
    }

    @Test
    fun `custom_variations reads leniently - keys longer than one character and non-arrays are skipped`() {
        val stored = """{"a":["ą",1,"à"],"ab":["x"],"b":"not a list","":["y"]}"""
        assertEquals(mapOf("a" to listOf("ą", "à")), SettingsCodec.fromMap(mapOf(SettingsKeys.CUSTOM_VARIATIONS to stored)).keys.customVariations)
        assertEquals(emptyMap(), SettingsCodec.fromMap(mapOf(SettingsKeys.CUSTOM_VARIATIONS to "garbage")).keys.customVariations)
    }

    @Test
    fun `the user's own Sym pages - always three, read leniently, off in an old config, layers-sym-alt SS4-6`() {
        val d = SettingsCodec.fromMap(emptyMap()).symPages
        assertEquals(3, d.customPages.size)
        assertEquals(false, d.pages.custom1Enabled || d.pages.custom2Enabled || d.pages.custom3Enabled)
        val stored = """{"pages":[{"name":"  A very long page name that goes on and on  ","mappings":{"KEYCODE_A":"ą"}},"junk"]}"""
        val pages = SettingsCodec.fromMap(mapOf(SettingsKeys.SYM_CUSTOM_PAGES to stored)).symPages.customPages
        assertEquals(3, pages.size)
        assertEquals("A very long page name th", pages[0].name)
        assertEquals(mapOf("KEYCODE_A" to "ą"), pages[0].mappings)
        assertEquals(CustomSymPage(), pages[1])
        assertEquals(CustomSymPage(), pages[2])
        val oldConfig = """{"gifEnabled":true,"symPageOrder":["symbols","gif"]}"""
        val config = SettingsCodec.fromMap(mapOf(SettingsKeys.SYM_PAGES_CONFIG to oldConfig)).symPages.pages
        assertEquals(listOf(SymPage.CUSTOM_1, SymPage.CUSTOM_2, SymPage.CUSTOM_3, SymPage.FILL), config.order.takeLast(4))
        // layers-sym-alt.md SS4.7: a config from before the Fill page reads it on, unlike the GIF page.
        assertEquals(true, config.fillEnabled)
        assertEquals(false, config.custom1Enabled)
        val placed = SettingsCodec.fromMap(mapOf(SettingsKeys.SYM_PAGES_CONFIG to """{"custom3Enabled":true,"symPageOrder":["custom3","emoji"]}""")).symPages.pages
        assertEquals(SymPage.CUSTOM_3, placed.order.first())
        assertEquals(true, placed.custom3Enabled)
    }

    @Test
    fun `sym_double_tap_chooser defaults on and round-trips`() {
        assertEquals(true, SettingsCodec.fromMap(emptyMap()).symPages.doubleTapChooser)
        val off = Settings().let { it.copy(symPages = it.symPages.copy(doubleTapChooser = false)) }
        val map = SettingsCodec.toMap(off)
        assertEquals("false", map[SettingsKeys.SYM_DOUBLE_TAP_CHOOSER])
        assertEquals(false, SettingsCodec.fromMap(map).symPages.doubleTapChooser)
    }

    @Test
    fun `otp_from_notifications and fill_inline_suggestions round-trip, and fillEnabled travels in sym_pages_config`() {
        assertEquals(true, SettingsCodec.fromMap(emptyMap()).symPages.otpFromNotifications)
        assertEquals(false, SettingsCodec.fromMap(emptyMap()).symPages.inlineSuggestions, "experimental, off by default")
        val off = Settings().let { it.copy(symPages = it.symPages.copy(otpFromNotifications = false, inlineSuggestions = true, pages = it.symPages.pages.copy(fillEnabled = false))) }
        val map = SettingsCodec.toMap(off)
        assertEquals("false", map[SettingsKeys.OTP_FROM_NOTIFICATIONS])
        assertEquals("true", map[SettingsKeys.FILL_INLINE_SUGGESTIONS])
        val back = SettingsCodec.fromMap(map).symPages
        assertEquals(false, back.otpFromNotifications)
        assertEquals(true, back.inlineSuggestions)
        assertEquals(false, back.pages.fillEnabled)
    }

    @Test
    fun `emoji_picker_kaomoji defaults off and round-trips`() {
        assertEquals(false, SettingsCodec.fromMap(emptyMap()).symPages.kaomojiEnabled)
        val on = Settings().let { it.copy(symPages = it.symPages.copy(kaomojiEnabled = true)) }
        val map = SettingsCodec.toMap(on)
        assertEquals("true", map[SettingsKeys.EMOJI_PICKER_KAOMOJI])
        assertEquals(true, SettingsCodec.fromMap(map).symPages.kaomojiEnabled)
    }

    @Test
    fun `a stored config from before the GIF page reads it off, a fresh install has it on`() {
        val old = SettingsCodec.fromMap(mapOf(SettingsKeys.SYM_PAGES_CONFIG to """{"emojiPickerEnabled":true,"symbolsEnabled":true,"symPageOrder":["emoji_picker","symbols"]}""")).symPages.pages
        assertEquals(false, old.gifEnabled)
        assertEquals(true, SettingsCodec.fromMap(emptyMap()).symPages.pages.gifEnabled)
    }

    @Test
    fun `unknown and empty strip buttons vanish from a slot list, spec SS12 test 19`() {
        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.STATUS_BAR_SLOTS_RIGHT to """["bogus","undo","none","software_keyboard_mode"]"""))
        assertEquals(listOf(BarButton.UNDO), s.statusBar.rightButtons)
    }

    @Test
    fun `Enter overrides drop duplicates and blank packages, spec SS12 test 22`() {
        val s = SettingsCodec.fromMap(
            mapOf(
                SettingsKeys.ENTER_OVERRIDES to """[
                    {"packageName":"a","behavior":"enter_newline_ctrl_send","sendStrategy":"ctrl_enter","additionalSendShortcut":"sym_enter"},
                    {"packageName":"a","behavior":"app_default"},
                    {"packageName":"","behavior":"app_default"},
                    {"packageName":"b","behavior":"weird","sendStrategy":"weird","additionalSendShortcut":"weird"}
                ]""",
            ),
        )
        assertEquals(
            listOf(
                EnterOverrideRow("a", EnterBehavior.NEWLINE_CTRL_SEND, EnterSendMethod.CTRL_ENTER, ExtraSendShortcut.SYM_ENTER),
                EnterOverrideRow("b"),
            ),
            s.perApp.enterOverrides,
        )
    }

    @Test
    fun `a theme missing fields takes Slate Dark per field and keeps the strip's own colours over the key colours`() {
        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.THEME to """{"background": -1, "normal_key": 5, "special_key": 6, "unknown": 1}"""))
        assertEquals(-1, s.statusBar.theme.background)
        assertEquals(5, s.statusBar.theme.suggestion)
        assertEquals(6, s.statusBar.theme.statusBarButton)
        assertEquals(StripTheme.SLATE_DARK.accent, s.statusBar.theme.accent)
        assertEquals(1.4, s.statusBar.theme.suggestionsHeightScale)
    }

    @Test
    fun `saved themes with a blank name are called Custom and entries without a theme are skipped`() {
        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.SAVED_THEMES to """[{"name":"  ","theme":{"accent":1}}, {"name":"x"}, 3]"""))
        assertEquals(listOf(NamedTheme("Custom", StripTheme.SLATE_DARK.copy(accent = 1))), s.statusBar.savedThemes)
    }

    @Test
    fun `layout overrides round trip, dropping an entry with neither locale nor layout and one with no theme`() {
        val overrides = listOf(
            ThemeLayoutOverride(locale = "it-IT", theme = StripTheme(accent = 7)),
            ThemeLayoutOverride(layout = "qwertz", theme = StripTheme(accent = 8)),
        )
        val written = SettingsCodec.toMap(Settings(statusBar = StatusBarPrefs(layoutOverrides = overrides)))
        val read = SettingsCodec.fromMap(written)
        assertEquals(overrides, read.statusBar.layoutOverrides)

        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.LAYOUT_OVERRIDES to """[{"theme":{"accent":1}}, {"locale":"fr"}, 3]"""))
        assertEquals(emptyList(), s.statusBar.layoutOverrides)
    }

    @Test
    fun `the ring fit exists only when the radius is stored`() {
        val noRadius = SettingsCodec.fromMap(mapOf(SettingsKeys.RING_CX to "12.5"))
        assertEquals(DevicePrefs().ringFit, noRadius.device.ringFit)
        val garbageRadius = SettingsCodec.fromMap(mapOf(SettingsKeys.RING_RADIUS to "wide"))
        assertNull(garbageRadius.device.ringFit)
        val fit = SettingsCodec.fromMap(mapOf(SettingsKeys.RING_RADIUS to "40", SettingsKeys.RING_CX to "1"))
        assertEquals(RingFit(1f, 0f, 40f, 0f), fit.device.ringFit)
    }

    @Test
    fun `snippets are lower-cased and blank replacements dropped`() {
        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.SNIPPETS to """{"Sig":"Regards","empty":"   ","":"x"}"""))
        assertEquals(mapOf("sig" to "Regards"), s.expansion.snippets)
    }

    @Test
    fun `the launcher documents are kept only when they are JSON objects`() {
        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.LAUNCHER_SHORTCUTS to "[1]", SettingsKeys.LAUNCHER_COMMAND_CUSTOMIZATIONS to """{"a":{}}"""))
        assertEquals("", s.launcher.assignedKeysJson)
        assertEquals("""{"a":{}}""", s.launcher.commandCustomizationsJson)
    }

    @Test
    fun `a string set may be a JSON array or a comma-separated list`() {
        assertEquals(setOf("a", "b"), SettingsCodec.fromMap(mapOf(SettingsKeys.RAW_MODE_PACKAGES to "a, b")).perApp.exactTypingPackages)
        assertEquals(setOf("a"), SettingsCodec.fromMap(mapOf(SettingsKeys.RAW_MODE_PACKAGES to """["a"]""")).perApp.exactTypingPackages)
        assertTrue(SettingsCodec.fromMap(mapOf(SettingsKeys.RAW_MODE_PACKAGES to "[]")).perApp.exactTypingPackages.isEmpty())
    }

    @Test
    fun `numbers stored as decimals or longs still read as ints`() {
        val s = SettingsCodec.fromMap(mapOf(SettingsKeys.STATUS_BAR_HEIGHT to "48.0", SettingsKeys.CARET_BADGE_ARMED_COLOR to "-15656921"))
        assertEquals(48, s.statusBar.heightDp)
        assertEquals(0xFF111827.toInt(), s.statusBar.caretBadgeArmedColor)
        assertFalse(SettingsCodec.fromMap(mapOf(SettingsKeys.TRACKPAD_ENABLED to "TRUE ")).trackpad.enabled.not())
    }
}
