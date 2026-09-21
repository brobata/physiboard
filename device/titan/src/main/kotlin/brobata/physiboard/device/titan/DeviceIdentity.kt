package brobata.physiboard.device.titan

/**
 * Whether a build fingerprint identifies a Titan 2 Elite QWERTY.
 *
 * spec: keys-and-modifiers.md SS1.2 (the Elite row of the device identification table), D14. Only
 * this one check is kept for 3.0: SS22 Keep/Drop ("keep Elite detection only as a sanity check;
 * 3.0 is Titan 2 Elite only and has no on-screen keyboard"). The Titan 2 (non-Elite) and
 * "unknown" profiles, the profile-id Alt-layer asset selection and the
 * `physical_keyboard_profile_override` preference are dropped: there is exactly one supported
 * device, so there is nothing to select between and no on-screen keyboard to align.
 */
object DeviceIdentity {

    private const val ELITE_MARKER_A = "titan2elite_qwerty"
    private const val ELITE_MARKER_B = "titan2elite-qwerty"
    private const val ELITE_MARKER_C = "titan2eliteqwerty"
    private const val FAMILY_MARKER_A = "unihertz"
    private const val FAMILY_MARKER_B = "titan"
    private const val ELITE_DISPLAY_MARKER = "elite"
    private const val ELITE_BOARD_MARKER = "g72"

    fun isTitan2EliteQwerty(
        brand: String,
        manufacturer: String,
        model: String,
        device: String,
        product: String,
        board: String,
        display: String,
    ): Boolean {
        val fields = listOf(brand, manufacturer, model, device, product, board, display).map { it.lowercase() }
        val hasEliteMarker = fields.any { it.contains(ELITE_MARKER_A) || it.contains(ELITE_MARKER_B) || it.contains(ELITE_MARKER_C) }
        if (hasEliteMarker) return true

        val isTitanFamily = fields.any { it.contains(FAMILY_MARKER_A) || it.contains(FAMILY_MARKER_B) }
        val displayLeaksElite = display.lowercase().contains(ELITE_DISPLAY_MARKER)
        val boardLeaksG72 = board.lowercase().contains(ELITE_BOARD_MARKER)
        return isTitanFamily && (displayLeaksElite || boardLeaksG72)
    }
}
