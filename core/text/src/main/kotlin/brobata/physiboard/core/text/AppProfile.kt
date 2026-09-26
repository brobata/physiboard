package brobata.physiboard.core.text

/**
 * The "wanted behaviour" Enter resolves to for one app, collapsed to what this module's Enter
 * path (text-input.md SS7) needs to know: whether Enter should insert a newline or hand off to a
 * send/editor-action mechanism a per-app Enter module owns. spec: per-app-behavior.md SS3.1.
 */
enum class EnterBehavior {
    /** No per-app opinion: the field's own editor action or the generic newline path decides. spec: SS3.3, SS3.5 step e. */
    APP_DEFAULT,

    /** Enter, Shift+Enter and Ctrl+Enter all insert a newline; only reachable through a stored override. spec: SS3.7. */
    NEWLINE,

    /** Enter sends, Shift+Enter inserts a newline. spec: SS3.1, SS3.7. */
    SEND_SHIFT_NEWLINE,

    /** Enter inserts a newline, Ctrl+Enter sends. spec: SS3.1, SS3.7. */
    NEWLINE_CTRL_SEND,
}

/**
 * How a "send" chosen by [EnterBehavior] is delivered to the app. spec: per-app-behavior.md SS3.1
 * ("Send method"), SS3.6 ("Choosing the send mechanism"). [EnterDecision] is what turns one of
 * these into an [EnterIntent]; this type is only the stored choice.
 */
enum class EnterSendMethod {
    /** Discord: [EnterIntent.SendPlainEnter]. Every other app: the editor action if allowed, else [EnterIntent.Swallow]. spec: SS3.6. */
    AUTO,

    /** Request the field's own editor action, or Send (id 4) as the fallback, when allowed; otherwise [EnterIntent.Swallow]. spec: SS3.4, SS3.6. */
    EDITOR_ACTION,

    /** Always [EnterIntent.SendCtrlEnter]. spec: SS3.6. */
    CTRL_ENTER,

    /** Always [EnterIntent.SendPlainEnter]. spec: SS3.6. */
    PLAIN_ENTER,
}

/**
 * What per-app-behavior.md says varies per app, resolved once (spec: rebuild-from-scratch.md "The
 * editor is not a reliable narrator" point 4, "The app is an input to the pipeline, not a lookup
 * inside it") and handed to the pipeline like any other setting rather than looked up from inside
 * it. [packageName] is the identity this profile was filed under, which for a WebAPK is its own
 * shell package, never the host browser's (spec: per-app-behavior.md SS4.5, "the WebAPK shell name
 * for a web app, never the host"); see [AppProfileResolver] for how a reported package is matched
 * back to a profile filed this way.
 */
data class AppProfile(
    val packageName: String,
    /**
     * spec: per-app-behavior.md SS3 ("Enter key behavior"). Already resolved for [packageName]
     * from the user's override list and the messaging preset (SS3.3) by [EnterOverrideResolver]
     * before this profile is built; [EnterDecision] (the thing that actually acts on this field)
     * never re-derives it, so the ordering rule SS3.3 depends on (override checked before any
     * preset list) lives in exactly one place.
     */
    val enterBehavior: EnterBehavior = EnterBehavior.APP_DEFAULT,
    /** spec: per-app-behavior.md SS3.3 ("Send method for P"); resolved the same way as [enterBehavior]. */
    val enterSendMethod: EnterSendMethod = EnterSendMethod.AUTO,
    /**
     * spec: per-app-behavior.md SS3.1, SS3.8 ("Sym+Enter as an extra send"), SS3.5 step 4a.
     * Resolved the same way as [enterBehavior] ([EnterOverrideResolver.resolveExtraShortcut]);
     * `:ime`'s Sym-chord session checks this ahead of [enterBehavior]'s own wanted-behaviour steps.
     */
    val extraSendShortcut: ExtraSendShortcut = ExtraSendShortcut.NONE,
    /**
     * spec: per-app-behavior.md SS3.3 ("Editor action allowed for P"): true when [packageName] is
     * one of the 8 send-action packages, or when the user has any override row for it at all
     * (regardless of that row's own content). [EnterOverrideResolver.isEditorActionAllowed]
     * computes this; it is carried here, not recomputed, for the same reason [enterBehavior] is.
     */
    val enterActionAllowed: Boolean = false,
    /**
     * "Exact typing", the raw mode [FieldKind.RAW_MODE_APP] names. spec: per-app-behavior.md SS4.1:
     * every field with no field-type restriction of its own turns off suggestions, autocorrect,
     * auto-cap, double-space period and text expansion.
     */
    val exactTypingEnabled: Boolean = false,
    /**
     * SPEC GAP: per-app-behavior.md never enumerates which apps need [FieldContext.isEditableButNotReallyEditable]
     * (text-input.md SS3, "some custom views" that report an unclassified field but are still
     * editable); no `EditorInfo` bit distinguishes that case from a genuinely non-editable field,
     * since `TYPE_NULL` is the only value the input-type class mask leaves once TEXT, NUMBER, PHONE
     * and DATETIME are accounted for. Until the spec names the apps, this is a profile flag the
     * caller sets rather than a heuristic this module invents.
     */
    val unclassifiedFieldsAreEditable: Boolean = false,
    /** spec: rebuild-from-scratch.md "The editor is not a reliable narrator" points 1-3. */
    val editorTrust: EditorTrust = EditorTrust.FULL,
) {
    companion object {
        /** No profile matched: full trust, no overrides. spec: per-app-behavior.md SS2.1, "a null package ... matches nothing". */
        fun default(packageName: String?): AppProfile = AppProfile(packageName = packageName.orEmpty())
    }
}

/**
 * The pure resolution rule for "which profile applies to the app the field just reported", kept
 * separate from [AppProfile] itself so it can be driven by the shape of the caller's own lists
 * without this module ever reading one (spec: rebuild-from-scratch.md build order step 6, no
 * `:settings` module yet; "Do NOT build a settings store", this task's own instruction).
 */
object AppProfileResolver {

    /**
     * [reportedPackage] is what the field's editor info gave: the host browser's package for a
     * WebAPK, never the shell's (spec: per-app-behavior.md SS2.2). [profiles] is keyed by the
     * identity the user configured the app under, which for a WebAPK is its own shell package
     * (SS4.5); an entry is matched by that identity first, and only falls back to the WebAPK-host
     * expansion (SS2.2, "Expanding a set of packages means adding the host browser of every WebAPK
     * in it") when nothing matches directly, so a profile filed under the reported package itself
     * always wins over one that merely expands to it. [webApkHost] resolves a WebAPK shell package
     * to its host browser (SS2.2); reading installed-package manifest metadata is `:ime`'s job, not
     * this pure module's, so it is supplied as data rather than looked up here. spec: per-app-
     * behavior.md SS2.1, "A null package ... matches nothing": a null or blank [reportedPackage]
     * always resolves to [AppProfile.default].
     */
    fun resolve(
        reportedPackage: String?,
        profiles: List<AppProfile>,
        webApkHost: (String) -> String? = { null },
    ): AppProfile {
        if (reportedPackage.isNullOrEmpty()) return AppProfile.default(reportedPackage)
        profiles.firstOrNull { it.packageName == reportedPackage }?.let { return it }
        profiles.firstOrNull { webApkHost(it.packageName) == reportedPackage }?.let { return it }
        return AppProfile.default(reportedPackage)
    }
}
