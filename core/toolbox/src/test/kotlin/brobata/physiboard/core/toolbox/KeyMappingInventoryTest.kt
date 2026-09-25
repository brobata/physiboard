package brobata.physiboard.core.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals

class KeyMappingInventoryTest {
    private fun baseSnapshot() = KeyMappingSnapshot(
        fnEnable = null,
        fnFunction = null,
        fnLongPressSpeechOn = false,
        fnLongPressActivity = null,
        symLongPressAssistantOn = false,
        symTrackpadTriggerOn = false,
        orangeShortPressActivity = null,
        orangeDoublePressActivity = null,
        orangeLongPressPackage = null,
        orangeLongPressActivity = null,
        ownPackageName = "brobata.physiboard",
        spaceTrackpadTriggerOn = false,
        shiftRightRemapped = false,
        homeRemapped = false,
        recentAppsRemapped = false,
    )

    @Test
    fun `T51 Fn remapped to Ctrl with dictation on`() {
        val snapshot = baseSnapshot().copy(fnEnable = 1, fnFunction = 1, fnLongPressSpeechOn = true)
        val row = KeyMappingInventory.rows(snapshot).first { it.label == "Fn" }
        assertEquals("Acts as Ctrl · hold to dictate", row.bindingText)
    }

    @Test
    fun `T52 orange key tap tail plus assistant hold, no double-press configured`() {
        val snapshot = baseSnapshot().copy(
            orangeShortPressActivity = "com.x.FooActivity",
            orangeLongPressPackage = "brobata.physiboard",
        )
        val row = KeyMappingInventory.rows(snapshot).first { it.label == "Orange side key" }
        assertEquals("tap: FooActivity · hold: the assistant, listening", row.bindingText)
    }

    @Test
    fun `Fn shows the fn layer with a long-press activity when not remapped`() {
        val snapshot = baseSnapshot().copy(fnLongPressActivity = "brobata.physiboard.SomeActivity")
        val row = KeyMappingInventory.rows(snapshot).first { it.label == "Fn" }
        assertEquals("Fn layer · long press opens SomeActivity", row.bindingText)
    }

    @Test
    fun `orange key with nothing bound anywhere reads as nothing`() {
        val row = KeyMappingInventory.rows(baseSnapshot()).first { it.label == "Orange side key" }
        assertEquals("hold: nothing", row.bindingText)
    }

    @Test
    fun `orange key hold falls back to a tailed activity when not the app itself`() {
        val snapshot = baseSnapshot().copy(orangeLongPressPackage = "com.other.app", orangeLongPressActivity = "com.other.app.MainActivity")
        val row = KeyMappingInventory.rows(snapshot).first { it.label == "Orange side key" }
        assertEquals("hold: MainActivity", row.bindingText)
    }

    @Test
    fun `Sym mentions the assistant and trackpad when both are configured`() {
        val snapshot = baseSnapshot().copy(symLongPressAssistantOn = true, symTrackpadTriggerOn = true)
        val row = KeyMappingInventory.rows(snapshot).first { it.label == "Sym" }
        assertEquals("Symbol and emoji pages · hold for the assistant · hold for the trackpad", row.bindingText)
    }

    @Test
    fun `Space mentions the trackpad only when it is the trigger`() {
        val row = KeyMappingInventory.rows(baseSnapshot().copy(spaceTrackpadTriggerOn = true)).first { it.label == "Space" }
        assertEquals("Space · hold for the trackpad", row.bindingText)
    }

    @Test
    fun `remapped fixed keys report vendor remapping`() {
        val snapshot = baseSnapshot().copy(shiftRightRemapped = true, homeRemapped = true, recentAppsRemapped = true)
        val rows = KeyMappingInventory.rows(snapshot).associateBy { it.label }
        assertEquals("Vendor remapping enabled", rows.getValue("Right Shift").bindingText)
        assertEquals("Vendor remapping enabled", rows.getValue("Home").bindingText)
        assertEquals("Vendor remapping enabled", rows.getValue("Recent apps").bindingText)
    }

    @Test
    fun `every row is present including the fixed ones`() {
        val labels = KeyMappingInventory.rows(baseSnapshot()).map { it.label }
        assertEquals(
            listOf("Fn", "Sym", "Orange side key", "Space", "Right Shift", "Home", "Recent apps", "Back", "Volume up / down", "Power"),
            labels,
        )
    }
}
