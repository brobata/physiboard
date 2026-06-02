package it.palsoftware.pastiera

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
import it.palsoftware.pastiera.commands.CommandSourceId
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.commands.PastieraCommandSource

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
    }

    @Test
    fun altShiftLayoutSwitch_defaultsEnabled_andPersistsDisabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.isAltShiftLayoutSwitchEnabled(context))

        SettingsManager.setAltShiftLayoutSwitchEnabled(context, false)

        assertFalse(SettingsManager.isAltShiftLayoutSwitchEnabled(context))
    }

    @Test
    fun ctrlSpaceLayoutSwitch_defaultsEnabled_andPersistsDisabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.isCtrlSpaceLayoutSwitchEnabled(context))

        SettingsManager.setCtrlSpaceLayoutSwitchEnabled(context, false)

        assertFalse(SettingsManager.isCtrlSpaceLayoutSwitchEnabled(context))
    }

    @Test
    fun layoutSwitchToast_defaultsEnabled_andPersistsDisabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.isToastOnLayoutSwitchEnabled(context))

        SettingsManager.setToastOnLayoutSwitchEnabled(context, false)

        assertFalse(SettingsManager.isToastOnLayoutSwitchEnabled(context))
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
    fun navModeCtrlHold_defaultsDisabled_andPersistsEnabledState() {
        val context = RuntimeEnvironment.getApplication()

        assertFalse(SettingsManager.getNavModeCtrlHoldEnabled(context))

        SettingsManager.setNavModeCtrlHoldEnabled(context, true)

        assertTrue(SettingsManager.getNavModeCtrlHoldEnabled(context))
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
    }

    @Test
    fun commandSourceVisibility_onlyFiltersQuickLauncherSearch() {
        val context = RuntimeEnvironment.getApplication()

        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.Apps.storageValue, CommandSurface.AssignedKey))
        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.Apps.storageValue, CommandSurface.QuickLauncher))
        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.Apps.storageValue, CommandSurface.NavMode))
        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.DeviceControl.storageValue, CommandSurface.AssignedKey))
        assertFalse(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.DeviceControl.storageValue, CommandSurface.QuickLauncher))
        assertTrue(SettingsManager.isCommandSourceEnabled(context, CommandSourceId.DeviceControl.storageValue, CommandSurface.NavMode))
    }
}
