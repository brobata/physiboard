package brobata.physiboard.device.titan

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: keys-and-modifiers.md SS1.2 (the Elite row only, kept as a sanity check per SS22 Keep/Drop). */
class DeviceIdentityTest {

    @Test
    fun `an explicit titan2elite_qwerty marker in any field is the Elite`() {
        assertTrue(
            DeviceIdentity.isTitan2EliteQwerty(
                brand = "unihertz", manufacturer = "unihertz", model = "Titan 2",
                device = "titan2elite_qwerty", product = "titan2elite_qwerty", board = "g72", display = "Titan 2 Elite_V02.00.02",
            ),
        )
    }

    @Test
    fun `a Unihertz Titan fingerprint that leaks elite in the display string is the Elite`() {
        assertTrue(
            DeviceIdentity.isTitan2EliteQwerty(
                brand = "unihertz", manufacturer = "Unihertz", model = "Titan 2",
                device = "titan2", product = "titan2", board = "unknown_board", display = "Titan 2 Elite_V02.00.02",
            ),
        )
    }

    @Test
    fun `a Unihertz Titan fingerprint that leaks g72 in the board string is the Elite`() {
        assertTrue(
            DeviceIdentity.isTitan2EliteQwerty(
                brand = "unihertz", manufacturer = "Unihertz", model = "Titan 2",
                device = "titan2", product = "titan2", board = "g72boardv1", display = "Titan 2_EEA_V01",
            ),
        )
    }

    @Test
    fun `a plain Titan 2 (non-Elite) fingerprint with neither leak is not the Elite`() {
        assertFalse(
            DeviceIdentity.isTitan2EliteQwerty(
                brand = "unihertz", manufacturer = "Unihertz", model = "Titan 2",
                device = "titan2", product = "titan2", board = "g71boardv1", display = "Titan 2_EEA_V01",
            ),
        )
    }

    @Test
    fun `an unrelated phone is not the Elite`() {
        assertFalse(
            DeviceIdentity.isTitan2EliteQwerty(
                brand = "google", manufacturer = "Google", model = "Pixel 8",
                device = "shiba", product = "shiba", board = "shiba", display = "Pixel 8",
            ),
        )
    }
}
