package brobata.physiboard.ime

/**
 * The component names the orange side key's long-press slot may carry for PhysiBoard. spec:
 * dictation.md SS11.3 ("The trampoline's name is a contract").
 *
 * The vendor row `func1_long_press_activity` is written once, by `SideKeyAssistantRemap.bind`,
 * and then outlives every update of the app (and even an uninstall). A build that stops declaring
 * a name the row carries breaks the key silently: the vendor's long press still fires its haptic,
 * then its start of the missing class fails. That is what 3.0.0 did to a row the 2.x line had
 * written, found on the maintainer's phone on 2026-10-22 (`cmd package resolve-activity` on the
 * row's value: "No activity found"). So every name ever written is served: [CURRENT] by the
 * activity itself, [LEGACY] by an exported `activity-alias` in the manifest, and the Voice
 * screen's switch reads "on" only for a row naming one of [SERVED].
 */
object AssistantTriggerNames {
    /** The activity's own class name, what `SideKeyAssistantRemap.bind` writes today. */
    const val CURRENT = "brobata.physiboard.ime.AssistantTriggerActivity"

    /** The name the 2.x line wrote into the same row; served by the manifest alias. */
    const val LEGACY = "brobata.physiboard.inputmethod.AssistantTriggerActivity"

    /** Every name a phone's row may carry that this build answers to. */
    val SERVED: Set<String> = setOf(CURRENT, LEGACY)
}
