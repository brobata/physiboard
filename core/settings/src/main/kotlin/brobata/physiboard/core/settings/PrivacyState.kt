package brobata.physiboard.core.settings

/**
 * What the keyboard may do with what is being typed, right now. spec: app-shell.md SS31.
 *
 * Two things can make it private: the user's own switch, `private_mode` ([privateMode]), and the
 * field being typed in setting Android's "no personalized learning" flag ([fieldAsksNoLearning]),
 * which an incognito browser tab or a banking app sets. Either one stops all learning. Only the
 * user's switch also takes PhysiBoard offline and shows the indicator: a field's flag is a
 * request about learning, and turning the network off under the user would be a surprise.
 *
 * Every place that would remember something reads [learningAllowed]: next-word pairs
 * (`user_ngrams.db`), personal words, clipboard history capture, recent emoji and the debug
 * capture of typed text. What is already known (the dictionaries, personal words, learned pairs,
 * the clipboard history) is still used while it is false; nothing new is written.
 */
data class PrivacyState(
    val privateMode: Boolean = false,
    val fieldAsksNoLearning: Boolean = false,
    /**
     * False until the stored `private_mode` has been read. The keyboard can take keystrokes before
     * its settings arrive; a user who left private mode on must not have those learned, so not
     * knowing counts as private for learning, the same way the network gate treats it.
     */
    val privateModeKnown: Boolean = true,
) {
    val learningAllowed: Boolean get() = privateModeKnown && !privateMode && !fieldAsksNoLearning

    val networkAllowed: Boolean get() = !privateMode

    val showsIndicator: Boolean get() = privateMode
}
