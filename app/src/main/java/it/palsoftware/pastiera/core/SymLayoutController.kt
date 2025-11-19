package it.palsoftware.pastiera.core

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.inputmethod.AltSymManager

class SymLayoutController(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val altSymManager: AltSymManager
) {

    companion object {
        private const val PREF_CURRENT_SYM_PAGE = "current_sym_page"
    }

    enum class SymKeyResult {
        NOT_HANDLED,
        CONSUME,
        CALL_SUPER
    }

    private var symPage: Int = prefs.getInt(PREF_CURRENT_SYM_PAGE, 0)

    fun currentSymPage(): Int = symPage

    fun isSymActive(): Boolean = symPage > 0

    fun toggleSymPage(): Int {
        symPage = (symPage + 1) % 3
        persistSymPage()
        return symPage
    }

    fun closeSymPage(): Boolean {
        if (symPage == 0) {
            return false
        }
        symPage = 0
        persistSymPage()
        return true
    }

    fun reset() {
        symPage = 0
        persistSymPage()
    }

    fun restoreSymPageIfNeeded(onStatusBarUpdate: () -> Unit) {
        val restoreSymPage = SettingsManager.getRestoreSymPage(context)
        if (restoreSymPage > 0) {
            symPage = restoreSymPage.coerceIn(0, 2)
            persistSymPage()
            SettingsManager.clearRestoreSymPage(context)
            Handler(Looper.getMainLooper()).post {
                onStatusBarUpdate()
            }
        }
    }

    fun emojiMapText(): String {
        return if (symPage == 1) altSymManager.buildEmojiMapText() else ""
    }

    fun currentSymMappings(): Map<Int, String>? {
        return when (symPage) {
            1 -> altSymManager.getSymMappings()
            2 -> altSymManager.getSymMappings2()
            else -> null
        }
    }

    fun handleKeyWhenActive(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        ctrlLatchActive: Boolean,
        altLatchActive: Boolean,
        updateStatusBar: () -> Unit
    ): SymKeyResult {
        val autoCloseEnabled = SettingsManager.getSymAutoClose(context)

        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                closeSymAndUpdate(updateStatusBar)
                return SymKeyResult.CALL_SUPER
            }
            KeyEvent.KEYCODE_ENTER -> {
                if (autoCloseEnabled) {
                    closeSymAndUpdate(updateStatusBar)
                    return SymKeyResult.CALL_SUPER
                }
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                closeSymAndUpdate(updateStatusBar)
                return SymKeyResult.NOT_HANDLED
            }
        }

        val symChar = when (symPage) {
            1 -> altSymManager.getSymMappings()[keyCode]
            2 -> altSymManager.getSymMappings2()[keyCode]
            else -> null
        }

        if (symChar != null && inputConnection != null) {
            inputConnection.commitText(symChar, 1)
            if (autoCloseEnabled) {
                closeSymAndUpdate(updateStatusBar)
            }
            return SymKeyResult.CONSUME
        }

        return SymKeyResult.NOT_HANDLED
    }

    fun handleKeyUp(keyCode: Int, shiftPressed: Boolean): Boolean {
        return altSymManager.handleKeyUp(keyCode, isSymActive(), shiftPressed)
    }

    fun emojiMapTextForLayout(): String = altSymManager.buildEmojiMapText()

    private fun closeSymAndUpdate(updateStatusBar: () -> Unit) {
        if (closeSymPage()) {
            updateStatusBar()
        }
    }

    private fun persistSymPage() {
        prefs.edit().putInt(PREF_CURRENT_SYM_PAGE, symPage).apply()
    }

}

