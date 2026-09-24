package brobata.physiboard.core.text

/**
 * A blanket default for the shipped list of tested messengers. spec: per-app-behavior.md SS3.1
 * ("Messaging preset"). The fifth stored value, `enter_newline_only`, is dropped rather than
 * modelled: section 14's Keep/Drop table calls it "Unreachable in the UI; Ctrl-send with newline
 * covers the need", and with no settings/persistence layer in this build (this task's own
 * instruction) there is nothing that would ever produce it.
 */
enum class MessagingPreset { APP_DEFAULT, SEND_SHIFT_NEWLINE, NEWLINE_CTRL_SEND, CUSTOM }

/** spec: per-app-behavior.md SS3.1 ("Extra send shortcut"), SS3.8. See [EnterOverride.extraSendShortcut] for why this is stored but not delivered yet. */
enum class ExtraSendShortcut { NONE, SYM_ENTER }

/**
 * One row of the user's Enter override list (`app_enter_behavior_overrides`). spec: per-app-
 * behavior.md SS3.1 ("Override"), SS3.12 (storage shape). [EnterOverrideResolver] turns a list of
 * these plus a [MessagingPreset] into what one app actually gets.
 */
data class EnterOverride(
    val packageName: String,
    val behavior: EnterBehavior = EnterBehavior.APP_DEFAULT,
    val sendMethod: EnterSendMethod = EnterSendMethod.AUTO,
    /**
     * spec: SS3.1, SS3.8 (Sym+Enter as an extra send). Section 14's Keep/Drop table marks this
     * "undecided" for 3.0 ("Cheap, but it silently loses to a QuickLauncher trigger; keep only if
     * the conflict is surfaced"), and its mechanism needs the Sym-chord session
     * (`layers-sym-alt.md`, owned by `:core:keys`) that this Enter-only decision has no access to.
     * [EnterOverrideResolver.resolveExtraShortcut] still resolves the stored value (spec SS3.13's
     * T16/T17) so a future module can act on it; [EnterDecision] never reads it.
     */
    val extraSendShortcut: ExtraSendShortcut = ExtraSendShortcut.NONE,
)

/**
 * The shipped app lists SS3.2 names that this module still needs. The "favourites" and "tested"
 * lists are dropped: section 14 says they "can collapse to one list with a per-app 'verified'
 * flag", and both only ever affected the settings screen's status-card wording (SS3.11), which
 * this task explicitly does not build.
 */
object EnterPresetPackages {
    const val DISCORD: String = "com.discord"

    /** "Preset packages (9)": the only apps the messaging preset may apply to without an override. spec: SS3.2. */
    val PRESET: Set<String> = setOf(
        "com.whatsapp",
        "org.telegram.messenger",
        "org.thoughtcrime.securesms",
        DISCORD,
        "im.vector.app",
        "com.google.android.apps.messaging",
        "ch.threema.app",
        "ch.threema.app.libre",
        "com.instagram.android",
    )

    /**
     * "Send-action packages (8)": the preset packages minus Discord. spec: SS3.2, "Discord ... is
     * excluded ... because its compose box ignores the editor action (it only hides the
     * keyboard); Discord already sends on Enter and inserts a newline on Shift+Enter by itself."
     */
    val SEND_ACTION: Set<String> = PRESET - DISCORD
}

/**
 * Resolves the user's Enter override list and the messaging preset into what one app gets. spec:
 * per-app-behavior.md SS3.3.
 *
 * Every function here looks [overrides] up for an exact match first, unconditionally, before it
 * ever consults [preset] or the shipped package lists. That order is not an implementation detail:
 * SS3.3's history note records that 2.x ran the "is this a preset package" test *before* the
 * override lookup until 2.0.3, so a user's override on an untested package (WhatsApp Business, a
 * Signal fork, Slack) was stored, shown on its own settings row, and silently ignored, and the
 * send method was stored and never read at all (commit 8b03e80). Checking the override first is
 * what keeps that bug from being reintroduced by this rewrite; this task's own instruction to fix
 * "the ordering bug the spec records" names exactly this.
 *
 * Kept apart from [AppProfileResolver] (which answers "which single profile applies") because
 * "editor action allowed" needs a fact that resolver's one-profile answer would erase: whether a
 * row for this package exists *at all*, regardless of what it says (SS3.3, "true ... when any
 * override row exists for P, regardless of its content, even `app_default`"). A profile already
 * collapsed to [AppProfile.default] cannot tell "no row" apart from "a row equal to the default".
 */
object EnterOverrideResolver {

