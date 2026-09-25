package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: app-shell.md SS5.1, SS29 T18-T19. */
class ChangeRecordSectionTest {

    @Test
    fun `T18 the card stops at the marker and trims the section`() {
        val record = """
            ## 2.0.7 (2026-09-01)
            First paragraph of the summary.

            Second paragraph of the summary.
            <!-- /card -->
            - **Bullet one.** detail
            - **Bullet two.** detail

            ## 2.0.6 (2026-08-01)
            older summary
        """.trimIndent()
        val card = ChangeRecordSection.extractCard(record, "2.0.7")
        assertEquals("First paragraph of the summary.\n\nSecond paragraph of the summary.\n", card)
    }

    @Test
    fun `T19 an unreleased section at the top is skipped for the first numeric heading`() {
        val record = """
            ## Unreleased
            work in progress notes

            ## 2.0.7 (2026-09-01)
            The real summary.
            <!-- /card -->
            - bullet
        """.trimIndent()
        assertEquals("The real summary.\n", ChangeRecordSection.extractCard(record, "2.0.7"))
    }

    @Test
    fun `an exact heading match is not fooled by a longer version sharing its prefix`() {
        val record = "## 2.0.71 (2026-09-01)\nOnly section, matches by fallback, not by exact prefix.\n"
        // "2.0.7" has no exact "## 2.0.7 " heading here (only "2.0.71"), so the fallback rule
        // (first `## <digits>.<digits>` heading) is what picks this section, not the exact match.
        assertEquals("Only section, matches by fallback, not by exact prefix.\n", ChangeRecordSection.extractCard(record, "2.0.7"))
    }

    @Test
    fun `a section without the marker ships whole`() {
        val record = "## 2.0.7 (2026-09-01)\nOne line only.\n"
        assertEquals("One line only.\n", ChangeRecordSection.extractCard(record, "2.0.7"))
    }

    @Test
    fun `no numeric heading at all yields an empty card`() {
        assertEquals("", ChangeRecordSection.extractCard("## Unreleased\nnothing else", "2.0.7"))
    }
}
