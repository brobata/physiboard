package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: app-shell.md SS10.6. */
class DiagnosticsReportTest {

    @Test
    fun `sections render in order under their bracket headers, ending with a matching sha256`() {
        val report = DiagnosticsReport.assemble(
            exportedAt = "2026-09-24T00:00:00.000+00:00",
            timezoneId = "UTC",
            timezoneOffsetSeconds = 0,
            sections = listOf(
                ReportSection("system", listOf("android_release=15")),
                ReportSection("events", listOf("(no recorded events)")),
            ),
        )
        assertTrue(report.startsWith("=== PhysiBoard Debug Export ===\n"))
        val systemIndex = report.indexOf("[system]")
        val eventsIndex = report.indexOf("[events]")
        assertTrue(systemIndex in 0 until eventsIndex)
        assertTrue(report.contains("android_release=15"))

        val bodyBeforeDigest = report.substringBeforeLast("\nsha256=")
        val expectedDigest = DiagnosticsReport.sha256Hex(bodyBeforeDigest)
        val actualDigestLine = report.substringAfterLast("sha256=").trim()
        assertEquals(expectedDigest, actualDigestLine)
    }

    @Test
    fun `settings_snapshot sorts keys and prints null for an absent value`() {
        val lines = SettingsSnapshotExport.lines(mapOf("b_key" to "2", "a_key" to null, "c_key" to "3"))
        assertEquals(listOf("a_key=null", "b_key=2", "c_key=3"), lines)
    }
}
