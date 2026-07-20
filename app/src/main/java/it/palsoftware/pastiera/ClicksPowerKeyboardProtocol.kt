package it.palsoftware.pastiera

internal object ClicksPowerKeyboardProtocol {
    const val GROUP_READ_CONFIG = 0x25
    const val GROUP_WRITE_CONFIG = 0x24
    const val GROUP_READ_WIRELESS = 0x27
    const val GROUP_WRITE_WIRELESS = 0x26
    const val GROUP_READ_HOST_NAMES = 0x29
    const val GROUP_WRITE_HOST_NAME = 0x28

    const val COMMAND_BACKLIGHT_BRIGHTNESS = 0x02
    const val COMMAND_CHARGING_RESERVE = 0x03
    const val COMMAND_BACKLIGHT_TIMEOUT = 0x04
    const val COMMAND_IDLE_TIMEOUT = 0x06
    const val COMMAND_FEATURE_FLAGS = 0x08
    const val COMMAND_ACTIVE_HOST = 0x0C
    const val COMMAND_HOSTS_1_TO_4 = 0x0D
    const val COMMAND_HOSTS_5_TO_8 = 0x0E
    const val COMMAND_HOST_9 = 0x0F
    const val COMMAND_SPECIAL_KEY_ENABLE_FLAGS = 0x12
    const val COMMAND_NUMBER_KEY_ENABLE_FLAGS = 0x13
    const val COMMAND_TAB_REMAP = 0x16
    const val COMMAND_GEMINI_REMAP = 0x1A
    const val COMMAND_ALT_REMAP = 0x1E
    const val COMMAND_BACKSPACE_REMAP = 0x22

    const val FLAG_BACKLIGHT = 0x01
    const val FLAG_SYM_LOCK = 0x02
    const val FLAG_CAPS_LOCK = 0x04
    const val FLAG_SOFT_RETURN = 0x08
    const val FLAG_CURSOR_MODE = 0x10

    const val FLAG_TAB_REMAP_ENABLED = 0x01
    const val FLAG_GEMINI_REMAP_ENABLED = 0x02
    const val FLAG_ALT_REMAP_ENABLED = 0x04
    const val FLAG_BACKSPACE_REMAP_ENABLED = 0x08

    val NUMBER_REMAP_COMMANDS = intArrayOf(0x26, 0x2A, 0x2E, 0x32, 0x36, 0x3A, 0x3E, 0x42, 0x46)

    data class RemapTarget(
        val command: Int,
        val enableCommand: Int,
        val enableFlag: Int
    )

    data class Response(
        val group: Int,
        val status: Int,
        val payload: ByteArray
    )

    fun readConfig(command: Int, expectedLength: Int): ByteArray =
        frame(GROUP_READ_CONFIG, command, byteArrayOf(expectedLength.toByte()))

    fun writeConfig(command: Int, vararg payload: Int): ByteArray =
        frame(GROUP_WRITE_CONFIG, command, payload.map(Int::toByte).toByteArray())

    fun writeConfigBytes(command: Int, payload: ByteArray): ByteArray =
        frame(GROUP_WRITE_CONFIG, command, payload)

    fun remapTarget(command: Int): RemapTarget? = when (command) {
        COMMAND_TAB_REMAP -> RemapTarget(command, COMMAND_SPECIAL_KEY_ENABLE_FLAGS, FLAG_TAB_REMAP_ENABLED)
        COMMAND_GEMINI_REMAP -> RemapTarget(command, COMMAND_SPECIAL_KEY_ENABLE_FLAGS, FLAG_GEMINI_REMAP_ENABLED)
        COMMAND_ALT_REMAP -> RemapTarget(command, COMMAND_SPECIAL_KEY_ENABLE_FLAGS, FLAG_ALT_REMAP_ENABLED)
        COMMAND_BACKSPACE_REMAP -> RemapTarget(command, COMMAND_SPECIAL_KEY_ENABLE_FLAGS, FLAG_BACKSPACE_REMAP_ENABLED)
        else -> NUMBER_REMAP_COMMANDS.indexOf(command).takeIf { it >= 0 }?.let { index ->
            if (index < 4) {
                RemapTarget(command, COMMAND_SPECIAL_KEY_ENABLE_FLAGS, 0x10 shl index)
            } else {
                RemapTarget(command, COMMAND_NUMBER_KEY_ENABLE_FLAGS, 1 shl (index - 4))
            }
        }
    }

    fun readWirelessCharging(): ByteArray = frame(GROUP_READ_WIRELESS, null, byteArrayOf())

    fun readHostName(slotIndex: Int): ByteArray {
        require(slotIndex in 0..8)
        return readHostNames(slotIndex * HOST_NAME_BLOCK_SIZE, HOST_NAME_BLOCK_SIZE)
    }

