package brobata.physiboard.core.shell

/** How "Share" hands the debug report to the system chooser. spec: app-shell.md SS10.5. */
enum class DebugShareMethod { TEXT, FILE }

/**
 * spec: SS10.5, T27. "Share" switches to a file when the raw-trackpad chip is on, more than 250
 * events are recorded, or the rendered report exceeds 500 KiB in UTF-8. Pure over already-known
 * facts so the 250/500 KiB boundary is a JVM test, not something only a real share sheet proves.
 */
object DebugExportPolicy {
    private const val EVENT_COUNT_THRESHOLD = 250
    private const val SIZE_THRESHOLD_BYTES = 500 * 1024

    fun shareMethod(recordedEventCount: Int, includeRawTrackpad: Boolean, reportUtf8ByteCount: Int): DebugShareMethod {
        val asFile = includeRawTrackpad || recordedEventCount > EVENT_COUNT_THRESHOLD || reportUtf8ByteCount > SIZE_THRESHOLD_BYTES
        return if (asFile) DebugShareMethod.FILE else DebugShareMethod.TEXT
    }

    /** spec: SS10.5, the file name pattern `physiboard-keyboard-debug-<yyyyMMdd-HHmmss>.txt`. Formatting the timestamp is Android glue's job; this just fixes the shape. */
    fun fileName(timestampSuffix: String): String = "physiboard-keyboard-debug-$timestampSuffix.txt"
}
