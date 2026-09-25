package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: app-shell.md SS11, SS29 T28-T30. */
class DebugCaptureStoreTest {

    @Test
    fun `T28 a not-applicable auto_replace_disabled attempt with blank before and after is pure noise`() {
        val store = DebugCaptureStore()
        store.recordAutocorrection(
            AutocorrectionRecord(atMs = 1, type = "attempt", trigger = "space", source = "s", outcome = "not_applicable", before = "", after = "", reason = "auto_replace_disabled"),
        )
        assertTrue(store.autocorrections().isEmpty())

        store.recordAutocorrection(
            AutocorrectionRecord(atMs = 2, type = "attempt", trigger = "space", source = "s", outcome = "not_applicable", before = "teh", after = "", reason = "auto_replace_disabled"),
        )
        assertEquals(1, store.autocorrections().size)
    }

    @Test
    fun `T29 the autocorrection buffer keeps only the newest 100`() {
        val store = DebugCaptureStore()
        repeat(101) { i ->
            store.recordAutocorrection(
                AutocorrectionRecord(atMs = i.toLong(), type = "commit", trigger = "space", source = "s", outcome = "applied", before = "teh$i", after = "the$i", reason = ""),
            )
        }
        val kept = store.autocorrections()
        assertEquals(100, kept.size)
        assertEquals(1L, kept.first().atMs) // the oldest (index 0) is gone
        assertEquals(100L, kept.last().atMs)
    }

    @Test
    fun `T30 consecutive identical suggestion snapshots collapse and empties are dropped`() {
        val store = DebugCaptureStore()
        store.recordSuggestionSnapshot(SuggestionSnapshot(1, listOf("A", "B")))
        store.recordSuggestionSnapshot(SuggestionSnapshot(2, listOf("A", "B")))
        store.recordSuggestionSnapshot(SuggestionSnapshot(3, emptyList()))
        store.recordSuggestionSnapshot(SuggestionSnapshot(4, listOf("C")))

        val rows = SuggestionExport.collapse(store.suggestionSnapshots())
        assertEquals(2, rows.size)
        assertEquals(listOf("A", "B"), rows[0].candidates)
        assertEquals(2, rows[0].repeatCount)
        assertEquals(2L, rows[0].atMs) // carries the last timestamp of the run
        assertEquals(listOf("C"), rows[1].candidates)
        assertEquals(1, rows[1].repeatCount)
    }

    @Test
    fun `clear wipes every buffer and both context slots`() {
        val store = DebugCaptureStore()
        store.recordAutocorrection(AutocorrectionRecord(1, "commit", "space", "s", "applied", "teh", "the", ""))
        store.recordSuggestionSnapshot(SuggestionSnapshot(1, listOf("A")))
        store.recordRawTrackpad(RawTrackpadRecord(1, "line"))
        store.recordFieldAttach(ImeContextSnapshot(1, "brobata.physiboard", emptyMap()), isPhysiBoardOwnPackage = true)

        store.clear()

        assertTrue(store.autocorrections().isEmpty())
        assertTrue(store.suggestionSnapshots().isEmpty())
        assertTrue(store.rawTrackpadEvents().isEmpty())
        assertEquals(null, store.lastField())
        assertEquals(null, store.lastFieldFromAnotherApp())
    }

    @Test
    fun `spec SS10_7, T31 the external-app slot updates only for a non-PhysiBoard field`() {
        val store = DebugCaptureStore()
        store.recordFieldAttach(ImeContextSnapshot(1, "com.other.app", mapOf("k" to "v")), isPhysiBoardOwnPackage = false)
        store.recordFieldAttach(ImeContextSnapshot(2, "brobata.physiboard", emptyMap()), isPhysiBoardOwnPackage = true)

        assertEquals("brobata.physiboard", store.lastField()?.packageName)
        assertEquals("com.other.app", store.lastFieldFromAnotherApp()?.packageName)
    }
}
