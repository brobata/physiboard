package brobata.physiboard.ime.actions

import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.design.DesignMotion
import brobata.physiboard.design.DesignTokens
import brobata.physiboard.design.PhysiFonts
import brobata.physiboard.ime.skin.PanelSkin

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
        // Opened again while up (the next long press): swapped in place, with a fade.
        val replacing = panel.isShown
        if (replacing) panel.hide(animate = false)
        val context = panel.overlayContext
        val skin = PanelSkin(context, theme)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = skin.panelBackground()
            setPadding(panel.dp(6), panel.dp(6), panel.dp(6), panel.dp(6))
        }
        forms.forEachIndexed { index, form ->
            val digit = digitOf(index)
            val keyLabel = keyLabels.getOrNull(index)
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = skin.keyDrawable()
                contentDescription = if (keyLabel != null) "$form, $digit or $keyLabel" else "$form, $digit"
                setOnClickListener { onPick(form) }
                DesignMotion.pressable(this)
            }
            cell.addView(
                TextView(context).apply {
                    text = form
                    gravity = Gravity.CENTER
                    maxLines = 1
                    setTextColor(theme.textAndIcons)
                    typeface = skin.face(PhysiFonts.Face.SANS)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, glyphSp)
                },
            )
            // The digit and its key, as a Sym key prints its letter: mono, quiet.
            val label = keyLabel?.let { "$digit · $it" } ?: digit.toString()
            cell.addView(
                skin.label(label, DesignTokens.Type.KEY_LETTER_SP + 1, PhysiFonts.Face.MONO_MEDIUM, skin.mutedText).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, panel.dp(2), 0, 0)
                },
            )
            row.addView(cell, LinearLayout.LayoutParams(0, panel.dp(CELL_HEIGHT_DP), 1f).apply { if (index > 0) marginStart = panel.dp(4) })
        }
        row.addView(
            skin.closeButton { onClose() },
            LinearLayout.LayoutParams(panel.dp(PanelSkin.CLOSE_WIDTH_DP), panel.dp(PanelSkin.CLOSE_HEIGHT_DP)).apply { marginStart = panel.dp(6) },
        )
        panel.show(row, heightPx = null, bottomMarginPx = aboveBottomPx, enter = if (replacing) DesignMotion.Enter.FADE else DesignMotion.Enter.SPRING)
    }

    fun hide() {
        panel.hide()
    }

    private companion object {
        const val TAG = "PhysiBoardSkinTones"
        const val GLYPH_SP = 28f
        const val CELL_HEIGHT_DP = 64
    }
}
