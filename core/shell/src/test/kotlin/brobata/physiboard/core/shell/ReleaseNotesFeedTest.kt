package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: app-shell.md SS14, SS29 T22-T23. */
class ReleaseNotesFeedTest {

    @Test
    fun `T22 a tag_name mismatch yields nothing`() {
        val body = """{"tag_name": "v2.0.6", "name": "PhysiBoard 2.0.6", "body": "- one\n- two"}"""
        assertNull(ReleaseNotesFeed.parse(body, requestedVersion = "2.0.7"))
    }

    @Test
    fun `T23 only the first 8 dash or star bullets become highlights, with bold markup stripped`() {
        val bullets = (1..10).joinToString("\n") { "- **Item $it** did a thing" }
        val body = """{"tag_name": "v2.0.7", "name": "PhysiBoard 2.0.7", "html_url": "https://github.com/brobata/physiboard/releases/tag/v2.0.7",
            "body": "## Heading\nSome prose line.\n$bullets"}"""
        val notes = ReleaseNotesFeed.parse(body, requestedVersion = "2.0.7")
        requireNotNull(notes)
        assertEquals(8, notes.highlights.size)
        assertEquals("Item 1 did a thing", notes.highlights.first())
        assertEquals(false, notes.highlights.any { it.contains("**") })
    }

    @Test
    fun `no bullet lines yields nothing even when the tag matches`() {
        val body = """{"tag_name": "v2.0.7", "body": "just prose, no bullets"}"""
        assertNull(ReleaseNotesFeed.parse(body, requestedVersion = "2.0.7"))
    }

    @Test
    fun `title falls back to the version when name is blank`() {
        val body = """{"tag_name": "v2.0.7", "name": "", "body": "- one"}"""
        assertEquals("PhysiBoard 2.0.7", ReleaseNotesFeed.parse(body, requestedVersion = "2.0.7")?.title)
    }

    @Test
    fun `docs url falls back to the releases list when html_url is not a github link`() {
        val body = """{"tag_name": "v2.0.7", "body": "- one", "html_url": "https://example.com/x"}"""
        assertEquals("https://github.com/brobata/physiboard/releases", ReleaseNotesFeed.parse(body, requestedVersion = "2.0.7")?.docsUrl)
    }
}
