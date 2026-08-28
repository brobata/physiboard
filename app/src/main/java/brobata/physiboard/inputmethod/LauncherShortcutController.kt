package brobata.physiboard.inputmethod

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import brobata.physiboard.R
import brobata.physiboard.SettingsManager
import brobata.physiboard.commands.CommandExecutor
import brobata.physiboard.commands.CommandKind
import brobata.physiboard.commands.CommandRegistry
import brobata.physiboard.commands.CommandSourceId
import brobata.physiboard.commands.CommandTarget
import brobata.physiboard.commands.PhysiBoardCommandSource

/**
 * Controller for handling launcher shortcuts functionality.
 * Manages app launching, launcher detection, and shortcut assignment dialogs.
 */
class LauncherShortcutController(
    private val context: Context
) {
    companion object {
        private const val TAG = "PhysiBoardInputMethod"
        private const val POWER_SHORTCUT_TIMEOUT_MS = 5000L // 5 secondi di timeout
    }

    // Cache for launcher packages
    private var cachedLauncherPackages: Set<String>? = null
    
    // Power Shortcuts state: SYM held to arm a shortcut
    private var powerShortcutSymPressed: Boolean = false
    private var powerShortcutTimeoutHandler: android.os.Handler? = null
    private var powerShortcutTimeoutRunnable: Runnable? = null
    private var powerShortcutToastRunnable: Runnable? = null
    
    // State for handling nav mode during power shortcuts
    private var navModeWasActive: Boolean = false
    private var exitNavModeCallback: (() -> Unit)? = null
    private var enterNavModeCallback: (() -> Unit)? = null

    /**
     * Checks whether the current package is a launcher.
     */
    fun isLauncher(packageName: String?): Boolean {
        if (packageName == null) return false
        
        // Cache the launcher list to avoid repeated queries
        if (cachedLauncherPackages == null) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                
                val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                cachedLauncherPackages = resolveInfos.map { it.activityInfo.packageName }.toSet()
                Log.d(TAG, "Launcher packages trovati: $cachedLauncherPackages")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to detect the launchers", e)
                cachedLauncherPackages = emptySet()
            }
        }
        
        val isLauncher = cachedLauncherPackages?.contains(packageName) ?: false
        Log.d(TAG, "isLauncher($packageName) = $isLauncher")
        return isLauncher
    }
    
    /**
     * Apre un'app tramite package name.
     */
    private fun launchApp(packageName: String): Boolean {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "App aperta: $packageName")
                return true
            } else {
                Log.w(TAG, "No launch intent found for: $packageName")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open the app $packageName", e)
            return false
        }
    }

    private fun executeShortcutCommand(shortcut: SettingsManager.LauncherShortcut): Boolean {
        if (shortcut.commandId == PhysiBoardCommandSource.COMMAND_QUICK_LAUNCHER) {
            return QuickLauncherOpener.open(context)
        }
        val command = shortcut.commandId?.let { CommandRegistry(context).resolve(it) }
            ?: shortcutToCommand(shortcut)
            ?: return false
        return CommandExecutor(context).execute(command).isSuccess
    }

    private fun shortcutToCommand(shortcut: SettingsManager.LauncherShortcut): CommandTarget? {
        val launch = shortcut.commandLaunch ?: return null
        return CommandTarget(
            id = shortcut.commandId ?: "legacy:${shortcut.type}:${shortcut.packageName ?: shortcut.action.orEmpty()}",
            source = shortcut.commandSource
                ?.let { CommandSourceId.fromStorageValue(it) }
                ?: CommandSourceId.PhysiBoard,
            kind = runCatching {
                CommandKind.valueOf(shortcut.commandKind ?: "")
            }.getOrDefault(CommandKind.PhysiBoardAction),
            label = shortcut.commandTitle ?: shortcut.appName ?: shortcut.packageName ?: "Shortcut",
            subtitle = shortcut.commandSubtitle,
            launch = launch
        )
    }
    
    /**
     * Handles launcher shortcuts when not in a text field.
     */
    fun handleLauncherShortcut(keyCode: Int): Boolean {
        val shortcut = SettingsManager.getLauncherShortcut(context, keyCode)
        if (shortcut != null) {
            // Gestisci diversi tipi di azioni
            when (shortcut.type) {
                SettingsManager.LauncherShortcut.TYPE_APP -> {
                    if (shortcut.packageName != null) {
                        val success = executeShortcutCommand(shortcut) || launchApp(shortcut.packageName)
                        if (success) {
                            Log.d(TAG, "Launcher shortcut fired: key $keyCode -> ${shortcut.packageName}")
                            return true // Consumiamo l'evento
                        }
                    }
                }
                SettingsManager.LauncherShortcut.TYPE_SHORTCUT -> {
                    // TODO: Gestire scorciatoie in futuro
                    Log.d(TAG, "Tipo scorciatoia non ancora implementato: ${shortcut.type}")
                }
                SettingsManager.LauncherShortcut.TYPE_QUICK_LAUNCHER -> {
                    if (QuickLauncherOpener.open(context)) {
                        Log.d(TAG, "Quick launcher shortcut executed: key $keyCode")
                        return true
                    }
                }
                SettingsManager.LauncherShortcut.TYPE_COMMAND -> {
                    if (executeShortcutCommand(shortcut)) {
                        Log.d(TAG, "Command shortcut executed: key $keyCode -> ${shortcut.commandId}")
                        return true
                    }
                }
                else -> {
                    Log.d(TAG, "Tipo azione sconosciuto: ${shortcut.type}")
                }
            }
        } else {
            // Unassigned key: show the dialog for picking an app
            showLauncherShortcutAssignmentDialog(keyCode)
            return true // Consumiamo l'evento per evitare che venga gestito altrove
        }
        return false // Non consumiamo l'evento
    }

    /**
     * Shows the dialog for assigning an app to a key.
     */
    private fun showLauncherShortcutAssignmentDialog(keyCode: Int) {
        try {
            val intent = Intent(context, LauncherShortcutAssignmentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(LauncherShortcutAssignmentActivity.EXTRA_KEY_CODE, keyCode)
            }
            context.startActivity(intent)
            Log.d(TAG, "Assignment dialog shown for key $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show the assignment dialog", e)
        }
    }
    
    /**
     * Sets the callbacks that handle nav mode during power shortcuts.
     */
    fun setNavModeCallbacks(
        exitNavMode: () -> Unit,
        enterNavMode: () -> Unit
    ) {
        exitNavModeCallback = exitNavMode
        enterNavModeCallback = enterNavMode
    }
    
    /**
     * Toggles Power Shortcut mode (SYM held).
     * Already active: turns it off (edge case).
     * Returns true when the mode was turned on, false when turned off.
     * @param isNavModeActive whether nav mode is active when SYM is pressed
     */
    fun togglePowerShortcutMode(
        showToast: (String) -> Unit,
        isNavModeActive: Boolean = false
    ): Boolean {
        if (powerShortcutSymPressed) {
            // Edge case: already active, so turn it off
            resetPowerShortcutMode()
            Log.d(TAG, "Power Shortcut mode disattivato da SYM")
            return false
        }
        
        // Salva se nav mode era attivo e disabilitalo se necessario
        navModeWasActive = isNavModeActive
        if (isNavModeActive) {
            exitNavModeCallback?.invoke()
            Log.d(TAG, "Nav mode disabilitato per attivare Power Shortcut")
        }
        
        // Turn the mode on
        powerShortcutSymPressed = true
        Log.d(TAG, "Power Shortcut mode attivato")
        
        // Cancella timeout precedente se esiste
        cancelPowerShortcutTimeout()
        
        // Timeout to reset automatically; the toast shows only if the chord doesn't continue straight away.
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val message = context.getString(R.string.power_shortcuts_press_key)
        powerShortcutToastRunnable = Runnable {
            if (powerShortcutSymPressed) {
                showToast(message)
            }
        }
        handler.postDelayed(powerShortcutToastRunnable!!, 500L)
        powerShortcutTimeoutRunnable = Runnable {
            resetPowerShortcutMode()
        }
        powerShortcutTimeoutHandler = handler
        handler.postDelayed(powerShortcutTimeoutRunnable!!, POWER_SHORTCUT_TIMEOUT_MS)
        
        return true
    }
    
    /**
     * Resetta il Power Shortcut mode.
     * Se nav mode era attivo prima, lo riabilita.
     */
    fun resetPowerShortcutMode() {
        if (powerShortcutSymPressed) {
            powerShortcutSymPressed = false
            cancelPowerShortcutTimeout()
            Log.d(TAG, "Power Shortcut mode resettato")
            
            // Se nav mode era attivo prima, riabilitalo
            if (navModeWasActive) {
                enterNavModeCallback?.invoke()
                navModeWasActive = false
                Log.d(TAG, "Nav mode riabilitato dopo Power Shortcut")
            }
        }
    }
    
    /**
     * Checks whether Power Shortcut mode is active.
     */
    fun isPowerShortcutModeActive(): Boolean {
        return powerShortcutSymPressed
    }
    
    /**
     * Cancels the Power Shortcut mode timeout.
     */
    private fun cancelPowerShortcutTimeout() {
        powerShortcutToastRunnable?.let { runnable ->
            powerShortcutTimeoutHandler?.removeCallbacks(runnable)
        }
        powerShortcutTimeoutRunnable?.let { runnable ->
            powerShortcutTimeoutHandler?.removeCallbacks(runnable)
        }
        powerShortcutToastRunnable = null
        powerShortcutTimeoutRunnable = null
        powerShortcutTimeoutHandler = null
    }

    /**
     * Handles power shortcuts when SYM was pressed first.
     * Reuses the existing handleLauncherShortcut logic.
     * Returns true when the shortcut was handled, false otherwise.
     */
    fun handlePowerShortcut(keyCode: Int): Boolean {
        if (!isPowerShortcutModeActive()) {
            return false
        }
        
        // Reset the mode after use
        resetPowerShortcutMode()
        
        // Reuse the existing logic - same function, same assignments
        return handleLauncherShortcut(keyCode)
    }
}
