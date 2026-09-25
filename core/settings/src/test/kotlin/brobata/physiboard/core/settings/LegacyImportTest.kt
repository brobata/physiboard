package brobata.physiboard.core.settings

import brobata.physiboard.core.pointer.trackpad.ActivationMode
import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.text.EnterBehavior
import brobata.physiboard.core.text.MessagingPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One test per mapping row the catalogue records (settings-catalog.md SS2, SS4.2, SS5.2, SS6.3, SS12). */
class LegacyImportTest {

    private fun import(vararg rows: Pair<String, Any?>): LegacyImportResult = LegacyImport.import(mapOf(*rows))

    @Test
    fun `an empty 2x store imports as the defaults with nothing carried`() {
        val r = import()
        assertEquals(Settings(), r.settings)
        assertTrue(r.carried.isEmpty() && r.ignored.isEmpty())
    }

    @Test
    fun `boolean rows carry over with their type`() {
        val r = import("auto_capitalize_first_letter" to false, "double_space_to_period" to false, "caret_modifier_badge" to false, "smart_backlight_enabled" to false)
        assertFalse(r.settings.typing.capitalizeAtTextStart)
        assertFalse(r.settings.typing.doubleSpaceToPeriod)
        assertFalse(r.settings.statusBar.caretModifierBadge)
        assertFalse(r.settings.device.smartBacklightEnabled)
        assertEquals(setOf("auto_capitalize_first_letter", "double_space_to_period", "caret_modifier_badge", "smart_backlight_enabled"), r.carried)
    }

    @Test
    fun `int, long and float rows carry over`() {
        val r = import("status_bar_height_dp" to 36, "clipboard_retention_time" to 30L, "notification_ring_radius" to 45.9375f, "notification_ring_cx" to 12.5f, "notification_ring_cy" to 1f, "notification_ring_stroke" to 2f)
        assertEquals(36, r.settings.statusBar.heightDp)
        assertEquals(30L, r.settings.expansion.clipboardRetentionMinutes)
        assertEquals(RingFit(12.5f, 1f, 45.9375f, 2f), r.settings.device.ringFit)
    }

    @Test
    fun `a wrong-typed value is read where it can be and defaulted where it cannot`() {
        val r = import("status_bar_height_dp" to "48", "bounce_keys_delay_ms" to "120", "dictation_end_silence_ms" to "soon", "auto_capitalize_first_letter" to "maybe", "notification_ring_minutes" to 7.0)
        assertEquals(48, r.settings.statusBar.heightDp)
        assertEquals(2000, r.settings.dictation.endSilenceMs)
        assertTrue(r.settings.typing.capitalizeAtTextStart)
        assertEquals(7, r.settings.device.ringMinutes)
        assertTrue("bounce_keys_delay_ms" in r.ignored)
    }

    @Test
    fun `string sets carry over as sets, spec SS12 test 8`() {
        val r = import("status_bar_apps" to setOf("com.whatsapp", "com.Slack"), "app_raw_mode_packages" to setOf("com.termux"), "app_keyboard_nudge_packages" to setOf("com.microsoft.teams", "com.example"))
        assertEquals(setOf("com.whatsapp", "com.Slack"), r.settings.statusBar.apps)
        assertEquals(setOf("com.termux"), r.settings.perApp.exactTypingPackages)
        assertEquals(setOf("com.microsoft.teams", "com.example"), r.settings.perApp.nudgePackages)
    }

    @Test
    fun `a null value is ignored and the field defaults`() {
        val r = import("status_bar_height_dp" to null)
        assertEquals(56, r.settings.statusBar.heightDp)
        assertEquals(setOf("status_bar_height_dp"), r.ignored)
    }

    @Test
    fun `pastierina slot rows are renamed, spec SS12 test 9`() {
        val r = import("pastierina_status_bar_slots_left" to """["settings"]""", "pastierina_status_bar_slots_right" to """["undo","redo"]""")
        assertEquals(listOf(BarButton.SETTINGS), r.settings.statusBar.leftButtons)
        assertEquals(listOf(BarButton.UNDO, BarButton.REDO), r.settings.statusBar.rightButtons)
        assertTrue("pastierina_status_bar_slots_left" in r.carried)
    }

