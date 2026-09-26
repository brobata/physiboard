package brobata.physiboard.core.actions.picker

/**
 * The intent action and extras `:ime` and `:app` agree on for autocorrect-suggestions.md SS8.5's
 * "Add substitution" sheet, reached by long-pressing the strip's add-word candidate (SS6.2). Plain
 * string constants (no android import) so `:ime` (building the [android.content.Intent]) and
 * `:app` (the sheet activity) share one spelling without either module depending on the other.
 */
object AddSubstitutionSheet {
    /** Package-restricted, matching [brobata.physiboard.core.actions.launcher.AssignmentSheet.ACTION_ASSIGN_KEY]'s own pattern. */
    const val ACTION_ADD_SUBSTITUTION: String = "brobata.physiboard.action.ADD_SUBSTITUTION"

    /** The word the sheet's "Replacement: <word>" line shows and, on Save, offers to the personal dictionary. */
    const val EXTRA_WORD: String = "brobata.physiboard.extra.SUBSTITUTION_WORD"

    /** spec SS8.5: "the custom set for the current subtype language (falling back to `it` when the subtype has no language)". */
    const val EXTRA_LANGUAGE_CODE: String = "brobata.physiboard.extra.SUBSTITUTION_LANGUAGE_CODE"
}
