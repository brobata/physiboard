package brobata.physiboard.core.shell

/** Where the launcher icon sends the user. spec: app-shell.md SS3. */
enum class LaunchDestination { SETUP, WHATS_NEW, HOME }

/**
 * The decision the launcher icon runs before anything is drawn (app-shell.md SS3). Setup wins
 * outright; the what's-new note is checked only once setup is behind the user; everything else
 * draws home. Scheduling the background update job (SS3 step 3) is the caller's job once this
 * says [LaunchDestination.HOME]: it is a side effect, not a routing decision.
 */
object LaunchRouting {
    fun decide(tutorialCompleted: Boolean, lastSeenWhatsNewVersion: String?, currentVersionName: String): LaunchDestination = when {
        !tutorialCompleted -> LaunchDestination.SETUP
        WhatsNewDue.isDue(tutorialCompleted, lastSeenWhatsNewVersion, currentVersionName) -> LaunchDestination.WHATS_NEW
        else -> LaunchDestination.HOME
    }
}

/** spec: app-shell.md SS4.1: whether each first-run step is done, from the one probe both steps share. */
data class FirstRunSteps(val enableDone: Boolean, val selectDone: Boolean) {
    val bothDone: Boolean get() = enableDone && selectDone
}

/** spec: SS4.1, SS4.2. The setup screen has no state of its own beyond the live probe; this names what it means. */
object FirstRunSetup {
    fun steps(enabled: Boolean, selected: Boolean): FirstRunSteps = FirstRunSteps(enableDone = enabled, selectDone = enabled && selected)
}
