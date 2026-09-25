package brobata.physiboard.core.shell

import java.security.MessageDigest

/**
 * One `[section]` of the debug export: its bracket name (blank for the unbracketed header block)
 * and its already-rendered `key=value` / event lines. spec: app-shell.md SS10.6. Every section's
 * own field list (`[system]`, `[device]`, `[privileged]`, the event rows, ...) is rendered by the
 * module that owns those facts (device detection here, `:device:privileged`'s
 * `PrivilegedExport`, the keyboard's own event tracker); this assembler only knows the order and
 * the trailing digest.
 */
data class ReportSection(val name: String, val lines: List<String>)

/**
 * Assembles the plain-text debug report (app-shell.md SS10.6): the fixed header, each section in
 * the order it is given, and a trailing SHA-256 of everything above it. Pure text in, pure text
 * out, so the digest and the section order are JVM-testable without a device.
 */
object DiagnosticsReport {

    /** [exportedAt] is `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` already formatted by the caller (Android glue owns the clock and time zone). */
    fun assemble(exportedAt: String, timezoneId: String, timezoneOffsetSeconds: Long, sections: List<ReportSection>): String {
        val body = StringBuilder()
        body.append("=== PhysiBoard Debug Export ===\n")
        body.append("exported_at=$exportedAt\n")
        body.append("timezone_id=$timezoneId\n")
        body.append("timezone_offset=${timezoneOffsetSeconds}s\n")
        for (section in sections) {
            body.append('\n')
            if (section.name.isNotEmpty()) body.append("[${section.name}]\n")
            for (line in section.lines) body.append(line).append('\n')
        }
        val digest = sha256Hex(body.toString())
        return body.append("\nsha256=$digest\n").toString()
    }

    fun sha256Hex(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * spec: SS10.6 `[settings_snapshot]`: every preference key=value sorted by key, string sets sorted
 * and bracketed, null as "null". Reads a flat settings map (the same shape [SettingsCodec.toMap]
 * produces) so the export can never disagree with the store about a value.
 */
object SettingsSnapshotExport {
    fun lines(flatMap: Map<String, String?>): List<String> =
        flatMap.toSortedMap().map { (key, value) -> "$key=${value ?: "null"}" }
}
