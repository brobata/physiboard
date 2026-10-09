package brobata.physiboard.core.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: broker-privileged-toolbox.md SS7 step 5 and SS10 step 6, the spell checker chosen once after pairing. */
class SpellCheckerSelectionTest {

    private val ours = SpellCheckerSelection.component("brobata.physiboard.dev3")
    private val vendor = "com.android.inputmethod.latin/com.android.inputmethod.latin.spellcheck.AndroidSpellCheckerService"
    private val release = SpellCheckerSelection.component("brobata.physiboard")

    private fun reading(selected: String? = vendor, enabled: String? = "1", subtype: String? = "0") = SpellCheckerReading(selected, enabled, subtype)

    private fun decide(owner: SpellCheckerOwner, reading: SpellCheckerReading? = reading(), autoSelect: Boolean = true, done: Boolean = false) =
        SpellCheckerSelection.decide(autoSelect, done, reading, owner, ours)

    @Test
    fun `the component is the running build's package plus the shared service class`() {
        assertEquals("brobata.physiboard.dev3/brobata.physiboard.ime.PhysiBoardSpellCheckerService", ours)
    }

    @Test
    fun `the read parses three lines and the string null as unset`() {
        assertEquals(SpellCheckerReading(vendor, "1", null), SpellCheckerSelection.parse("$vendor\n1\nnull\n"))
        assertEquals(SpellCheckerReading(null, null, null), SpellCheckerSelection.parse("null\nnull\nnull"))
        assertNull(SpellCheckerSelection.parse("null\n1"))
        assertEquals(SpellCheckerReading(null, "1", "0"), SpellCheckerSelection.parse("\n1\n0\n"), "an empty row is unset, not a missing line")
        assertNull(SpellCheckerSelection.parse(""))
    }

    @Test
    fun `owner - nothing selected, ours, preinstalled, installed by the user, and a leftover`() {
        val pre = setOf("com.android.inputmethod.latin")
        val installed = setOf("com.android.inputmethod.latin", "brobata.physiboard", "com.grammarly.android.keyboard")
        fun owner(selected: String?) = SpellCheckerSelection.ownerOf(selected, "brobata.physiboard.dev3", { it in pre }, { it in installed })
        assertNull(SpellCheckerSelection.ownerOf(vendor, "brobata.physiboard.dev3", { null }, { true }), "a failed check is unknown, never 'not installed'")
        assertNull(SpellCheckerSelection.ownerOf(vendor, "brobata.physiboard.dev3", { false }, { null }))
        assertEquals(SpellCheckerOwner.NONE, owner(null))
        assertEquals(SpellCheckerOwner.NONE, owner(""))
        assertEquals(SpellCheckerOwner.OURS, owner(ours))
        assertEquals(SpellCheckerOwner.PREINSTALLED, owner(vendor))
        assertEquals(SpellCheckerOwner.USER_INSTALLED, owner(release), "the other PhysiBoard build is a choice, not a default")
        assertEquals(SpellCheckerOwner.MISSING, owner("com.gone.app/com.gone.app.Spell"))
        assertEquals(SpellCheckerOwner.MISSING, owner("not a component"))
    }

    @Test
    fun `the factory default is replaced, spell checking switched on, and what was there recorded`() {
        val before = reading(enabled = "0", subtype = "123")
        val plan = assertIs<SpellCheckerPlan.Select>(decide(SpellCheckerOwner.PREINSTALLED, before))
        assertEquals(
            "settings put secure selected_spell_checker '$ours'; settings put secure selected_spell_checker_subtype 0; settings put secure spell_checker_enabled 1",
            plan.line,
        )
        assertEquals(before, plan.previous)
    }

    @Test
    fun `nothing selected or a leftover is replaced too`() {
        assertIs<SpellCheckerPlan.Select>(decide(SpellCheckerOwner.NONE, reading(selected = null)))
        assertIs<SpellCheckerPlan.Select>(decide(SpellCheckerOwner.MISSING))
    }

    @Test
    fun `a spell checker the user installed is left alone, and never looked at again`() {
        assertEquals(SpellCheckerPlan.Leave(SpellCheckerSelection.REASON_OTHER_CHOSEN, markDone = true), decide(SpellCheckerOwner.USER_INSTALLED))
    }

