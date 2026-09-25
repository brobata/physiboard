package brobata.physiboard.device.privileged.toolbox

import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.device.privileged.setup.AppIdentity
import brobata.physiboard.device.privileged.setup.FakeSystemSettingsAccess
import brobata.physiboard.device.privileged.setup.VendorKeyRows
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyMappingReaderTest {
    private val identity = AppIdentity("brobata.physiboard", "brobata.physiboard/.Ring")

    @Test
    fun `T51 Fn remapped to Ctrl with dictation speech on reads through from Settings_System and dictation prefs`() {
        val settings = FakeSystemSettingsAccess().apply {
            put(VendorKeyRows.FN_ENABLE, 1)
            put(VendorKeyRows.FN_FUNCTION, 1)
        }
        val current = Settings(dictation = Settings().dictation.copy(fnLongPressSpeech = true))
        val rows = KeyMappingReader(settings, identity).read(current)
        assertEquals("Acts as Ctrl · hold to dictate", rows.first { it.label == "Fn" }.bindingText)
    }

    @Test
    fun `T52 orange key tap tail plus assistant hold when the long-press slot points at this app`() {
        val settings = FakeSystemSettingsAccess().apply {
            put(KeyMappingReader.ORANGE_SHORT_PRESS_ACTIVITY, "com.x.FooActivity")
            put(VendorKeyRows.SIDE_KEY_PACKAGE, identity.packageName)
        }
        val rows = KeyMappingReader(settings, identity).read(Settings())
        assertEquals("tap: FooActivity · hold: the assistant, listening", rows.first { it.label == "Orange side key" }.bindingText)
    }

    @Test
    fun `Space reads the trackpad trigger from the settings snapshot`() {
        val current = Settings(trackpad = Settings().trackpad.copy(enabled = true, triggerKey = TriggerKey.SPACE))
        val rows = KeyMappingReader(FakeSystemSettingsAccess(), identity).read(current)
        assertEquals("Space · hold for the trackpad", rows.first { it.label == "Space" }.bindingText)
    }

    @Test
    fun `every fixed row is present`() {
        val rows = KeyMappingReader(FakeSystemSettingsAccess(), identity).read(Settings())
        assertEquals(10, rows.size)
    }
}
