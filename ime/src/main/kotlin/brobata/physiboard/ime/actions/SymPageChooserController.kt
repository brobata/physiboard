package brobata.physiboard.ime.actions

import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.SymChooserEntry
import brobata.physiboard.core.keys.SymChooserTarget
import brobata.physiboard.core.keys.SymPageChooser
import brobata.physiboard.core.strip.StripTheme

/**
 * The Sym page chooser: a small transient panel listing every Sym page with the key that opens
 * it. spec: layers-sym-alt.md SS5.10. The rules (the rows, what each key does) are `:core:keys`'
 * [SymPageChooser]; this class draws the rows, keeps the open chooser's key bookkeeping, and
 * closes itself after a pick, a dismissal, or [IDLE_CLOSE_MS] without a key.
 */
internal class SymPageChooserController(service: InputMethodService, private val handler: Handler) {

    private val panel = BottomOverlay(service, TAG)
    private var onPick: ((SymChooserTarget) -> Unit)? = null
    private val consumedUps = HashSet<KeyId>()
    private val idleClose = Runnable { close() }

    val isOpen: Boolean get() = panel.isShown

    fun show(entries: List<SymChooserEntry>, theme: StripTheme, aboveBottomPx: Int, pick: (SymChooserTarget) -> Unit) {
        close()
        onPick = pick
        val context = panel.overlayContext
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
            val pad = panel.dp(6)
            setPadding(pad, pad, pad, pad)
        }
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(
            TextView(context).apply {
                text = TITLE
                setTextColor(theme.textAndIcons)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            TextView(context).apply {
                text = "✕"
                gravity = Gravity.CENTER
                setTextColor(theme.textAndIcons)
                background = GradientDrawable().apply { setColor(theme.button); cornerRadius = panel.dp(6).toFloat() }
                setOnClickListener { close() }
            },
            LinearLayout.LayoutParams(panel.dp(36), panel.dp(32)),
        )
        column.addView(header)
        val grid = GridLayout(context).apply { columnCount = COLUMNS }
        entries.forEachIndexed { index, entry ->
            val cell = TextView(context).apply {
                text = "${entry.target.letter}  ${entry.target.label}"
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                setTextColor(theme.textAndIcons)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setPadding(panel.dp(10), 0, panel.dp(6), 0)
                // A page that is off for the cycle still opens from here; it is drawn dimmer so
                // the chooser also shows what the Sym taps will step through.
                alpha = if (entry.inCycle) 1f else 0.6f
                background = GradientDrawable().apply {
                    setColor(theme.suggestion)
                    setStroke(panel.dp(1), theme.divider)
                    cornerRadius = panel.dp(6).toFloat()
                }
                setOnClickListener { choose(entry.target) }
            }
            val params = GridLayout.LayoutParams(GridLayout.spec(index / COLUMNS), GridLayout.spec(index % COLUMNS, 1f)).apply {
                width = 0
                height = panel.dp(ROW_DP)
                setMargins(panel.dp(3), panel.dp(3), panel.dp(3), panel.dp(3))
            }
            grid.addView(cell, params)
        }
        column.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (!panel.show(column, heightPx = null, bottomMarginPx = aboveBottomPx)) {
            onPick = null
            return
        }
        handler.postDelayed(idleClose, IDLE_CLOSE_MS)
    }

    /**
     * spec SS5.10's key table for one hardware key event while the chooser is open. Returns true
     * when the event is consumed here; false lets it go on to the keyboard as usual.
     */
    fun onKey(key: KeyId, down: Boolean, repeatCount: Int): Boolean {
        if (!down) return consumedUps.remove(key)
        if (!isOpen) return repeatCount > 0 && key in consumedUps
        return when (val outcome = SymPageChooser.onKeyDown(key, isRepeat = repeatCount > 0 && key in consumedUps)) {
            is SymPageChooser.KeyOutcome.Open -> {
                consumedUps.add(key)
                choose(outcome.target)
                true
            }
            SymPageChooser.KeyOutcome.Dismiss -> {
                consumedUps.add(key)
                close()
                true
            }
            SymPageChooser.KeyOutcome.Swallow -> {
                consumedUps.add(key)
                // A key still counts as activity: the idle close starts over.
                handler.removeCallbacks(idleClose)
                handler.postDelayed(idleClose, IDLE_CLOSE_MS)
                true
            }
            SymPageChooser.KeyOutcome.CloseAndPassOn -> {
                close()
                false
            }
        }
    }

    /** Closes the chooser (field finished, window hidden, service going away). */
    fun close() {
        handler.removeCallbacks(idleClose)
        panel.hide()
        onPick = null
    }

    fun reset() {
        close()
        consumedUps.clear()
    }

    private fun choose(target: SymChooserTarget) {
        val pick = onPick
        close()
        pick?.invoke(target)
    }

    private companion object {
        const val TAG = "PhysiBoardSymChooser"
        const val TITLE = "Open a Sym page: press its letter"
        const val COLUMNS = 2
        const val ROW_DP = 40
        const val IDLE_CLOSE_MS = 10_000L
    }
}
