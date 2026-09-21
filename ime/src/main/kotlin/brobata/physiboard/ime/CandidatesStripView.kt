package brobata.physiboard.ime

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The milestone-3 candidates strip: three plain slots showing `:core:text`'s ranked suggestions,
 * left to right; tapping one accepts it through [KeyboardPipeline.onAcceptSuggestion], never by
 * writing to the editor itself. spec: status-bar.md SS5.1 describes the eventual themed three-slot
 * row (left/center/right mapped from the add-word candidate and the ranked list); this view is
 * deliberately the "milestone-3 strip, not the final one" the task calls for: plain views, no
 * theme, no buttons, no LEDs, no add-word slot. It is built fresh by
 * [PhysiBoardInputMethodService.onCreateCandidatesView] and never touched from the typing path in
 * [KeyboardSession.onKeyEvent] other than by calling [update] after the fact, so a slow strip
 * redraw can never delay a keystroke reaching the app.
 */
internal class CandidatesStripView(context: Context) : LinearLayout(context) {

    /** Set by the caller; invoked with the tapped word's exact text. */
    var onSuggestionTapped: ((String) -> Unit)? = null

    private val slots: List<TextView> = List(SLOT_COUNT) { buildSlot() }

    init {
        orientation = HORIZONTAL
        slots.forEach { addView(it) }
    }

    private fun buildSlot(): TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        setPadding(SLOT_PADDING_PX, SLOT_PADDING_PX, SLOT_PADDING_PX, SLOT_PADDING_PX)
        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener {
            val word = tag as? String ?: return@setOnClickListener
            onSuggestionTapped?.invoke(word)
        }
    }

    /** spec: autocorrect-suggestions.md SS3, "up to three suggestions"; slot mapping beyond plain left-to-right order is the themed strip's job (status-bar.md SS5.1), out of scope here. */
    fun update(words: List<String>) {
        slots.forEachIndexed { index, slot ->
            val word = words.getOrNull(index)
            slot.text = word.orEmpty()
            slot.tag = word
            slot.isClickable = word != null
            slot.visibility = View.VISIBLE
        }
    }

    private companion object {
        const val SLOT_COUNT = 3
        const val SLOT_PADDING_PX = 24
    }
}
