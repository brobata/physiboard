package brobata.physiboard.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** spec: settings-catalog.md SS4 (the factory baseline) and SS2.15 (`settings_baseline_version`). */
class SettingsBaselineTest {

    @Test
    fun `storedVersion reads 0 when the marker is absent`() {
        assertEquals(0, SettingsBaseline.storedVersion(emptyMap()))
    }

    @Test
    fun `storedVersion reads the marker's int value`() {
        assertEquals(3, SettingsBaseline.storedVersion(mapOf(SettingsKeys.BASELINE_VERSION to "3")))
    }

    @Test
    fun `apply is a no-op when storedVersion already matches toVersion`() {
        val flatMap = mapOf("some_key" to "some_value", SettingsKeys.BASELINE_VERSION to "2")
        val result = SettingsBaseline.apply(flatMap, storedVersion = 2, corrections = mapOf(2 to mapOf("some_key" to "corrected")), toVersion = 2)
        assertSame(flatMap, result)
    }

    @Test
    fun `apply is a no-op when storedVersion is already past toVersion`() {
        val flatMap = mapOf("some_key" to "some_value")
        val result = SettingsBaseline.apply(flatMap, storedVersion = 5, corrections = mapOf(2 to mapOf("some_key" to "corrected")), toVersion = 2)
        assertSame(flatMap, result)
    }

    @Test
    fun `a pending version's correction overwrites a key even when the map already disagrees`() {
        val flatMap = mapOf("auto_replace_on_space_enter" to "false")
        val corrections = mapOf(1 to mapOf("auto_replace_on_space_enter" to "true"))

        val result = SettingsBaseline.apply(flatMap, storedVersion = 0, corrections = corrections, toVersion = 1)

        assertEquals("true", result["auto_replace_on_space_enter"])
    }

    @Test
    fun `the version marker ends up at toVersion after a pending correction runs`() {
        val result = SettingsBaseline.apply(emptyMap(), storedVersion = 0, corrections = mapOf(1 to mapOf("k" to "v")), toVersion = 1)
        assertEquals("1", result[SettingsKeys.BASELINE_VERSION])
    }

    @Test
    fun `a key not mentioned by any pending version's corrections is left untouched`() {
        val flatMap = mapOf("untouched_key" to "original", "corrected_key" to "wrong")
        val corrections = mapOf(1 to mapOf("corrected_key" to "right"))

        val result = SettingsBaseline.apply(flatMap, storedVersion = 0, corrections = corrections, toVersion = 1)

        assertEquals("original", result["untouched_key"])
        assertEquals("right", result["corrected_key"])
    }

    @Test
    fun `multiple pending versions apply in ascending order, so a later version wins the same key`() {
        val corrections = mapOf(
            1 to mapOf("k" to "from-v1"),
            2 to mapOf("k" to "from-v2"),
        )

        val result = SettingsBaseline.apply(mapOf("k" to "stale"), storedVersion = 0, corrections = corrections, toVersion = 2)

        assertEquals("from-v2", result["k"])
        assertEquals("2", result[SettingsKeys.BASELINE_VERSION])
    }

    @Test
    fun `a version already passed is not re-applied, only versions strictly above storedVersion run`() {
        val corrections = mapOf(
            1 to mapOf("k" to "from-v1"),
            2 to mapOf("k" to "from-v2"),
        )

        // storedVersion 1 means version 1's correction already ran; only version 2's should apply now.
        val result = SettingsBaseline.apply(mapOf("k" to "already-v1"), storedVersion = 1, corrections = corrections, toVersion = 2)

        assertEquals("from-v2", result["k"])
    }

    @Test
    /** Version 5 hides the suggestion row again (2026-10-05), after version 3 had brought it back. */
    fun `the production baseline hides the suggestion row and stamps its marker`() {
        val fresh = SettingsBaseline.apply(emptyMap(), storedVersion = 0)
        assertEquals(SettingsBaseline.CURRENT_VERSION.toString(), fresh[SettingsKeys.BASELINE_VERSION])
        assertEquals(StatusBarVisibility.NEVER.storedValue, fresh[SettingsKeys.STATUS_BAR_VISIBILITY])

        val hadItOn = mapOf(SettingsKeys.STATUS_BAR_VISIBILITY to StatusBarVisibility.ALWAYS.storedValue)
        val corrected = SettingsBaseline.apply(hadItOn, storedVersion = 4)
        assertEquals(StatusBarVisibility.NEVER.storedValue, corrected[SettingsKeys.STATUS_BAR_VISIBILITY])

        val alreadyApplied = SettingsBaseline.apply(hadItOn, storedVersion = SettingsBaseline.CURRENT_VERSION)
        assertEquals(
            StatusBarVisibility.ALWAYS.storedValue,
            alreadyApplied[SettingsKeys.STATUS_BAR_VISIBILITY],
            "a correction runs once per version: switching the row back on afterwards must stand",
        )
    }

