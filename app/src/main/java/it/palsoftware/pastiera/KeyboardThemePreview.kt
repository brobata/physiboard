package it.palsoftware.pastiera

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.inputmethod.StatusBarController
import it.palsoftware.pastiera.inputmethod.aospkeyboard.AospKeyboardView
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardLayoutTemplates
import it.palsoftware.pastiera.inputmethod.aospkeyboard.SoftwareKeyboardSymLabels
import it.palsoftware.pastiera.inputmethod.statusbar.StatusBarButtonRegistry
import it.palsoftware.pastiera.inputmethod.suggestions.ui.FullSuggestionsBar
import it.palsoftware.pastiera.inputmethod.ui.LedStatusView
import it.palsoftware.pastiera.inputmethod.ui.VariationBarView

private const val HARDWARE_PREVIEW_MAX_CHROME_SCALE = 1.6f
private const val HARDWARE_PREVIEW_EXTRA_HEIGHT_DP = 12f
private const val SUGGESTIONS_PREVIEW_BASE_HEIGHT_DP = 36f
private const val VARIATIONS_PREVIEW_BASE_HEIGHT_DP = 55f
private const val SOFTWARE_PREVIEW_TALL_SCREEN_HEIGHT_FRACTION = 0.46f
private const val SOFTWARE_PREVIEW_SQUARE_SCREEN_HEIGHT_FRACTION = 0.78f
private const val SOFTWARE_PREVIEW_SQUARE_SCREEN_RATIO = 1.25f
private const val SOFTWARE_PREVIEW_MIN_HEIGHT_DP = 360f
private const val SOFTWARE_PREVIEW_TALL_MAX_HEIGHT_DP = 520f
private const val SOFTWARE_PREVIEW_SQUARE_MAX_HEIGHT_DP = 500f
private const val SOFTWARE_PREVIEW_KEYBOARD_BOTTOM_PADDING_DP = 12f
private const val SOFTWARE_PREVIEW_LED_HEIGHT_DP = 6.5f

@Composable
internal fun HardwareKeyboardThemePreview(theme: KeyboardThemePreset) {
    val previewHeightDp = hardwareKeyboardPreviewHeightDp(theme)
    val viewportHeightDp = hardwareKeyboardPreviewViewportHeightDp()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(viewportHeightDp.dp)
            .clipToBounds()
            .background(Color(theme.background)),
        contentAlignment = Alignment.BottomCenter
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeightDp.dp),
            factory = { context -> createHardwareKeyboardThemePreviewView(context, theme) },
            update = { view -> updateHardwareKeyboardThemePreviewView(view, theme) }
        )
    }
}

@Composable
internal fun VirtualKeyboardThemePreview(
    theme: KeyboardThemePreset,
    viewportScale: Float
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val viewportHeightDp = softwareKeyboardPreviewViewportHeightDp(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp
    ) * viewportScale.coerceIn(
        SettingsManager.KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MIN,
        SettingsManager.KEYBOARD_THEME_PREVIEW_VIEWPORT_SCALE_MAX
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(viewportHeightDp.dp)
            .clipToBounds()
            .background(Color(theme.background)),
        contentAlignment = Alignment.BottomCenter
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportHeightDp.dp),
            factory = { viewContext -> createVirtualKeyboardThemePreviewView(viewContext, theme) },
            update = { view -> updateVirtualKeyboardThemePreviewView(view, theme) }
        )
    }
}

private fun createHardwareKeyboardThemePreviewView(
    context: Context,
    theme: KeyboardThemePreset
): LinearLayout {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(theme.background)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    val suggestionsBar = FullSuggestionsBar(context).apply {
        themeOverride = theme.toKeyboardThemeColors()
    }
    root.addView(suggestionsBar.ensureView())
    suggestionsBar.showPreview(listOf("Werk", "Team", "Park"))

    val variationBar = VariationBarView(context, buttonRegistry = StatusBarButtonRegistry()).apply {
        themeOverride = theme.toKeyboardThemeColors()
    }
    root.addView(variationBar.ensureView())
    variationBar.showVariations(hardwarePreviewVariationsSnapshot(), inputConnection = null)

    val leds = LedStatusView(context).apply {
        themeOverride = theme.toKeyboardThemeColors()
    }
    root.addView(leds.ensureView())
    leds.update(hardwarePreviewLedSnapshot())

    root.setTag(R.id.keyboard_theme_preview_suggestions_bar, suggestionsBar)
    root.setTag(R.id.keyboard_theme_preview_variation_bar, variationBar)
    root.setTag(R.id.keyboard_theme_preview_led_view, leds)
    return root
}

