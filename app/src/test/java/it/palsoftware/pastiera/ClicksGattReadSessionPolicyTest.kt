package it.palsoftware.pastiera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClicksGattReadSessionPolicyTest {
    @Test
    fun cachedStateIsKeptOnlyForTheSameFreshIdentity() {
        assertFalse(ClicksGattReadSessionPolicy.shouldDiscardCachedState(true, "PK-42", "PK-42"))
        assertTrue(ClicksGattReadSessionPolicy.shouldDiscardCachedState(true, "PK-42", "PK-99"))
        assertTrue(ClicksGattReadSessionPolicy.shouldDiscardCachedState(true, null, "PK-42"))
        assertFalse(ClicksGattReadSessionPolicy.shouldDiscardCachedState(false, null, "PK-42"))
    }

    @Test
    fun sessionNeedsACompleteSuccessfulReadAndFreshSerial() {
        assertTrue(ClicksGattReadSessionPolicy.isSessionValidated(false, "PK-42"))
        assertFalse(ClicksGattReadSessionPolicy.isSessionValidated(true, "PK-42"))
        assertFalse(ClicksGattReadSessionPolicy.isSessionValidated(false, null))
        assertFalse(ClicksGattReadSessionPolicy.isSessionValidated(false, "  "))
    }

    @Test
    fun timedOutSessionRejectsLateResponsesEvenFromTheExpectedGroup() {
        val gate = ClicksGattResponseSessionGate()

        assertTrue(gate.acceptsResponse(expectedGroup = 0x25, receivedGroup = 0x25))

        gate.onOperationTimeout()

        assertTrue(gate.timedOut)
        assertFalse(gate.acceptsResponse(expectedGroup = 0x25, receivedGroup = 0x25))
        assertFalse(gate.acceptsResponse(expectedGroup = 0x25, receivedGroup = 0x27))
    }
}
