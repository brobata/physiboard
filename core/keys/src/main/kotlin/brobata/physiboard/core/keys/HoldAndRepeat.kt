package brobata.physiboard.core.keys

/**
 * Multi-tap cycling for layouts that map one key to several taps.
 *
 * spec: keys-and-modifiers.md SS9. All timing here is timestamp-driven: [isWithinWindow] is a
 * pure comparison a caller makes against its own clock, whether that clock is a real timer or
 * just the time carried on the next [KeyStroke] to arrive.
 */
object MultiTap {

    /** spec: keys-and-modifiers.md SS9 ("start a 400 ms window"). */
    const val DEFAULT_WINDOW_MS: Long = 400

    /** spec: layers-sym-alt.md SS9.1 ("multi-tap is honoured only when... at least two taps survive"). */
    fun isMultiTapKey(key: KeyId, layout: LayoutMap): Boolean = layout[key]?.isMultiTap == true

    /**
     * spec: keys-and-modifiers.md SS9 exception: "with an uppercase resolution, a key whose later
     * taps include the capital sharp S (SS) does not multi-tap at all; it commits the plain
     * uppercase letter".
     */
    fun isSharpSException(key: KeyId, uppercase: Boolean, layout: LayoutMap): Boolean {
        val entry = layout[key] ?: return false
        return uppercase && entry.isMultiTap && entry.taps.any { it.uppercase == "ẞ" }
    }

    /** The state of one in-progress multi-tap cycle. */
    data class Cycle(
        val key: KeyId,
        val tapIndex: Int,
        val committedText: String,
        val uppercase: Boolean,
        val windowExpiresAtMs: Long,
    )

    /** spec: keys-and-modifiers.md SS9 ("first tap: commit tap 0... start a window"). */
    fun begin(key: KeyId, uppercase: Boolean, nowMs: Long, layout: LayoutMap, windowMs: Long = DEFAULT_WINDOW_MS): Pair<Cycle, String> {
        val entry = requireNotNull(layout[key]) { "not a multi-tap key: $key" }
        val text = if (uppercase) entry.taps[0].uppercase else entry.taps[0].lowercase
        return Cycle(key, 0, text, uppercase, nowMs + windowMs) to text
    }

    /** spec: keys-and-modifiers.md SS9 ("a different key, the window expiring... finalises the cycle"). */
    fun isWithinWindow(cycle: Cycle, nowMs: Long): Boolean = nowMs < cycle.windowExpiresAtMs

    /**
     * spec: keys-and-modifiers.md SS9 ("delete the previously committed text..., commit the next
     * tap, wrapping around; the case chosen on the first tap is kept for the whole cycle; the
     * window restarts. The replacement is done as one batch edit").
     */
    fun advance(cycle: Cycle, nowMs: Long, layout: LayoutMap, windowMs: Long = DEFAULT_WINDOW_MS): Pair<Cycle, Action> {
        val entry = requireNotNull(layout[cycle.key]) { "not a multi-tap key: ${cycle.key}" }
        val nextIndex = (cycle.tapIndex + 1) % entry.taps.size
        val tap = entry.taps[nextIndex]
        val text = if (cycle.uppercase) tap.uppercase else tap.lowercase
        val deleteCount = cycle.committedText.length.coerceAtLeast(1)
        val newCycle = cycle.copy(tapIndex = nextIndex, committedText = text, windowExpiresAtMs = nowMs + windowMs)
        return newCycle to Action.ReplaceRecent(deleteCount, text)
    }
}

/**
 * PhysiBoard's own long press: what a held key produces once it has been down longer than
 * [LongPressSettings.thresholdMs].
 *
 * spec: keys-and-modifiers.md SS8.2, SS8.3; layers-sym-alt.md SS7.
 */
object LongPress {

    /** One armed long press, waiting to see whether the key comes up first or the timer fires. */
    data class Pending(
        val key: KeyId,
        val armedAtMs: Long,
        val thresholdMs: Long,
        val mode: LongPressMode,
        val shiftEffective: Boolean,
        val committedText: String,
    )

