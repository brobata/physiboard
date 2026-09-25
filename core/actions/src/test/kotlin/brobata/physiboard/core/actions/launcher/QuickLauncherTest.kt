package brobata.physiboard.core.actions.launcher

import brobata.physiboard.core.actions.commands.BuiltInCommands
import brobata.physiboard.core.actions.commands.Command
import brobata.physiboard.core.actions.commands.CommandSource
import brobata.physiboard.core.actions.commands.LaunchSpec
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.ModifierKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS12, T40 to T51, and the SS7 / SS11 rows. */
class QuickLauncherTest {

    private fun app(label: String, pkg: String = label.lowercase().replace(" ", "")) = BuiltInCommands.app("com.$pkg", label)
    private fun row(command: Command, c: CommandCustomization = CommandCustomization()) = LauncherRow(command, c)
    private val settings = QuickLauncherSettings()

    @Test
    fun `T46 Termux ranks before Telegram and Settings only matches as a subsequence`() {
        val rows = listOf(row(app("Telegram")), row(app("Termux")), row(app("Settings", "android.settings")))
        val ranked = QuickLauncherRanking.rank(rows, "te", settings)
        // SPEC GAP: T46's parenthetical arithmetic (80 + gap 1 + 8 = 89) assumes an `e` after the
        // `t` in "settings"; there is none, so by the rule the same row states ("every query
        // character to appear in order") Settings has no subsequence match and is not listed.
        assertEquals(listOf("Termux", "Telegram"), ranked.map { it.command.label })
        assertEquals(16, QuickLauncherRanking.score(rows[1], "te", true))
        assertEquals(18, QuickLauncherRanking.score(rows[0], "te", true))
        assertNull(QuickLauncherRanking.score(rows[2], "te", true))
        assertEquals(89, 80 + QuickLauncherRanking.subsequenceGap("stxeting", "te")!! + 8, "the arithmetic T46 describes, on a label that does have the subsequence")
    }

    @Test
    fun `T47 a typo match exists but the prefix match wins`() {
        val whatsapp = row(app("whatsapp"))
        assertEquals(206, QuickLauncherRanking.typoScore("whatsapp", "whatsap"))
        assertEquals(18, QuickLauncherRanking.score(whatsapp, "whatsap", true))
        assertEquals(205, QuickLauncherRanking.score(row(Command("x", CommandSource.APPS, "whatsapp", null, LaunchSpec.AppPackage("x"))), "whatsopp", true), "one substitution, no length difference")
    }

    @Test
    fun `a two-character query never gets a typo match`() {
        assertNull(QuickLauncherRanking.typoScore("whatsapp", "wh"))
        assertNull(QuickLauncherRanking.score(row(Command("x", CommandSource.APPS, "whatsapp", null, LaunchSpec.AppPackage("x"))), "xq", true))
    }

    @Test
    fun `T48 a matching alias scores 0 and short-circuits`() {
        val wa = row(app("WhatsApp"), CommandCustomization(alias = "wa"))
        assertEquals(0, QuickLauncherRanking.score(wa, "wa", true))
        val nonMatchingAlias = row(app("WhatsApp"), CommandCustomization(alias = "zz"))
        assertEquals(10 + 8, QuickLauncherRanking.score(nonMatchingAlias, "wha", true), "an alias that does not match leaves the normal scores")
    }

