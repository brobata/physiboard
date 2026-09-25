package brobata.physiboard.core.shell

/**
 * The two markers a settings row can carry (app-shell.md SS18). spec: SS30 Keep/Drop drops
 * feature-status markers for 3.0 ("only Construction on soft-keyboard-adjacent screens", none of
 * which exist in this milestone), so [text] is kept as the one place the wording lives if a future
 * screen needs it, but nothing in `:app` attaches a marker to a row.
 */
enum class FeatureStatus(val text: String) {
    CONSTRUCTION("This feature is work in progress or planned. It may be incomplete, unavailable, or still change."),
    EXPERIMENTAL("This may already work, but it is new and may soon be changed or improved. Bug reports are welcome."),
}
