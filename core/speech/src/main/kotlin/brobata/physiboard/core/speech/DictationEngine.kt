package brobata.physiboard.core.speech

/**
 * The whole dictation session as a state machine: `(state, event) -> (state, effects, text ops)`,
 * with no Android import anywhere in this module. spec: dictation.md, whole document; the specific
 * section is cited on each private handler below.
 *
 * [handle] is the single entry point. Every other function in this file is a private step of it,
 * kept separate only so each one can cite the one spec rule it encodes; none of them is meant to be
 * called on its own; the [FirstWordsGrace], [DictationModeDecision], [DictationErrorClassifier],
 * [UtteranceFinisher] and [LanguageTagResolver] objects are the pieces of this decision that are
 * useful standalone and are tested directly.
 */
object DictationEngine {

    fun handle(
        state: DictationSession?,
        event: DictationEvent,
        now: Long,
        settings: DictationSettings,
        textSettings: DictationTextSettings,
        segmentedRefusalLatch: Boolean,
    ): DictationOutcome = when (event) {
        is DictationEvent.Trigger -> handleTrigger(state, event, now, settings, segmentedRefusalLatch)
        else -> handleWithSession(state, event, now, settings, textSettings, segmentedRefusalLatch)
    }

    /** spec SS6.6 rule 7: "session already ended ... ignore." Every event but [DictationEvent.Trigger] needs a live session to act on. */
    private fun handleWithSession(
        state: DictationSession?,
        event: DictationEvent,
        now: Long,
        settings: DictationSettings,
        textSettings: DictationTextSettings,
        segmentedRefusalLatch: Boolean,
    ): DictationOutcome {
        val session = state ?: return DictationOutcome(null)
        return when (event) {
            is DictationEvent.Trigger -> throw IllegalStateException("Trigger is handled before a session is required")
            DictationEvent.ReadyForSpeech -> handleReady(session)
            DictationEvent.BeginningOfSpeech -> handleBeginningOfSpeech(session)
            is DictationEvent.PartialResult -> handlePartialResult(session, event.text, textSettings)
            is DictationEvent.FinalResult -> handleFinalResult(session, event.text, now, settings, textSettings)
            is DictationEvent.SegmentResult -> handleSegmentResult(session, event.text, now, settings, textSettings)
            DictationEvent.SegmentedSessionEnded -> DictationOutcome(null, endEffects(session, cancelRecognizer = false), clearComposingOps(session.utterance.pending))
            is DictationEvent.Error -> handleError(session, event.code, now, settings, textSettings)
            DictationEvent.EditorFieldClosed -> DictationOutcome(session.copy(editorGoneDeadlineMs = now + DictationTiming.EDITOR_GONE_GRACE_MS))
            is DictationEvent.EditorFieldOpened -> handleEditorFieldOpened(session, event.ownerPackage)
            DictationEvent.UserEditedComposingText -> DictationOutcome(session.copy(utterance = session.utterance.copy(pending = PendingUtterance.Invalidated)))
            DictationEvent.EditorRejectedInsert -> DictationOutcome(null, endEffects(session, cancelRecognizer = true), clearComposingOps(session.utterance.pending))
            DictationEvent.StartFailed -> DictationOutcome(null, endEffects(session, cancelRecognizer = false), clearComposingOps(session.utterance.pending))
            DictationEvent.ClockTick -> handleClockTick(session, now)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Starting and stopping. spec: dictation.md SS2, SS2.6, SS6.1, SS6.5.
    // ---------------------------------------------------------------------------------------

    /**
     * spec SS2: "if a session is currently marked active ... the trigger stops it; otherwise it
     * starts one." spec SS15 edge case: "Trigger fired twice before the engine reports 'ready' ...
     * restarts the session state and issues another request instead of stopping", which this
     * reaches naturally since a not-yet-[DictationSession.active] session takes the same "start
     * fresh" branch as no session at all.
     */
    private fun handleTrigger(
        state: DictationSession?,
        event: DictationEvent.Trigger,
        now: Long,
        settings: DictationSettings,
        segmentedRefusalLatch: Boolean,
    ): DictationOutcome {
        if (state != null && state.active) return handleExplicitStop(state, now, settings)
        val mode = DictationModeDecision.decide(settings, segmentedRefusalLatch)
        val fresh = DictationSession.fresh(event.ownerPackage, now, mode, event.textBeforeSession)
        return DictationOutcome(fresh, effects = listOf(DictationEffect.StartListening(mode)))
    }

    /**
     * spec SS6.5: cancels the silence timer and asks the engine to stop; SS6.3: the watchdog "is
     * also armed ... on an explicit stop". The watchdog is armed in both modes, because a
     * recognizer that never answers stopListening would otherwise leave the session active for
     * ever, with every later trigger taking this stop branch again. A pending busy retry is
     * dropped too: the user asked for the microphone to close, so nothing may reopen it.
     */
    private fun handleExplicitStop(session: DictationSession, now: Long, settings: DictationSettings): DictationOutcome {
        val next = session.copy(
            stopRequested = true,
            silenceDeadlineMs = null,
            busyRetryDeadlineMs = null,
            watchdogDeadlineMs = now + DictationTiming.watchdogMs(settings.pauseMs),
        )
        return DictationOutcome(next, effects = listOf(DictationEffect.StopListening))
    }

    /** spec SS6.1: "Later 'ready' reports within the same session ... do neither; the cue and the strip state are once per session." */
    private fun handleReady(session: DictationSession): DictationOutcome {
        if (session.active) return DictationOutcome(session)
        return DictationOutcome(session.copy(active = true), effects = listOf(DictationEffect.PlayStartCue))
    }

    /** spec SS6.3, SS6.4: beginning of speech cancels both the silence timer and the segmented watchdog. */
    private fun handleBeginningOfSpeech(session: DictationSession): DictationOutcome =
        DictationOutcome(session.copy(silenceDeadlineMs = null, watchdogDeadlineMs = null))

    // ---------------------------------------------------------------------------------------
    // Partials. spec SS7.1.
    // ---------------------------------------------------------------------------------------

    /**
     * spec SS7.1, plus the c440844 invariant: once the user has edited the field under this
     * utterance ([PendingUtterance.Invalidated]) every later partial of the same utterance is
     * still speech for the timers' purposes but writes nothing, since a new composing text would
     * put the deleted words straight back. The utterance stays invalidated until its boundary.
     */
    private fun handlePartialResult(session: DictationSession, rawText: String, textSettings: DictationTextSettings): DictationOutcome {
        val text = SessionEcho.strip(rawText, session.utterance.finishedThisSession)
        if (text.isBlank()) return DictationOutcome(session) // "Empty partials are ignored."
        val invalidated = session.utterance.pending is PendingUtterance.Invalidated
        // SPEC GAP: SS7.2's "new utterance inside one request" is written, tested and
        // deliberately NOT wired. dictation.md's own keep-or-drop table marks it undecided
        // ("written for a pre-segmented world; check whether Google still restarts hypotheses
        // inside one request"), and SameUtteranceCheck answers "different" for an ordinary
        // recognizer self-correction ("the coffee is hot" then "that coffee is hot"), which
        // would commit the first hypothesis and compose the second after it: the very text
        // duplication SessionEcho was added to stop. Wire it only with device evidence.
        val next = session.copy(
            heardSpeech = true,
            utterance = if (invalidated) session.utterance else session.utterance.copy(pending = PendingUtterance.Live(text)),
            silenceDeadlineMs = if (session.stopRequested) session.silenceDeadlineMs else null,
            watchdogDeadlineMs = if (session.stopRequested) session.watchdogDeadlineMs else null,
        )
        if (invalidated) return DictationOutcome(next)
        val displayed = DictationPartialDisplay.display(text, session.utterance.context, textSettings)
        return DictationOutcome(next, textOps = listOf(DictationTextOp.SetComposingText(displayed)))
    }

    // ---------------------------------------------------------------------------------------
    // Finals. spec SS7.3, SS3 ("pause 0"), SS6.3 (segmented mode ignored), SS6.4 (continuation).
    // ---------------------------------------------------------------------------------------

    private fun handleFinalResult(session: DictationSession, text: String?, now: Long, settings: DictationSettings, textSettings: DictationTextSettings): DictationOutcome {
        val finished = finishFromResult(text, session.utterance, textSettings)

        if (session.stopRequested) {
            return DictationOutcome(null, endEffects(session, cancelRecognizer = false), finished.ops)
        }
        if (settings.pauseMs <= 0L) {
            // spec SS3: "Pause set to 0 and a final arrives (restart-loop mode): one utterance per session."
            return DictationOutcome(null, endEffects(session, cancelRecognizer = false), finished.ops)
        }
        if (session.mode == DictationMode.SEGMENTED) {
            // spec SS6.3: "An ordinary final arriving in segmented mode means the engine ignored the
            // request and ran a plain one-shot ... the watchdog (armed with zero segments) will
            // close the session pause + 5000 ms later and set the latch." No new request is issued.
            val next = session.copy(
                heardSpeech = true,
                utterance = afterFinish(session.utterance, finished),
                watchdogDeadlineMs = now + DictationTiming.watchdogMs(settings.pauseMs),
            )
            return DictationOutcome(next, textOps = finished.ops)
        }
        // spec SS6.4: arm the silence timer and start a continuation at once. spec SS1: a final,
        // like a non-empty partial, means the session has heard speech.
        val next = session.copy(
            heardSpeech = true,
            isContinuation = true,
            requestStartMs = now,
            silenceDeadlineMs = now + DictationTiming.silenceTimerMs(settings.pauseMs),
            utterance = afterFinish(session.utterance, finished),
        )
        return DictationOutcome(next, listOf(DictationEffect.StartListening(DictationMode.RESTART_LOOP)), finished.ops)
    }

    /** spec SS6.3: one utterance inside a segmented session; committed like a final, then the watchdog is (re)armed. */
    private fun handleSegmentResult(session: DictationSession, text: String, now: Long, settings: DictationSettings, textSettings: DictationTextSettings): DictationOutcome {
        val finished = finishFromResult(text, session.utterance, textSettings)
        val next = session.copy(
            heardSpeech = true,
            segmentsSeen = session.segmentsSeen + 1,
            utterance = afterFinish(session.utterance, finished),
            watchdogDeadlineMs = now + DictationTiming.watchdogMs(settings.pauseMs),
            silenceDeadlineMs = null,
        )
        return DictationOutcome(next, textOps = finished.ops)
    }

    /**
     * spec SS7.3: "A final with text finishes the utterance with that text. A final without text
     * finishes the utterance from the last partial instead ... A final without text and with no
     * partial remembered clears any composing region and inserts nothing."
     *
     * The c440844 invariant, "words the user deleted are never typed back", governs both branches:
     * an [PendingUtterance.Invalidated] utterance finishes with nothing even when the final carries
     * its own text, because the engine's final normally repeats the very words the user just
     * removed (D3: the whole utterance arrives as the last partial and again as the final). The
     * callers of this function all reset the pending state to [PendingUtterance.None] afterwards,
     * which is the utterance boundary where invalidation ends.
     */
    private fun finishFromResult(rawText: String?, utterance: UtteranceState, textSettings: DictationTextSettings): UtteranceFinisher.Finished {
        if (utterance.pending is PendingUtterance.Invalidated) return UtteranceFinisher.NOTHING
        // The echo of what this session already finished is not part of this utterance (SessionEcho).
        val text = rawText?.let { SessionEcho.strip(it, utterance.finishedThisSession) }
        // A whitespace-only final carries no words of its own (SS7.3's "final without text").
        val resolvedText = if (!text.isNullOrBlank()) text else (utterance.pending as? PendingUtterance.Live)?.text
        return resolvedText?.let { UtteranceFinisher.finish(it, utterance.context, textSettings) } ?: UtteranceFinisher.NOTHING
    }

    /** The state after an utterance finished: context extended, nothing pending, and the finished words remembered for [SessionEcho]. */
    private fun afterFinish(utterance: UtteranceState, finished: UtteranceFinisher.Finished): UtteranceState = UtteranceState(
        context = extendContext(utterance.context, finished.plainText),
        pending = PendingUtterance.None,
        finishedThisSession = SessionEcho.extend(utterance.finishedThisSession, finished.plainText),
    )

    /** The next utterance's frozen context is this utterance's context plus whatever this module itself just wrote; never a fresh read. See [UtteranceContext]'s KDoc. */
    private fun extendContext(context: UtteranceContext, addedPlainText: String?): UtteranceContext =
        if (addedPlainText == null) context else UtteranceContext((context.textBeforeUtterance ?: "") + addedPlainText)

    // ---------------------------------------------------------------------------------------
    // Errors. spec SS6.6: eight rules, tried top to bottom, first match wins.
    // ---------------------------------------------------------------------------------------

    private fun handleError(session: DictationSession, code: Int, now: Long, settings: DictationSettings, textSettings: DictationTextSettings): DictationOutcome {
        // Rule 1: segmented refusal (SS6.3).
        if (session.mode == DictationMode.SEGMENTED &&
            session.segmentsSeen == 0 &&
            session.active &&
            !session.stopRequested &&
            now - session.sessionStartMs < DictationTiming.SEGMENTED_REFUSAL_WINDOW_MS && // SS6.3: "within 1200 ms of the session start"
            DictationErrorClassifier.isSegmentedRefusal(code)
        ) {
            val next = session.copy(
                mode = DictationMode.RESTART_LOOP,
                isContinuation = false,
                requestStartMs = now,
                utterance = session.utterance.copy(pending = PendingUtterance.None),
            )
            return DictationOutcome(
                next,
                listOf(DictationEffect.StartListening(DictationMode.RESTART_LOOP)),
                clearComposingOps(session.utterance.pending),
                newSegmentedRefusalLatch = true,
            )
        }

        // Rule 2: not a continuation, and the first-words grace conditions hold (SS6.2).
        if (!session.isContinuation && FirstWordsGrace.canReListen(session, now, code)) {
            val next = session.copy(restartsUsed = session.restartsUsed + 1, requestStartMs = now)
            return if (DictationErrorClassifier.isBusy(code)) {
                DictationOutcome(next.copy(busyRetryDeadlineMs = now + DictationTiming.BUSY_RETRY_DELAY_MS))
            } else {
                DictationOutcome(next, listOf(DictationEffect.StartListening(session.mode)))
            }
        }

        // Rule 3: a continuation older than 700 ms ends with 7 or 6 (SS6.4).
        if (session.isContinuation && DictationErrorClassifier.isQuiet(code) && now - session.requestStartMs >= DictationTiming.CONTINUATION_FAILURE_WINDOW_MS) {
            val finished = finishPendingIfAny(session.utterance, textSettings)
            return if (session.silenceDeadlineMs != null) {
                val next = session.copy(
                    utterance = afterFinish(session.utterance, finished),
                    requestStartMs = now,
                )
                DictationOutcome(next, listOf(DictationEffect.StartListening(session.mode)), finished.ops)
            } else {
                DictationOutcome(null, endEffects(session, cancelRecognizer = false), finished.ops)
            }
        }

        // Rule 4: segmented mode, 7 or 6, at least one segment already seen (SS6.3).
        if (session.mode == DictationMode.SEGMENTED && DictationErrorClassifier.isQuiet(code) && session.segmentsSeen >= 1) {
            val finished = finishPendingIfAny(session.utterance, textSettings)
            return DictationOutcome(null, endEffects(session, cancelRecognizer = false), finished.ops)
        }

        // Rule 5: session active, 7 or 6, a non-blank partial is on screen (SS7.3).
        val live = session.utterance.pending as? PendingUtterance.Live
        if (session.active && DictationErrorClassifier.isQuiet(code) && live != null && live.text.isNotBlank()) {
            val finished = UtteranceFinisher.finish(live.text, session.utterance.context, textSettings)
            return if (!session.stopRequested && settings.pauseMs > 0L) {
                val next = session.copy(
                    isContinuation = true,
                    requestStartMs = now,
                    silenceDeadlineMs = now + DictationTiming.silenceTimerMs(settings.pauseMs),
                    utterance = afterFinish(session.utterance, finished),
                )
                DictationOutcome(next, listOf(DictationEffect.StartListening(session.mode)), finished.ops)
            } else {
                DictationOutcome(null, endEffects(session, cancelRecognizer = false), finished.ops)
            }
        }

        // Rule 6: session active, stop requested, 7 or 6.
        if (session.active && session.stopRequested && DictationErrorClassifier.isQuiet(code)) {
            return DictationOutcome(null, endEffects(session, cancelRecognizer = false), clearComposingOps(session.utterance.pending))
        }

        // Rule 7 (session already ended) never reaches this function; see [handleWithSession].

        // Rule 8: anything else.
        return DictationOutcome(
            null,
            endEffects(session, cancelRecognizer = false, message = DictationErrorClassifier.toastFor(code)),
            clearComposingOps(session.utterance.pending),
        )
    }

    private fun finishPendingIfAny(utterance: UtteranceState, textSettings: DictationTextSettings): UtteranceFinisher.Finished {
        val live = utterance.pending as? PendingUtterance.Live ?: return UtteranceFinisher.NOTHING
        return UtteranceFinisher.finish(live.text, utterance.context, textSettings)
    }

    /** spec SS3's ending table: every row that ends the session (or, for a segmented refusal, drops the request) clears the composing partial rather than committing it, unlike a recognizer-reported quiet error. */
    private fun clearComposingOps(pending: PendingUtterance): List<DictationTextOp> =
        if (pending is PendingUtterance.Live) listOf(DictationTextOp.SetComposingText(""), DictationTextOp.FinishComposing) else emptyList()

    // ---------------------------------------------------------------------------------------
    // Editor lifecycle. spec SS3.
    // ---------------------------------------------------------------------------------------

    private fun handleEditorFieldOpened(session: DictationSession, ownerPackage: String?): DictationOutcome {
        if (ownerPackage != session.ownerPackage) {
            // "Another app takes the editor ... immediately", no grace.
            return DictationOutcome(null, endEffects(session, cancelRecognizer = true), clearComposingOps(session.utterance.pending))
        }
        // Same app, a new field: the 500 ms grace is cancelled and dictation continues.
        return DictationOutcome(session.copy(editorGoneDeadlineMs = null))
    }

    // ---------------------------------------------------------------------------------------
    // The clock. spec SS6.3, SS6.4, SS3.
    // ---------------------------------------------------------------------------------------

    private fun handleClockTick(session: DictationSession, now: Long): DictationOutcome {
        session.busyRetryDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                return DictationOutcome(session.copy(busyRetryDeadlineMs = null), listOf(DictationEffect.StartListening(session.mode)))
            }
        }
        session.silenceDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                // spec SS6.4: "Expiry cancels the recognizer, clears any composing partial, and ends the session."
                return DictationOutcome(null, endEffects(session, cancelRecognizer = true), clearComposingOps(session.utterance.pending))
            }
        }
        session.watchdogDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                // spec SS6.3: zero segments seen sets the refusal latch; one or more leaves it
                // untouched. Only a segmented session can say anything about segmented support;
                // the same watchdog after a restart-loop stop (SS6.5) says nothing about it.
                val latch = if (session.mode == DictationMode.SEGMENTED && session.segmentsSeen == 0) true else null
                return DictationOutcome(null, endEffects(session, cancelRecognizer = true), clearComposingOps(session.utterance.pending), newSegmentedRefusalLatch = latch)
            }
        }
        session.editorGoneDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                return DictationOutcome(null, endEffects(session, cancelRecognizer = true), clearComposingOps(session.utterance.pending))
            }
        }
        return DictationOutcome(session)
    }

    // ---------------------------------------------------------------------------------------

    /**
     * spec SS8.1: "the stop cue plays when the session ends, and only if a start cue was played for
     * it." [cancelRecognizer] distinguishes an ending that must still tear the in-flight request
     * down (a timer fired with no recognizer answer coming) from one where the recognizer's own
     * terminal callback already ended it.
     */
    private fun endEffects(session: DictationSession, cancelRecognizer: Boolean, message: DictationMessage? = null): List<DictationEffect> = buildList {
        if (cancelRecognizer) add(DictationEffect.CancelListening)
        if (session.active) add(DictationEffect.PlayStopCue)
        message?.let { add(DictationEffect.ShowMessage(it)) }
    }
}
