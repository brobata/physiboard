package brobata.physiboard.device.privileged.broker

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * spec: broker-privileged-toolbox.md SS4.1 step 3, the pairing watcher re-arm gap: when the
 * process dies (reinstall, low memory) with no key stored, nothing re-arms the watcher until the
 * setup card is shown again, so a pairing dialog opened from Android's own Settings is never
 * discovered. [BrokerRules.shouldRearmPairingWatcherAtProcessStart] is the pure decision.
 */
class BrokerRulesTest {

    @Test
    fun `re-arms when the watcher was armed before the process died and no key is stored`() {
        assertTrue(BrokerRules.shouldRearmPairingWatcherAtProcessStart(wasArmed = true, hasStoredKey = false))
    }

    @Test
    fun `does not re-arm once a key is stored, armed or not`() {
        assertFalse(BrokerRules.shouldRearmPairingWatcherAtProcessStart(wasArmed = true, hasStoredKey = true))
        assertFalse(BrokerRules.shouldRearmPairingWatcherAtProcessStart(wasArmed = false, hasStoredKey = true))
    }

    @Test
    fun `does not re-arm when no card ever armed it this run`() {
        assertFalse(BrokerRules.shouldRearmPairingWatcherAtProcessStart(wasArmed = false, hasStoredKey = false))
    }
}
