package it.palsoftware.pastiera

import android.content.Context
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClicksPowerKeyboardStateSnapshotTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun serializationPreservesDeviceDataButNeverConnectionTruth() {
        val state = ClicksPowerKeyboardState(
            connected = true,
            ready = true,
            stale = true,
            sessionValidated = true,
            mtu = 517,
            serialNumber = "CPK-42",
            batteryPercent = 74,
            chargingReservePercent = 20,
            featureFlags = 0x22,
            hostNames = listOf("Pixel", null, null, null, null, null, null, null, null),
            tabRemap = byteArrayOf(0x29, 0x00),
            numberRemaps = listOf(byteArrayOf(0x4b, 0x00)) + List(8) { null },
            error = "Disconnected"
        )

        val decoded = ClicksPowerKeyboardStateSnapshotCodec.decode(
            ClicksPowerKeyboardStateSnapshotCodec.encode(
                ClicksPowerKeyboardStateSnapshot("Power Keyboard-1", state, 1234L)
            )
        )!!

        assertEquals("Power Keyboard-1", decoded.deviceName)
        assertEquals("CPK-42", decoded.state.serialNumber)
        assertEquals(74, decoded.state.batteryPercent)
        assertEquals(20, decoded.state.chargingReservePercent)
        assertEquals("Pixel", decoded.state.hostNames.first())
        assertArrayEquals(byteArrayOf(0x29, 0x00), decoded.state.tabRemap)
        assertArrayEquals(byteArrayOf(0x4b, 0x00), decoded.state.numberRemaps.first())
        assertFalse(decoded.state.connected)
        assertFalse(decoded.state.ready)
        assertFalse(decoded.state.stale)
        assertFalse(decoded.state.sessionValidated)
        assertEquals(23, decoded.state.mtu)
        assertNull(decoded.state.error)
    }

    @Test
    fun persistedSnapshotRestoresAsStaleAfterDisconnectAndProcessRestart() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.saveClicksPowerKeyboardSnapshot(
            context,
            "Power Keyboard-1",
            ClicksPowerKeyboardState(
                connected = true,
                ready = true,
                serialNumber = "CPK-42",
                firmwareVersion = "1.2.3",
                batteryPercent = 66,
                chargingReservePercent = 15,
                wirelessChargingEnabled = true
            )
        )

        val restored = SettingsManager.getMostRecentClicksPowerKeyboardSnapshot(context)
        assertNotNull(restored)
        val offline = ClicksPowerKeyboardStateSnapshotCodec.forOfflineDisplay(restored!!.state)

        assertEquals("Power Keyboard-1", restored.deviceName)
        assertEquals("CPK-42", restored.state.serialNumber)
        assertEquals(66, offline.batteryPercent)
        assertEquals(15, offline.chargingReservePercent)
        assertEquals(true, offline.wirelessChargingEnabled)
        assertTrue(offline.wirelessChargingEnabledStale)
        assertTrue(offline.stale)
        assertFalse(offline.connected)
        assertFalse(offline.ready)
        assertNull(offline.error)
    }

    @Test
    fun batteryReserveDisplayDistinguishesLiveFromLastKnownValues() {
        val live = ClicksPowerKeyboardState(
            batteryPercent = 83,
            chargingReservePercent = 20
        ).batteryReserveDisplay()!!
        val offline = ClicksPowerKeyboardStateSnapshotCodec.forOfflineDisplay(
            ClicksPowerKeyboardState(batteryPercent = 83, chargingReservePercent = 20)
        ).batteryReserveDisplay()!!

        assertEquals(20, live.reservePercent)
        assertEquals(83, live.keyboardBatteryPercent)
        assertEquals(ClicksBatteryReserveSource.Live, live.source)
        assertEquals(ClicksBatteryReserveSource.LastKnown, offline.source)
    }

    @Test
    fun reconnectKeepsCachedSocAndReserveLastKnownUntilTheirOwnGattReads() {
        val offline = ClicksPowerKeyboardStateSnapshotCodec.forOfflineDisplay(
            ClicksPowerKeyboardState(
                batteryPercent = 83,
                chargingReservePercent = 20,
                wirelessChargingEnabled = true
            )
        )

        val afterModelRead = ClicksPowerKeyboardStateSnapshotCodec.forGattReconnect(offline)
            .copy(model = "Power Keyboard")
        assertTrue(afterModelRead.stale)
        assertFalse(afterModelRead.sessionValidated)
        assertTrue(afterModelRead.batteryPercentStale)
        assertTrue(afterModelRead.chargingReservePercentStale)
        assertTrue(afterModelRead.wirelessChargingEnabledStale)
        assertEquals(ClicksBatteryReserveSource.LastKnown, afterModelRead.batteryReserveDisplay()!!.source)

        val afterBatteryRead = afterModelRead.copy(batteryPercent = 84, batteryPercentStale = false)
        assertEquals(ClicksBatteryReserveSource.LastKnown, afterBatteryRead.batteryReserveDisplay()!!.source)

        val afterReserveRead = afterBatteryRead.copy(chargingReservePercent = 25, chargingReservePercentStale = false)
        assertEquals(ClicksBatteryReserveSource.LastKnown, afterReserveRead.batteryReserveDisplay()!!.source)

        val afterValidatedSession = afterReserveRead.copy(stale = false, sessionValidated = true)
        assertEquals(ClicksBatteryReserveSource.Live, afterValidatedSession.batteryReserveDisplay()!!.source)
    }
}
