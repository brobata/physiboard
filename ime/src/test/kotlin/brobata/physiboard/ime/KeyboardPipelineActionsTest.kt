package brobata.physiboard.ime

import brobata.physiboard.core.actions.launcher.AssignableKeys
import brobata.physiboard.core.actions.launcher.LauncherKeyDecision
import brobata.physiboard.core.actions.launcher.LauncherKeySettings
import brobata.physiboard.core.actions.launcher.LauncherShortcuts
import brobata.physiboard.core.actions.launcher.ShortcutEntry
import brobata.physiboard.core.actions.snippets.Snippet
import brobata.physiboard.core.actions.snippets.SnippetSettings
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyEdge
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.KeyStroke
import brobata.physiboard.core.keys.ModifierKey
import brobata.physiboard.core.text.EditorOp
import brobata.physiboard.core.text.EditorSnapshot
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import brobata.physiboard.core.text.TextWindow
import brobata.physiboard.device.titan.TitanLayouts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How text expansion and the launcher keys sit in the key pipeline's order (expansion-clipboard-
 * pickers-launcher.md SS2.4 "before the key is acted on", SS6.2 C ahead of the Sym chord symbol,
 * SS6.2 B outside a field). The rules themselves are `:core:actions`' and tested there; these pin
 * the wiring.
 */
class KeyboardPipelineActionsTest {

    private val space = KeyId.Control(ControlKey.SPACE)
    private val sym = KeyId.Modifier(ModifierKey.SYM)
    private val snippetsOn = SnippetSettings(enabled = true, snippets = listOf(Snippet("sig", "Regards")))

    private fun pipeline(settings: KeyboardSettings = KeyboardSettings(), editable: Boolean = true): KeyboardPipeline {
        val p = KeyboardPipeline(layout = TitanLayouts.titan2EliteQwerty(), settings = settings)
        p.onStartInput(FieldContext(if (editable) FieldKind.NORMAL else FieldKind.NOT_EDITABLE))
        return p
    }

    private fun snapshot(text: String, at: Long = 100) = EditorSnapshot(textBeforeCursor = text, fullText = TextWindow(text, text.length, text.length), nowMs = at)
    private fun down(key: KeyId, at: Long = 100, symMeta: Boolean = false) = KeyStroke(key, KeyEdge.DOWN, 0, at, meta = brobata.physiboard.core.keys.ModifierFlags(sym = symMeta))
    private fun up(key: KeyId, at: Long = 120) = KeyStroke(key, KeyEdge.UP, 0, at)

    @Test
    fun `T1 through the pipeline - Space on an exact match commits the replacement instead of a space`() {
        val p = pipeline(KeyboardSettings(expansion = snippetsOn))
        val result = p.onKeyStroke(down(space), snapshot("Hello !sig"))
        assertTrue(result.consumed)
        assertEquals(listOf(EditorOp.FinishComposing, EditorOp.ReplaceBeforeCursor(4, "Regards ")), result.ops)
    }

    @Test
    fun `with snippets off Space is the ordinary commit`() {
        val p = pipeline()
        val result = p.onKeyStroke(down(space), snapshot("Hello !sig"))
        assertTrue(result.ops.none { it is EditorOp.ReplaceBeforeCursor })
        assertTrue(result.ops.any { it is EditorOp.CommitText || it == EditorOp.PassThroughKey } || result.ops.isEmpty())
    }

    @Test
    fun `the scheduled refresh answers the popup rows and a row tap commits with no trailing space`() {
        val p = pipeline(KeyboardSettings(expansion = snippetsOn))
        val rows = p.refreshExpansion("!si")
        assertEquals(listOf("sig → Regards"), rows.map { it.label })
        val result = p.onExpansionRowTapped(0, snapshot("!si"))
        assertEquals(listOf(EditorOp.FinishComposing, EditorOp.ReplaceBeforeCursor(3, "Regards")), result.ops)
        assertTrue(p.refreshExpansion("!si x").isEmpty(), "a space after the shortcut ends the candidacy")
    }

