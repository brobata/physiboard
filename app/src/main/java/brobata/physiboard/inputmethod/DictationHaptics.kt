package brobata.physiboard.inputmethod

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import brobata.physiboard.SettingsManager

/**
 * The start/stop cues for dictation, in one place so the settings preview plays exactly what the
 * keyboard will.
 *
 * Every level runs at the hardware's maximum amplitude already, because the phone is usually on a
 * desk or held at arm's length while dictating and the default touch-feedback amplitude is easy to
 * miss. Past that ceiling there is nothing left to raise — so the levels differ in how LONG each
 * pulse runs, which is what actually reads as "firmer".
 */
object DictationHaptics {

    /** Two pulses: listening. */
    fun startEffect(strength: String): VibrationEffect = when (strength) {
        SettingsManager.DICTATION_HAPTIC_LIGHT -> waveform(35, 60, 180)
        SettingsManager.DICTATION_HAPTIC_STANDARD -> waveform(60, 70, 255)
        else -> waveform(150, 90, 255)
    }

    /** One pulse: stopped — always distinct from the start cue's two. */
    fun stopEffect(strength: String): VibrationEffect = when (strength) {
        SettingsManager.DICTATION_HAPTIC_LIGHT -> VibrationEffect.createOneShot(90, 180)
        SettingsManager.DICTATION_HAPTIC_STANDARD -> VibrationEffect.createOneShot(160, 255)
        else -> VibrationEffect.createOneShot(300, 255)
    }

    /**
     * Plays a cue immediately, for the settings preview: the only way to judge a haptic is to
     * feel it, and picking one blind is how the wrong level gets chosen.
     */
    fun play(context: Context, strength: String, started: Boolean = true) {
        val effect = if (started) startEffect(strength) else stopEffect(strength)
        // Plain vibrate(): notification-class attributes are muted whenever the phone's
        // notification vibration is off, which silenced the cues entirely in 1.0.4.
        vibrator(context)?.vibrate(effect)
    }

    fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun waveform(pulseMs: Long, gapMs: Long, amplitude: Int): VibrationEffect =
        VibrationEffect.createWaveform(
            longArrayOf(0, pulseMs, gapMs, pulseMs),
            intArrayOf(0, amplitude, 0, amplitude),
            -1
        )
}
