package brobata.physiboard.ime.actions

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
import brobata.physiboard.design.DesignMotion
import brobata.physiboard.design.DesignTokens
import brobata.physiboard.design.PhysiFonts
import brobata.physiboard.ime.skin.PanelSkin

/**
 * The Sym page chooser: a small transient panel listing every Sym page with the key that opens
 * it. spec: layers-sym-alt.md SS5.10. The rules (the rows, what each key does) are `:core:keys`'
 * [SymPageChooser]; this class draws the rows, keeps the open chooser's key bookkeeping, and
 * closes itself after a pick, a dismissal, or [IDLE_CLOSE_MS] without a key.
 */
internal class SymPageChooserController(service: InputMethodService, private val handler: Handler) {

    private val panel = BottomOverlay(service, TAG)
    private var onPick: ((SymChooserTarget) -> Unit)? = null
    private var listed: Set<SymChooserTarget> = emptySet()
    private val consumedUps = HashSet<KeyId>()
    private val idleClose = Runnable { close() }

    val isOpen: Boolean get() = panel.isShown

    fun show(entries: List<SymChooserEntry>, theme: StripTheme, aboveBottomPx: Int, pick: (SymChooserTarget) -> Unit) {
        close()
        onPick = pick
        listed = entries.map { it.target }.toSet()
        val context = panel.overlayContext
        val skin = PanelSkin(context, theme)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = skin.panelBackground()
            setPadding(panel.dp(8), panel.dp(6), panel.dp(6), panel.dp(8))
        }
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(skin.comment(TITLE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(skin.closeButton { close() }, LinearLayout.LayoutParams(panel.dp(PanelSkin.CLOSE_WIDTH_DP), panel.dp(PanelSkin.CLOSE_HEIGHT_DP)))
        column.addView(header)
        val grid = GridLayout(context).apply { columnCount = COLUMNS; setPadding(0, panel.dp(3), 0, 0) }
        entries.forEachIndexed { index, entry ->
            // The key that opens the page, as a keycap, then the page's name: a row of the settings
            // app's key-and-label lists.
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(panel.dp(6), 0, panel.dp(6), 0)
                // A page that is off for the cycle still opens from here; it is drawn dimmer so
                // the chooser also shows what the Sym taps will step through.
                alpha = if (entry.inCycle) 1f else 0.6f
                background = skin.keyDrawable()
                contentDescription = "${entry.label}, ${entry.target.letter}"
                setOnClickListener { choose(entry.target) }
                DesignMotion.pressable(this)
            }
            cell.addView(
                skin.label(entry.target.letter.toString(), DesignTokens.Type.LABEL_SP, PhysiFonts.Face.MONO_BOLD, theme.accent).apply {
                    gravity = Gravity.CENTER
                    background = skin.rounded(theme.button, theme.divider, DesignTokens.Radius.KEY)
                },
                LinearLayout.LayoutParams(panel.dp(26), panel.dp(26)).apply { marginEnd = panel.dp(10) },
            )
            cell.addView(
                skin.label(entry.label, DesignTokens.Type.BODY_SP).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
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
        return when (val outcome = SymPageChooser.onKeyDown(key, isRepeat = repeatCount > 0 && key in consumedUps, listed = listed)) {
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
