package brobata.physiboard.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: app-shell.md SS31, settings-catalog.md SS2.17. */
class PrivacyStateTest {

    @Test
    fun `ordinary typing learns, goes online and shows nothing`() {
        val s = PrivacyState()
        assertTrue(s.learningAllowed)
        assertTrue(s.networkAllowed)
        assertFalse(s.showsIndicator)
    }

    @Test
    fun `private mode learns nothing, goes offline and shows the indicator`() {
        val s = PrivacyState(privateMode = true)
        assertFalse(s.learningAllowed)
        assertFalse(s.networkAllowed)
        assertTrue(s.showsIndicator)
    }

    @Test
    fun `a field that asks for no personalized learning stops learning only`() {
        val s = PrivacyState(fieldAsksNoLearning = true)
        assertFalse(s.learningAllowed)
        assertTrue(s.networkAllowed, "a field's flag is about learning, not the network")
        assertFalse(s.showsIndicator)
    }

    @Test
    fun `nothing is learned before the stored setting has been read`() {
        val s = PrivacyState(privateModeKnown = false)
        assertFalse(s.learningAllowed)
        assertFalse(s.showsIndicator, "not knowing is not the same as the user's switch")
    }

    @Test
    fun `both together are as private as private mode`() {
        val s = PrivacyState(privateMode = true, fieldAsksNoLearning = true)
        assertFalse(s.learningAllowed)
        assertFalse(s.networkAllowed)
    }

    @Test
    fun `private mode ships off and clean links ships on, under their own keys`() {
        assertFalse(Settings().privacy.privateMode)
        assertTrue(Settings().privacy.cleanLinks)
        val map = SettingsCodec.toMap(Settings())
        assertEquals("false", map["private_mode"])
        assertEquals("true", map["clean_links"])
    }

    @Test
    fun `private mode persists through the store's flat map`() {
        val on = Settings().copy(privacy = PrivacyPrefs(privateMode = true, cleanLinks = false))
        assertEquals(on.privacy, SettingsCodec.fromMap(SettingsCodec.toMap(on)).privacy)
        assertEquals(PrivacyPrefs(), SettingsCodec.fromMap(mapOf("private_mode" to "maybe")).privacy, "a malformed value reads as the default")
    }
}
