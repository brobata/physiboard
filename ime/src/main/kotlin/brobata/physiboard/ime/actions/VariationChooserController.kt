package brobata.physiboard.ime.actions

import android.inputmethodservice.InputMethodService
import android.os.Handler
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.VariationChooser
import brobata.physiboard.core.strip.StripTheme

/**
 * The accent chooser. spec: layers-sym-alt.md SS8.4. When a long press in Accent mode types the
 * first of several accents, a transient bar above the keyboard shows them all, each labelled with
 * the digit printed on a key and that key's letter. The rules (what picks, what closes) are
 * `:core:keys`' [VariationChooser]; this class keeps the one open chooser, its idle timer, and
 * draws it through the skin-tone chooser's row ([SkinTonePanelController]).
 *
 * It never intercepts Space, Enter, Shift or Backspace: those close it and do what they always do.
 * A bare letter picks only while the long-pressed key is still held, so typing on never picks.
 */
internal class VariationChooserController(service: InputMethodService, private val handler: Handler) {

    interface Host {
        val theme: StripTheme
        val aboveBottomPx: Int

        /** The device-layer (Alt) text of [key]: the Titan prints the digits 0 to 9 on ten letter keys. */
        fun deviceLayerText(key: KeyId): String?

        /** The letter key that carries [digit] on the device layer, or null. */
        fun digitKeyLabel(digit: Int): String?

        /** Replace [previous], just before the caret, with [picked]; false when the text no longer ends with [previous]. */
        fun replace(previous: String, picked: String): Boolean
    }

    var host: Host? = null

    private val panel = SkinTonePanelController(service)
    private var state: VariationChooser.State? = null
    private val consumedUps = HashSet<KeyId>()
    private val idleClose = Runnable { close() }

    val isOpen: Boolean get() = state != null && panel.isShown

    /** spec SS8.4: the long press on [key] just typed [committed], the first of [choices]; the key is still held. */
    fun open(key: KeyId, choices: List<String>, committed: String) {
        val host = host ?: return
        close()
        if (!VariationChooser.opens(choices)) return
        state = VariationChooser.State(heldKey = key, choices = choices, committed = committed)
        trace("open held=$key choices=${choices.size}")
        panel.show(
            forms = choices,
            keyLabels = choices.indices.map { host.digitKeyLabel(VariationChooser.digitForIndex(it)) },
            theme = host.theme,
            aboveBottomPx = host.aboveBottomPx,
            onPick = { form -> pick(choices.indexOf(form)) },
            onClose = { close() },
            digitOf = VariationChooser::digitForIndex,
            glyphSp = GLYPH_SP,
        )
        restartIdle()
    }

    /**
     * spec SS8.4's key table, for one hardware key event. Returns true when the event is consumed
     * here (a pick, Back, Alt, the held key's auto-repeat, or the release of a key consumed here);
     * false lets it go on to the keyboard as usual.
     */
    fun onKey(key: KeyId, down: Boolean, repeatCount: Int, altHeld: Boolean): Boolean {
        val consumed = decide(key, down, repeatCount, altHeld)
        if (state != null || consumed || key in consumedUps) {
            trace("key $key ${if (down) "down" else "up"} rep=$repeatCount alt=$altHeld -> ${if (consumed) "consumed" else "passed"} open=${state != null} shown=${panel.isShown}")
        }
        return consumed
    }

    private fun decide(key: KeyId, down: Boolean, repeatCount: Int, altHeld: Boolean): Boolean {
        if (!down) {
            if (consumedUps.remove(key)) return true
            state?.let { state = VariationChooser.onKeyUp(it, key) }
            return false
        }
        // A key consumed here that is still held: its auto-repeats would reach the keyboard,
        // which never saw its down, and type its letter after the pick.
        if (repeatCount > 0 && key in consumedUps) return true
        val current = state ?: return false
        if (!panel.isShown) {
            state = null
            return false
        }
        val digit = VariationChooser.digitFor(host?.deviceLayerText(key), (key as? KeyId.Digit)?.digit)
        return when (val outcome = VariationChooser.onKeyDown(current, key, repeatCount, digit, altHeld)) {
            is VariationChooser.KeyOutcome.Pick -> {
                consumedUps.add(key)
                pick(outcome.index)
                true
            }
            VariationChooser.KeyOutcome.Dismiss -> {
                consumedUps.add(key)
                close()
                true
            }
            VariationChooser.KeyOutcome.Swallow -> true
            VariationChooser.KeyOutcome.Cycle -> {
                consumedUps.add(key)
                cycle(current)
                true
            }
            VariationChooser.KeyOutcome.ArmAlt -> {
                consumedUps.add(key)
                state = current.copy(altArmed = true)
                restartIdle()
                true
            }
            VariationChooser.KeyOutcome.PassOnKeepOpen -> false
            VariationChooser.KeyOutcome.CloseAndPassOn -> {
                close()
                false
            }
        }
    }

    /** Close the chooser and forget every consumed key: the field finished, the window hid, the service is going away. */
    fun reset() {
        close()
        consumedUps.clear()
    }

    private fun pick(index: Int) {
        val current = state ?: return
        val picked = current.choices.getOrNull(index)
        close()
        if (picked == null || picked == current.committed) return
        val replaced = host?.replace(current.committed, picked)
        trace("pick index=$index replaced=$replaced")
    }

    /** The same letter again: the next accent replaces the current one and the bar stays open for another tap. */
    private fun cycle(current: VariationChooser.State) {
        val next = current.choices[VariationChooser.nextIndex(current)]
        val replaced = host?.replace(current.committed, next) == true
        state = if (replaced) current.copy(committed = next) else null
        if (!replaced) panel.hide()
        trace("cycle replaced=$replaced")
        restartIdle()
    }

    /** A compact, always-on trace (Log.println survives the release build's stripping); keys only, never field text. */
    private fun trace(line: String) {
        android.util.Log.println(android.util.Log.INFO, "PhysiBoardAccentTrace", line)
    }

    private fun close() {
        handler.removeCallbacks(idleClose)
        state = null
        panel.hide()
    }

    private fun restartIdle() {
        handler.removeCallbacks(idleClose)
        handler.postDelayed(idleClose, IDLE_CLOSE_MS)
    }

    private companion object {
        /** spec SS8.4: like the page chooser, it closes after 10 seconds without a key. */
        const val IDLE_CLOSE_MS = 10_000L

        /** Ten accents across the Titan's narrow screen. */
        const val GLYPH_SP = 22f
    }
}
