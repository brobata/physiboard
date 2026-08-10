package it.palsoftware.pastiera.inputmethod

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccidentalKeyPressFilterTest {
    private lateinit var filter: AccidentalKeyPressFilter

    @Before
    fun setUp() {
        filter = AccidentalKeyPressFilter()
    }

    @Test
    fun adjacentMode_allowsDistantOverlappingKeys() {
        val configuration = configuration(AccidentalKeyPressFilter.OverlapRule.ADJACENT)

        assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration)
        assertAccepted(KeyEvent.KEYCODE_N, ClicksPowerKeyboardLayout.N, configuration, 1_010L)
    }

    @Test
    fun adjacentMode_suppressesEveryImmediateNeighbourOfE() {
        val neighbours = listOf(
            KeyEvent.KEYCODE_W to ClicksPowerKeyboardLayout.W,
            KeyEvent.KEYCODE_R to ClicksPowerKeyboardLayout.R,
            KeyEvent.KEYCODE_S to ClicksPowerKeyboardLayout.S,
            KeyEvent.KEYCODE_D to ClicksPowerKeyboardLayout.D,
            KeyEvent.KEYCODE_3 to ClicksPowerKeyboardLayout.DIGIT_3,
            KeyEvent.KEYCODE_4 to ClicksPowerKeyboardLayout.DIGIT_4
        )

        neighbours.forEach { (keyCode, physicalKey) ->
            filter.reset()
            val configuration = configuration(AccidentalKeyPressFilter.OverlapRule.ADJACENT)
            assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration)
            val suppressed = consumeDown(keyCode, physicalKey, configuration, 1_010L)
            assertEquals(AccidentalKeyPressFilter.Reason.ADJACENT_KEY, suppressed?.reason)
            assertTrue(
                filter.onKeyUp(keyCode, up(keyCode, 1_020L)) is
                    AccidentalKeyPressFilter.KeyUpResult.Suppressed
            )
        }
    }

    @Test
    fun adjacentMode_allowsFarNumberKey() {
        val configuration = configuration(AccidentalKeyPressFilter.OverlapRule.ADJACENT)

        assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration)
        assertAccepted(KeyEvent.KEYCODE_9, ClicksPowerKeyboardLayout.DIGIT_9, configuration, 1_010L)
    }

    @Test
    fun clicksAdjacencyGraph_isSymmetricAndMatchesDocumentedExamples() {
        val pairs = listOf(
            ClicksPowerKeyboardLayout.E to ClicksPowerKeyboardLayout.W,
            ClicksPowerKeyboardLayout.E to ClicksPowerKeyboardLayout.D,
            ClicksPowerKeyboardLayout.E to ClicksPowerKeyboardLayout.DIGIT_4,
            ClicksPowerKeyboardLayout.U to ClicksPowerKeyboardLayout.DIGIT_7,
            ClicksPowerKeyboardLayout.DIGIT_3 to ClicksPowerKeyboardLayout.W
        )

        pairs.forEach { (first, second) ->
            assertTrue(ClicksPowerKeyboardLayout.isAdjacent(first, second))
            assertTrue(ClicksPowerKeyboardLayout.isAdjacent(second, first))
        }
        assertEquals(false, ClicksPowerKeyboardLayout.isAdjacent(ClicksPowerKeyboardLayout.E, ClicksPowerKeyboardLayout.N))
    }

    @Test
    fun strictMode_suppressesDistantSecondKey() {
        val configuration = configuration(AccidentalKeyPressFilter.OverlapRule.ALL)

        assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration)
        assertEquals(
            AccidentalKeyPressFilter.Reason.OVERLAPPING_KEY,
            consumeDown(KeyEvent.KEYCODE_N, ClicksPowerKeyboardLayout.N, configuration, 1_010L)?.reason
        )
    }

    @Test
    fun adjacencyWithoutLayoutKnowledge_failsOpenButStrictModeStillWorks() {
        val unknown = resolution(emptySet(), profile = null)
        val adjacent = configuration(AccidentalKeyPressFilter.OverlapRule.ADJACENT)

        assertNull(filter.shouldConsumeKeyDown(KeyEvent.KEYCODE_E, down(KeyEvent.KEYCODE_E), unknown, adjacent))
        assertNull(filter.shouldConsumeKeyDown(KeyEvent.KEYCODE_W, down(KeyEvent.KEYCODE_W, 1_010L), unknown, adjacent))

        filter.reset()
        val strict = configuration(AccidentalKeyPressFilter.OverlapRule.ALL)
        assertNull(filter.shouldConsumeKeyDown(KeyEvent.KEYCODE_E, down(KeyEvent.KEYCODE_E), unknown, strict))
        assertNotNull(filter.shouldConsumeKeyDown(KeyEvent.KEYCODE_N, down(KeyEvent.KEYCODE_N, 1_010L), unknown, strict))
    }

    @Test
    fun numberRowAdjacentRule_worksWhenGeneralClicksRuleIsOff() {
        val configuration = configuration(
            numberRowPolicy = AccidentalKeyPressFilter.NumberRowPolicy(
                overlapMinimum = AccidentalKeyPressFilter.OverlapRule.ADJACENT
            )
        )

        assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration)
        assertEquals(
            AccidentalKeyPressFilter.Reason.ADJACENT_KEY,
            consumeDown(KeyEvent.KEYCODE_3, ClicksPowerKeyboardLayout.DIGIT_3, configuration, 1_010L)?.reason
        )

        filter.reset()
        assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration)
        assertAccepted(KeyEvent.KEYCODE_9, ClicksPowerKeyboardLayout.DIGIT_9, configuration, 1_010L)
    }

    @Test
    fun numberRowAnyHeldRule_suppressesFarNumberButNotLetter() {
        val configuration = configuration(
            numberRowPolicy = AccidentalKeyPressFilter.NumberRowPolicy(
                overlapMinimum = AccidentalKeyPressFilter.OverlapRule.ALL
            )
        )

        assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration)
        assertEquals(
            AccidentalKeyPressFilter.Reason.OVERLAPPING_KEY,
            consumeDown(KeyEvent.KEYCODE_9, ClicksPowerKeyboardLayout.DIGIT_9, configuration, 1_010L)?.reason
        )
        assertAccepted(KeyEvent.KEYCODE_N, ClicksPowerKeyboardLayout.N, configuration, 1_020L)
    }

    @Test
    fun ignoreAllNumberRowRule_suppressesDownAndMatchingUp() {
        val configuration = configuration(
            numberRowPolicy = AccidentalKeyPressFilter.NumberRowPolicy(
                acceptance = AccidentalKeyPressFilter.NumberRowAcceptance.NEVER
            )
        )

        assertEquals(
            AccidentalKeyPressFilter.Reason.NUMBER_ROW_DISABLED,
            consumeDown(KeyEvent.KEYCODE_5, ClicksPowerKeyboardLayout.DIGIT_5, configuration)?.reason
        )
        assertTrue(
            filter.onKeyUp(KeyEvent.KEYCODE_5, up(KeyEvent.KEYCODE_5, 1_010L)) is
                AccidentalKeyPressFilter.KeyUpResult.Suppressed
        )
    }

    @Test
    fun numberRowLongPress_suppressesShortPressAndReplaysExactlyOneTap() {
        val configuration = configuration(
            numberRowPolicy = AccidentalKeyPressFilter.NumberRowPolicy(
                acceptance = AccidentalKeyPressFilter.NumberRowAcceptance.LONG_PRESS
            ),
            longPressThresholdMs = 300L
        )

        assertNotNull(consumeDown(KeyEvent.KEYCODE_5, ClicksPowerKeyboardLayout.DIGIT_5, configuration))
        assertNotNull(
            filter.shouldConsumeKeyDown(
                KeyEvent.KEYCODE_5,
                down(KeyEvent.KEYCODE_5, 1_200L, repeatCount = 1),
                resolution(ClicksPowerKeyboardLayout.DIGIT_5),
                configuration
            )
        )
        val replay = filter.onKeyUp(KeyEvent.KEYCODE_5, up(KeyEvent.KEYCODE_5, 1_300L))
        assertTrue(replay is AccidentalKeyPressFilter.KeyUpResult.ReplayTap)

        filter.reset()
        assertNotNull(consumeDown(KeyEvent.KEYCODE_5, ClicksPowerKeyboardLayout.DIGIT_5, configuration))
        val shortPress = filter.onKeyUp(KeyEvent.KEYCODE_5, up(KeyEvent.KEYCODE_5, 1_299L))
        assertEquals(
            AccidentalKeyPressFilter.Reason.NUMBER_ROW_SHORT_PRESS,
            (shortPress as AccidentalKeyPressFilter.KeyUpResult.Suppressed).event.reason
        )
    }

    @Test
    fun disabledNumberRowRepeat_suppressesOnlyRepeatedNumberDownEvents() {
        val configuration = configuration(numberRowRepeatEnabled = false)

        assertAccepted(KeyEvent.KEYCODE_3, ClicksPowerKeyboardLayout.DIGIT_3, configuration)
        val repeatedNumber = filter.shouldConsumeKeyDown(
            KeyEvent.KEYCODE_3,
            down(KeyEvent.KEYCODE_3, 1_100L, repeatCount = 1),
            resolution(ClicksPowerKeyboardLayout.DIGIT_3),
            configuration
        )
        assertEquals(
            AccidentalKeyPressFilter.Reason.NUMBER_ROW_REPEAT_DISABLED,
            repeatedNumber?.reason
        )
        assertNull(filter.onKeyUp(KeyEvent.KEYCODE_3, up(KeyEvent.KEYCODE_3, 1_110L)))

        assertAccepted(KeyEvent.KEYCODE_U, ClicksPowerKeyboardLayout.U, configuration, 1_200L)
        assertNull(
            filter.shouldConsumeKeyDown(
                KeyEvent.KEYCODE_U,
                down(KeyEvent.KEYCODE_U, 1_300L, repeatCount = 1),
                resolution(ClicksPowerKeyboardLayout.U),
                configuration
            )
        )
    }

    @Test
    fun enabledNumberRowRepeat_preservesRepeatedNumberDownEvents() {
        val configuration = configuration(numberRowRepeatEnabled = true)

        assertAccepted(KeyEvent.KEYCODE_3, ClicksPowerKeyboardLayout.DIGIT_3, configuration)
        assertNull(
            filter.shouldConsumeKeyDown(
                KeyEvent.KEYCODE_3,
                down(KeyEvent.KEYCODE_3, 1_100L, repeatCount = 1),
                resolution(ClicksPowerKeyboardLayout.DIGIT_3),
                configuration
            )
        )
    }

    @Test
    fun disabledNumberRowRepeat_remembersTheHeldKeyWhenARepeatCannotBeResolvedAgain() {
        val configuration = configuration(numberRowRepeatEnabled = false)

        assertAccepted(KeyEvent.KEYCODE_3, ClicksPowerKeyboardLayout.DIGIT_3, configuration)
        val repeatedNumber = filter.shouldConsumeKeyDown(
            KeyEvent.KEYCODE_3,
            down(KeyEvent.KEYCODE_3, 1_100L, repeatCount = 1),
            resolution(emptySet()),
            configuration
        )

        assertEquals(
            AccidentalKeyPressFilter.Reason.NUMBER_ROW_REPEAT_DISABLED,
            repeatedNumber?.reason
        )
    }

    @Test
    fun modifiersDoNotCompeteAndCtrlUPlusSevenSuppressesOnlySeven() {
        val configuration = configuration(AccidentalKeyPressFilter.OverlapRule.ADJACENT)

        assertNull(
            filter.shouldConsumeKeyDown(
                KeyEvent.KEYCODE_CTRL_LEFT,
                down(KeyEvent.KEYCODE_CTRL_LEFT),
                resolution(ClicksPowerKeyboardLayout.CTRL, isModifier = true),
                configuration
            )
        )
        assertAccepted(KeyEvent.KEYCODE_U, ClicksPowerKeyboardLayout.U, configuration, 1_010L)
        assertEquals(
            AccidentalKeyPressFilter.Reason.ADJACENT_KEY,
            consumeDown(KeyEvent.KEYCODE_7, ClicksPowerKeyboardLayout.DIGIT_7, configuration, 1_020L)?.reason
        )
        assertTrue(
            filter.onKeyUp(KeyEvent.KEYCODE_7, up(KeyEvent.KEYCODE_7, 1_030L)) is
                AccidentalKeyPressFilter.KeyUpResult.Suppressed
        )
        assertNull(filter.onKeyUp(KeyEvent.KEYCODE_U, up(KeyEvent.KEYCODE_U, 1_040L)))
        assertAccepted(KeyEvent.KEYCODE_7, ClicksPowerKeyboardLayout.DIGIT_7, configuration, 1_050L)
    }

    @Test
    fun ambiguousAndUnknownPhysicalOrigins_failOpen() {
        val configuration = configuration(AccidentalKeyPressFilter.OverlapRule.ADJACENT)
        assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration)

        val ambiguous = resolution(
            setOf(ClicksPowerKeyboardLayout.W, ClicksPowerKeyboardLayout.N)
        )
        assertNull(
            filter.shouldConsumeKeyDown(
                KeyEvent.KEYCODE_W,
                down(KeyEvent.KEYCODE_W, 1_010L),
                ambiguous,
                configuration
            )
        )
        assertNull(
            filter.shouldConsumeKeyDown(
                KeyEvent.KEYCODE_UNKNOWN,
                down(KeyEvent.KEYCODE_UNKNOWN, 1_020L),
                resolution(emptySet()),
                configuration
            )
        )
    }

    @Test
    fun stateIsIsolatedPerDeviceAndResetOnDisconnect() {
        val configuration = configuration(AccidentalKeyPressFilter.OverlapRule.ALL)
        assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration, deviceId = 7)
        assertAccepted(KeyEvent.KEYCODE_N, ClicksPowerKeyboardLayout.N, configuration, 1_010L, deviceId = 8)
        assertNotNull(consumeDown(KeyEvent.KEYCODE_N, ClicksPowerKeyboardLayout.N, configuration, 1_020L, 7))

        filter.resetDevice(7)
        assertNull(filter.onKeyUp(KeyEvent.KEYCODE_N, up(KeyEvent.KEYCODE_N, 1_030L, 7)))
        assertAccepted(KeyEvent.KEYCODE_N, ClicksPowerKeyboardLayout.N, configuration, 1_040L, deviceId = 7)
    }

    @Test
    fun canceledKeyUpClearsHeldState() {
        val configuration = configuration(AccidentalKeyPressFilter.OverlapRule.ADJACENT)
        assertAccepted(KeyEvent.KEYCODE_E, ClicksPowerKeyboardLayout.E, configuration)
        assertNull(
            filter.onKeyUp(
                KeyEvent.KEYCODE_E,
                up(KeyEvent.KEYCODE_E, 1_010L, flags = KeyEvent.FLAG_CANCELED)
            )
        )
        assertAccepted(KeyEvent.KEYCODE_W, ClicksPowerKeyboardLayout.W, configuration, 1_020L)
    }

    private fun assertAccepted(
        keyCode: Int,
        physicalKey: PhysicalKeyId,
        configuration: AccidentalKeyPressFilter.Configuration,
        eventTime: Long = 1_000L,
        deviceId: Int = 7
    ) {
        assertNull(consumeDown(keyCode, physicalKey, configuration, eventTime, deviceId))
    }

    private fun consumeDown(
        keyCode: Int,
        physicalKey: PhysicalKeyId,
        configuration: AccidentalKeyPressFilter.Configuration,
        eventTime: Long = 1_000L,
        deviceId: Int = 7
    ) = filter.shouldConsumeKeyDown(
        keyCode,
        down(keyCode, eventTime, deviceId),
        resolution(physicalKey),
        configuration
    )

    private fun configuration(
        overlapRule: AccidentalKeyPressFilter.OverlapRule = AccidentalKeyPressFilter.OverlapRule.NONE,
        numberRowPolicy: AccidentalKeyPressFilter.NumberRowPolicy = AccidentalKeyPressFilter.NumberRowPolicy(),
        longPressThresholdMs: Long = 500L,
        numberRowRepeatEnabled: Boolean = true
    ) = AccidentalKeyPressFilter.Configuration(
        overlapRule,
        numberRowPolicy,
        longPressThresholdMs,
        numberRowRepeatEnabled
    )

    private fun resolution(
        physicalKey: PhysicalKeyId,
        isModifier: Boolean = false
    ) = resolution(setOf(physicalKey), ClicksPowerKeyboardLayout, isModifier)

    private fun resolution(
        candidates: Set<PhysicalKeyId>,
        profile: PhysicalKeyAdjacencyProfile? = ClicksPowerKeyboardLayout,
        isModifier: Boolean = false
    ) = PhysicalKeyResolver.Resolution(candidates, profile, isModifier)

    private fun down(
        keyCode: Int,
        eventTime: Long = 1_000L,
        deviceId: Int = 7,
        repeatCount: Int = 0
    ) = keyEvent(KeyEvent.ACTION_DOWN, keyCode, eventTime, deviceId, repeatCount)

    private fun up(
        keyCode: Int,
        eventTime: Long,
        deviceId: Int = 7,
        flags: Int = 0
    ) = keyEvent(KeyEvent.ACTION_UP, keyCode, eventTime, deviceId, flags = flags)

    private fun keyEvent(
        action: Int,
        keyCode: Int,
        eventTime: Long,
        deviceId: Int,
        repeatCount: Int = 0,
        flags: Int = 0
    ): KeyEvent = KeyEvent(
        1_000L,
        eventTime,
        action,
        keyCode,
        repeatCount,
        0,
        deviceId,
        keyCode + 100,
        flags,
        0x00000101
    )
}
