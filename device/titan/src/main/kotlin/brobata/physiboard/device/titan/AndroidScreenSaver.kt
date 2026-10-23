package brobata.physiboard.device.titan

/** What Android's own screen saver gets in the way of. spec: device-backlight-ring.md SS2.3. */
enum class ScreenSaverConflict {
    /** Smart backlight promises a lit keyboard whenever the screen is on; the screen saver puts it out and leaves it out. */
    KEYBOARD_LIGHT,

    /** The charging clock starts at screen-off; a screen saver takes the screen instead and no screen-off ever comes. */
    CHARGING_CLOCK,
}

/**
 * Android's screen saver ("Screen saver" under Display) as it bears on the keyboard light and
 * on the charging clock, and the one decision this module makes about it. The Secure rows are
 * read by the app layer (readable without any permission) and passed in; nothing here touches
 * the phone.
 *
 * The facts (D35 to D38): the vendor's framework sends `agui.action.CLOSE_KEYBOARD_LIGHT` from
 * `DreamService.onAttachedToWindow`, so every screen saver puts the keyboard light out the moment
 * it appears; the vendor controller then relights it only at a SCREEN_ON broadcast or a key
 * press. A screen saver ended by a touch leaves the screen on and the keyboard dark. Dreaming
 * also counts as interactive, so no SCREEN_OFF is ever broadcast and the charging clock
 * (SS5.11.2) never gets its cue.
 *
 * spec: device-backlight-ring.md SS2.3.
 */
object AndroidScreenSaver {
    /** `Settings.Secure`: the screen saver is on at all. */
    const val ENABLED_KEY = "screensaver_enabled"

    /** `Settings.Secure`: "While charging" in the UI; the name is the historical one. */
    const val ACTIVATE_ON_SLEEP_KEY = "screensaver_activate_on_sleep"

    /** The screen saver comes up when the screen times out on the charger. */
    fun runsWhileCharging(enabled: Boolean, activateOnSleep: Boolean): Boolean = enabled && activateOnSleep

    /**
     * Which of PhysiBoard's promises the screen saver breaks on this phone, given it runs while
     * charging. Empty when it does not run while charging, or when neither feature is on; a
     * feature that is off has nothing to be broken.
     */
    fun conflicts(runsWhileCharging: Boolean, smartBacklightEnabled: Boolean, chargingClockOn: Boolean): Set<ScreenSaverConflict> {
        if (!runsWhileCharging) return emptySet()
        val found = LinkedHashSet<ScreenSaverConflict>()
        if (smartBacklightEnabled) found += ScreenSaverConflict.KEYBOARD_LIGHT
        if (chargingClockOn) found += ScreenSaverConflict.CHARGING_CLOCK
        return found
    }
}
