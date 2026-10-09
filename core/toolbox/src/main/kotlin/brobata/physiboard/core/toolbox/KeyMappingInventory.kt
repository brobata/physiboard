package brobata.physiboard.core.toolbox

/**
 * Everything the Key mapping screen needs to know about the phone right now, read once
 * (`Settings.System`, no broker) by `:device:privileged` and handed here as data so the per-row
 * text is a pure function. [ownPackageName] is never a literal: the sideload build has another
 * applicationId, so "hold: the assistant, listening" must compare against the running app's own
 * identity, not a hard-coded string.
 *
 * spec: broker-privileged-toolbox.md SS15.
 */
data class KeyMappingSnapshot(
    val fnEnable: Int?,
    val fnFunction: Int?,
    val fnLongPressSpeechOn: Boolean,
    val fnLongPressActivity: String?,
    val symLongPressAssistantOn: Boolean,
    val symTrackpadTriggerOn: Boolean,
    val orangeShortPressActivity: String?,
    val orangeDoublePressActivity: String?,
    val orangeLongPressPackage: String?,
    val orangeLongPressActivity: String?,
    val ownPackageName: String,
    val spaceTrackpadTriggerOn: Boolean,
    val shiftRightRemapped: Boolean,
    val homeRemapped: Boolean,
    val recentAppsRemapped: Boolean,
)

/** One row of the inventory. [opensRoute] is a route id matching the app's own `Routes` constants, or null for a fixed key. spec: SS15. */
data class KeyMappingRow(val label: String, val hardwareText: String, val bindingText: String, val opensRoute: String?)

/**
 * The read-only key mapping inventory: what each physical key is bound to in the vendor layer and
 * in PhysiBoard's own handling, "including the ones bound to nothing" (SS15). Needs no broker:
 * every input is a plain `Settings.System` read.
 *
 * The route ids below are literals matching `app/.../ui/Routes.kt`'s constants
 * (`fn_layer`, `voice`, `screen_trackpad`); this module cannot depend on the app module, so the
 * two are kept in sync by convention rather than a shared type.
 *
 * spec: broker-privileged-toolbox.md SS15; T51, T52.
 */
object KeyMappingInventory {
    const val ROUTE_FN_LAYER = "fn_layer"
    const val ROUTE_VOICE = "voice"
    const val ROUTE_SCREEN_TRACKPAD = "screen_trackpad"

    /** The segment after the last dot, spec's "activity values are shown as the segment after the last dot". */
    fun tail(activity: String?): String? = activity?.substringAfterLast('.')?.takeIf { it.isNotBlank() }

    fun rows(snapshot: KeyMappingSnapshot): List<KeyMappingRow> = listOf(
        fnRow(snapshot),
        symRow(snapshot),
        orangeSideKeyRow(snapshot),
        spaceRow(snapshot),
        KeyMappingRow("Right Shift", "keyboard matrix", if (snapshot.shiftRightRemapped) "Vendor remapping enabled" else "Types Shift", null),
        KeyMappingRow("Home", "scancode 102", if (snapshot.homeRemapped) "Vendor remapping enabled" else "Home", null),
        KeyMappingRow("Recent apps", "navigation key", if (snapshot.recentAppsRemapped) "Vendor remapping enabled" else "Recent apps", null),
        KeyMappingRow("Back", "scancode 158", "Back", null),
        KeyMappingRow("Volume up / down", "gpio-keys 115 / 114", "Volume", null),
        KeyMappingRow("Power", "ff_key 116", "Power and screen lock", null),
    )

    /** spec: SS15 Fn row; T51. */
    private fun fnRow(s: KeyMappingSnapshot): KeyMappingRow {
        val remappedToCtrl = s.fnEnable == 1 && s.fnFunction == 1
        val binding = StringBuilder(if (remappedToCtrl) "Acts as Ctrl" else "Fn layer")
        if (s.fnLongPressSpeechOn) binding.append(" · hold to dictate")
        if (!remappedToCtrl) {
            tail(s.fnLongPressActivity)?.let { binding.append(" · long press opens $it") }
        }
        return KeyMappingRow("Fn", "scancode 251 (FUNC3)", binding.toString(), ROUTE_FN_LAYER)
    }

    /** spec: SS15 Sym row. Labelled for what the row opens (Voice, where the hold-for-assistant switch lives), not the bare key name. */
    private fun symRow(s: KeyMappingSnapshot): KeyMappingRow {
        val binding = StringBuilder("Symbol and emoji pages")
        if (s.symLongPressAssistantOn) binding.append(" · hold for the assistant")
        if (s.symTrackpadTriggerOn) binding.append(" · hold for the trackpad")
        return KeyMappingRow("Hold Sym: assistant", "scancode 253 (AGUI_SYM)", binding.toString(), ROUTE_VOICE)
    }

    /**
     * spec: SS15 orange side key row; T52. Only "hold" has a stated "nothing" fallback; "tap" and
     * "double" are left out entirely when there is no activity to tail, rather than printed as
     * "nothing" (T52 shows "tap: FooActivity · hold: ..." with no "double" segment at all when
     * `func1_double_press_activity` is unset).
     */
    private fun orangeSideKeyRow(s: KeyMappingSnapshot): KeyMappingRow {
        val segments = buildList {
            tail(s.orangeShortPressActivity)?.let { add("tap: $it") }
            tail(s.orangeDoublePressActivity)?.let { add("double: $it") }
            add(
                when {
                    s.orangeLongPressPackage == s.ownPackageName -> "hold: the assistant, listening"
                    tail(s.orangeLongPressActivity) != null -> "hold: ${tail(s.orangeLongPressActivity)}"
                    else -> "hold: nothing"
                },
            )
        }
        return KeyMappingRow("Orange side key", "ff_key 249", segments.joinToString(" · "), ROUTE_VOICE)
    }

    /** spec: SS15 Space row. */
    private fun spaceRow(s: KeyMappingSnapshot): KeyMappingRow {
        val binding = if (s.spaceTrackpadTriggerOn) "Space · hold for the trackpad" else "Space"
        return KeyMappingRow("Space", "keyboard matrix, also the fingerprint sensor", binding, ROUTE_SCREEN_TRACKPAD)
    }
}
