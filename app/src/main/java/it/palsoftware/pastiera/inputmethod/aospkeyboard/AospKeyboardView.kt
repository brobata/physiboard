/*
 * Pastiera modifications for full virtual keyboard mode.
 *
 * This file derives keyboard geometry behavior from Android Open Source Project LatinIME
 * (`platform/packages/inputmethods/LatinIME`) at commit
 * 127336e9f29d69607eab55982324b210279ae8c5.
 *
 * AOSP LatinIME is licensed under the Apache License, Version 2.0. See
 * THIRD_PARTY_NOTICES.md and third_party/licenses/Apache-2.0.txt in this repository.
 */
package it.palsoftware.pastiera.inputmethod.aospkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import it.palsoftware.pastiera.R
import java.util.Locale
import kotlin.math.abs

/**
 * AOSP LatinIME alphabet key plane embedded in Pastiera.
 *
 * This intentionally keeps Pastiera's IME lifecycle/suggestions/status bars, but mirrors the
 * AOSP qwerty/qwertz/azerty key geometry from rows_*.xml and row_qwerty4.xml.
 */
class AospKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
        fun onShift()
        fun onSymbols()
        fun onCtrl()
        fun onLanguageSwitch()
        fun onCursorMove(delta: Int)
        fun onKeyPressSound(keyCode: Int)
        fun onModifierKeyDown(keyCode: Int): Boolean = false
        fun onModifierKeyUp(keyCode: Int): Boolean = false
        fun onKeyStroke(keyCode: Int, text: String): Boolean = false
        fun onSymbolLongPress(keyCode: Int): Boolean = false
    }

    data class ThemeOverride(
        val background: Int,
        val divider: Int,
        val normalKey: Int,
        val specialKey: Int,
        val textAndIcons: Int,
        val ledInactive: Int,
        val ledActive: Int,
        val ledLocked: Int,
        val accent: Int,
        val keyPopup: Int = specialKey,
        val keyPopupSelected: Int = accent,
        val keyCornerRadiusRatio: Float = 0.08f,
        val keyHeightScale: Float = 1f,
        val keyWidthScale: Float = 1f,
        val rowGapScale: Float = 1f,
        val distributeHorizontalSpacing: Boolean = true,
        val ortholinear: Boolean = false
    )

    enum class SoftwareLayoutStyle {
        COMPACT,
        EXTENDED_ISO,
        FULL_ANSI,
        FULL_ISO
    }

    private enum class LayoutFamily { QWERTY, QWERTZ, AZERTY }

    private enum class KeyType { CHAR, SHIFT, BACKSPACE, SYMBOLS, CTRL, COMMA, PERIOD, SPACE, ENTER, LANGUAGE }

    private data class KeySpec(
        val type: KeyType,
        val label: String,
        val output: String = label,
        val hint: String = "",
        val moreKeys: List<String> = emptyList(),
        val xPercent: Float,
        val widthPercent: Float,
        val visualInsetLeftPercent: Float = 0f,
        val visualInsetRightPercent: Float = 0f
    )

    private data class Key(
        val spec: KeySpec,
        val hitRect: RectF,
        val visualRect: RectF
    )

    private data class MoreKeysPanelState(
        val baseKey: Key,
        val keys: List<String>,
        val popupRectInView: RectF,
        val keyWidth: Float,
        val keyHeight: Float,
        val padding: Float,
        var selectedIndex: Int = -1
    )

    private data class PreviewPopupState(
        val label: String,
        val rect: RectF,
        val hasMoreKeys: Boolean
    )

    var listener: Listener? = null
    var layoutName: String = "qwerty"
        set(value) {
            val normalized = value.trim().lowercase(Locale.ROOT).ifBlank { "qwerty" }
            if (field == normalized) {
                return
            }
            field = normalized
            rebuildKeys(width, height)
            invalidate()
        }
    var layoutStyle: SoftwareLayoutStyle = SoftwareLayoutStyle.COMPACT
        set(value) {
            if (field == value) {
                return
            }
            field = value
            rebuildKeys(width, height)
            invalidate()
        }
    var nearestKeyTouchEnabled: Boolean = true
    var shifted: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            rebuildKeys(width, height)
            invalidate()
        }
    var shiftLocked: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var ctrlOneShot: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var ctrlLocked: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var ctrlPressed: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var ctrlPreviewActive: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var symPageActive: Boolean = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var symPreviewLabels: Map<Int, String> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var symPageLabels: Map<Int, String> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var ctrlPreviewLabels: Map<Int, String> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var ctrlPreviewIconRes: Map<Int, Int> = emptyMap()
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var spacebarLabel: String = "space"
        set(value) {
            val normalized = value.ifBlank { "space" }
            if (field == normalized) {
                return
            }
            field = normalized
            invalidate()
        }
    var symbolsLabel: String = "SYM"
        set(value) {
            val normalized = value.ifBlank { "SYM" }
            if (field == normalized) {
                return
            }
            field = normalized
            invalidate()
        }
    var symbolsIconRes: Int? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            invalidate()
        }
    var longPressTimeoutMs: Long = 500L
        set(value) {
            field = value.coerceIn(50L, 1000L)
        }
    var longPressAlternatesProvider: ((String) -> List<String>)? = null
    var themeOverride: ThemeOverride? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            rebuildKeys(width, height)
            requestLayout()
            invalidate()
        }

    private val keys = mutableListOf<Key>()
    private var pressedKey: Key? = null
    private var heldModifierKey: Key? = null
    private var heldModifierPointerId: Int = -1
    private var chordKey: Key? = null
    private var chordPointerId: Int = -1
    private var previewPopupState: PreviewPopupState? = null
    private var moreKeysPanelState: MoreKeysPanelState? = null
    private var popupOverlayDrawable: Drawable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private val longPressRunnable = Runnable { showMoreKeysOrRepeat() }
    private var spaceSwipeActive = false
    private var spaceSwipeLastX = 0f
    private var spaceLongPressArmed = false

    private val keyboardBackground = drawable(R.drawable.keyboard_background_lxx_dark)
    private val normalKeyBackground = drawable(R.drawable.btn_keyboard_key_normal_off_lxx_dark)
    private val normalKeyPressedBackground = drawable(R.drawable.btn_keyboard_key_pressed_off_lxx_dark)
    private val shiftedKeyBackground = drawable(R.drawable.btn_keyboard_key_normal_on_lxx_dark)
    private val shiftedKeyPressedBackground = drawable(R.drawable.btn_keyboard_key_pressed_on_lxx_dark)
    private val spacebarBackground = drawable(R.drawable.btn_keyboard_spacebar_normal_lxx_dark)
    private val spacebarPressedBackground = drawable(R.drawable.btn_keyboard_spacebar_pressed_lxx_dark)
    private val previewBackground = drawable(R.drawable.keyboard_key_feedback_background_lxx_dark)
    private val previewMoreBackground = drawable(R.drawable.keyboard_key_feedback_more_background_lxx_dark)
    private val moreKeysBackground = drawable(R.drawable.keyboard_popup_panel_background_lxx_dark)
    private val shiftIcon = drawable(R.drawable.shift_24)
    private val shiftFilledIcon = drawable(R.drawable.shift_filled_24)
    private val shiftLockIcon = drawable(R.drawable.shift_lock_24)
    private val backspaceIcon = drawable(R.drawable.backspace_24)
    private val returnIcon = drawable(R.drawable.keyboard_return_24)
    private val ctrlIcon = drawable(R.drawable.keyboard_control_key_24)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(238, 238, 238)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(156, 164, 172)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }

    private val gapPx = dp(2f)
    private val horizontalPaddingPx = 0
    private val verticalPaddingPx = 0
    private val preferredKeyHeightPx: Int
        get() = (dp(50f) * (themeOverride?.keyHeightScale ?: 1f).coerceIn(0.72f, 1.45f)).toInt()
    private val rowGapPx: Int
        get() = (dp(6f) * ((themeOverride?.rowGapScale ?: 0f).coerceIn(0f, 2f))).toInt()

    init {
        isClickable = true
        isFocusable = true
        setBackgroundColor(Color.BLACK)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = verticalPaddingPx * 2 + preferredKeyHeightPx * 4 + rowGapPx * 3
        val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, resolvedHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rebuildKeys(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        themeOverride?.let {
            canvas.drawColor(it.background)
        } ?: drawDrawable(canvas, keyboardBackground, RectF(0f, 0f, width.toFloat(), height.toFloat()))
        keys.forEach { key ->
            drawDrawable(canvas, backgroundFor(key), key.visualRect)
            val previewIcon = previewIconFor(key)
            if (previewIcon != null) {
                drawCenteredIcon(canvas, previewIcon, key.visualRect)
                return@forEach
            }
            val previewLabel = previewLabelFor(key)
            val label = previewLabel ?: symPageLabelFor(key) ?: displayLabel(key.spec)
            textPaint.textSize = when (key.spec.type) {
                KeyType.SPACE -> sp(12f)
                KeyType.SYMBOLS -> sp(16f)
                KeyType.ENTER, KeyType.SHIFT, KeyType.BACKSPACE, KeyType.CTRL, KeyType.LANGUAGE -> sp(23f)
                else -> sp(24f)
            }
            if (previewLabel != null && heldModifierKey?.spec?.type != KeyType.SYMBOLS) {
                textPaint.textSize = previewTextSize(previewLabel, key.visualRect)
            }
            textPaint.typeface = if (key.spec.type == KeyType.SPACE) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textPaint.color = themeOverride?.textAndIcons
                ?: if (isFunctional(key.spec.type)) Color.rgb(202, 209, 216) else Color.rgb(238, 238, 238)
            if (previewLabel == null && drawFunctionalIcon(canvas, key)) {
                return@forEach
            }
            val baselineOffset = -(textPaint.ascent() + textPaint.descent()) / 2f
            val y = if (key.spec.type == KeyType.SPACE) key.visualRect.centerY() + dp(7f) else key.visualRect.centerY() + baselineOffset
            canvas.drawText(label, key.visualRect.centerX(), y, textPaint)
            val hint = displayHint(key)
            if (previewLabel == null && hint.isNotBlank()) {
                hintPaint.textSize = sp(10f)
                hintPaint.color = themeOverride?.textAndIcons?.let { colorWithAlpha(it, 150) }
                    ?: Color.rgb(156, 164, 172)
                canvas.drawText(hint, key.visualRect.right - dp(9f), key.visualRect.top + dp(12f), hintPaint)
            }
        }
    }

    private fun drawFunctionalIcon(canvas: Canvas, key: Key): Boolean {
        val icon = when (key.spec.type) {
            KeyType.SHIFT -> when {
                shiftLocked -> shiftLockIcon
                shifted -> shiftFilledIcon
                else -> shiftIcon
            }
            KeyType.BACKSPACE -> backspaceIcon
            KeyType.ENTER -> returnIcon
            KeyType.CTRL -> ctrlIcon
            KeyType.SYMBOLS -> symbolsIconRes?.let(::drawable)
            else -> null
        } ?: return false
        drawCenteredIcon(canvas, icon, key.visualRect)
        return true
    }

    private fun drawCenteredIcon(canvas: Canvas, source: Drawable, rect: RectF) {
        val icon = source.constantState?.newDrawable()?.mutate() ?: source.mutate()
        val color = themeOverride?.textAndIcons ?: Color.rgb(202, 209, 216)
        icon.setTint(color)
        val maxSize = minOf(rect.width(), rect.height()) * 0.55f
        val intrinsicWidth = icon.intrinsicWidth.takeIf { it > 0 } ?: 24
        val intrinsicHeight = icon.intrinsicHeight.takeIf { it > 0 } ?: 24
        val scale = minOf(maxSize / intrinsicWidth, maxSize / intrinsicHeight)
        val iconWidth = intrinsicWidth * scale
        val iconHeight = intrinsicHeight * scale
        val left = (rect.centerX() - iconWidth / 2f).toInt()
        val top = (rect.centerY() - iconHeight / 2f).toInt()
        icon.setBounds(left, top, (left + iconWidth).toInt(), (top + iconHeight).toInt())
        icon.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = event.actionIndex
                val key = findKey(event.getX(pointerIndex), event.getY(pointerIndex))
                pressedKey = key
                spaceSwipeActive = false
                spaceSwipeLastX = event.x
                spaceLongPressArmed = false
                longPressTriggered = false
                invalidate()
                key?.let {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onKeyPressSound(soundKeyCodeFor(it))
                    if (it.spec.type.isHoldModifier()) {
                        heldModifierKey = it
                        heldModifierPointerId = event.getPointerId(pointerIndex)
                        listener?.onModifierKeyDown(soundKeyCodeFor(it))
                        invalidate()
                    } else {
                        if (!isModifierPreviewLayerActive()) {
                            showPreview(it)
                            handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (heldModifierKey == null) return true
                val pointerIndex = event.actionIndex
                val key = findKey(event.getX(pointerIndex), event.getY(pointerIndex)) ?: return true
                if (key.spec.type.isHoldModifier()) return true
                handler.removeCallbacks(longPressRunnable)
                chordKey = key
                chordPointerId = event.getPointerId(pointerIndex)
                pressedKey = key
                longPressTriggered = false
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener?.onKeyPressSound(soundKeyCodeFor(key))
                if (!isModifierPreviewLayerActive()) {
                    showPreview(key)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (moreKeysPanelState != null) {
                    updateMoreKeysSelection(event.x, event.y)
                    return true
                }
                val pressed = pressedKey
                if (pressed?.spec?.type == KeyType.SPACE) {
                    val step = dp(18f).toFloat()
                    val delta = event.x - spaceSwipeLastX
                    if (kotlin.math.abs(delta) >= step) {
                        handler.removeCallbacks(longPressRunnable)
                        dismissPopup()
                        spaceSwipeActive = true
                        spaceLongPressArmed = false
                        longPressTriggered = true
                        val steps = (delta / step).toInt()
                        repeat(kotlin.math.abs(steps).coerceAtMost(4)) {
                            listener?.onCursorMove(if (steps > 0) 1 else -1)
                        }
                        spaceSwipeLastX += steps * step
                    }
                    return true
                }
                val key = findKey(event.x, event.y)
                if (key != pressedKey) {
                    dismissPopup()
                    pressedKey = key
                    invalidate()
                    key?.let {
                        listener?.onKeyPressSound(soundKeyCodeFor(it))
                        if (!isModifierPreviewLayerActive()) {
                            showPreview(it)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == chordPointerId) {
                    handler.removeCallbacks(longPressRunnable)
                    val key = chordKey
                    dismissPopup()
                    chordKey = null
                    chordPointerId = -1
                    pressedKey = heldModifierKey
                    invalidate()
                    if (key != null && !longPressTriggered && !spaceSwipeActive) {
                        dispatchKey(key)
                    }
                    return true
                }
                if (pointerId == heldModifierPointerId) {
                    releaseHeldModifier()
                    return true
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                moreKeysPanelState?.let { panel ->
                    val selected = selectedMoreKey(event.x, event.y, panel)
                    dismissPopup()
                    pressedKey = null
                    invalidate()
                    if (selected != null) {
                        listener?.onText(selected)
                    }
                    return true
                }
                val key = pressedKey
                val releasedHeldModifier = key != null && key == heldModifierKey
                dismissPopup()
                val wasSpaceSwipe = spaceSwipeActive
                val wasSpaceLongPress = spaceLongPressArmed
                spaceSwipeActive = false
                spaceLongPressArmed = false
                pressedKey = null
                chordKey = null
                chordPointerId = -1
                if (releasedHeldModifier) {
                    releaseHeldModifier()
                    return true
                }
                invalidate()
                if (key?.spec?.type == KeyType.SPACE && wasSpaceLongPress && !wasSpaceSwipe) {
                    listener?.onLanguageSwitch()
                    return true
                }
                if (key != null && !longPressTriggered && !wasSpaceSwipe) {
                    dispatchKey(key)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                dismissPopup()
                spaceSwipeActive = false
                spaceLongPressArmed = false
                releaseHeldModifier()
                chordKey = null
                chordPointerId = -1
                pressedKey = null
                invalidate()
                return true
            }
        }
        return true
    }

    private fun rebuildKeys(viewWidth: Int, viewHeight: Int) {
        keys.clear()
        if (viewWidth <= 0 || viewHeight <= 0) return
        val theme = themeOverride
        val keyWidthScale = (theme?.keyWidthScale ?: 1f).coerceIn(0.72f, 1.12f)
        val totalWidthScale = if (theme?.distributeHorizontalSpacing == false) keyWidthScale else 1f
        val usableWidth = ((viewWidth - horizontalPaddingPx * 2) * totalWidthScale).toInt()
        val horizontalOffset = horizontalPaddingPx + ((viewWidth - horizontalPaddingPx * 2) - usableWidth) / 2f
        val visualWidthScale = if (theme?.distributeHorizontalSpacing == false) 1f else keyWidthScale
        val rowHeight = (viewHeight - verticalPaddingPx * 2 - rowGapPx * 3) / 4f
        rowsFor(layoutName, layoutStyle).forEachIndexed { rowIndex, row ->
            val y = verticalPaddingPx + rowIndex * (rowHeight + rowGapPx)
            row.forEach { spec ->
                val rawLeft = horizontalOffset + usableWidth * (spec.xPercent / 100f)
                val rawRight = horizontalOffset + usableWidth * ((spec.xPercent + spec.widthPercent) / 100f)
                val hit = RectF(rawLeft, y, rawRight, y + rowHeight)
                val visualInset = hit.width() * (1f - visualWidthScale) / 2f
                val visual = RectF(
                    hit.left + gapPx / 2f + visualInset + usableWidth * (spec.visualInsetLeftPercent / 100f),
                    hit.top,
                    hit.right - gapPx / 2f - visualInset - usableWidth * (spec.visualInsetRightPercent / 100f),
                    hit.bottom
                )
                keys.add(Key(spec, hit, visual))
            }
        }
    }

    private fun rowsFor(layout: String, style: SoftwareLayoutStyle): List<List<KeySpec>> {
        val family = layoutFamilyFor(layout)
        val rowStrings = rowTemplateFor(family, style)
        if (style == SoftwareLayoutStyle.FULL_ANSI || style == SoftwareLayoutStyle.FULL_ISO) {
            return fullRowsFor(rowStrings, style)
        }
        val row1Width = 100f / rowStrings[0].length
        val row1 = rowStrings[0].mapIndexed { index, ch ->
            charSpec(ch, index * row1Width, widthPercent = row1Width)
        }
        val row2Start = if (
            themeOverride?.ortholinear == true ||
            family == LayoutFamily.AZERTY ||
            style == SoftwareLayoutStyle.EXTENDED_ISO
        ) 0f else 5f
        val row2Width = if (style == SoftwareLayoutStyle.EXTENDED_ISO) {
            100f / rowStrings[1].length
        } else {
            10f
        }
        val row2 = rowStrings[1].mapIndexed { index, ch -> charSpec(ch, row2Start + index * row2Width, widthPercent = row2Width) }
        val row3CharWidth = if (style == SoftwareLayoutStyle.EXTENDED_ISO) row1Width else 10f
        val row3Start = if (style == SoftwareLayoutStyle.EXTENDED_ISO) row3CharWidth * 2f else 15f
        val row3SideKeyWidth = if (style == SoftwareLayoutStyle.EXTENDED_ISO) row3CharWidth * 2f else 15f
        val row3Chars = rowStrings[2].mapIndexed { index, ch ->
            charSpec(ch, row3Start + index * row3CharWidth, widthPercent = row3CharWidth)
        }
        val row3 = listOf(
            KeySpec(KeyType.SHIFT, "⇧", xPercent = 0f, widthPercent = row3SideKeyWidth, visualInsetRightPercent = 1f)
        ) + row3Chars + listOf(
            KeySpec(
                KeyType.BACKSPACE,
                "⌫",
                xPercent = 100f - row3SideKeyWidth,
                widthPercent = row3SideKeyWidth,
                visualInsetLeftPercent = 1f
            )
        )
        return listOf(row1, row2, row3, bottomRow(includeEnter = true))
    }

    private fun fullRowsFor(rowStrings: List<String>, style: SoftwareLayoutStyle): List<List<KeySpec>> {
        if (style == SoftwareLayoutStyle.FULL_ANSI) {
            return fullAnsiRowsFor(rowStrings)
        }
        val columns = maxOf(rowStrings[0].length + 1, rowStrings[1].length + 1, rowStrings[2].length + 2)
        val cellWidth = 100f / columns
        fun row(chars: String, reservedRightCells: Int): List<KeySpec> {
            val freeColumns = columns - reservedRightCells
            val start = if (themeOverride?.ortholinear == true || chars.length >= freeColumns) {
                0f
            } else {
                (freeColumns - chars.length) * cellWidth / 2f
            }
            return chars.mapIndexed { index, ch ->
                charSpec(ch, start + index * cellWidth, widthPercent = cellWidth)
            }
        }

        val row1 = row(rowStrings[0], reservedRightCells = 1) + KeySpec(
            KeyType.BACKSPACE,
            "⌫",
            xPercent = 100f - cellWidth,
            widthPercent = cellWidth
        )
        val row2 = row(rowStrings[1], reservedRightCells = 1) + KeySpec(
            KeyType.ENTER,
            "↵",
            output = "\n",
            xPercent = 100f - cellWidth,
            widthPercent = cellWidth
        )
        val row3Start = cellWidth
        val row3Chars = rowStrings[2].mapIndexed { index, ch ->
            charSpec(ch, row3Start + index * cellWidth, widthPercent = cellWidth)
        }
        val row3 = listOf(
            KeySpec(KeyType.SHIFT, "⇧", xPercent = 0f, widthPercent = cellWidth)
        ) + row3Chars + listOf(
            KeySpec(
                KeyType.SHIFT,
                "⇧",
                xPercent = 100f - cellWidth,
                widthPercent = cellWidth
            )
        )
        return listOf(row1, row2, row3, bottomRow(includeEnter = false))
    }

    private fun fullAnsiRowsFor(rowStrings: List<String>): List<List<KeySpec>> {
        val columns = maxOf(rowStrings[0].length + 1, rowStrings[1].length + 2, rowStrings[2].length + 3)
        val cellWidth = 100f / columns
        fun row(chars: String, reservedRightCells: Int): List<KeySpec> {
            val freeColumns = columns - reservedRightCells
            val start = if (themeOverride?.ortholinear == true || chars.length >= freeColumns) {
                0f
            } else {
                (freeColumns - chars.length) * cellWidth / 2f
            }
            return chars.mapIndexed { index, ch ->
                charSpec(ch, start + index * cellWidth, widthPercent = cellWidth)
            }
        }

        val row1 = row(rowStrings[0], reservedRightCells = 1) + KeySpec(
            KeyType.BACKSPACE,
            "⌫",
            xPercent = 100f - cellWidth,
            widthPercent = cellWidth
        )
        val row2 = row(rowStrings[1], reservedRightCells = 2) + KeySpec(
            KeyType.ENTER,
            "↵",
            output = "\n",
            xPercent = 100f - cellWidth * 2f,
            widthPercent = cellWidth * 2f
        )
        val row3Chars = rowStrings[2].mapIndexed { index, ch ->
            charSpec(ch, cellWidth + index * cellWidth, widthPercent = cellWidth)
        }
        val row3 = listOf(
            KeySpec(KeyType.SHIFT, "⇧", xPercent = 0f, widthPercent = cellWidth)
        ) + row3Chars + listOf(
            KeySpec(
                KeyType.SHIFT,
                "⇧",
                xPercent = 100f - cellWidth * 2f,
                widthPercent = cellWidth * 2f
            )
        )
        return listOf(row1, row2, row3, bottomRow(includeEnter = false))
    }

    private fun rowTemplateFor(family: LayoutFamily, style: SoftwareLayoutStyle): List<String> =
        when (style) {
            SoftwareLayoutStyle.COMPACT -> when (family) {
                LayoutFamily.QWERTY -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
                LayoutFamily.QWERTZ -> listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
                LayoutFamily.AZERTY -> listOf("azertyuiop", "qsdfghjklm", "wxcvbn'")
            }
            SoftwareLayoutStyle.EXTENDED_ISO -> when (family) {
                LayoutFamily.QWERTY -> listOf("qwertyuiop[", "asdfghjkl;'", "zxcvbnm")
                LayoutFamily.QWERTZ -> listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")
                LayoutFamily.AZERTY -> listOf("azertyuiop^", "qsdfghjklmù", "wxcvbn'")
            }
            SoftwareLayoutStyle.FULL_ANSI -> when (family) {
                LayoutFamily.QWERTY -> listOf("qwertyuiop[]", "asdfghjkl;'", "zxcvbnm,./")
                LayoutFamily.QWERTZ -> listOf("qwertzuiop[]", "asdfghjkl;'", "yxcvbnm,./")
                LayoutFamily.AZERTY -> listOf("azertyuiop[]", "qsdfghjklm'", "wxcvbn,;:!")
            }
            SoftwareLayoutStyle.FULL_ISO -> when (family) {
                LayoutFamily.QWERTY -> listOf("qwertyuiop[]", "asdfghjkl;'#", "\\zxcvbnm,./")
                LayoutFamily.QWERTZ -> listOf("qwertzuiopü+", "asdfghjklöä#", "<yxcvbnm,.-")
                LayoutFamily.AZERTY -> listOf("azertyuiop^$", "qsdfghjklmù*", "<wxcvbn,;:!")
            }
        }

    private fun layoutFamilyFor(layout: String): LayoutFamily =
        when (layout) {
            "qwertz", "german_multitap_qwertz" -> LayoutFamily.QWERTZ
            "azerty" -> LayoutFamily.AZERTY
            else -> LayoutFamily.QWERTY
        }

    private fun bottomRow(includeEnter: Boolean): List<KeySpec> {
        val row = listOf(
            KeySpec(KeyType.SYMBOLS, "SYM", xPercent = 0f, widthPercent = 12f),
            KeySpec(KeyType.CTRL, "CTRL", xPercent = 12f, widthPercent = 10f),
            KeySpec(KeyType.COMMA, ",", xPercent = 22f, widthPercent = 8f, moreKeys = listOf("'", "\"", ";", ":")),
            KeySpec(KeyType.SPACE, "space", output = " ", xPercent = 30f, widthPercent = 40f),
            KeySpec(KeyType.PERIOD, ".", xPercent = 70f, widthPercent = 8f, moreKeys = listOf("!", "?", ";", ":", "…")),
            KeySpec(KeyType.CTRL, "CTRL", xPercent = 78f, widthPercent = 10f)
        )
        return if (!includeEnter) {
            row
        } else {
            row + listOf(
            KeySpec(KeyType.ENTER, "↵", output = "\n", xPercent = 88f, widthPercent = 12f)
            )
        }
    }

    private fun charSpec(ch: Char, xPercent: Float, hint: String = "", widthPercent: Float = 10f): KeySpec {
        val label = ch.toString()
        return KeySpec(
            type = KeyType.CHAR,
            label = label,
            output = label,
            hint = hint,
            moreKeys = moreKeysFor(ch),
            xPercent = xPercent,
            widthPercent = widthPercent
        )
    }

    private fun moreKeysFor(ch: Char): List<String> = when (ch.lowercaseChar()) {
        'a' -> listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā")
        'c' -> listOf("ç", "ć", "č")
        'e' -> listOf("è", "é", "ê", "ë", "ē", "ė", "ę")
        'i' -> listOf("î", "ï", "í", "ī", "į", "ì")
        'n' -> listOf("ñ", "ń")
        'o' -> listOf("ô", "ö", "ò", "ó", "œ", "ø", "ō", "õ")
        's' -> listOf("ß", "ś", "š")
        'u' -> listOf("û", "ü", "ù", "ú", "ū")
        'y' -> listOf("ÿ")
        'z' -> listOf("ž", "ź", "ż")
        else -> emptyList()
    }

    private fun displayLabel(spec: KeySpec): String = when (spec.type) {
        KeyType.CHAR -> if (shifted && spec.label == "'") "?" else if (shifted) spec.label.uppercase(Locale.ROOT) else spec.label
        KeyType.SPACE -> spacebarLabel
        KeyType.SYMBOLS -> symbolsLabel
        else -> spec.label
    }

    private fun displayHint(key: Key): String {
        if (symPageActive) return ""
        if (key.spec.type !in listOf(KeyType.CHAR, KeyType.COMMA, KeyType.PERIOD)) return ""
        return longPressAlternatesFor(key).firstOrNull().orEmpty()
    }

    private fun previewLabelFor(key: Key): String? {
        val keyCode = soundKeyCodeFor(key)
        val heldType = heldModifierKey?.spec?.type
        return when {
            heldType == KeyType.SYMBOLS && !symPageActive -> symPreviewLabels[keyCode]
            heldType == KeyType.CTRL || ctrlPreviewActive -> ctrlPreviewLabels[keyCode]
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    private fun previewIconFor(key: Key): Drawable? {
        val keyCode = soundKeyCodeFor(key)
        val heldType = heldModifierKey?.spec?.type
        if (heldType != KeyType.CTRL && !ctrlPreviewActive) {
            return null
        }
        return ctrlPreviewIconRes[keyCode]?.let(::drawable)
    }

    private fun isModifierPreviewLayerActive(): Boolean {
        val heldType = heldModifierKey?.spec?.type
        return heldType == KeyType.SYMBOLS || heldType == KeyType.CTRL || ctrlPreviewActive
    }

    private fun symPageLabelFor(key: Key): String? {
        if (!symPageActive) return null
        if (key.spec.type !in listOf(KeyType.CHAR, KeyType.COMMA, KeyType.PERIOD)) return null
        return symPageLabels[soundKeyCodeFor(key)]?.takeIf { it.isNotBlank() }
    }

    private fun previewTextSize(label: String, rect: RectF): Float {
        var textSize = when {
            label.length <= 2 -> sp(20f)
            label.length <= 5 -> sp(14f)
            else -> sp(11f)
        }
        textPaint.textSize = textSize
        val maxWidth = rect.width() - dp(8f)
        while (textSize > sp(8f) && textPaint.measureText(label) > maxWidth) {
            textSize -= sp(1f)
            textPaint.textSize = textSize
        }
        return textSize
    }

    private fun dispatchKey(key: Key) {
        when (key.spec.type) {
            KeyType.CHAR -> {
                val text = if (shifted && key.spec.output == "'") "?" else if (shifted) key.spec.output.uppercase(Locale.ROOT) else key.spec.output
                if (listener?.onKeyStroke(soundKeyCodeFor(key), text) != true) {
                    listener?.onText(text)
                }
            }
            KeyType.COMMA, KeyType.PERIOD, KeyType.SPACE -> {
                if (listener?.onKeyStroke(soundKeyCodeFor(key), key.spec.output) != true) {
                    listener?.onText(key.spec.output)
                }
            }
            KeyType.BACKSPACE -> listener?.onBackspace()
            KeyType.ENTER -> listener?.onEnter()
            KeyType.SHIFT -> listener?.onShift()
            KeyType.SYMBOLS -> listener?.onSymbols()
            KeyType.CTRL -> listener?.onCtrl()
            KeyType.LANGUAGE -> listener?.onLanguageSwitch()
        }
    }

    private fun releaseHeldModifier() {
        val key = heldModifierKey ?: return
        listener?.onModifierKeyUp(soundKeyCodeFor(key))
        heldModifierKey = null
        heldModifierPointerId = -1
        if (pressedKey == key) {
            pressedKey = null
        }
        invalidate()
    }

    fun cancelActiveTouchState() {
        handler.removeCallbacks(longPressRunnable)
        dismissPopup()
        spaceSwipeActive = false
        spaceLongPressArmed = false
        releaseHeldModifier()
        chordKey = null
        chordPointerId = -1
        pressedKey = null
        invalidate()
    }

    private fun showMoreKeysOrRepeat() {
        val key = pressedKey ?: return
        if (isModifierPreviewLayerActive()) {
            return
        }
        if (key.spec.type == KeyType.BACKSPACE) {
            longPressTriggered = true
            listener?.onKeyPressSound(KeyEvent.KEYCODE_DEL)
            listener?.onBackspace()
            handler.postDelayed(longPressRunnable, 55L)
            return
        }
        if (key.spec.type == KeyType.SPACE) {
            longPressTriggered = true
            spaceLongPressArmed = true
            dismissPopup()
            invalidate()
            return
        }
        if (symPageActive) {
            if (!key.spec.type.canOpenSymbolPicker()) {
                return
            }
            val keyCode = soundKeyCodeFor(key)
            if (listener?.onSymbolLongPress(keyCode) == true) {
                longPressTriggered = true
                dismissPopup()
                invalidate()
            }
            return
        }
        val resolvedMoreKeys = longPressAlternatesFor(key)
        if (resolvedMoreKeys.isEmpty()) return
        longPressTriggered = true
        dismissPopup()
        val moreKeys = resolvedMoreKeys.map { if (shifted && it.length == 1 && it[0].isLetter()) it.uppercase(Locale.ROOT) else it }
        val itemWidth = dp(42f)
        val itemHeight = dp(52f)
        val padding = dp(6f)
        val popupWidth = padding * 2 + moreKeys.size * itemWidth
        val popupHeight = padding * 2 + itemHeight
        val popupLeft = (key.visualRect.centerX() - popupWidth / 2f).coerceIn(0f, width - popupWidth.toFloat())
        val popupTop = key.visualRect.top - popupHeight - dp(8f)
        moreKeysPanelState = MoreKeysPanelState(
            baseKey = key,
            keys = moreKeys,
            popupRectInView = RectF(popupLeft, popupTop, popupLeft + popupWidth, popupTop + popupHeight),
            keyWidth = itemWidth.toFloat(),
            keyHeight = itemHeight.toFloat(),
            padding = padding.toFloat(),
            selectedIndex = 0
        )
        previewPopupState = null
        updatePopupOverlay()
        invalidate()
    }

    private fun showPreview(key: Key) {
        if (isModifierPreviewLayerActive()) return
        if (key.spec.type != KeyType.CHAR && key.spec.type != KeyType.COMMA && key.spec.type != KeyType.PERIOD) return
        val previewWidth = maxOf(key.visualRect.width() + dp(18f), dp(52f).toFloat())
        val previewHeight = dp(72f).toFloat()
        val popupLeft = (key.visualRect.centerX() - previewWidth / 2f).coerceIn(0f, width - previewWidth)
        val popupTop = key.visualRect.top - previewHeight - dp(8f)
        previewPopupState = PreviewPopupState(
            label = symPageLabelFor(key) ?: displayLabel(key.spec),
            rect = RectF(popupLeft, popupTop, popupLeft + previewWidth, popupTop + previewHeight),
            hasMoreKeys = longPressAlternatesFor(key).isNotEmpty()
        )
        moreKeysPanelState = null
        updatePopupOverlay()
        invalidate()
    }

    private fun dismissPopup() {
        previewPopupState = null
        moreKeysPanelState = null
        updatePopupOverlay()
    }

    private fun updateMoreKeysSelection(x: Float, y: Float) {
        val panel = moreKeysPanelState ?: return
        val selected = selectedMoreKeyIndex(x, y, panel)
        if (selected < 0) {
            return
        }
        if (selected == panel.selectedIndex) return
        panel.selectedIndex = selected
        updatePopupOverlay()
        invalidate()
    }

    private fun selectedMoreKey(x: Float, y: Float, panel: MoreKeysPanelState): String? {
        val index = selectedMoreKeyIndex(x, y, panel).takeIf { it >= 0 } ?: panel.selectedIndex
        return panel.keys.getOrNull(index)
    }

    private fun selectedMoreKeyIndex(x: Float, y: Float, panel: MoreKeysPanelState): Int {
        val rect = panel.popupRectInView
        val verticalSlop = dp(24f)
        if (y < rect.top - verticalSlop || y > rect.bottom + verticalSlop) return -1
        val relativeX = (x - rect.left - panel.padding).coerceIn(0f, panel.keys.size * panel.keyWidth - 1f)
        return (relativeX / panel.keyWidth).toInt().coerceIn(0, panel.keys.lastIndex)
    }

    private fun findKey(x: Float, y: Float): Key? {
        keys.firstOrNull { it.hitRect.contains(x, y) }?.let { return it }
        if (!nearestKeyTouchEnabled) return null

        val verticalSlop = maxOf(rowGapPx.toFloat(), dp(8f).toFloat())
        val candidateRows = keys
            .filter { y >= it.hitRect.top - verticalSlop && y <= it.hitRect.bottom + verticalSlop }
            .ifEmpty { return null }
        return candidateRows.minByOrNull { key ->
            val dx = when {
                x < key.hitRect.left -> key.hitRect.left - x
                x > key.hitRect.right -> x - key.hitRect.right
                else -> 0f
            }
            val dy = abs(y - key.hitRect.centerY())
            dx * dx + dy * dy
        }
    }

    private fun soundKeyCodeFor(key: Key): Int {
        return when (key.spec.type) {
            KeyType.SPACE -> KeyEvent.KEYCODE_SPACE
            KeyType.BACKSPACE -> KeyEvent.KEYCODE_DEL
            KeyType.ENTER -> KeyEvent.KEYCODE_ENTER
            KeyType.SHIFT -> KeyEvent.KEYCODE_SHIFT_LEFT
            KeyType.CTRL -> KeyEvent.KEYCODE_CTRL_LEFT
            KeyType.SYMBOLS, KeyType.LANGUAGE -> KeyEvent.KEYCODE_SYM
            KeyType.COMMA -> KeyEvent.KEYCODE_COMMA
            KeyType.PERIOD -> KeyEvent.KEYCODE_PERIOD
            KeyType.CHAR -> keyCodeForText(key.spec.output)
        }
    }

    private fun keyCodeForText(text: String): Int {
        val char = text.firstOrNull()?.lowercaseChar() ?: return KeyEvent.KEYCODE_UNKNOWN
        return when (char) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (char - 'a')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (char - '0')
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }

    private fun longPressAlternatesFor(key: Key): List<String> {
        if (!key.spec.type.canShowLongPressAlternates()) {
            return emptyList()
        }
        val providerAlternates = longPressAlternatesProvider
            ?.invoke(key.spec.output)
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return providerAlternates.ifEmpty { key.spec.moreKeys }
    }


    private fun drawPreviewPopup(canvas: Canvas, offsetX: Float = 0f, offsetY: Float = 0f) {
        val popup = previewPopupState ?: return
        val rect = popup.rect.offsetBy(offsetX, offsetY)
        drawDrawable(canvas, themedPopupBackground() ?: if (popup.hasMoreKeys) previewMoreBackground else previewBackground, rect)
        textPaint.textSize = sp(30f)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = themeOverride?.textAndIcons ?: Color.rgb(238, 238, 238)
        val baselineOffset = -(textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(popup.label, rect.centerX(), rect.centerY() + baselineOffset, textPaint)
    }

    private fun drawMoreKeysPanel(canvas: Canvas, offsetX: Float = 0f, offsetY: Float = 0f) {
        val panel = moreKeysPanelState ?: return
        val panelRect = panel.popupRectInView.offsetBy(offsetX, offsetY)
        drawDrawable(canvas, themedPopupBackground() ?: moreKeysBackground, panelRect)
        panel.keys.forEachIndexed { index, label ->
            val left = panelRect.left + panel.padding + index * panel.keyWidth
            val top = panelRect.top + panel.padding
            val rect = RectF(left, top, left + panel.keyWidth, top + panel.keyHeight)
            if (index == panel.selectedIndex) {
                drawDrawable(canvas, themedPopupSelectedKeyBackground() ?: normalKeyBackground, rect)
            }
            textPaint.textSize = sp(24f)
            textPaint.typeface = Typeface.DEFAULT
            textPaint.color = themeOverride?.textAndIcons
                ?: if (index == panel.selectedIndex) Color.BLACK else Color.rgb(238, 238, 238)
            val baselineOffset = -(textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(label, rect.centerX(), rect.centerY() + baselineOffset, textPaint)
        }
    }

    private fun updatePopupOverlay() {
        val root = rootView ?: return
        popupOverlayDrawable?.let { root.overlay.remove(it) }
        popupOverlayDrawable = null
        if (previewPopupState == null && moreKeysPanelState == null) {
            root.invalidate()
            return
        }
        val location = IntArray(2)
        val rootLocation = IntArray(2)
        getLocationOnScreen(location)
        root.getLocationOnScreen(rootLocation)
        val offsetX = (location[0] - rootLocation[0]).toFloat()
        val offsetY = (location[1] - rootLocation[1]).toFloat()
        popupOverlayDrawable = object : Drawable() {
            override fun draw(canvas: Canvas) {
                drawPreviewPopup(canvas, offsetX, offsetY)
                drawMoreKeysPanel(canvas, offsetX, offsetY)
            }

            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }.also { drawable ->
            drawable.setBounds(0, 0, root.width, root.height)
            root.overlay.add(drawable)
        }
        root.invalidate()
    }

    override fun onDetachedFromWindow() {
        popupOverlayDrawable?.let { rootView?.overlay?.remove(it) }
        popupOverlayDrawable = null
        super.onDetachedFromWindow()
    }

    private fun backgroundFor(key: Key): Drawable? {
        themeOverride?.let { theme ->
            val pressed = key == pressedKey
            val baseColor = themedKeyColor(theme, key.spec.type)
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = keyCornerRadius(theme)
                setColor(if (pressed) blendColors(baseColor, theme.accent, 0.28f) else baseColor)
                setStroke(dp(1f), theme.divider)
            }
        }
        val pressed = key == pressedKey
        val drawable = when {
            key.spec.type == KeyType.SPACE && pressed -> spacebarPressedBackground
            key.spec.type == KeyType.SPACE -> spacebarBackground
            key.spec.type == KeyType.SHIFT && shifted && pressed -> shiftedKeyPressedBackground
            key.spec.type == KeyType.SHIFT && shifted -> shiftedKeyBackground
            pressed -> normalKeyPressedBackground
            else -> normalKeyBackground
        }
        return drawable?.constantState?.newDrawable()?.mutate() ?: drawable
    }

    private fun themedKeyColor(theme: ThemeOverride, type: KeyType): Int {
        return when (type) {
            KeyType.SHIFT -> when {
                shiftLocked -> theme.ledLocked
                shifted -> theme.ledActive
                else -> theme.specialKey
            }
            KeyType.CTRL -> when {
                ctrlLocked -> theme.ledLocked
                ctrlPressed || ctrlOneShot -> theme.ledActive
                else -> theme.specialKey
            }
            else -> if (isFunctional(type)) theme.specialKey else theme.normalKey
        }
    }

    private fun themedPopupBackground(): Drawable? {
        val theme = themeOverride ?: return null
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10f).toFloat()
            setColor(theme.keyPopup)
            setStroke(dp(1f), theme.divider)
        }
    }

    private fun themedPopupSelectedKeyBackground(): Drawable? {
        val theme = themeOverride ?: return null
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = keyCornerRadius(theme)
            setColor(theme.keyPopupSelected)
            setStroke(dp(1f), theme.divider)
        }
    }

    private fun keyCornerRadius(theme: ThemeOverride): Float {
        return preferredKeyHeightPx * theme.keyCornerRadiusRatio.coerceIn(0f, 0.35f)
    }

    private fun drawDrawable(canvas: Canvas, drawable: Drawable?, rect: RectF) {
        if (drawable == null) return
        drawable.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        drawable.draw(canvas)
    }

    private fun RectF.offsetBy(dx: Float, dy: Float): RectF =
        RectF(left + dx, top + dy, right + dx, bottom + dy)

    private fun isFunctional(type: KeyType): Boolean =
        type == KeyType.SHIFT || type == KeyType.BACKSPACE || type == KeyType.SYMBOLS || type == KeyType.CTRL || type == KeyType.ENTER || type == KeyType.LANGUAGE

    private fun KeyType.canShowLongPressAlternates(): Boolean =
        this == KeyType.CHAR || this == KeyType.COMMA || this == KeyType.PERIOD

    private fun KeyType.canOpenSymbolPicker(): Boolean =
        this == KeyType.CHAR || this == KeyType.COMMA || this == KeyType.PERIOD

    private fun KeyType.isHoldModifier(): Boolean =
        this == KeyType.CTRL || this == KeyType.SYMBOLS

    private fun drawable(resId: Int): Drawable? = ContextCompat.getDrawable(context, resId)

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun blendColors(first: Int, second: Int, ratio: Float): Int {
        val clamped = ratio.coerceIn(0f, 1f)
        val inverse = 1f - clamped
        return Color.rgb(
            (Color.red(first) * inverse + Color.red(second) * clamped).toInt().coerceIn(0, 255),
            (Color.green(first) * inverse + Color.green(second) * clamped).toInt().coerceIn(0, 255),
            (Color.blue(first) * inverse + Color.blue(second) * clamped).toInt().coerceIn(0, 255)
        )
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    ).toInt()

    private fun sp(value: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        value,
        resources.displayMetrics
    )

    fun showInputMethodPicker() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }
}
