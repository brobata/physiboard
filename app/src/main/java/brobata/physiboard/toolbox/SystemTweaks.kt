package brobata.physiboard.toolbox

import android.content.Context
import brobata.physiboard.inputmethod.EmbeddedAdbShell

/**
 * Small system settings that Android supports but this ROM never surfaces.
 *
 * Unlike density these cannot make the phone unusable, so they apply directly rather than
 * through [RevertibleChange] — a countdown on "make animations faster" would be theatre. What
 * they do share is that every one is a plain settings write the broker can also undo, which
 * is why each carries the exact command that puts it back.
 *
 * BLOCKING — call off the main thread.
 */
object SystemTweaks {

    /**
     * Android animates in three places and reads three separate globals. Setting one and not
     * the others is the classic half-done version of this tweak, where menus feel snappy and
     * app launches still crawl.
     */
    private val ANIMATION_KEYS = listOf(
        "window_animation_scale",
        "transition_animation_scale",
        "animator_duration_scale"
    )

    enum class AnimationSpeed(val scale: Float) {
        OFF(0f),
        FAST(0.5f),
        NORMAL(1f);

        companion object {
            fun nearest(scale: Float): AnimationSpeed =
                entries.minByOrNull { kotlin.math.abs(it.scale - scale) } ?: NORMAL
        }
    }

    /** A boolean the platform understands but this ROM leaves unset. */
    enum class Toggle(val namespace: String, val key: String) {
        /** Pixel's notification history: the log of what you swiped away. */
        NOTIFICATION_HISTORY("secure", "notification_history_enabled"),
        /** Swipe down on the nav bar to pull the top of the screen into reach. */
        ONE_HANDED_MODE("secure", "one_handed_enabled")
    }

    data class State(
        val animation: AnimationSpeed,
        val toggles: Map<Toggle, Boolean>
    )

    fun read(context: Context): State? {
        if (!EmbeddedAdbShell.isPaired(context)) return null
        val reads = buildList {
            add("settings get global ${ANIMATION_KEYS.first()}")
            Toggle.entries.forEach { add("settings get ${it.namespace} ${it.key}") }
        }
        if (!runCatching { EmbeddedAdbShell.runShell(context, reads.joinToString("; ")) }
                .getOrDefault(false)
        ) {
            return null
        }
        val lines = EmbeddedAdbShell.lastResult.orEmpty()
            .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return null

        val animation = AnimationSpeed.nearest(lines.first().toFloatOrNull() ?: 1f)
        val toggles = Toggle.entries.mapIndexed { index, toggle ->
            // "null" is what settings prints for a key that was never written — which for
            // these two is the shipped state, not an error.
            toggle to (lines.getOrNull(index + 1)?.trim() == "1")
        }.toMap()
        return State(animation = animation, toggles = toggles)
    }

    fun setAnimationSpeed(context: Context, speed: AnimationSpeed): Boolean {
        val command = ANIMATION_KEYS.joinToString("; ") { "settings put global $it ${speed.scale}" }
        return runCatching { EmbeddedAdbShell.runShell(context, command) }.getOrDefault(false)
    }

    /**
     * Turning one of these off DELETES the key rather than writing 0: these ship unset, so
     * restoring means putting them back to absent, not to an explicit false the ROM never had.
     */
    fun setToggle(context: Context, toggle: Toggle, enabled: Boolean): Boolean {
        val command = if (enabled) {
            "settings put ${toggle.namespace} ${toggle.key} 1"
        } else {
            "settings delete ${toggle.namespace} ${toggle.key}"
        }
        return runCatching { EmbeddedAdbShell.runShell(context, command) }.getOrDefault(false)
    }

    /** Everything on this screen back to how the phone shipped. */
    fun resetAll(context: Context): Boolean {
        val commands = ANIMATION_KEYS.map { "settings put global $it 1.0" } +
            Toggle.entries.map { "settings delete ${it.namespace} ${it.key}" }
        return runCatching { EmbeddedAdbShell.runShell(context, commands.joinToString("; ")) }
            .getOrDefault(false)
    }
}
