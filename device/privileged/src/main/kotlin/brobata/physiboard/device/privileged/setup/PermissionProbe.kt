package brobata.physiboard.device.privileged.setup

/**
 * The five grants the privileged features depend on, read live every time. Nothing here is
 * cached: each is re-checked after the shell line that should have granted it, because that
 * re-check IS the outcome (`pm grant` prints nothing on success, spec SS7 step 4).
 *
 * spec: broker-privileged-toolbox.md SS7 steps 2 to 4; device-backlight-ring.md SS5.9.
 */
interface PermissionProbe {
    /** "Display over other apps" (`SYSTEM_ALERT_WINDOW`). spec: SS7 step 2. */
    fun canDrawOverlays(): Boolean

    /** The ring listener is in the enabled notification listeners. spec: ring SS5.9 row 1. */
    fun isNotificationListenerGranted(): Boolean

    /** Full-screen intents allowed (always true below Android 14). spec: ring SS5.9 row 2. */
    fun canUseFullScreenIntent(): Boolean

    /** Notifications enabled for the app. spec: ring SS5.9 row 3. */
    fun areNotificationsEnabled(): Boolean

    /** `WRITE_SECURE_SETTINGS` held. spec: ring SS5.9 row 4, SS2.2. */
    fun hasWriteSecureSettings(): Boolean
}

/** A probe with every answer settable; the JVM tests' stand-in. */
class FakePermissionProbe(
    @Volatile var overlays: Boolean = false,
    @Volatile var listener: Boolean = false,
    @Volatile var fullScreenIntent: Boolean = false,
    @Volatile var notifications: Boolean = false,
    @Volatile var writeSecureSettings: Boolean = false,
) : PermissionProbe {
    override fun canDrawOverlays(): Boolean = overlays
    override fun isNotificationListenerGranted(): Boolean = listener
    override fun canUseFullScreenIntent(): Boolean = fullScreenIntent
    override fun areNotificationsEnabled(): Boolean = notifications
    override fun hasWriteSecureSettings(): Boolean = writeSecureSettings
}
