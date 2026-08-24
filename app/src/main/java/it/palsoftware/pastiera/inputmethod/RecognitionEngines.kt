package it.palsoftware.pastiera.inputmethod

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager

/**
 * The speech services dictation can be routed through.
 *
 * Android picks one recognizer for the whole device (`Settings.Secure.voice_recognition_service`),
 * but [SpeechRecognizer] lets a caller bypass that choice per session. Engines differ in how they
 * end an utterance, whether they need the network and how well they punctuate, so PhysiBoard
 * exposes the choice rather than inheriting whatever the vendor shipped.
 */
object RecognitionEngines {
    private const val TAG = "RecognitionEngines"

    /**
     * @param id the value stored in [SettingsManager.getDictationEngine].
     * @param label what the picker shows.
     * @param detail the package or service backing it, or null for the abstract choices.
     */
    data class Engine(
        val id: String,
        val label: String,
        val detail: String?
    )

    /** The component the system currently routes voice input to, or null if unset/unreadable. */
    fun systemDefaultComponent(context: Context): ComponentName? {
        val flattened = runCatching {
            Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
        }.getOrNull()
        return flattened?.takeIf { it.isNotEmpty() }?.let { ComponentName.unflattenFromString(it) }
    }

    /** Every installed [RecognitionService], plus the "system default" and on-device choices. */
    fun available(context: Context): List<Engine> {
        val engines = mutableListOf<Engine>()

        val defaultLabel = systemDefaultComponent(context)
            ?.let { appLabel(context, it.packageName) }
        engines += Engine(
            id = SettingsManager.DICTATION_ENGINE_SYSTEM_DEFAULT,
            label = context.getString(R.string.dictation_engine_system_default),
            detail = defaultLabel
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            engines += Engine(
                id = SettingsManager.DICTATION_ENGINE_ON_DEVICE,
                label = context.getString(R.string.dictation_engine_on_device),
                detail = context.getString(R.string.dictation_engine_on_device_detail)
            )
        }

        installedServices(context).forEach { component ->
            engines += Engine(
                id = component.flattenToString(),
                label = appLabel(context, component.packageName) ?: component.packageName,
                detail = component.packageName
            )
        }

        return engines
    }

    /**
     * Resolves a stored id to a live recognizer, falling back to the system default whenever the
     * chosen engine has been uninstalled or turns out to be unavailable — dictation going silent
     * because an engine disappeared would be far worse than quietly using another one.
     */
    fun createRecognizer(context: Context, engineId: String): SpeechRecognizer? {
        return runCatching {
            when {
                engineId == SettingsManager.DICTATION_ENGINE_ON_DEVICE &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

                engineId.isNotEmpty() -> {
                    val component = ComponentName.unflattenFromString(engineId)
                    if (component != null && installedServices(context).contains(component)) {
                        SpeechRecognizer.createSpeechRecognizer(context, component)
                    } else {
                        Log.w(TAG, "Engine $engineId is gone — using the system default")
                        SpeechRecognizer.createSpeechRecognizer(context)
                    }
                }

                else -> SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.onFailure {
            Log.e(TAG, "Unable to create recognizer for '$engineId'", it)
        }.getOrNull() ?: runCatching {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull()
    }

    /** The picker label for a stored id, for summary rows. */
    fun labelFor(context: Context, engineId: String): String {
        return available(context).firstOrNull { it.id == engineId }?.label
            ?: context.getString(R.string.dictation_engine_system_default)
    }

    private fun installedServices(context: Context): List<ComponentName> {
        val intent = Intent(RecognitionService.SERVICE_INTERFACE)
        return runCatching {
            context.packageManager
                .queryIntentServices(intent, PackageManager.GET_META_DATA)
                .mapNotNull { resolved ->
                    resolved.serviceInfo?.let { ComponentName(it.packageName, it.name) }
                }
        }.getOrElse {
            Log.w(TAG, "Unable to list recognition services", it)
            emptyList()
        }
    }

    private fun appLabel(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}
