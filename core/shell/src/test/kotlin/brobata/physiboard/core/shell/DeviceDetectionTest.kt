package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: app-shell.md SS27 D1, D2. */
class DeviceDetectionTest {

    private fun fields(brand: String = "", manufacturer: String = "", model: String = "", device: String = "", product: String = "", board: String = "", display: String = "") =
        DeviceFields(brand, manufacturer, model, device, product, board, display)

    @Test
    fun `the exact elite model string is recognized`() {
        assertEquals(TitanModel.TITAN_2_ELITE, DeviceDetection.classify(fields(model = "titan2elite_qwerty")))
    }

    @Test
    fun `a reviewer unit is recognized through board or display leaking elite`() {
        assertEquals(TitanModel.TITAN_2_ELITE, DeviceDetection.classify(fields(brand = "unihertz", board = "g72")))
        assertEquals(TitanModel.TITAN_2_ELITE, DeviceDetection.classify(fields(brand = "unihertz", display = "titan2 elite panel")))
    }

    @Test
    fun `a plain titan 2 is recognized without any elite trait`() {
        assertEquals(TitanModel.TITAN_2, DeviceDetection.classify(fields(brand = "unihertz", model = "titan 2")))
    }

    @Test
    fun `an unrelated phone is neither`() {
        assertEquals(TitanModel.OTHER, DeviceDetection.classify(fields(brand = "google", model = "pixel 9")))
    }

    @Test
    fun `T26 the bug report labels match the template's dropdown exactly`() {
        assertEquals("Unihertz Titan 2 Elite", DeviceDetection.bugReportLabel(TitanModel.TITAN_2_ELITE))
        assertEquals("Unihertz Titan 2 (untested/unsupported)", DeviceDetection.bugReportLabel(TitanModel.TITAN_2))
        assertEquals("Something else", DeviceDetection.bugReportLabel(TitanModel.OTHER))
    }
}