    /**
     * "Wanted behavior for package P". [EnterBehavior.APP_DEFAULT] is the resolver's spelling of
     * "nothing applies" (SS3.3 rules 2 and 3), matching [EnterBehavior.APP_DEFAULT]'s own KDoc.
     * Both [packageName] being blank and the master switch being off answer [EnterBehavior.APP_DEFAULT]
     * (SS3.3, "All three lookups return 'nothing' when the master switch ... is off or there is no
     * current package"; SS2.1, "a null package ... matches nothing").
     */
    fun resolveBehavior(
        packageName: String?,
        overrides: List<EnterOverride>,
        preset: MessagingPreset,
        masterSwitchEnabled: Boolean,
    ): EnterBehavior {
        if (!masterSwitchEnabled || packageName.isNullOrEmpty()) return EnterBehavior.APP_DEFAULT
        val override = overrides.firstOrNull { it.packageName == packageName }
        // Rule 1: an override that is not itself "app default" wins outright, for any package
        // whatsoever (SS3.3: "This applies to any package whatsoever; the user naming an app is
        // the authority"). An `app_default` row deliberately falls through to the preset check
        // below (SS3.3, "An override row with behavior app_default therefore falls through to the
        // preset"; edge case E4).
        if (override != null && override.behavior != EnterBehavior.APP_DEFAULT) return override.behavior
        // Rule 2: only the 9 preset packages ever get a preset-driven behavior.
        if (packageName !in EnterPresetPackages.PRESET) return EnterBehavior.APP_DEFAULT
        // Rule 3: the preset decides, with Discord excluded from the send-on-Enter preset only.
        return when (preset) {
            MessagingPreset.SEND_SHIFT_NEWLINE ->
                if (packageName == EnterPresetPackages.DISCORD) EnterBehavior.APP_DEFAULT else EnterBehavior.SEND_SHIFT_NEWLINE
            MessagingPreset.NEWLINE_CTRL_SEND -> EnterBehavior.NEWLINE_CTRL_SEND
            MessagingPreset.APP_DEFAULT, MessagingPreset.CUSTOM -> EnterBehavior.APP_DEFAULT
        }
    }

    /** "Send method for P": the override row's value, else `auto`. spec: SS3.3. Never preset-driven (only [resolveBehavior] is). */
    fun resolveSendMethod(packageName: String?, overrides: List<EnterOverride>, masterSwitchEnabled: Boolean): EnterSendMethod {
        if (!masterSwitchEnabled || packageName.isNullOrEmpty()) return EnterSendMethod.AUTO
        return overrides.firstOrNull { it.packageName == packageName }?.sendMethod ?: EnterSendMethod.AUTO
    }

    /** "Extra send shortcut for P": the override row's value, else `none`. spec: SS3.3. Resolved for completeness (SS3.13 T16/T17); see [EnterOverride.extraSendShortcut] for why nothing delivers it yet. */
    fun resolveExtraShortcut(packageName: String?, overrides: List<EnterOverride>, masterSwitchEnabled: Boolean): ExtraSendShortcut {
        if (!masterSwitchEnabled || packageName.isNullOrEmpty()) return ExtraSendShortcut.NONE
        return overrides.firstOrNull { it.packageName == packageName }?.extraSendShortcut ?: ExtraSendShortcut.NONE
    }

    /**
     * "Editor action allowed for P": true when [packageName] is one of the 8 send-action packages,
     * or when any override row exists for P at all. spec: SS3.3. Edge case E14 reads this lookup as
     * gated by the master switch exactly like the other three ("Eligibility checks rows via the
     * same switch, so it too is off"); this is unreachable in practice with the switch off, since
     * [resolveBehavior] then never returns anything but [EnterBehavior.APP_DEFAULT], and step e
     * (SS3.5) never consults this value.
     */
    fun isEditorActionAllowed(packageName: String?, overrides: List<EnterOverride>, masterSwitchEnabled: Boolean): Boolean {
        if (!masterSwitchEnabled || packageName.isNullOrEmpty()) return false
        if (packageName in EnterPresetPackages.SEND_ACTION) return true
        return overrides.any { it.packageName == packageName }
    }
}

/**
 * What Enter should do, described as data for `:ime` to perform. spec: per-app-behavior.md SS3.4
 * ("The four delivery mechanisms") plus the two cases ([Decline], [InsertNewline]) `:core:text`
 * settles itself. Nothing here is ever performed by this module: rebuild-from-scratch.md "The
 * editor is not a reliable narrator" point 4 and this task's own split ("the decision belongs in
 * :core:text ... performing it belongs in :ime") keep every real `InputConnection` call out of
 * this sealed class's reach, so every branch of [EnterDecision] is provable on the JVM with no
 * editor at all.
 */
sealed class EnterIntent {

