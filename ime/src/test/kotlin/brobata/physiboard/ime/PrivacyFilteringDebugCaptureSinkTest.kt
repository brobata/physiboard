package brobata.physiboard.ime

import brobata.physiboard.core.settings.PrivacyState
import brobata.physiboard.core.shell.AutocorrectionRecord
import brobata.physiboard.core.shell.ImeContextSnapshot
import brobata.physiboard.core.shell.KeyboardEventRecord
import kotlin.test.Test
import kotlin.test.assertEquals

/** app-shell.md SS31: no debug capture of typed text while learning is off. */
class PrivacyFilteringDebugCaptureSinkTest {

    private val received = mutableListOf<String>()
    private val delegate = object : DebugCaptureSink {
        override fun report(event: KeyboardEventRecord) { received += "key ${event.keyCode}" }
        override fun reportFieldAttach(snapshot: ImeContextSnapshot, isPhysiBoardOwnPackage: Boolean) { received += "field ${snapshot.packageName}" }
        override fun recordAutocorrection(record: AutocorrectionRecord) { received += "fix ${record.before}>${record.after}" }
    }

    private var privacy = PrivacyState()
    private val sink = PrivacyFilteringDebugCaptureSink(delegate) { privacy.learningAllowed }

    private val key = KeyboardEventRecord(
        origin = "ime_service", action = "KEY_DOWN", keyCode = 29, scanCode = 30, deviceId = 1, source = 257, flags = 8,
        repeatCount = 0, metaState = 0, unicodeRaw = 'a'.code, unicodeEffective = 'a'.code, layout = "qwerty",
        shift = false, ctrl = false, alt = false, altLatch = false, altOneShot = false, shiftLatch = false, ctrlLatch = false,
        symPage = "none", eventUptimeMs = 1,
    )
    private val field = ImeContextSnapshot(atMs = 1, packageName = "com.example.bank", fields = emptyMap())
    private val fix = AutocorrectionRecord(atMs = 1, type = "auto", trigger = "space", source = "dict", outcome = "replaced", before = "teh", after = "the", reason = "")

    private fun sendAll() {
        sink.report(key)
        sink.reportFieldAttach(field, isPhysiBoardOwnPackage = false)
        sink.recordAutocorrection(fix)
    }

    @Test
    fun `everything passes while learning is allowed`() {
        sendAll()
        assertEquals(listOf("key 29", "field com.example.bank", "fix teh>the"), received)
    }

    @Test
    fun `nothing passes in private mode`() {
        privacy = PrivacyState(privateMode = true)
        sendAll()
        assertEquals(emptyList(), received)
    }

    @Test
    fun `nothing passes in a field that asks for no personalized learning`() {
        privacy = PrivacyState(fieldAsksNoLearning = true)
        sendAll()
        assertEquals(emptyList(), received)
    }

    @Test
    fun `the gate is read at every record, so leaving private mode resumes capture`() {
        privacy = PrivacyState(privateMode = true)
        sink.report(key)
        privacy = PrivacyState()
        sink.report(key)
        assertEquals(listOf("key 29"), received)
    }
}
