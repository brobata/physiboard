package brobata.physiboard.core.keys

/**
 * Keys with no text box: the accessibility service's half of the key path.
 *
 * Android sends an input method keys only while a window that takes text has focus. In a camera
 * viewfinder, a video player or most of Settings the keyboard is never asked, so the Sym shortcuts,
 * the Fn layer and the home screen keys did nothing there. The optional accessibility service sees
 * every hardware key before any window does. While the keyboard has no editable field it hands the
 * key to the keyboard's own no-field path (keys-and-modifiers.md SS15), the same code that runs
 * when the keyboard is sent the key itself, so nothing is decided twice and nothing can disagree.
 *
 * Where the keyboard IS sent the key as well (a launcher with a search box connected, say), the
 * service saw it first. [RelayedKeys] remembers what the service already handed over, so the
 * keyboard lets that same event through untouched instead of running it a second time.
 *
 * spec: keys-and-modifiers.md SS15.1; per-app-behavior.md SS16.
 */
object AccessibilityKeyRelay {

    /** What the service does with one key. */
    enum class Verdict {
        /** Return false: the window (and, if it is sent the key, the keyboard) gets it as if the service were not there. */
        PASS_THROUGH,

        /** Hand it to the keyboard's no-field path; the service returns whatever that path consumed. */
        RELAY,
    }

    /**
     * [featureOn] is `accessibility_fn_shortcuts`; [keyboardRunning] whether PhysiBoard's keyboard
     * is the one Android has bound (its service exists in this process); [fieldReallyEditable] the
     * keyboard's own answer for the field it is attached to, the same flag SS15 branches on.
     *
     * Fn itself always passes: the Titan sends no press or release for it, only a burst of repeats,
     * and a chord with it arrives as the other key carrying the Ctrl bit, which is all the no-field
     * path reads. Feeding the repeats in would only latch a Ctrl that is never released.
     */
    fun verdict(featureOn: Boolean, keyboardRunning: Boolean, fieldReallyEditable: Boolean, fnOrigin: Boolean): Verdict = when {
        !featureOn || !keyboardRunning -> Verdict.PASS_THROUGH
        fieldReallyEditable -> Verdict.PASS_THROUGH
        fnOrigin -> Verdict.PASS_THROUGH
        else -> Verdict.RELAY
    }

    /**
     * What the service answers for a key it relayed, once the keyboard's own path has run it.
     * The rule is "every other key goes on to the app untouched", so:
     *
     * - Alt, Shift and Ctrl update the keyboard's state (a Sym chord or the Fn layer may read it)
     *   but always go on to the app: with no text box they are the app's keys, even though the
     *   keyboard's own path (which assumes it owns them) marks them used.
     * - A key-up is consumed exactly when its down was ([downWasConsumed]), so an app never sees a
     *   press without its release, or a release without its press.
     * - A key-down is consumed when the keyboard's path consumed it.
     */
    fun consumes(key: KeyId, edge: KeyEdge, pathConsumed: Boolean, downWasConsumed: Boolean): Boolean = when {
        key is KeyId.Modifier && key.key != ModifierKey.SYM -> false
        edge == KeyEdge.UP -> downWasConsumed
        else -> pathConsumed
    }
}

/** The keys whose relayed down the service consumed, so their release is consumed too ([AccessibilityKeyRelay.consumes]). */
class ConsumedRelayDowns {
    private val held = HashSet<Int>()

    fun onDown(keyCode: Int, consumed: Boolean) {
        if (consumed) held += keyCode else held -= keyCode
    }

    /** Whether [keyCode]'s down was consumed; the answer is used once, by its release. */
    fun takeOnUp(keyCode: Int): Boolean = held.remove(keyCode)

    fun clear() = held.clear()
}

/**
 * One key event as both the accessibility service and the keyboard receive it. The service is
 * handed a copy, so identity is by value: the times, the action, the key and the repeat count are
 * what the copy keeps.
 */
data class KeyEventIdentity(
    val downTimeMs: Long,
    val eventTimeMs: Long,
    val action: Int,
    val keyCode: Int,
    val scanCode: Int,
    val repeatCount: Int,
)

/**
 * The last few events the service handed to the keyboard. The keyboard asks [wasRelayed] before it
 * runs an event it is sent itself; a match means the service already ran it (and did not consume
 * it, or the keyboard would not be seeing it), so it goes on to the app unchanged. A handful is
 * plenty: the service sees each event moments before the keyboard does, in order.
 */
class RelayedKeys(private val capacity: Int = DEFAULT_CAPACITY) {
    private val recent = ArrayDeque<KeyEventIdentity>()

    fun remember(event: KeyEventIdentity) {
        recent.addLast(event)
        while (recent.size > capacity) recent.removeFirst()
    }

    /** True once per remembered event: a second identical delivery is a new event and runs. */
    fun wasRelayed(event: KeyEventIdentity): Boolean = recent.remove(event)

    fun clear() = recent.clear()

    companion object {
        const val DEFAULT_CAPACITY = 8
    }
}
