package it.palsoftware.pastiera.inputmethod.trackpad

object TrackpadEventDeviceResolver {
    const val LEGACY_EVENT_DEVICE = "/dev/input/event7"
    const val UPDATED_TITAN2_EVENT_DEVICE = "/dev/input/event6"
    const val UPDATED_TITAN2_MIN_FIRMWARE = "V01.00.12"

    fun resolve(
        physicalKeyboardName: String,
        firmwareIncremental: String
    ): String {
        if (!physicalKeyboardName.equals("titan2", ignoreCase = true)) {
            return LEGACY_EVENT_DEVICE
        }
        return if (isFirmwareAtLeast(firmwareIncremental, UPDATED_TITAN2_MIN_FIRMWARE)) {
            UPDATED_TITAN2_EVENT_DEVICE
        } else {
            LEGACY_EVENT_DEVICE
        }
    }

    internal fun isFirmwareAtLeast(current: String, minimum: String): Boolean {
        val currentParts = parseFirmwareVersionParts(current) ?: return false
        val minimumParts = parseFirmwareVersionParts(minimum) ?: return false
        for (index in 0..2) {
            if (currentParts[index] > minimumParts[index]) return true
            if (currentParts[index] < minimumParts[index]) return false
        }
        return true
    }

    private fun parseFirmwareVersionParts(version: String): List<Int>? {
        val match = Regex("V(\\d+)\\.(\\d+)\\.(\\d+)", RegexOption.IGNORE_CASE).find(version)
            ?: return null
        return match.groupValues.drop(1).map { it.toInt() }
    }
}
