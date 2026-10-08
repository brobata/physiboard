package brobata.physiboard.core.pointer.caret

/**
 * What a badge glyph reports. spec: trackpad-caret-nav.md SS4.2 ("the fixed order Shift, Alt,
 * Ctrl, Sym"), then [PRIVATE], which is not a modifier: it is private mode's indicator
 * (app-shell.md SS31.4), and [FILL], the Fill page's cue; both drawn after the modifiers so those
 * keep their places.
 */
enum class ModifierGlyph {
    SHIFT, ALT, CTRL, SYM, PRIVATE,

    /** layers-sym-alt.md SS4.7: the Fill page has a code or a password manager's suggestions for this field; drawn faint, last. */
    FILL,
}

/** How a glyph is drawn. spec: trackpad-caret-nav.md SS4.2, SS4.3 ("Colour: blue for one click, red for two... A held modifier is drawn faint"). */
enum class GlyphStyle { LOCKED_FULL, ARMED_FULL, ARMED_FAINT }

/** One glyph the badge should draw, in the left-to-right order [CaretBadge.items] returns them. */
data class BadgeItem(val modifier: ModifierGlyph, val style: GlyphStyle)

/**
 * The modifier facts the badge chooses its glyphs from, one field per row of spec SS4.2's table.
 *
 * This is deliberately its own small type rather than `:core:keys`' `ModifierState`: the badge's
 * decision only ever needs "is this row true", never the one-shot timers, hold bookkeeping or
 * Fn-burst counters `ModifierState` carries for the key pipeline's own reasons, and a caller
 * translating from a real `ModifierState` (or a test) can build one of these by name instead of
 * by field-order guesswork.
 */
data class ModifierGlyphInput(
    val capsLockOn: Boolean = false,
    val shiftOneShotArmed: Boolean = false,
    val shiftPhysicallyHeld: Boolean = false,
    val altLatched: Boolean = false,
    val altOneShotArmed: Boolean = false,
    val altPhysicallyHeld: Boolean = false,
    /** spec SS4.2: "Ctrl latched, and the latch is not nav mode." A latch from nav mode is deliberately excluded here, not passed as false by another means: nav mode's own exclusion (SS4.2's last paragraph) is a caller decision this input already bakes in. */
    val ctrlLatchedNotNavMode: Boolean = false,
    val ctrlOneShotArmed: Boolean = false,
    val ctrlPhysicallyHeld: Boolean = false,
    /** spec SS4.2: "Any Sym page open (page not 0)", "follows the Sym page, not a modifier flag". */
    val symPageOpen: Boolean = false,
    /** app-shell.md SS31.4: the user's private mode is on, so the badge shows its marker whatever the modifiers are. */
    val privateMode: Boolean = false,
    /** layers-sym-alt.md SS4.7: the Fill page has something for this field (and no Sym page is open). */
    val fillAvailable: Boolean = false,
)

/**
 * Decides which glyphs the caret badge shows, from modifier state alone.
 *
 * spec: trackpad-caret-nav.md SS4.2 ("What it shows"): "Items are listed left to right in the
 * fixed order Shift, Alt, Ctrl, Sym. At most one item per modifier, chosen by the first matching
 * row" of that section's table.
 */
object CaretBadge {

    fun items(input: ModifierGlyphInput): List<BadgeItem> = listOfNotNull(
        shiftItem(input),
        altItem(input),
        ctrlItem(input),
        symItem(input),
        privateItem(input),
        fillItem(input),
    )

    private fun shiftItem(input: ModifierGlyphInput): BadgeItem? = when {
        input.capsLockOn -> BadgeItem(ModifierGlyph.SHIFT, GlyphStyle.LOCKED_FULL)
        input.shiftOneShotArmed -> BadgeItem(ModifierGlyph.SHIFT, GlyphStyle.ARMED_FULL)
        input.shiftPhysicallyHeld -> BadgeItem(ModifierGlyph.SHIFT, GlyphStyle.ARMED_FAINT)
        else -> null
    }

