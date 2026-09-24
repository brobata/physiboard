package brobata.physiboard.core.strip

/** The two button groups of the strip. spec: status-bar.md SS6.3, "left buttons, then the suggestion slots, then right buttons". */
enum class StripSide { LEFT, RIGHT }

/**
 * Which buttons sit at each edge, in order. spec: status-bar.md SS6.3, simplified per SS19 to
 * "one editable list per side that the page shows in full": the hidden second right slot and the
 * three mirror keys are gone, so every entry here is both stored and drawn. A [StripButton.NONE]
 * entry is kept in the list (SS6.3, "kept in the list but draw nothing") so slot positions stay
 * stable when the page clears one; [drawn] is what the strip actually lays out.
 */
data class ButtonSlots(val left: List<StripButton>, val right: List<StripButton>) {

    /** spec SS6.3: `none` entries draw nothing; SS6.2: "a side with no buttons is hidden". */
    fun drawn(side: StripSide): List<StripButton> = list(side).filter { it != StripButton.NONE }

    fun list(side: StripSide): List<StripButton> = when (side) {
        StripSide.LEFT -> left
        StripSide.RIGHT -> right
    }

    /**
     * The settings page picking [button] for the slot at [index] on [side]. spec SS6.3: "Picking a
     * button in a visible dropdown clears that same button from every other slot", so choosing
     * the microphone for R1 while R2 is the microphone leaves R2 as `none` (T28). Choosing `none`
     * clears nothing else. An index past the end grows the list with `none` entries.
     */
    fun choose(side: StripSide, index: Int, button: StripButton): ButtonSlots {
        require(index >= 0) { "slot index must not be negative: $index" }
        fun List<StripButton>.cleared(): List<StripButton> =
            if (button == StripButton.NONE) this else map { if (it == button) StripButton.NONE else it }
        val target = list(side).cleared().toMutableList()
        while (target.size <= index) target += StripButton.NONE
        target[index] = button
        val other = list(side.opposite()).cleared()
        return when (side) {
            StripSide.LEFT -> ButtonSlots(left = target, right = other)
            StripSide.RIGHT -> ButtonSlots(left = other, right = target)
        }
    }

    private fun StripSide.opposite(): StripSide = if (this == StripSide.LEFT) StripSide.RIGHT else StripSide.LEFT

    companion object {
        /** spec SS6.3, "Absent keys (preference default)" and the settings page's "Reset" (T29). */
        val DEFAULT: ButtonSlots = ButtonSlots(left = listOf(StripButton.HAMBURGER), right = listOf(StripButton.EMOJI, StripButton.MICROPHONE))

        /** spec SS6.3, "First-run baseline written once on a fresh install": clipboard left, microphone right, and a second right entry that is `none`. */
        val FIRST_RUN_BASELINE: ButtonSlots = ButtonSlots(left = listOf(StripButton.CLIPBOARD), right = listOf(StripButton.MICROPHONE, StripButton.NONE))

        /**
         * Reads stored id lists. spec SS6.3: an unknown id becomes `none` in place (T26); an absent
         * list (null) falls back to that side's [DEFAULT]. Decoding the JSON array itself is the
         * settings layer's job; this takes the strings it decoded.
         */
        fun fromIds(left: List<String>?, right: List<String>?): ButtonSlots = ButtonSlots(
            left = left?.map(StripButton::fromId) ?: DEFAULT.left,
            right = right?.map(StripButton::fromId) ?: DEFAULT.right,
        )

        /** spec SS6.3, T29: the "Reset" button "restores the slot defaults only". */
        fun reset(): ButtonSlots = DEFAULT
    }

    /** What a settings layer writes back. spec SS6.3: normalised "on read and on write", so an unknown id can never reach storage. */
    fun ids(side: StripSide): List<String> = list(side).map { it.id }
}