private fun updateHardwareKeyboardThemePreviewView(view: android.view.View, theme: KeyboardThemePreset) {
    view.setBackgroundColor(theme.background)
    val colors = theme.toKeyboardThemeColors()
    (view.getTag(R.id.keyboard_theme_preview_suggestions_bar) as? FullSuggestionsBar)?.apply {
        themeOverride = colors
        showPreview(listOf("Werk", "Team", "Park"))
    }
    (view.getTag(R.id.keyboard_theme_preview_variation_bar) as? VariationBarView)?.apply {
        themeOverride = colors
        resetVariationsState()
        showVariations(hardwarePreviewVariationsSnapshot(), inputConnection = null)
    }
    (view.getTag(R.id.keyboard_theme_preview_led_view) as? LedStatusView)?.apply {
        themeOverride = colors
        update(hardwarePreviewLedSnapshot())
    }
}

private fun createVirtualKeyboardThemePreviewView(
    context: Context,
    theme: KeyboardThemePreset
): android.view.View {
    val colors = theme.toKeyboardThemeColors()
    val previewHeightPx = virtualKeyboardPreviewHeightPx(context, theme)
    val frame = BottomAnchoredKeyboardPreviewFrame(context).apply {
        setBackgroundColor(theme.background)
        desiredPreviewHeightPx = previewHeightPx
    }
    val root = AdditiveVerticalKeyboardPreviewLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(theme.background)
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            previewHeightPx
        )
    }
    frame.contentRoot = root

    val suggestionsBar = FullSuggestionsBar(context).apply {
        themeOverride = colors
        requireDictionaryForSuggestions = false
    }
    root.addView(suggestionsBar.ensureView())
    suggestionsBar.showPreview(listOf("Werk", "Team", "Park"))

    val variationBar = VariationBarView(context, buttonRegistry = StatusBarButtonRegistry()).apply {
        themeOverride = colors
        forceVariationAreaVisible = true
    }
    root.addView(variationBar.ensureView())
    showVirtualPreviewVariations(variationBar)

    val keyboardView = AospKeyboardView(context).apply {
        layoutName = SettingsManager.getKeyboardLayout(context)
        layoutStyle = softwareKeyboardPreviewLayoutStyle(context)
        includeNumberRow = SettingsManager.getSoftwareKeyboardNumberRowEnabled(context)
        shifted = false
        spacebarLabel = "space"
        themeOverride = theme.toAospThemeOverride()
        applyVirtualKeyboardSymPreviewState(context)
    }
    val keyboardContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            0,
            0,
            0,
            dpToPx(context, SOFTWARE_PREVIEW_KEYBOARD_BOTTOM_PADDING_DP)
        )
        setBackgroundColor(theme.background)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            virtualKeyboardSurfaceHeightPx(context, theme)
        )
        addView(
            keyboardView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }
    root.keyboardSurface = keyboardContainer

    val leds = LedStatusView(context).apply {
        themeOverride = colors
    }
    root.addView(keyboardContainer)
    if (theme.showLeds) {
        root.addView(leds.ensureView())
        leds.update(virtualPreviewLedSnapshot())
    }

    root.setTag(R.id.keyboard_theme_preview_suggestions_bar, suggestionsBar)
    root.setTag(R.id.keyboard_theme_preview_variation_bar, variationBar)
    root.setTag(R.id.keyboard_theme_preview_aosp_keyboard, keyboardView)
    root.setTag(R.id.keyboard_theme_preview_led_view, leds)
    frame.addView(
        root,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            previewHeightPx
        )
    )
    return frame
}

