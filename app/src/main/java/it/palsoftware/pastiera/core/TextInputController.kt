package it.palsoftware.pastiera.core

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AutoCapitalizeHelper

/**
 * Orchestrates text-level helpers such as double-space-to-period and
 * auto-capitalization triggers. Keeps state like double-space timing isolated
 * from the IME service.
 *
 * Important: this controller never decides long-term Shift state on its own.
 * For smart auto-cap (Shift one-shot for the next character), it always
 * delegates to [AutoCapitalizeHelper] and [ModifierStateController] so that
 * there is a single source of truth for modifier state.
 */
class TextInputController(
    private val context: Context,
    private val modifierStateController: ModifierStateController,
    private val doubleTapThreshold: Long
) {

    private var lastSpacePressTime: Long = 0L

    data class SmartQuotePair(
        val opening: String,
        val closing: String
    )

    fun handleDoubleSpaceToPeriod(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableDoubleSpaceToPeriod: Boolean,
        shouldDisableAutoCapitalize: Boolean,
        onStatusBarUpdate: () -> Unit
    ): Boolean {
        // Detects a "double space" pattern and replaces the trailing space
        // with ". ". The decision to enable Shift one-shot after that is
        // delegated to AutoCapitalizeHelper so it can be tracked as a
        // smart auto-capitalization (and cleared when context changes).
        if (keyCode != KeyEvent.KEYCODE_SPACE || shouldDisableDoubleSpaceToPeriod) {
            if (lastSpacePressTime > 0) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSpacePressTime >= doubleTapThreshold) {
                    lastSpacePressTime = 0
                }
            }
            return false
        }

        if (!SettingsManager.getDoubleSpaceToPeriod(context)) {
            lastSpacePressTime = 0
            return false
        }

        val currentTime = System.currentTimeMillis()
        val isDoubleTap = lastSpacePressTime > 0 &&
            (currentTime - lastSpacePressTime) < doubleTapThreshold

        if (!isDoubleTap || inputConnection == null) {
            lastSpacePressTime = currentTime
            return false
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0) ?: return false
        
        if (textBeforeCursor.isEmpty() || !textBeforeCursor.endsWith(" ")) {
            lastSpacePressTime = currentTime
            return false
        }

        val endsWithSingleSpace = textBeforeCursor.length < 2 ||
            textBeforeCursor[textBeforeCursor.length - 2] != ' '

        val endsWithDoubleSpaceAfterAutoSpace =
            AutoSpaceTracker.isPending() &&
                textBeforeCursor.length >= 2 &&
                textBeforeCursor.endsWith("  ") &&
                (textBeforeCursor.length < 3 || textBeforeCursor[textBeforeCursor.length - 3] != ' ')

        if (!endsWithSingleSpace && !endsWithDoubleSpaceAfterAutoSpace) {
            lastSpacePressTime = currentTime
            return false
        }

        // Reuse AutoCapitalizeHelper logic to check if already has sentence-ending punctuation
        // (don't convert if punctuation already exists)
        // Note: We check without requiring whitespace after, since we're about to add ". "
        if (AutoCapitalizeHelper.hasSentenceEndingPunctuation(
            textBeforeCursor, 
            requireWhitespaceAfter = false
        )) {
            lastSpacePressTime = currentTime
            return false
        }

        val spacesToReplace = if (endsWithDoubleSpaceAfterAutoSpace) 2 else 1
        inputConnection.deleteSurroundingText(spacesToReplace, 0)
        inputConnection.commitText(". ", 1)
        AutoSpaceTracker.clear()
        AutoCapitalizeHelper.enableAfterPunctuation(
            context = context,
            inputConnection = inputConnection,
            shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
            onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
            disableShift = { modifierStateController.consumeShiftOneShot() },
            onUpdateStatusBar = onStatusBarUpdate
        )
        lastSpacePressTime = 0
        return true
    }

    fun handleAutoCapAfterPeriod(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        onStatusBarUpdate: () -> Unit
    ) {
        // If user presses Space after punctuation and Shift is not already
        // one-shot (e.g. pressed manually), delegate to AutoCapitalizeHelper.
        // The helper inspects the surrounding text and user settings to decide
        // whether to enable smart Shift for the next character.
        if (keyCode == KeyEvent.KEYCODE_SPACE &&
            !modifierStateController.shiftOneShot
        ) {
            AutoCapitalizeHelper.enableAfterPunctuation(
                context = context,
                inputConnection = inputConnection,
                shouldDisableAutoCapitalize = shouldDisableAutoCapitalize,
                onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = onStatusBarUpdate
            )
        }
    }

    fun handleSpacedHyphenToEnDash(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartPunctuation: Boolean
    ): Boolean {
        if (
            keyCode != KeyEvent.KEYCODE_SPACE ||
            inputConnection == null ||
            shouldDisableSmartPunctuation ||
            !SettingsManager.getSpacedHyphenToEnDash(context)
        ) {
            return false
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)?.toString() ?: return false
        if (!textBeforeCursor.endsWith(" -")) {
            return false
        }

        val prefix = textBeforeCursor.dropLast(2)
        if (prefix.isBlank()) {
            return false
        }

        val linePrefix = prefix.substringAfterLast('\n')
        if (linePrefix.isBlank()) {
            return false
        }

        val dash = when (SettingsManager.getSpacedHyphenDashStyle(context)) {
            SettingsManager.DASH_STYLE_EM -> "—"
            else -> "–"
        }
        inputConnection.deleteSurroundingText(1, 0)
        inputConnection.commitText("$dash ", 1)
        return true
    }

    fun handlePendingMidWordQuoteToApostrophe(
        typedText: String,
        inputConnection: InputConnection?,
        shouldDisableSmartPunctuation: Boolean
    ): Boolean {
        if (
            typedText.isEmpty() ||
            typedText.any { it.isWhitespace() } ||
            inputConnection == null ||
            shouldDisableSmartPunctuation ||
            !SettingsManager.getMidWordQuoteToApostrophe(context)
        ) {
            return false
        }

        val next = typedText.first()
        if (!next.isLetter()) {
            return false
        }

        val before = inputConnection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        if (before.lastOrNull() != '"') {
            return false
        }
        val previous = before.dropLast(1).lastOrNull() ?: return false
        if (!previous.isLetter()) {
            return false
        }

        inputConnection.deleteSurroundingText(1, 0)
        inputConnection.commitText("'$typedText", 1)
        return true
    }

    fun handleSmartQuoteReplacement(
        typedText: String,
        inputConnection: InputConnection?,
        shouldDisableSmartPunctuation: Boolean
    ): Boolean {
        if (
            typedText.isEmpty() ||
            inputConnection == null ||
            shouldDisableSmartPunctuation ||
            !SettingsManager.getSmartQuotes(context)
        ) {
            return false
        }
        if (typedText == "\"" || !isSmartQuoteTrailingDelimiter(typedText)) {
            return false
        }

        val pair = smartQuotePair(SettingsManager.getSmartQuotesStyle(context))
        val before = inputConnection.getTextBeforeCursor(240, 0)?.toString().orEmpty()
        if (!before.endsWith("\"")) {
            return false
        }

        val openingIndex = findSmartQuoteOpeningIndex(before)
        if (openingIndex < 0) {
            return false
        }
        val quotedText = before.substring(openingIndex + 1, before.length - 1)
        if (quotedText.isBlank() || quotedText.any { it == '\n' || it == '"' }) {
            return false
        }

        val replacement = pair.opening + quotedText + pair.closing + typedText
        inputConnection.deleteSurroundingText(before.length - openingIndex, 0)
        inputConnection.commitText(replacement, 1)
        return true
    }

    private fun isSmartQuoteTrailingDelimiter(typedText: String): Boolean {
        if (typedText.length != 1) {
            return false
        }
        val char = typedText[0]
        return char.isWhitespace() ||
            char in setOf('-', '–', '—', '.', ',', ';', ':', '!', '?', ')', ']', '}', '»', '›')
    }

    private fun findSmartQuoteOpeningIndex(textBeforeCursor: String): Int {
        val candidateText = textBeforeCursor.dropLast(1)
        var index = candidateText.lastIndexOf('"')
        while (index >= 0) {
            if (hasSmartQuoteOpeningContext(candidateText, index)) {
                return index
            }
            index = candidateText.lastIndexOf('"', startIndex = index - 1)
        }
        return -1
    }

    private fun hasSmartQuoteOpeningContext(text: String, quoteIndex: Int): Boolean {
        if (quoteIndex == 0) {
            return true
        }
        val previous = text[quoteIndex - 1]
        return previous.isWhitespace() ||
            previous in setOf('(', '[', '{', '<', '«', '‹', '„', '“', '”', '»', '›', '-', '–', '—')
    }

    private fun smartQuotePair(style: String): SmartQuotePair {
        return when (style) {
            SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS -> SmartQuotePair("«", "»")
            SettingsManager.SMART_QUOTES_STYLE_FRENCH_GUILLEMETS_NARROW_SPACED -> SmartQuotePair("« ", " »")
            SettingsManager.SMART_QUOTES_STYLE_GERMAN_LOW_HIGH -> SmartQuotePair("„", "“")
            SettingsManager.SMART_QUOTES_STYLE_ENGLISH_CURLY -> SmartQuotePair("“", "”")
            else -> SmartQuotePair("»", "«")
        }
    }

    fun handleAutoCapAfterEnter(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableAutoCapitalize: Boolean,
        onStatusBarUpdate: () -> Unit
    ) {
        // After Enter, we reuse the same smart auto-cap logic used for
        // "first letter in empty field" by delegating to AutoCapitalizeHelper.
        // This keeps all "start of sentence" detection in a single place.
        if (keyCode == KeyEvent.KEYCODE_ENTER && !shouldDisableAutoCapitalize) {
            AutoCapitalizeHelper.enableAfterEnter(
                context,
                inputConnection,
                shouldDisableAutoCapitalize,
                onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = onStatusBarUpdate
            )
        }
    }
}
