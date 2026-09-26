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
    fun `the production CURRENT_VERSION is 1 with an empty correction table, and applying it just stamps the marker`() {
        val result = SettingsBaseline.apply(emptyMap(), storedVersion = 0)
        assertEquals(SettingsBaseline.CURRENT_VERSION.toString(), result[SettingsKeys.BASELINE_VERSION])
        assertEquals(1, result.size, "no known-wrong default exists yet, so nothing besides the marker should change")
    }
}
