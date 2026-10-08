package brobata.physiboard.core.pointer.caret

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: trackpad-caret-nav.md SS10, test cases T40-T51 (the badge subset owned by this package). */
class CaretBadgeTest {

    @Test
    fun `T40 - caps lock, Alt one-shot, Ctrl held and an open Sym page all show in fixed order`() {
        val input = ModifierGlyphInput(
            capsLockOn = true,
            altOneShotArmed = true,
            ctrlPhysicallyHeld = true,
            symPageOpen = true,
        )
        assertEquals(
            listOf(
                BadgeItem(ModifierGlyph.SHIFT, GlyphStyle.LOCKED_FULL),
                BadgeItem(ModifierGlyph.ALT, GlyphStyle.ARMED_FULL),
                BadgeItem(ModifierGlyph.CTRL, GlyphStyle.ARMED_FAINT),
                BadgeItem(ModifierGlyph.SYM, GlyphStyle.ARMED_FULL),
            ),
            CaretBadge.items(input),
        )
    }

    @Test
    fun `T41 - a Ctrl latch from nav mode alone shows nothing`() {
        // spec SS4.2's last paragraph: a latch from nav mode is excluded from the "Ctrl latched"
        // row entirely, so a caller building this input for that state simply never sets
        // ctrlLatchedNotNavMode true (see the field's own KDoc); there is nothing else on for the
        // badge to show.
        assertEquals(emptyList(), CaretBadge.items(ModifierGlyphInput(ctrlLatchedNotNavMode = false)))
    }

    @Test
    fun `T42 - Shift held and Shift one-shot together show one armed full arrow`() {
        val input = ModifierGlyphInput(shiftOneShotArmed = true, shiftPhysicallyHeld = true)
        assertEquals(listOf(BadgeItem(ModifierGlyph.SHIFT, GlyphStyle.ARMED_FULL)), CaretBadge.items(input))
    }

    @Test
    fun `T43 - nothing on and Sym page 0 shows nothing`() {
        assertEquals(emptyList(), CaretBadge.items(ModifierGlyphInput()))
    }

    @Test
    fun `T44 - a caret near the right edge flips the badge to the left`() {
        val caret = CaretGeometry(leftPx = 1050f, topPx = 100f, bottomPx = 140f)
        val badge = BadgeSize(widthPx = 60f, heightPx = 20f, glyphFeetOffsetPx = 20f)
        val screen = ScreenGeometry(widthPx = 1080f)
        val position = CaretBadgePlacement.place(caret, badge, screen, pxPerDp = 1.75f)
        assertEquals(BadgePosition(983, 87), position)
    }

    @Test
    fun `T45 - a caret at the top of the screen drops the badge below the line`() {
        val caret = CaretGeometry(leftPx = 0f, topPx = 5f, bottomPx = 45f)
        val badge = BadgeSize(widthPx = 60f, heightPx = 20f, glyphFeetOffsetPx = 20f)
        val screen = ScreenGeometry(widthPx = 1080f)
        val position = CaretBadgePlacement.place(caret, badge, screen, pxPerDp = 1.75f)
        assertEquals(38, position.yPx)
    }

    @Test
    fun `T46 - a NaN horizontal makes the caret report unusable`() {
        val report = CursorAnchorReport(horizontalPx = Float.NaN, topPx = 0f, bottomPx = 10f)
        assertEquals(false, CaretUsability.isUsable(report))
    }

    @Test
    fun `T47 - an invisible region with no visible region is unusable`() {
        val report = CursorAnchorReport(horizontalPx = 0f, topPx = 0f, bottomPx = 10f, hasInvisibleRegion = true, hasVisibleRegion = false)
        assertEquals(false, CaretUsability.isUsable(report))
    }

    @Test
    fun `T48 - both region flags together are usable`() {
        val report = CursorAnchorReport(horizontalPx = 0f, topPx = 0f, bottomPx = 10f, hasInvisibleRegion = true, hasVisibleRegion = true)
        assertEquals(true, CaretUsability.isUsable(report))
    }

    @Test
    fun `T49 - the editor accepting on the third scheduled attempt stops the later retries`() {
        var state = CursorUpdateRequestState()
        val issued = mutableListOf<Boolean>()
        repeat(5) { attempt ->
            val (next, shouldIssue) = CursorUpdateRetrySchedule.onScheduledAttempt(state)
            state = next
            issued += shouldIssue
            if (attempt == 2) state = CursorUpdateRetrySchedule.onRequestAccepted(state)
        }
        assertEquals(listOf(true, true, true, false, false), issued)
        assertEquals(3, state.attemptsMade)
    }

    @Test
    fun `T50 - an editor that never accepts stops retrying after 8 attempts total`() {
        var state = CursorUpdateRequestState()
        repeat(5) {
            val (next, _) = CursorUpdateRetrySchedule.onScheduledAttempt(state)
            state = next
        }
        assertEquals(5, state.attemptsMade)
        val refreshResults = mutableListOf<Boolean>()
        repeat(5) {
            val (next, shouldIssue) = CursorUpdateRetrySchedule.onRefresh(state)
            state = next
            refreshResults += shouldIssue
        }
        assertEquals(listOf(true, true, true, false, false), refreshResults)
        assertEquals(8, state.attemptsMade)
    }

    @Test
    fun `T51 - the locked colour at faint alpha keeps its RGB and drops to alpha 140`() {
        assertEquals(BadgeColor(140, 0xDC, 0x26, 0x26), CaretBadgeColor.faint(0xFFDC2626.toInt()))
    }

    @Test
    fun `private mode shows its marker on its own and after the modifiers`() {
        assertEquals(listOf(BadgeItem(ModifierGlyph.PRIVATE, GlyphStyle.LOCKED_FULL)), CaretBadge.items(ModifierGlyphInput(privateMode = true)))
        assertEquals(
            listOf(ModifierGlyph.SHIFT, ModifierGlyph.PRIVATE),
            CaretBadge.items(ModifierGlyphInput(capsLockOn = true, privateMode = true)).map { it.modifier },
        )
    }

    @Test
    fun `layers-sym-alt SS4_7 - the Fill cue is faint and comes after everything else`() {
        assertEquals(listOf(BadgeItem(ModifierGlyph.FILL, GlyphStyle.ARMED_FAINT)), CaretBadge.items(ModifierGlyphInput(fillAvailable = true)))
        assertEquals(
            listOf(ModifierGlyph.SHIFT, ModifierGlyph.PRIVATE, ModifierGlyph.FILL),
            CaretBadge.items(ModifierGlyphInput(capsLockOn = true, privateMode = true, fillAvailable = true)).map { it.modifier },
        )
    }
}
