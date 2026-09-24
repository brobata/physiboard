package brobata.physiboard.device.privileged.backlight

/**
 * The one system setting this module touches in-process: `Settings.Global`
 * `agui_keyboard_background_light`, the vendor's keyboard-light master switch.
 *
 * Everything else goes through the broker. This one is the exception the spec makes on
 * purpose: the ring must turn the keyboard off in the moment a notification lands, before the
 * screen comes on, and "a broker round trip of seconds [is] useless in the moment"
 * (device-backlight-ring.md SS5.8). The write needs `WRITE_SECURE_SETTINGS`, which the setup pass
 * grants through the broker (SS5.9); until then [write] fails and the callers record why.
 *
 * spec: device-backlight-ring.md SS2 point 3, SS2.2, SS5.8, D14.
 */
interface MasterSwitchAccess {
    /** The current value, or null when the system has no value (which the vendor reads as on). */
    fun read(): Int?

    /** Writes [value] (0 or 1); false when the permission is missing or the write threw. */
    fun write(value: Int): Boolean
}

/** A switch held in memory; the JVM tests' stand-in. [permissionHeld] false makes every write fail as the real one would. */
class FakeMasterSwitch(@Volatile var value: Int? = 1, @Volatile var permissionHeld: Boolean = true) : MasterSwitchAccess {
    val writes = mutableListOf<Int>()

    override fun read(): Int? = value

    override fun write(value: Int): Boolean {
        if (!permissionHeld) return false
        writes += value
        this.value = value
        return true
    }
}
