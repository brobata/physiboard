package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: dictation.md SS4.1, the picker's friendly names, details, and copy. */
class SpeechEngineCatalogTest {
    @Test
    fun `google package gets the Google label`() {
        assertEquals("Google", SpeechEngineCatalog.friendlyName("com.google.android.tts", "Speech Services by Google"))
    }

    @Test
    fun `android system intelligence package gets its own label`() {
        assertEquals("Android System Intelligence", SpeechEngineCatalog.friendlyName("com.google.android.as", "Android System Intelligence"))
    }

    @Test
    fun `unknown package falls back to its app label`() {
        assertEquals("Acme Dictation", SpeechEngineCatalog.friendlyName("com.acme.dictation", "Acme Dictation"))
    }

    @Test
    fun `unknown package with no readable label falls back to the package name`() {
        assertEquals("com.acme.dictation", SpeechEngineCatalog.friendlyName("com.acme.dictation", null))
        assertEquals("com.acme.dictation", SpeechEngineCatalog.friendlyName("com.acme.dictation", ""))
    }

    @Test
    fun `detail text is keyed by package with an any-other fallback`() {
        assertTrue(SpeechEngineCatalog.detailFor("com.google.android.tts").contains("Google voice typing"))
        assertTrue(SpeechEngineCatalog.detailFor("com.google.android.as").contains("Live Caption"))
        assertTrue(SpeechEngineCatalog.detailFor("io.homeassistant.companion.android").contains("Home Assistant"))
        assertTrue(SpeechEngineCatalog.detailFor("com.anthropic.claude").contains("Claude"))
        assertEquals(
            "A speech engine added by this app. How well it hears you, and whether it needs a connection, is up to that app.",
            SpeechEngineCatalog.detailFor("com.example.other"),
        )
    }

    @Test
    fun `system default detail names the current voice recognition service`() {
        assertEquals(
            "Follows Android's voice input setting, which is Google today. Pick this to keep matching the rest of the phone.",
            SpeechEngineCatalog.systemDefaultDetail("Google"),
        )
    }

    @Test
    fun `system default detail falls back when the secure key is empty or unreadable`() {
        assertEquals("Follows Android's voice input setting", SpeechEngineCatalog.systemDefaultDetail(null))
        assertEquals("Follows Android's voice input setting", SpeechEngineCatalog.systemDefaultDetail(""))
    }
}
