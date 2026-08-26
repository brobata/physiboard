package it.palsoftware.pastiera.ring

/**
 * How hard the ring pixels are driven. On the Titan's AMOLED the rest of the screen is black
 * and costs nothing, so this is the only knob that trades visibility for battery.
 */
enum class RingBrightness(val screenBrightness: Float) {
    DIM(0.05f),
    NORMAL(0.2f),
    BRIGHT(0.6f);

    companion object {
        val DEFAULT = NORMAL
        fun fromName(name: String?): RingBrightness =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
