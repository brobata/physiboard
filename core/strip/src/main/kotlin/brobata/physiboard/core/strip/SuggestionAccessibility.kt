package brobata.physiboard.core.strip

/**
 * The suggestion row's live announcements. spec: status-bar.md SS5.6, kept for 3.0 ("Accessibility
 * announcement throttling", SS19). The row itself is "never a live region"; instead, on a change,
 * its descendants are hidden from accessibility for a delay and then re-exposed with an
 * announcement, so a screen reader speaks the settled slots once rather than every intermediate
 * frame of a fast correction. `:ime` owns the one real timer ("any new update cancels a pending
 * announcement" is a `Handler.removeCallbacks`-and-repost, the same idiom this codebase already
 * uses for the cursor-update and dip timers); this object only answers the three pure questions
 * that timer needs: whether to start it at all, what to say, and whether saying it again would be
 * a no-op repeat of the last thing spoken.
 */
object SuggestionAccessibility {
    /** spec SS15: `accessibility_suggestions_announcement_delay_ms`, default 500 ms. */
    const val DEFAULT_DELAY_MS: Long = 500

    /** spec SS5.6: "never negative". A stored negative value (a malformed import) floors at zero, an immediate announcement rather than a crash. */
    fun delayMs(configuredMs: Long): Long = configuredMs.coerceAtLeast(0)

    /** spec SS5.6: an announcement is only scheduled "with 'live announcements' on... and at least one non-blank slot". */
    fun shouldSchedule(liveAnnouncementsEnabled: Boolean, slotTexts: List<String>): Boolean =
        liveAnnouncementsEnabled && slotTexts.any { it.isNotBlank() }

    /** spec SS5.6: "announces the non-blank slot texts joined by ', '". */
    fun announcementText(slotTexts: List<String>): String = slotTexts.filter { it.isNotBlank() }.joinToString(", ")

    /** spec SS5.6: announced only "if they differ from the last announcement", so a re-render of the same slots stays silent. */
    fun isNewAnnouncement(text: String, lastAnnounced: String?): Boolean = text != lastAnnounced

    /** spec SS5.6: "The language button... state description reads 'Language X, layout Y'." */
    fun languageStateDescription(languageLabel: String, layoutName: String): String = "Language $languageLabel, layout $layoutName"
}
