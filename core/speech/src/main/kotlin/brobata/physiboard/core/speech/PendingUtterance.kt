package brobata.physiboard.core.speech

/**
 * What this module remembers of the words spoken so far in one utterance, for the moment a final
 * arrives with no text of its own or a quiet error ends the request while a partial is still on
 * screen (spec: dictation.md SS7.3, D3: "Google's system engine delivers the whole utterance as
 * its last partial followed by an empty final"). Only [Live] carries a string; [None] and
 * [Invalidated] do not, on purpose.
 *
 * This is the type that makes the "words the user deleted are not typed back" bug (2.0.7's sibling
 * fix, git c440844) unwriteable. The old code kept one mutable "last partial" string and reused it
 * whenever a result carried no text, with nothing recording whether the field still agreed. If the
 * user edited or deleted those words while the engine was still listening, the remembered string
 * out-lived the thing it described, and a later empty final or quiet error typed it straight back.
 *
 * Here, [DictationEngine.handle] moves the state to [Invalidated] the moment it learns the user
 * changed the field mid-utterance ([DictationEvent.UserEditedComposingText]), and [Invalidated]
 * carries no text field at all: there is no expression anywhere in this module that can pull a
 * string out of it, so a stale insert cannot be written even by mistake. Fresh data always wins: a
 * new non-empty partial or a final that carries its own text moves the state straight to a new
 * [Live] regardless of a prior [Invalidated], since that is the engine's own current answer, not
 * memory of something now gone.
 */
sealed class PendingUtterance {
    /** Nothing has been heard yet in this utterance. */
    object None : PendingUtterance()

    /** The last non-empty partial the engine reported, still trustworthy because nothing has changed the field since. */
    data class Live(val text: String) : PendingUtterance()

    /** The user changed the field since [Live] was last true; whatever text it held is gone for good. */
    object Invalidated : PendingUtterance()
}

/** One utterance's frozen context (spec: [UtteranceContext]) paired with what has been heard of it so far. */
data class UtteranceState(val context: UtteranceContext, val pending: PendingUtterance)
