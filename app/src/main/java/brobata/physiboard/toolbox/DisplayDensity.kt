package brobata.physiboard.toolbox

import android.content.Context
import android.util.Log
import brobata.physiboard.inputmethod.EmbeddedAdbShell

/**
 * Screen density, the most-asked-for Android tweak there is.
 *
 * On a 1080x1200 Titan panel the payoff is unusually direct: the screen is short, so every
 * step down in density buys another line or two of whatever you are reading, and the physical
 * keyboard means none of that space goes back to a soft keyboard.
 *
 * Applied through [RevertibleChange] rather than directly, because a bad value is exactly the
 * failure a confirmation dialog cannot save you from.
 */
object DisplayDensity {

    private const val TAG = "DisplayDensity"
    private const val CHANGE_ID = "display_density"

    /**
     * Bounds, not a preference. Below roughly 60% of stock the system UI starts laying out in
     * ways nothing was designed for; above 140% the keyboard's own rows stop fitting. The
     * auto-revert catches a bad choice, but it should not be reachable in the first place.
     */
    private const val MIN_FACTOR = 0.6f
    private const val MAX_FACTOR = 1.4f

    data class Info(
        /** What the panel reports — the value "reset" returns to. */
        val physical: Int,
        /** What is in force now, which may be an override. */
        val current: Int
    ) {
        val isOverridden: Boolean get() = physical != current
    }

    fun range(physical: Int): IntRange =
        (physical * MIN_FACTOR).toInt()..(physical * MAX_FACTOR).toInt()

    /**
     * Reads both densities in one round trip.
     *
     * `wm density` prints "Physical density: N" and, only when an override is in force, a
     * second "Override density: N" line — so the absence of that line is itself the signal
     * that nothing has been changed.
     */
    fun read(context: Context): Info? {
        if (!EmbeddedAdbShell.isPaired(context)) return null
        if (!runCatching { EmbeddedAdbShell.runShell(context, "wm density") }.getOrDefault(false)) {
            return null
        }
        val out = EmbeddedAdbShell.lastResult.orEmpty()
        val physical = Regex("Physical density:\\s*(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val override = Regex("Override density:\\s*(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull()
        return Info(physical = physical, current = override ?: physical)
    }

    /**
     * Applies a density with the revert already armed.
     *
     * The revert is always `wm density reset` rather than "back to the previous number":
     * chaining changes would otherwise leave the undo pointing at another override rather
     * than at the value the panel actually shipped with.
     */
    fun apply(context: Context, info: Info, density: Int): RevertibleChange.Outcome {
        val allowed = range(info.physical)
        if (density !in allowed) {
            Log.w(TAG, "Refusing density $density outside $allowed")
            return RevertibleChange.Outcome.Failed("Outside the safe range")
        }
        return RevertibleChange.apply(
            context,
            RevertibleChange.Change(
                id = CHANGE_ID,
                apply = "wm density $density",
                revert = "wm density reset"
            )
        )
    }

    /** Back to the panel's own density, with no confirmation window — this is always safe. */
    fun reset(context: Context): Boolean {
        if (!EmbeddedAdbShell.isPaired(context)) return false
        val ok = runCatching { EmbeddedAdbShell.runShell(context, "wm density reset") }
            .getOrDefault(false)
        if (ok) RevertibleChange.keep(context)
        return ok
    }
}