    private fun altItem(input: ModifierGlyphInput): BadgeItem? = when {
        input.altLatched -> BadgeItem(ModifierGlyph.ALT, GlyphStyle.LOCKED_FULL)
        input.altOneShotArmed -> BadgeItem(ModifierGlyph.ALT, GlyphStyle.ARMED_FULL)
        input.altPhysicallyHeld -> BadgeItem(ModifierGlyph.ALT, GlyphStyle.ARMED_FAINT)
        else -> null
    }

    private fun ctrlItem(input: ModifierGlyphInput): BadgeItem? = when {
        input.ctrlLatchedNotNavMode -> BadgeItem(ModifierGlyph.CTRL, GlyphStyle.LOCKED_FULL)
        input.ctrlOneShotArmed -> BadgeItem(ModifierGlyph.CTRL, GlyphStyle.ARMED_FULL)
        input.ctrlPhysicallyHeld -> BadgeItem(ModifierGlyph.CTRL, GlyphStyle.ARMED_FAINT)
        else -> null
    }

    private fun symItem(input: ModifierGlyphInput): BadgeItem? =
        if (input.symPageOpen) BadgeItem(ModifierGlyph.SYM, GlyphStyle.ARMED_FULL) else null

    /** app-shell.md SS31.4: drawn in the locked colour, since it stays until switched off. */
    private fun privateItem(input: ModifierGlyphInput): BadgeItem? =
        if (input.privateMode) BadgeItem(ModifierGlyph.PRIVATE, GlyphStyle.LOCKED_FULL) else null

    /** layers-sym-alt.md SS4.7: a quiet cue, faint in the one-shot colour, never on its own a reason to look. */
    private fun fillItem(input: ModifierGlyphInput): BadgeItem? =
        if (input.fillAvailable) BadgeItem(ModifierGlyph.FILL, GlyphStyle.ARMED_FAINT) else null
}

/**
 * The badge's three stored rows. spec: trackpad-caret-nav.md SS4.8: `caret_modifier_badge`
 * ("whether the badge exists and whether cursor updates are requested for it") and the two
 * colours, one-shot/held items in [armedColorArgb], locked ones in [lockedColorArgb]. The
 * defaults are the catalogue's code defaults, not the Titan baseline; the store supplies that.
 */
data class CaretBadgeSettings(
    val enabled: Boolean = true,
    val armedColorArgb: Int = 0xFF2563EB.toInt(),
    val lockedColorArgb: Int = 0xFFDC2626.toInt(),
)

/** One colour, as a straight alpha/red/green/blue tuple, no platform colour-int packing assumed. */
data class BadgeColor(val alpha: Int, val red: Int, val green: Int, val blue: Int)

/**
 * Applies the badge's two alpha levels to a stored ARGB colour.
 *
 * spec: trackpad-caret-nav.md SS4.3 ("Full alpha 245", "Faint alpha 140"). [storedArgb] is read as
 * `0xAARRGGBB`; its own alpha byte is discarded; spec SS4.8's stored colours are opaque
 * (`0xFF2563EB`, `0xFFDC2626`) so this never had one worth keeping.
 */
object CaretBadgeColor {
    const val FULL_ALPHA = 245
    const val FAINT_ALPHA = 140

    fun full(storedArgb: Int): BadgeColor = withAlpha(storedArgb, FULL_ALPHA)
    fun faint(storedArgb: Int): BadgeColor = withAlpha(storedArgb, FAINT_ALPHA)

    private fun withAlpha(storedArgb: Int, alpha: Int): BadgeColor = BadgeColor(
        alpha = alpha,
        red = (storedArgb shr 16) and 0xFF,
        green = (storedArgb shr 8) and 0xFF,
        blue = storedArgb and 0xFF,
    )
}
