package brobata.physiboard.core.speech

/** `dictation_haptic_strength`'s three levels. spec: dictation.md SS8.1; "any stored value other than `light` or `standard` plays the strong pattern" is the caller's parse rule. */
enum class CueStrength { LIGHT, STANDARD, STRONG }

/**
 * One vibration as a waveform: [timingsMs] alternates off and on segments starting with an off
 * segment of 0, and [amplitudes] gives each segment's strength (0 for the gaps), which is the
 * shape a platform waveform call takes without this module naming the platform.
 */
data class CuePattern(val timingsMs: List<Long>, val amplitudes: List<Int>) {
    init {
        require(timingsMs.size == amplitudes.size) { "one amplitude per timing segment" }
    }
}

/**
 * The start and stop cues. spec: dictation.md SS8.1: "Every level runs at the hardware maximum
 * amplitude 255 except Light; the levels differ in pulse length, which is what reads as
 * 'firmer'" (D10), the table of two-pulse start cues and one-pulse stop cues, and the double
 * gate "only when `dictation_haptics` is on and the system's own haptic feedback toggle... is on".
 */
object DictationCues {
    private const val FULL_AMPLITUDE = 255
    private const val LIGHT_AMPLITUDE = 180

    /** spec SS8.1: both switches must be on; the system toggle "read as on when unreadable" is the caller's parse rule. */
    fun shouldPlay(hapticsEnabled: Boolean, systemHapticsEnabled: Boolean): Boolean = hapticsEnabled && systemHapticsEnabled

    /** spec SS8.1's "Start cue (two pulses)" column. */
    fun startCue(strength: CueStrength): CuePattern = when (strength) {
        CueStrength.LIGHT -> twoPulses(35, 60, LIGHT_AMPLITUDE)
        CueStrength.STANDARD -> twoPulses(60, 70, FULL_AMPLITUDE)
        CueStrength.STRONG -> twoPulses(150, 90, FULL_AMPLITUDE)
    }

    /** spec SS8.1's "Stop cue (one pulse)" column. */
    fun stopCue(strength: CueStrength): CuePattern = when (strength) {
        CueStrength.LIGHT -> onePulse(90, LIGHT_AMPLITUDE)
        CueStrength.STANDARD -> onePulse(160, FULL_AMPLITUDE)
        CueStrength.STRONG -> onePulse(300, FULL_AMPLITUDE)
    }

    private fun twoPulses(pulseMs: Long, gapMs: Long, amplitude: Int): CuePattern =
        CuePattern(listOf(0L, pulseMs, gapMs, pulseMs), listOf(0, amplitude, 0, amplitude))

    private fun onePulse(pulseMs: Long, amplitude: Int): CuePattern = CuePattern(listOf(0L, pulseMs), listOf(0, amplitude))
}
