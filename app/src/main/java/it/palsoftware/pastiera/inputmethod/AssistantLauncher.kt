package it.palsoftware.pastiera.inputmethod

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Log
import it.palsoftware.pastiera.SettingsManager

/**
 * Opens the device's voice assistant already listening, the way holding a phone's side button
 * does — not the assistant's app icon, which would just put a text box on screen.
 *
 * Android has no single "start the assistant listening" API. Three public intents come close and
 * which one an assistant honours varies by version, so they are tried in order and the first that
 * resolves wins.
 */
object AssistantLauncher {
    private const val TAG = "AssistantLauncher"

    /**
     * The request PhysiBoard sends. No intent reproduces the system assist gesture — that calls
     * the VoiceInteractionService session directly and is closed to apps — so these are the
     * public approximations, and whether one opens *listening* or merely opens the assistant is
     * the assistant app's own decision. That varies by device and by version, which is why the
     * choice is the user's rather than a constant.
     */
    private fun actionFor(mode: String): String? = when (mode) {
        SettingsManager.ASSISTANT_ACTION_VOICE_COMMAND -> Intent.ACTION_VOICE_COMMAND
        SettingsManager.ASSISTANT_ACTION_ASSIST -> Intent.ACTION_ASSIST
        SettingsManager.ASSISTANT_ACTION_HANDS_FREE -> RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE
        else -> null
    }

    /**
     * Automatic order, listening first: VOICE_COMMAND and the hands-free action both exist to
     * take a spoken question immediately, whereas ASSIST only promises to open the assistant.
     */
    private val AUTO_ORDER = listOf(
        Intent.ACTION_VOICE_COMMAND,
        RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE,
        Intent.ACTION_ASSIST
    )

    /** @return true once an assistant has been started. */
    fun launch(context: Context): Boolean {
        // The chosen action first, then the rest — so picking one that this device cannot handle
        // degrades to "something opened" instead of to nothing happening.
        val chosen = actionFor(SettingsManager.getAssistantAction(context))
        val actions = (listOfNotNull(chosen) + AUTO_ORDER).distinct()

        // Aim at the chosen assistant explicitly. Several apps typically register these actions,
        // so an untargeted intent resolves to the system chooser — a dialog to dismiss instead of
        // an assistant listening, which defeats the point of a hold-to-ask key.
        val assistant = assistantPackage(context)
        if (assistant != null && actions.any { start(context, it, assistant) }) {
            return true
        }
        // No assistant configured, or it handles none of these: fall back to whoever does, even
        // if that means the user picks from a chooser once.
        if (actions.any { start(context, it, null) }) {
            return true
        }
        Log.w(TAG, "No assistant available for any voice action")
        return false
    }

    /** Whether anything can handle a voice action, for settings to warn ahead of time. */
    fun isAvailable(context: Context): Boolean = AUTO_ORDER.any { action ->
        Intent(action).resolveActivity(context.packageManager) != null
    }

    private fun start(context: Context, action: String, packageName: String?): Boolean {
        val intent = Intent(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .apply { packageName?.let { setPackage(it) } }
        if (intent.resolveActivity(context.packageManager) == null) {
            Log.d(TAG, "No handler for $action in ${packageName ?: "any package"}")
            return false
        }
        return runCatching { context.startActivity(intent) }
            .onSuccess { Log.d(TAG, "Assistant started via $action (${packageName ?: "chooser"})") }
            .onFailure { Log.w(TAG, "Unable to start $action", it) }
            .isSuccess
    }

    /**
     * The package of the assistant the user has chosen. Read from the secure settings the role is
     * mirrored into; the RoleManager query itself is not open to ordinary apps.
     */
    private fun assistantPackage(context: Context): String? {
        val flattened = ASSISTANT_SETTINGS.firstNotNullOfOrNull { key ->
            runCatching { Settings.Secure.getString(context.contentResolver, key) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
        } ?: return null
        // Stored as a flattened component, but a bare package name is also valid here.
        return ComponentName.unflattenFromString(flattened)?.packageName ?: flattened
    }

    private val ASSISTANT_SETTINGS = listOf("assistant", "voice_interaction_service")
}
