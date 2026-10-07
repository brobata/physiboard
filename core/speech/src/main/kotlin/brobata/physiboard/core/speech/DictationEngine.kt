package brobata.physiboard.core.speech

/**
 * The whole dictation session as a state machine: `(state, event) -> (state, effects, text ops)`,
 * with no Android import anywhere in this module. spec: dictation.md, whole document; the specific
 * section is cited on each private handler below.
 *
 * The model (spec SS2, SS3): a trigger STARTS a session, and the session then runs until something
 * explicit stops it: the trigger again, a key going down, the field going away, another app
 * taking the audio, or the two safety limits. Nothing in here guesses when the user has finished
 * talking. The recognizer's own endings (a quiet error, an ordinary final, the end of a segmented
 * session) are re-listened silently; they can only ever arrive in silence, so the one thing lost
 * is the ~100 ms the microphone takes to reopen, never a word.
 *
 * [handle] is the single entry point. Every other function in this file is a private step of it,
 * kept separate only so each one can cite the one spec rule it encodes.
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
        is DictationEvent.Trigger -> handleTrigger(state, event, now, settings, textSettings, segmentedRefusalLatch)
        else -> handleWithSession(state, event, now, settings, textSettings)
    }

    /** spec SS6.6: a callback for a session that has already ended is ignored. Every event but [DictationEvent.Trigger] needs a live session to act on. */
    private fun handleWithSession(
        state: DictationSession?,
        event: DictationEvent,
        now: Long,
        settings: DictationSettings,
        textSettings: DictationTextSettings,
    ): DictationOutcome {
        val session = state ?: return DictationOutcome(null)
        return when (event) {
            is DictationEvent.Trigger -> throw IllegalStateException("Trigger is handled before a session is required")
            DictationEvent.ReadyForSpeech -> handleReady(session, now)
            DictationEvent.FirstAudio -> handleFirstAudio(session)
            DictationEvent.BeginningOfSpeech -> DictationOutcome(session.copy(lastSpeechMs = now, consecutiveFailures = 0))
            DictationEvent.EndOfSpeech -> DictationOutcome(session)
            is DictationEvent.PartialResult -> handlePartialResult(session, event.text, now, textSettings)
            is DictationEvent.FinalResult -> handleFinalResult(session, event.text, now, textSettings)
            is DictationEvent.SegmentResult -> handleSegmentResult(session, event.text, now, textSettings)
            DictationEvent.SegmentedSessionEnded -> handleSegmentedSessionEnded(session, now, textSettings)
            is DictationEvent.Error -> handleError(session, event.code, now, settings, textSettings)
            DictationEvent.KeyDown -> if (settings.stopOnTyping) endNow(session, textSettings) else DictationOutcome(session)
            DictationEvent.AudioFocusLost -> endNow(session, textSettings)
            DictationEvent.PrivateModeTurnedOn ->
                if (!session.request.preferOffline && session.phase != DictationPhase.STOPPING) requestStop(session, now) else DictationOutcome(session)
            DictationEvent.EditorFieldClosed -> DictationOutcome(session.copy(editorGoneDeadlineMs = now + DictationTiming.EDITOR_GONE_GRACE_MS))
            is DictationEvent.EditorFieldOpened -> handleEditorFieldOpened(session, event.ownerPackage)
            DictationEvent.UserEditedComposingText -> DictationOutcome(session.copy(utterance = session.utterance.copy(pending = PendingUtterance.Invalidated)))
            DictationEvent.EditorRejectedInsert -> DictationOutcome(null, endEffects(session, cancelRecognizer = true), clearComposingOps(session.utterance.pending))
            is DictationEvent.StartFailed -> DictationOutcome(
                null,
                endEffects(session, cancelRecognizer = false, message = DictationStartFailureMessages.forReason(event.reason)),
                clearComposingOps(session.utterance.pending),
            )
            DictationEvent.ClockTick -> handleClockTick(session, now, textSettings)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Starting and stopping. spec: dictation.md SS2, SS2.6, SS3, SS6.5.
    // ---------------------------------------------------------------------------------------

    /**
     * spec SS2: "if a session is running, the trigger stops it; otherwise it starts one." A trigger
     * while the session is still starting (no audio yet) or already stopping is the user saying
     * "off, now": the session ends at once rather than waiting on the engine.
     */
    private fun handleTrigger(
        state: DictationSession?,
        event: DictationEvent.Trigger,
        now: Long,
        settings: DictationSettings,
        textSettings: DictationTextSettings,
        segmentedRefusalLatch: Boolean,
    ): DictationOutcome {
        if (state != null) {
            return if (state.phase == DictationPhase.LISTENING) requestStop(state, now) else endNow(state, textSettings)
        }
        val request = RecognizerRequestPlanner.plan(settings, segmentedRefusalLatch)
        val fresh = DictationSession.fresh(event.ownerPackage, now, request, settings, event.textBeforeSession)
        val effects = buildList {
            // spec SS6.8: the keyboard must count as shown before the microphone opens, or the
            // recording is silenced (D22); `:ime` waits for that grant before issuing the request.
            add(DictationEffect.HoldImeVisible)
            // spec SS6.7: focus is taken before the microphone opens, so music is already paused
            // by the time the first word is spoken.
            if (settings.pauseMedia) add(DictationEffect.AcquireAudioFocus)
            add(DictationEffect.StartListening(request))
        }
        return DictationOutcome(fresh, effects)
    }

    /**
     * spec SS6.5: the graceful stop. The engine is asked to stop so it delivers the words it is
     * still holding; the session closes on that delivery, or on the watchdog if none comes. Any
     * pending re-listen is dropped: the user asked for the microphone to close, so nothing may
     * reopen it.
     */
    private fun requestStop(session: DictationSession, now: Long): DictationOutcome {
        val next = session.copy(
            phase = DictationPhase.STOPPING,
            busyRetryDeadlineMs = null,
            relistenDeadlineMs = null,
            cueFallbackDeadlineMs = null,
            stopWatchdogDeadlineMs = now + DictationTiming.STOP_WATCHDOG_MS,
        )
        return DictationOutcome(next, listOf(DictationEffect.StopListening))
    }

    /**
     * spec SS3: the immediate stop (a key, lost audio focus, a second trigger while stopping). The
     * words on screen are what the user sees, so they are committed as they stand; the request is
     * cancelled so nothing it says afterwards can land in the field.
     */
    private fun endNow(session: DictationSession, textSettings: DictationTextSettings): DictationOutcome {
        val finished = finishPendingIfAny(session.utterance, textSettings)
        return DictationOutcome(null, endEffects(session, cancelRecognizer = true), finished.ops)
    }

    /** spec SS6.1, SS8.1: "ready" opens the session to speech; the cue waits for the first audio report, or this fallback. */
    private fun handleReady(session: DictationSession, now: Long): DictationOutcome {
        if (session.phase != DictationPhase.STARTING) return DictationOutcome(session)
        return DictationOutcome(
            session.copy(
                phase = DictationPhase.LISTENING,
                cueFallbackDeadlineMs = if (session.cuePlayed) null else now + DictationTiming.CUE_FALLBACK_MS,
            ),
        )
    }

    /** spec SS8.1: the start cue plays at the first audio level report, once per session. */
    private fun handleFirstAudio(session: DictationSession): DictationOutcome {
        if (session.cuePlayed || session.phase == DictationPhase.STOPPING) return DictationOutcome(session)
        val next = session.copy(
            phase = if (session.phase == DictationPhase.STARTING) DictationPhase.LISTENING else session.phase,
            cuePlayed = true,
            cueFallbackDeadlineMs = null,
        )
        return DictationOutcome(next, listOf(DictationEffect.PlayStartCue))
    }

    // ---------------------------------------------------------------------------------------
    // Partials. spec SS7.1.
    // ---------------------------------------------------------------------------------------

    /**
     * spec SS7.1, plus the c440844 invariant: once the user has edited the field under this
     * utterance ([PendingUtterance.Invalidated]) every later partial of the same utterance is
     * still speech for the timers' purposes but writes nothing, since a new composing text would
     * put the deleted words straight back. The utterance stays invalidated until its boundary.
     */
    private fun handlePartialResult(session: DictationSession, rawText: String, now: Long, textSettings: DictationTextSettings): DictationOutcome {
        val text = SessionEcho.strip(rawText, session.utterance.finishedThisSession)
        if (text.isBlank()) return DictationOutcome(session) // "Empty partials are ignored."
        val invalidated = session.utterance.pending is PendingUtterance.Invalidated
        val next = session.copy(
            heardSpeech = true,
            lastSpeechMs = now,
            consecutiveFailures = 0,
            utterance = if (invalidated) session.utterance else session.utterance.copy(pending = PendingUtterance.Live(text)),
        )
        if (invalidated) return DictationOutcome(next)
        val displayed = DictationPartialDisplay.display(text, session.utterance.context, textSettings)
        return DictationOutcome(next, textOps = listOf(DictationTextOp.SetComposingText(displayed)))
    }

    // ---------------------------------------------------------------------------------------
    // Finals and segments. spec SS6.2, SS6.3, SS7.3.
    // ---------------------------------------------------------------------------------------

    /**
     * spec SS6.3: an ordinary final means the engine ran one request per utterance (it refused or
     * does not know the segmented session). The words are committed and, while the session is
     * still open, the next request is issued at once; a segmented request answered this way sets
     * the latch so later sessions ask for one request per utterance from the start.
     */
    private fun handleFinalResult(session: DictationSession, text: String?, now: Long, textSettings: DictationTextSettings): DictationOutcome {
        val finished = finishFromResult(text, session.utterance, textSettings)
        val heard = session.heardSpeech || finished.plainText != null
        if (session.phase == DictationPhase.STOPPING) {
            return DictationOutcome(null, endEffects(session, cancelRecognizer = false), finished.ops)
        }
        val ignoredSegmented = session.request.segmented
        val request = if (ignoredSegmented) session.request.copy(segmented = false) else session.request
        val next = session.copy(
            heardSpeech = heard,
            lastSpeechMs = if (finished.plainText != null) now else session.lastSpeechMs,
            request = request,
        )
        val relistened = relistenAfterEngineEnding(next, finished, now, textSettings)
        return relistened.copy(
            effects = if (ignoredSegmented) listOf(DictationEffect.LogMessage(DictationMessage.SEGMENTED_REFUSED)) + relistened.effects else relistened.effects,
            newSegmentedRefusalLatch = if (ignoredSegmented) true else null,
        )
    }

    /**
     * spec SS6.2: the engine ended its request (a quiet error, a plain final, the end of a
     * segmented session); the words it was holding are committed and the next request goes out.
     * An ending that brought no words and came within 700 ms of its request is the engine
     * failing fast, not silence: the re-listen waits 500 ms and is counted, and the fifth in a
     * row ends the session with one message, so a broken engine or language pack never has
     * the microphone opened and closed in a tight loop for the whole silence limit.
     */
    private fun relistenAfterEngineEnding(session: DictationSession, finished: UtteranceFinisher.Finished, now: Long, textSettings: DictationTextSettings): DictationOutcome {
        val fast = finished.plainText == null && now - session.requestStartMs < DictationTiming.FAST_FAILURE_WINDOW_MS
        val failures = if (fast) session.consecutiveFailures + 1 else 0
        if (failures >= DictationTiming.MAX_CONSECUTIVE_FAILURES) {
            return DictationOutcome(null, endEffects(session, cancelRecognizer = false, message = DictationMessage.SPEECH_RECOGNITION_ERROR), finished.ops)
        }
        val next = session.copy(
            requestStartMs = now,
            consecutiveFailures = failures,
            utterance = afterFinish(session.utterance, finished),
        )
        return if (fast) {
            DictationOutcome(next.copy(relistenDeadlineMs = now + DictationTiming.FAST_FAILURE_BACKOFF_MS), textOps = finished.ops)
        } else {
            DictationOutcome(next, listOf(DictationEffect.StartListening(session.request)), finished.ops)
        }
    }

    /** spec SS6.2: one utterance inside a segmented session; committed like a final, and the session simply goes on. */
    private fun handleSegmentResult(session: DictationSession, text: String, now: Long, textSettings: DictationTextSettings): DictationOutcome {
        val finished = finishFromResult(text, session.utterance, textSettings)
        val next = session.copy(
            heardSpeech = session.heardSpeech || finished.plainText != null,
            lastSpeechMs = if (finished.plainText != null) now else session.lastSpeechMs,
            consecutiveFailures = 0,
            utterance = afterFinish(session.utterance, finished),
        )
        return DictationOutcome(next, textOps = finished.ops)
    }

    /**
     * spec SS6.2: the engine ended its segmented session. A partial can genuinely be on screen
     * here (the engine may end between a partial and the segment that would have finalized it),
     * so it is committed, never discarded. While the session is still open the next segmented
     * session is asked for at once: the engine ends only in silence, so nothing is lost.
     */
    private fun handleSegmentedSessionEnded(session: DictationSession, now: Long, textSettings: DictationTextSettings): DictationOutcome {
        val finished = finishPendingIfAny(session.utterance, textSettings)
        if (session.phase == DictationPhase.STOPPING) {
            return DictationOutcome(null, endEffects(session, cancelRecognizer = false), finished.ops)
        }
        return relistenAfterEngineEnding(session, finished, now, textSettings)
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

    private fun finishPendingIfAny(utterance: UtteranceState, textSettings: DictationTextSettings): UtteranceFinisher.Finished {
        val live = utterance.pending as? PendingUtterance.Live ?: return UtteranceFinisher.NOTHING
        return UtteranceFinisher.finish(live.text, utterance.context, textSettings)
    }

    // ---------------------------------------------------------------------------------------
    // Errors. spec SS6.6: the rules, tried top to bottom, first match wins.
    // ---------------------------------------------------------------------------------------

    private fun handleError(session: DictationSession, code: Int, now: Long, settings: DictationSettings, textSettings: DictationTextSettings): DictationOutcome {
        // Rule 1: the session is stopping anyway. Whatever the engine says now, the words on
        // screen are committed and the session closes without a message: the user asked for
        // the microphone to close, and a "no speech" answer to that request is the normal case.
        if (session.phase == DictationPhase.STOPPING) {
            val finished = finishPendingIfAny(session.utterance, textSettings)
            return DictationOutcome(null, endEffects(session, cancelRecognizer = false), finished.ops)
        }

        // Rule 2: segmented refusal (SS6.3): the same session goes on with one request per utterance.
        if (session.request.segmented &&
            !session.heardSpeech &&
            now - session.sessionStartMs < DictationTiming.SEGMENTED_REFUSAL_WINDOW_MS &&
            DictationErrorClassifier.isSegmentedRefusal(code)
        ) {
            val request = session.request.copy(segmented = false)
            val next = session.copy(request = request, requestStartMs = now, utterance = session.utterance.copy(pending = PendingUtterance.None))
            return DictationOutcome(
                next,
                listOf(DictationEffect.LogMessage(DictationMessage.SEGMENTED_REFUSED), DictationEffect.StartListening(request)),
                clearComposingOps(session.utterance.pending),
                newSegmentedRefusalLatch = true,
            )
        }

        // Rule 3: a quiet error (SS6.6). The engine closed its microphone on silence (Google's
        // engine does so after about five seconds with nothing heard, D14): any partial it was
        // holding is finished, and the keyboard listens again at once. There is no cap on how
        // often this happens while the user is silent; the silence limit of SS6.4 is the cap.
        if (DictationErrorClassifier.isQuiet(code)) {
            return relistenAfterEngineEnding(session, finishPendingIfAny(session.utterance, textSettings), now, textSettings)
        }

        // Rule 4: busy (SS6.6): the previous request is still winding down; retry after 300 ms.
        if (DictationErrorClassifier.isBusy(code)) {
            val failures = session.consecutiveFailures + 1
            if (failures >= DictationTiming.MAX_CONSECUTIVE_FAILURES) {
                val finished = finishPendingIfAny(session.utterance, textSettings)
                return DictationOutcome(null, endEffects(session, cancelRecognizer = false, message = DictationMessage.SPEECH_RECOGNITION_ERROR), finished.ops)
            }
            return DictationOutcome(session.copy(consecutiveFailures = failures, busyRetryDeadlineMs = now + DictationTiming.BUSY_RETRY_DELAY_MS))
        }

        // Rule 5: the on-device recognizer has no pack for this language (SS4.3). Outside private
        // mode the same session goes online, silently; in private mode that is not an option.
        if (DictationErrorClassifier.isLanguage(code)) {
            val finished = finishPendingIfAny(session.utterance, textSettings)
            if (session.request.preferOffline && !settings.privateMode) {
                val request = session.request.copy(preferOffline = false)
                val next = session.copy(request = request, requestStartMs = now, utterance = afterFinish(session.utterance, finished))
                return DictationOutcome(
                    next,
                    listOf(DictationEffect.LogMessage(DictationMessage.FELL_BACK_TO_ONLINE), DictationEffect.StartListening(request)),
                    finished.ops,
                )
            }
            val message = if (settings.privateMode) DictationMessage.PRIVATE_MODE_NEEDS_OFFLINE_LANGUAGE else DictationMessage.OFFLINE_LANGUAGE_MISSING
            return DictationOutcome(null, endEffects(session, cancelRecognizer = false, message = message), finished.ops)
        }

        // Rule 6: anything else is a real error: the session ends with a message, and whatever was
        // already heard is committed rather than thrown away.
        val finished = finishPendingIfAny(session.utterance, textSettings)
        return DictationOutcome(
            null,
            endEffects(session, cancelRecognizer = false, message = DictationErrorClassifier.toastFor(code)),
            finished.ops,
        )
    }

    /** spec SS3's ending table: the rows that end the session with no words worth keeping clear the composing partial rather than committing it. */
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
    // The clock. spec SS6.4, SS6.5, SS6.6, SS8.1, SS3.
    // ---------------------------------------------------------------------------------------

    private fun handleClockTick(session: DictationSession, now: Long, textSettings: DictationTextSettings): DictationOutcome {
        session.busyRetryDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                return DictationOutcome(session.copy(busyRetryDeadlineMs = null, requestStartMs = now), listOf(DictationEffect.StartListening(session.request)))
            }
        }
        session.relistenDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                return DictationOutcome(session.copy(relistenDeadlineMs = null, requestStartMs = now), listOf(DictationEffect.StartListening(session.request)))
            }
        }
        session.cueFallbackDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                // spec SS8.1: an engine that reports no audio levels still gets its cue, after "ready".
                return DictationOutcome(session.copy(cueFallbackDeadlineMs = null, cuePlayed = true), listOf(DictationEffect.PlayStartCue))
            }
        }
        session.stopWatchdogDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                // spec SS6.5: the engine never answered the stop; the words on screen are kept.
                return endNow(session, textSettings)
            }
        }
        session.editorGoneDeadlineMs?.let { deadline ->
            if (now >= deadline) {
                return DictationOutcome(null, endEffects(session, cancelRecognizer = true), clearComposingOps(session.utterance.pending))
            }
        }
        // spec SS6.4: the silence limit and the session cap both stop gracefully, so the engine's
        // last words (if it has any) still land.
        val silence = session.silenceDeadlineMs
        val cap = session.sessionCapDeadlineMs
        if ((silence != null && now >= silence) || (cap != null && now >= cap)) {
            return requestStop(session, now)
        }
        return DictationOutcome(session)
    }

    // ---------------------------------------------------------------------------------------

    /**
     * spec SS8.1: "the stop cue plays when the session ends, and only if a start cue was played for
     * it." spec SS6.7, SS6.8: focus and the keyboard's visibility hold, taken at the start, are
     * given back at every ending. [cancelRecognizer]
     * distinguishes an ending that must still tear the in-flight request down from one where the
     * recognizer's own terminal callback already ended it.
     */
    private fun endEffects(
        session: DictationSession,
        cancelRecognizer: Boolean,
        message: DictationMessage? = null,
    ): List<DictationEffect> = buildList {
        if (cancelRecognizer) add(DictationEffect.CancelListening)
        if (session.cuePlayed) add(DictationEffect.PlayStopCue)
        if (session.audioFocusHeld) add(DictationEffect.ReleaseAudioFocus)
        add(DictationEffect.ReleaseImeVisible)
        message?.let { add(DictationEffect.ShowMessage(it)) }
    }
}