private fun updateVirtualKeyboardThemePreviewView(view: android.view.View, theme: KeyboardThemePreset) {
    val frame = view as? BottomAnchoredKeyboardPreviewFrame
    val root = (frame?.contentRoot ?: view) as? LinearLayout ?: return
    val colors = theme.toKeyboardThemeColors()
    val previewHeightPx = virtualKeyboardPreviewHeightPx(root.context, theme)
    frame?.setBackgroundColor(theme.background)
    frame?.desiredPreviewHeightPx = previewHeightPx
    root.setBackgroundColor(theme.background)
    root.layoutParams = (root.layoutParams as? FrameLayout.LayoutParams)?.apply {
        width = ViewGroup.LayoutParams.MATCH_PARENT
        height = previewHeightPx
    } ?: FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        previewHeightPx
    )
    (root.getTag(R.id.keyboard_theme_preview_suggestions_bar) as? FullSuggestionsBar)?.apply {
        themeOverride = colors
        requireDictionaryForSuggestions = false
        showPreview(listOf("Werk", "Team", "Park"))
    }
    (root.getTag(R.id.keyboard_theme_preview_variation_bar) as? VariationBarView)?.apply {
        themeOverride = colors
        forceVariationAreaVisible = true
        resetVariationsState()
        showVirtualPreviewVariations(this)
    }
    (root.getTag(R.id.keyboard_theme_preview_aosp_keyboard) as? AospKeyboardView)?.apply {
        layoutName = SettingsManager.getKeyboardLayout(context)
        layoutStyle = softwareKeyboardPreviewLayoutStyle(context)
        includeNumberRow = SettingsManager.getSoftwareKeyboardNumberRowEnabled(context)
        themeOverride = theme.toAospThemeOverride()
        applyVirtualKeyboardSymPreviewState(context)
        ((root as? AdditiveVerticalKeyboardPreviewLayout)?.keyboardSurface?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            params.height = virtualKeyboardSurfaceHeightPx(root.context, theme)
            params.weight = 0f
            (root as? AdditiveVerticalKeyboardPreviewLayout)?.keyboardSurface?.layoutParams = params
        }
        layoutParams = (layoutParams as? LinearLayout.LayoutParams)?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = 0
            weight = 1f
        } ?: LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    }
    (root.getTag(R.id.keyboard_theme_preview_led_view) as? LedStatusView)?.apply {
        themeOverride = colors
        val ledView = ensureView()
        if (theme.showLeds) {
            if (ledView.parent !== root) {
                root.addView(ledView)
            }
            update(virtualPreviewLedSnapshot())
        } else if (ledView.parent === root) {
            root.removeView(ledView)
        }
    }
}

private class BottomAnchoredKeyboardPreviewFrame(context: Context) : FrameLayout(context) {
    var contentRoot: android.view.View? = null
        set(value) {
            field = value
            requestLayout()
        }

    var desiredPreviewHeightPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    init {
        clipChildren = true
        clipToPadding = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val frameHeight = when (heightMode) {
            MeasureSpec.UNSPECIFIED -> desiredPreviewHeightPx
            else -> heightSize
        }
        val childWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0)
        val childHeight = desiredPreviewHeightPx.coerceAtLeast(0)

        contentRoot?.measure(
            MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY)
        )

        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(frameHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = contentRoot ?: return
        val childLeft = paddingLeft
        val childBottom = height - paddingBottom
        val childTop = childBottom - child.measuredHeight
        child.layout(
            childLeft,
            childTop,
            childLeft + child.measuredWidth,
            childBottom
        )
    }
}

private class AdditiveVerticalKeyboardPreviewLayout(context: Context) : LinearLayout(context) {
    var keyboardSurface: android.view.View? = null
        set(value) {
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val surface = keyboardSurface
        if (surface == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        var totalChildHeight = 0
        var maxWidth = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == android.view.View.GONE) continue
            measureChildWithMargins(
                child,
                widthMeasureSpec,
                0,
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                0
            )
            val params = child.layoutParams as ViewGroup.MarginLayoutParams
            totalChildHeight += child.measuredHeight + params.topMargin + params.bottomMargin
            maxWidth = maxOf(maxWidth, child.measuredWidth + params.leftMargin + params.rightMargin)
        }

