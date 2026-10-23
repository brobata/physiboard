package brobata.physiboard.core.speech

/**
 * What one partial-results bundle from the engine means. spec: dictation.md SS5, SS6.2, D28.
 *
 * Google's continuous session does not only report provisional text through partials: it
 * finalizes an utterance through a partial flagged `final_result`, the shape Chromium's
 * `SpeechRecognitionImpl` (the one public continuous client Google maintains) reads, and it
 * splits a provisional hypothesis into the stable text and an unstable tail that concatenate to
 * the whole. Treating the flagged partial as provisional is how an utterance went missing: the
 * next utterance's first partial replaced its composing text (D24 on the Titan: three finals
 * created, two delivered through `onResults`, and SS7.3's word-overlap rescue needs three words
 * none of which the next utterance shares). Decided here, with no Android import, so it can be
 * tested; `:ime` only reads the bundle's fields and hands them in.
 */
object RecognizerPartial {
    /** The event for a partial bundle, or `null` for one carrying no words (Chrome ignores those too: some bundles carry only metadata). */
    fun toEvent(stableText: String?, unstableTail: String?, flaggedFinal: Boolean): DictationEvent? {
        val stable = stableText ?: return null
        if (flaggedFinal) return if (stable.isBlank()) null else DictationEvent.SegmentResult(stable)
        val whole = stable + (unstableTail ?: "")
        return if (whole.isBlank()) null else DictationEvent.PartialResult(whole)
    }
}