    fun readHostNames(offset: Int, length: Int): ByteArray {
        require(offset in 0 until 9 * HOST_NAME_BLOCK_SIZE)
        require(length in 1..0xff && offset + length <= 9 * HOST_NAME_BLOCK_SIZE)
        return frame(
            GROUP_READ_HOST_NAMES,
            null,
            byteArrayOf((offset ushr 8).toByte(), offset.toByte(), length.toByte())
        )
    }

    fun writeHostName(slotIndex: Int, name: String): ByteArray {
        require(slotIndex in 0..8)
        val offset = slotIndex * HOST_NAME_BLOCK_SIZE
        return frame(
            GROUP_WRITE_HOST_NAME,
            null,
            byteArrayOf((offset ushr 8).toByte(), offset.toByte()) + encodeHostName(name)
        )
    }

    fun encodeHostName(name: String): ByteArray {
        val utf8 = ArrayList<Byte>(HOST_NAME_MAX_BYTES)
        var offset = 0
        while (offset < name.length) {
            val codePoint = name.codePointAt(offset)
            val encoded = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8)
            if (utf8.size + encoded.size > HOST_NAME_MAX_BYTES) break
            encoded.forEach(utf8::add)
            offset += Character.charCount(codePoint)
        }
        val block = ByteArray(HOST_NAME_BLOCK_SIZE)
        block[0] = utf8.size.toByte()
        utf8.forEachIndexed { index, byte -> block[index + 1] = byte }
        var checksum = 0
        repeat(HOST_NAME_BLOCK_SIZE - 1) { checksum = checksum xor (block[it].toInt() and 0xff) }
        block[HOST_NAME_BLOCK_SIZE - 1] = checksum.toByte()
        return block
    }

    fun normalizeHostName(name: String): String =
        decodeHostName(encodeHostName(name)).orEmpty()

    fun decodeHostName(block: ByteArray): String? {
        if (block.size != HOST_NAME_BLOCK_SIZE) return null
        var checksum = 0
        repeat(HOST_NAME_BLOCK_SIZE - 1) { checksum = checksum xor (block[it].toInt() and 0xff) }
        if (checksum.toByte() != block.last()) return null
        val length = block[0].toInt() and 0xff
        if (length > HOST_NAME_MAX_BYTES) return null
        return block.copyOfRange(1, 1 + length).toString(Charsets.UTF_8)
    }

    fun writeWirelessCharging(enabled: Boolean): ByteArray = frame(
        GROUP_WRITE_WIRELESS,
        null,
        byteArrayOf(0x00, 0x04, 0x00, if (enabled) 0x04 else 0x00)
    )

    fun littleEndian16(value: Int): ByteArray {
        require(value in 0..0xffff)
        return byteArrayOf(value.toByte(), (value ushr 8).toByte())
    }

    fun decodeLittleEndian16(payload: ByteArray): Int? =
        if (payload.size == 2) {
            (payload[0].toInt() and 0xff) or ((payload[1].toInt() and 0xff) shl 8)
        } else {
            null
        }

    fun parseResponse(bytes: ByteArray): Response? {
        if (bytes.size < 6 || bytes[0].toInt() and 0xff != 0xff) return null
        val declaredLength = bytes[1].toInt() and 0xff
        if (declaredLength != bytes.size - 2 || crc8(bytes.copyOf(bytes.size - 1)) != bytes.last()) return null
        if (bytes[2].toInt() and 0xff != 0x02) return null
        return Response(
            group = bytes[3].toInt() and 0xff,
            status = bytes[4].toInt() and 0xff,
            payload = bytes.copyOfRange(5, bytes.lastIndex)
        )
    }

    fun frame(group: Int, command: Int?, payload: ByteArray): ByteArray {
        val bodyLength = 1 + (if (command == null) 0 else 1) + payload.size + 1
        require(bodyLength <= 0xff)
        val withoutCrc = ByteArray(bodyLength + 1)
        withoutCrc[0] = 0xff.toByte()
        withoutCrc[1] = bodyLength.toByte()
        withoutCrc[2] = group.toByte()
        var offset = 3
        if (command != null) withoutCrc[offset++] = command.toByte()
        payload.copyInto(withoutCrc, offset)
        return withoutCrc + crc8(withoutCrc)
    }

    fun crc8(bytes: ByteArray): Byte {
        var crc = 0xff
        val start = if (bytes.size > 1 && bytes[0].toInt() and 0xff == 0xff) 1 else 0
        for (index in start until bytes.size) {
            crc = crc xor (bytes[index].toInt() and 0xff)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xb8 else crc ushr 1
                crc = crc and 0xff
            }
        }
        return crc.toByte()
    }

    private const val HOST_NAME_BLOCK_SIZE = 32
    private const val HOST_NAME_MAX_BYTES = 30
}
