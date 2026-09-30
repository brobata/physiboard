package brobata.physiboard.core.strip

/** spec: status-bar.md SS3.5, the "Show status bar" setting's three values. */
enum class StripVisibilityMode { ALWAYS, NEVER, APPS }

/**
 * Whether the strip has a footprint. spec: status-bar.md SS3.4: a hidden strip "collapses to
 * zero height. The window still exists, the input method is still active and hardware keys
 * still work"; hiding the window is never this decision's answer.
 */
enum class StripFootprint { SHOWN, COLLAPSED }

/**
 * The visibility decision, made "on every refresh from `status_bar_visibility` and the app being
 * typed into". spec: status-bar.md SS3.4, SS3.5, SS17.
 */
object StripVisibility {
    /**
     * spec SS3.5's table (T1 to T4): `ALWAYS` shows, `NEVER` hides, `APPS` shows only when the
     * field's package is listed, and "No package (no field) counts as not listed".
     */
    fun isShownForApp(mode: StripVisibilityMode, apps: Set<String>, packageName: String?): Boolean = when (mode) {
        StripVisibilityMode.ALWAYS -> true
        StripVisibilityMode.NEVER -> false
        StripVisibilityMode.APPS -> packageName != null && packageName in apps
    }

    /**
     * The whole footprint decision. spec SS3.4: nav mode latched makes the root invisible with
     * the window up; SS3.5's exception: "a Sym page still shows while the mode says hidden, and
     * the strip collapses again when the page closes" (SS17, "Strip hidden by mode, user presses
     * Sym").
     *
     * SPEC GAP: nothing says which wins when nav mode is latched and a Sym page is open at once.
     * Nav mode wins here, since it is the only case where the strip must contribute no height for
     * the whole latch, not just for a page's lifetime.
     */
    fun footprint(
        mode: StripVisibilityMode,
        apps: Set<String>,
        packageName: String?,
        symPageOpen: Boolean,
        navModeLatched: Boolean,
        fieldOffersSuggestions: Boolean = true,
        hideWhereNothingToSuggest: Boolean = true,
        fieldDrawsUnderStrip: Boolean = false,
    ): StripFootprint = when {
        navModeLatched -> StripFootprint.COLLAPSED
        // Ahead of the Sym exception: a page the user cannot see under the app's own message box
        // is worse than no page. See [StripOverlap].
        fieldDrawsUnderStrip -> StripFootprint.COLLAPSED
        symPageOpen -> StripFootprint.SHOWN
        // A field that allows no suggestions can never fill a slot, so the strip is a band of
        // nothing: the maintainer's terminal showed an empty black bar taking a tenth of the
        // screen (2026-09-26, "if I'm using a terminal app it's useless"). It keeps its
        // footprint only while a Sym page is open, which is the case above.
        hideWhereNothingToSuggest && !fieldOffersSuggestions -> StripFootprint.COLLAPSED
        isShownForApp(mode, apps, packageName) -> StripFootprint.SHOWN
        else -> StripFootprint.COLLAPSED
    }

    /**
     * spec SS3.5 and T6: reading the mode "when `status_bar_visibility` is absent falls back to
     * the older boolean `show_status_bar`: true or absent means `ALWAYS`, an explicit false means
     * `NEVER`". An unreadable stored mode also lands on `ALWAYS`, because "silence about it is
     * not a request to hide it". SS19 drops the legacy key from storage; the rule is kept for the
     * settings importer, which still meets a 2.x backup.
     */
    fun resolveMode(storedMode: String?, legacyShowStatusBar: Boolean?): StripVisibilityMode {
        val parsed = storedMode?.let { stored -> StripVisibilityMode.entries.firstOrNull { it.name == stored } }
        if (parsed != null) return parsed
        return if (legacyShowStatusBar == false) StripVisibilityMode.NEVER else StripVisibilityMode.ALWAYS
    }

    /** spec SS3.5: writing the mode "writes both keys (the boolean is true for anything but `NEVER`)". */
    fun legacyBooleanFor(mode: StripVisibilityMode): Boolean = mode != StripVisibilityMode.NEVER

    /**
     * spec SS3.5: the app list "is seeded the first time it is read with twenty messaging, mail
     * and social packages". SS3.5 names the apps; SS15 says "their package names are in the
     * settings catalog", which lists only the count (SS14, "Seeded status bar apps 20") and T5's
     * two examples.
     *
     * SPEC GAP: the other eighteen package names are not written down anywhere in docs/spec. The
     * ids below are the apps' public store identifiers for the labels SS3.5 lists, in SS3.5's own
     * order; none is taken from any source outside the spec.
     */
    val SEEDED_APPS: Set<String> = setOf(
        "com.google.android.gm",                 // Gmail
        "com.google.android.apps.messaging",     // Google Messages
        "com.android.messaging",                 // AOSP Messaging
        "com.whatsapp",                          // WhatsApp
        "com.whatsapp.w4b",                      // WhatsApp Business
        "com.facebook.orca",                     // Messenger
        "com.facebook.mlite",                    // Messenger Lite
        "com.facebook.katana",                   // Facebook
        "com.instagram.android",                 // Instagram
        "org.telegram.messenger",                // Telegram
        "org.thoughtcrime.securesms",            // Signal
        "com.discord",                           // Discord
        "com.Slack",                             // Slack
        "com.microsoft.teams",                   // Teams
        "com.microsoft.office.outlook",          // Outlook
        "com.snapchat.android",                  // Snapchat
        "com.twitter.android",                   // X
        "com.reddit.frontpage",                  // Reddit
        "com.linkedin.android",                  // LinkedIn
        "com.google.android.apps.dynamite",      // Google Chat
    )
}
