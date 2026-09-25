package brobata.physiboard.core.toolbox

/** The three animation-scale chips. spec: broker-privileged-toolbox.md SS14. */
enum class AnimationSpeed(val scale: Float) {
    OFF(0f), FAST(0.5f), NORMAL(1f);

    companion object {
        /** spec: SS14 ("snapped to the nearest of Off (0), Fast (0.5), Normal (1.0)"); T27. */
        fun nearest(scale: Float): AnimationSpeed = entries.minByOrNull { kotlin.math.abs(it.scale - scale) } ?: NORMAL
    }
}

/** What the three-line tweak read reported. spec: SS14 ("Read"); T26, T28. */
data class TweaksReading(val animation: AnimationSpeed, val notificationHistoryOn: Boolean, val oneHandedOn: Boolean)

/**
 * System tweaks' read/write lines and the pure parsing of their output. Every write is a single
 * self-contained shell line the broker sends once; there is no persisted state of its own (SS14,
 * "Controls with no preference of their own... they read and write the phone").
 *
 * spec: broker-privileged-toolbox.md SS14; T26 to T30.
 */
object SystemTweaks {
    const val ANIMATION_KEY = "window_animation_scale"
    private const val TRANSITION_KEY = "transition_animation_scale"
    private const val ANIMATOR_KEY = "animator_duration_scale"
    const val NOTIFICATION_HISTORY_KEY = "notification_history_enabled"
    const val ONE_HANDED_KEY = "one_handed_enabled"

    /** spec: SS14 ("Read"): one joined line, three `settings get` calls. */
    const val READ_LINE = "settings get global $ANIMATION_KEY; settings get secure $NOTIFICATION_HISTORY_KEY; settings get secure $ONE_HANDED_KEY"

    /** spec: SS14 ("the string `null`... is the shipped state, not an error"). */
    private fun isToggleOn(line: String?): Boolean = line?.trim() == "1"

    /** spec: SS14 ("the first line is parsed as a float (unparseable = 1.0)"). */
    private fun parseAnimationScale(line: String?): Float = line?.trim()?.toFloatOrNull() ?: 1.0f

    /**
     * spec: SS14 ("Read"); T26, T28. Blank lines are dropped before the three are matched
     * positionally, so a device that prints nothing for a key still lines the others up. Null
     * when the output carries no line at all (T28, "empty output").
     */
    fun parse(rawOutput: String): TweaksReading? {
        val lines = rawOutput.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val animationLine = lines.getOrNull(0)
        val historyLine = lines.getOrNull(1)
        val oneHandedLine = lines.getOrNull(2)
        return TweaksReading(
            animation = AnimationSpeed.nearest(parseAnimationScale(animationLine)),
            notificationHistoryOn = isToggleOn(historyLine),
            oneHandedOn = isToggleOn(oneHandedLine),
        )
    }

    /** spec: SS14 ("Selecting one writes all three globals... in one line"). */
    fun animationLine(speed: AnimationSpeed): String {
        val value = speed.scale
        return "settings put global $ANIMATION_KEY $value; settings put global $TRANSITION_KEY $value; settings put global $ANIMATOR_KEY $value"
    }

    /** spec: SS14 ("writes... 1", "sends `settings delete`... rather than writing 0"); T29. */
    fun toggleLine(key: String, on: Boolean): String = if (on) "settings put secure $key 1" else "settings delete secure $key"

    /** spec: SS14 ("'Put all of these back to stock'"); T30. */
    val resetAllLine: String =
        "settings put global $ANIMATION_KEY 1.0; settings put global $TRANSITION_KEY 1.0; settings put global $ANIMATOR_KEY 1.0; " +
            "settings delete secure $NOTIFICATION_HISTORY_KEY; settings delete secure $ONE_HANDED_KEY"
}