    /**
     * spec: SS3.5 step 4e (no wanted behaviour, nav mode owns Enter, or the field declares
     * nothing) and step 5. `:core:text` still answers this Enter itself, through the existing
     * generic newline / autocorrect-boundary path (text-input.md SS7); there is nothing left for
     * `:ime` to perform.
     */
    object Decline : EnterIntent()

    /**
     * spec: SS3.4 "Newline": finish composing, commit "\n", run the after-Enter auto-cap check,
     * reset the suggestion context. `:core:text` performs this directly; it is never handed to
     * `:ime` as a delivery either.
     */
    object InsertNewline : EnterIntent()

    /**
     * spec: SS3.4 "Editor action". [actionId] is the field's own action id when SS3.9 resolves
     * one, else Send (4), already picked by [EnterDecision]. [clearCtrlIfDelivered]: SS3.4, "when
     * the send was triggered by Ctrl, clear the Ctrl state (latch, one-shot, nav-mode latch ...)
     * ... Handled if and only if the request was delivered." The hard-won fact this whole feature
     * is built around lives here: `:ime`'s `performEditorAction` call reports only whether the
     * `InputConnection` was alive, never whether the app actually acted on the request, so a
     * swallowed Enter looks exactly like success (SS3.13, edge case E2).
     */
    data class RequestEditorAction(val actionId: Int, val clearCtrlIfDelivered: Boolean) : EnterIntent()

    /**
     * spec: SS3.4 "Plain Enter": Enter key down then key up, keycode 66, meta state 0, "the
     * delivery that is known to work in apps that ignore the [editor] action" (SS3.13). Unlike
     * [RequestEditorAction], the Ctrl state is cleared once both events are delivered whether or
     * not this particular send was Ctrl-triggered (SS3.4, "always, not only for Ctrl-triggered
     * sends").
     */
    object SendPlainEnter : EnterIntent()

    /** spec: SS3.4 "Ctrl+Enter": identical to [SendPlainEnter] but both key events carry META_CTRL_ON | META_CTRL_LEFT_ON. */
    object SendCtrlEnter : EnterIntent()

    /**
     * spec: SS3.4 "Unsupported send": the send method resolves to an editor action but the app is
     * not allowed one. The key is swallowed (nothing inserted, nothing sent) and reported as
     * handled unconditionally; [clearCtrlNow] mirrors "if any Ctrl state was active it is cleared
     * as above", which does not wait on any delivery outcome since nothing is sent.
     */
    data class Swallow(val clearCtrlNow: Boolean) : EnterIntent()
}

/**
 * Decides what one Enter key-down should do, given the app's already-resolved [AppProfile], the
 * field it landed in, and which modifiers are active. spec: per-app-behavior.md SS3.4-SS3.10.
 *
 * Every input is already-resolved data (the profile, the field's declared action, plain booleans
 * for the modifiers), so this stays a pure function of its arguments: exactly the "described
 * intent, not action performed" shape this task calls for, and provable on the JVM with no editor
 * or `:core:keys` state machine in reach.
 *
 * Deliberately out of scope, both per this task's instructions and per section 14's own Keep/Drop
 * verdict: Sym+Enter as an extra send (SS3.5 step 4a, SS3.8; "undecided" in section 14, and its
 * mechanism needs the Sym-chord session `:core:keys` owns, not this module). A caller that wants
 * it can intercept before this decision runs, the same way SS3.8 describes Sym+Enter being
 * checked ahead of the ordinary Enter handler.
 */
object EnterDecision {

