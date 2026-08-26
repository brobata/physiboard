package it.palsoftware.pastiera.ring

/**
 * The colours a ring can be given per app. A short list on purpose: the ring is a few pixels
 * wide on a black screen, and colours that are hard to tell apart at that size are not choices.
 */
enum class RingPalette(val argb: Int) {
    GREEN(0xFF34C759.toInt()),
    BLUE(0xFF2F80ED.toInt()),
    CYAN(0xFF22D3EE.toInt()),
    PURPLE(0xFFA855F7.toInt()),
    PINK(0xFFF472B6.toInt()),
    RED(0xFFEF4444.toInt()),
    ORANGE(0xFFF97316.toInt()),
    YELLOW(0xFFFACC15.toInt()),
    WHITE(0xFFF5F5F5.toInt());

    companion object {
        fun closest(argb: Int): RingPalette = entries.firstOrNull { it.argb == argb } ?: GREEN
    }
}
