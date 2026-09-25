package brobata.physiboard.app.settings.ui

import brobata.physiboard.core.actions.commands.CommandIcon

/**
 * A text glyph per [CommandIcon] for the assignment sheet and the assignments grid
 * (expansion-clipboard-pickers-launcher.md SS8.5 names Material icons; no icon assets ship in the
 * 3.0 tree yet, matching the strip's own placeholder glyphs).
 */
object CommandGlyphs {
    fun glyph(icon: CommandIcon): String = when (icon) {
        CommandIcon.APPS_GRID -> "☷"
        CommandIcon.EVENT -> "📅"
        CommandIcon.TASK_CHECK -> "☑"
        CommandIcon.MICROPHONE -> "🎤"
        CommandIcon.HOME -> "⌂"
        CommandIcon.MAGNIFIER -> "🔍"
        CommandIcon.GEAR -> "⚙"
        CommandIcon.PLAY -> "⏯"
        CommandIcon.SKIP_PREVIOUS -> "⏮"
        CommandIcon.SKIP_NEXT -> "⏭"
        CommandIcon.VOLUME_UP -> "🔊"
        CommandIcon.VOLUME_DOWN -> "🔉"
        CommandIcon.VOLUME_MUTE -> "🔇"
        CommandIcon.SUN -> "☀"
        CommandIcon.KEYBOARD -> "⌨"
        CommandIcon.ACCESSIBILITY -> "♿"
        CommandIcon.GLOBE -> "🌐"
        CommandIcon.BLUETOOTH -> "⎅"
        CommandIcon.WIFI -> "📶"
        CommandIcon.VOLUME -> "🔈"
        CommandIcon.CONTACTLESS -> "⭘"
        CommandIcon.BATTERY_SAVER -> "🔋"
        CommandIcon.BELL -> "🔔"
        CommandIcon.ARROW_UP -> "↑"
        CommandIcon.ARROW_DOWN -> "↓"
        CommandIcon.ARROW_LEFT -> "←"
        CommandIcon.ARROW_RIGHT -> "→"
        CommandIcon.TAB -> "⇥"
        CommandIcon.FIRST_PAGE -> "⇤"
        CommandIcon.LAST_PAGE -> "⇥"
        CommandIcon.VERTICAL_ALIGN_TOP -> "⤒"
        CommandIcon.VERTICAL_ALIGN_BOTTOM -> "⤓"
        CommandIcon.CLOSE -> "✕"
        CommandIcon.BACKSPACE -> "⌦"
        CommandIcon.COPY -> "⎘"
        CommandIcon.PASTE -> "📋"
        CommandIcon.CUT -> "✂"
        CommandIcon.UNDO -> "↶"
        CommandIcon.SELECT_ALL -> "☰"
        CommandIcon.COMMAND_KEY -> "⌘"
        CommandIcon.NAVIGATION -> "✥"
    }
}
