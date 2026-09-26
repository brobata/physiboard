package brobata.physiboard.core.actions.launcher

import brobata.physiboard.core.actions.commands.Command
import brobata.physiboard.core.actions.commands.CommandCatalog
import brobata.physiboard.core.actions.commands.CommandSource
import brobata.physiboard.core.actions.commands.CommandSurface
import brobata.physiboard.core.keys.KeyId

/**
 * What the assignment sheet lists and in what order. spec: expansion-clipboard-pickers-
 * launcher.md SS6.4. The sheet itself is an Android surface; every rule about its content is
 * here so a JVM test can pin it.
 */
object AssignmentSheet {
    const val TITLE: String = "Shortcut"
    const val SEARCH_HINT: String = "Search commands..."
    const val ALL_CHIP: String = "All"
    const val REMOVE_CHIP: String = "Remove"
    const val EMPTY: String = "No commands found"
    fun emptyForQuery(query: String): String = "No results for \"$query\""

    /** spec SS6.4: result code 1 "assigned", 2 "removed"; the intent extras. */
    const val RESULT_ASSIGNED: Int = 1
    const val RESULT_REMOVED: Int = 2
    const val EXTRA_KEY_CODE: String = "key_code"
    const val EXTRA_SKIP_LAUNCH: String = "skip_launch"

    /** The intent action the keyboard sends (package-restricted) and the settings app's sheet answers, so neither names the other's class. */
    const val ACTION_ASSIGN_KEY: String = "brobata.physiboard.action.ASSIGN_LAUNCHER_KEY"

    /**
     * spec SS6.2/SS6.4: "when the sheet was opened by a key press the command also runs
     * immediately." The sheet's own process can start an app or an intent directly, but an
     * [brobata.physiboard.core.actions.commands.LaunchSpec.InternalAction] or
     * [brobata.physiboard.core.actions.commands.LaunchSpec.NavAction] needs the running keyboard's
     * own session (its quick launcher, its input connection), so the sheet sends this
     * package-restricted broadcast back to the keyboard instead of trying to run it itself.
     */
    const val ACTION_RUN_COMMAND_NOW: String = "brobata.physiboard.action.RUN_ASSIGNED_COMMAND_NOW"
    const val EXTRA_COMMAND_ID: String = "command_id"

    /** spec SS6.4: "every command that lists the 'assigned key' surface, from every source, regardless of the quick launcher's per-source visibility setting". */
    fun candidates(catalog: CommandCatalog): List<Command> = catalog.forSurface(CommandSurface.ASSIGNED_KEY)

    /** spec SS6.4: the source chips present, in rank order. */
    fun sourcesPresent(commands: List<Command>): List<CommandSource> = commands.map { it.source }.distinct().sortedBy { it.rank }

    /**
     * spec SS6.4 "Ordering": with an empty search and a letter key, apps whose label begins with
     * that letter first (case-insensitive), alphabetically, then the rest by source rank and label;
     * otherwise by source rank and label. A search keeps commands whose label, subtitle or any
     * search token contains the query (case-insensitive). [source] filters to one chip.
     */
    fun order(commands: List<Command>, key: KeyId?, query: String, source: CommandSource? = null): List<Command> {
        val trimmed = query.trim()
        val filtered = commands.filter { source == null || it.source == source }.filter { trimmed.isEmpty() || matches(it, trimmed) }
        val byRankAndLabel = compareBy<Command> { it.source.rank }.thenBy { it.label.lowercase() }
        val letter = (key as? KeyId.Letter)?.qwertyLetter?.lowercaseChar()
        if (trimmed.isNotEmpty() || letter == null) return filtered.sortedWith(byRankAndLabel)
        val (first, rest) = filtered.partition { it.source == CommandSource.APPS && it.label.lowercase().startsWith(letter) }
        return first.sortedBy { it.label.lowercase() } + rest.sortedWith(byRankAndLabel)
    }

    private fun matches(command: Command, query: String): Boolean =
        command.label.contains(query, ignoreCase = true) ||
            (command.subtitle?.contains(query, ignoreCase = true) ?: false) ||
            command.searchTokens.any { it.contains(query, ignoreCase = true) }
}
