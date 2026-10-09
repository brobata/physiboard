package brobata.physiboard.ime.skin

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.design.DesignMotion
import brobata.physiboard.design.DesignTokens
import brobata.physiboard.design.DesignTokens.Alpha
import brobata.physiboard.design.DesignTokens.Radius
import brobata.physiboard.design.PhysiFonts
import brobata.physiboard.design.R as DesignR
import java.util.Locale

/**
 * The terminal skin for the keyboard's panels (docs/design/design-system.md, "Panels"): the same
 * type, hairlines, corners, press and close affordances the settings app wears, painted in the
 * user's keyboard theme ([StripTheme], status-bar.md SS9.4: Background, Keys, Buttons, Key
 * outlines, Text and icons, Accent).
 *
 * It decides nothing about what a panel holds or does; every controller builds the same views it
 * always did and asks this class only how they look. One instance per panel build.
 */
internal class PanelSkin(val context: Context, val theme: StripTheme) {

    private val density = context.resources.displayMetrics.density

    fun dp(value: Int): Int = (value * density).toInt()
    fun dp(value: Float): Float = value * density

    fun face(face: PhysiFonts.Face): Typeface = PhysiFonts.get(context, face)

    /** Secondary text: the theme's one text colour, quieter. */
    val mutedText: Int get() = withAlpha(theme.textAndIcons, (Alpha.MUTED_TEXT * 255).toInt())

    // -----------------------------------------------------------------------------------------
    // Surfaces
    // -----------------------------------------------------------------------------------------

    /** A panel's own background: the theme's Background, with a 1 dp Key-outline hairline along its top edge. */
    fun panelBackground(): Drawable {
        val layers = LayerDrawable(arrayOf(ColorDrawable(theme.divider), ColorDrawable(theme.background)))
        layers.setLayerInset(1, 0, dp(DesignTokens.BORDER_DP), 0, 0)
        return layers
    }

