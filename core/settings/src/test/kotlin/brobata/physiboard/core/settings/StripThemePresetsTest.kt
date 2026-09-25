package brobata.physiboard.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: status-bar.md SS9.3 ("Twenty-four presets are offered... The presets' locked LED colors are all distinct from each other (a test asserts it)"). */
class StripThemePresetsTest {

    @Test
    fun `there are twenty-four presets with unique names`() {
        assertEquals(24, StripThemePresets.ALL.size)
        assertEquals(24, StripThemePresets.ALL.map { it.name }.toSet().size)
    }

    @Test
    fun `locked LED colors are distinct except the two documented table duplicates, per this module's SPEC GAP`() {
        val lockedColors = StripThemePresets.ALL.map { it.theme.ledLocked }
        // SS9.3's table (transcribed verbatim, see the class KDoc's SPEC GAP) gives Cloud Tap and
        // Classic Cloud, and separately Moon Tap and Classic Midnight, the same locked color.
        assertEquals(lockedColors.size - 2, lockedColors.toSet().size)
    }

    @Test
    fun `Slate Dark matches the default theme's own hardware baseline`() {
        val slateDark = StripThemePresets.ALL.first { it.name == "Slate Dark" }.theme
        assertEquals(StripTheme.SLATE_DARK.background, slateDark.background)
        assertEquals(StripTheme.SLATE_DARK.ledLocked, slateDark.ledLocked)
        assertEquals(0.10, slateDark.keyCornerRadiusRatio)
    }

    @Test
    fun `Cloud Tap overrides suggestion and status bar button away from normal and special key`() {
        val cloudTap = StripThemePresets.ALL.first { it.name == "Cloud Tap" }.theme
        assertTrue(cloudTap.suggestion != cloudTap.textAndIcons) // sanity: fields are actually populated
        assertEquals(0xFFDDE0E5.toInt(), cloudTap.suggestion)
        assertEquals(0xFFFFFFFF.toInt(), cloudTap.statusBarButton)
    }
}
