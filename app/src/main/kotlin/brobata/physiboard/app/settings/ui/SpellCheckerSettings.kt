package brobata.physiboard.app.settings.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import android.util.Log
import android.view.textservice.TextServicesManager
import brobata.physiboard.core.toolbox.SpellCheckerOwner
import brobata.physiboard.core.toolbox.SpellCheckerSelection

/**
 * The Auto-correction screen's "System spell checker" row (autocorrect-suggestions.md SS18): whether
 * PhysiBoard is the spell checker Android uses, and the way to Android's own picker.
 */
object SpellCheckerSettings {

    /** Android has no public action for the spell checker picker; this is the screen AOSP Settings declares for it (present on the Titan, Android 16). */
    private val SPELL_CHECKER_SCREEN = ComponentName("com.android.settings", "com.android.settings.Settings\$SpellCheckersSettingsActivity")

    enum class State { OURS, OTHER, OFF }

    fun state(context: Context): State {
        val manager = context.getSystemService(TextServicesManager::class.java) ?: return State.OFF
        return runCatching {
            when {
                !manager.isSpellCheckerEnabled -> State.OFF
                manager.currentSpellCheckerInfo?.packageName == context.packageName -> State.OURS
                else -> State.OTHER
            }
        }.getOrDefault(State.OFF)
    }

    /**
     * Whether Home's status card should offer "Turn on spell checking" (app-shell.md SS6.3): the
     * same rules the paired setup pass decides by ([SpellCheckerSelection.wouldSelect]), from what
     * the app can see without a shell. A spell checker someone installed is their choice and is
     * never nagged about; the phone's own preinstalled one is the factory default.
     */
    fun shouldOfferTurnOn(context: Context, autoSelect: Boolean): Boolean {
        if (!autoSelect) return false
        val manager = context.getSystemService(TextServicesManager::class.java) ?: return false
        return runCatching {
            val info = manager.currentSpellCheckerInfo
            val owner = when {
                info == null -> SpellCheckerOwner.NONE
                info.packageName == context.packageName -> SpellCheckerOwner.OURS
                (info.serviceInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0 -> SpellCheckerOwner.PREINSTALLED
                else -> SpellCheckerOwner.USER_INSTALLED
            }
            SpellCheckerSelection.wouldSelect(autoSelect = true, enabled = manager.isSpellCheckerEnabled, owner = owner)
        }.getOrDefault(false)
    }

    fun description(state: State): String = when (state) {
        State.OURS -> "On. Apps underline misspellings with PhysiBoard's dictionary; tap an underlined word for its corrections."
        State.OTHER -> "Another spell checker is selected. Pick PhysiBoard in Android's spell checker settings so apps underline misspellings and offer PhysiBoard's corrections."
        State.OFF -> "Off. Turn on Android's spell checker and pick PhysiBoard so apps underline misspellings and offer PhysiBoard's corrections."
    }

    /** Opens Android's spell checker picker, or the nearest screen this phone has to it. */
    fun open(context: Context) {
        val attempts = listOf(
            Intent(Intent.ACTION_MAIN).setComponent(SPELL_CHECKER_SCREEN),
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in attempts) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                Log.w("PhysiBoardSpell", "spell checker settings refused", e)
                continue
            }
        }
    }
}