        setMeasuredDimension(
            resolveSize(maxWidth + paddingLeft + paddingRight, widthMeasureSpec),
            paddingTop + paddingBottom + totalChildHeight
        )
    }
}

private fun AospKeyboardView.applyVirtualKeyboardSymPreviewState(context: Context) {
    val page = SettingsManager.getPreferences(context).getInt("current_sym_page", 0)
    symPageActive = page in 1..2
    if (page !in 1..2) {
        symPageLabels = emptyMap()
        symPageTextLabels = emptyMap()
        return
    }
    val mappings = when (page) {
        1 -> SettingsManager.getSymMappings(context).takeIf { it.isNotEmpty() }
            ?: KeyMappingLoader.loadSymKeyMappings(context.assets)
        2 -> SettingsManager.getSymMappingsPage2(context).takeIf { it.isNotEmpty() }
            ?: KeyMappingLoader.loadSymKeyMappingsPage2(context.assets)
        else -> emptyMap()
    }
    val layout = SettingsManager.getKeyboardLayout(context)
    val style = softwareKeyboardPreviewLayoutStyle(context)
    symPageLabels = mappings
    symPageTextLabels = SoftwareKeyboardSymLabels.buildContentByChar(
        page = page,
        rows = SoftwareKeyboardLayoutTemplates.rowTemplateFor(layout, style),
        symMappings = mappings
    ).mapKeys { (char, _) -> char.toString() }
}

private fun softwareKeyboardPreviewLayoutStyle(context: Context): AospKeyboardView.SoftwareLayoutStyle =
    when (SettingsManager.getSoftwareKeyboardLayoutStyle(context)) {
        SettingsManager.SoftwareKeyboardLayoutStyle.COMPACT -> AospKeyboardView.SoftwareLayoutStyle.COMPACT
        SettingsManager.SoftwareKeyboardLayoutStyle.EXTENDED_ISO -> AospKeyboardView.SoftwareLayoutStyle.EXTENDED_ISO
        SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ANSI -> AospKeyboardView.SoftwareLayoutStyle.FULL_ANSI
        SettingsManager.SoftwareKeyboardLayoutStyle.FULL_ISO -> AospKeyboardView.SoftwareLayoutStyle.FULL_ISO
    }

private fun hardwareKeyboardPreviewHeightDp(theme: KeyboardThemePreset): Float =
    suggestionsPreviewHeightDp(theme) + variationsPreviewHeightDp(theme) + HARDWARE_PREVIEW_EXTRA_HEIGHT_DP

private fun hardwareKeyboardPreviewViewportHeightDp(): Float =
    SUGGESTIONS_PREVIEW_BASE_HEIGHT_DP * HARDWARE_PREVIEW_MAX_CHROME_SCALE +
        VARIATIONS_PREVIEW_BASE_HEIGHT_DP * HARDWARE_PREVIEW_MAX_CHROME_SCALE +
        HARDWARE_PREVIEW_EXTRA_HEIGHT_DP

private fun softwareKeyboardPreviewViewportHeightDp(
    screenWidthDp: Int,
    screenHeightDp: Int
): Float {
    val squarishScreen = screenHeightDp <= screenWidthDp * SOFTWARE_PREVIEW_SQUARE_SCREEN_RATIO
    val targetHeight = screenHeightDp * if (squarishScreen) {
        SOFTWARE_PREVIEW_SQUARE_SCREEN_HEIGHT_FRACTION
    } else {
        SOFTWARE_PREVIEW_TALL_SCREEN_HEIGHT_FRACTION
    }
    val maximumHeight = if (squarishScreen) {
        SOFTWARE_PREVIEW_SQUARE_MAX_HEIGHT_DP
    } else {
        SOFTWARE_PREVIEW_TALL_MAX_HEIGHT_DP
    }
    return targetHeight.coerceIn(SOFTWARE_PREVIEW_MIN_HEIGHT_DP, maximumHeight)
}

