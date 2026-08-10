package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import it.palsoftware.pastiera.ClicksPowerKeyboardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PhysicalKeyResolverTest {
    private val resolver = PhysicalKeyResolver()

    @Test
    fun directEventUsesCanonicalPhysicalScanCode() {
        val result = resolve(KeyEvent.KEYCODE_E, scanCode = 18)

        assertEquals(setOf(ClicksPowerKeyboardLayout.E), result.candidates)
    }

    @Test
    fun bottomRowUsesPhysicalLauncherAndRedClicksPositions() {
        assertEquals(
            setOf(ClicksPowerKeyboardLayout.LAUNCHER),
            resolve(KeyEvent.KEYCODE_META_LEFT, scanCode = 125).candidates
        )
        assertEquals(
            setOf(ClicksPowerKeyboardLayout.RED_CLICKS),
            resolve(KeyEvent.KEYCODE_TAB, scanCode = 15).candidates
        )
        assertTrue(
            ClicksPowerKeyboardLayout.isAdjacent(
                ClicksPowerKeyboardLayout.N,
                ClicksPowerKeyboardLayout.RED_CLICKS
            )
        )
        assertTrue(
            ClicksPowerKeyboardLayout.isAdjacent(
                ClicksPowerKeyboardLayout.X,
                ClicksPowerKeyboardLayout.LAUNCHER
            )
        )
    }

    @Test
    fun currentFirmwareSymUUnderscoreResolvesToPhysicalU() {
        val result = resolve(
            KeyEvent.KEYCODE_MINUS,
            scanCode = 12,
            metaState = KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON
        )

        assertEquals(setOf(ClicksPowerKeyboardLayout.U), result.candidates)
    }

    @Test
    fun currentFirmwareSymUPlusFatFingerSevenSuppressesOnlySeven() {
        val filter = AccidentalKeyPressFilter()
        val configuration = AccidentalKeyPressFilter.Configuration(
            overlapRule = AccidentalKeyPressFilter.OverlapRule.ADJACENT
        )
        val underscore = event(
            keyCode = KeyEvent.KEYCODE_MINUS,
            scanCode = 12,
            metaState = KeyEvent.META_SHIFT_ON
        )
        val seven = event(
            keyCode = KeyEvent.KEYCODE_7,
            scanCode = 8,
            eventTime = 1_010L
        )

        assertNull(
            filter.shouldConsumeKeyDown(
                KeyEvent.KEYCODE_MINUS,
                underscore,
                resolver.resolve(
                    KeyEvent.KEYCODE_MINUS,
                    underscore,
                    ClicksPowerKeyboardLayout
                ),
                configuration
            )
        )
        assertNotNull(
            filter.shouldConsumeKeyDown(
                KeyEvent.KEYCODE_7,
                seven,
                resolver.resolve(KeyEvent.KEYCODE_7, seven, ClicksPowerKeyboardLayout),
                configuration
            )
        )
    }

    @Test
    fun configuredSymNumberRemapResolvesBackToPhysicalNumberKey() {
        val state = ClicksPowerKeyboardState(
            numberRemaps = MutableList<ByteArray?>(9) { null }.also {
                it[6] = byteArrayOf(0x29, 0x00)
            }
        )

        val result = resolve(KeyEvent.KEYCODE_ESCAPE, scanCode = 1, state = state)

        assertEquals(setOf(ClicksPowerKeyboardLayout.DIGIT_7), result.candidates)
        assertTrue(result.isDefinitelyNumberRowKey())
    }

    @Test
    fun configuredSymNumberRemapParticipatesInNumberRowProtection() {
        val state = ClicksPowerKeyboardState(
            numberRemaps = MutableList<ByteArray?>(9) { null }.also {
                it[6] = byteArrayOf(0x29, 0x00)
            }
        )
        val escape = event(KeyEvent.KEYCODE_ESCAPE, scanCode = 1)
        val resolution = resolver.resolve(
            KeyEvent.KEYCODE_ESCAPE,
            escape,
            ClicksPowerKeyboardLayout,
            state
        )
        val filter = AccidentalKeyPressFilter()
        val configuration = AccidentalKeyPressFilter.Configuration(
            numberRowPolicy = AccidentalKeyPressFilter.NumberRowPolicy(
                acceptance = AccidentalKeyPressFilter.NumberRowAcceptance.NEVER
            )
        )

        assertEquals(
            AccidentalKeyPressFilter.Reason.NUMBER_ROW_DISABLED,
            filter.shouldConsumeKeyDown(
                KeyEvent.KEYCODE_ESCAPE,
                escape,
                resolution,
                configuration
            )?.reason
        )
    }

    @Test
    fun gattRemapChangesAreUsedImmediately() {
        val withRemap = ClicksPowerKeyboardState(
            numberRemaps = MutableList<ByteArray?>(9) { null }.also {
                it[6] = byteArrayOf(0x29, 0x00)
            }
        )

        assertTrue(resolve(KeyEvent.KEYCODE_ESCAPE, 1, state = withRemap).candidates.isNotEmpty())
        assertTrue(resolve(KeyEvent.KEYCODE_ESCAPE, 1, state = ClicksPowerKeyboardState()).candidates.isEmpty())
    }

    @Test
    fun collidingFirmwareOutputKeepsEveryPossibleOrigin() {
        val result = resolve(
            KeyEvent.KEYCODE_3,
            scanCode = 4,
            metaState = KeyEvent.META_SHIFT_ON
        )

        assertEquals(
            setOf(ClicksPowerKeyboardLayout.DIGIT_3, ClicksPowerKeyboardLayout.W),
            result.candidates
        )
    }

    @Test
    fun unknownOutputFailsOpenWithEmptyCandidateSet() {
        val result = resolve(KeyEvent.KEYCODE_F12, scanCode = 88)

        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun everySupportedModifierIsExcludedFromKeyCompetition() {
        listOf(
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_SYM,
            KeyEvent.KEYCODE_FUNCTION
        ).forEach { keyCode ->
            assertTrue(resolve(keyCode, scanCode = 0).isModifier)
        }
    }

    private fun resolve(
        keyCode: Int,
        scanCode: Int,
        metaState: Int = 0,
        state: ClicksPowerKeyboardState = ClicksPowerKeyboardState()
    ): PhysicalKeyResolver.Resolution = resolver.resolve(
        keyCode = keyCode,
        event = event(keyCode, scanCode, metaState),
        profile = ClicksPowerKeyboardLayout,
        clicksState = state
    )

    private fun event(
        keyCode: Int,
        scanCode: Int,
        metaState: Int = 0,
        eventTime: Long = 1_000L
    ) = KeyEvent(
        1_000L,
        eventTime,
        KeyEvent.ACTION_DOWN,
        keyCode,
        0,
        metaState,
        7,
        scanCode,
        0,
        0x00000101
    )
}
