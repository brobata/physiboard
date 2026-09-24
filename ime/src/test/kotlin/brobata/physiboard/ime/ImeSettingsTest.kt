package brobata.physiboard.ime

import brobata.physiboard.core.keys.LongPressMode
import brobata.physiboard.core.pointer.trackpad.ActivationMode
import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.settings.CorrectionPrefs
import brobata.physiboard.core.settings.DictationPrefs
import brobata.physiboard.core.settings.EnterOverrideRow
import brobata.physiboard.core.settings.KeyPrefs
import brobata.physiboard.core.settings.PerAppPrefs
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SymPagePrefs
import brobata.physiboard.core.settings.TrackpadPrefs
import brobata.physiboard.core.settings.TypingPrefs
import brobata.physiboard.core.speech.DictationTextSettings
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
        val shipped = KeyboardSettings()
        val expected = shipped.copy(textInput = shipped.textInput.copy(autocorrect = shipped.textInput.autocorrect.copy(useKeyboardProximity = true)))
        assertEquals(expected, ImeSettings.keyboardSettings(Settings()))
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
        val s = Settings(correction = CorrectionPrefs(textReplacementsEnabled = false, autoReplaceOnSpaceEnter = false, maxAutoReplaceDistance = 3, suggestionsEnabled = false, accentMatching = false, useKeyboardProximity = false))
        val k = ImeSettings.keyboardSettings(s)
        assertFalse(k.textInput.autocorrect.autoCorrectEnabled)
        assertFalse(k.textInput.autocorrect.autoReplaceOnSpaceEnter)
        assertEquals(3, k.textInput.autocorrect.maxAutoReplaceDistance)
        assertFalse(k.textInput.autocorrect.suggestionsEnabled)
        assertFalse(k.textInput.autocorrect.accentMatchingEnabled)
        assertFalse(k.textInput.autocorrect.useKeyboardProximity)
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

    @Test
    fun `the long-press mode and threshold ride on the layout`() {
        val layout = ImeSettings.layout(TitanLayouts.titan2EliteQwerty(), Settings(keys = KeyPrefs(longPressMode = LongPressMode.VARIATIONS, longPressThresholdMs = 700)))
        assertEquals(LongPressMode.VARIATIONS, layout.longPress.mode)
        assertEquals(700, layout.longPress.thresholdMs)
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
        val s = Settings(dictation = DictationPrefs(endSilenceMs = 3500, continuousSession = false), typing = TypingPrefs(capitalizeAtTextStart = false))
        val d = ImeSettings.dictationSettings(s, androidApiLevel = 34)
        assertEquals(3500L, d.pauseMs)
        assertFalse(d.segmentedSessionEnabled)
        assertEquals(34, d.androidApiLevel)
        val t = ImeSettings.dictationTextSettings(DictationTextSettings(capitalizationAllowed = false), s)
        assertFalse(t.capitalizeFirstLetter)
        assertTrue(t.capitalizeAfterSentenceEnd)
        assertFalse(t.capitalizationAllowed)
    }
}