private fun virtualKeyboardPreviewHeightPx(
    context: Context,
    theme: KeyboardThemePreset
): Int {
    return dpToPx(context, suggestionsPreviewHeightDp(theme)) +
        dpToPx(context, variationsPreviewHeightDp(theme)) +
        virtualKeyboardSurfaceHeightPx(context, theme) +
        if (theme.showLeds) dpToPx(context, SOFTWARE_PREVIEW_LED_HEIGHT_DP) else 0
}

private fun virtualKeyboardSurfaceHeightPx(
    context: Context,
    theme: KeyboardThemePreset
): Int {
    val keyboardHeight = AospKeyboardView(context).apply {
        layoutName = SettingsManager.getKeyboardLayout(context)
        layoutStyle = softwareKeyboardPreviewLayoutStyle(context)
        includeNumberRow = SettingsManager.getSoftwareKeyboardNumberRowEnabled(context)
        themeOverride = theme.toAospThemeOverride()
    }.preferredKeyboardHeightPx()
    return keyboardHeight + dpToPx(context, SOFTWARE_PREVIEW_KEYBOARD_BOTTOM_PADDING_DP)
}

private fun suggestionsPreviewHeightDp(theme: KeyboardThemePreset): Float =
    SUGGESTIONS_PREVIEW_BASE_HEIGHT_DP * theme.suggestionsHeightScale.coerceIn(0.65f, 1.6f)

private fun variationsPreviewHeightDp(theme: KeyboardThemePreset): Float =
    VARIATIONS_PREVIEW_BASE_HEIGHT_DP * theme.variationsHeightScale.coerceIn(0.65f, 1.6f)

private fun hardwarePreviewVariationsSnapshot(): StatusBarController.StatusSnapshot =
    StatusBarController.StatusSnapshot(
        capsLockEnabled = false,
        shiftPhysicallyPressed = false,
        shiftOneShot = false,
        ctrlLatchActive = true,
        ctrlPhysicallyPressed = false,
        ctrlOneShot = false,
        ctrlLatchFromNavMode = true,
        altLatchActive = false,
        altPhysicallyPressed = false,
        altOneShot = false,
        symPage = 1,
        clipboardCount = 2,
        variations = listOf("@", "\"", ":", "!", "?", ",", ".")
    )

private fun hardwarePreviewLedSnapshot(): StatusBarController.StatusSnapshot =
    hardwarePreviewVariationsSnapshot().copy(
        shiftPhysicallyPressed = true,
        symPage = 2,
        variations = emptyList()
    )

private fun showVirtualPreviewVariations(variationBar: VariationBarView) {
    variationBar.showVariations(
        StatusBarController.StatusSnapshot(
            capsLockEnabled = false,
            shiftPhysicallyPressed = false,
            shiftOneShot = false,
            ctrlLatchActive = false,
            ctrlPhysicallyPressed = false,
            ctrlOneShot = false,
            ctrlLatchFromNavMode = false,
            altLatchActive = false,
            altPhysicallyPressed = false,
            altOneShot = false,
            symPage = 0,
            clipboardCount = 2,
            variations = listOf("@", "\"", ":", "!", "?", ",", ".")
        ),
        inputConnection = null
    )
}

private fun virtualPreviewLedSnapshot(): StatusBarController.StatusSnapshot =
    StatusBarController.StatusSnapshot(
        capsLockEnabled = false,
        shiftPhysicallyPressed = true,
        shiftOneShot = false,
        ctrlLatchActive = false,
        ctrlPhysicallyPressed = false,
        ctrlOneShot = false,
        ctrlLatchFromNavMode = false,
        altLatchActive = false,
        altPhysicallyPressed = false,
        altOneShot = false,
        symPage = 0
    )

private fun dpToPx(context: Context, dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        context.resources.displayMetrics
    ).toInt()
}
