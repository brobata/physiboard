package brobata.physiboard.device.privileged

import brobata.physiboard.core.settings.DeviceCaptures
import brobata.physiboard.core.settings.DevicePrefs
import brobata.physiboard.core.settings.Settings

/**
 * How this module reads and writes the one typed settings value the app persists.
 *
 * `:app` owns the store and depends on this module, not the other way round, so the module
 * only ever sees it through this seam. Both calls are synchronous and the write is committed
 * before it returns: the ring's keyboard-dark record must be on disk BEFORE the switch is
 * written (device-backlight-ring.md SS5.8 step 2, "a process death between the two must leave a
 * restore still to do"), and the setup pass runs on its own worker where blocking is fine.
 *
 * spec: broker-privileged-toolbox.md SS20 (which rows this module owns), device-backlight-ring.md
 * SS3.3, SS5.8; settings-catalog.md SS2.10 for the `DeviceCaptures` rows.
 */
interface DeviceStateStore {
    /** The current value; never stale by more than the store's own write latency. */
    fun snapshot(): Settings

    /** Applies [transform] atomically and returns the value as committed. */
    fun update(transform: (Settings) -> Settings): Settings
}

/** The device rows only; a convenience over [DeviceStateStore.update]. */
fun DeviceStateStore.updateDevice(transform: (DevicePrefs) -> DevicePrefs): Settings =
    update { it.copy(device = transform(it.device)) }

/** The captured originals only; a convenience over [DeviceStateStore.update]. */
fun DeviceStateStore.updateCaptures(transform: (DeviceCaptures) -> DeviceCaptures): Settings =
    update { it.copy(captures = transform(it.captures)) }

/**
 * A store that lives in memory. The JVM tests drive every flow through it, and a host without
 * `:app`'s wiring (an instrumentation harness) can use it so nothing here dereferences null.
 */
class InMemoryDeviceStateStore(initial: Settings = Settings()) : DeviceStateStore {
    @Volatile
    private var value: Settings = initial

    override fun snapshot(): Settings = value

    @Synchronized
    override fun update(transform: (Settings) -> Settings): Settings {
        value = transform(value)
        return value
    }
}