    @Test
    fun `T49 a favourite's score is reduced by 25 and ranks first`() {
        val favorite = row(Command("a", CommandSource.APPS, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaz", null, LaunchSpec.AppPackage("a")), CommandCustomization(favorite = true, favoriteOrder = 0))
        val other = row(Command("b", CommandSource.APPS, "aaaaaaaaaz", null, LaunchSpec.AppPackage("b")))
        assertEquals(40 - 25, QuickLauncherRanking.score(favorite, "a", true))
        assertEquals(20, QuickLauncherRanking.score(other, "a", true))
        assertEquals(listOf("a", "b"), QuickLauncherRanking.rank(listOf(other, favorite), "a", settings).map { it.command.id })
        assertEquals(0, QuickLauncherRanking.score(row(app("ab"), CommandCustomization(favorite = true)), "ab", true), "not below 0")
    }

    @Test
    fun `T50 limit results shows the best three`() {
        val rows = (1..5).map { row(app("app$it")) }
        assertEquals(3, QuickLauncherRanking.rank(rows, "app", settings.copy(limitResults = true)).size)
        assertEquals(5, QuickLauncherRanking.rank(rows, "app", settings).size)
    }

    @Test
    fun `T51 an all-default customisation is removed from the document`() {
        val c = CommandCustomizations().setFavorite("app:x", true).setAlias("app:x", "x").setFavorite("app:x", false).setAlias("app:x", "")
        assertTrue(c.entries.isEmpty())
        assertEquals("{}", CommandCustomizations.encode(c))
        val parsed = CommandCustomizations.parse("""{"app:x":{"favorite":false,"hidden":false,"custom_search":"","favorite_order":2147483647},"app:y":{"hidden":true}}""")
        assertEquals(setOf("app:y"), parsed.entries.keys)
    }

    @Test
    fun `T52 the source visibility defaults`() {
        val v = brobata.physiboard.core.actions.commands.SourceVisibility.parse(null)
        assertTrue(v.isEnabled(CommandSource.APPS) && v.isEnabled(CommandSource.PHYSIBOARD))
        assertFalse(v.isEnabled(CommandSource.APP_ACTIONS) || v.isEnabled(CommandSource.DEVICE_CONTROL) || v.isEnabled(CommandSource.NAVIGATION))
        val parsed = brobata.physiboard.core.actions.commands.SourceVisibility.parse("""{"apps":{"quick_launcher":false},"nav_actions":{"quick_launcher":true}}""")
        assertFalse(parsed.isEnabled(CommandSource.APPS))
        assertTrue(parsed.isEnabled(CommandSource.NAVIGATION))
        assertTrue(parsed.isEnabled(CommandSource.PHYSIBOARD), "missing sources take the defaults")
    }

    @Test
    fun `hidden entries are never listed even when searched, and favourites come first with an empty query`() {
        val rows = listOf(
            row(app("Zed"), CommandCustomization(favorite = true, favoriteOrder = 1)),
            row(app("Alpha")),
            row(app("Beta"), CommandCustomization(hidden = true)),
            row(app("Gamma"), CommandCustomization(favorite = true, favoriteOrder = 0)),
        )
        assertEquals(listOf("Gamma", "Zed", "Alpha"), QuickLauncherRanking.rank(rows, "", settings).map { it.command.label })
        assertEquals(listOf("Gamma", "Zed"), QuickLauncherRanking.rank(rows, "", settings.copy(limitResults = true)).map { it.command.label })
        assertTrue(QuickLauncherRanking.rank(rows, "bet", settings).isEmpty())
    }

    @Test
    fun `a new favourite gets one more than the highest order and move up swaps with its neighbour`() {
        var c = CommandCustomizations().setFavorite("a", true).setFavorite("b", true).setFavorite("c", true)
        assertEquals(listOf(0, 1, 2), listOf("a", "b", "c").map { c[it].favoriteOrder })
        c = c.moveFavorite("c", up = true)
        assertEquals(1, c["c"].favoriteOrder)
        assertEquals(2, c["b"].favoriteOrder)
        assertEquals(c, c.moveFavorite("a", up = true), "the first favourite cannot move up")
    }

    @Test
    fun `auto-launch fires only for a unique result of a non-blank query`() {
        val rows = listOf(row(app("Only")))
        assertEquals(rows[0], QuickLauncherRanking.autoLaunches("on", rows, settings.copy(openUniqueMatch = true)))
        assertNull(QuickLauncherRanking.autoLaunches("", rows, settings.copy(openUniqueMatch = true)))
        assertNull(QuickLauncherRanking.autoLaunches("on", rows, settings))
    }

    // ---- the sheet's keys, T40 to T44 ----

    private val space = KeyId.Control(ControlKey.SPACE)
    private val back = KeyId.Control(ControlKey.BACK)

    @Test
    fun `T40 Back is consumed on down and dismisses on up`() {
        val (afterDown, downEffect) = QuickLauncherKeys.onKeyDown(SheetKeyState(), back, symHeld = false, ctrl = false, quickLauncherKey = space, layoutText = null, eventChar = null)
        assertEquals(SheetKeyEffect.ConsumedOnly, downEffect)
        val (_, upEffect) = QuickLauncherKeys.onKeyUp(afterDown, back, cancelled = false)
        assertEquals(SheetKeyEffect.Dismiss, upEffect)
    }

    @Test
    fun `T41 Sym held plus the launcher's own key toggles it closed on release`() {
        val (afterDown, downEffect) = QuickLauncherKeys.onKeyDown(SheetKeyState(), space, symHeld = true, ctrl = false, quickLauncherKey = space, layoutText = " ", eventChar = ' ')
        assertEquals(SheetKeyEffect.ConsumedOnly, downEffect)
        val (_, upEffect) = QuickLauncherKeys.onKeyUp(afterDown, space, cancelled = false)
        assertEquals(SheetKeyEffect.Dismiss, upEffect)
    }

    @Test
    fun `releasing Sym slightly before the bound key still dismisses, per SS7-1`() {
        // The down-time decision (Sym was held when the key went down) must be remembered and
        // used at release, not recomputed from whatever is held live at that later moment.
        val (afterDown, downEffect) = QuickLauncherKeys.onKeyDown(SheetKeyState(), space, symHeld = true, ctrl = false, quickLauncherKey = space, layoutText = " ", eventChar = ' ')
        assertEquals(SheetKeyEffect.ConsumedOnly, downEffect)
        val (_, upEffect) = QuickLauncherKeys.onKeyUp(afterDown, space, cancelled = false)
        assertEquals(SheetKeyEffect.Dismiss, upEffect, "the sheet must still dismiss even though Sym is no longer held at release")
    }

    @Test
    fun `T42 a plain Sym tap is not consumed`() {
        val sym = KeyId.Modifier(ModifierKey.SYM)
        assertEquals(SheetKeyEffect.NotConsumed, QuickLauncherKeys.onKeyDown(SheetKeyState(), sym, false, false, space, null, null).second)
        assertEquals(SheetKeyEffect.NotConsumed, QuickLauncherKeys.onKeyUp(SheetKeyState(), sym, false).second)
    }

    @Test
    fun `T43 and T44 a letter grows the query, through the layout even with Alt`() {
        assertEquals(SheetKeyEffect.AppendText("a"), QuickLauncherKeys.onKeyDown(SheetKeyState(), KeyId.Letter('A'), false, false, space, "a", 'a').second)
        assertEquals(SheetKeyEffect.AppendText("a"), QuickLauncherKeys.onKeyDown(SheetKeyState(), KeyId.Letter('A'), false, false, space, "a", 'ä').second, "Alt-modified letters still go through the layout")
        assertEquals(SheetKeyEffect.NotConsumed, QuickLauncherKeys.onKeyDown(SheetKeyState(), KeyId.Letter('A'), false, ctrl = true, quickLauncherKey = space, layoutText = "a", eventChar = 'a').second)
    }

    @Test
    fun `Enter launches the top match once and a cancelled release does nothing`() {
        val (afterDown, effect) = QuickLauncherKeys.onKeyDown(SheetKeyState(), KeyId.Control(ControlKey.ENTER), false, false, space, null, '\n')
        assertEquals(SheetKeyEffect.LaunchTop, effect)
        assertEquals(SheetKeyEffect.ConsumedOnly, QuickLauncherKeys.onKeyUp(afterDown, KeyId.Control(ControlKey.ENTER), false).second)
        assertEquals(SheetKeyEffect.LaunchTop, QuickLauncherKeys.onKeyUp(SheetKeyState(), KeyId.Control(ControlKey.ENTER), false).second, "a release without a prior handled down launches")
        assertEquals(SheetKeyEffect.ConsumedOnly, QuickLauncherKeys.onKeyUp(afterDown, KeyId.Control(ControlKey.ENTER), cancelled = true).second)
    }

    @Test
    fun `the display label puts the alias first when asked`() {
        val r = row(app("WhatsApp"), CommandCustomization(alias = "wa"))
        assertEquals("wa | WhatsApp", r.displayLabel(showAliasFirst = true))
        assertEquals("WhatsApp", r.displayLabel(showAliasFirst = false))
        assertEquals("com.whatsapp", r.displaySubtitle)
    }
}
