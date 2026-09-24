package brobata.physiboard.core.speech

/**
 * The text immediately before one utterance began, captured once when the utterance opens and
 * carried unchanged for that utterance's whole life. spec: dictation.md SS7.5, SS7.6;
 * rebuild-from-scratch.md "The editor is not a reliable narrator" point 1: "Never depend on
 * reading back what you just wrote."
 *
 * This is the type that makes the 2.0.7 bug unwriteable rather than merely fixed. In 2.x, spacing
 * and capitalisation re-read the field on every partial and every final, so once the keyboard's
 * own dictated words were sitting in the field, later decisions were made against text this same
 * session had written, not against what the user actually had there (spec SS7.1: "the capital
 * survives only because each new partial is checked against the text that now precedes it, the
 * previous partial's own words"; SS7.6's leading-space quirk has the identical cause). No function
 * in this module accepts a fresh "current text" alongside an in-progress utterance: the ONLY place
 * a live editor read is ever consumed is [DictationEngine] turning a session-start or new-utterance
 * boundary into one of these, and from then on every capitalisation and spacing decision for that
 * utterance is computed from this frozen snapshot, or from text this module itself already
 * produced (see [DictationSession] carrying [UtteranceContext] forward by simple append, never by
 * asking the caller to read anything again).
 */
data class UtteranceContext(val textBeforeUtterance: String?)
