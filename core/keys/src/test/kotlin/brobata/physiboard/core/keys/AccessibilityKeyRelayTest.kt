package brobata.physiboard.core.keys

import brobata.physiboard.core.keys.AccessibilityKeyRelay.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: keys-and-modifiers.md SS15.1, which keys the accessibility service hands the keyboard. */
class AccessibilityKeyRelayTest {

    private fun verdict(featureOn: Boolean = true, keyboardRunning: Boolean = true, editable: Boolean = false, fn: Boolean = false) =
        AccessibilityKeyRelay.verdict(featureOn, keyboardRunning, editable, fn)

    @Test
    fun `with no text box a key goes to the keyboard's no-field path`() {
        assertEquals(Verdict.RELAY, verdict())
    }

    @Test
    fun `a text box means the keyboard is sent the key itself, so the service stays out`() {
        assertEquals(Verdict.PASS_THROUGH, verdict(editable = true))
    }

    @Test
    fun `the switch off, or another keyboard in use, passes everything`() {
        assertEquals(Verdict.PASS_THROUGH, verdict(featureOn = false))
        assertEquals(Verdict.PASS_THROUGH, verdict(keyboardRunning = false))
    }

    @Test
    fun `Fn's own repeats always pass, since a chord carries the Ctrl bit on the other key`() {
        assertEquals(Verdict.PASS_THROUGH, verdict(fn = true))
    }

    private fun id(t: Long, action: Int = 0, key: Int = 62) = KeyEventIdentity(downTimeMs = t, eventTimeMs = t, action = action, keyCode = key, scanCode = 57, repeatCount = 0)

    @Test
    fun `an event the service handed over is let through once by the keyboard`() {
        val relayed = RelayedKeys()
        relayed.remember(id(100))
        assertTrue(relayed.wasRelayed(id(100)))
        assertFalse(relayed.wasRelayed(id(100)), "a second delivery of the same values is a new event")
    }

    @Test
    fun `an event the service never saw runs as usual`() {
        val relayed = RelayedKeys()
        relayed.remember(id(100, action = 0))
        assertFalse(relayed.wasRelayed(id(100, action = 1)), "the release is its own event")
        assertFalse(relayed.wasRelayed(id(200)))
    }

    @Test
    fun `only the last few are kept`() {
        val relayed = RelayedKeys(capacity = 2)
        relayed.remember(id(1))
        relayed.remember(id(2))
        relayed.remember(id(3))
        assertFalse(relayed.wasRelayed(id(1)))
        assertTrue(relayed.wasRelayed(id(2)))
        assertTrue(relayed.wasRelayed(id(3)))
    }

    @Test
    fun `Alt, Shift and Ctrl always go on to the app, Sym follows the keyboard`() {
        for (m in listOf(ModifierKey.ALT, ModifierKey.SHIFT, ModifierKey.CTRL)) {
            assertFalse(AccessibilityKeyRelay.consumes(KeyId.Modifier(m), KeyEdge.DOWN, pathConsumed = true, downWasConsumed = false))
        }
        assertTrue(AccessibilityKeyRelay.consumes(KeyId.Modifier(ModifierKey.SYM), KeyEdge.DOWN, pathConsumed = true, downWasConsumed = false))
    }

    @Test
    fun `a release is consumed exactly when its press was`() {
        val e = KeyId.Letter('E')
        assertFalse(AccessibilityKeyRelay.consumes(e, KeyEdge.UP, pathConsumed = true, downWasConsumed = false), "nav mode's up rule must not eat the release of a press the app saw")
        assertTrue(AccessibilityKeyRelay.consumes(e, KeyEdge.UP, pathConsumed = false, downWasConsumed = true))
        val downs = ConsumedRelayDowns()
        downs.onDown(62, consumed = true)
        downs.onDown(33, consumed = false)
        assertTrue(downs.takeOnUp(62))
        assertFalse(downs.takeOnUp(62), "used once")
        assertFalse(downs.takeOnUp(33))
    }
}
