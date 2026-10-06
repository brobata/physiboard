package brobata.physiboard.core.actions.kaomoji

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** spec: expansion-clipboard-pickers-launcher.md SS4.9. */
class KaomojiTest {

    private val groups = KaomojiCatalog.groups

    @Test
    fun `the collection is about three hundred kaomoji in twelve mood groups`() {
        assertEquals(
            listOf("JOY", "LOVE", "EMBARRASSED", "SAD", "ANGRY", "SURPRISE", "CONFUSED", "SHRUG", "GREETING", "SLEEPY", "ANIMALS", "ACTIONS"),
            groups.map { it.id },
        )
        val total = groups.sumOf { it.entries.size }
        assertTrue(total in 280..340, "$total kaomoji")
        assertTrue(groups.all { it.entries.size >= 10 }, groups.joinToString { "${it.id}=${it.entries.size}" })
    }

    @Test
    fun `every kaomoji is in exactly one group and has a name and a tag`() {
        val all = groups.flatMap { g -> g.entries.map { it.text } }
        assertEquals(all.size, all.toSet().size, "duplicates: ${all.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}")
        for (g in groups) for (k in g.entries) {
            assertTrue(k.text.isNotBlank() && k.text == k.text.trim(), "'${k.text}'")
            assertTrue(k.name.isNotBlank(), k.text)
            assertTrue(k.tags.isNotEmpty(), k.text)
        }
        assertTrue(groups.all { it.tabLabel.isNotBlank() && it.label.isNotBlank() })
    }

    @Test
    fun `the classic kaomoji are where a user looks for them`() {
        fun groupOf(text: String) = groups.first { g -> g.entries.any { it.text == text } }.id
        assertEquals("SHRUG", groupOf("¯\\_(ツ)_/¯"))
        assertEquals("ACTIONS", groupOf("(╯°□°)╯︵ ┻━┻"))
        assertEquals("ACTIONS", groupOf("( ͡° ͜ʖ ͡°)"))
        assertEquals("ANGRY", groupOf("ಠ_ಠ"))
        assertEquals("ANIMALS", groupOf("ʕ•ᴥ•ʔ"))
    }

    @Test
    fun `search ranks a name match first and finds by tag and by mood`() {
        assertEquals("( ͡° ͜ʖ ͡°)", KaomojiCatalog.search("lenny face").first().kaomoji.text)
        assertEquals("¯\\_(ツ)_/¯", KaomojiCatalog.search("shrug").first().kaomoji.text)
        assertEquals("(╯°□°)╯︵ ┻━┻", KaomojiCatalog.search("table flip").first().kaomoji.text)
        assertEquals("(╯°□°)╯︵ ┻━┻", KaomojiCatalog.search("flip table").first().kaomoji.text)
        // Every other table flip is still found, by its tag.
        assertTrue(KaomojiCatalog.search("table flip").map { it.kaomoji.text }.containsAll(listOf("(ノಠ益ಠ)ノ彡┻━┻", "┻━┻ ︵ヽ(`Д´)ﾉ︵ ┻━┻")))
        // A tag finds kaomoji named otherwise; "idk" is only a tag.
        assertTrue(KaomojiCatalog.search("idk").any { it.kaomoji.text == "¯\\_(ツ)_/¯" })
        // The group label is a weak tag: every Sleepy kaomoji answers "sleepy", the one named so first.
        val sleepy = KaomojiCatalog.search("sleepy")
        assertEquals("(´-ω-`)", sleepy.first().kaomoji.text)
        val sleepyGroup = groups.first { it.id == "SLEEPY" }.entries.map { it.text }.toSet()
        assertTrue(sleepy.map { it.kaomoji.text }.containsAll(sleepyGroup))
    }

    @Test
    fun `a name beats the same word as a tag, and equal scores keep group order`() {
        val custom = listOf(
            KaomojiGroup("A", "Alpha", "a", listOf(Kaomoji("x1", "wave hello", listOf("hi")), Kaomoji("x2", "other", listOf("hi")))),
            KaomojiGroup("B", "Beta", "b", listOf(Kaomoji("x3", "hi", listOf("wave")))),
        )
        val hits = KaomojiIndex(custom).search("hi")
        assertEquals(listOf("x3", "x1", "x2"), hits.map { it.kaomoji.text })
        // An exact tag still beats a name that only starts with the word.
        assertEquals(listOf("x3", "x1"), KaomojiIndex(custom).search("wave").map { it.kaomoji.text })
        assertEquals(listOf("x2"), KaomojiIndex(custom).search("x2").map { it.kaomoji.text })
        assertTrue(KaomojiIndex(custom).search("").isEmpty())
    }

    @Test
    fun `parse reads the three fields and rejects a malformed line`() {
        val parsed = KaomojiCatalog.parse(listOf(KaomojiData.Source("G", "Group", "g", "\n(^_^)  ::  smile  ::  happy, joy\n\n")))
        assertEquals(listOf(Kaomoji("(^_^)", "smile", listOf("happy", "joy"))), parsed.single().entries)
        val failed = runCatching { KaomojiCatalog.parse(listOf(KaomojiData.Source("G", "Group", "g", "(^_^) smile"))) }
        assertTrue(failed.isFailure)
    }
}