    /**
     * Version 4: the chords that intercept Alt, Shift, Enter and Space go off whatever a 2.x
     * store carried, because shipping eighteen layouts made them live for the first time and
     * Alt with Shift began switching the keyboard out from under the maintainer (2026-09-29).
     */
    @Test
    fun `the layout-switch chords are switched off on an install that imported them on`() {
        val imported = mapOf(
            SettingsKeys.ALT_SHIFT_LAYOUT_SWITCH to "true",
            SettingsKeys.CTRL_SPACE_LAYOUT_SWITCH to "true",
        )
        val corrected = SettingsBaseline.apply(imported, storedVersion = 3)
        assertEquals("false", corrected[SettingsKeys.ALT_SHIFT_LAYOUT_SWITCH])
        assertEquals("false", corrected[SettingsKeys.ALT_ENTER_LAYOUT_SWITCH])
        assertEquals("false", corrected[SettingsKeys.CTRL_SPACE_LAYOUT_SWITCH])

        val chosenSince = SettingsBaseline.apply(imported, storedVersion = SettingsBaseline.CURRENT_VERSION)
        assertEquals("true", chosenSince[SettingsKeys.ALT_SHIFT_LAYOUT_SWITCH], "a later choice must stand")
    }

    /**
     * Version 8: the maintainer's dev build stored GIFs off and the picker first, so GIFs were
     * reachable only through a double tap. The correction puts Emoji, Symbols, GIFs on in that
     * order, the rest off, and kaomoji back to opt-in; a fresh install reads the same.
     */
    @Test
    fun `version 8 moves this install to Emoji, Symbols, GIFs and nothing else`() {
        val phone = mapOf(
            SettingsKeys.SYM_PAGES_CONFIG to """{"emojiEnabled":false,"symbolsEnabled":true,"clipboardEnabled":false,"emojiPickerEnabled":true,"gifEnabled":false,"custom1Enabled":false,"custom2Enabled":false,"custom3Enabled":false,"symPageOrder":["emoji_picker","symbols","clipboard","emoji","gif","custom1","custom2","custom3"]}""",
            SettingsKeys.BASELINE_VERSION to "7",
        )
        val corrected = SettingsCodec.fromMap(SettingsBaseline.apply(phone, storedVersion = 7)).symPages
        val pages = corrected.pages
        assertEquals(listOf(SymPage.EMOJI_PICKER, SymPage.SYMBOLS, SymPage.GIF), pages.order.take(3))
        assertEquals(true, pages.emojiPickerEnabled)
        assertEquals(true, pages.symbolsEnabled)
        assertEquals(true, pages.gifEnabled)
        assertEquals(listOf(false, false, false, false, false), listOf(pages.emojiEnabled, pages.clipboardEnabled, pages.custom1Enabled, pages.custom2Enabled, pages.custom3Enabled))
        assertEquals(false, corrected.kaomojiEnabled)
        assertEquals(SettingsCodec.fromMap(emptyMap()).symPages.pages, pages, "a fresh install gets the same list")
    }

    @Test
    fun `version 8 leaves the pages a 2x import just carried over`() {
        val imported = mapOf(
            SettingsKeys.SYM_PAGES_CONFIG to """{"emojiEnabled":true,"clipboardEnabled":true,"symPageOrder":["clipboard","emoji"]}""",
            "legacy_import_state" to "imported",
        )
        val result = SettingsBaseline.apply(imported, storedVersion = 0)
        assertEquals(imported[SettingsKeys.SYM_PAGES_CONFIG], result[SettingsKeys.SYM_PAGES_CONFIG])
        assertEquals("false", result[SettingsKeys.EMOJI_PICKER_KAOMOJI])
        // An install that imported long ago and has run 3.0 since is moved like any other.
        val later = SettingsBaseline.apply(imported, storedVersion = 7)
        assertEquals(false, later[SettingsKeys.SYM_PAGES_CONFIG] == imported[SettingsKeys.SYM_PAGES_CONFIG])
    }
}
