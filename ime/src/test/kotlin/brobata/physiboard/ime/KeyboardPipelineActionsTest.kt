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
    private fun repeat(key: KeyId, at: Long, count: Int = 1) = KeyStroke(key, KeyEdge.DOWN, count, at)

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
    fun `C - Sym pending plus the quick launcher's key fires the launcher on the release and marks the chord`() {
        val p = pipeline()
        p.onKeyStroke(down(sym), snapshot(""))
        val pressed = p.onKeyStroke(down(space, at = 110), snapshot("", 110))
        assertTrue(pressed.consumed)
        assertNull(pressed.launcherKey, "SS6.2 D: nothing runs until the key is released or held")
        assertTrue(pressed.ops.isEmpty())
        // The chord counts as used: the Sym release must not cycle a page.
        p.onKeyStroke(up(sym, 130), snapshot("", 130))
        assertEquals(0, p.currentSymPage)
        val released = p.onKeyStroke(up(space, 160), snapshot("", 160))
        assertTrue(released.consumed)
        assertTrue(released.ops.isEmpty())
        assertIs<LauncherKeyDecision.Run>(released.launcherKey)
        assertTrue((released.launcherKey as LauncherKeyDecision.Run).entry.isQuickLauncher)
        assertNull(p.launcherHoldDeadlineMs, "no timer left behind")
    }

    @Test
    fun `D - in a field, Sym held plus an assigned key held past the threshold opens its sheet and types nothing`() {
        val q = KeyId.Letter('Q')
        val qCode = AssignableKeys.keycodeOf(q)!!
        val app = ShortcutEntry.QUICK_LAUNCHER.copy(type = ShortcutEntry.TYPE_APP, commandId = null, launch = null, packageName = "com.whatsapp", title = "WhatsApp")
        val p = pipeline(KeyboardSettings(launcherShortcuts = LauncherShortcuts().assign(qCode, app)))
        p.onKeyStroke(down(sym, at = 1_000), snapshot("hi", 1_000))
        val pressed = p.onKeyStroke(down(q, at = 1_100), snapshot("hi", 1_100))
        assertTrue(pressed.consumed)
        assertNull(pressed.launcherKey)
        val threshold = p.layout.longPress.clampedThresholdMs
        assertEquals(1_100 + threshold, p.launcherHoldDeadlineMs)
        assertNull(p.onLauncherHoldTick(1_100 + threshold - 1))
        // The system's auto-repeat starts before the threshold: swallowed, no Sym chord symbol typed.
        val early = p.onKeyStroke(repeat(q, at = 1_500), snapshot("hi", 1_500))
        assertTrue(early.consumed)
        assertTrue(early.ops.isEmpty(), "a repeat under Sym must not type the chord symbol: ${early.ops}")
        assertNull(early.launcherKey)
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(qCode, byHold = true), p.onLauncherHoldTick(1_100 + threshold))
        assertNull(p.launcherHoldDeadlineMs)
        val late = p.onKeyStroke(repeat(q, at = 1_700, count = 2), snapshot("hi", 1_700))
        assertTrue(late.consumed && late.ops.isEmpty() && late.launcherKey == null, "the sheet opens once and nothing launches")
        val released = p.onKeyStroke(up(q, 1_900), snapshot("hi", 1_900))
        assertTrue(released.consumed && released.ops.isEmpty())
        assertNull(released.launcherKey, "a hold never launches as well")
        p.onKeyStroke(up(sym, 1_950), snapshot("hi", 1_950))
        assertEquals(0, p.currentSymPage)
        // The next press of the key is an ordinary letter again.
        val typed = p.onKeyStroke(down(q, at = 3_000), snapshot("hi", 3_000))
        assertTrue(typed.ops.any { it is EditorOp.CommitText }, "plain Q types again: ${typed.ops}")
    }

    @Test
    fun `D - a field that changes while the key is held drops the press but still swallows its release`() {
        val p = pipeline()
        p.onKeyStroke(down(sym), snapshot(""))
        p.onKeyStroke(down(space, at = 110), snapshot("", 110))
        p.onStartInput(FieldContext(FieldKind.NORMAL))
        assertNull(p.launcherHoldDeadlineMs, "no sheet can open later from a release lost with the old field")
        val released = p.onKeyStroke(up(space, 160), snapshot("", 160))
        assertTrue(released.consumed)
        assertNull(released.launcherKey)
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
        val pressed = p.onKeyStroke(down(space, at = 1200), snapshot("", 1200))
        assertTrue(pressed.consumed)
        assertNull(pressed.launcherKey)
        assertNull(p.powerShortcutArmedAtMs)
        assertIs<LauncherKeyDecision.Run>(p.onKeyStroke(up(space, 1300), snapshot("", 1300)).launcherKey)
        val unassigned = pipeline(editable = false)
        unassigned.onKeyStroke(down(sym, at = 1000), snapshot(""))
        // An unassigned key keeps its old behaviour: the sheet on the down; its repeats and release are swallowed.
        assertEquals(LauncherKeyDecision.OpenAssignmentSheet(AssignableKeys.keycodeOf(KeyId.Letter('Q'))!!), unassigned.onKeyStroke(down(KeyId.Letter('Q'), at = 1100), snapshot("", 1100)).launcherKey)
        assertTrue(unassigned.onKeyStroke(repeat(KeyId.Letter('Q'), at = 1600), snapshot("", 1600)).let { it.consumed && it.launcherKey == null })
        assertTrue(unassigned.onKeyStroke(up(KeyId.Letter('Q'), 1700), snapshot("", 1700)).let { it.consumed && it.launcherKey == null })
    }

    /**
     * spec SS6.2 B: arming the mode suspends an active nav-mode latch and restores it once the
     * mode disarms -- but only while there is still no field to restore it into. Before this fix,
     * [KeyboardPipeline.onStartInput] never touched the armed mode at all, so a field focused
     * mid-arm left the restore pending; the 5000 ms timeout (exercised here) would then re-latch
     * nav mode into the field the user was already typing in.
     */
    @Test
    fun `B - focusing a real field while armed cancels the pending nav-mode restore instead of applying it later`() {
        val p = pipeline(editable = false)
        val ctrl = KeyId.Modifier(ModifierKey.CTRL)
        p.onKeyStroke(KeyStroke(ctrl, KeyEdge.DOWN, 0, 0), snapshot(""))
        p.onKeyStroke(KeyStroke(ctrl, KeyEdge.UP, 0, 50), snapshot(""))
        p.onKeyStroke(KeyStroke(ctrl, KeyEdge.DOWN, 0, 400), snapshot("")) // nav mode now latched
        p.onKeyStroke(KeyStroke(ctrl, KeyEdge.UP, 0, 450), snapshot(""))

        p.onKeyStroke(down(sym, at = 1000), snapshot("")) // arms the mode, suspending the latch
        assertEquals(1000L, p.powerShortcutArmedAtMs)

        // A real field focuses before the 5000 ms disarm timer fires.
        p.onStartInput(FieldContext(FieldKind.NORMAL))
        assertNull(p.powerShortcutArmedAtMs, "starting a real field must cancel the armed mode outright")

        // The timer firing afterward (or any later key) must not restore nav mode into that field.
        p.onPowerShortcutTimeout(6000)
        val typed = p.onKeyStroke(KeyStroke(KeyId.Letter('E'), KeyEdge.DOWN, 0, 7000), snapshot("", 7000))
        assertTrue(typed.ops.none { it is EditorOp.SendKey }, "nav mode must not have re-latched into the field being typed in: ${typed.ops}")
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