    @Test
    fun `the properly named row wins over its legacy alias`() {
        val r = import("pastierina_status_bar_slots_left" to """["settings"]""", "status_bar_slots_left" to """["emoji"]""")
        assertEquals(listOf(BarButton.EMOJI), r.settings.statusBar.leftButtons)
        assertTrue("pastierina_status_bar_slots_left" in r.ignored)
    }

    @Test
    fun `the misnamed Fn to Ctrl capture rows map onto the prev rows, spec SS4 2`() {
        val r = import("fn_ctrl_captured" to true, "fn_ctrl_original_enable" to 1, "fn_ctrl_original_function" to 4)
        assertTrue(r.settings.captures.fnCtrlPrevCaptured)
        assertEquals(1, r.settings.captures.fnCtrlPrevEnable)
        assertEquals(4, r.settings.captures.fnCtrlPrevFunction)
        val proper = import("fn_ctrl_prev_captured" to true, "fn_ctrl_prev_enable" to 0, "fn_ctrl_original_enable" to 9)
        assertEquals(0, proper.settings.captures.fnCtrlPrevEnable)
    }

    @Test
    fun `the other captured originals survive, spec SS12 test 12`() {
        val r = import(
            "side_key_original_captured" to true, "side_key_original_package" to "com.google.android.apps.bard", "side_key_original_activity" to "x.Main",
            "qs_backlight_prev_captured" to true, "qs_backlight_prev" to 0, "ring_backlight_prev_captured" to true, "ring_backlight_prev" to 1, "smart_backlight_applied" to true,
        )
        val c = r.settings.captures
        assertTrue(c.sideKeyOriginalCaptured && c.qsBacklightPrevCaptured && c.ringBacklightPrevCaptured && c.smartBacklightApplied)
        assertEquals("com.google.android.apps.bard", c.sideKeyOriginalPackage)
        assertEquals("x.Main", c.sideKeyOriginalActivity)
        assertEquals(0, c.qsBacklightPrev)
        assertEquals(1, c.ringBacklightPrev)
        assertNull(DeviceCaptures().qsBacklightPrev)
    }

    @Test
    fun `show_status_bar false becomes NEVER when the visibility row is absent, spec SS12 test 17`() {
        assertEquals(StatusBarVisibility.NEVER, import("show_status_bar" to false).settings.statusBar.visibility)
        assertEquals(StatusBarVisibility.ALWAYS, import("show_status_bar" to true).settings.statusBar.visibility)
        assertEquals(StatusBarVisibility.APPS, import("show_status_bar" to false, "status_bar_visibility" to "APPS").settings.statusBar.visibility)
        assertEquals(StatusBarVisibility.NEVER, import("show_status_bar" to false, "status_bar_visibility" to "SOMETIMES").settings.statusBar.visibility)
        assertTrue("show_status_bar" in import("show_status_bar" to false).carried)
    }

    @Test
    fun `single-slot rows become the slot lists when the arrays are absent, spec SS12 test 19`() {
        val r = import("status_bar_slot_left" to "emoji", "status_bar_slot_right_1" to "clipboard", "status_bar_slot_right_2" to "none")
        assertEquals(listOf(BarButton.EMOJI), r.settings.statusBar.leftButtons)
        assertEquals(listOf(BarButton.CLIPBOARD), r.settings.statusBar.rightButtons)
        assertEquals(setOf("status_bar_slot_left", "status_bar_slot_right_1", "status_bar_slot_right_2"), r.carried)
    }

    @Test
    fun `the slot arrays win over stale single rows, spec SS11`() {
        val r = import("status_bar_slots_left" to """["undo"]""", "status_bar_slot_left" to "emoji", "status_bar_slots_right" to "garbage", "status_bar_slot_right_1" to "redo")
        assertEquals(listOf(BarButton.UNDO), r.settings.statusBar.leftButtons)
        assertEquals(listOf(BarButton.REDO), r.settings.statusBar.rightButtons)
    }

