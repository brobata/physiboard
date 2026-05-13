package it.palsoftware.pastiera.inputmethod.trackpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackpadEventDeviceResolverTest {
    @Test
    fun `non Titan 2 uses legacy event device regardless of firmware`() {
        assertEquals(
            TrackpadEventDeviceResolver.LEGACY_EVENT_DEVICE,
            TrackpadEventDeviceResolver.resolve(
                physicalKeyboardName = "titan",
                firmwareIncremental = "V99.99.99"
            )
        )
    }

    @Test
    fun `Titan 2 firmware before V01_00_14 uses legacy event device`() {
        listOf(
            "V01.00.11",
            "V01.00.12",
            "Titan 2_EEA_V01.00.12-20260206"
        ).forEach { firmware ->
            assertEquals(
                firmware,
                TrackpadEventDeviceResolver.LEGACY_EVENT_DEVICE,
                TrackpadEventDeviceResolver.resolve(
                    physicalKeyboardName = "titan2",
                    firmwareIncremental = firmware
                )
            )
        }
    }

    @Test
    fun `Titan 2 firmware V01_00_14 and newer uses updated event device`() {
        listOf(
            "V01.00.14",
            "Titan 2_EEA_V01.00.14-20260413",
            "Titan 2_TEE_V01.00.14-20260422"
        ).forEach { firmware ->
            assertEquals(
                firmware,
                TrackpadEventDeviceResolver.UPDATED_TITAN2_EVENT_DEVICE,
                TrackpadEventDeviceResolver.resolve(
                    physicalKeyboardName = "titan2",
                    firmwareIncremental = firmware
                )
            )
        }
    }

    @Test
    fun `firmware parsing compares semantic version parts`() {
        assertFalse(TrackpadEventDeviceResolver.isFirmwareAtLeast("V01.00.12", "V01.00.14"))
        assertTrue(TrackpadEventDeviceResolver.isFirmwareAtLeast("V01.00.14", "V01.00.14"))
        assertTrue(TrackpadEventDeviceResolver.isFirmwareAtLeast("V01.01.00", "V01.00.14"))
    }

    @Test
    fun `unparseable Titan 2 firmware falls back to legacy event device`() {
        assertEquals(
            TrackpadEventDeviceResolver.LEGACY_EVENT_DEVICE,
            TrackpadEventDeviceResolver.resolve(
                physicalKeyboardName = "titan2",
                firmwareIncremental = "unknown"
            )
        )
    }
}
