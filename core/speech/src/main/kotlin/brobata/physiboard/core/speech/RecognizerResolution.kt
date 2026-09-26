package brobata.physiboard.core.speech

/** spec: dictation.md SS4.2's resolution table: what recognizer PhysiBoard should try to create. */
sealed interface RecognizerTarget {
    /** The platform's default recognizer (whatever `voice_recognition_service` names). */
    data object SystemDefault : RecognizerTarget

    /** The platform's on-device recognizer; only reached when the caller has confirmed one is available. */
    data object OnDevice : RecognizerTarget

    /** An installed recognition service, `package/class`. */
    data class Component(val flattenedName: String) : RecognizerTarget
}

/**
 * spec SS4.2: resolves a stored `dictation_engine` value to the recognizer target to try first.
 * [isComponentInstalled] answers whether [engineId] (when it is a `package/class` id) still names
 * an installed `RecognitionService`; [onDeviceAvailable] is the caller's already-gated answer for
 * "Android 12+ with an on-device recognizer available" (SS4.1 row 2), so this function needs no
 * `android.os.Build` import to make that call. The "any creation failure falls to the system
 * default" half of the table is `:ime`'s job, since only it can attempt the actual creation.
 */
object RecognizerResolution {
    fun resolve(engineId: String, onDeviceAvailable: Boolean, isComponentInstalled: (String) -> Boolean): RecognizerTarget = when {
        engineId == SpeechEngineCatalog.SYSTEM_DEFAULT -> RecognizerTarget.SystemDefault
        engineId == SpeechEngineCatalog.ON_DEVICE -> if (onDeviceAvailable) RecognizerTarget.OnDevice else RecognizerTarget.SystemDefault
        isComponentInstalled(engineId) -> RecognizerTarget.Component(engineId)
        else -> RecognizerTarget.SystemDefault
    }
}
