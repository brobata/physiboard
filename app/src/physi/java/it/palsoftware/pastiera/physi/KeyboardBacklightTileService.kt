package it.palsoftware.pastiera.physi

import android.content.pm.PackageManager
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager

/**
 * Quick Settings tile that toggles the Unihertz keyboard backlight
 * (Settings.Global "agui_keyboard_background_light").
 *
 * Writing the global setting requires WRITE_SECURE_SETTINGS, granted once via:
 *   adb shell pm grant brobata.physiboard android.permission.WRITE_SECURE_SETTINGS
 */
class KeyboardBacklightTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!hasWritePermission()) {
            Toast.makeText(
                this,
                getString(R.string.backlight_tile_permission_missing),
                Toast.LENGTH_LONG
            ).show()
            updateTile()
            return
        }
        // Snapshot the device's ORIGINAL value ONCE before we first overwrite it, so the
        // "Reset device settings to stock" button can restore the exact prior state.
        val prior = Settings.Global.getInt(
            contentResolver,
            VENDOR_BACKLIGHT_SETTING,
            SettingsManager.QS_BACKLIGHT_VALUE_UNSET
        )
        SettingsManager.captureQsBacklightOriginalIfNeeded(this, prior)
        val newValue = if (readBacklightEnabled()) 0 else 1
        try {
            Settings.Global.putInt(contentResolver, VENDOR_BACKLIGHT_SETTING, newValue)
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to write keyboard backlight setting", e)
        }
        updateTile()
    }

    private fun readBacklightEnabled(): Boolean {
        return Settings.Global.getInt(contentResolver, VENDOR_BACKLIGHT_SETTING, 1) == 1
    }

    private fun hasWritePermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = readBacklightEnabled()
        tile.state = when {
            !hasWritePermission() -> Tile.STATE_UNAVAILABLE
            enabled -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.backlight_tile_label)
        tile.subtitle = if (!hasWritePermission()) {
            getString(R.string.backlight_tile_no_permission_subtitle)
        } else null
        tile.updateTile()
    }

    companion object {
        private const val TAG = "KbBacklightTile"
        private const val VENDOR_BACKLIGHT_SETTING = "agui_keyboard_background_light"
    }
}
