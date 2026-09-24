package brobata.physiboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import brobata.physiboard.core.strip.ClipboardBadge
import brobata.physiboard.core.strip.LanguageLabel
import brobata.physiboard.core.strip.LedLevel
import brobata.physiboard.core.strip.LedRow
import brobata.physiboard.core.strip.MicrophoneLevel
import brobata.physiboard.core.strip.Slot
import brobata.physiboard.core.strip.SlotKind
import brobata.physiboard.core.strip.SlotPosition
import brobata.physiboard.core.strip.SlotTextSizeSp
import brobata.physiboard.core.strip.StripButton
import brobata.physiboard.core.strip.StripFootprint
import brobata.physiboard.core.strip.StripGeometry
import brobata.physiboard.core.strip.StripModel
import brobata.physiboard.core.strip.StripSide
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.core.strip.SuggestionRow
import brobata.physiboard.core.strip.SuggestionRowRules

/**
 * The status bar: PhysiBoard's candidates view, drawn from a [StripModel] and nothing else.
 * spec: status-bar.md SS2 (geometry), SS4 (layout: suggestion row, LED row, window-inset
 * padding, rounded bottom corners), SS5 (slots), SS6.2 (button drawing), SS7 (LEDs).
 *
 * This view decides nothing. `:core:strip` decides what each region shows; this class lays it
 * out at the Titan's pixel numbers ([StripGeometry]) and reports taps back through [Listener].
 * It sits on the typing path ([KeyboardSession] calls [render] after every keystroke), so:
 * [render] returns at once when the model is unchanged (SS1, "only redrawn when the snapshot...
 * actually changed"), every entry point Android or the session can reach is wrapped so a drawing
 * bug is logged and never thrown back into `onKeyDown`, and the button views are built once and
 * re-attached (SS6.2, "Buttons are created once and reused across refreshes").
 *
 * SPEC GAP: no icon assets exist in the 3.0 tree yet, so the glyphs are text placeholders
 * ([glyphFor]); SS6.1's named glyphs (material icons) arrive with the settings/app milestone.
 */
