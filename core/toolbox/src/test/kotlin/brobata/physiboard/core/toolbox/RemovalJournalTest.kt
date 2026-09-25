package brobata.physiboard.core.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemovalJournalTest {
    @Test
    fun `T10 disabling an active package journals its prior state`() {
        val record = JournalRecord("com.agui.game", JournalPriorState.ACTIVE, JournalAction.DISABLED, atMs = 5L)
        val journal = RemovalJournalCodec.upsert(emptyList(), record)
        assertEquals(listOf(record), journal)
    }

    @Test
    fun `T12 acting on the same package twice replaces the record rather than stacking`() {
        val first = JournalRecord("com.agui.game", JournalPriorState.ACTIVE, JournalAction.DISABLED, atMs = 1L)
        val second = JournalRecord("com.agui.game", JournalPriorState.ACTIVE, JournalAction.DISABLED, atMs = 2L)
        val journal = RemovalJournalCodec.upsert(RemovalJournalCodec.upsert(emptyList(), first), second)
        assertEquals(listOf(second), journal)
    }

    @Test
    fun `T13 an unknown prev or action reads as ACTIVE and DISABLED`() {
        val decoded = RemovalJournalCodec.decode("""[{"pkg":"x","prev":"BOGUS","action":"BOGUS","at":5}]""")
        assertEquals(listOf(JournalRecord("x", JournalPriorState.ACTIVE, JournalAction.DISABLED, 5L)), decoded)
    }

    @Test
    fun `T14 unreadable json decodes as empty`() {
        assertEquals(emptyList(), RemovalJournalCodec.decode("not json"))
        assertEquals(emptyList(), RemovalJournalCodec.decode(null))
        assertEquals(emptyList(), RemovalJournalCodec.decode(""))
    }

    @Test
    fun `encode then decode round-trips`() {
        val records = listOf(
            JournalRecord("com.agui.game", JournalPriorState.ACTIVE, JournalAction.DISABLED, 10L),
            JournalRecord("com.tiqiaa.icontrol", JournalPriorState.UNINSTALLED, JournalAction.UNINSTALLED, 20L),
        )
        val decoded = RemovalJournalCodec.decode(RemovalJournalCodec.encode(records))
        assertEquals(records, decoded)
    }

    @Test
    fun `remove drops only the named package`() {
        val records = listOf(
            JournalRecord("a", JournalPriorState.ACTIVE, JournalAction.DISABLED, 1L),
            JournalRecord("b", JournalPriorState.ACTIVE, JournalAction.DISABLED, 2L),
        )
        val after = RemovalJournalCodec.remove(records, "a")
        assertTrue(after.none { it.packageName == "a" })
        assertEquals(1, after.size)
    }
}
