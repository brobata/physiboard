package it.palsoftware.pastiera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClicksButtonBindingSyncPolicyTest {
    @Test
    fun nativeOutputIsConfirmedByDisabledFirmwareRemap() {
        val state = writableState(tabRemap = null)

        assertTrue(
            ClicksButtonBindingSyncPolicy.isConfirmed(
                state,
                ClicksButtonBindingTarget.RED,
                ClicksPowerKeyboardProtocol.nativeRemapOutput()
            )
        )
    }

    @Test
    fun desiredOutputMustMatchTheTargetRemap() {
        val dictation = ClicksPowerKeyboardProtocol.dictationRemapOutput()
        val state = writableState(geminiRemap = dictation)

        assertTrue(
            ClicksButtonBindingSyncPolicy.isConfirmed(
                state,
                ClicksButtonBindingTarget.MICROPHONE,
                dictation
            )
        )
        assertFalse(
            ClicksButtonBindingSyncPolicy.isConfirmed(
                state,
                ClicksButtonBindingTarget.KEYBOARD,
                dictation
            )
        )
    }

    @Test
    fun staleOrUnvalidatedSessionsCannotWritePendingBindings() {
        assertFalse(ClicksButtonBindingSyncPolicy.canWrite(writableState(stale = true)))
        assertFalse(ClicksButtonBindingSyncPolicy.canWrite(writableState(sessionValidated = false)))
        assertTrue(ClicksButtonBindingSyncPolicy.canWrite(writableState()))
    }

    @Test
    fun supersededWriteKeepsLatestRequestPending() {
        val first = ClicksPowerKeyboardProtocol.leftAltRemapOutput()
        val latest = ClicksPowerKeyboardProtocol.languageSwitchRemapOutput()

        assertEquals(
            ClicksButtonBindingCompletion.KEEP_PENDING,
            ClicksButtonBindingCompletionPolicy.resolve(
                attemptedOutput = first,
                latestDesiredOutput = latest,
                latestDesiredConfirmed = false
            )
        )
    }

    @Test
    fun latestRequestCompletesOnlyWhenItsOwnStateIsKnown() {
        val latest = ClicksPowerKeyboardProtocol.languageSwitchRemapOutput()

        assertEquals(
            ClicksButtonBindingCompletion.SUCCESS,
            ClicksButtonBindingCompletionPolicy.resolve(
                attemptedOutput = latest,
                latestDesiredOutput = latest,
                latestDesiredConfirmed = true
            )
        )
        assertEquals(
            ClicksButtonBindingCompletion.FAILURE,
            ClicksButtonBindingCompletionPolicy.resolve(
                attemptedOutput = latest,
                latestDesiredOutput = latest,
                latestDesiredConfirmed = false
            )
        )
    }

    private fun writableState(
        stale: Boolean = false,
        sessionValidated: Boolean = true,
        tabRemap: ByteArray? = null,
        geminiRemap: ByteArray? = null,
        altRemap: ByteArray? = null
    ) = ClicksPowerKeyboardState(
        ready = true,
        stale = stale,
        sessionValidated = sessionValidated,
        firmwareVersion = "1.0.9",
        specialKeyEnableFlags = 0,
        tabRemap = tabRemap,
        geminiRemap = geminiRemap,
        altRemap = altRemap
    )
}
