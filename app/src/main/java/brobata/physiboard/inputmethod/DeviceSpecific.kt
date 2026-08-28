package brobata.physiboard.inputmethod

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent

/**
 * Identifies the built-in keyboard. PhysiBoard targets the Unihertz Titan 2 family: the Titan 2
 * Elite is the device it is built and tested on, and the plain Titan 2 is recognised on a
 * best-effort basis - same scancodes, no hardware to test against.
 *
 * Neither model needs key-event remapping, so [remapHardwareKeyEvent] is a pass-through. The seam
 * is kept because the input path calls it in fifteen places; collapsing it is an altitude cleanup,
 * not a device cull.
 */
object DeviceSpecific {
    enum class InputDeviceKind {
        BUILT_IN,
        ACCESSORY,
        UNKNOWN
    }

    data class KeyboardInputIdentity(
        val name: String,
        val descriptor: String,
        val vendorId: Int,
        val productId: Int,
        val sources: Int,
        val keyboardType: Int,
        val isExternal: Boolean,
        val isVirtual: Boolean
    )

    data class ResolvedInputProfile(
        val profileId: String,
        val kind: InputDeviceKind,
        val autoDetected: Boolean
    )

    data class RemappedHardwareEvent(
        val keyCode: Int,
        val event: KeyEvent?
    )

    private enum class KeyboardModel {
        TITAN_2_ELITE_QWERTY,
        TITAN_2,
        UNKNOWN
    }

    private data class DeviceProfile(
        val model: KeyboardModel,
        val physicalLayoutName: String
    )

    private var testBuildFingerprintOverride: BuildFingerprint? = null

    // Unihertz scan codes, shared by the Titan 2 and the Titan 2 Elite.
    private const val SCANCODE_TITAN2_CTRL: Int = 251
    private const val SCANCODE_TITAN2_SYM: Int = 253

    /** The Titan 2 family sends usable key events as-is. */
    fun needsRemapping(): Boolean = false

    fun remapHardwareKeyEvent(
        keyCode: Int,
        event: KeyEvent?,
        physicalProfileOverride: String? = null
    ): RemappedHardwareEvent = RemappedHardwareEvent(keyCode, event)

    private data class BuildFingerprint(
        val brand: String,
        val manufacturer: String,
        val model: String,
        val device: String,
        val product: String,
        val board: String,
        val display: String
    ) {
        fun containsAny(vararg tokens: String): Boolean {
            return tokens.any { token ->
                brand.contains(token) ||
                    manufacturer.contains(token) ||
                    model.contains(token) ||
                    device.contains(token) ||
                    product.contains(token) ||
                    board.contains(token) ||
                    display.contains(token)
            }
        }
    }

    private fun resolveDeviceProfile(): DeviceProfile {
        val fp = buildFingerprint()
        if (isTitan2EliteQwerty(fp)) {
            return DeviceProfile(KeyboardModel.TITAN_2_ELITE_QWERTY, "titan2elite_qwerty")
        }
        if (isTitanFamily(fp) && fp.containsAny("titan 2", "titan2")) {
            return DeviceProfile(KeyboardModel.TITAN_2, "titan2")
        }
        return DeviceProfile(KeyboardModel.UNKNOWN, "unknown")
    }

    private fun buildFingerprint(): BuildFingerprint {
        testBuildFingerprintOverride?.let { return it }
        return BuildFingerprint(
            brand = Build.BRAND.orEmpty().lowercase(),
            manufacturer = Build.MANUFACTURER.orEmpty().lowercase(),
            model = Build.MODEL.orEmpty().lowercase(),
            device = Build.DEVICE.orEmpty().lowercase(),
            product = Build.PRODUCT.orEmpty().lowercase(),
            board = Build.BOARD.orEmpty().lowercase(),
            display = Build.DISPLAY.orEmpty().lowercase()
        )
    }

    private fun currentDeviceProfile(): DeviceProfile = resolveDeviceProfile()

    fun resolveInputProfile(
        event: KeyEvent?,
        physicalProfileOverride: String? = null
    ): ResolvedInputProfile {
        val identity = event
            ?.takeIf { it.deviceId >= 0 }
            ?.let { InputDevice.getDevice(it.deviceId) }
            ?.let(::keyboardInputIdentity)
        return resolveInputProfile(identity, physicalProfileOverride)
    }

    fun resolveInputProfile(
        device: InputDevice,
        physicalProfileOverride: String? = null
    ): ResolvedInputProfile {
        return resolveInputProfile(keyboardInputIdentity(device), physicalProfileOverride)
    }