    /**
     * A keycap: [fill] with a 1 dp [stroke], [radiusDp] corners; pressed, an accent wash over the
     * fill and an accent outline, so a tap is seen on any theme.
     */
    fun keyDrawable(fill: Int = theme.suggestion, stroke: Int = theme.divider, radiusDp: Int = Radius.KEY): Drawable {
        val pressed = rounded(blend(fill, theme.accent, Alpha.PRESSED_WASH), theme.accent, radiusDp)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), rounded(fill, stroke, radiusDp))
            setExitFadeDuration(PRESS_FADE_MS)
        }
    }

    /** A chrome key (pencil, globe, close, mode and tool buttons): the theme's Buttons colour. */
    fun buttonDrawable(radiusDp: Int = Radius.KEY): Drawable = keyDrawable(fill = theme.button, radiusDp = radiusDp)

    /** A borderless grid cell (an emoji, a kaomoji, a symbol): nothing at rest, an accent wash while pressed. */
    fun cellDrawable(): Drawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(withAlpha(theme.accent, Alpha.PRESSED_WASH), null, Radius.KEY))
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        setExitFadeDuration(PRESS_FADE_MS)
    }

    /** A selected tab or a pinned card: an accent wash and an accent outline. */
    fun selectedDrawable(fill: Int = theme.background, radiusDp: Int = Radius.KEY): Drawable =
        rounded(blend(fill, theme.accent, Alpha.SELECTED_WASH), theme.accent, radiusDp)

    /** An outlined text field: Keys fill, Key-outline hairline, the accent once it takes keys. */
    fun fieldDrawable(active: Boolean): Drawable =
        rounded(theme.suggestion, if (active) theme.accent else theme.divider, Radius.FIELD)

    fun rounded(fill: Int, stroke: Int?, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(DesignTokens.BORDER_DP), stroke)
    }

    // -----------------------------------------------------------------------------------------
    // Text
    // -----------------------------------------------------------------------------------------

    /** A line of chrome text in mono: labels, tabs, chips, buttons. */
    fun label(text: CharSequence, sp: Float = DesignTokens.Type.LABEL_SP, face: PhysiFonts.Face = PhysiFonts.Face.MONO_MEDIUM, color: Int = theme.textAndIcons): TextView =
        TextView(context).apply {
            this.text = text
            typeface = face(face)
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            includeFontPadding = false
        }

    /** Text a person reads through (a clip, a status line): Inter. */
    fun reading(text: CharSequence, sp: Float = DesignTokens.Type.READING_SP, color: Int = theme.textAndIcons): TextView =
        label(text, sp, PhysiFonts.Face.SANS, color).apply { includeFontPadding = true; setLineSpacing(0f, 1.15f) }

    /**
     * A panel's title as a shell comment, `# clipboard history`: the `#` in the accent, the words
     * lower-cased in the muted text colour. Read aloud as the plain title, marked a heading.
     */
    fun comment(title: String): TextView =
        label("", DesignTokens.Type.LABEL_SP, PhysiFonts.Face.MONO_MEDIUM, mutedText).apply {
            isAccessibilityHeading = true
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setComment(this, title)
        }

    /** Changes a [comment]'s title in place (the clipboard's private wording). */
    fun setComment(view: TextView, title: String) {
        view.text = SpannableStringBuilder("# ").append(title.lowercase(Locale.getDefault())).apply {
            setSpan(ForegroundColorSpan(theme.accent), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        view.contentDescription = title
    }

    /** A text button the way the settings app draws one: the label in the accent inside a 1 dp accent outline. */
    fun textButton(text: String, onClick: () -> Unit): TextView = label(text, DesignTokens.Type.LABEL_SP, PhysiFonts.Face.MONO_MEDIUM, theme.accent).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        background = keyDrawable(fill = theme.background, stroke = theme.accent)
        setPadding(dp(DesignTokens.Space.S + 2), dp(5), dp(DesignTokens.Space.S + 2), dp(5))
        setOnClickListener { onClick() }
        DesignMotion.pressable(this)
    }

    // -----------------------------------------------------------------------------------------
    // Controls
    // -----------------------------------------------------------------------------------------

    /** An icon from the shared family, centred on a chrome key; [description] is what TalkBack reads. */
    fun iconButton(icon: Int, description: String, onClick: () -> Unit): View = FrameLayout(context).apply {
        background = buttonDrawable()
        contentDescription = description
        addView(
            ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(theme.textAndIcons)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            FrameLayout.LayoutParams(dp(ICON_DP), dp(ICON_DP), Gravity.CENTER),
        )
        setOnClickListener { onClick() }
        DesignMotion.pressable(this)
    }

    /** Every panel's close button: the same icon, key and size everywhere (36 by 32 dp). */
    fun closeButton(onClose: () -> Unit): View = iconButton(DesignR.drawable.pb_ic_close, "Close", onClose)

    /** A text chip (the GIF page's quick searches): mono, a keycap outline, pressable. */
    fun chip(text: String, onClick: () -> Unit): TextView = label(text, DesignTokens.Type.LABEL_SP).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        background = keyDrawable()
        setPadding(dp(DesignTokens.Space.M), 0, dp(DesignTokens.Space.M), 0)
        setOnClickListener { onClick() }
        DesignMotion.pressable(this)
    }

    /**
     * A search field as a prompt (the settings app's `$ search settings_`): a `$` in the accent,
     * then [field] in mono with its hint lower-cased and ending in `_`, on an outlined field.
     * Returns the row holding both; the field keeps every listener and flag its caller set.
     */
    fun promptField(field: EditText, hint: String): LinearLayout {
        field.typeface = face(PhysiFonts.Face.MONO)
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, DesignTokens.Type.BODY_SP)
        field.setTextColor(theme.textAndIcons)
        field.setHintTextColor(mutedText)
        field.hint = promptHint(hint)
        field.background = null
        field.setPadding(0, dp(6), dp(8), dp(6))
        field.highlightColor = withAlpha(theme.accent, 0x66)
        field.textCursorDrawable?.mutate()?.setTint(theme.accent)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = fieldDrawable(active = false)
            setPadding(dp(10), 0, 0, 0)
            addView(label("$", DesignTokens.Type.BODY_SP, PhysiFonts.Face.MONO_BOLD, theme.accent).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
            addView(field, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Marks a [promptField] row as taking keys (accent outline) or not. */
    fun setPromptActive(row: View?, active: Boolean) {
        row?.background = fieldDrawable(active)
    }

    fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    companion object {
        const val ICON_DP = 18
        const val CLOSE_WIDTH_DP = 36
        const val CLOSE_HEIGHT_DP = 32
        private const val PRESS_FADE_MS = 120

        /** A search hint as the prompt's placeholder: "Search KLIPY" is `search klipy_`. */
        fun promptHint(hint: String): String = hint.lowercase(Locale.getDefault()).trimEnd('.', '…', ' ') + "_"

        /** [over] laid over [base] at [alpha] (0 to 255), as one opaque colour. */
        fun blend(base: Int, over: Int, alpha: Int): Int {
            val a = alpha / 255f
            fun mix(b: Int, o: Int) = (b + (o - b) * a).toInt().coerceIn(0, 255)
            return Color.argb(
                Color.alpha(base).coerceAtLeast(alpha),
                mix(Color.red(base), Color.red(over)),
                mix(Color.green(base), Color.green(over)),
                mix(Color.blue(base), Color.blue(over)),
            )
        }

        /** Whether the system is in dark mode: the theme-less surfaces (trackpad hint, popups) follow it. */
        fun isNight(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        /** The design scheme for the theme-less surfaces. */
        fun scheme(context: Context): DesignTokens.Scheme = DesignTokens.scheme(isNight(context))
    }
}
