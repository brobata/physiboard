package brobata.physiboard.toolbox

import android.content.Context
import android.provider.Settings
import brobata.physiboard.SettingsManager

/**
 * Every physical key on the Titan 2 Elite, and what it currently does.
 *
 * Bindings live in two unrelated places — vendor rows in `Settings.System` that the firmware
 * reads before any app sees the key, and PhysiBoard's own handling for keys that do reach the
 * IME. Neither half is much use alone: knowing Fn is remapped to Ctrl does not tell you that
 * holding it dictates. This assembles both into one answer per key.
 *
 * Reads only, and deliberately no broker: `Settings.System` is readable without permission, so
 * the inventory works before pairing. Only changing a binding needs the broker.
 */
object KeyInventory {

    /** Where the user goes to change this key, if anywhere. */
    enum class Editor {
        /** Fn's modifier role and its layer mappings. */
        FN_LAYER,
        /** Dictation and assistant triggers. */
        VOICE,
        /** The screen trackpad's trigger key. */
        TRACKPAD,
        /** Fixed by the system or the hardware. */
        NONE
    }

    data class PhysicalKey(
        val label: String,
        /** What the hardware reports, so an unfamiliar key can still be identified. */
        val hardware: String,
        /** What it does today, assembled from both layers. */
        val binding: String,
        val editor: Editor
    )

    private const val FN_ENABLE = "fn_programmable_key_enable"
    private const val FN_FUNCTION = "fn_programmable_key_function"
    private const val FN_LONG_ACTIVITY = "fn_long_press_activity"
    private const val FUNC1_SHORT = "func1_short_press_activity"
    private const val FUNC1_DOUBLE = "func1_double_press_activity"
    private const val FUNC1_LONG_ACTIVITY = "func1_long_press_activity"
    private const val FUNC1_LONG_PACKAGE = "func1_long_press_package"
    private const val SHIFT_R_ENABLE = "shift_r_programmable_key_enable"
    private const val HOME_ENABLE = "home_programmable_key_enable"
    private const val RECENT_ENABLE = "recent_programmable_key_enable"

    fun keys(context: Context): List<PhysicalKey> = listOf(
        fnKey(context),
        symKey(context),
        orangeKey(context),
        spaceKey(context),
        vendorFlagKey(
            context, "Right Shift", "keyboard matrix", SHIFT_R_ENABLE,
            onText = "Vendor remapping enabled", offText = "Types Shift"
        ),
        vendorFlagKey(
            context, "Home", "scancode 102", HOME_ENABLE,
            onText = "Vendor remapping enabled", offText = "Home"
        ),
        vendorFlagKey(
            context, "Recent apps", "navigation key", RECENT_ENABLE,
            onText = "Vendor remapping enabled", offText = "Recent apps"
        ),
        PhysicalKey("Back", "scancode 158", "Back", Editor.NONE),
        PhysicalKey("Volume up / down", "gpio-keys 115 / 114", "Volume", Editor.NONE),
        PhysicalKey("Power", "ff_key 116", "Power and screen lock", Editor.NONE)
    )

    private fun fnKey(context: Context): PhysicalKey {
        val remapped = read(context, FN_ENABLE) == "1" && read(context, FN_FUNCTION) == "1"
        val parts = buildList {
            add(if (remapped) "Acts as Ctrl" else "Fn layer")
            if (SettingsManager.getFnLongPressSpeechEnabled(context)) add("hold to dictate")
            read(context, FN_LONG_ACTIVITY)?.takeIf { it.isNotEmpty() && !remapped }
                ?.let { add("long press opens ${shortName(it)}") }
        }
        return PhysicalKey(
            label = "Fn",
            hardware = "scancode 251 (FUNC3)",
            binding = parts.joinToString(" · "),
            editor = Editor.FN_LAYER
        )
    }

    private fun symKey(context: Context): PhysicalKey {
        val parts = buildList {
            add("Symbol and emoji pages")
            if (SettingsManager.getSymLongPressAssistantEnabled(context)) add("hold for the assistant")
            if (SettingsManager.isScreenTrackpadEnabled(context) &&
                SettingsManager.getScreenTrackpadTriggerKey(context) ==
                SettingsManager.SCREEN_TRACKPAD_TRIGGER_SYM
            ) {
                add("hold for the trackpad")
            }
        }
        return PhysicalKey(
            label = "Sym",
            hardware = "scancode 253 (AGUI_SYM)",
            binding = parts.joinToString(" · "),
            editor = Editor.VOICE
        )
    }

    private fun orangeKey(context: Context): PhysicalKey {
        val longTarget = read(context, FUNC1_LONG_ACTIVITY)
        val ours = read(context, FUNC1_LONG_PACKAGE) == context.packageName
        val parts = buildList {
            read(context, FUNC1_SHORT)?.let { add("tap: ${shortName(it)}") }
            read(context, FUNC1_DOUBLE)?.let { add("double: ${shortName(it)}") }
            add(
                when {
                    ours -> "hold: the assistant, listening"
                    longTarget != null -> "hold: ${shortName(longTarget)}"
                    else -> "hold: nothing"
                }
            )
        }
        return PhysicalKey(
            label = "Orange side key",
            hardware = "ff_key 249",
            binding = parts.joinToString(" · "),
            editor = Editor.VOICE
        )
    }

    private fun spaceKey(context: Context): PhysicalKey {
        val trigger = SettingsManager.isScreenTrackpadEnabled(context) &&
            SettingsManager.getScreenTrackpadTriggerKey(context) ==
            SettingsManager.SCREEN_TRACKPAD_TRIGGER_SPACE
        return PhysicalKey(
            label = "Space",
            hardware = "keyboard matrix, also the fingerprint sensor",
            binding = if (trigger) "Space · hold for the trackpad" else "Space",
            editor = Editor.TRACKPAD
        )
    }

    private fun vendorFlagKey(
        context: Context,
        label: String,
        hardware: String,
        key: String,
        onText: String,
        offText: String
    ) = PhysicalKey(
        label = label,
        hardware = hardware,
        binding = if (read(context, key) == "1") onText else offText,
        editor = Editor.NONE
    )

    /** Vendor rows hold fully-qualified activities; only the tail means anything to a reader. */
    private fun shortName(activity: String): String =
        activity.substringAfterLast('.').ifEmpty { activity }

    private fun read(context: Context, key: String): String? = runCatching {
        Settings.System.getString(context.contentResolver, key)
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}
