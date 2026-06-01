package it.palsoftware.pastiera.commands

import android.content.Context

interface CommandSource {
    val id: CommandSourceId
    fun getCommands(context: Context): List<CommandTarget>
}
