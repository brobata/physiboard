package brobata.physiboard.app.settings.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.textservice.TextServicesManager

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
