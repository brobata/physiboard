package brobata.physiboard.core.shell

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** spec: app-shell.md SS31.2. */
class NetworkGateTest {

    @Test
    fun `every purpose goes out when private mode is off`() {
        for (purpose in NetworkPurpose.entries) assertEquals(NetworkDecision.Allowed, NetworkGate.decide(privateMode = false, purpose))
    }

    @Test
    fun `every purpose is refused while private mode is on`() {
        for (purpose in NetworkPurpose.entries) {
            val decision = assertIs<NetworkDecision.Blocked>(NetworkGate.decide(privateMode = true, purpose))
            assertEquals(purpose, decision.purpose)
            assertEquals(NetworkGate.BLOCKED_PRIVATE, decision.reason)
        }
    }

    @Test
    fun `an unreadable setting keeps PhysiBoard offline`() {
        for (purpose in NetworkPurpose.entries) {
            assertEquals(NetworkGate.BLOCKED_UNKNOWN, assertIs<NetworkDecision.Blocked>(NetworkGate.decide(privateMode = null, purpose)).reason)
        }
    }

    /**
     * The gate only works if nothing goes around it. Every Kotlin file PhysiBoard itself ships is
     * scanned for a way to open a network connection; the only file allowed one is the gated
     * opener in `:app`. The embedded ADB broker (`:broker`, and `:device:privileged`'s transport
     * that drives it) is exempt: it talks only to the phone's own adbd over loopback, and only
     * when the user runs the privileged setup (broker-privileged-toolbox.md SS1).
     */
    @Test
    fun `no file opens a connection except the gated opener`() {
        val root = repoRoot()
        val allowed = setOf("app/src/main/kotlin/brobata/physiboard/app/shell/GatedHttp.kt")
        val exemptPrefixes = listOf("broker/", "device/privileged/src/main/kotlin/brobata/physiboard/device/privileged/broker/")
        val opens = Regex("""\bopenConnection\s*\(|\bopenStream\s*\(|\bHttpURLConnection\s*\(|\bHttpsURLConnection\b|\bSocket\s*\(|\bDatagramSocket\b|\bokhttp3\b|\bio\.ktor\b|\bDownloadManager\b|\bWebView\b|\bURL\s*\([^)]*\)\s*\.\s*read""")
        val sources = listOf("app", "ime", "core", "device").flatMap { dir ->
            File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" && "/src/main/" in it.invariantSeparatorsPath && "/build/" !in it.invariantSeparatorsPath }.toList()
        }
        assertTrue(sources.size > 100, "the scan found ${sources.size} files; it is not looking where the code is")
        val offenders = sources.mapNotNull { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            if (relative in allowed || exemptPrefixes.any { relative.startsWith(it) }) return@mapNotNull null
            val code = file.readLines().filterNot { it.trimStart().let { line -> line.startsWith("*") || line.startsWith("//") || line.startsWith("/*") } }.joinToString("\n")
            relative.takeIf { opens.containsMatchIn(code) }
        }
        assertEquals(emptyList(), offenders, "these open a network connection without NetworkGate; route them through GatedHttp")
        assertTrue(File(root, allowed.single()).readText().contains("NetworkGate.decide"), "the gated opener must ask the gate")
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "repository root not found" }
    }
}
