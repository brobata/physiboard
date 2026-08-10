package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClicksPowerKeyboardGattClientTest {
    @Test
    fun operationSubmittedAfterTerminalConnectionFailureCompletesAsFailure() {
        val client = ClicksPowerKeyboardGattClient(
            context = RuntimeEnvironment.getApplication(),
            deviceName = "Not bonded",
            onStateChanged = {}
        )
        var completion: Boolean? = null

        client.setSpecialKeyRemap(
            ClicksPowerKeyboardProtocol.COMMAND_TAB_REMAP,
            ClicksPowerKeyboardProtocol.nativeRemapOutput()
        ) { completion = it }

        assertEquals(false, completion)
    }
}
