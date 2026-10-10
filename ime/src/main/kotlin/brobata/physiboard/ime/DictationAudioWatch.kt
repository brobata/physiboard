package brobata.physiboard.ime

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import brobata.physiboard.core.speech.SessionAudioRoute
import java.util.concurrent.Executor

/**
 * What the phone's audio is doing around a dictation session, for the two things that differ in
 * a car (dictation.md SS6.10): whether the microphone the recognizer gets is a Bluetooth one that
 * is still coming up, and whether a phone call has started. It also writes every audio fact
 * worth having after a failed drive to the always-on trace: the routes in and out at the start
 * and whenever they change, the recordings the system reports (their input device and whether
 * they are silenced), media playback, the audio mode and every focus change. Sizes and types
 * only; no audio, no words, and no other app's identity (the platform anonymises those anyway).
 *
 * The keyboard never starts Bluetooth SCO, never sets a communication device and never routes
 * anything: the recognizer's input is the audio policy's choice, and fighting Android Auto or a
 * head unit for the microphone would only make it worse. This only watches.
 *
 * Everything runs on [handler]'s (main) thread; [start] and [stop] bracket one session.
 */
internal class DictationAudioWatch(
    private val context: Context,
    private val handler: Handler,
) {
    /** The route looks like a Bluetooth microphone still coming up; the start cue should wait. */
    var onRouteSettling: (() -> Unit)? = null

    /** The input route is up (or was never going to be Bluetooth); the cue may play. */
    var onRouteSettled: (() -> Unit)? = null

    /** A phone call is ringing or running; the session ends. */
    var onCallStarted: (() -> Unit)? = null

    private val audioManager: AudioManager? by lazy { runCatching { context.getSystemService(AudioManager::class.java) }.getOrNull() }
    private val executor = Executor { runnable -> handler.post(runnable) }

    private var active = false
    private var settling = false
    private var scoState = AudioManager.SCO_AUDIO_STATE_DISCONNECTED
    private var lastRecordingSummary = ""
    private var lastPlaybackSummary = ""
    private var lastRouteSummary = ""
    private var lastListenStartMs = 0L

    /** Recordings already running when the session started (another app's, a hotword); they say nothing about the recognizer's microphone. */
    private var recordingsBefore: Set<Int> = emptySet()

    /** The route class at the start of a session, for the engine's first-words grace (SS6.10). */
    fun currentRoute(): SessionAudioRoute {
        val car = runCatching { context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR }.getOrDefault(false)
        if (car) return SessionAudioRoute.CAR
        val devices = devices(AudioManager.GET_DEVICES_ALL)
        return if (devices.any { it.type in BLUETOOTH_TYPES }) SessionAudioRoute.BLUETOOTH else SessionAudioRoute.LOCAL
    }

    /** [route] is what [currentRoute] said at the trigger, reused for the trace. */
    fun start(route: SessionAudioRoute) {
        if (active) return
        active = true
        settling = false
        lastRecordingSummary = ""
        lastPlaybackSummary = ""
        lastRouteSummary = ""
        val manager = audioManager
        if (manager == null) {
            trace("audio no AudioManager")
            return
        }
        // The SCO state broadcast is sticky: registering returns the current state at once.
        val sticky = runCatching {
            ContextCompat.registerReceiver(context, scoReceiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED), ContextCompat.RECEIVER_NOT_EXPORTED)
        }.getOrNull()
        scoState = sticky?.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_DISCONNECTED) ?: AudioManager.SCO_AUDIO_STATE_DISCONNECTED
        recordingsBefore = runCatching { manager.activeRecordingConfigurations.map { it.clientAudioSessionId }.toSet() }.getOrDefault(emptySet())
        runCatching { manager.registerAudioDeviceCallback(deviceCallback, handler) }
        runCatching { manager.registerAudioRecordingCallback(recordingCallback, handler) }
        runCatching { manager.registerAudioPlaybackCallback(playbackCallback, handler) }
        runCatching { manager.addOnModeChangedListener(executor, modeListener) }
        lastRouteSummary = routeSummary()
        trace("audio start route=${route.name} $lastRouteSummary sco=${scoName(scoState)} mode=${modeName(manager.mode)} ${playbackSummary()}")
        // A Bluetooth microphone (a head unit's hands-free link) is connected: the recognizer may
        // get it, and its link takes about a second to come up, all of it silence. Wait for the
        // first recording to say which microphone it actually has (recordingCallback).
        if (scoState == AudioManager.SCO_AUDIO_STATE_CONNECTING || devices(AudioManager.GET_DEVICES_INPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }) {
            settling = true
            trace("audio route settling (Bluetooth microphone present)")
            onRouteSettling?.invoke()
        }
        // A call already running is only noted: the session was started on purpose during it, and
        // only a call that starts under a session ends it (modeListener).
        if (callActive()) trace("audio call already active at start")
    }

    fun stop() {
        if (!active) return
        active = false
        settling = false
        val manager = audioManager ?: return
        runCatching { context.unregisterReceiver(scoReceiver) }
        runCatching { manager.unregisterAudioDeviceCallback(deviceCallback) }
        runCatching { manager.unregisterAudioRecordingCallback(recordingCallback) }
        runCatching { manager.unregisterAudioPlaybackCallback(playbackCallback) }
        runCatching { manager.removeOnModeChangedListener(modeListener) }
        trace("audio stop ${playbackSummary()}")
    }

    /** A request was just issued to the recognizer; a focus loss soon after is most likely its own. */
    fun noteListenStarted() {
        lastListenStartMs = SystemClock.uptimeMillis()
    }

    /** True while the telephony side has a call ringing or running (not a VoIP app's communication mode, which a Bluetooth recording can set too). */
    fun callActive(): Boolean = isCallMode(audioManager?.mode)

    private fun isCallMode(mode: Int?): Boolean = when (mode) {
        AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_CALL_SCREENING -> true
        else -> false
    }

    /** Traces one focus change with what can be said about where it came from. */
    fun traceFocusChange(change: Int, ownRequestHeld: Boolean) {
        val sinceListen = SystemClock.uptimeMillis() - lastListenStartMs
        val source = when {
            callActive() -> "call"
            change == AudioManager.AUDIOFOCUS_GAIN -> "returned"
            sinceListen in 0..RECOGNIZER_FOCUS_WINDOW_MS && change != AudioManager.AUDIOFOCUS_LOSS -> "recognizer"
            audioManager?.isMusicActive == true -> "media"
            else -> "other"
        }
        trace("audio focus ${focusName(change)} source=$source sinceListenMs=$sinceListen held=$ownRequestHeld mode=${modeName(audioManager?.mode ?: -1)} ${playbackSummary()}")
    }

    fun traceFocusRequest(result: Int, retake: Boolean) {
        trace("audio focus request ${if (retake) "retake" else "start"} result=${when (result) { AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "granted"; AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "delayed"; else -> "failed" }} ${playbackSummary()}")
    }

    private fun settle(reason: String) {
        if (!settling) return
        settling = false
        trace("audio route settled ($reason)")
        onRouteSettled?.invoke()
    }

    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!active) return
            val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
            if (state == scoState) return
            scoState = state
            trace("audio sco ${scoName(state)}")
            when (state) {
                AudioManager.SCO_AUDIO_STATE_CONNECTED -> settle("Bluetooth microphone link up")
                AudioManager.SCO_AUDIO_STATE_CONNECTING -> if (!settling) {
                    // Someone (the recognizer, Android Auto) is bringing a Bluetooth microphone up
                    // mid-session; the words already flowing are not held, only logged.
                    trace("audio Bluetooth microphone link coming up mid-session")
                }
                AudioManager.SCO_AUDIO_STATE_ERROR, AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> settle("Bluetooth microphone link ${scoName(state)}")
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = routeChanged()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = routeChanged()
    }

    private fun routeChanged() {
        if (!active) return
        val summary = routeSummary()
        if (summary == lastRouteSummary) return
        lastRouteSummary = summary
        trace("audio route changed $summary sco=${scoName(scoState)}")
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            if (!active) return
            val list = configs.orEmpty()
            val summary = list.joinToString(",") { config ->
                val device = runCatching { config.audioDevice?.type }.getOrNull()
                val silenced = config.isClientSilenced
                "src${config.clientAudioSource}:${typeName(device)}${if (silenced) ":SILENCED" else ""}"
            }
            if (summary != lastRecordingSummary) {
                lastRecordingSummary = summary
                trace("audio recordings [${summary}]")
            }
            if (!settling || list.isEmpty()) return
            val types = list.filter { it.clientAudioSessionId !in recordingsBefore }.mapNotNull { runCatching { it.audioDevice?.type }.getOrNull() }
            when {
                types.isEmpty() -> Unit
                types.none { it == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } -> settle("recording on ${typeName(types.first())}")
                scoState == AudioManager.SCO_AUDIO_STATE_CONNECTED -> settle("recording on a connected Bluetooth microphone")
            }
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (!active) return
            val summary = playbackSummary(configs.orEmpty())
            if (summary == lastPlaybackSummary) return
            lastPlaybackSummary = summary
            trace("audio playback $summary")
        }
    }

    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        if (!active) return@OnModeChangedListener
        trace("audio mode ${modeName(mode)}")
        if (isCallMode(mode)) onCallStarted?.invoke()
    }

    private fun devices(flags: Int): List<AudioDeviceInfo> = runCatching { audioManager?.getDevices(flags)?.toList() }.getOrNull().orEmpty()

    private fun routeSummary(): String {
        val inputs = devices(AudioManager.GET_DEVICES_INPUTS).map { typeName(it.type) }.distinct().sorted()
        val outputs = devices(AudioManager.GET_DEVICES_OUTPUTS).map { typeName(it.type) }.distinct().sorted()
        val comm = runCatching { audioManager?.communicationDevice?.type }.getOrNull()
        return "in=$inputs out=$outputs comm=${typeName(comm)}"
    }

    private fun playbackSummary(configs: List<AudioPlaybackConfiguration>? = null): String {
        val list = configs ?: runCatching { audioManager?.activePlaybackConfigurations }.getOrNull().orEmpty()
        val media = list.count { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }
        return "music=${audioManager?.isMusicActive == true} players=${list.size} media=$media"
    }

    private fun trace(line: String) = DictationTrace.audio(line)

    private companion object {
        /** A transient loss this soon after a request is the recognizer's own focus request (D14: within a few ms on the Titan). */
        const val RECOGNIZER_FOCUS_WINDOW_MS = 400L

        val BLUETOOTH_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST,
        )

        fun typeName(type: Int?): String = when (type) {
            null -> "none"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "builtin_mic"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
            AudioDeviceInfo.TYPE_TELEPHONY -> "telephony"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt_sco"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bt_a2dp"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "ble_headset"
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> "ble_speaker"
            AudioDeviceInfo.TYPE_BLE_BROADCAST -> "ble_broadcast"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "usb_device"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "usb_accessory"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "usb_headset"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired_headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired_headphones"
            AudioDeviceInfo.TYPE_BUS -> "bus"
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "remote_submix"
            AudioDeviceInfo.TYPE_FM_TUNER -> "fm_tuner"
            else -> "type$type"
        }

        fun scoName(state: Int): String = when (state) {
            AudioManager.SCO_AUDIO_STATE_CONNECTED -> "connected"
            AudioManager.SCO_AUDIO_STATE_CONNECTING -> "connecting"
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> "disconnected"
            else -> "error"
        }

        fun modeName(mode: Int): String = when (mode) {
            AudioManager.MODE_NORMAL -> "normal"
            AudioManager.MODE_RINGTONE -> "ringtone"
            AudioManager.MODE_IN_CALL -> "in_call"
            AudioManager.MODE_IN_COMMUNICATION -> "in_communication"
            AudioManager.MODE_CALL_SCREENING -> "call_screening"
            else -> "mode$mode"
        }

        fun focusName(change: Int): String = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
            AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
            else -> "change$change"
        }
    }
}
