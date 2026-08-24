package it.palsoftware.pastiera.inputmethod

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules deciding whether the engine or the keyboard times the end-of-speech pause.
 * Getting these wrong strands dictation: too eager and an engine that ignores segmented
 * sessions never stops listening; too shy and the recognizer keeps cutting off after a second.
 */
class SpeechRecognitionSegmentedSessionTest {

    private fun decide(
        sdkInt: Int = Build.VERSION_CODES.TIRAMISU,
        userEnabled: Boolean = true,
        engineRefusedSegments: Boolean = false,
        pauseMs: Int = 2500
    ) = SpeechRecognitionManager.shouldUseSegmentedSession(
        sdkInt = sdkInt,
        userEnabled = userEnabled,
        engineRefusedSegments = engineRefusedSegments,
        pauseMs = pauseMs
    )

    @Test
    fun `lets the engine time the pause on android 13 and later`() {
        assertTrue(decide(sdkInt = Build.VERSION_CODES.TIRAMISU))
        assertTrue(decide(sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
    }

    @Test
    fun `falls back to the restart loop before android 13`() {
        assertFalse(decide(sdkInt = Build.VERSION_CODES.S_V2))
        assertFalse(decide(sdkInt = Build.VERSION_CODES.Q))
    }

    @Test
    fun `honours the user turning it off`() {
        assertFalse(decide(userEnabled = false))
    }

    @Test
    fun `stops asking once an engine has refused`() {
        assertFalse(decide(engineRefusedSegments = true))
    }

    @Test
    fun `needs a configured pause to end the session on`() {
        assertFalse(decide(pauseMs = 0))
        assertTrue(decide(pauseMs = 500))
    }
}
