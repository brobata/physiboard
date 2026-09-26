package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: dictation.md SS4.2, the "Recognizer created" resolution table. */
class RecognizerResolutionTest {
    @Test
    fun `empty id resolves to the system default`() {
        assertEquals(RecognizerTarget.SystemDefault, RecognizerResolution.resolve("", onDeviceAvailable = true) { true })
    }

    @Test
    fun `ondevice with an available recognizer resolves to on-device`() {
        assertEquals(RecognizerTarget.OnDevice, RecognizerResolution.resolve("ondevice", onDeviceAvailable = true) { false })
    }

    @Test
    fun `ondevice without an available recognizer falls to the system default`() {
        assertEquals(RecognizerTarget.SystemDefault, RecognizerResolution.resolve("ondevice", onDeviceAvailable = false) { false })
    }

    @Test
    fun `an installed component resolves to that component`() {
        assertEquals(
            RecognizerTarget.Component("com.example/.Service"),
            RecognizerResolution.resolve("com.example/.Service", onDeviceAvailable = false) { it == "com.example/.Service" },
        )
    }

    @Test
    fun `a component no longer installed falls to the system default`() {
        assertEquals(RecognizerTarget.SystemDefault, RecognizerResolution.resolve("com.gone/.Service", onDeviceAvailable = false) { false })
    }
}
