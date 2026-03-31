package it.palsoftware.pastiera.inputmethod

import android.os.Build
import android.view.KeyEvent

object DeviceSpecific {
    data class RemappedHardwareEvent(
        val keyCode: Int,
        val event: KeyEvent?
    )

    private enum class KeyboardFamily {
        BLACKBERRY,
        UNIHERTZ,
        UNKNOWN
    }

    private enum class KeyboardModel {
        Q25,
        KEY2,
        TITAN_2,
        TITAN_POCKET,
        TITAN_SLIM,
        TITAN_ORIGINAL,
        UNKNOWN
    }

    private data class DeviceProfile(
        val family: KeyboardFamily,
        val model: KeyboardModel,
        val physicalLayoutName: String,
        val needsEventRemapping: Boolean
    )

    private var testBuildFingerprintOverride: BuildFingerprint? = null

    // Unihertz scan codes (Titan2)
    private const val SCANCODE_TITAN2_CTRL: Int = 251
    private const val SCANCODE_TITAN2_SYM: Int = 253

    private const val KEYCODE_CTRL: Int = KeyEvent.KEYCODE_CTRL_LEFT
    private const val KEYCODE_SYM: Int = KeyEvent.KEYCODE_SYM
    private const val KEYCODE_Q25_CTRL: Int = KeyEvent.KEYCODE_SHIFT_RIGHT
    private const val KEYCODE_Q25_SYM: Int = KeyEvent.KEYCODE_ALT_RIGHT
    private const val SCANCODE_KEY2_W: Int = 17
    private const val SCANCODE_KEY2_Z: Int = 44
    private const val SCANCODE_KEY2_M: Int = 50

    private const val RELOADABLE_META_MASK: Int =
        KeyEvent.META_SHIFT_MASK or
            KeyEvent.META_ALT_MASK or
            KeyEvent.META_CTRL_MASK or
            KeyEvent.META_SYM_ON

    private const val META_Q25_SHIFT: Int = KeyEvent.META_SHIFT_LEFT_ON
    private const val META_Q25_ALT: Int = KeyEvent.META_ALT_LEFT_ON
    private const val META_Q25_CTRL: Int = KeyEvent.META_SHIFT_RIGHT_ON
    private const val META_Q25_SYM: Int = KeyEvent.META_ALT_RIGHT_ON
    private const val META_Q25_CTRL_OR_SYM: Int = META_Q25_CTRL or META_Q25_SYM

    private const val META_SHIFT: Int = KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON
    private const val META_ALT: Int = KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_ON
    private const val META_CTRL: Int = KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_ON
    private const val META_SYM: Int = KeyEvent.META_SYM_ON

    private var lastQ25MetaState: Int = 0

    fun needsRemapping(): Boolean = currentDeviceProfile().needsEventRemapping

    fun remapHardwareKeyEvent(keyCode: Int, event: KeyEvent?): RemappedHardwareEvent {
        return when (currentDeviceProfile().model) {
            KeyboardModel.Q25 -> remapQ25KeyEvent(keyCode, event)
            KeyboardModel.KEY2 -> remapKey2KeyEvent(keyCode, event)
            else -> RemappedHardwareEvent(keyCode, event)
        }
    }

    // Backward-compatible API used by existing callers.
    fun remapKeyEvent(keyCode: Int, event: KeyEvent?): Pair<Int, KeyEvent?>? {
        val remapped = remapHardwareKeyEvent(keyCode, event)
        if (remapped.keyCode == keyCode && remapped.event === event) {
            return null
        }
        return remapped.keyCode to remapped.event
    }

    private fun remapQ25KeyEvent(keyCode: Int, event: KeyEvent?): RemappedHardwareEvent {
        if (!shouldRemapQ25Event(keyCode, event)) {
            return RemappedHardwareEvent(keyCode, event)
        }

        val normalizedKeyCode = when (keyCode) {
            KEYCODE_Q25_CTRL -> KEYCODE_CTRL
            KEYCODE_Q25_SYM -> KEYCODE_SYM
            else -> keyCode
        }

        return RemappedHardwareEvent(
            keyCode = normalizedKeyCode,
            event = patchQ25MetaState(event)
        )
    }

