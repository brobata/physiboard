package it.palsoftware.pastiera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClicksPowerKeyboardProtocolTest {
    @Test
    fun wirelessChargingFrame_matchesCapturedTraffic() {
        assertArrayEquals(
            hex("ff 06 26 00 04 00 04 b4"),
            ClicksPowerKeyboardProtocol.writeWirelessCharging(true)
        )
    }

    @Test
    fun capturedSuccessResponse_isParsedAndValidated() {
        val response = ClicksPowerKeyboardProtocol.parseResponse(hex("ff 04 02 26 00 7c"))

        assertEquals(0x26, response?.group)
        assertEquals(0, response?.status)
        assertArrayEquals(byteArrayOf(), response?.payload)
    }

    @Test
    fun corruptResponse_isRejected() {
        assertNull(ClicksPowerKeyboardProtocol.parseResponse(hex("ff 04 02 26 00 00")))
    }

    @Test
    fun configFrames_useReadAndWriteGroups() {
        val read = ClicksPowerKeyboardProtocol.readConfig(
            ClicksPowerKeyboardProtocol.COMMAND_BACKLIGHT_BRIGHTNESS,
            1
        )
        val write = ClicksPowerKeyboardProtocol.writeConfig(
            ClicksPowerKeyboardProtocol.COMMAND_BACKLIGHT_BRIGHTNESS,
            0x80
        )

        assertEquals(0x25, read[2].toInt() and 0xff)
        assertEquals(0x24, write[2].toInt() and 0xff)
        assertEquals(0x02, read[3].toInt() and 0xff)
        assertEquals(0x80, write[4].toInt() and 0xff)
    }

    @Test
    fun capturedConfigFrames_matchExactly() {
        assertArrayEquals(
            hex("ff 04 25 02 01 6b"),
            ClicksPowerKeyboardProtocol.readConfig(
                ClicksPowerKeyboardProtocol.COMMAND_BACKLIGHT_BRIGHTNESS,
                1
            )
        )
        assertArrayEquals(
            hex("ff 05 24 04 88 13 38"),
            ClicksPowerKeyboardProtocol.writeConfigBytes(
                ClicksPowerKeyboardProtocol.COMMAND_BACKLIGHT_TIMEOUT,
                ClicksPowerKeyboardProtocol.littleEndian16(5_000)
            )
        )
        assertArrayEquals(
            hex("ff 04 24 12 fd bd"),
            ClicksPowerKeyboardProtocol.writeConfig(
                ClicksPowerKeyboardProtocol.COMMAND_SPECIAL_KEY_ENABLE_FLAGS,
                0xfd
            )
        )
        assertArrayEquals(
            hex("ff 05 24 16 e0 2c 49"),
            ClicksPowerKeyboardProtocol.writeConfigBytes(
                ClicksPowerKeyboardProtocol.COMMAND_TAB_REMAP,
                hex("e0 2c")
            )
        )
        assertArrayEquals(
            hex("ff 04 24 02 7f 97"),
            ClicksPowerKeyboardProtocol.writeConfig(
                ClicksPowerKeyboardProtocol.COMMAND_BACKLIGHT_BRIGHTNESS,
                0x7f
            )
        )
        assertArrayEquals(
            hex("ff 04 24 03 28 02"),
            ClicksPowerKeyboardProtocol.writeConfig(
                ClicksPowerKeyboardProtocol.COMMAND_CHARGING_RESERVE,
                40
            )
        )
        assertArrayEquals(
            hex("ff 05 24 06 08 07 fa"),
            ClicksPowerKeyboardProtocol.writeConfigBytes(
                ClicksPowerKeyboardProtocol.COMMAND_IDLE_TIMEOUT,
                ClicksPowerKeyboardProtocol.littleEndian16(30 * 60)
            )
        )
        assertArrayEquals(
            hex("ff 05 24 26 4a 00 99"),
            ClicksPowerKeyboardProtocol.writeConfigBytes(
                ClicksPowerKeyboardProtocol.NUMBER_REMAP_COMMANDS[0],
                hex("4a 00")
            )
        )
        assertArrayEquals(
            hex("ff 05 24 26 29 00 f0"),
            ClicksPowerKeyboardProtocol.writeConfigBytes(
                ClicksPowerKeyboardProtocol.NUMBER_REMAP_COMMANDS[0],
                hex("29 00")
            )
        )
    }

    @Test
    fun everyDocumentedRemap_hasTheCorrectEnableMask() {
        assertEquals(0x01, ClicksPowerKeyboardProtocol.remapTarget(0x16)?.enableFlag)
        assertEquals(0x08, ClicksPowerKeyboardProtocol.remapTarget(0x22)?.enableFlag)
        assertEquals(0x10, ClicksPowerKeyboardProtocol.remapTarget(0x26)?.enableFlag)
        assertEquals(0x80, ClicksPowerKeyboardProtocol.remapTarget(0x32)?.enableFlag)
        assertEquals(
            ClicksPowerKeyboardProtocol.COMMAND_NUMBER_KEY_ENABLE_FLAGS,
            ClicksPowerKeyboardProtocol.remapTarget(0x36)?.enableCommand
        )
        assertEquals(0x10, ClicksPowerKeyboardProtocol.remapTarget(0x46)?.enableFlag)
        assertNull(ClicksPowerKeyboardProtocol.remapTarget(0x47))
    }

    @Test
    fun hostNameFrames_matchCapturedReadsAndRoundTripUtf8() {
        assertArrayEquals(
            hex("ff 05 29 00 60 60 15"),
            ClicksPowerKeyboardProtocol.readHostNames(0x60, 0x60)
        )
        val name = "Pixel 🍝 mit einem sehr langen Namen"
        val block = ClicksPowerKeyboardProtocol.encodeHostName(name)

        assertEquals(32, block.size)
        assertEquals("Pixel 🍝 mit einem sehr lang", ClicksPowerKeyboardProtocol.decodeHostName(block))
        assertEquals("Pixel 🍝 mit einem sehr lang", ClicksPowerKeyboardProtocol.normalizeHostName(name))
        assertEquals(38, ClicksPowerKeyboardProtocol.writeHostName(3, name).size)
    }

    @Test
    fun littleEndianValues_roundTrip() {
        val bytes = ClicksPowerKeyboardProtocol.littleEndian16(60_000)
        assertArrayEquals(byteArrayOf(0x60, 0xea.toByte()), bytes)
        assertEquals(60_000, ClicksPowerKeyboardProtocol.decodeLittleEndian16(bytes))
    }

    private fun hex(value: String): ByteArray = value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
