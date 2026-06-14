package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import it.palsoftware.pastiera.commands.CommandLaunchSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppPackageChangeMonitorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AppListHelper.invalidateInstalledApps()
    }

    @Test
    fun packageChange_invalidatesCachedInstalledApps() {
        AppListHelper.refreshInstalledApps(context)

        AppListHelper.handlePackagesChanged(
            context = context,
            packageNames = listOf("com.example.newapp")
        )

        assertNull(AppListHelper.getCachedInstalledApps())
    }

    @Test
    fun packageRemoved_cleansLauncherShortcutForRemovedApp() {
        SettingsManager.setLauncherCommand(
            context = context,
            keyCode = KeyEvent.KEYCODE_B,
            commandId = "app:com.example.removed",
            source = "apps",
            kind = "App",
            title = "Removed",
            subtitle = "com.example.removed",
            launch = CommandLaunchSpec.AppPackage("com.example.removed")
        )

        AppPackageChangeMonitor.handlePackageIntent(
            context,
            Intent(Intent.ACTION_PACKAGE_REMOVED, Uri.parse("package:com.example.removed"))
        )

        assertEquals(null, SettingsManager.getLauncherShortcut(context, KeyEvent.KEYCODE_B))
    }
}
