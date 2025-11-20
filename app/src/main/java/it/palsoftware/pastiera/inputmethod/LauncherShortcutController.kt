package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import it.palsoftware.pastiera.SettingsManager

/**
 * Controller for handling launcher shortcuts functionality.
 * Manages app launching, launcher detection, and shortcut assignment dialogs.
 */
class LauncherShortcutController(
    private val context: Context
) {
    companion object {
        private const val TAG = "PastieraInputMethod"
    }

    // Cache for launcher packages
    private var cachedLauncherPackages: Set<String>? = null

    /**
     * Verifica se il package corrente è un launcher.
     */
    fun isLauncher(packageName: String?): Boolean {
        if (packageName == null) return false
        
        // Cache la lista dei launcher per evitare query ripetute
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
                Log.e(TAG, "Errore nel rilevamento dei launcher", e)
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
                Log.w(TAG, "Nessun launch intent trovato per: $packageName")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'apertura dell'app $packageName", e)
            return false
        }
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
                        val success = launchApp(shortcut.packageName)
                        if (success) {
                            Log.d(TAG, "Scorciatoia launcher eseguita: tasto $keyCode -> ${shortcut.packageName}")
                            return true // Consumiamo l'evento
                        }
                    }
                }
                SettingsManager.LauncherShortcut.TYPE_SHORTCUT -> {
                    // TODO: Gestire scorciatoie in futuro
                    Log.d(TAG, "Tipo scorciatoia non ancora implementato: ${shortcut.type}")
                }
                else -> {
                    Log.d(TAG, "Tipo azione sconosciuto: ${shortcut.type}")
                }
            }
        } else {
            // Tasto non assegnato: mostra dialog per assegnare un'app
            showLauncherShortcutAssignmentDialog(keyCode)
            return true // Consumiamo l'evento per evitare che venga gestito altrove
        }
        return false // Non consumiamo l'evento
    }
    
    /**
     * Mostra il dialog per assegnare un'app a un tasto.
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
            Log.d(TAG, "Dialog assegnazione mostrato per tasto $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel mostrare il dialog di assegnazione", e)
        }
    }
}

