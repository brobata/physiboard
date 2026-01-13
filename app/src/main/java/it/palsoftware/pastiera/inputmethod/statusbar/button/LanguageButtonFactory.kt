package it.palsoftware.pastiera.inputmethod.statusbar.button

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.UnderlineSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonCreationResult
import it.palsoftware.pastiera.inputmethod.statusbar.ButtonState
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarCallbacks
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonStyles
import kotlin.math.max

/**
 * Factory for creating the language switch button with debounce and dashed underline hint.
 */
class LanguageButtonFactory : StatusBarButtonFactory {
    
    companion object {
        private const val TAG = "LanguageButtonFactory"
        private const val DEBOUNCE_MS = 500L
        private const val RE_ENABLE_DELAY_MS = 300L
    }
    
    // Per-factory debounce state (shared across all instances if singleton)
    private var lastSwitchTime: Long = 0
    
    override fun create(context: Context, size: Int, callbacks: StatusBarCallbacks): ButtonCreationResult {
        val button = createButton(context, size)
        
        // Wrap the onClick callback with debounce logic
        callbacks.onLanguageSwitchRequested?.let { originalOnClick ->
            button.setOnClickListener {
                val now = System.currentTimeMillis()
                // Debounce: prevent rapid consecutive clicks
                if (now - lastSwitchTime < DEBOUNCE_MS) {
                    return@setOnClickListener
                }
                lastSwitchTime = now
                
                // Disable button during switch to prevent multiple simultaneous switches
                button.isEnabled = false
                button.alpha = 0.5f
                
                originalOnClick()
                
                // Re-enable button after delay
                Handler(Looper.getMainLooper()).postDelayed({
                    button.isEnabled = true
                    button.alpha = 1f
                    // Update text after language switch
                    updateLanguageText(context, button)
                }, RE_ENABLE_DELAY_MS)
            }
        }
        
        // Set up long click listener (opens settings)
        callbacks.onOpenSettings?.let { onOpenSettings ->
            button.setOnLongClickListener {
                onOpenSettings()
                true
            }
            button.isLongClickable = true
        }
        
        // Initialize with current language
        updateLanguageText(context, button)
        
        return ButtonCreationResult(view = button)
    }
    
    override fun update(view: View, state: ButtonState) {
        if (state !is ButtonState.LanguageState) return
        val button = view as? TextView ?: return
        
        applyLanguageText(button, state.languageCode)
    }
    
    /**
     * Refreshes the language text by reading the current IME subtype.
     * Call this after a language switch to update the displayed code.
     */
    fun refreshLanguageText(context: Context, view: View) {
        val button = view as? TextView ?: return
        updateLanguageText(context, button)
    }
    
    private fun createButton(context: Context, size: Int): TextView {
        return TextView(context).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            background = StatusBarButtonStyles.createButtonDrawable(size)
            isClickable = true
            isFocusable = true
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            // layoutParams will be set by VariationBarView for consistency
        }
    }
    
    private fun updateLanguageText(context: Context, button: TextView) {
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val currentSubtype = imm.currentInputMethodSubtype
            val languageCode = if (currentSubtype != null) {
                // Extract language code from locale (e.g., "en_US" -> "EN", "it_IT" -> "IT")
                val locale = currentSubtype.locale
                locale.split("_").firstOrNull()?.uppercase() ?: "??"
            } else {
                "??"
            }
            applyLanguageText(button, languageCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating language button text", e)
            applyLanguageText(button, "??")
        }
    }
    
    private fun applyLanguageText(button: TextView, languageCode: String) {
        // Clear any icons so the label stays perfectly centered.
        button.setCompoundDrawables(null, null, null, null)
        button.compoundDrawablePadding = 0
        button.gravity = Gravity.CENTER
        button.textAlignment = View.TEXT_ALIGNMENT_CENTER
        button.setPadding(0, 0, 0, 0)

        val paintCopy = TextPaint(button.paint).apply {
            textSize = button.textSize
        }
        val textWidth = paintCopy.measureText(languageCode).coerceAtLeast(1f)
        // Target 3 dashes -> 3 dash segments + 2 gaps = 5 units.
        val minDash = dpToPx(button.context, 2f).toFloat()
        val dashLength = max(minDash, textWidth / 5f)
        val gapLength = dashLength
        val dashEffect = DashPathEffect(floatArrayOf(dashLength, gapLength), 0f)

        val dottedText = SpannableString(languageCode).apply {
            setSpan(
                object : UnderlineSpan() {
                    override fun updateDrawState(tp: TextPaint) {
                        super.updateDrawState(tp)
                        tp.isUnderlineText = true
                        // Use a dashed underline to hint the long-press action.
                        tp.pathEffect = dashEffect
                    }
                },
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        button.text = dottedText
    }
    
    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
