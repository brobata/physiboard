package brobata.physiboard.device.privileged.toolbox

import android.os.Build
import brobata.physiboard.device.titan.DeviceIdentity

/** Remove bloat's device gate: the catalogue removes packages by name, so it stays off any phone that is not the one it was written for. spec: broker-privileged-toolbox.md SS12.1. */
fun interface DeviceProfile {
    fun isTitan2Elite(): Boolean
}

/** [DeviceIdentity.isTitan2EliteQwerty] over the real `Build` fields. */
object AndroidDeviceProfile : DeviceProfile {
    override fun isTitan2Elite(): Boolean = DeviceIdentity.isTitan2EliteQwerty(
        brand = Build.BRAND ?: "",
        manufacturer = Build.MANUFACTURER ?: "",
        model = Build.MODEL ?: "",
        device = Build.DEVICE ?: "",
        product = Build.PRODUCT ?: "",
        board = Build.BOARD ?: "",
        display = Build.DISPLAY ?: "",
    )
}
