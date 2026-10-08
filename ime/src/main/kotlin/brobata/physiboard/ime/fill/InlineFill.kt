package brobata.physiboard.ime.fill

import android.annotation.SuppressLint
import android.content.Context
import android.util.Size
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlineContentView
import android.widget.inline.InlinePresentationSpec
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import brobata.physiboard.core.strip.StripTheme

/**
 * A password manager's inline suggestions for the field being typed in. spec: layers-sym-alt.md
 * SS4.7.
 *
 * The request describes the chips the Fill page draws (their size and colours, in the style
 * format password managers read from Jetpack's autofill library). A response replaces what the
 * field had; an empty one, or the field finishing, clears it: suggestions belong to one field.
 * The suggestions themselves are opaque views the password manager draws and fills from; this
 * class never sees a login or a password.
 */
internal class InlineFill {

    /** The current field's suggestions, pinned ones (the manager's own icon or menu chip) first. */
    var suggestions: List<InlineSuggestion> = emptyList()
        private set

    val hasSuggestions: Boolean get() = suggestions.isNotEmpty()

    /** The autofill hints the manager attached ("password", "username", "smsOTPCode"...), for the code-field check. */
    val hints: List<String>
        get() = suggestions.flatMap { suggestion -> runCatching { suggestion.info.autofillHints?.toList() }.getOrNull().orEmpty() }

    /**
     * What the Fill page asks a password manager for. The style setters are public methods the
     * library's builders inherit from a base class it marks restricted, which lint reads as a
     * call into the library's internals; they are the documented way to build this style.
     */
    @SuppressLint("RestrictedApi")
    fun request(context: Context, theme: StripTheme, maxWidthPx: Int): InlineSuggestionsRequest {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()
        val style = InlineSuggestionUi.newStyleBuilder()
            .setSingleIconChipStyle(ViewStyle.Builder().setBackgroundColor(theme.suggestion).setPadding(dp(8), 0, dp(8), 0).build())
            .setChipStyle(ViewStyle.Builder().setBackgroundColor(theme.suggestion).setPadding(dp(10), 0, dp(10), 0).build())
            .setTitleStyle(TextViewStyle.Builder().setTextColor(theme.textAndIcons).setTextSize(15f).build())
            .setSubtitleStyle(TextViewStyle.Builder().setTextColor(theme.textAndIcons).setTextSize(12f).build())
            .setStartIconStyle(ImageViewStyle.Builder().setLayoutMargin(0, 0, dp(6), 0).build())
            .setEndIconStyle(ImageViewStyle.Builder().setLayoutMargin(dp(6), 0, 0, 0).build())
            .build()
        val styles = UiVersions.newStylesBuilder().addStyle(style).build()
        val spec = InlinePresentationSpec.Builder(Size(dp(CHIP_MIN_WIDTH_DP), dp(CHIP_HEIGHT_DP)), Size(maxWidthPx.coerceAtLeast(dp(CHIP_MIN_WIDTH_DP)), dp(CHIP_HEIGHT_DP)))
            .setStyle(styles)
            .build()
        return InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(MAX_SUGGESTIONS).build()
    }

    /** A response arrived for the current field; returns true when there is something to show. */
    fun onResponse(response: InlineSuggestionsResponse): Boolean {
        val all = response.inlineSuggestions
        suggestions = all.filter { it.info.isPinned } + all.filterNot { it.info.isPinned }
        return suggestions.isNotEmpty()
    }

    fun clear() {
        suggestions = emptyList()
    }

    /**
     * Draws [suggestion] as a chip for a window made from [context]; [onView] gets the view, or
     * nothing when the manager could not draw it. Asynchronous: the manager renders it.
     */
    fun inflate(context: Context, suggestion: InlineSuggestion, onView: (InlineContentView) -> Unit) {
        val density = context.resources.displayMetrics.density
        val wrap = Size(ViewGroup.LayoutParams.WRAP_CONTENT, (CHIP_HEIGHT_DP * density).toInt())
        val fixed = Size((CHIP_FALLBACK_WIDTH_DP * density).toInt(), (CHIP_HEIGHT_DP * density).toInt())
        val deliver: (InlineContentView?) -> Unit = { view -> if (view != null) onView(view) }
        val inflated = runCatching { suggestion.inflate(context, wrap, context.mainExecutor, deliver) }
        if (inflated.isFailure) runCatching { suggestion.inflate(context, fixed, context.mainExecutor, deliver) }
    }

    companion object {
        const val CHIP_HEIGHT_DP = 40
        const val CHIP_MIN_WIDTH_DP = 40
        const val CHIP_FALLBACK_WIDTH_DP = 160

        /** Enough for the logins a site usually has, plus the manager's own chip. */
        const val MAX_SUGGESTIONS = 6
    }
}