    internal fun resolveInputProfile(
        identity: KeyboardInputIdentity?,
        physicalProfileOverride: String? = null
    ): ResolvedInputProfile {
        val kind = when {
            identity == null -> InputDeviceKind.UNKNOWN
            identity.isExternal -> InputDeviceKind.ACCESSORY
            else -> InputDeviceKind.BUILT_IN
        }

        normalizePhysicalProfileOverride(physicalProfileOverride)?.let { manualProfile ->
            return ResolvedInputProfile(
                profileId = manualProfile,
                kind = kind,
                autoDetected = false
            )
        }

        val profile = currentDeviceProfile()
        return ResolvedInputProfile(
            profileId = profile.physicalLayoutName,
            kind = kind,
            autoDetected = profile.model != KeyboardModel.UNKNOWN
        )
    }

    fun detectedInputProfiles(): List<ResolvedInputProfile> {
        val builtIn = currentDeviceProfile()
        if (builtIn.model == KeyboardModel.UNKNOWN) {
            return emptyList()
        }
        return listOf(
            ResolvedInputProfile(
                profileId = builtIn.physicalLayoutName,
                kind = InputDeviceKind.BUILT_IN,
                autoDetected = true
            )
        )
    }

    fun hasConnectedHardwareKeyboard(): Boolean {
        if (hasBuiltInHardwareKeyboard()) {
            return true
        }
        return InputDevice.getDeviceIds().any { deviceId ->
            val device = InputDevice.getDevice(deviceId) ?: return@any false
            val identity = keyboardInputIdentity(device)
            !identity.isVirtual &&
                isKeyboardLike(identity) &&
                identity.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        }
    }

    fun hasBuiltInHardwareKeyboard(): Boolean =
        currentDeviceProfile().model != KeyboardModel.UNKNOWN

    private fun keyboardInputIdentity(device: InputDevice): KeyboardInputIdentity {
        return KeyboardInputIdentity(
            name = device.name.orEmpty(),
            descriptor = device.descriptor.orEmpty(),
            vendorId = device.vendorId,
            productId = device.productId,
            sources = device.sources,
            keyboardType = device.keyboardType,
            isExternal = device.isExternal,
            isVirtual = device.isVirtual
        )
    }

    private fun isKeyboardLike(identity: KeyboardInputIdentity): Boolean {
        return (identity.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD ||
            identity.keyboardType != InputDevice.KEYBOARD_TYPE_NONE
    }

    private fun normalizePhysicalProfileOverride(physicalProfileOverride: String?): String? {
        return when (physicalProfileOverride?.trim()?.lowercase().orEmpty()) {
            "titan2elite_qwerty" -> "titan2elite_qwerty"
            "titan2" -> "titan2"
            else -> null
        }
    }

    internal fun setBuildFingerprintForTests(
        brand: String,
        manufacturer: String,
        model: String,
        device: String,
        product: String,
        board: String = "",
        display: String = ""
    ) {
        testBuildFingerprintOverride = BuildFingerprint(
            brand = brand.lowercase(),
            manufacturer = manufacturer.lowercase(),
            model = model.lowercase(),
            device = device.lowercase(),
            product = product.lowercase(),
            board = board.lowercase(),
            display = display.lowercase()
        )
    }

    internal fun clearTestOverrides() {
        testBuildFingerprintOverride = null
    }

    private fun isTitanFamily(fp: BuildFingerprint): Boolean {
        return fp.containsAny("unihertz", "titan")
    }

    private fun isTitan2EliteQwerty(fp: BuildFingerprint): Boolean {
        val strictTokenMatch = fp.containsAny(
            "titan2elite_qwerty",
            "titan2elite-qwerty",
            "titan2eliteqwerty"
        )
        if (strictTokenMatch) {
            return true
        }

        // Reviewer devices may expose Titan 2-like model/product, but still leak Elite traits
        // via BOARD or DISPLAY. Use these only inside the Unihertz Titan family.
        return isTitanFamily(fp) && (fp.display.contains("elite") || fp.board.contains("g72"))
    }

    fun deviceName(): String {
        return Build.BRAND + " " + Build.MODEL
    }

    fun keyboardName(): String {
        return if (currentDeviceProfile().model == KeyboardModel.UNKNOWN) "unknown" else "Unihertz"
    }

    fun physicalKeyboardName(): String {
        return currentDeviceProfile().physicalLayoutName
    }

    fun isTitan2Device(): Boolean {
        return currentDeviceProfile().model != KeyboardModel.UNKNOWN
    }

    fun isTitan2EliteDevice(): Boolean =
        currentDeviceProfile().model == KeyboardModel.TITAN_2_ELITE_QWERTY

    /**
     * True on a Titan 2 that is not an Elite: everything should work, but nothing here has been
     * tested on that hardware. Drives the one-time unsupported-device notice.
     */
    fun isUntestedTitanDevice(): Boolean =
        currentDeviceProfile().model == KeyboardModel.TITAN_2

    fun isPhysicalKeyboardDevice(physicalProfileOverride: String? = null): Boolean {
        if (normalizePhysicalProfileOverride(physicalProfileOverride) != null) {
            return true
        }
        return currentDeviceProfile().model != KeyboardModel.UNKNOWN
    }
}
