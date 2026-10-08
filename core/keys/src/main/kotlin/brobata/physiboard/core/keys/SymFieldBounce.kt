package brobata.physiboard.core.keys

/**
 * The text box an app just took away, as the Sym key remembers it. spec: layers-sym-alt.md SS5.2
 * ("A text box that went away").
 *
 * [openPage] is the Sym page that was open when it went (0 for none).
 */
data class FieldLoss(val packageName: String, val atMs: Long, val openPage: Int)

/**
 * Sym and a text box that comes and goes in the same app. spec: layers-sym-alt.md SS5.2.
 *
 * Found on the Titan (2026-10-07): with the emoji page open in Messages the maintainer took a
 * screenshot. The screenshot window took focus, and when Messages got it back it had no focused
 * text box (Android logged `HIDE_SAME_WINDOW_FOCUSED_WITHOUT_EDITOR`), so the keyboard closed the
 * page, and the next Sym, with no text box, armed the launcher shortcuts and toasted "Press
 * shortcut key to launch". To the person holding the phone the box was still on screen and Sym
 * had simply broken.
 *
 * Two rules come out of it, both limited to the same app and [WINDOW_MS] after the box went:
 * - Sym does not arm the launcher shortcuts there ([symWantsTheField]); the keyboard says to tap
 *   the box instead, since a Sym page has nothing to type into without one.
 * - When that app's text box comes back, the page that was open comes back with it
 *   ([pageToRestore]).
 */
object SymFieldBounce {
    /** How long after the box went the two rules hold. */
    const val WINDOW_MS: Long = 15_000

    /**
     * The box of [packageName] went away at [nowMs] with [openPage] open. A second report of the
     * same loss (the platform finishes the field, then starts a box-less one) keeps the page the
     * first report saw, since the first already closed it.
     */
    fun onEditorLost(previous: FieldLoss?, packageName: String?, openPage: Int, nowMs: Long): FieldLoss? {
        if (packageName.isNullOrEmpty()) return previous
        val samePrevious = previous?.takeIf { it.packageName == packageName && within(it, nowMs) }
        val page = if (openPage != 0) openPage else samePrevious?.openPage ?: 0
        return FieldLoss(packageName, samePrevious?.atMs ?: nowMs, page)
    }

    /** The page to reopen as [packageName]'s text box starts at [nowMs], or 0. */
    fun pageToRestore(loss: FieldLoss?, packageName: String?, nowMs: Long): Int {
        loss ?: return 0
        if (loss.packageName != packageName || !within(loss, nowMs)) return 0
        return loss.openPage
    }

    /** Whether a Sym press at [nowMs] with no text box, in [packageName], is meant for the box that just went. */
    fun symWantsTheField(loss: FieldLoss?, packageName: String?, nowMs: Long): Boolean =
        loss != null && !packageName.isNullOrEmpty() && loss.packageName == packageName && within(loss, nowMs)

    private fun within(loss: FieldLoss, nowMs: Long): Boolean = nowMs - loss.atMs in 0..WINDOW_MS

    /** The toast for a Sym press [symWantsTheField] caught. */
    const val TAP_THE_BOX: String = "Tap the text box, then Sym"
}
