package brobata.physiboard.core.keys

/**
 * Command ids this module itself emits through [Action.RunCommand], distinct from ids that come
 * from a caller-supplied [CtrlMappingTable] entry or a launcher shortcut, whose catalogues belong
 * to other modules.
 *
 * spec: keys-and-modifiers.md SS3.3 (dictation toggle), SS15 item 3 (nav mode exit), SS4.4 /
 * layers-sym-alt.md SS5.6 (the assistant), SS5.10 (the Sym page chooser).
 */
object KeyCommands {
    const val TOGGLE_DICTATION: String = "physiboard.toggle_dictation"
    const val EXIT_NAV_MODE: String = "physiboard.exit_nav_mode"
    const val LAUNCH_ASSISTANT: String = "physiboard.launch_assistant"
    const val SWITCH_LAYOUT: String = "physiboard.switch_layout"

    /** Also a catalogue command (`CommandIds.SYM_PAGE_CHOOSER`), so it can sit on a launcher key or the Fn layer. */
    const val OPEN_SYM_PAGE_CHOOSER: String = "physiboard.sym_page_chooser"
}
