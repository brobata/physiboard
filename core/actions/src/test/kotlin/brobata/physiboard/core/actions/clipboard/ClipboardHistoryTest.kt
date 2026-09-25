package brobata.physiboard.core.actions.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS12, T18 to T23, and the SS11 clipboard rows. */
class ClipboardHistoryTest {

    private val minute = 60_000L

    @Test
    fun `T18 copying a, b, a gives two entries with a newest`() {
        val h = ClipboardHistory().capture("a", 1).capture("b", 2).capture("a", 3)
        assertEquals(listOf("a", "b"), h.ordered.map { it.text })
        assertEquals(2, h.count)
    }

    @Test
    fun `T19 cleanup with retention 5 keeps the pinned and the fresh entry, pinned first`() {
        val now = 100 * minute
        val h = ClipboardHistory(
            entries = listOf(
                Clip(1, "old", now - 6 * minute),
                Clip(2, "old pinned", now - 6 * minute, pinned = true),
                Clip(3, "fresh", now - 1 * minute),
            ),
            nextId = 4,
        ).cleanup(now, retentionMinutes = 5).history
        assertEquals(listOf("old pinned", "fresh"), h.ordered.map { it.text })
    }

    @Test
    fun `T20 retention 0 never expires anything`() {
        val now = 2 * 24 * 60 * minute
        val h = ClipboardHistory(entries = listOf(Clip(1, "day old", now - 24 * 60 * minute)), nextId = 2).cleanup(now, retentionMinutes = 0).history
        assertEquals(1, h.count)
    }

    @Test
    fun `T21 Clear All keeps pinned entries`() {
        var h = ClipboardHistory().capture("x", 1).capture("y", 2)
        val xId = h.entries.first { it.text == "x" }.id
        h = h.togglePinned(xId, 3).clearAll()
        assertEquals(listOf("x"), h.entries.map { it.text })
    }

    @Test
    fun `T22 Delete removes a pinned entry`() {
        var h = ClipboardHistory().capture("x", 1)
        val id = h.entries[0].id
        h = h.togglePinned(id, 2).delete(id)
        assertEquals(0, h.count)
    }

    @Test
    fun `T23 an unforced cleanup within 5 s of the last is skipped`() {
        val first = ClipboardHistory().cleanup(10_000, 5, forced = true)
        assertTrue(first.ran)
        val second = first.history.cleanup(12_000, 5, forced = false)
        assertFalse(second.ran)
        val third = first.history.cleanup(12_000, 5, forced = true)
        assertTrue(third.ran)
    }

    @Test
    fun `pin refreshes the timestamp so the entry tops its new group`() {
        var h = ClipboardHistory().capture("a", 1).capture("b", 2)
        val aId = h.entries.first { it.text == "a" }.id
        h = h.togglePinned(aId, 3)
        assertEquals(listOf("a", "b"), h.ordered.map { it.text })
        h = h.togglePinned(aId, 4)
        assertEquals(listOf("a", "b"), h.ordered.map { it.text }, "unpinned with the refreshed timestamp, it is now the most recent")
    }

    @Test
    fun `the capture rule takes text clips and untyped clips, refuses empty and sensitive ones`() {
        assertTrue(ClipCapture.accepts(listOf("text/plain"), "x", sensitive = false))
        assertTrue(ClipCapture.accepts(emptyList(), "x", sensitive = false))
        assertFalse(ClipCapture.accepts(listOf("image/png"), "x", sensitive = false))
        assertFalse(ClipCapture.accepts(listOf("text/plain"), "", sensitive = false))
        assertFalse(ClipCapture.accepts(listOf("text/plain"), "hunter2", sensitive = true), "SS13: 3.0 honours the sensitive flag")
    }

    @Test
    fun `a stored row with the same text as an in-memory entry is superseded, not loaded twice`() {
        val inMemory = ClipboardHistory().capture("a", 50)
        val (merged, superseded) = inMemory.mergeLoaded(listOf(Clip(7, "a", 10), Clip(8, "b", 20)))
        assertEquals(listOf(7L), superseded)
        assertEquals(listOf("a", "b"), merged.ordered.map { it.text })
        assertEquals(9, merged.nextId)
    }

    @Test
    fun `duplicate rows already in storage are collapsed, keeping the latest timestamp`() {
        val (merged, superseded) = ClipboardHistory().mergeLoaded(listOf(Clip(1, "dup", 10), Clip(2, "dup", 30), Clip(3, "dup", 20)))
        assertEquals(setOf(1L, 3L), superseded.toSet(), "the two older duplicate rows are cleaned up")
        assertEquals(listOf(Clip(2, "dup", 30)), merged.entries, "the latest-timestamped duplicate is the one kept")
        assertEquals(4, merged.nextId)
    }
}