    @Test
    fun `dropped strip button ids vanish`() {
        val r = import("status_bar_slots_right" to """["software_keyboard_mode","microphone","bogus"]""")
        assertEquals(listOf(BarButton.MICROPHONE), r.settings.statusBar.rightButtons)
    }

    @Test
    fun `x-pastiera is stripped from the text replacement languages`() {
        val r = import("auto_correct_enabled_languages" to "en,x-pastiera, fr,,en")
        assertEquals(listOf("en", "fr"), r.settings.correction.textReplacementLanguages)
    }

    @Test
    fun `custom substitutions carry per language with their display name`() {
        val r = import("auto_correct_custom_en" to """{"__name":"English","teh":"the"}""", "auto_correct_custom_" to "{}", "auto_correct_custom_xx" to "not json")
        assertEquals(mapOf("en" to SubstitutionSet("English", mapOf("teh" to "the"))), r.settings.correction.customSubstitutions)
    }

    @Test
    fun `the sym pages config keeps its contract and loses the device page and emojiFirst, spec SS12 test 20`() {
        val r = import("sym_pages_config" to """{"deviceEnabled":true,"emojiEnabled":true,"symbolsEnabled":true,"clipboardEnabled":false,"emojiPickerEnabled":true,"emojiFirst":false,"symPageOrder":["emoji_picker","symbols","clipboard","emoji"]}""")
        assertEquals(listOf(SymPage.EMOJI_PICKER, SymPage.SYMBOLS, SymPage.CLIPBOARD, SymPage.EMOJI), r.settings.symPages.pages.order)
        assertTrue(r.settings.symPages.pages.emojiEnabled)
        val legacyShape = import("sym_pages_config" to """{"emojiFirst": true}""")
        assertEquals(listOf(SymPage.EMOJI, SymPage.SYMBOLS, SymPage.CLIPBOARD, SymPage.EMOJI_PICKER), legacyShape.settings.symPages.pages.order)
    }

    @Test
    fun `custom Sym maps carry over`() {
        val r = import("sym_mappings_custom" to """{"mappings":{"KEYCODE_Q":"😀"}}""", "sym_mappings_page2_custom" to """{"mappings":{"KEYCODE_W":"€"}}""")
        assertEquals(mapOf("KEYCODE_Q" to "😀"), r.settings.symPages.customEmojiPage)
        assertEquals(mapOf("KEYCODE_W" to "€"), r.settings.symPages.customSymbolsPage)
    }

    @Test
    fun `the launcher behavior pastiera is PhysiBoard's own launcher`() {
        assertEquals(LauncherBehavior.PHYSIBOARD, import("quick_launcher_behavior" to "pastiera").settings.launcher.behavior)
        assertEquals(LauncherBehavior.NIAGARA, import("quick_launcher_behavior" to "niagara").settings.launcher.behavior)
        assertEquals(LauncherBehavior.PHYSIBOARD, import("quick_launcher_behavior" to "other").settings.launcher.behavior)
    }

    @Test
    fun `the launcher documents carry verbatim when they are JSON objects`() {
        val doc = """{"62":{"type":"command","commandId":"pastiera.quick_launcher"}}"""
        val r = import("launcher_shortcuts" to doc, "quick_launcher_command_customizations" to "oops")
        assertEquals(doc, r.settings.launcher.assignedKeysJson)
        assertEquals("", r.settings.launcher.commandCustomizationsJson)
    }

    @Test
    fun `a hardware theme still at the pre-lift bar height is lifted on the way in, spec SS6 1`() {
        val r = import("keyboard_theme_hardware" to """{"background":-1,"suggestions_height_scale":1.0}""")
        assertEquals(1.4, r.settings.statusBar.theme.suggestionsHeightScale)
        assertEquals(-1, r.settings.statusBar.theme.background)
        val untouched = import("keyboard_theme_hardware" to """{"suggestions_height_scale":1.2}""")
        assertEquals(1.2, untouched.settings.statusBar.theme.suggestionsHeightScale)
    }

