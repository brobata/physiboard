package brobata.physiboard.core.shell

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: autocorrect-suggestions.md SS9, "The correction log"; app-shell.md SS11. */
class CorrectionLogTest {

    private fun tempFile(): File = File(Files.createTempDirectory("correction-log").toFile(), CorrectionLog.FILE_NAME)

    private fun entry(at: Long, typed: String, committed: String, source: String = "CONTEXT", detail: String = "share=0.93 cost=0.45") =
        CorrectionLogEntry(at, typed, committed, source, detail, undone = false)

    @Test
    fun `a line round-trips, and a tab or newline in a field is flattened to a space`() {
        val e = CorrectionLogEntry(1_700_000_000_000L, "teh", "the", "CONTEXT", "share=0.99\tcost=0.40", undone = true)
        val decoded = CorrectionLog.decode(CorrectionLog.encode(e))
        assertEquals(e.copy(detail = "share=0.99 cost=0.40"), decoded)
        assertNull(CorrectionLog.decode("not a log line"))
        assertNull(CorrectionLog.decode("x\tteh\tthe\tCONTEXT\t\t0"))
    }

    @Test
    fun `a missing file is an empty log and append creates it`() {
        val file = tempFile()
        assertEquals(emptyList(), CorrectionLog.read(file))
        assertTrue(CorrectionLog.append(file, entry(1, "teh", "the")))
        assertEquals(listOf(entry(1, "teh", "the")), CorrectionLog.read(file))
        assertFalse(File(file.parentFile, file.name + ".tmp").exists(), "the temporary file is renamed away")
    }

    @Test
    fun `the log is bounded, the oldest entries go first`() {
        val file = tempFile()
        repeat(CorrectionLog.CAPACITY + 1) { i -> CorrectionLog.append(file, entry(i.toLong(), "t$i", "c$i")) }
        val kept = CorrectionLog.read(file)
        assertEquals(CorrectionLog.TRIM_TO, kept.size)
        assertEquals(CorrectionLog.CAPACITY.toLong(), kept.last().atMs)
        assertEquals((CorrectionLog.CAPACITY + 1 - CorrectionLog.TRIM_TO).toLong(), kept.first().atMs)
    }

    @Test
    fun `an undo marks the newest matching entry only`() {
        val file = tempFile()
        CorrectionLog.append(file, entry(1, "teh", "the"))
        CorrectionLog.append(file, entry(2, "adn", "and"))
        CorrectionLog.append(file, entry(3, "teh", "the"))
        assertTrue(CorrectionLog.markUndone(file, "teh", "the", nowMs = 10))
        assertEquals(listOf(false, false, true), CorrectionLog.read(file).map { it.undone })
        // Nothing left to mark for a pair that was never logged, and the file is left as it was.
        assertFalse(CorrectionLog.markUndone(file, "xyz", "abc", nowMs = 10))
    }

    @Test
    fun `undoing a mix-up fix marks both words it rewrote`() {
        val file = tempFile()
        // "wagged it's taill" -> "wagged its tail": two entries for one replacement of the span.
        CorrectionLog.append(file, entry(1, "it's", "its", source = "WORD_MIXUP", detail = ""))
        CorrectionLog.append(file, entry(1, "taill", "tail"))
        assertTrue(CorrectionLog.markUndone(file, "it's taill", "its tail", nowMs = 10))
        assertEquals(listOf(true, true), CorrectionLog.read(file).map { it.undone })
    }

    @Test
    fun `an undo looks back only a few entries, never into history`() {
        val file = tempFile()
        CorrectionLog.append(file, entry(1, "teh", "the"))
        repeat(5) { i -> CorrectionLog.append(file, entry(10L + i, "adn$i", "and$i")) }
        assertFalse(CorrectionLog.markUndone(file, "teh", "the", nowMs = 20))
        assertFalse(CorrectionLog.read(file).first().undone)
    }

    @Test
    fun `an undo marks nothing older than a minute, so a text replacement undone leaves yesterday's entry`() {
        val file = tempFile()
        CorrectionLog.append(file, entry(1_000, "teh", "the"))
        assertFalse(CorrectionLog.markUndone(file, "teh", "the", nowMs = 1_000 + CorrectionLog.UNDO_WINDOW_MS + 1))
        assertTrue(CorrectionLog.markUndone(file, "teh", "the", nowMs = 1_000 + CorrectionLog.UNDO_WINDOW_MS))
    }

    @Test
    fun `clear removes the log and a temporary file a crash left behind`() {
        val file = tempFile()
        CorrectionLog.append(file, entry(1, "teh", "the"))
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText("leftover")
        CorrectionLog.clear(file)
        assertFalse(file.exists())
        assertFalse(tmp.exists())
        assertEquals(emptyList(), CorrectionLog.read(file))
    }

    @Test
    fun `export rows carry every field and say when nothing is logged`() {
        assertEquals(listOf("(no corrections logged)"), CorrectionLog.exportLines(emptyList()) { it.toString() })
        val rows = CorrectionLog.exportLines(listOf(entry(5, "teh", "the").copy(undone = true))) { "t$it" }
        assertEquals(listOf("t5 | typed='teh' committed='the' source=CONTEXT share=0.93 cost=0.45 undone=yes"), rows)
    }

    @Test
    fun `a corrupt line is skipped and dropped on the next write`() {
        val file = tempFile()
        CorrectionLog.append(file, entry(1, "teh", "the"))
        file.appendText("garbage line\n")
        assertEquals(1, CorrectionLog.read(file).size)
        CorrectionLog.append(file, entry(2, "adn", "and"))
        assertEquals(2, file.readLines().size)
    }
}
