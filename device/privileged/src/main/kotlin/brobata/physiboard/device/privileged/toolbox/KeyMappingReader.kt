package brobata.physiboard.device.privileged.toolbox

import brobata.physiboard.core.pointer.trackpad.TriggerKey
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.toolbox.KeyMappingRow
import brobata.physiboard.core.toolbox.KeyMappingSnapshot
import brobata.physiboard.device.privileged.setup.AppIdentity
import brobata.physiboard.device.privileged.setup.SystemSettingsAccess
import brobata.physiboard.device.privileged.setup.VendorKeyRows

/**
 * Builds the Key mapping screen's [KeyMappingSnapshot] from the two places a binding lives: the
 * vendor's plain `Settings.System` rows (no broker needed, SS15) and PhysiBoard's own settings.
 * Read once, when the screen is entered.
 *
 * spec: broker-privileged-toolbox.md SS15.
 */
class KeyMappingReader(private val settings: SystemSettingsAccess, private val identity: AppIdentity) {
    fun read(current: Settings): List<KeyMappingRow> {
        val trackpad = current.trackpad
        val dictation = current.dictation
        val snapshot = KeyMappingSnapshot(
            fnEnable = settings.getInt(VendorKeyRows.FN_ENABLE),
            fnFunction = settings.getInt(VendorKeyRows.FN_FUNCTION),
            fnLongPressSpeechOn = dictation.fnLongPressSpeech,
            fnLongPressActivity = settings.getString(FN_LONG_PRESS_ACTIVITY),
            symLongPressAssistantOn = dictation.symLongPressAssistant,
            symTrackpadTriggerOn = trackpad.enabled && trackpad.triggerKey == TriggerKey.SYM,
            orangeShortPressActivity = settings.getString(ORANGE_SHORT_PRESS_ACTIVITY),
            orangeDoublePressActivity = settings.getString(ORANGE_DOUBLE_PRESS_ACTIVITY),
            orangeLongPressPackage = settings.getString(VendorKeyRows.SIDE_KEY_PACKAGE),
            orangeLongPressActivity = settings.getString(VendorKeyRows.SIDE_KEY_ACTIVITY),
            ownPackageName = identity.packageName,
            spaceTrackpadTriggerOn = trackpad.enabled && trackpad.triggerKey == TriggerKey.SPACE,
            shiftRightRemapped = settings.getInt(SHIFT_RIGHT_ENABLE) == 1,
            homeRemapped = settings.getInt(HOME_ENABLE) == 1,
            recentAppsRemapped = settings.getInt(RECENT_ENABLE) == 1,
        )
        return brobata.physiboard.core.toolbox.KeyMappingInventory.rows(snapshot)
    }

    companion object {
        /** spec: keys-and-modifiers.md SS3.6 ("`fn_long_press_activity`"). */
        const val FN_LONG_PRESS_ACTIVITY = "fn_long_press_activity"
        const val ORANGE_SHORT_PRESS_ACTIVITY = "func1_short_press_activity"
        const val ORANGE_DOUBLE_PRESS_ACTIVITY = "func1_double_press_activity"
        const val SHIFT_RIGHT_ENABLE = "shift_r_programmable_key_enable"
        const val HOME_ENABLE = "home_programmable_key_enable"
        const val RECENT_ENABLE = "recent_programmable_key_enable"
    }
}