    @Test
    fun `saved themes are lifted too and keep their names`() {
        val r = import("keyboard_theme_saved_themes" to """[{"name":"Mine","theme":{"suggestions_height_scale":1.0,"accent":7}}]""")
        assertEquals(listOf(NamedTheme("Mine", StripTheme.SLATE_DARK.copy(accent = 7, suggestionsHeightScale = 1.4))), r.settings.statusBar.savedThemes)
    }

    @Test
    fun `the Enter preset and overrides carry with the catalogue's normalisation, spec SS12 test 22`() {
        val r = import(
            "app_enter_behavior_preset" to "enter_newline_only",
            "app_enter_behavior_enabled" to false,
            "app_enter_behavior_overrides" to """[{"packageName":"com.whatsapp","behavior":"enter_send_shift_newline","sendStrategy":"auto","additionalSendShortcut":"none"},{"packageName":"com.whatsapp","behavior":"app_default"}]""",
        )
        assertEquals(MessagingPreset.APP_DEFAULT, r.settings.perApp.enterPreset)
        assertFalse(r.settings.perApp.enterBehaviorEnabled)
        assertEquals(listOf(EnterOverrideRow("com.whatsapp", EnterBehavior.SEND_SHIFT_NEWLINE)), r.settings.perApp.enterOverrides)
    }

    @Test
    fun `the trackpad tuning carries but its switch does not`() {
        val r = import("screen_trackpad_enabled" to true, "screen_trackpad_trigger_key" to "sym", "screen_trackpad_activation" to "double_tap", "screen_trackpad_step_px" to 4, "screen_trackpad_show_hint" to false)
        assertFalse(r.settings.trackpad.enabled)
        assertEquals(TriggerKey.SYM, r.settings.trackpad.triggerKey)
        assertEquals(ActivationMode.DOUBLE_TAP, r.settings.trackpad.activation)
        assertEquals(8, r.settings.trackpad.stepPx)
        assertFalse(r.settings.trackpad.showHint)
        assertTrue("screen_trackpad_enabled" in r.ignored)
    }

    @Test
    fun `dictation rows carry over`() {
        val engine = "com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService"
        val r = import("dictation_engine" to engine, "dictation_haptic_strength" to "light", "assistant_action" to "assist", "side_key_assistant" to true, "dictation_mask_offensive" to true, "fn_long_press_speech" to false)
        assertEquals(engine, r.settings.dictation.engine)
        assertEquals(HapticStrength.LIGHT, r.settings.dictation.hapticStrength)
        assertEquals(AssistantAction.ASSIST, r.settings.dictation.assistantAction)
        assertTrue(r.settings.dictation.sideKeyAssistant && r.settings.dictation.maskOffensive)
        assertFalse(r.settings.dictation.fnLongPressSpeech)
    }

    @Test
    fun `input styles, suggestion locales, hidden styles and the app language carry over`() {
        val r = import("custom_input_styles" to "de_DE:qwertz;en_US:qwerty;", "input_style_suggestion_locales" to """{"de-DE:qwertz":["en-US"]}""", "hidden_system_input_styles" to """["it_IT:qwerty"]""", "app_language_tag" to "de", "keyboard_layout" to "qwertz")
        assertEquals(listOf("de_DE:qwertz", "en_US:qwerty"), r.settings.languages.inputStyles)
        assertEquals(mapOf("de-DE:qwertz" to listOf("en-US")), r.settings.languages.suggestionLocales)
        assertEquals(listOf("it_IT:qwerty"), r.settings.languages.hiddenSystemInputStyles)
        assertEquals("de", r.settings.languages.appLanguageTag)
        assertEquals("qwertz", r.settings.languages.keyboardLayout)
    }

