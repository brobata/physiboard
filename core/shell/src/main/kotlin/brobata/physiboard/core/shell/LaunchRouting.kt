package brobata.physiboard.core.shell

/** Where the launcher icon sends the user. spec: app-shell.md SS3. */
enum class LaunchDestination { SETUP, WHATS_NEW, HOME }

/**
 * What the launcher icon does (app-shell.md SS3): the screen to draw first, and whether setup is
 * to be recorded as finished before it is drawn ([markSetupComplete]), which happens when the
 * keyboard turns out to be on and chosen already, so the first-run pages have nothing to do.
 */
data class LaunchDecision(val destination: LaunchDestination, val markSetupComplete: Boolean = false)

/**
 * The decision the launcher icon runs before anything is drawn (app-shell.md SS3). Setup wins
 * outright while it is due ([FirstRun.shouldShow]); the what's-new note is checked only once setup
 * is behind the user; everything else draws home. Scheduling the background update job (SS3 step
 * 3) is the caller's job once this says [LaunchDestination.HOME]: it is a side effect, not a
 * routing decision.
 */
object LaunchRouting {
    /**
     * Whether this install's setup is behind it (app-shell.md SS4.1): `tutorial_completed` is
     * true, or `last_seen_whats_new_version` holds a version. Every setup finish (3.0 to 3.2: the
     * setup page's Skip and Done, the what's-new note's Done, launch routing's own record) writes
     * both together, but 3.0 and 3.1's Help "Show the tutorial" reset `tutorial_completed` to
     * false and left the stamp alone. So a phone updating from 3.0 or 3.1 whose user once
     * reviewed the tutorial holds false with a stamp, and it is the stamp that says setup was
     * finished. Nothing writes the stamp without finishing setup, and nothing clears it.
     */
    fun setupRecorded(tutorialCompleted: Boolean, lastSeenWhatsNewVersion: String?): Boolean =
        tutorialCompleted || !lastSeenWhatsNewVersion.isNullOrBlank()

    fun decide(
        tutorialCompleted: Boolean,
        imeEnabled: Boolean,
        imeSelected: Boolean,
        lastSeenWhatsNewVersion: String?,
        currentVersionName: String,
    ): LaunchDecision {
        val recorded = setupRecorded(tutorialCompleted, lastSeenWhatsNewVersion)
        return when {
            FirstRun.shouldShow(recorded, imeEnabled, imeSelected) -> LaunchDecision(LaunchDestination.SETUP)
            // No record at all, but the keyboard is already on and chosen (app data cleared, a
            // backup of a phone that never finished setup): settle it the way Skip would, version
            // stamp and all, so neither setup nor the what's-new note for this version shows.
            !recorded -> LaunchDecision(LaunchDestination.HOME, markSetupComplete = true)
            WhatsNewDue.isDue(recorded, lastSeenWhatsNewVersion, currentVersionName) -> LaunchDecision(LaunchDestination.WHATS_NEW)
            else -> LaunchDecision(LaunchDestination.HOME)
        }
    }
}

/**
 * The first-run rule (app-shell.md SS4). The pages open only while setup is not recorded
 * ([LaunchRouting.setupRecorded]: neither `tutorial_completed` nor a what's-new version stamp)
 * AND PhysiBoard is not already both enabled and selected. An install updating from 3.0 or 3.1
 * that finished setup there holds one record or the other, so updaters never see the pages; the
 * keyboard check is a second guard for an install whose records were lost but whose keyboard is
 * clearly set up.
 */
object FirstRun {
    fun shouldShow(setupRecorded: Boolean, imeEnabled: Boolean, imeSelected: Boolean): Boolean =
        !setupRecorded && !(imeEnabled && imeSelected)
}

/** spec: app-shell.md SS4.1: whether each first-run step is done, from the one probe both steps share. */
data class FirstRunSteps(val enableDone: Boolean, val selectDone: Boolean) {
    val bothDone: Boolean get() = enableDone && selectDone
}

/** spec: SS4.1. The first-run pages have no state of their own beyond the live probe; this names what it means. */
object FirstRunSetup {
    fun steps(enabled: Boolean, selected: Boolean): FirstRunSteps = FirstRunSteps(enableDone = enabled, selectDone = enabled && selected)

    /**
     * app-shell.md SS4: the screen trackpad (hold Space) after the pages close. It needs "Display
     * over other apps", so a first run that ends with that granted switches it on, the same rule
     * pairing Titan tools follows (broker-privileged-toolbox.md SS7 step 2). Without the
     * permission a hold would only open Android's permission screen, so the switch stays as it
     * was; a replay from Help never touches it.
     */
    fun trackpadAfterSetup(firstRun: Boolean, overlayGranted: Boolean, trackpadOn: Boolean): Boolean =
        trackpadOn || (firstRun && overlayGranted)
}
