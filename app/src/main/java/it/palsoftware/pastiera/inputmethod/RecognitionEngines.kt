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
     * @param detail why someone would choose it — never a package name, which tells a reader
     *   nothing about how the engine behaves.
     * @param isSystemDefault whether this is the engine "System default" currently resolves to,
     *   so the picker can say so instead of listing the same engine twice unexplained.
     */
    data class Engine(
        val id: String,
        val label: String,
        val detail: String?,
        val isSystemDefault: Boolean = false
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
        val defaultPackage = systemDefaultComponent(context)?.packageName

        engines += Engine(
            id = SettingsManager.DICTATION_ENGINE_SYSTEM_DEFAULT,
            label = context.getString(R.string.dictation_engine_system_default),
            detail = defaultPackage
                ?.let { context.getString(R.string.dictation_engine_system_default_is, engineName(context, it)) }
                ?: context.getString(R.string.dictation_engine_system_default_detail)
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
                label = engineName(context, component.packageName),
                detail = describe(context, component.packageName),
                isSystemDefault = component.packageName == defaultPackage
            )
        }

        return engines
    }

    /**
     * A name someone would recognise. App labels are written for the app drawer, not for a
     * chooser — Google's ships as "Speech Recognition and Synthesis from Google", which says
     * nothing useful next to three other rows.
     */
    private fun engineName(context: Context, packageName: String): String = when (packageName) {
        PACKAGE_GOOGLE_SPEECH -> context.getString(R.string.dictation_engine_google)
        PACKAGE_SYSTEM_INTELLIGENCE -> context.getString(R.string.dictation_engine_asi)
        else -> appLabel(context, packageName) ?: packageName
    }

    /** What choosing it means in practice — the only thing that helps someone decide. */
    private fun describe(context: Context, packageName: String): String = when (packageName) {
        PACKAGE_GOOGLE_SPEECH -> context.getString(R.string.dictation_engine_google_detail)
        PACKAGE_SYSTEM_INTELLIGENCE -> context.getString(R.string.dictation_engine_asi_detail)
        PACKAGE_HOME_ASSISTANT -> context.getString(R.string.dictation_engine_home_assistant_detail)
        PACKAGE_CLAUDE -> context.getString(R.string.dictation_engine_claude_detail)
        else -> context.getString(R.string.dictation_engine_other_detail)
    }

    private const val PACKAGE_GOOGLE_SPEECH = "com.google.android.tts"
    private const val PACKAGE_SYSTEM_INTELLIGENCE = "com.google.android.as"
    private const val PACKAGE_HOME_ASSISTANT = "io.homeassistant.companion.android"
    private const val PACKAGE_CLAUDE = "com.anthropic.claude"

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