    @Test
    fun `ring colours and the shell markers carry over`() {
        val r = import("notification_ring_app_colors" to """{"co.kidcasa.app":-757066}""", "notification_ring_default_color" to -13318311, "dismissed_releases" to "v2.0.6,v2.0.7", "tutorial_completed" to true, "last_seen_whats_new_version" to "2.0.7")
        assertEquals(mapOf("co.kidcasa.app" to -757066), r.settings.device.ringAppColors)
        assertEquals(-13318311, r.settings.device.ringDefaultColor)
        assertEquals(listOf("v2.0.6", "v2.0.7"), r.settings.shell.dismissedReleases)
        assertTrue(r.settings.shell.tutorialCompleted)
        assertEquals("2.0.7", r.settings.shell.lastSeenWhatsNewVersion)
    }

    @Test
    fun `snippets and the expansion switches carry over`() {
        val r = import("snippets_v1" to """{"Sig":"Regards"}""", "snippets_prefix" to ":", "snippets_enabled" to true, "snippets_presentation" to "suggestion_bar")
        assertEquals(mapOf("sig" to "Regards"), r.settings.expansion.snippets)
        assertEquals("!", r.settings.expansion.snippetPrefix, "a colon is not a valid prefix")
        assertTrue(r.settings.expansion.snippetsEnabled)
        assertEquals(SnippetPresentation.SUGGESTION_BAR, r.settings.expansion.presentation)
    }

    @Test
    fun `dropped rows and 2x-only markers are ignored, spec SS13 and SS12 tests 10 to 12`() {
        val dropped = mapOf<String, Any?>(
            "shift_tap_latches" to true, "alt_latch_stays_on_space" to true, "software_keyboard_mode" to "force_virtual",
            "software_keyboard_layout_style" to "compact", "modifier_indicator_mode" to "menu_bar", "physical_keyboard_currency_symbol" to "$",
            "auto_show_keyboard" to true, "mid_word_quote_to_apostrophe" to true, "alt_ctrl_speech_shortcut" to true, "fn_speech_scan_code" to 251,
            "use_edit_type_ranking" to true, "bounce_keys_enabled" to true, "overlapping_keys_enabled" to true, "additional_ime_subtypes" to setOf("en_US:qwerty"),
            "keyboard_layout_list" to "[]", "physical_keyboard_profile_override" to "titan2", "titan2_layout_enabled" to true, "global_variation_layout_override" to "x",
            "alt_character_layer_binding" to "emoji", "restore_sym_page" to 1, "keyboard_theme_software" to "{}", "keyboard_theme_assignment_mode_hardware" to "fixed",
            "keyboard_theme_light_hardware" to "{}", "keyboard_theme_drafts" to "[]", "keyboard_theme_preview_viewport_scale" to 1.2f,
            "accessibility_live_announcements_enabled" to true, "ime_overlay_debug_logging" to true, "pastierina_mode_active" to true, "pastierina_mode_override" to "x",
            "software_keyboard_mode_runtime_override" to "force_hardware", "quick_launcher_default_assigned" to true, "trackpad_gestures_enabled" to true, "trackpad_provider" to "shizuku",
            "typing_sound_mode" to "click", "quick_launcher_width_percent" to 80, "quick_launcher_animation_duration_ms" to 200, "command_surface_sources" to "{}",
            "impact_defaults_applied" to true, "prefs_migrated_v2" to true, "v2_migration_notice_seen" to true, "settings_baseline_version" to 1,
            "alt_shift_default_initialized" to true, "nav_mode_default_mappings_version" to 3, "nav_mode_mappings_updated" to 1L, "variations_updated" to 1L,
            "hardware_bar_height_migrated" to true, "clicks_button_mode" to "x", "static_variation_bar_preset" to "x", "status_bar_variations_visible" to true,
            "privileged_backlight_ok" to true, "user_dictionary_entries" to "[]", "suggestion_debug_logging" to true, "toast_on_layout_switch_typo" to true,
        )
        val r = LegacyImport.import(dropped)
        assertEquals(Settings(), r.settings)
        assertEquals(dropped.keys, r.ignored)
        assertTrue(r.carried.isEmpty())
    }

