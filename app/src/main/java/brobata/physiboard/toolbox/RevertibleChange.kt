package brobata.physiboard.toolbox

import android.content.Context
import android.util.Log
import brobata.physiboard.inputmethod.EmbeddedAdbShell
import org.json.JSONObject

/**
 * Applies a change that could make the phone unusable, and undoes it unless the user confirms
 * they can still read the screen.
 *
 * A confirmation dialog is the wrong tool here. Density is the proof: set it badly and the UI
 * is unreadable or the buttons sit off-screen, so asking "are you sure?" beforehand protects
 * nobody — the damage is only visible afterwards, and by then the user may not be able to
 * press anything. Every monitor resolution dialog of the last twenty years solves this the
 * same way, and so do we:
 *
 *     apply -> "Keep this? Reverting in 15s" -> revert unless confirmed
 *
 * The undo is recorded BEFORE the change is applied, and it lives in preferences rather than
 * memory, so a crash, a killed process or a user who simply puts the phone down all end at
 * the safe value instead of stranding them.
 *
 * BLOCKING — every call runs a broker command. Never call from the main thread.
 */
object RevertibleChange {

    private const val TAG = "RevertibleChange"
    private const val PREFS = "physiboard_toolbox"
    private const val KEY_PENDING = "pending_revert"

    /** Long enough to read the screen and react, short enough not to strand anyone. */
    const val CONFIRM_WINDOW_MS = 15_000L

    sealed interface Outcome {
        data object Applied : Outcome
        data object NotPaired : Outcome
        data class Failed(val reason: String) : Outcome
    }

    /**
     * @param apply the command that makes the change.
     * @param revert the command that undoes it — captured up front, so the undo never has to
     *   be derived from a device whose state may have moved since.
     */
    data class Change(
        val id: String,
        val apply: String,
        val revert: String
    )

    fun apply(context: Context, change: Change): Outcome {
        if (!EmbeddedAdbShell.isPaired(context)) return Outcome.NotPaired

        // Recorded first: a change we cannot reverse is worse than one that never lands.
        arm(context, change)

        val ok = runCatching { EmbeddedAdbShell.runShell(context, change.apply) }
            .onFailure { Log.w(TAG, "apply failed", it) }
            .getOrDefault(false)

        if (!ok) {
            disarm(context)
            return Outcome.Failed(EmbeddedAdbShell.lastError ?: "Command failed")
        }
        return Outcome.Applied
    }

    /** The user confirmed the screen is still usable. Stand the revert down. */
    fun keep(context: Context) = disarm(context)

    /** The window expired, or the user said no. Put it back. */
    fun revertNow(context: Context): Boolean {
        val change = pending(context) ?: return true
        val ok = runCatching { EmbeddedAdbShell.runShell(context, change.revert) }
            .getOrDefault(false)
        if (ok) disarm(context)
        return ok
    }

    /**
     * A change still waiting to be confirmed.
     *
     * Checked at IME start as well as by the screen that armed it: a change that outlived the
     * app — process death, or a reboot inside the window — must still be undone rather than
     * quietly becoming permanent.
     */
    fun pending(context: Context): Change? {
        val raw = prefs(context).getString(KEY_PENDING, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Change(
                id = json.getString("id"),
                apply = json.getString("apply"),
                revert = json.getString("revert")
            )
        }.onFailure { Log.w(TAG, "Pending revert unreadable", it) }.getOrNull()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun arm(context: Context, change: Change) {
        val json = JSONObject()
            .put("id", change.id)
            .put("apply", change.apply)
            .put("revert", change.revert)
        prefs(context).edit().putString(KEY_PENDING, json.toString()).apply()
    }

    private fun disarm(context: Context) {
        prefs(context).edit().remove(KEY_PENDING).apply()
    }
}