    fun decide(
        profile: AppProfile,
        /**
         * The field the Enter landed in. Only [FieldContext.imeAction] affects this decision:
         * spec SS3.9 is explicit that "There is no separate multi-line rule... gets the action on
         * Enter exactly like a single-line one", so [FieldContext.isMultiLine] is accepted (this
         * task calls for "the field: single or multi line, and what editor action it advertises"
         * as an input) but never changes the answer.
         */
        field: FieldContext,
        /** spec: SS3.5 step 4, "Ctrl active": event Ctrl meta, held, latched, one-shot, or latched by nav mode; the caller (`:ime`, which owns `:core:keys`' [brobata.physiboard.core.keys.ModifierState]) has already folded all of that into one boolean. */
        ctrlActive: Boolean,
        /** spec: SS3.5 step 4, "Shift active": event Shift meta, or the Shift layer latched (a one-shot is already consumed before this step runs, SS3.5 step 3, so it never reaches here). */
        shiftActive: Boolean,
        /**
         * spec: SS3.5 step 4e, SS3.7 (`enter_newline`'s own nav-mode row), SS3.10. Nav mode
         * (`trackpad-caret-nav.md`) has no owning module yet (rebuild-from-scratch.md build order
         * step 4), so every existing caller leaves this false, which is exactly "nav mode is not
         * active" and keeps today's behaviour unchanged.
         */
        navModeActive: Boolean = false,
    ): EnterIntent = when (profile.enterBehavior) {
        EnterBehavior.APP_DEFAULT -> declineOrStepE(navModeActive, field.imeAction)

        // spec SS3.7 "enter_newline" table: every combination outside nav mode is a newline; in
        // nav mode with Ctrl active (the nav-mode latch counts as Ctrl active, SS3.10) it sends.
        EnterBehavior.NEWLINE ->
            if (navModeActive && ctrlActive) send(profile, field.imeAction, ctrlTriggered = true) else EnterIntent.InsertNewline

        // spec SS3.7 "enter_newline_ctrl_send": Ctrl active (nav-mode latch included) sends; else newline.
        EnterBehavior.NEWLINE_CTRL_SEND ->
            if (ctrlActive) send(profile, field.imeAction, ctrlTriggered = true) else EnterIntent.InsertNewline

        // spec SS3.7 "enter_send_shift_newline": Ctrl active sends; else Shift active is a newline; else send.
        EnterBehavior.SEND_SHIFT_NEWLINE -> when {
            ctrlActive -> send(profile, field.imeAction, ctrlTriggered = true)
            shiftActive -> EnterIntent.InsertNewline
            else -> send(profile, field.imeAction, ctrlTriggered = false)
        }
    }

    /** spec: SS3.5 step 4e: nav mode declines first; otherwise the field's own declared action (SS3.9), with no fallback to Send and no Ctrl consumption; otherwise decline to the generic path (step 5). */
    private fun declineOrStepE(navModeActive: Boolean, fieldAction: ImeAction): EnterIntent {
        if (navModeActive) return EnterIntent.Decline
        val id = declaredActionId(fieldAction) ?: return EnterIntent.Decline
        return EnterIntent.RequestEditorAction(id, clearCtrlIfDelivered = false)
    }

    /** spec: SS3.6, "Choosing the send mechanism". */
    private fun send(profile: AppProfile, fieldAction: ImeAction, ctrlTriggered: Boolean): EnterIntent = when (profile.enterSendMethod) {
        EnterSendMethod.PLAIN_ENTER -> EnterIntent.SendPlainEnter
        EnterSendMethod.CTRL_ENTER -> EnterIntent.SendCtrlEnter
        EnterSendMethod.EDITOR_ACTION -> viaEditorActionOrSwallow(profile.enterActionAllowed, fieldAction, ctrlTriggered)
        EnterSendMethod.AUTO ->
            if (profile.packageName == EnterPresetPackages.DISCORD) {
                EnterIntent.SendPlainEnter
            } else {
                viaEditorActionOrSwallow(profile.enterActionAllowed, fieldAction, ctrlTriggered)
            }
    }

    private fun viaEditorActionOrSwallow(allowed: Boolean, fieldAction: ImeAction, ctrlTriggered: Boolean): EnterIntent =
        if (allowed) {
            EnterIntent.RequestEditorAction(actionIdForSend(fieldAction), clearCtrlIfDelivered = ctrlTriggered)
        } else {
            EnterIntent.Swallow(clearCtrlNow = ctrlTriggered)
        }

    /**
     * spec: SS3.9: only ids 2-7 (Go, Search, Send, Next, Done, Previous) count as the field
     * declaring an action; "Unspecified and None give 'none'." [ImeAction.CUSTOM] (a field with a
     * custom action label, text-input.md's own broader vocabulary) is not one of SS3.9's six named
     * actions, so it is treated the same as declaring none for per-app Enter purposes. Per-app-
     * behavior.md never says what a custom-labelled action should do here, and text-input.md's own
     * use of [ImeAction.CUSTOM] is for a different question (whether the autocorrect boundary
     * engine should run at all, text-input.md SS7); SPEC GAP: absent a worked example combining a
     * custom action label with a per-app Enter override, "not one of the six" is the closest
     * reading of SS3.9's own words.
     */
    private fun declaredActionId(action: ImeAction): Int? = when (action) {
        ImeAction.GO -> 2
        ImeAction.SEARCH -> 3
        ImeAction.SEND -> 4
        ImeAction.NEXT -> 5
        ImeAction.DONE -> 6
        ImeAction.PREVIOUS -> 7
        ImeAction.NONE, ImeAction.CUSTOM -> null
    }

    /** spec: SS3.4, "The action id is the field's own action (section 3.9) if it declares one, else Send (id 4)." Only the explicit send mechanisms fall back this way; [declineOrStepE] never does. */
    private fun actionIdForSend(action: ImeAction): Int = declaredActionId(action) ?: 4
}