    /** spec: keys-and-modifiers.md SS8.2 (the eligibility table, one row per [LongPressMode]). */
    fun isEligible(key: KeyId, shiftEffective: Boolean, layout: LayoutDescription): Boolean = when (layout.longPress.mode) {
        LongPressMode.ALT -> layout.deviceLayer[key] != null
        LongPressMode.SHIFT -> CharacterResolution.defaultCharacterText(key, uppercase = false)?.singleOrNull()?.isLetter() == true
        LongPressMode.VARIATIONS -> {
            val produced = CharacterResolution.layoutOrDefaultCharacter(key, shiftEffective, tapIndex = 0, layout.baseLayout)
            produced?.singleOrNull()?.let { layout.variations.listFor(it).isNotEmpty() } == true
        }
        LongPressMode.SYM -> symEntryFor(key, shiftEffective, layout, usesEmojiPage(layout.symPagesConfig)) != null
        LongPressMode.SYM_SYMBOLS -> layout.symbolsPage[key] != null
        LongPressMode.SYM_EMOJI -> layout.emojiPage[key] != null
    }

    /** Arms a long press for an eligible key at the moment its base character was committed. */
    fun arm(key: KeyId, shiftEffective: Boolean, committedText: String, nowMs: Long, layout: LayoutDescription): Pending =
        Pending(key, nowMs, layout.longPress.clampedThresholdMs, layout.longPress.mode, shiftEffective, committedText)

    /** spec: keys-and-modifiers.md SS8.3 ("a timer of `long_press_threshold` ms"), timestamp-driven. */
    fun hasFired(pending: Pending, nowMs: Long): Boolean = nowMs - pending.armedAtMs >= pending.thresholdMs

    /** spec: keys-and-modifiers.md SS8.3, layers-sym-alt.md SS7.4 (the replacement per mode). */
    fun replacement(pending: Pending, layout: LayoutDescription): Action {
        val text = when (pending.mode) {
            LongPressMode.ALT -> layout.deviceLayer[pending.key]
            LongPressMode.SHIFT ->
                CharacterResolution.baseCharacter(pending.key, uppercase = true, tapIndex = 0, layout.baseLayout)
                    ?: pending.committedText.uppercase()
            LongPressMode.VARIATIONS ->
                correctedVariationCase(pending.committedText, pending.shiftEffective)?.let { layout.variations.listFor(it).firstOrNull() }
            LongPressMode.SYM -> symEntryFor(pending.key, pending.shiftEffective, layout, usesEmojiPage(layout.symPagesConfig))
            LongPressMode.SYM_SYMBOLS -> symEntryFor(pending.key, pending.shiftEffective, layout, useEmoji = false)
            LongPressMode.SYM_EMOJI -> symEntryFor(pending.key, pending.shiftEffective, layout, useEmoji = true)
        }
        return if (text != null) Action.ReplaceRecent(deleteCount = 1, text = text) else Action.Ignored
    }

    /** spec: layers-sym-alt.md SS7.2 ("Emoji page entry if `emoji` precedes `symbols` in the configured order... order only, enabled state ignored"). */
    private fun usesEmojiPage(pages: SymPagesConfig): Boolean {
        val order = pages.normalizedOrder
        val emojiIndex = order.indexOf(SymPageId.EMOJI)
        val symbolsIndex = order.indexOf(SymPageId.SYMBOLS)
        return when {
            emojiIndex < 0 -> false
            symbolsIndex < 0 -> true
            else -> emojiIndex < symbolsIndex
        }
    }

    private fun symEntryFor(key: KeyId, shiftEffective: Boolean, layout: LayoutDescription, useEmoji: Boolean): String? =
        CharacterResolution.symPageEntryText(if (useEmoji) layout.emojiPage[key] else layout.symbolsPage[key], shiftEffective)

    /** spec: layers-sym-alt.md SS7.4 ("shifted and lowercase becomes uppercase; unshifted and uppercase becomes lowercase"). */
    private fun correctedVariationCase(committedText: String, shiftEffective: Boolean): Char? {
        val ch = committedText.singleOrNull() ?: return null
        return when {
            shiftEffective && ch.isLowerCase() -> ch.uppercaseChar()
            !shiftEffective && ch.isUpperCase() -> ch.lowercaseChar()
            else -> ch
        }
    }
}

/**
 * The per-key session state [LayerResolver] threads alongside [ModifierState]: at most one
 * multi-tap cycle and one armed long press at a time, matching the spec's own single-key focus
 * (keys-and-modifiers.md SS9, SS8.3).
 */
data class TypingSessionState(
    val multiTapCycle: MultiTap.Cycle? = null,
    val pendingLongPress: LongPress.Pending? = null,
)
