package brobata.physiboard.app.settings.ui

import brobata.physiboard.core.actions.feedback.TypingSoundMode
import brobata.physiboard.core.settings.CustomSymPage
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.core.settings.SymPage
import brobata.physiboard.core.settings.SymPagesConfig
import brobata.physiboard.core.text.MessagingPreset
import kotlin.test.Test
import kotlin.test.assertEquals

class SummariesTest {
    private val defaults = Settings()

    @Test
    fun `the shipped defaults read as plain sentences`() {
        assertEquals("Auto-capitals on · double-space period on", Summaries.typing(defaults))
        assertEquals("Autocorrect on · mix-ups off", Summaries.autocorrect(defaults))
        assertEquals("QWERTY · follows the language", Summaries.languages(defaults))
        assertEquals("Alt symbol · 500 ms", Summaries.longPress(defaults))
        assertEquals("Emoji → Symbols", Summaries.symPages(defaults))
        assertEquals("Hold Fn · stops after 2.5 s", Summaries.voice(defaults))
        assertEquals("Fn layer on · trackpad off", Summaries.keys(defaults))
        assertEquals("No terminal apps · Enter sends", Summaries.apps(defaults))
        assertEquals("Private mode off · clean links on", Summaries.privacy(defaults))
    }

    @Test
    fun `sym pages follow the stored order and skip Fill and switched-off pages`() {
        val pages = SymPagesConfig(
            emojiPickerEnabled = true, symbolsEnabled = false, gifEnabled = true, custom2Enabled = true, fillEnabled = true,
            order = listOf(SymPage.FILL, SymPage.GIF, SymPage.CUSTOM_2, SymPage.SYMBOLS, SymPage.EMOJI_PICKER),
        )
        val custom = listOf(CustomSymPage(), CustomSymPage(name = "  Maths "), CustomSymPage())
        val s = defaults.copy(symPages = defaults.symPages.copy(pages = pages, customPages = custom))
        assertEquals("GIFs → Maths → Emoji", Summaries.symPages(s))
        val none = defaults.copy(symPages = defaults.symPages.copy(pages = SymPagesConfig(emojiPickerEnabled = false, symbolsEnabled = false, gifEnabled = false)))
        assertEquals("No pages switched on", Summaries.symPages(none))
    }

    @Test
    fun `silence reads in whole or tenths of a second, and zero runs until stopped`() {
        assertEquals("stops after 15 s", Summaries.silenceLabel(15000))
        assertEquals("stops after 2.5 s", Summaries.silenceLabel(2500))
        assertEquals("runs until you stop it", Summaries.silenceLabel(0))
        val off = defaults.copy(dictation = defaults.dictation.copy(fnLongPressSpeech = false))
        assertEquals("Hold Fn is off", Summaries.voice(off))
    }

    @Test
    fun `apps counts terminal apps and names the Enter preset`() {
        val s = defaults.copy(perApp = defaults.perApp.copy(exactTypingPackages = setOf("a", "b"), enterPreset = MessagingPreset.NEWLINE_CTRL_SEND))
        assertEquals("2 terminal apps · Ctrl+Enter sends", Summaries.apps(s))
        val one = s.copy(perApp = s.perApp.copy(exactTypingPackages = setOf("a"), enterBehaviorEnabled = false))
        assertEquals("1 terminal app · Enter as each app sets it", Summaries.apps(one))
    }

    @Test
    fun `layout ids read as the Keyboard layout screen names them`() {
        assertEquals("QWERTZ | Deutsch", Summaries.layoutName("qwertz"))
        assertEquals("Russian (ЙЦУКЕН)", Summaries.layoutName("russian_standard"))
        assertEquals("my_layout", Summaries.layoutName("my_layout"))
        assertEquals("QWERTY", Summaries.layoutName(""))
    }

    @Test
    fun `look names the active preset or says the colours are custom`() {
        assertEquals("silent keys", Summaries.soundLabel(TypingSoundMode.OFF))
        val custom = defaults.copy(statusBar = defaults.statusBar.copy(theme = defaults.statusBar.theme.copy(accent = 0xFF123456.toInt())))
        assertEquals("Custom colours · silent keys", Summaries.look(custom))
    }
}