    @Test
    fun `already ours and on is left alone and marked done`() {
        assertEquals(SpellCheckerPlan.Leave(SpellCheckerSelection.REASON_ALREADY_OURS, markDone = true), decide(SpellCheckerOwner.OURS, reading(selected = ours)))
    }

    @Test
    fun `ours but spell checking off only switches it on`() {
        val plan = assertIs<SpellCheckerPlan.Select>(decide(SpellCheckerOwner.OURS, reading(selected = ours, enabled = "0")))
        assertEquals("settings put secure spell_checker_enabled 1", plan.line)
    }

    @Test
    fun `the setting off, an earlier decision, or an unreadable phone write nothing and do not mark done`() {
        assertEquals(SpellCheckerPlan.Leave(SpellCheckerSelection.REASON_DISABLED, false), decide(SpellCheckerOwner.PREINSTALLED, autoSelect = false))
        assertEquals(SpellCheckerPlan.Leave(SpellCheckerSelection.REASON_DONE, false), decide(SpellCheckerOwner.PREINSTALLED, done = true))
        assertEquals(SpellCheckerPlan.Leave(SpellCheckerSelection.REASON_UNREADABLE, false), decide(SpellCheckerOwner.PREINSTALLED, reading = null))
        assertEquals(SpellCheckerPlan.Leave(SpellCheckerSelection.REASON_UNREADABLE, false), SpellCheckerSelection.decide(true, false, reading(), null, ours))
    }

    @Test
    fun `the reset puts every row back, deleting the ones that were unset`() {
        val line = SpellCheckerSelection.revertLine(reading(selected = vendor, enabled = null, subtype = "42"), reading(selected = ours), ours)
        assertEquals(
            "settings put secure selected_spell_checker '$vendor'; settings put secure selected_spell_checker_subtype '42'; settings delete secure spell_checker_enabled",
            line,
        )
    }

    @Test
    fun `a class name with a dollar sign is quoted so the shell keeps it`() {
        val inner = "com.foo/com.foo.Spell\$Service"
        val line = SpellCheckerSelection.revertLine(reading(selected = inner), reading(selected = ours), ours)!!
        assertTrue(line.startsWith("settings put secure selected_spell_checker 'com.foo/com.foo.Spell\$Service';"))
    }

    @Test
    fun `the reset leaves a spell checker the user picked since alone`() {
        assertNull(SpellCheckerSelection.revertLine(reading(selected = vendor), reading(selected = release), ours))
    }

    @Test
    fun `a recorded value that is not a component or a number is deleted, never written into the line`() {
        val line = SpellCheckerSelection.revertLine(reading(selected = "x; reboot", enabled = "1; reboot", subtype = "\$(id)"), reading(selected = ours), ours)!!
        assertFalse(line.contains("reboot"))
        assertFalse(line.contains("id"))
        assertEquals(
            "settings delete secure selected_spell_checker; settings delete secure selected_spell_checker_subtype; settings delete secure spell_checker_enabled",
            line,
        )
    }

    @Test
    fun `package lists match whole lines only`() {
        assertTrue(SpellCheckerSelection.listsPackage("package:com.a.b\npackage:com.a.bc\n", "com.a.b"))
        assertFalse(SpellCheckerSelection.listsPackage("package:com.a.bc\n", "com.a.b"))
        assertTrue(SpellCheckerSelection.isSafePackage("com.android.inputmethod.latin"))
        assertFalse(SpellCheckerSelection.isSafePackage("com.a; rm -rf /"))
    }

    @Test
    fun `the app offers the shortcut on the same rules`() {
        assertTrue(SpellCheckerSelection.wouldSelect(true, enabled = true, owner = SpellCheckerOwner.PREINSTALLED))
        assertTrue(SpellCheckerSelection.wouldSelect(true, enabled = false, owner = SpellCheckerOwner.OURS))
        assertFalse(SpellCheckerSelection.wouldSelect(true, enabled = true, owner = SpellCheckerOwner.OURS))
        assertFalse(SpellCheckerSelection.wouldSelect(true, enabled = true, owner = SpellCheckerOwner.USER_INSTALLED))
        assertFalse(SpellCheckerSelection.wouldSelect(false, enabled = false, owner = SpellCheckerOwner.NONE))
    }
}
