package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: app-shell.md SS10.5, SS29 T27. */
class DebugExportPolicyTest {

    @Test
    fun `T27 more than 250 events shares as a file even with a small report and no raw trackpad`() {
        assertEquals(DebugShareMethod.FILE, DebugExportPolicy.shareMethod(recordedEventCount = 251, includeRawTrackpad = false, reportUtf8ByteCount = 100))
    }

    @Test
    fun `T27 250 events and a 400 KiB report share as text`() {
        assertEquals(DebugShareMethod.TEXT, DebugExportPolicy.shareMethod(recordedEventCount = 250, includeRawTrackpad = false, reportUtf8ByteCount = 400 * 1024))
    }

    @Test
    fun `a report over 500 KiB shares as a file`() {
        assertEquals(DebugShareMethod.FILE, DebugExportPolicy.shareMethod(recordedEventCount = 1, includeRawTrackpad = false, reportUtf8ByteCount = 500 * 1024 + 1))
    }

    @Test
    fun `raw trackpad on always shares as a file`() {
        assertEquals(DebugShareMethod.FILE, DebugExportPolicy.shareMethod(recordedEventCount = 0, includeRawTrackpad = true, reportUtf8ByteCount = 0))
    }
}
