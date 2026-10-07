package brobata.physiboard.ime.actions

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.SystemClock
import brobata.physiboard.core.actions.emoji.SkinToneChooser
import brobata.physiboard.core.actions.emoji.SkinTones
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.strip.StripTheme

/**
 * Skin tones from the hardware keys and from a held key on the Emoji page grid. spec:
 * expansion-clipboard-pickers-launcher.md SS4.7. The rules (what arms, what each key does while
 * the chooser is open) are `:core:actions`' [SkinToneChooser]; this class keeps the one hold and
 * the one open chooser, runs the timer, and draws the forms through [SkinTonePanelController].
 *
 * Nothing here intercepts Space, Enter, Shift or Backspace on its own: while the chooser is open
 * those keys close it and then do what they always do.
 */
internal class SkinToneHoldController(service: InputMethodService, private val handler: Handler) {

    interface Host {
        val theme: StripTheme
        val aboveBottomPx: Int

        /** The device-layer (Alt) text of [key]: the Titan prints 0 to 5 on Q, W, E, R, S and D. */
        fun deviceLayerText(key: KeyId): String?

        /** For each digit 0 to 5, the letter key that carries it on the device layer, or null. */
        fun digitKeyLabels(): List<String?>

        /** Replace [committed] just before the caret with [form] (insert [form] when it is not there). */
        fun replaceCommitted(committed: String, form: String)
    }

    var host: Host? = null

    private val panel = SkinTonePanelController(service)
    private var hold: SkinToneChooser.Hold? = null
    private var forms: List<String> = emptyList()
    private var heldKey: KeyId? = null
    private var onPick: ((String) -> Unit)? = null
    private val consumedUps = HashSet<KeyId>()
    private val fireRunnable = Runnable { fire() }

    val isOpen: Boolean get() = panel.isShown

    /**
     * spec SS4.7: a fresh press of [key] just committed [committed]; when it takes tones and the
     * key is still down after [thresholdMs] (the long-press threshold), the chooser opens.
     */
    fun onCommitted(key: KeyId, committed: String, eventTimeMs: Long, thresholdMs: Long) {
        cancelHold()
        if (!SkinToneChooser.arms(committed)) return
        // The exact committed text (a custom page entry may carry spaces) is what the pick
        // compares with the text before the caret and replaces.
        val armed = SkinToneChooser.Hold(key, committed, eventTimeMs, thresholdMs)
        hold = armed
        val delay = (armed.deadlineMs() - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.postDelayed(fireRunnable, delay)
    }

    /** spec SS4.7, touch: a long press on an Emoji page key whose emoji takes tones; [pick] inserts the chosen form. */
    fun openForTouch(emoji: String, pick: (String) -> Unit): Boolean {
        val choices = SkinTones.forms(emoji)
        if (choices.isEmpty()) return false
        cancelHold()
        open(choices, heldKey = null, pick = pick)
        return true
    }

    /**
     * spec SS4.7's key table, for one hardware key event. Returns true when the event is consumed
     * here (a pick, a dismissal, the held key's auto-repeat, or the release of a key consumed
     * here); false lets it go on to the keyboard as usual.
     */
    fun onKey(key: KeyId, down: Boolean, repeatCount: Int, eventTimeMs: Long): Boolean {
        if (!down) {
            if (consumedUps.remove(key)) return true
            if (hold?.key == key) cancelHold()
            if (heldKey == key) heldKey = null
            return false
        }
        // A key consumed here (the pick key, Back, Alt) that is still held: its auto-repeats would
        // reach the keyboard, which never saw its down, and type its letter after the pick.
        if (repeatCount > 0 && key in consumedUps) return true
        if (isOpen) {
            val digit = SkinToneChooser.digitFor(host?.deviceLayerText(key), (key as? KeyId.Digit)?.digit)
            val isBack = key == KeyId.Control(ControlKey.BACK)
            val altOrShift = key == KeyId.Modifier(ModifierKey.ALT) || key == KeyId.Modifier(ModifierKey.SHIFT)
            val outcome = SkinToneChooser.onKeyDown(forms, isBack, isHeldKeyRepeat = key == heldKey && repeatCount > 0, digit = digit, isAltOrShift = altOrShift)
            return when (outcome) {
                is SkinToneChooser.KeyOutcome.Pick -> {
                    val pick = onPick
                    close()
                    consumedUps.add(key)
                    pick?.invoke(outcome.form)
                    true
                }
                SkinToneChooser.KeyOutcome.Dismiss -> {
                    close()
                    consumedUps.add(key)
                    true
                }
                SkinToneChooser.KeyOutcome.Swallow -> {
                    if (altOrShift) consumedUps.add(key)
                    true
                }
                SkinToneChooser.KeyOutcome.CloseAndPassOn -> {
                    close()
                    false
                }
            }
        }
        val armed = hold ?: return false
        if (key == armed.key && repeatCount > 0) {
            // The held key's auto-repeat must not type its letter after the emoji. A repeat that
            // arrives once the threshold has passed opens the chooser at once, should the timer
            // be late.
            if (armed.hasFired(eventTimeMs)) fire()
            return true
        }
        if (repeatCount == 0) cancelHold()
        return false
    }

    /** Close the chooser and forget any hold: the field finished, the service is going away. */
    fun reset() {
        cancelHold()
        close()
        consumedUps.clear()
        heldKey = null
    }

    private fun fire() {
        handler.removeCallbacks(fireRunnable)
        val armed = hold ?: return
        hold = null
        val emoji = armed.committed.trim()
        val choices = SkinTones.forms(emoji)
        if (choices.isEmpty()) return
        // Any spaces around the emoji in the committed text are kept around the chosen form.
        open(choices, heldKey = armed.key) { form -> host?.replaceCommitted(armed.committed, armed.committed.replace(emoji, form)) }
    }

    private fun open(choices: List<String>, heldKey: KeyId?, pick: (String) -> Unit) {
        val host = host ?: return
        forms = choices
        this.heldKey = heldKey
        onPick = pick
        panel.show(
            choices,
            host.digitKeyLabels(),
            host.theme,
            host.aboveBottomPx,
            onPick = { form ->
                close()
                pick(form)
            },
            onClose = { close() },
        )
    }

    private fun close() {
        panel.hide()
        forms = emptyList()
        onPick = null
    }

    private fun cancelHold() {
        hold = null
        handler.removeCallbacks(fireRunnable)
    }
}
