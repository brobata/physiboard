package brobata.physiboard.ime.actions

import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import brobata.physiboard.core.strip.StripTheme

/**
 * The skin-tone chooser: one row of an emoji's six forms (no tone, then the five tones), each
 * labelled with the key that picks it. spec: expansion-clipboard-pickers-launcher.md SS4.7. A
 * transient [BottomOverlay] like the Sym pages; it decides nothing, it draws what the caller
 * (`KeyboardSession`, which owns `:core:actions`' `SkinToneChooser` rules) hands it and reports
 * a tap. It closes itself only through the caller.
 *
 * The accent chooser (layers-sym-alt.md SS8.4, [VariationChooserController]) draws through the
 * same row: its choices are labelled 1 to 9 then 0 ([show]'s `digitOf`), in a smaller glyph so
 * ten of them fit across.
 */
internal class SkinTonePanelController(service: InputMethodService) {

    private val panel = BottomOverlay(service, TAG)

    val isShown: Boolean get() = panel.isShown

    /**
     * Shows [forms] with [keyLabels] under them (the letter printed with each form's digit, or
     * null when no key picks it), above the strip by [aboveBottomPx]. [digitOf] is the digit
     * labelling the form at an index (the skin tones count from 0); [glyphSp] the forms' size.
     */
    fun show(
        forms: List<String>,
        keyLabels: List<String?>,
        theme: StripTheme,
        aboveBottomPx: Int,
        onPick: (String) -> Unit,
        onClose: () -> Unit,
        digitOf: (Int) -> Int = { it },
        glyphSp: Float = GLYPH_SP,
    ) {
        if (panel.isShown) panel.hide()
        val context = panel.overlayContext
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.background)
            val pad = panel.dp(6)
            setPadding(pad, pad, pad, pad)
        }
        forms.forEachIndexed { index, form ->
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(theme.suggestion)
                    setStroke(panel.dp(1), theme.divider)
                    cornerRadius = panel.dp(6).toFloat()
                }
                setOnClickListener { onPick(form) }
            }
            cell.addView(
                TextView(context).apply {
                    text = form
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setTextColor(theme.textAndIcons)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, glyphSp)
                },
            )
            val digit = digitOf(index)
            val label = keyLabels.getOrNull(index)?.let { "$digit · $it" } ?: digit.toString()
            cell.addView(
                TextView(context).apply {
                    text = label
                    gravity = Gravity.CENTER
                    setTextColor(theme.textAndIcons)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, LABEL_SP)
                },
            )
            row.addView(cell, LinearLayout.LayoutParams(0, panel.dp(CELL_HEIGHT_DP), 1f).apply { if (index > 0) marginStart = panel.dp(4) })
        }
        row.addView(
            TextView(context).apply {
                text = "✕"
                gravity = Gravity.CENTER
                setTextColor(theme.textAndIcons)
                background = GradientDrawable().apply {
                    setColor(theme.button)
                    cornerRadius = panel.dp(6).toFloat()
                }
                setOnClickListener { onClose() }
            },
            LinearLayout.LayoutParams(panel.dp(36), panel.dp(32)).apply { marginStart = panel.dp(6) },
        )
        panel.show(row, heightPx = null, bottomMarginPx = aboveBottomPx)
    }

    fun hide() {
        panel.hide()
    }

    private companion object {
        const val TAG = "PhysiBoardSkinTones"
        const val GLYPH_SP = 28f
        const val LABEL_SP = 11f
        const val CELL_HEIGHT_DP = 64
    }
}
