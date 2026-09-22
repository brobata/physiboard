package brobata.physiboard.core.text

/**
 * How far a read of the field can be trusted this keystroke. spec: rebuild-from-scratch.md "The
 * editor is not a reliable narrator", the failure table ("will not answer" / "answers with stale
 * text" / "mishandles what is written") and point 2 ("trust is a value the pipeline is given, not
 * an assumption it makes").
 */
enum class EditorReadTrust {
    /** The field answers truthfully and promptly: every read reflects what the document actually holds right now. */
    RELIABLE,

    /**
     * The field may answer with text that no longer matches the document (an asynchronous Compose
     * or Flutter field applying edits on its own schedule, or a proxy connection such as a WebAPK's
     * browser host). A read is still worth taking as a drift check ([DriftCheck]), but no rule may
     * treat it as ground truth for a decision.
     */
    POSSIBLY_STALE,

    /** The field refuses reads outright (a null answer, or a throw the caller has already turned into one). */
    UNAVAILABLE,
}

/**
 * Whether marking a composing region is safe on this field. spec: rebuild-from-scratch.md "The
 * editor is not a reliable narrator" point 3, "A composing region is a privilege, not a default."
 */
enum class ComposingSafety {
    /** The field finishes and replaces a composing region the way the platform contract promises. */
    SAFE,

    /** The field is known to mishandle a composing region (duplicates or loses text); nothing may set one. */
    UNSAFE,
}

/**
 * How far this pipeline may trust the editor it is attached to for one field, supplied by the
 * caller alongside [FieldContext] rather than assumed from the field's own classification (spec:
 * rebuild-from-scratch.md "The editor is not a reliable narrator" point 2). [reads] and
 * [composing] are independent: the plan's own failure table lists "will not answer", "answers
 * with stale text" and "mishandles what is written" as three separate ways a field misbehaves, so
 * a field can, for instance, answer reads reliably while still mishandling a composing region (a
 * web view that echoes `getTextBeforeCursor` correctly but drops `setComposingRegion`).
 */
data class EditorTrust(
    val reads: EditorReadTrust = EditorReadTrust.RELIABLE,
    val composing: ComposingSafety = ComposingSafety.SAFE,
) {
    /**
     * Whether a rule that needs surrounding context to make its decision (capitalisation from a
     * sentence end, boundary correction, the spacing rules that inspect what precedes) may run
     * this keystroke. False for anything less than [EditorReadTrust.RELIABLE]: these rules do not
     * run on a guess, they simply do not run, and the caller still types the plain character.
     */
    val contextRulesAllowed: Boolean get() = reads == EditorReadTrust.RELIABLE

    /** Whether a composing region may be set at all this field. spec: same section, point 3. */
    val composingRegionAllowed: Boolean get() = composing == ComposingSafety.SAFE

    companion object {
        /** A well-behaved field: every existing caller that does not supply a value gets exactly today's behaviour. */
        val FULL: EditorTrust = EditorTrust()
    }
}
