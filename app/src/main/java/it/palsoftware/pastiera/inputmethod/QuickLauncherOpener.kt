package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import it.palsoftware.pastiera.SettingsManager

object QuickLauncherOpener {
    private const val TAG = "QuickLauncherOpener"
    private const val NIAGARA_PACKAGE = "bitpit.launcher"
    private val NIAGARA_SEARCH_URI: Uri = Uri.parse("niagara://search")

    fun open(context: Context): Boolean {
        return when (SettingsManager.getQuickLauncherBehavior(context)) {
            SettingsManager.QUICK_LAUNCHER_BEHAVIOR_NIAGARA ->
                openNiagaraSearch(context) || openPastieraQuickLauncher(context)
            else -> openPastieraQuickLauncher(context)
        }
    }

    private fun openPastieraQuickLauncher(context: Context): Boolean {
        return try {
            val intent = Intent(context, QuickLauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            true
        } catch (error: Exception) {
            Log.e(TAG, "Error opening Pastiera QuickLauncher", error)
            false
        }
    }

    private fun openNiagaraSearch(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, NIAGARA_SEARCH_URI).apply {
                setPackage(NIAGARA_PACKAGE)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Error opening Niagara search; falling back to Pastiera QuickLauncher", error)
            false
        }
    }
}