    @Test
    fun `a corrupt, partial store imports what it can and defaults the rest`() {
        val r = import(
            "auto_capitalize_first_letter" to false,
            "status_bar_height_dp" to "tall",
            "keyboard_theme_hardware" to "{broken",
            "app_enter_behavior_overrides" to 12,
            "sym_pages_config" to Any(),
            "status_bar_apps" to listOf("com.whatsapp", 7),
            "notification_ring_radius" to "round",
            "screen_trackpad_step_px" to 999,
        )
        val s = r.settings
        assertFalse(s.typing.capitalizeAtTextStart)
        assertEquals(56, s.statusBar.heightDp)
        assertEquals(StripTheme(), s.statusBar.theme)
        assertEquals(PerAppPrefs().enterOverrides, s.perApp.enterOverrides)
        assertEquals(SymPagesConfig(), s.symPages.pages)
        assertEquals(setOf("com.whatsapp", "7"), s.statusBar.apps)
        assertNull(s.device.ringFit)
        assertEquals(64, s.trackpad.stepPx)
        assertTrue("sym_pages_config" in r.ignored)
    }

    @Test
    fun `the whole factory baseline imports to the defaults plus its personal data and the trackpad off`() {
        val baseline = mapOf<String, Any?>(
            "alt_ctrl_speech_shortcut" to false, "alt_shift_layout_switch" to true, "app_enter_behavior_preset" to "enter_send_shift_newline",
            "app_raw_mode_packages" to setOf("org.chromium.webapk.a5d49fddf77614419_v2"), "auto_capitalize_first_letter" to true, "auto_replace_on_space_enter" to true,
            "auto_show_keyboard" to true, "caret_badge_armed_color" to -15656921, "dictation_end_silence_ms" to 2000, "dictation_haptics" to true, "dictation_mask_offensive" to false,
            "emoji_picker_expanded_height" to false, "fn_long_press_speech" to true, "keyboard_layout" to "qwerty", "max_auto_replace_distance" to 2, "modifier_indicator_mode" to "menu_bar",
            "notification_ring_app_colors" to """{"co.kidcasa.app":-757066,"com.google.android.apps.googlevoice":-13318311}""",
            "notification_ring_cx" to 78.4834f, "notification_ring_cy" to 80.4834f, "notification_ring_enabled" to true, "notification_ring_minutes" to 2,
            "notification_ring_radius" to 45.9375f, "notification_ring_stroke" to 9.625f, "physical_keyboard_currency_symbol" to "$", "screen_trackpad_enabled" to true,
            "screen_trackpad_step_px" to 32, "show_status_bar" to true, "side_key_assistant" to true, "smart_backlight_enabled" to true, "software_keyboard_mode" to "auto",
            "status_bar_apps" to StatusBarPrefs.SEEDED_STATUS_BAR_APPS, "status_bar_height_dp" to 56, "status_bar_slot_left" to "clipboard", "status_bar_slot_right_1" to "microphone",
            "status_bar_slot_right_2" to "none", "status_bar_slots_left" to """["clipboard"]""", "status_bar_slots_right" to """["microphone","none"]""", "status_bar_visibility" to "ALWAYS",
            "sym_pages_config" to """{"deviceEnabled":false,"emojiEnabled":false,"symbolsEnabled":true,"clipboardEnabled":false,"emojiPickerEnabled":true,"emojiFirst":false,"symPageOrder":["emoji_picker","symbols","clipboard","emoji"]}""",
            "titan2_elite_rounded_corner_insets" to true, "use_keyboard_proximity" to true,
        )
        val r = LegacyImport.import(baseline)
        val expected = Settings(
            perApp = PerAppPrefs(exactTypingPackages = setOf("org.chromium.webapk.a5d49fddf77614419_v2")),
            device = DevicePrefs(ringAppColors = mapOf("co.kidcasa.app" to -757066, "com.google.android.apps.googlevoice" to -13318311)),
            dictation = DictationPrefs(sideKeyAssistant = true),
        )
        assertEquals(expected, r.settings)
        assertEquals(setOf("alt_ctrl_speech_shortcut", "auto_show_keyboard", "modifier_indicator_mode", "physical_keyboard_currency_symbol", "screen_trackpad_enabled", "software_keyboard_mode"), r.ignored)
    }
}
