package brobata.physiboard.app.shell

import android.os.Build
import brobata.physiboard.core.shell.DeviceDetection
import brobata.physiboard.core.shell.DeviceFields
import brobata.physiboard.core.shell.TitanModel

/** Reads `Build.*` and hands the lower-cased fields to [DeviceDetection]. spec: app-shell.md SS27. */
object DeviceDetectionAndroid {
    fun currentFields(): DeviceFields = DeviceFields(
        brand = Build.BRAND.lowercase(),
        manufacturer = Build.MANUFACTURER.lowercase(),
        model = Build.MODEL.lowercase(),
        device = Build.DEVICE.lowercase(),
        product = Build.PRODUCT.lowercase(),
        board = Build.BOARD.lowercase(),
        display = Build.DISPLAY.lowercase(),
    )

    fun classify(): TitanModel = DeviceDetection.classify(currentFields())
}