internal class StatusBarView(
    context: Context,
    private val geometry: StripGeometry,
    private val theme: StripTheme,
    private val roundedCorners: Boolean,
    private val slotTextSize: SlotTextSizeSp,
    private val listener: Listener,
) : FrameLayout(context) {

    /** What the session hears. Every call arrives from a real touch on the strip, already guarded here. */
    interface Listener {
        fun onSlotTapped(slot: Slot)
        fun onSlotLongPressed(slot: Slot)
        fun onButtonTapped(button: StripButton)
        fun onButtonLongPressed(button: StripButton)
    }

    /** The collapsible root. spec SS3.4: hidden means "collapses to zero height", never a hidden window. */
    private val bar = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipToPadding = true
        clipChildren = true
    }

    private val rowFrame = FrameLayout(context)
    private val slotsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val slotViews: List<TextView> = List(SLOT_COUNT) { buildSlotView() }
    private val fullWidthSlot: TextView = buildSlotView()
    private val leftGroup = buildButtonGroup(Gravity.START)
    private val rightGroup = buildButtonGroup(Gravity.END)
    private val buttons: Map<StripButton, ButtonView> =
        StripButton.entries.filter { it != StripButton.NONE }.associateWith { ButtonView(it) }

    private val ledRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, geometry.ledTopPaddingPx, 0, 0)
        visibility = GONE
    }
    private val ledViews: List<View> = List(LED_COUNT) { index ->
        View(context).apply {
            background = GradientDrawable().apply { cornerRadius = geometry.ledCornerPx.toFloat() }
            layoutParams = LinearLayout.LayoutParams(0, geometry.ledHeightPx, 1f).apply {
                if (index > 0) marginStart = geometry.ledGapPx
            }
        }
    }

    private var lastModel: StripModel? = null
    private var lastClipboardCount = 0
    private var bottomCornerRadiusPx: Int = geometry.bottomCornerFallbackPx

    init {
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        rowFrame.addView(slotsRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        rowFrame.addView(leftGroup, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START))
        rowFrame.addView(rightGroup, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END))
        bar.addView(rowFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, geometry.barHeightPx))
        ledViews.forEach(ledRow::addView)
        bar.addView(ledRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        slotViews.forEach { slotsRow.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        slotsRow.addView(fullWidthSlot, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, SLOT_COUNT.toFloat()))
        applyBackground()
    }

    // -----------------------------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------------------------

    /** spec SS1's "refresh": draw [model], or do nothing when it equals the last one drawn (SS5.5, "an update only forces the row and every slot back to fully visible"). */
    fun render(model: StripModel) {
        runCatching {
            if (model == lastModel) {
                slotsRow.alpha = 1f
                return
            }
            lastModel = model
            bar.visibility = if (model.footprint == StripFootprint.SHOWN) VISIBLE else GONE
            renderButtons(model)
            renderRow(model)
            renderLeds(model.leds)
        }.onFailure { error -> Log.e(TAG, "strip render crashed; keeping the last drawn strip", error) }
    }

    /** spec SS13: "the render cache is invalidated" when the window hides, so the next refresh draws even an unchanged snapshot. */
    fun invalidateRenderCache() {
        lastModel = null
    }

    /** spec SS12.2 step 1: the dip needs the strip "actually rendered on screen (attached, visible, non-zero size, non-empty visible rectangle)". */
    fun isRenderedOnScreen(): Boolean = runCatching {
        isAttachedToWindow && bar.visibility == VISIBLE && bar.width > 0 && bar.height > 0 && bar.getGlobalVisibleRect(Rect())
    }.getOrDefault(false)

    /** spec SS6.1: the microphone's level colour while dictation is active; the button stays red across refreshes because [renderButtons] re-applies it from the model (fixing SS17's "red indicator likely lost" quirk). */
    fun setMicrophoneLevel(levelDb: Float) {
        runCatching {
            if (lastModel?.dictationActive != true) return
            buttons[StripButton.MICROPHONE]?.setRecordingColor(MicrophoneLevel.color(levelDb.toDouble()))
        }.onFailure { error -> Log.e(TAG, "microphone level crashed", error) }
    }

    /** spec SS5.4: the pressed colour held for 160 ms on the slot a trackpad swipe or a tap committed. */
    fun flashSlot(position: SlotPosition) {
        runCatching {
            val view = slotViews[position.ordinal]
            view.isPressed = true
            view.postDelayed({ view.isPressed = false }, SuggestionRowRules.FLASH_MS)
        }.onFailure { error -> Log.e(TAG, "slot flash crashed", error) }
    }

    private fun renderRow(model: StripModel) {
        val leftCount = model.leftButtons.size
        val rightCount = model.rightButtons.size
        (slotsRow.layoutParams as LayoutParams).apply {
            leftMargin = geometry.sideInsetPx(leftCount, model.isEdgeButton(StripSide.LEFT, 0, roundedCorners))
            rightMargin = geometry.sideInsetPx(rightCount, model.isEdgeButton(StripSide.RIGHT, rightCount - 1, roundedCorners))
        }
        slotsRow.requestLayout()
        when (val row = model.row) {
            SuggestionRow.Hidden -> slotsRow.visibility = GONE
            is SuggestionRow.AddWordOnly -> {
                slotsRow.visibility = VISIBLE
                slotViews.forEach { it.visibility = GONE }
                fullWidthSlot.visibility = VISIBLE
                bindSlot(fullWidthSlot, Slot(row.word, SlotKind.ADD_WORD))
            }
            is SuggestionRow.Slots -> {
                slotsRow.visibility = VISIBLE
                fullWidthSlot.visibility = GONE
                SlotPosition.entries.forEachIndexed { index, position ->
                    val view = slotViews[index]
                    view.visibility = VISIBLE
                    bindSlot(view, row[position])
                }
            }
        }
    }

    /** spec SS5.1: text centered, one line, ellipsized; the add-word candidate drawn with a "+" after the text. */
    private fun bindSlot(view: TextView, slot: Slot) {
        view.text = if (slot.kind == SlotKind.ADD_WORD) "${slot.text} +" else slot.text
        view.tag = slot
        view.isClickable = slot.isTappable
        view.isLongClickable = slot.isTappable
        view.contentDescription = slot.text.ifEmpty { null }
        view.importantForAccessibility = if (slot.isTappable) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** spec SS6.2: re-attach the reusable buttons in the model's order and re-apply the theme; SS6.1: the language text, the clipboard badge, the microphone's recording state. */
    private fun renderButtons(model: StripModel) {
        leftGroup.removeAllViews()
        rightGroup.removeAllViews()
        attachGroup(leftGroup, StripSide.LEFT, model)
        attachGroup(rightGroup, StripSide.RIGHT, model)
        buttons[StripButton.LANGUAGE]?.setGlyph(model.languageLabel)
        buttons[StripButton.CLIPBOARD]?.setBadge(model.clipboardCount, flash = ClipboardBadge.flashes(lastClipboardCount, model.clipboardCount))
        lastClipboardCount = model.clipboardCount
        buttons[StripButton.MICROPHONE]?.let { mic ->
            if (model.dictationActive) mic.setRecordingColor(MicrophoneLevel.INITIAL_RECORDING_COLOR) else mic.applyTheme()
        }
    }

    private fun attachGroup(group: LinearLayout, side: StripSide, model: StripModel) {
        val list = if (side == StripSide.LEFT) model.leftButtons else model.rightButtons
        group.visibility = if (list.isEmpty()) GONE else VISIBLE
        list.forEachIndexed { index, button ->
            val view = buttons[button] ?: return@forEachIndexed
            (view.parent as? LinearLayout)?.removeView(view)
            val edge = model.isEdgeButton(side, index, roundedCorners)
            view.configure(edge = edge, outerCornerAtStart = side == StripSide.LEFT)
            val params = LinearLayout.LayoutParams(if (edge) geometry.edgeButtonPx else geometry.buttonPx, geometry.buttonPx)
            if (index > 0) params.marginStart = geometry.gapPx
            group.addView(view, params)
        }
    }

    /** spec SS7: six LEDs, two of them invisible placeholders, coloured from the theme. */
    private fun renderLeds(leds: LedRow?) {
        if (leds == null) {
            ledRow.visibility = GONE
            return
        }
        ledRow.visibility = VISIBLE
        leds.positions.forEachIndexed { index, level ->
            val view = ledViews[index]
            view.visibility = if (level == null) INVISIBLE else VISIBLE
            (view.background as? GradientDrawable)?.setColor(
                when (level) {
                    LedLevel.LOCKED -> theme.ledLocked
                    LedLevel.ACTIVE -> theme.ledActive
                    LedLevel.INACTIVE, null -> theme.ledInactive
                },
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Window insets and the rounded bottom corners. spec SS4.
    // -----------------------------------------------------------------------------------------

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        runCatching {
            val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            val side = if (roundedCorners) bars else android.graphics.Insets.NONE
            bar.setPadding(side.left, 0, side.right, bars.bottom)
            if (roundedCorners) {
                val reported = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius
                bottomCornerRadiusPx = reported?.takeIf { it > 0 } ?: geometry.bottomCornerFallbackPx
                applyBackground()
            }
        }.onFailure { error -> Log.e(TAG, "inset padding crashed", error) }
        return super.onApplyWindowInsets(insets)
    }

    /** spec SS4: the bar's background "rounds its two bottom corners... the top corners stay square because the bar butts against the app". */
    private fun applyBackground() {
        val radius = if (roundedCorners) bottomCornerRadiusPx.toFloat() else 0f
        bar.background = GradientDrawable().apply {
            setColor(theme.background)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
        }
        bar.clipToOutline = roundedCorners
    }

    // -----------------------------------------------------------------------------------------
    // View factories
    // -----------------------------------------------------------------------------------------

    private fun buildButtonGroup(gravity: Int): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity or Gravity.CENTER_VERTICAL
    }

    /** spec SS5.1: slot fill, 1 dp divider border, chrome-rounded corners, auto-sized centered text; SS5.3: the pressed colour is the accent. */
    private fun buildSlotView(): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTextColor(theme.textAndIcons)
        setAutoSizeTextTypeUniformWithConfiguration(slotTextSize.minSp, slotTextSize.maxSp, 1, TypedValue.COMPLEX_UNIT_SP)
        setPadding(geometry.slotPaddingHorizontalPx, geometry.slotPaddingVerticalPx, geometry.slotPaddingHorizontalPx, geometry.slotPaddingVerticalPx)
        background = pressable(fill = theme.suggestion, corner = geometry.slotCornerPx)
        setOnClickListener {
            val slot = tag as? Slot ?: return@setOnClickListener
            runCatching { listener.onSlotTapped(slot) }.onFailure { error -> Log.e(TAG, "slot tap crashed", error) }
        }
        setOnLongClickListener {
            val slot = tag as? Slot ?: return@setOnLongClickListener false
            runCatching { listener.onSlotLongPressed(slot) }.onFailure { error -> Log.e(TAG, "slot long press crashed", error) }
            true
        }
    }

    /** spec SS6.2: theme fill, accent when pressed, 1 dp divider border, key-rounded corners (the edge button's outer bottom corner at 0.9 times the button). */
    private fun pressable(fill: Int, corner: Float, outerBottomCorner: Float? = null, outerAtStart: Boolean = true): StateListDrawable {
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color)
            setStroke(geometry.borderPx, theme.divider)
            if (outerBottomCorner == null) {
                cornerRadius = corner
            } else {
                // Order: top-left, top-right, bottom-right, bottom-left, each as (x, y).
                val bl = if (outerAtStart) outerBottomCorner else corner
                val br = if (outerAtStart) corner else outerBottomCorner
                cornerRadii = floatArrayOf(corner, corner, corner, corner, br, br, bl, bl)
            }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(theme.accent))
            addState(intArrayOf(), shape(fill))
        }
    }

    /**
     * One strip button: a square of the button size (SS6.2) with a glyph, an optional badge at
     * the top-end corner (SS6.1, the clipboard count), and a red overlay for the badge flash.
     */
    private inner class ButtonView(val button: StripButton) : FrameLayout(context) {
        private val glyph = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.textAndIcons)
            text = glyphFor(button)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (button == StripButton.LANGUAGE) LANGUAGE_TEXT_SP else GLYPH_TEXT_SP)
            includeFontPadding = false
            if (button == StripButton.LANGUAGE) paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        }
        private val badge = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, BADGE_TEXT_SP)
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            visibility = GONE
        }
        private val flashOverlay = View(context).apply {
            setBackgroundColor(Color.RED)
            alpha = 0f
        }
        private var edge = false
        private var outerAtStart = true

        init {
            isClickable = true
            isLongClickable = true
            contentDescription = button.label
            addView(glyph, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(flashOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, BADGE_NUDGE_PX, BADGE_MARGIN_PX, 0) })
            setOnClickListener {
                runCatching { listener.onButtonTapped(button) }.onFailure { error -> Log.e(TAG, "${button.id} tap crashed", error) }
            }
            setOnLongClickListener {
                runCatching { listener.onButtonLongPressed(button) }.onFailure { error -> Log.e(TAG, "${button.id} long press crashed", error) }
                true
            }
            applyTheme()
        }

        fun configure(edge: Boolean, outerCornerAtStart: Boolean) {
            if (this.edge == edge && this.outerAtStart == outerCornerAtStart) return
            this.edge = edge
            this.outerAtStart = outerCornerAtStart
            applyTheme()
        }

        fun applyTheme() {
            background = pressable(
                fill = theme.button,
                corner = geometry.buttonCornerPx,
                outerBottomCorner = if (edge) geometry.edgeCornerPx.toFloat() else null,
                outerAtStart = outerAtStart,
            )
            // spec SS6.2: the edge button's glyph leans inward by half the extra width.
            val lean = if (edge) geometry.edgeExtraInsetPx / 2 else 0
            glyph.setPadding(if (outerAtStart) lean else 0, 0, if (outerAtStart) 0 else lean, 0)
        }

        fun setGlyph(text: String) {
            glyph.text = text
        }

        /** spec SS6.1: the count "hidden at zero"; a change to a different positive number flashes red, alpha 0 to 0.4 and back over 350 ms. */
        fun setBadge(count: Int, flash: Boolean) {
            val text = ClipboardBadge.text(count)
            badge.text = text
            badge.visibility = if (text == null) GONE else VISIBLE
            stateDescription = ClipboardBadge.accessibilityState(count)
            if (flash) {
                val half = ClipboardBadge.FLASH_MS / 2
                flashOverlay.animate().alpha(ClipboardBadge.FLASH_PEAK_ALPHA).setDuration(half).withEndAction {
                    flashOverlay.animate().alpha(0f).setDuration(half).start()
                }.start()
            }
        }

        /** spec SS6.1: the microphone's red background while dictation is active, pressed colour the fixed blue. */
        fun setRecordingColor(color: Int) {
            fun shape(fill: Int) = GradientDrawable().apply {
                setColor(fill)
                setStroke(geometry.borderPx, theme.divider)
                cornerRadius = geometry.buttonCornerPx
            }
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), shape(MicrophoneLevel.ACTIVE_PRESSED_COLOR))
                addState(intArrayOf(), shape(color))
            }
            stateDescription = "On"
        }
    }

    private fun glyphFor(button: StripButton): String = when (button) {
        StripButton.NONE -> ""
        StripButton.CLIPBOARD -> "📋"
        StripButton.MICROPHONE -> "🎤"
        StripButton.EMOJI -> "😀"
        StripButton.LANGUAGE -> LanguageLabel.NO_SUBTYPE
        StripButton.HAMBURGER -> "☰"
        StripButton.SETTINGS -> "⚙"
        StripButton.SYMBOLS -> "#+="
        StripButton.UNDO -> "↶"
        StripButton.REDO -> "↷"
    }

    private companion object {
        const val TAG = "PhysiBoardStrip"
        const val SLOT_COUNT = 3
        const val LED_COUNT = 6
        const val GLYPH_TEXT_SP = 22f
        const val LANGUAGE_TEXT_SP = 14f
        const val BADGE_TEXT_SP = 10f
        const val BADGE_MARGIN_PX = 4
        const val BADGE_NUDGE_PX = 4
    }
}
