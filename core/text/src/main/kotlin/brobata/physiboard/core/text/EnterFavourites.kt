package brobata.physiboard.core.text

/**
 * spec: per-app-behavior.md SS3.2, "Favourites (10)": surfaced at the top of the override list in
 * this fixed order ("WhatsApp, Telegram, Signal, Discord, Element, Google Messages, Threema,
 * Threema Libre, Instagram, Messenger"). [tested] marks the "Tested packages (7)" subset SS3.2
 * also names; it changes only a favourite's status-card wording (SS3.11), never how Enter itself
 * resolves, which is why [EnterOverrideResolver] and [EnterPresetPackages] never need it.
 */
enum class FavouriteApp(val packageName: String, val tested: Boolean) {
    WHATSAPP("com.whatsapp", tested = true),
    TELEGRAM("org.telegram.messenger", tested = true),
    SIGNAL("org.thoughtcrime.securesms", tested = false),
    DISCORD(EnterPresetPackages.DISCORD, tested = false),
    ELEMENT("im.vector.app", tested = true),
    GOOGLE_MESSAGES("com.google.android.apps.messaging", tested = true),
    THREEMA("ch.threema.app", tested = true),
    THREEMA_LIBRE("ch.threema.app.libre", tested = true),
    INSTAGRAM("com.instagram.android", tested = true),
    MESSENGER("com.facebook.orca", tested = false);

    companion object {
        /** spec SS3.2's favourite order, exactly as listed. */
        val ORDERED: List<FavouriteApp> = entries.toList()
        val PACKAGE_NAMES: Set<String> = ORDERED.map { it.packageName }.toSet()
        fun of(packageName: String): FavouriteApp? = entries.firstOrNull { it.packageName == packageName }
    }
}

/** spec SS3.11's status block: what a favourite's read-only card shows before "Manual override" is revealed. */
data class FavouriteStatus(val badge: String, val line1: String, val line2: String)

/**
 * spec: per-app-behavior.md SS3.11's status-block table, evaluated in the priority order the spec
 * itself lists (Discord's two special rows first). [behavior] is the already-resolved wanted
 * behaviour ([EnterOverrideResolver.resolveBehavior]'s answer for [app]'s package), not the raw
 * override row: SS3.11's own note explains the Discord badge describes "the visible result", which
 * is the resolved behaviour whether or not an override row exists (edge case E13).
 */
object FavouriteStatusCard {

    fun forRow(app: FavouriteApp, behavior: EnterBehavior): FavouriteStatus = when {
        app == FavouriteApp.DISCORD && behavior == EnterBehavior.SEND_SHIFT_NEWLINE -> FavouriteStatus(
            badge = "No override",
            line1 = "App default: PhysiBoard does not intervene.",
            line2 = "Tested: Discord already behaves correctly for Enter sends and Shift+Enter newline. " +
                "PhysiBoard does not intervene here.",
        )

        app == FavouriteApp.DISCORD && behavior != EnterBehavior.APP_DEFAULT -> FavouriteStatus(
            badge = "Plain Enter",
            line1 = "Active: Enter newline, Ctrl+Enter sends.",
            line2 = "Tested: Newlines are inserted directly. Ctrl+Enter sends a plain Enter without Ctrl " +
                "meta because Discord's app action only hides the keyboard.",
        )

        behavior == EnterBehavior.APP_DEFAULT -> FavouriteStatus(
            badge = "No override",
            line1 = "App default: PhysiBoard does not intervene.",
            line2 = "The app decides whether Enter sends or inserts a newline.",
        )

        app.tested -> FavouriteStatus(
            badge = "App action",
            line1 = "Active: ${behaviorLabel(behavior)}.",
            line2 = testedLine2(behavior),
        )

        else -> FavouriteStatus(
            badge = "App action",
            line1 = "Experimentally active: ${behaviorLabel(behavior)}.",
            line2 = "Not confirmed: PhysiBoard is trying the app's send action and direct newline insertion.",
        )
    }

    private fun testedLine2(behavior: EnterBehavior): String = when (behavior) {
        EnterBehavior.SEND_SHIFT_NEWLINE ->
            "PhysiBoard sends through the app's send action. Shift+Enter is inserted directly as a newline."
        EnterBehavior.NEWLINE_CTRL_SEND ->
            "PhysiBoard inserts newlines directly and sends through the app's send action."
        EnterBehavior.NEWLINE, EnterBehavior.APP_DEFAULT ->
            "Tested: PhysiBoard inserts Enter directly as a newline and does not trigger send."
    }

    /** spec SS3.1's user-facing "Wanted behavior" labels. */
    fun behaviorLabel(behavior: EnterBehavior): String = when (behavior) {
        EnterBehavior.APP_DEFAULT -> "App default"
        EnterBehavior.NEWLINE -> "Enter newline"
        EnterBehavior.SEND_SHIFT_NEWLINE -> "Enter sends, Shift+Enter newline"
        EnterBehavior.NEWLINE_CTRL_SEND -> "Enter newline, Ctrl+Enter sends"
    }
}

/**
 * spec: per-app-behavior.md SS3.11 step 3: "Choosing a preset: stores it; then rewrites the
 * override list so that every installed favourite has a row with the preset's behavior (send
 * method `auto`, shortcut `none`), replacing any existing row for a favourite and keeping rows for
 * non-favourites... Choosing 'Custom' stores `custom` and leaves rows alone." [installed] answers
 * whether a favourite package is on the device; reading `PackageManager` is `:app`'s job (only it
 * may touch it), so that fact is supplied as data rather than looked up here. Sorting the result
 * (favourites first in favourite order, then the rest by label) needs app labels this pure module
 * has no access to, so it is left to the settings screen; this only decides which rows exist.
 */
object EnterPresetApplication {

    fun apply(preset: MessagingPreset, currentOverrides: List<EnterOverride>, installed: (String) -> Boolean): List<EnterOverride> {
        // spec SS3.11 step 3: only "Custom" leaves rows alone; "App default" still rewrites every
        // installed favourite's row (to app_default), same as the other two preset values.
        if (preset == MessagingPreset.CUSTOM) return currentOverrides
        val kept = currentOverrides.filterNot { it.packageName in FavouriteApp.PACKAGE_NAMES }
        val favouriteRows = FavouriteApp.ORDERED
            .filter { installed(it.packageName) }
            .map { fav -> EnterOverride(fav.packageName, behaviorFor(preset, fav), EnterSendMethod.AUTO, ExtraSendShortcut.NONE) }
        return kept + favouriteRows
    }

    /** spec SS3.3 rule 3, applied to a favourite the way a fresh preset row would resolve it (Discord excluded from send-on-Enter). */
    private fun behaviorFor(preset: MessagingPreset, app: FavouriteApp): EnterBehavior = when (preset) {
        MessagingPreset.SEND_SHIFT_NEWLINE -> if (app == FavouriteApp.DISCORD) EnterBehavior.APP_DEFAULT else EnterBehavior.SEND_SHIFT_NEWLINE
        MessagingPreset.NEWLINE_CTRL_SEND -> EnterBehavior.NEWLINE_CTRL_SEND
        MessagingPreset.APP_DEFAULT, MessagingPreset.CUSTOM -> EnterBehavior.APP_DEFAULT
    }
}
