package it.palsoftware.pastiera

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import it.palsoftware.pastiera.commands.CommandLaunchSpec
import it.palsoftware.pastiera.commands.CommandExecutor
import it.palsoftware.pastiera.commands.CommandRegistry
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.commands.PastieraCommandSource
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsManagerLayoutSwitchTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ShadowToast.reset()
    }

    @Test
    fun altShiftLayoutSwitch_defaultsEnabled_andPersistsDisabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.isAltShiftLayoutSwitchEnabled(context))

        SettingsManager.setAltShiftLayoutSwitchEnabled(context, false)

        assertFalse(SettingsManager.isAltShiftLayoutSwitchEnabled(context))
    }

    @Test
    fun altEnterLayoutSwitch_defaultsDisabled_andPersistsState() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(SettingsManager.isAltEnterLayoutSwitchEnabled(context))

        SettingsManager.setAltEnterLayoutSwitchEnabled(context, true)

        assertTrue(SettingsManager.isAltEnterLayoutSwitchEnabled(context))

        SettingsManager.setAltEnterLayoutSwitchEnabled(context, false)

        assertFalse(SettingsManager.isAltEnterLayoutSwitchEnabled(context))
    }

    @Test
    fun ctrlSpaceLayoutSwitch_defaultsEnabled_andPersistsDisabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.isCtrlSpaceLayoutSwitchEnabled(context))

        SettingsManager.setCtrlSpaceLayoutSwitchEnabled(context, false)

        assertFalse(SettingsManager.isCtrlSpaceLayoutSwitchEnabled(context))
    }

    @Test
    fun symAutoCloseOnTouch_defaultsEnabled_andPersistsDisabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.getSymAutoCloseOnTouch(context))

        SettingsManager.setSymAutoCloseOnTouch(context, false)

        assertFalse(SettingsManager.getSymAutoCloseOnTouch(context))
    }

    @Test
    fun modifierIndicators_defaultToBottomStripAndPersistMultipleTargets() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(
            setOf(SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP),
            SettingsManager.getModifierIndicators(context)
        )
        assertTrue(SettingsManager.getModifierIndicatorShowsBottomStrip(context))
        assertFalse(SettingsManager.getModifierIndicatorShowsMenuBar(context))
        assertFalse(SettingsManager.getModifierIndicatorShowsStatusBar(context))

        SettingsManager.setModifierIndicators(
            context,
            setOf(
                SettingsManager.MODIFIER_INDICATOR_MENU_BAR,
                SettingsManager.MODIFIER_INDICATOR_STATUS_BAR
            )
        )

        assertEquals(
            setOf(
                SettingsManager.MODIFIER_INDICATOR_MENU_BAR,
                SettingsManager.MODIFIER_INDICATOR_STATUS_BAR
            ),
            SettingsManager.getModifierIndicators(context)
        )
        assertFalse(SettingsManager.getModifierIndicatorShowsBottomStrip(context))
        assertTrue(SettingsManager.getModifierIndicatorShowsMenuBar(context))
        assertTrue(SettingsManager.getModifierIndicatorShowsStatusBar(context))
    }

    @Test
    fun modifierIndicators_fallBackToBottomStripForUnknownStoredValue() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.getPreferences(context).edit()
            .putString(SettingsManager.KEY_MODIFIER_INDICATOR_MODE, "weird")
            .commit()

        assertEquals(
            setOf(SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP),
            SettingsManager.getModifierIndicators(context)
        )
    }

    @Test
    fun modifierIndicators_migrateOldCombinedModeToBottomAndStatusbar() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.getPreferences(context).edit()
            .putString(SettingsManager.KEY_MODIFIER_INDICATOR_MODE, SettingsManager.MODIFIER_INDICATOR_MODE_BOTTOM_AND_MENU)
            .commit()

        assertEquals(
            setOf(
                SettingsManager.MODIFIER_INDICATOR_BOTTOM_STRIP,
                SettingsManager.MODIFIER_INDICATOR_STATUS_BAR
            ),
            SettingsManager.getModifierIndicators(context)
        )
    }

    @Test
    fun trackpadSwipeThresholds_fallBackToLegacyValue() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.getPreferences(context).edit()
            .putFloat("trackpad_swipe_threshold", 420f)
            .commit()

        assertEquals(420f, SettingsManager.getTrackpadSuggestionSwipeThreshold(context), 0.01f)
        assertEquals(420f, SettingsManager.getTrackpadDeleteSwipeThreshold(context), 0.01f)
    }

    @Test
    fun trackpadSwipeThresholds_persistSeparatelyAndClamp() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setTrackpadSuggestionSwipeThreshold(context, 240f)
        SettingsManager.setTrackpadDeleteSwipeThreshold(context, 720f)

        assertEquals(240f, SettingsManager.getTrackpadSuggestionSwipeThreshold(context), 0.01f)
        assertEquals(720f, SettingsManager.getTrackpadDeleteSwipeThreshold(context), 0.01f)

        SettingsManager.setTrackpadSuggestionSwipeThreshold(context, 40f)
        SettingsManager.setTrackpadDeleteSwipeThreshold(context, 2000f)

        assertEquals(120f, SettingsManager.getTrackpadSuggestionSwipeThreshold(context), 0.01f)
        assertEquals(750f, SettingsManager.getTrackpadDeleteSwipeThreshold(context), 0.01f)
    }

    @Test
    fun inputStyleSuggestionLocales_persistPerLocaleAndLayout() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setAdditionalSuggestionLocalesForInputStyle(
            context,
            locale = "de_DE",
            layout = "qwertz",
            locales = listOf("en", "fr-FR", "en")
        )

        assertEquals(
            listOf("en", "fr-FR"),
            SettingsManager.getAdditionalSuggestionLocalesForInputStyle(context, "de-DE", "qwertz")
        )
        assertTrue(
            SettingsManager.getAdditionalSuggestionLocalesForInputStyle(context, "de-DE", "qwerty").isEmpty()
        )
    }

    @Test
    fun inputStyleSuggestionLocales_fallBackAcrossLanguageAndLayout() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setAdditionalSuggestionLocalesForInputStyle(
            context,
            locale = "de",
            layout = "german_multitap_qwertz",
            locales = listOf("en")
        )

        assertEquals(
            listOf("en"),
            SettingsManager.getAdditionalSuggestionLocalesForInputStyle(context, "de-DE", "qwertz")
        )
    }

    @Test
    fun hiddenSystemInputStyles_persistPerLocaleAndLayout() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(SettingsManager.isSystemInputStyleHidden(context, "de_DE", "qwertz"))

        SettingsManager.hideSystemInputStyle(context, "de_DE", "qwertz")

        assertTrue(SettingsManager.isSystemInputStyleHidden(context, "de-DE", "qwertz"))
        assertFalse(SettingsManager.isSystemInputStyleHidden(context, "de-DE", "qwerty"))

        SettingsManager.showSystemInputStyle(context, "de-DE", "qwertz")

        assertFalse(SettingsManager.isSystemInputStyleHidden(context, "de_DE", "qwertz"))
    }

    @Test
    fun layoutSwitchToast_defaultsEnabled_andPersistsDisabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.isToastOnLayoutSwitchEnabled(context))

        SettingsManager.setToastOnLayoutSwitchEnabled(context, false)

        assertFalse(SettingsManager.isToastOnLayoutSwitchEnabled(context))
    }

    @Test
    fun softwareKeyboardModeToggleToasts_defaultsEnabled_andPersistsDisabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context))

        SettingsManager.setSoftwareKeyboardModeToggleToastsEnabled(context, false)

        assertFalse(SettingsManager.getSoftwareKeyboardModeToggleToastsEnabled(context))
    }

    @Test
    fun softwareKeyboardMode_defaultsAuto_andPersistsVirtualAndHardwareModes() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(
            SettingsManager.SoftwareKeyboardMode.AUTO,
            SettingsManager.getSoftwareKeyboardMode(context)
        )

        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL)
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            SettingsManager.getSoftwareKeyboardMode(context)
        )

        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE)
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SettingsManager.getSoftwareKeyboardMode(context)
        )
    }

    @Test
    fun softwareKeyboardMode_storageValuesUseVirtualAndHardwareNaming() {
        assertEquals("auto", SettingsManager.SoftwareKeyboardMode.AUTO.storageValue)
        assertEquals("force_virtual", SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL.storageValue)
        assertEquals("force_hardware", SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE.storageValue)
    }

    @Test
    fun softwareKeyboardModeToggle_switchesBetweenForceVirtualAndForceHardware() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL)
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SoftwareKeyboardModeActions.toggleForceMode(context)
        )
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SettingsManager.getSoftwareKeyboardMode(context)
        )

        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            SoftwareKeyboardModeActions.toggleForceMode(context)
        )
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            SettingsManager.getSoftwareKeyboardMode(context)
        )
    }

    @Test
    fun softwareKeyboardModeToggle_isExposedToNavModeAndQuickLauncher() {
        val context = RuntimeEnvironment.getApplication()
        val registry = CommandRegistry(context)

        assertTrue(
            registry.getCommands(CommandSurface.NavMode)
                .any { it.id == PastieraCommandSource.COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE }
        )
        assertTrue(
            registry.getCommands(CommandSurface.QuickLauncher)
                .any { it.id == PastieraCommandSource.COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE }
        )
    }

    @Test
    fun softwareKeyboardModeToggle_commandExecutorRunsInternalAction() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE)

        val command = CommandRegistry(context)
            .resolve(PastieraCommandSource.COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE)
        val result = CommandExecutor(context, showToast = false).execute(requireNotNull(command))

        assertTrue(result.isSuccess)
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            SettingsManager.getSoftwareKeyboardMode(context)
        )
    }

    @Test
    fun softwareKeyboardModeToggle_commandExecutorRespectsToastSetting() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setSoftwareKeyboardModeToggleToastsEnabled(context, false)
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE)

        val command = CommandRegistry(context)
            .resolve(PastieraCommandSource.COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE)
        val result = CommandExecutor(context, showToast = true).execute(requireNotNull(command))

        assertTrue(result.isSuccess)
        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL,
            SettingsManager.getSoftwareKeyboardMode(context)
        )
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun homeScreenCommand_isDeviceControlAndVisibleOnShortcutSurfaces() {
        val context = RuntimeEnvironment.getApplication()
        val registry = CommandRegistry(context)

        assertTrue(
            registry.getCommands(CommandSurface.AssignedKey)
                .any { it.id == "device.home" && it.source == CommandSourceId.DeviceControl }
        )
        assertTrue(
            registry.getCommands(CommandSurface.NavMode)
                .any { it.id == "device.home" && it.source == CommandSourceId.DeviceControl }
        )
        assertFalse(
            registry.getCommands(CommandSurface.QuickLauncher)
                .any { it.id == "device.home" }
        )

        SettingsManager.setCommandSourceVisibility(
            context,
            listOf(
                SettingsManager.CommandSourceVisibility(CommandSourceId.Apps.storageValue, quickLauncherEnabled = true),
                SettingsManager.CommandSourceVisibility(CommandSourceId.Pastiera.storageValue, quickLauncherEnabled = true),
                SettingsManager.CommandSourceVisibility(CommandSourceId.AppActions.storageValue, quickLauncherEnabled = false),
                SettingsManager.CommandSourceVisibility(CommandSourceId.DeviceControl.storageValue, quickLauncherEnabled = true),
                SettingsManager.CommandSourceVisibility(CommandSourceId.NavActions.storageValue, quickLauncherEnabled = false)
            )
        )

        assertTrue(
            registry.getCommands(CommandSurface.QuickLauncher)
                .any { it.id == "device.home" && it.source == CommandSourceId.DeviceControl }
        )
    }

    @Test
    fun homeScreenCommandExecutorStartsAndroidHomeIntent() {
        val context = RuntimeEnvironment.getApplication()
        val command = CommandRegistry(context)
            .resolve("device.home")
        val result = CommandExecutor(context, showToast = false).execute(requireNotNull(command))

        assertTrue(result.isSuccess)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.categories?.contains(Intent.CATEGORY_HOME) == true)
    }

    @Test
    fun softwareKeyboardModeActionActivity_runsExternalToggleIntent() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL)

        Robolectric.buildActivity(
            SoftwareKeyboardModeActionActivity::class.java,
            android.content.Intent(SoftwareKeyboardModeActions.ACTION_TOGGLE)
        ).create().destroy()

        assertEquals(
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE,
            SettingsManager.getSoftwareKeyboardMode(context)
        )
    }

    @Test
    fun navModeCtrlHold_defaultsDisabled_andPersistsEnabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(SettingsManager.getNavModeCtrlHoldEnabled(context))

        SettingsManager.setNavModeCtrlHoldEnabled(context, true)

        assertTrue(SettingsManager.getNavModeCtrlHoldEnabled(context))
    }

    @Test
    fun commaSpace_defaultsDisabled_andPersistsEnabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(SettingsManager.getCommaSpace(context))

        SettingsManager.setCommaSpace(context, true)

        assertTrue(SettingsManager.getCommaSpace(context))
    }

    @Test
    fun autoSpacePunctuation_defaultsOff_andPersistsSelection() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("", SettingsManager.getAutoSpacePunctuation(context))

        SettingsManager.setAutoSpacePunctuation(context, ".;:")

        assertEquals(".;:", SettingsManager.getAutoSpacePunctuation(context))
    }

    @Test
    fun autoSpacePunctuation_quoteRoundTripsWithoutEscapingArtifacts() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setAutoSpacePunctuation(context, "\"")

        assertEquals("\"", SettingsManager.getAutoSpacePunctuation(context))
    }

    @Test
    fun spaceAfterPunctuation_defaultsOffAndNormalizesSelection() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals("", SettingsManager.getSpaceAfterPunctuation(context))

        SettingsManager.setSpaceAfterPunctuation(context, "!?x?!")

        assertEquals("!?", SettingsManager.getSpaceAfterPunctuation(context))
    }

    @Test
    fun autoSpacePunctuation_legacyStoredDefaultWithoutSemicolonStaysWithoutSemicolon() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("auto_space_punctuation", ".,!?\\/\"")
            .commit()

        assertEquals(".,!?\\/\"", SettingsManager.getAutoSpacePunctuation(context))
    }

    @Test
    fun layoutAwareCtrlShortcuts_defaultsDisabled_andPersistsEnabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(SettingsManager.getLayoutAwareCtrlShortcutsEnabled(context))

        SettingsManager.setLayoutAwareCtrlShortcutsEnabled(context, true)

        assertTrue(SettingsManager.getLayoutAwareCtrlShortcutsEnabled(context))
    }

    @Test
    fun quickLauncherBehavior_defaultsPastiera_andPersistsNiagara() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(
            SettingsManager.QUICK_LAUNCHER_BEHAVIOR_PASTIERA,
            SettingsManager.getQuickLauncherBehavior(context)
        )

        SettingsManager.setQuickLauncherBehavior(context, SettingsManager.QUICK_LAUNCHER_BEHAVIOR_NIAGARA)

        assertEquals(
            SettingsManager.QUICK_LAUNCHER_BEHAVIOR_NIAGARA,
            SettingsManager.getQuickLauncherBehavior(context)
        )
    }

    @Test
    fun quickLauncherBehavior_unknownValueFallsBackToPastiera() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setQuickLauncherBehavior(context, "unknown")

        assertEquals(
            SettingsManager.QUICK_LAUNCHER_BEHAVIOR_PASTIERA,
            SettingsManager.getQuickLauncherBehavior(context)
        )
    }

    @Test
    fun launcherShortcut_writesCommandForm_andKeepsLegacyAppReadable() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setLauncherShortcut(context, android.view.KeyEvent.KEYCODE_B, "com.brave.browser", "Brave")

        val shortcut = SettingsManager.getLauncherShortcut(context, android.view.KeyEvent.KEYCODE_B)

        assertEquals(SettingsManager.LauncherShortcut.TYPE_COMMAND, shortcut?.type)
        assertEquals("app:com.brave.browser", shortcut?.commandId)
        assertEquals(CommandSourceId.Apps.storageValue, shortcut?.commandSource)
        assertEquals(CommandLaunchSpec.AppPackage("com.brave.browser"), shortcut?.commandLaunch)
    }

    @Test
    fun quickLauncherShortcut_isStoredAsPastieraCommandAndDetectedAsDefaultKey() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setQuickLauncherShortcut(context, android.view.KeyEvent.KEYCODE_SPACE)

        val shortcut = SettingsManager.getLauncherShortcut(context, android.view.KeyEvent.KEYCODE_SPACE)

        assertEquals(SettingsManager.LauncherShortcut.TYPE_COMMAND, shortcut?.type)
        assertEquals(PastieraCommandSource.COMMAND_QUICK_LAUNCHER, shortcut?.commandId)
        assertEquals(android.view.KeyEvent.KEYCODE_SPACE, SettingsManager.getQuickLauncherShortcutKey(context))
        assertTrue(SettingsManager.isQuickLauncherShortcut(context, android.view.KeyEvent.KEYCODE_SPACE))
        assertFalse(SettingsManager.isQuickLauncherShortcut(context, android.view.KeyEvent.KEYCODE_ENTER))
    }

    @Test
    fun tutorialQuickLauncherCheck_acceptsCommandBasedSpaceShortcut() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setQuickLauncherShortcut(context, android.view.KeyEvent.KEYCODE_SPACE)

        assertFalse(shouldShowQuickLauncherMappingConflict(context))
    }

    @Test
    fun tutorialQuickLauncherCheck_flagsNonQuickLauncherSpaceShortcut() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setLauncherShortcut(context, android.view.KeyEvent.KEYCODE_SPACE, "com.example.app", "Example")

        assertTrue(shouldShowQuickLauncherMappingConflict(context))
    }

    @Test
    fun legacyQuickLauncherShortcut_isDetectedAsQuickLauncherKey() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.setLauncherAction(
            context,
            android.view.KeyEvent.KEYCODE_K,
            SettingsManager.LauncherShortcut(type = SettingsManager.LauncherShortcut.TYPE_QUICK_LAUNCHER)
        )

        assertEquals(android.view.KeyEvent.KEYCODE_K, SettingsManager.getQuickLauncherShortcutKey(context))
        assertTrue(SettingsManager.isQuickLauncherShortcut(context, android.view.KeyEvent.KEYCODE_K))
    }

    @Test
    fun commandSourceVisibility_onlyFiltersQuickLauncherSearch() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.Apps.storageValue, CommandSurface.AssignedKey))
        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.Apps.storageValue, CommandSurface.QuickLauncher))
        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.Apps.storageValue, CommandSurface.NavMode))
        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.Pastiera.storageValue, CommandSurface.QuickLauncher))
        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.DeviceControl.storageValue, CommandSurface.AssignedKey))
        assertFalse(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.DeviceControl.storageValue, CommandSurface.QuickLauncher))
        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.DeviceControl.storageValue, CommandSurface.NavMode))
    }

    @Test
    fun initializeNavModeMappings_migratesEmptyCtrlBToKeyboardModeToggle() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.getNavModeMappingsFile(context).writeText(
            """
            {
              "mappings": {
                "KEYCODE_B": { "type": "none" },
                "KEYCODE_N": { "type": "action", "action": "custom_action" }
              }
            }
            """.trimIndent()
        )

        SettingsManager.initializeNavModeMappingsFile(context)

        val mappings = it.palsoftware.pastiera.data.mappings.KeyMappingLoader.loadCtrlKeyMappings(context.assets, context)
        assertEquals(
            it.palsoftware.pastiera.data.mappings.KeyMappingLoader.CtrlMapping(
                "command",
                PastieraCommandSource.COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE
            ),
            mappings[android.view.KeyEvent.KEYCODE_B]
        )
        assertEquals(
            it.palsoftware.pastiera.data.mappings.KeyMappingLoader.CtrlMapping("action", "custom_action"),
            mappings[android.view.KeyEvent.KEYCODE_N]
        )
    }

    @Test
    fun saveNavModeKeyMappings_preservesCommandMappings() {
        val context = RuntimeEnvironment.getApplication()

        SettingsManager.saveNavModeKeyMappings(
            context,
            mapOf(
                android.view.KeyEvent.KEYCODE_B to it.palsoftware.pastiera.data.mappings.KeyMappingLoader.CtrlMapping(
                    "command",
                    PastieraCommandSource.COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE
                ),
                android.view.KeyEvent.KEYCODE_K to it.palsoftware.pastiera.data.mappings.KeyMappingLoader.CtrlMapping(
                    "command",
                    "device.home"
                )
            )
        )

        val mappings = it.palsoftware.pastiera.data.mappings.KeyMappingLoader.loadCtrlKeyMappings(context.assets, context)
        assertEquals(
            it.palsoftware.pastiera.data.mappings.KeyMappingLoader.CtrlMapping(
                "command",
                PastieraCommandSource.COMMAND_TOGGLE_SOFTWARE_KEYBOARD_MODE
            ),
            mappings[android.view.KeyEvent.KEYCODE_B]
        )
        assertEquals(
            it.palsoftware.pastiera.data.mappings.KeyMappingLoader.CtrlMapping("command", "device.home"),
            mappings[android.view.KeyEvent.KEYCODE_K]
        )
    }
}
