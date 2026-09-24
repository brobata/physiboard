package brobata.physiboard.device.privileged.backlight

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import brobata.physiboard.device.privileged.PrivilegedServices

/**
 * The Quick Settings "Keyboard light" tile. It flips the vendor's master switch with the
 * broker-granted `WRITE_SECURE_SETTINGS`; the decisions ([KeyboardBacklightTile]) are pure and
 * tested, this class only reads the switch, applies the tap and refreshes the tile.
 *
 * Registered in this module's manifest under the name it compiles to,
 * `brobata.physiboard.device.privileged.backlight.KeyboardBacklightTileService`: 2.x declared it
 * under a package the class had left, and the system could not instantiate it (spec SS2.2
 * "Known bug", SS11 "keep, fixed ... must be verified on the phone"). That verification still
 * needs the phone.
 *
 * spec: device-backlight-ring.md SS2.2.
 */
class KeyboardBacklightTileService : TileService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening() {
        runCatching { refresh() }.onFailure { Log.e(TAG, "refresh crashed", it) }
    }

    override fun onClick() {
        runCatching { tap() }.onFailure { Log.e(TAG, "tap crashed", it) }
    }

    /** spec: SS2.2 step 1. */
    private fun refresh() {
        val tile = qsTile ?: return
        val services = PrivilegedServices.from(this)
        val switch = services?.masterSwitch ?: GlobalMasterSwitchAccess(this)
        val granted = services?.permissions?.hasWriteSecureSettings() ?: false
        tile.label = KeyboardBacklightTile.LABEL
        tile.subtitle = KeyboardBacklightTile.subtitle(granted)
        tile.state = when {
            !granted -> Tile.STATE_UNAVAILABLE
            KeyboardBacklightTile.isActive(switch.read()) -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    /** spec: SS2.2 steps 2 and 3. */
    private fun tap() {
        val services = PrivilegedServices.from(this)
        if (services == null || !services.permissions.hasWriteSecureSettings()) {
            Toast.makeText(this, KeyboardBacklightTile.grantToast(packageName), Toast.LENGTH_LONG).show()
            refresh()
            return
        }
        services.worker.execute {
            runCatching {
                val decision = KeyboardBacklightTile.decideTap(services.masterSwitch.read(), services.store.snapshot().captures)
                services.store.update { it.copy(captures = decision.captures) }
                services.masterSwitch.write(decision.writeValue)
            }.onFailure { Log.e(TAG, "tile write crashed", it) }
            main.post { runCatching { refresh() }.onFailure { Log.e(TAG, "refresh crashed", it) } }
        }
    }

    private companion object {
        const val TAG = "KeyboardLightTile"
    }
}
