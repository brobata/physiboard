package brobata.physiboard.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: keys-and-modifiers.md SS12.1, the version-stamped migration for `ctrl_key_mappings.json`. */
class CtrlMappingMigrationTest {

    @Test
    fun `an old file missing the four word-motion keys and B gets every default filled in`() {
        val table = CtrlMappingTable(mapOf(KeyId.Letter('Q') to CtrlMapping.Keycode(KeyId.Control(ControlKey.ESCAPE))))

        val migrated = CtrlMappingMigration.migrate(table, storedVersion = 0)

        assertEquals(CtrlMapping.NamedAction("move_word_left"), migrated.mappingFor(KeyId.Letter('N')))
        assertEquals(CtrlMapping.NamedAction("move_word_right"), migrated.mappingFor(KeyId.Letter('M')))
        assertEquals(CtrlMapping.NamedAction("expand_selection_word_left"), migrated.mappingFor(KeyId.Letter('U')))
        assertEquals(CtrlMapping.NamedAction("expand_selection_word_right"), migrated.mappingFor(KeyId.Letter('I')))
        assertEquals(CtrlMapping.Command("pastiera.toggle_software_keyboard_mode"), migrated.mappingFor(KeyId.Letter('B')))
        // Untouched keys survive migration unchanged.
        assertEquals(CtrlMapping.Keycode(KeyId.Control(ControlKey.ESCAPE)), migrated.mappingFor(KeyId.Letter('Q')))
    }

    @Test
    fun `a key explicitly stored as none is treated the same as missing`() {
        val table = CtrlMappingTable(mapOf(KeyId.Letter('N') to CtrlMapping.None))

        val migrated = CtrlMappingMigration.migrate(table, storedVersion = 1)

        assertEquals(CtrlMapping.NamedAction("move_word_left"), migrated.mappingFor(KeyId.Letter('N')))
    }

    @Test
    fun `a key the user already set to something else is never overwritten`() {
        val table = CtrlMappingTable(mapOf(KeyId.Letter('N') to CtrlMapping.NamedAction("copy")))

        val migrated = CtrlMappingMigration.migrate(table, storedVersion = 0)

        assertEquals(CtrlMapping.NamedAction("copy"), migrated.mappingFor(KeyId.Letter('N')))
    }

    @Test
    fun `a file already at the current version is returned untouched, even with a key set to none`() {
        val table = CtrlMappingTable(mapOf(KeyId.Letter('N') to CtrlMapping.None))

        val migrated = CtrlMappingMigration.migrate(table, storedVersion = CTRL_MAPPING_DEFAULTS_VERSION)

        assertEquals(CtrlMapping.None, migrated.mappingFor(KeyId.Letter('N')), "a deliberate post-migration 'none' must survive")
    }
}
