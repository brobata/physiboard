package it.palsoftware.pastiera.commands

import android.content.Context

class NavCommandSource : CommandSource {
    override val id = CommandSourceId.NavActions

    override fun getCommands(context: Context): List<CommandTarget> {
        return keycodeCommands() + actionCommands()
    }

    private fun keycodeCommands(): List<CommandTarget> {
        return listOf(
            "DPAD_UP" to "Up",
            "DPAD_DOWN" to "Down",
            "DPAD_LEFT" to "Left",
            "DPAD_RIGHT" to "Right",
            "TAB" to "Tab",
            "MOVE_HOME" to "Home",
            "MOVE_END" to "End",
            "PAGE_UP" to "Page up",
            "PAGE_DOWN" to "Page down",
            "ESCAPE" to "Escape",
            "DPAD_CENTER" to "Center",
            "FORWARD_DEL" to "Forward delete"
        ).map { (value, label) ->
            navTarget("nav.keycode.$value", label, "Navigation", "keycode", value)
        }
    }

    private fun actionCommands(): List<CommandTarget> {
        return listOf(
            "copy" to "Copy",
            "paste" to "Paste",
            "cut" to "Cut",
            "undo" to "Undo",
            "select_all" to "Select all",
            "expand_selection_left" to "Select left",
            "expand_selection_right" to "Select right",
            "move_word_left" to "Word left",
            "move_word_right" to "Word right",
            "expand_selection_word_left" to "Select word left",
            "expand_selection_word_right" to "Select word right",
            "page_start" to "Page start",
            "page_end" to "Page end",
            "toggle_minimal_ui" to "Pastierina",
            "media_play_pause" to "Play/Pause",
            "media_previous" to "Media previous",
            "media_next" to "Media next"
        ).map { (value, label) ->
            navTarget("nav.action.$value", label, "Action", "action", value)
        }
    }

    private fun navTarget(
        id: String,
        label: String,
        subtitle: String,
        mappingType: String,
        value: String
    ): CommandTarget {
        return CommandTarget(
            id = id,
            source = this.id,
            kind = CommandKind.NavAction,
            label = label,
            subtitle = subtitle,
            icon = CommandIcon.Navigation,
            launch = CommandLaunchSpec.NavAction(mappingType, value),
            capabilities = setOf(CommandCapability.RequiresImeContext),
            defaultSurfaces = setOf(CommandSurface.NavMode),
            searchTokens = listOf(label, subtitle, value)
        )
    }
}
