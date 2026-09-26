package brobata.physiboard.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import brobata.physiboard.core.strip.BacklightNudge
import brobata.physiboard.core.strip.BacklightNudgeVisibility
import brobata.physiboard.core.strip.ClipboardBadge
import brobata.physiboard.core.strip.LanguageLabel
import brobata.physiboard.core.strip.LedLevel
import brobata.physiboard.core.strip.LedRow
import brobata.physiboard.core.strip.MicrophoneLevel
import brobata.physiboard.core.strip.QuickActions
import brobata.physiboard.core.strip.QuickActionsGeometry
import brobata.physiboard.core.strip.Slot
import brobata.physiboard.core.strip.SlotActionButton
import brobata.physiboard.core.strip.SlotKind
import brobata.physiboard.core.strip.SlotPosition
import brobata.physiboard.core.strip.SlotTextSizeSp
import brobata.physiboard.core.strip.StripButton
import brobata.physiboard.core.strip.StripFootprint
import brobata.physiboard.core.strip.StripGeometry
import brobata.physiboard.core.strip.StripModel
import brobata.physiboard.core.strip.StripSide
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.core.strip.SuggestionAccessibility
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
    /** spec SS6.4: the quick-actions row's own vertical padding is derived from the bar height, in dp (the constructor's other geometry is already in px). */
    private val barHeightDp: Int,
    private val listener: Listener,
) : FrameLayout(context) {

    /** What the session hears. Every call arrives from a real touch on the strip, already guarded here. */
    interface Listener {
        fun onSlotTapped(slot: Slot)

        /** spec SS5.3: long-pressing a suggestion; [buttons] is already [SuggestionRowRules.actionModeButtons]'s answer for this slot. */
        fun onSlotLongPressed(position: SlotPosition, slot: Slot)
        fun onSlotActionButtonTapped(position: SlotPosition, slot: Slot, action: SlotActionButton)
        fun onButtonTapped(button: StripButton)
        fun onButtonLongPressed(button: StripButton)

        /** spec SS6.4: one of the overlay's nine mirrored buttons (the close button is [onQuickActionsClosed]). */
        fun onQuickActionTapped(button: StripButton)
        fun onQuickActionsClosed()

        /** spec SS10: the pill or dot tapped (open Smart Backlight settings) or the pill's "✕" dismissed. */
        fun onBacklightNudgeTapped()
        fun onBacklightNudgeDismissed()
    }

    /** The collapsible root. spec SS3.4: hidden means "collapses to zero height", never a hidden window. */
    private val bar = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipToPadding = true
        clipChildren = true
    }

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * density).toInt()

    private val rowFrame = FrameLayout(context)
    private val slotsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    /** spec SS5.3: one cell per slot, its text swapped for the "eye"/"trash" action row while that slot is in action mode. */
    private inner class SlotCell(val position: SlotPosition) {
        var slot: Slot = Slot.EMPTY
        val text: TextView = buildSlotView()
        val eyeButton: TextView = buildActionModeButton("👁")
        val trashButton: TextView = buildActionModeButton("🗑")
        val actionRow: LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = GONE
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val root: FrameLayout = FrameLayout(context).apply {
            addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(actionRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }

        init {
            text.setOnClickListener {
                if (actionModePosition != null) exitActionMode()
                val current = slot
                if (!current.isTappable) return@setOnClickListener
                runCatching { listener.onSlotTapped(current) }.onFailure { error -> Log.e(TAG, "slot tap crashed", error) }
            }
            text.setOnLongClickListener {
                val current = slot
                if (!current.isTappable) return@setOnLongClickListener false
                runCatching { listener.onSlotLongPressed(position, current) }.onFailure { error -> Log.e(TAG, "slot long press crashed", error) }
                true
            }
            eyeButton.setOnClickListener {
                runCatching { listener.onSlotActionButtonTapped(position, slot, SlotActionButton.HIDE_SUGGESTION) }.onFailure { error -> Log.e(TAG, "hide-suggestion tap crashed", error) }
            }
            trashButton.setOnClickListener {
                runCatching { listener.onSlotActionButtonTapped(position, slot, SlotActionButton.DELETE_FROM_PERSONAL_DICTIONARY) }.onFailure { error -> Log.e(TAG, "delete-suggestion tap crashed", error) }
            }
        }

        /** spec SS5.3: "its text is replaced by one or two icon buttons of equal width". */
        fun showActionButtons(buttons: List<SlotActionButton>) {
            actionRow.removeAllViews()
            buttons.forEachIndexed { index, action ->
                val button = if (action == SlotActionButton.HIDE_SUGGESTION) eyeButton else trashButton
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                if (index > 0) params.marginStart = dp(4)
                actionRow.addView(button, params)
            }
            text.visibility = GONE
            actionRow.visibility = VISIBLE
        }

        fun hideActionButtons() {
            actionRow.visibility = GONE
            text.visibility = VISIBLE
        }
    }

    private val slotCells: List<SlotCell> = SlotPosition.entries.map { SlotCell(it) }
    private var actionModePosition: SlotPosition? = null
    private val fullWidthSlot: TextView = buildSlotView().apply {
        setOnClickListener {
            if (actionModePosition != null) exitActionMode()
            val slot = tag as? Slot ?: return@setOnClickListener
            if (!slot.isTappable) return@setOnClickListener
            runCatching { listener.onSlotTapped(slot) }.onFailure { error -> Log.e(TAG, "slot tap crashed", error) }
        }
        // spec SS5.3's "add substitution" dialog on a long press of the add-word slot has no owning
        // screen yet in 3.0 (SPEC GAP, out of this task's scope: SS5.3 covers a suggestion's long
        // press only); left silent rather than guessed at.
    }
    private val leftGroup = buildButtonGroup(Gravity.START)
    private val rightGroup = buildButtonGroup(Gravity.END)
    private val buttons: Map<StripButton, ButtonView> =
        StripButton.entries.filter { it != StripButton.NONE }.associateWith { ButtonView(it) }

    // spec SS6.4: the hamburger's quick-actions overlay, "a full frame, hidden until opened", laid
    // out inside this same window rather than a second `TYPE_APPLICATION_OVERLAY` (unlike the Sym
    // pages, its content never grows past the bar's own height, so it needs no window of its own).
    private val quickActionsClose: TextView = buildOverlayButton("✕") { listener.onQuickActionsClosed() }
    private val quickActionButtons: Map<StripButton, TextView> =
        QuickActions.ITEMS.associateWith { button -> buildOverlayButton(glyphFor(button)) { listener.onQuickActionTapped(button) } }
    private val hamburgerOverlay: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = GONE
        isClickable = true
        val verticalPadding = dp(QuickActionsGeometry.verticalPaddingDp(barHeightDp))
        setPadding(0, verticalPadding, 0, verticalPadding)
    }
    private var quickActionsOpenState = false

    // spec SS10: the smart-backlight-paused pill and dot, right-anchored just inside the right
    // buttons.
    private val backlightPillLabel = TextView(context).apply {
        text = "⚡ " + BacklightNudge.PILL_TEXT.replaceFirst(", ", " — ")
        setTextColor(Color.BLACK)
        typeface = android.graphics.Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
    }
    private val backlightPillClose = TextView(context).apply {
        text = "✕"
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(6), 0, 0, 0)
        setOnClickListener { runCatching { listener.onBacklightNudgeDismissed() }.onFailure { error -> Log.e(TAG, "backlight nudge dismiss crashed", error) } }
    }
    private val backlightPill: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        visibility = GONE
        background = GradientDrawable().apply { setColor(BACKLIGHT_AMBER); cornerRadius = dp(8).toFloat() }
        setPadding(dp(9), dp(3), dp(6), dp(3))
        addView(backlightPillLabel)
        addView(backlightPillClose)
        setOnClickListener { runCatching { listener.onBacklightNudgeTapped() }.onFailure { error -> Log.e(TAG, "backlight nudge tap crashed", error) } }
    }
    private val backlightDot: View = View(context).apply {
        visibility = GONE
        background = GradientDrawable().apply { setColor(BACKLIGHT_AMBER); shape = GradientDrawable.OVAL }
        setOnClickListener { runCatching { listener.onBacklightNudgeTapped() }.onFailure { error -> Log.e(TAG, "backlight nudge tap crashed", error) } }
    }

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

    // spec SS5.6: the suggestion row's live announcements. `:ime` sets the two settings; the delay
    // and the "any new update cancels a pending announcement" timer live here, next to the views
    // they hide and re-expose.
    var liveAnnouncementsEnabled: Boolean = false
    var announcementDelayMs: Long = SuggestionAccessibility.DEFAULT_DELAY_MS

    /** spec SS5.6: the current input style's layout id, for the language button's state description. */
    var languageLayoutName: String = ""
    private var lastAnnouncedSuggestions: String? = null
    private var pendingAnnouncement: String? = null
    private val reExposeSuggestionsRunnable = Runnable { reExposeSuggestions() }

    init {
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        rowFrame.addView(slotsRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        rowFrame.addView(leftGroup, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.START))
        rowFrame.addView(rightGroup, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT, Gravity.END))
        // spec SS4's layer order: slots and buttons first, then the hamburger overlay "full frame,
        // hidden until opened", then the backlight-paused pill and dot on top of everything.
        rowFrame.addView(hamburgerOverlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        hamburgerOverlay.addView(quickActionsClose, quickActionsOverlayParams(first = true))
        QuickActions.ITEMS.forEach { button -> hamburgerOverlay.addView(quickActionButtons.getValue(button), quickActionsOverlayParams(first = false)) }
        rowFrame.addView(backlightPill, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
        rowFrame.addView(backlightDot, LayoutParams(dp(BACKLIGHT_DOT_DP), dp(BACKLIGHT_DOT_DP), Gravity.END or Gravity.CENTER_VERTICAL))
        bar.addView(rowFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, geometry.barHeightPx))
        ledViews.forEach(ledRow::addView)
        bar.addView(ledRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        slotCells.forEach { slotsRow.addView(it.root, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        slotsRow.addView(fullWidthSlot, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, SLOT_COUNT.toFloat()))
        applyBackground()
    }

    private fun quickActionsOverlayParams(first: Boolean): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { if (!first) marginStart = geometry.gapPx }

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
            // spec SS5.3: "Action mode also ends when the slot contents change".
            if (actionModePosition != null && model.row != lastModel?.row) exitActionMode()
            lastModel = model
            bar.visibility = if (model.footprint == StripFootprint.SHOWN) VISIBLE else GONE
            renderButtons(model)
            renderRow(model)
            renderLeds(model.leds)
        }.onFailure { error -> Log.e(TAG, "strip render crashed; keeping the last drawn strip", error) }
    }

    // -----------------------------------------------------------------------------------------
    // Action mode. spec SS5.3.
    // -----------------------------------------------------------------------------------------

    /** Enters action mode on [position] with [buttons] (already [SuggestionRowRules.actionModeButtons]'s answer); a caller passing an empty list is a no-op (SS17: "long press on an empty slot"). */
    fun enterActionMode(position: SlotPosition, buttons: List<SlotActionButton>) {
        if (buttons.isEmpty()) return
        runCatching {
            actionModePosition = position
            slotCells[position.ordinal].showActionButtons(buttons)
        }.onFailure { error -> Log.e(TAG, "enter action mode crashed", error) }
    }

    /** spec SS5.3: "Action mode also ends when... the field finishes, when the window hides, and before any tap on any slot." */
    fun exitActionMode() {
        val position = actionModePosition ?: return
        runCatching {
            actionModePosition = null
            slotCells[position.ordinal].hideActionButtons()
        }.onFailure { error -> Log.e(TAG, "exit action mode crashed", error) }
    }

    // -----------------------------------------------------------------------------------------
    // The hamburger's quick-actions overlay. spec SS6.4.
    // -----------------------------------------------------------------------------------------

    val quickActionsOpen: Boolean get() = quickActionsOpenState

    fun setQuickActionsOpen(open: Boolean) {
        runCatching {
            if (quickActionsOpenState == open) return
            quickActionsOpenState = open
            hamburgerOverlay.visibility = if (open) VISIBLE else GONE
            if (open) renderQuickActionsContent()
        }.onFailure { error -> Log.e(TAG, "quick actions overlay crashed", error) }
    }

    /** spec SS6.4: "The overlay carries the clipboard count, microphone state and language text like the main buttons." */
    private fun renderQuickActionsContent() {
        val model = lastModel ?: return
        quickActionButtons[StripButton.LANGUAGE]?.text = model.languageLabel
        quickActionButtons[StripButton.CLIPBOARD]?.text = glyphFor(StripButton.CLIPBOARD) + (ClipboardBadge.text(model.clipboardCount)?.let { " $it" } ?: "")
        quickActionButtons[StripButton.MICROPHONE]?.let { mic ->
            mic.background = pressable(fill = if (model.dictationActive) MicrophoneLevel.INITIAL_RECORDING_COLOR else theme.button, corner = geometry.slotCornerPx)
        }
    }

    // -----------------------------------------------------------------------------------------
    // The smart-backlight-paused nudge. spec SS10.
    // -----------------------------------------------------------------------------------------

    fun renderBacklightNudge(visibility: BacklightNudgeVisibility) {
        runCatching {
            backlightPill.visibility = if (visibility == BacklightNudgeVisibility.PILL) VISIBLE else GONE
            backlightDot.visibility = if (visibility == BacklightNudgeVisibility.DOT) VISIBLE else GONE
            val model = lastModel ?: return
            val rightCount = model.rightButtons.size
            val inset = geometry.sideInsetPx(rightCount, model.isEdgeButton(StripSide.RIGHT, rightCount - 1, roundedCorners)) + dp(4)
            (backlightPill.layoutParams as? LayoutParams)?.let { it.rightMargin = inset; backlightPill.layoutParams = it }
            (backlightDot.layoutParams as? LayoutParams)?.let { it.rightMargin = inset; backlightDot.layoutParams = it }
        }.onFailure { error -> Log.e(TAG, "backlight nudge render crashed", error) }
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
            val view = slotCells[position.ordinal].text
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
            SuggestionRow.Hidden -> {
                slotsRow.visibility = GONE
                scheduleSuggestionAnnouncement(emptyList())
            }
            is SuggestionRow.AddWordOnly -> {
                slotsRow.visibility = VISIBLE
                slotCells.forEach { it.root.visibility = GONE }
                fullWidthSlot.visibility = VISIBLE
                bindSlot(fullWidthSlot, Slot(row.word, SlotKind.ADD_WORD))
                scheduleSuggestionAnnouncement(listOf(row.word))
            }
            is SuggestionRow.Slots -> {
                slotsRow.visibility = VISIBLE
                fullWidthSlot.visibility = GONE
                slotCells.forEach { cell ->
                    cell.root.visibility = VISIBLE
                    cell.slot = row[cell.position]
                    bindSlot(cell.text, cell.slot)
                }
                scheduleSuggestionAnnouncement(row.texts)
            }
        }
    }

    /**
     * spec SS5.6: "hides its descendants from accessibility for [announcementDelayMs]... then
     * re-exposes them and announces the non-blank slot texts... if they differ from the last
     * announcement. Any new update cancels a pending announcement."
     */
    private fun scheduleSuggestionAnnouncement(slotTexts: List<String>) {
        slotsRow.removeCallbacks(reExposeSuggestionsRunnable)
        if (!SuggestionAccessibility.shouldSchedule(liveAnnouncementsEnabled, slotTexts)) {
            setSuggestionsAccessibilityHidden(false)
            pendingAnnouncement = null
            return
        }
        val text = SuggestionAccessibility.announcementText(slotTexts)
        if (!SuggestionAccessibility.isNewAnnouncement(text, lastAnnouncedSuggestions)) {
            setSuggestionsAccessibilityHidden(false)
            return
        }
        pendingAnnouncement = text
        setSuggestionsAccessibilityHidden(true)
        slotsRow.postDelayed(reExposeSuggestionsRunnable, SuggestionAccessibility.delayMs(announcementDelayMs))
    }

    private fun setSuggestionsAccessibilityHidden(hidden: Boolean) {
        val mode = if (hidden) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_YES
        slotCells.forEach { it.root.importantForAccessibility = mode }
        fullWidthSlot.importantForAccessibility = mode
    }

    private fun reExposeSuggestions() {
        setSuggestionsAccessibilityHidden(false)
        pendingAnnouncement?.let { text ->
            lastAnnouncedSuggestions = text
            announceForAccessibility(text)
        }
        pendingAnnouncement = null
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
        buttons[StripButton.LANGUAGE]?.setStateDescription(SuggestionAccessibility.languageStateDescription(model.languageLabel, languageLayoutName))
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
    // Touch-screen-awake. spec: trackpad-caret-nav.md SS6.
    // -----------------------------------------------------------------------------------------

    /**
     * spec: trackpad-caret-nav.md SS6: invoked on "every touch down (action down only; moves and
     * ups do nothing) on the keyboard's chrome layout, that is the strip and everything drawn in
     * the keyboard window". A settable callback rather than a [Listener] method, because this is
     * not something the strip was tapped *for*: it fires for every down anywhere in the chrome,
     * including ones a child button goes on to handle, and no strip decision reads it.
     */
    var onChromeTouchDown: (() -> Unit)? = null

    /** spec SS6: any held pulse is released "when the chrome layout is detached from its window". */
    var onChromeDetached: (() -> Unit)? = null

    /**
     * spec SS6: the pulse is taken at the root of the chrome, so a down a child view goes on to
     * consume still counts as the user touching the keyboard. The event itself is never altered.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            runCatching { onChromeTouchDown?.invoke() }.onFailure { error -> Log.e(TAG, "touch-awake pulse crashed", error) }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        runCatching { onChromeDetached?.invoke() }.onFailure { error -> Log.e(TAG, "touch-awake release crashed", error) }
        super.onDetachedFromWindow()
    }

    // -----------------------------------------------------------------------------------------
    // Window insets and the rounded bottom corners. spec SS4.
    // -----------------------------------------------------------------------------------------

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        runCatching {
            val navBars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            val cutout = insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
            val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
            val side = if (roundedCorners) bars else android.graphics.Insets.NONE
            bar.setPadding(side.left, 0, side.right, bars.bottom)
            logInsetsOnce(navBars.bottom, cutout.bottom, bars.bottom)
            if (roundedCorners) {
                val reported = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius
                bottomCornerRadiusPx = reported?.takeIf { it > 0 } ?: geometry.bottomCornerFallbackPx
                applyBackground()
            }
        }.onFailure { error -> Log.e(TAG, "inset padding crashed", error) }
        return super.onApplyWindowInsets(insets)
    }

    /** spec SS11: `ime_overlay_debug_logging`, "logs the navigation, cutout and applied bottom padding once per distinct combination." */
    var overlayDebugLoggingEnabled: Boolean = false
    private var lastLoggedInsetsCombination: Triple<Int, Int, Int>? = null

    private fun logInsetsOnce(navigationBottomPx: Int, cutoutBottomPx: Int, appliedBottomPaddingPx: Int) {
        if (!overlayDebugLoggingEnabled) return
        val combination = Triple(navigationBottomPx, cutoutBottomPx, appliedBottomPaddingPx)
        if (combination == lastLoggedInsetsCombination) return
        lastLoggedInsetsCombination = combination
        Log.i(TAG, "insets: navigation=$navigationBottomPx cutout=$cutoutBottomPx appliedBottomPadding=$appliedBottomPaddingPx")
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

    /**
     * spec SS5.1: slot fill, 1 dp divider border, chrome-rounded corners, auto-sized centered
     * text; SS5.3: the pressed colour is the accent. Carries no click behaviour of its own: every
     * caller ([SlotCell] and [fullWidthSlot]) attaches its own listeners afterward, since a plain
     * suggestion slot and the add-word slot commit differently and only the former has action mode.
     */
    private fun buildSlotView(): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTextColor(theme.textAndIcons)
        setAutoSizeTextTypeUniformWithConfiguration(slotTextSize.minSp, slotTextSize.maxSp, 1, TypedValue.COMPLEX_UNIT_SP)
        setPadding(geometry.slotPaddingHorizontalPx, geometry.slotPaddingVerticalPx, geometry.slotPaddingHorizontalPx, geometry.slotPaddingVerticalPx)
        background = pressable(fill = theme.suggestion, corner = geometry.slotCornerPx)
    }

    /** spec SS5.3: the action-mode "eye"/"trash" buttons, "equal width, 4 dp padding, 7 dp corners". */
    private fun buildActionModeButton(glyph: String): TextView = TextView(context).apply {
        text = glyph
        gravity = Gravity.CENTER
        setTextColor(theme.textAndIcons)
        background = pressable(fill = theme.button, corner = dp(7).toFloat())
    }

    /** spec SS6.4: one of the quick-actions overlay's equal-width buttons, chrome-rounded like the row's own cells. */
    private fun buildOverlayButton(glyph: String, onTap: () -> Unit): TextView = TextView(context).apply {
        text = glyph
        gravity = Gravity.CENTER
        setTextColor(theme.textAndIcons)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, GLYPH_TEXT_SP)
        background = pressable(fill = theme.button, corner = geometry.slotCornerPx)
        setOnClickListener { onTap() }
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

        /** spec SS5.6: "The language button is never a live region; its state description reads 'Language X, layout Y'." */
        fun setStateDescription(text: String) {
            stateDescription = text
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

        /** spec SS10: the backlight-paused pill and dot, amber 0xFFFFB300. */
        val BACKLIGHT_AMBER = 0xFFFFB300.toInt()
        const val BACKLIGHT_DOT_DP = 9
    }
}