    private fun remapKey2KeyEvent(keyCode: Int, event: KeyEvent?): RemappedHardwareEvent {
        val normalizedKeyCode = normalizeKey2KeyCode(keyCode, event?.scanCode ?: -1)
        if (event == null || normalizedKeyCode == keyCode) {
            return RemappedHardwareEvent(normalizedKeyCode, event)
        }

        return RemappedHardwareEvent(
            keyCode = normalizedKeyCode,
            event = KeyEvent(
                event.downTime,
                event.eventTime,
                event.action,
                normalizedKeyCode,
                event.repeatCount,
                event.metaState,
                event.deviceId,
                event.scanCode,
                event.flags,
                event.source
            )
        )
    }

    private fun normalizeKey2KeyCode(keyCode: Int, scanCode: Int): Int {
        return when (scanCode) {
            // Some BBF100-4 / LineageOS builds report these keys with layout-shifted keycodes.
            // Normalize by scan code so physical key behavior stays consistent.
            SCANCODE_KEY2_M -> KeyEvent.KEYCODE_M
            SCANCODE_KEY2_W -> KeyEvent.KEYCODE_W
            SCANCODE_KEY2_Z -> KeyEvent.KEYCODE_Z
            else -> keyCode
        }
    }

    private fun shouldRemapQ25Event(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KEYCODE_Q25_CTRL || keyCode == KEYCODE_Q25_SYM) {
            return true
        }
        if (event == null) {
            return false
        }
        val combinedMetaState = event.metaState or lastQ25MetaState
        return (combinedMetaState and META_Q25_CTRL_OR_SYM) != 0
    }

    private fun patchQ25MetaState(event: KeyEvent?): KeyEvent? {
        if (event == null) {
            return null
        }

        val currentMetaState = event.metaState
        val combinedMetaState = currentMetaState or lastQ25MetaState
        if ((combinedMetaState and META_Q25_CTRL_OR_SYM) == 0) {
            lastQ25MetaState = currentMetaState
            return event
        }
        lastQ25MetaState = currentMetaState

        val normalizedMetaState = rebuildNormalizedMetaState(currentMetaState)
        val normalizedKeyCode = when (event.keyCode) {
            KEYCODE_Q25_CTRL -> KEYCODE_CTRL
            KEYCODE_Q25_SYM -> KEYCODE_SYM
            else -> event.keyCode
        }
        val normalizedScanCode = when (event.keyCode) {
            KEYCODE_Q25_CTRL -> SCANCODE_TITAN2_CTRL
            KEYCODE_Q25_SYM -> SCANCODE_TITAN2_SYM
            else -> event.scanCode
        }

        if (
            normalizedMetaState == currentMetaState &&
            normalizedKeyCode == event.keyCode &&
            normalizedScanCode == event.scanCode
        ) {
            return event
        }

        return KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            normalizedKeyCode,
            event.repeatCount,
            normalizedMetaState,
            event.deviceId,
            normalizedScanCode,
            event.flags,
            event.source
        )
    }

    private fun rebuildNormalizedMetaState(metaState: Int): Int {
        val mappedShift = if ((metaState and META_Q25_SHIFT) != 0) META_SHIFT else 0
        val mappedCtrl = if ((metaState and META_Q25_CTRL) != 0) META_CTRL else 0
        val mappedAlt = if ((metaState and META_Q25_ALT) != 0) META_ALT else 0
        val mappedSym = if ((metaState and META_Q25_SYM) != 0) META_SYM else 0
        val mappedMetaState = mappedShift or mappedCtrl or mappedAlt or mappedSym
        return (metaState and RELOADABLE_META_MASK.inv()) or mappedMetaState
    }

    private data class BuildFingerprint(
        val brand: String,
        val manufacturer: String,
        val model: String,
        val device: String,
        val product: String
    ) {
        fun containsAny(vararg tokens: String): Boolean {
            return tokens.any { token ->
                brand.contains(token) ||
                    manufacturer.contains(token) ||
                    model.contains(token) ||
                    device.contains(token) ||
                    product.contains(token)
            }
        }
    }

    private fun resolveDeviceProfile(): DeviceProfile {
        val fp = buildFingerprint()
        if (isQ25(fp)) {
            return DeviceProfile(
                family = KeyboardFamily.BLACKBERRY,
                model = KeyboardModel.Q25,
                physicalLayoutName = "Q25",
                needsEventRemapping = true
            )
        }
        if (isKey2(fp)) {
            return DeviceProfile(
                family = KeyboardFamily.BLACKBERRY,
                model = KeyboardModel.KEY2,
                physicalLayoutName = "key2",
                needsEventRemapping = false
            )
        }
        if (isTitanFamily(fp)) {
            return DeviceProfile(
                family = KeyboardFamily.UNIHERTZ,
                model = resolveTitanModel(fp),
                physicalLayoutName = "titan2",
                needsEventRemapping = false
            )
        }
        return DeviceProfile(
            family = KeyboardFamily.UNKNOWN,
            model = KeyboardModel.UNKNOWN,
            physicalLayoutName = "unknown",
            needsEventRemapping = false
        )
    }

    private fun buildFingerprint(): BuildFingerprint {
        testBuildFingerprintOverride?.let { return it }
        return BuildFingerprint(
            brand = Build.BRAND.orEmpty().lowercase(),
            manufacturer = Build.MANUFACTURER.orEmpty().lowercase(),
            model = Build.MODEL.orEmpty().lowercase(),
            device = Build.DEVICE.orEmpty().lowercase(),
            product = Build.PRODUCT.orEmpty().lowercase()
        )
    }

    private fun currentDeviceProfile(): DeviceProfile = resolveDeviceProfile()

    internal fun setBuildFingerprintForTests(
        brand: String,
        manufacturer: String,
        model: String,
        device: String,
        product: String
    ) {
        testBuildFingerprintOverride = BuildFingerprint(
            brand = brand.lowercase(),
            manufacturer = manufacturer.lowercase(),
            model = model.lowercase(),
            device = device.lowercase(),
            product = product.lowercase()
        )
    }

    internal fun clearTestOverrides() {
        testBuildFingerprintOverride = null
        lastQ25MetaState = 0
    }

    private fun isQ25(fp: BuildFingerprint): Boolean {
        return fp.containsAny("q25") &&
            (fp.containsAny("zinwa", "blackberry", "q20") || fp.device == "q25" || fp.model == "q25")
    }

    private fun isKey2(fp: BuildFingerprint): Boolean {
        // LineageOS Key2 codenames:
        // - KEY2: athena
        // - KEY2 LE: luna
        if (fp.device == "athena" || fp.device == "luna") {
            return true
        }
        return fp.containsAny("blackberry key2", "key2", "bbf100")
    }

    private fun isTitanFamily(fp: BuildFingerprint): Boolean {
        return fp.containsAny("unihertz", "titan")
    }

    private fun resolveTitanModel(fp: BuildFingerprint): KeyboardModel {
        return when {
            fp.containsAny("titan pocket", "titan_pocket") -> KeyboardModel.TITAN_POCKET
            fp.containsAny("titan slim", "titan_slim") -> KeyboardModel.TITAN_SLIM
            fp.containsAny("titan 2", "titan2") -> KeyboardModel.TITAN_2
            fp.containsAny("titan") -> KeyboardModel.TITAN_ORIGINAL
            else -> KeyboardModel.UNKNOWN
        }
    }

    fun deviceName(): String {
        return Build.BRAND + " " + Build.MODEL
    }

    fun keyboardName(): String {
        return when (currentDeviceProfile().family) {
            KeyboardFamily.BLACKBERRY -> "Blackberry"
            KeyboardFamily.UNIHERTZ -> "Unihertz"
            KeyboardFamily.UNKNOWN -> "unknown"
        }
    }

    fun physicalKeyboardName(): String {
        return currentDeviceProfile().physicalLayoutName
    }

    fun isTitan2Device(): Boolean {
        return currentDeviceProfile().model == KeyboardModel.TITAN_2
    }
}
