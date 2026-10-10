package brobata.physiboard.core.shell

import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SettingsCodec
import brobata.physiboard.core.settings.SettingsKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: app-shell.md SS3 and SS4 (the first-run rule), tests T34 to T40. */
class LaunchRoutingTest {

    private fun decide(completed: Boolean, enabled: Boolean, selected: Boolean, lastSeen: String? = "3.2.0", current: String = "3.2.0") =
        LaunchRouting.decide(completed, enabled, selected, lastSeen, current)

    @Test
    fun `T34 a fresh install with the keyboard off opens the first-run pages`() {
        assertEquals(LaunchDecision(LaunchDestination.SETUP), decide(completed = false, enabled = false, selected = false, lastSeen = null))
    }

    @Test
    fun `T35 enabled but not chosen still opens the first-run pages, at the step that is left`() {
        assertEquals(LaunchDestination.SETUP, decide(completed = false, enabled = true, selected = false, lastSeen = null).destination)
    }

    @Test
    fun `T36 setup not recorded but the keyboard already on and chosen goes home and records it`() {
        val decision = decide(completed = false, enabled = true, selected = true, lastSeen = null)
        assertEquals(LaunchDestination.HOME, decision.destination)
        assertTrue(decision.markSetupComplete)
    }

    @Test
    fun `T37 once setup is done the pages never open again, whatever the keyboard state`() {
        for (enabled in listOf(false, true)) {
            for (selected in listOf(false, true)) {
                val decision = decide(completed = true, enabled = enabled, selected = selected)
                assertEquals(LaunchDestination.HOME, decision.destination)
                assertFalse(decision.markSetupComplete)
                assertFalse(FirstRun.shouldShow(setupRecorded = true, imeEnabled = enabled, imeSelected = selected))
            }
        }
    }

    @Test
    fun `T38 an install updating from 3_1 sees the what's-new note, not the first-run pages`() {
        // The store a 3.1 phone holds after its own setup: tutorial_completed true, stamped 3.1.0.
        val stored = SettingsCodec.toMap(Settings()).toMutableMap()
        stored[SettingsKeys.TUTORIAL_COMPLETED] = "true"
        stored[SettingsKeys.LAST_SEEN_WHATS_NEW] = "3.1.0"
        val shell = SettingsCodec.fromMap(stored).shell
        // Even with the keyboard switched away (a phone that fell back to the vendor keyboard).
        val decision = LaunchRouting.decide(shell.tutorialCompleted, imeEnabled = true, imeSelected = false, shell.lastSeenWhatsNewVersion, "3.2.0")
        assertEquals(LaunchDecision(LaunchDestination.WHATS_NEW), decision)
    }

    @Test
    fun `T39 a restored backup carries setup done, so a restore does not reopen the pages`() {
        val backedUp = SettingsCodec.toMap(Settings().let { it.copy(shell = it.shell.copy(tutorialCompleted = true, lastSeenWhatsNewVersion = "3.2.0")) })
        val meta = BackupMeta(versionCode = 30200, versionName = "3.2.0", timestampIso = "2026-10-09T00:00:00Z", components = emptyList())
        val restored = BackupRestore.restore(Settings(), BackupFile(meta, backedUp)).settings
        assertTrue(restored.shell.tutorialCompleted)
        assertEquals(LaunchDecision(LaunchDestination.HOME), decide(restored.shell.tutorialCompleted, enabled = false, selected = false))
    }

    @Test
    fun `T40 the what's-new note never comes before setup`() {
        for (lastSeen in listOf(null, "", "  ")) {
            assertEquals(LaunchDestination.SETUP, decide(completed = false, enabled = false, selected = false, lastSeen = lastSeen, current = "3.2.0").destination)
        }
    }

    @Test
    fun `T41 a 3_1 install whose user once reviewed the tutorial is not sent through first run`() {
        // 3.0 and 3.1's Help "Show the tutorial" reset tutorial_completed to false and left the
        // version stamp setup had written.
        val stored = SettingsCodec.toMap(Settings()).toMutableMap()
        stored[SettingsKeys.TUTORIAL_COMPLETED] = "false"
        stored[SettingsKeys.LAST_SEEN_WHATS_NEW] = "3.1.0"
        val shell = SettingsCodec.fromMap(stored).shell
        val decision = LaunchRouting.decide(shell.tutorialCompleted, imeEnabled = false, imeSelected = false, shell.lastSeenWhatsNewVersion, "3.2.0")
        assertEquals(LaunchDecision(LaunchDestination.WHATS_NEW), decision)
        assertEquals(LaunchDecision(LaunchDestination.HOME), LaunchRouting.decide(false, imeEnabled = true, imeSelected = false, "3.2.0", "3.2.0"))
    }

    @Test
    fun `T42 a first run that ends with the overlay granted switches the trackpad on, and only then`() {
        assertEquals(true, FirstRunSetup.trackpadAfterSetup(firstRun = true, overlayGranted = true, trackpadOn = false))
        assertEquals(false, FirstRunSetup.trackpadAfterSetup(firstRun = true, overlayGranted = false, trackpadOn = false))
        // A replay from Help leaves a deliberate off alone.
        assertEquals(false, FirstRunSetup.trackpadAfterSetup(firstRun = false, overlayGranted = true, trackpadOn = false))
        // Never switches it off.
        assertEquals(true, FirstRunSetup.trackpadAfterSetup(firstRun = true, overlayGranted = false, trackpadOn = true))
    }
}
