package it.palsoftware.pastiera.core

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.commands.CommandExecutor
import it.palsoftware.pastiera.commands.CommandRegistry
import it.palsoftware.pastiera.commands.CommandSurface
import it.palsoftware.pastiera.data.mappings.KeyMappingLoader
import it.palsoftware.pastiera.inputmethod.NavModeHandler
import it.palsoftware.pastiera.inputmethod.NotificationHelper

/**
 * Encapsulates nav-mode specific state and routing logic (Ctrl latch double-tap,
 * keycode remapping, notification lifecycle).
 */
class NavModeController(
    private val context: Context,
    private val modifierStateController: ModifierStateController
) {

    companion object {
        private const val TAG = "NavModeController"
    }

    private var navModeChangedListener: ((Boolean) -> Unit)? = null
    private var lastNavModeActive: Boolean = false

    fun isNavModeActive(): Boolean {
        return modifierStateController.ctrlLatchActive && modifierStateController.ctrlLatchFromNavMode
    }

    fun hasCtrlLatchFromNavMode(): Boolean = modifierStateController.ctrlLatchFromNavMode

    fun setOnNavModeChangedListener(listener: ((Boolean) -> Unit)?) {
        navModeChangedListener = listener
    }

    fun refreshNavModeState() {
        notifyNavModeChanged()
    }

    fun isNavModeKey(keyCode: Int): Boolean {
        val navModeEnabled = SettingsManager.getNavModeEnabled(context)
        val navModeActive = isNavModeActive()
        if (!navModeEnabled && !navModeActive) {
            return false
        }
        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        return isCtrlKey || navModeActive
    }

    fun handleNavModeKey(
        keyCode: Int,
        event: KeyEvent?,
        isKeyDown: Boolean,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        inputConnectionProvider: () -> InputConnection?
    ): Boolean {
        val navModeEnabled = SettingsManager.getNavModeEnabled(context)
        val navModeActive = isNavModeActive()
        if (!navModeEnabled && !navModeActive) {
            return false
        }

        val isCtrlKey = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        if (isCtrlKey) {
            return if (isKeyDown) {
                val isConsecutiveTap = modifierStateController.registerModifierTap(keyCode)
                val (shouldConsume, result) = NavModeHandler.handleCtrlKeyDown(
                    keyCode,
                    modifierStateController.ctrlPressed,
                    modifierStateController.ctrlLatchActive,
                    modifierStateController.ctrlLastReleaseTime,
                    isConsecutiveTap
                )
                applyNavModeResult(result)
                if (shouldConsume) {
                    modifierStateController.ctrlPressed = true
                }
                shouldConsume
            } else {
                val result = NavModeHandler.handleCtrlKeyUp()
                applyNavModeResult(result)
                modifierStateController.ctrlPressed = false
                true
            }
        }

        if (!isKeyDown) {
            return isNavModeActive()
        }

        if (!isNavModeActive()) {
            return false
        }

        val inputConnection = inputConnectionProvider() ?: return false

        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            return sendMappedKey(KeyEvent.KEYCODE_DPAD_CENTER, event, inputConnection)
        }

        val ctrlMapping = ctrlKeyMap[keyCode] ?: return false
        return if (ctrlMapping.type == "command") {
            val command = CommandRegistry(context).resolve(ctrlMapping.value) ?: return false
            if (!command.defaultSurfaces.contains(CommandSurface.NavMode)) return false
            CommandExecutor(
                context = context,
                navModeController = this,
                inputConnectionProvider = inputConnectionProvider
            ).execute(command).isSuccess
        } else if (ctrlMapping.type == "native_ctrl") {
            sendNativeCtrlKey(keyCode, event, inputConnection)
        } else {
            executeMapping(ctrlMapping.type, ctrlMapping.value, event, inputConnection)
        }
    }

    fun executeMapping(
        mappingType: String,
        mappingValue: String,
        event: KeyEvent?,
        inputConnection: InputConnection
    ): Boolean {
        return when (mappingType) {
            "keycode" -> {
                val mappedKeyCode = when (mappingValue) {
                    "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                    "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "DPAD_CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
                    "TAB" -> KeyEvent.KEYCODE_TAB
                    "PAGE_UP" -> KeyEvent.KEYCODE_PAGE_UP
                    "PAGE_DOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
                    "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
                    "FORWARD_DEL" -> KeyEvent.KEYCODE_FORWARD_DEL
                    else -> null
                } ?: return false
                sendMappedKey(mappedKeyCode, event, inputConnection)
            }
            "action" -> performMappedAction(mappingValue, inputConnection)
            "native_ctrl" -> false
            else -> false
        }
    }

    fun cancelNotification() {
        NotificationHelper.cancelNavModeNotification(context)
        hideNavModeStatusIcon()
    }

    fun exitNavMode() {
        if (modifierStateController.ctrlLatchFromNavMode || modifierStateController.ctrlLatchActive) {
            modifierStateController.ctrlLatchFromNavMode = false
            modifierStateController.ctrlLatchActive = false
            NotificationHelper.cancelNavModeNotification(context)
            hideNavModeStatusIcon()
        }
        notifyNavModeChanged()
    }
    
    /**
     * Riattiva nav mode (usato quando power shortcut termina e nav mode era attivo prima).
     * Riutilizza applyNavModeResult per mantenere la logica consistente.
     */
    fun enterNavMode() {
        val navModeEnabled = SettingsManager.getNavModeEnabled(context)
        if (navModeEnabled && !isNavModeActive()) {
            // Riutilizza la logica esistente per attivare nav mode
            applyNavModeResult(
                NavModeHandler.NavModeResult(
                    ctrlLatchActive = true
                )
            )
            Log.d(TAG, "Nav mode riattivato")
        }
    }

    private fun applyNavModeResult(result: NavModeHandler.NavModeResult) {
        result.ctrlLatchActive?.let { latchActive ->
            modifierStateController.ctrlLatchActive = latchActive
            if (latchActive) {
                modifierStateController.ctrlLatchFromNavMode = true
            } else if (modifierStateController.ctrlLatchFromNavMode) {
                modifierStateController.ctrlLatchFromNavMode = false
                // Ensure any legacy nav mode notification is cleared.
                NotificationHelper.cancelNavModeNotification(context)
                hideNavModeStatusIcon()
            }
        }
        result.ctrlPhysicallyPressed?.let { modifierStateController.ctrlPhysicallyPressed = it }
        result.ctrlPressed?.let { modifierStateController.ctrlPressed = it }
        result.lastCtrlReleaseTime?.let { modifierStateController.ctrlLastReleaseTime = it }
        notifyNavModeChanged()
    }

    /**
     * Mostra l'icona del nav mode nella status bar tramite la vecchia API IME (deprecata).
     * Usa reflection per supportare sia showStatusIcon() che setStatusIcon().
     */
    private fun showNavModeStatusIcon() {
        try {
            if (context is android.inputmethodservice.InputMethodService) {
                val imeClass = android.inputmethodservice.InputMethodService::class.java
                val iconResId = R.drawable.ic_settings_24 // Placeholder: icona impostazioni per nav mode

                // Prova prima showStatusIcon(int), poi setStatusIcon(int)
                val methodNames = listOf("showStatusIcon", "setStatusIcon")
                for (name in methodNames) {
                    try {
                        val method = imeClass.getMethod(name, Int::class.javaPrimitiveType)
                        method.invoke(context, iconResId)
                        Log.d(TAG, "Nav mode status icon shown using $name")
                        return
                    } catch (e: NoSuchMethodException) {
                        // Prova il nome successivo
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to show nav mode status icon", e)
        }
    }

    /**
     * Nasconde l'icona del nav mode dalla status bar tramite la vecchia API IME (deprecata).
     * Usa reflection per supportare sia hideStatusIcon() che setStatusIcon(0).
     */
    private fun hideNavModeStatusIcon() {
        try {
            if (context is android.inputmethodservice.InputMethodService) {
                val imeClass = android.inputmethodservice.InputMethodService::class.java

                // Prova prima hideStatusIcon(), poi setStatusIcon(0)
                try {
                    val hideMethod = imeClass.getMethod("hideStatusIcon")
                    hideMethod.invoke(context)
                    Log.d(TAG, "Nav mode status icon hidden using hideStatusIcon")
                    return
                } catch (e: NoSuchMethodException) {
                    // Continua con setStatusIcon(0)
                }

                try {
                    val setMethod = imeClass.getMethod("setStatusIcon", Int::class.javaPrimitiveType)
                    setMethod.invoke(context, 0)
                    Log.d(TAG, "Nav mode status icon hidden using setStatusIcon(0)")
                } catch (e: NoSuchMethodException) {
                    // Nessuna API disponibile: non fare nulla
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to hide nav mode status icon", e)
        }
    }

    private fun sendMappedKey(
        mappedKeyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection
    ): Boolean {
        val downTime = event?.downTime ?: SystemClock.uptimeMillis()
        val eventTime = event?.eventTime ?: downTime
        val metaState = event?.metaState ?: 0

        val downEvent = KeyEvent(
            downTime,
            eventTime,
            KeyEvent.ACTION_DOWN,
            mappedKeyCode,
            0,
            metaState
        )
        val upEvent = KeyEvent(
            downTime,
            eventTime,
            KeyEvent.ACTION_UP,
            mappedKeyCode,
            0,
            metaState
        )
        inputConnection.sendKeyEvent(downEvent)
        inputConnection.sendKeyEvent(upEvent)
        Log.d(TAG, "Nav mode: dispatched keycode $mappedKeyCode")
        return true
    }

    private fun performMappedAction(
        action: String,
        inputConnection: InputConnection
    ): Boolean {
        val mediaKeyCode = when (action) {
            "media_play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "media_previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "media_next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> null
        }
        if (mediaKeyCode != null) {
            return dispatchMediaKey(mediaKeyCode)
        }

        val actionId = when (action) {
            "copy" -> android.R.id.copy
            "paste" -> android.R.id.paste
            "cut" -> android.R.id.cut
            "undo" -> android.R.id.undo
            "select_all" -> android.R.id.selectAll
            else -> null
        } ?: return false

        inputConnection.performContextMenuAction(actionId)
        Log.d(TAG, "Nav mode: performed action $action")
        return true
    }

    private fun dispatchMediaKey(keyCode: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val eventTime = SystemClock.uptimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
        Log.d(TAG, "Nav mode: dispatched media keycode $keyCode")
        return true
    }

    private fun sendNativeCtrlKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection
    ): Boolean {
        val downTime = event?.downTime ?: System.currentTimeMillis()
        val eventTime = event?.eventTime ?: System.currentTimeMillis()
        val metaState = (event?.metaState ?: 0) or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        inputConnection.sendKeyEvent(
            KeyEvent(downTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
        )
        inputConnection.sendKeyEvent(
            KeyEvent(downTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0, metaState)
        )
        Log.d(TAG, "Nav mode: passed native Ctrl combo for keycode $keyCode")
        return true
    }

    private fun notifyNavModeChanged() {
        val isActiveNow = isNavModeActive()
        if (lastNavModeActive != isActiveNow) {
            if (isActiveNow) {
                NotificationHelper.vibrateNavModeActivated(context)
            }
            navModeChangedListener?.invoke(isActiveNow)
        }
        lastNavModeActive = isActiveNow
    }
}