    @Test
    fun `the suggestion bar presentation hands the strip the matches`() {
        val p = pipeline(KeyboardSettings(expansion = snippetsOn.copy(presentation = brobata.physiboard.core.actions.snippets.SnippetPresentation.SUGGESTION_BAR)))
        p.refreshExpansion("!s")
        assertTrue(p.expansionOwnsStripSlots)
        assertTrue(p.expansionPopupRows().isEmpty(), "the popup draws nothing in the bar presentation")
        val model = p.stripModel(clipboardCount = 0, dictationActive = false, dictionaryInstalled = true, subtypeLocale = "en")
        assertNotNull(model)
    }

    @Test
    fun `C - Sym pending plus the quick launcher's key fires the launcher and marks the chord`() {
        val p = pipeline()
        p.onKeyStroke(down(sym), snapshot(""))
        val result = p.onKeyStroke(down(space, at = 110), snapshot("", 110))
        assertTrue(result.consumed)
        assertIs<LauncherKeyDecision.Run>(result.launcherKey)
        assertTrue(result.launcherKey!!.let { (it as LauncherKeyDecision.Run).entry.isQuickLauncher })
        // The chord counts as used: the Sym release must not cycle a page.
        p.onKeyStroke(up(sym, 130), snapshot("", 130))
        assertEquals(0, p.currentSymPage)
    }

    @Test
    fun `C - an unassigned key under Sym falls through to the chord and a Sym edit shortcut is never a launcher key`() {
        val q = AssignableKeys.keycodeOf(KeyId.Letter('C'))!!
        val settings = KeyboardSettings(launcherShortcuts = LauncherShortcuts().assign(q, ShortcutEntry.QUICK_LAUNCHER))
        val p = pipeline(settings)
        p.onKeyStroke(down(sym), snapshot(""))
        val c = p.onKeyStroke(down(KeyId.Letter('C'), at = 110), snapshot("abc", 110))
        assertNull(c.launcherKey, "Sym+C is the copy shortcut first (layers-sym-alt.md SS5.3 step 1)")
        val p2 = pipeline(settings)
        p2.onKeyStroke(down(sym), snapshot(""))
        assertNull(p2.onKeyStroke(down(KeyId.Letter('Q'), at = 110), snapshot("", 110)).launcherKey)
    }

    @Test
    fun `C - power shortcuts off leaves Sym plus Space to the chord`() {
        val p = pipeline(KeyboardSettings(launcherKeys = LauncherKeySettings(symShortcutsEnabled = false)))
        p.onKeyStroke(down(sym), snapshot(""))
        assertNull(p.onKeyStroke(down(space, at = 110), snapshot("", 110)).launcherKey)
    }

    @Test
    fun `B - outside a field Sym arms the mode and the next assigned key fires`() {
        val p = pipeline(editable = false)
        val armed = p.onKeyStroke(down(sym, at = 1000), snapshot(""))
        assertTrue(armed.consumed)
        assertEquals(1000L, armed.powerModeArmedAtMs)
        assertEquals(1000L, p.powerShortcutArmedAtMs)
        val fired = p.onKeyStroke(down(space, at = 1200), snapshot("", 1200))
        assertIs<LauncherKeyDecision.Run>(fired.launcherKey)
        assertNull(p.powerShortcutArmedAtMs)
        val unassigned = pipeline(editable = false)
        unassigned.onKeyStroke(down(sym, at = 1000), snapshot(""))
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(AssignableKeys.keycodeOf(KeyId.Letter('Q'))!!), unassigned.onKeyStroke(down(KeyId.Letter('Q'), at = 1100), snapshot("", 1100)).launcherKey)
    }

    @Test
    fun `B - the mode disarms after 5000 ms and A - a bare key with the home switch off does nothing`() {
        val p = pipeline(editable = false)
        p.onKeyStroke(down(sym, at = 0), snapshot(""))
        p.onPowerShortcutTimeout(5000)
        assertNull(p.powerShortcutArmedAtMs)
        p.foregroundIsHome = true
        val bare = p.onKeyStroke(down(space, at = 6000), snapshot("", 6000))
        assertNull(bare.launcherKey)
        assertFalse(bare.consumed)
    }

    @Test
    fun `the Sym panels toggle through the strip's direct-open path and close on request`() {
        val p = pipeline()
        p.toggleSymPage(3)
        assertEquals(3, p.currentSymPage)
        p.toggleSymPage(3)
        assertEquals(0, p.currentSymPage)
        p.toggleSymPage(4)
        p.closeSymPage()
        assertEquals(0, p.currentSymPage)
    }
}
