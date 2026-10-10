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
    fun decide(
        tutorialCompleted: Boolean,
        imeEnabled: Boolean,
        imeSelected: Boolean,
        lastSeenWhatsNewVersion: String?,
        currentVersionName: String,
    ): LaunchDecision = when {
        FirstRun.shouldShow(tutorialCompleted, imeEnabled, imeSelected) -> LaunchDecision(LaunchDestination.SETUP)
        // Not completed, but the keyboard is already on and chosen (app data cleared, a backup of
        // a phone that never finished setup): settle it the way Skip would, version stamp and all,
        // so neither setup nor the what's-new note for this version shows.
        !tutorialCompleted -> LaunchDecision(LaunchDestination.HOME, markSetupComplete = true)
        WhatsNewDue.isDue(tutorialCompleted, lastSeenWhatsNewVersion, currentVersionName) -> LaunchDecision(LaunchDestination.WHATS_NEW)
        else -> LaunchDecision(LaunchDestination.HOME)
    }
}

/**
 * The first-run rule (app-shell.md SS4). The pages open only while `tutorial_completed` is false
 * AND PhysiBoard is not already both enabled and selected. Everyone updating from 3.0 or 3.1 who
 * has seen the home screen has `tutorial_completed` true already (the launcher could not reach
 * home any other way), so updaters never see the pages; the keyboard check is a second guard for
 * an install whose flag was lost but whose keyboard is clearly set up.
 */
object FirstRun {
    fun shouldShow(tutorialCompleted: Boolean, imeEnabled: Boolean, imeSelected: Boolean): Boolean =
        !tutorialCompleted && !(imeEnabled && imeSelected)
}

/** spec: app-shell.md SS4.1: whether each first-run step is done, from the one probe both steps share. */
data class FirstRunSteps(val enableDone: Boolean, val selectDone: Boolean) {
    val bothDone: Boolean get() = enableDone && selectDone
}

/** spec: SS4.1. The first-run pages have no state of their own beyond the live probe; this names what it means. */
object FirstRunSetup {
    fun steps(enabled: Boolean, selected: Boolean): FirstRunSteps = FirstRunSteps(enableDone = enabled, selectDone = enabled && selected)
}
