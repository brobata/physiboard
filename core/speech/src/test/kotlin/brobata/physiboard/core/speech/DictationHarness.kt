package brobata.physiboard.core.speech

/**
 * A fake of everything around [DictationEngine]: the recognizer's callbacks are fed in as events
 * in the order and at the times the phone's log showed them, the effects are recorded, and the
 * text ops are applied to a model of an editor with a composing region. Every scenario test
 * drives this rather than calling [DictationEngine.handle] by hand, so "no word is lost" is
 * asserted on the text a real field would end up holding.
 */
class DictationHarness(
    var settings: DictationSettings = DictationSettings(androidApiLevel = 36),
    private val textSettings: DictationTextSettings = DictationTextSettings(),
) {
    var session: DictationSession? = null
        private set
    var segmentedRefusalLatch: Boolean = false
    val effects = mutableListOf<DictationEffect>()
    val field = FakeField()

    fun send(event: DictationEvent, now: Long): DictationOutcome {
        val outcome = DictationEngine.handle(session, event, now, settings, textSettings, segmentedRefusalLatch)
        session = outcome.session
        outcome.newSegmentedRefusalLatch?.let { segmentedRefusalLatch = it }
        effects += outcome.effects
        field.apply(outcome.textOps)
        return outcome
    }

    /** Fires every armed timer whose deadline is at or before [now], in deadline order, the way the controller's single wakeup would. */
    fun runClockTo(now: Long): List<DictationOutcome> {
        val outcomes = mutableListOf<DictationOutcome>()
        while (true) {
            val deadline = session?.nextDeadlineMs ?: break
            if (deadline > now) break
            outcomes += send(DictationEvent.ClockTick, deadline)
        }
        return outcomes
    }

    /** The effects recorded since the last call; lets a test assert that a stretch of speech produced none. */
    fun drainEffects(): List<DictationEffect> = effects.toList().also { effects.clear() }

    fun starts(): Int = effects.count { it is DictationEffect.StartListening }
}

/** An editor with one composing region, the way `InputConnection` behaves for the ops `:core:speech` emits. */
class FakeField {
    private val committed = StringBuilder()
    private var composing: String = ""

    val text: String get() = committed.toString() + composing

    fun apply(ops: List<DictationTextOp>) {
        for (op in ops) {
            when (op) {
                is DictationTextOp.SetComposingText -> composing = op.text
                DictationTextOp.FinishComposing -> {
                    committed.append(composing)
                    composing = ""
                }
                is DictationTextOp.CommitText -> {
                    committed.append(op.text)
                    composing = ""
                }
                is DictationTextOp.DeleteBeforeCursor -> {
                    if (composing.isNotEmpty()) composing = composing.dropLast(op.count) else committed.setLength((committed.length - op.count).coerceAtLeast(0))
                }
            }
        }
    }
}
