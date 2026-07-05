package it.palsoftware.pastiera

import org.junit.Assert.assertNotEquals
import org.junit.Test

class KeyboardThemeModelTest {
    @Test
    fun keyboardThemePresetsUseDistinctLockedLedColors() {
        keyboardThemePresets().forEach { preset ->
            assertNotEquals(
                "${preset.name} locked LED color should differ from active",
                preset.ledActive,
                preset.ledLocked
            )
            assertNotEquals(
                "${preset.name} locked LED color should differ from inactive",
                preset.ledInactive,
                preset.ledLocked
            )
        }
    }
}
