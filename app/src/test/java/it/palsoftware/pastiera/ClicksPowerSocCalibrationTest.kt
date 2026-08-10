package it.palsoftware.pastiera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClicksPowerSocCalibrationTest {
    @Test
    fun calibrationRequiresFreshValuesFromTheCurrentGattSession() {
        val fresh = ClicksPowerKeyboardState(
            connected = true,
            ready = true,
            sessionValidated = true,
            serialNumber = "serial-a",
            batteryPercent = 80,
            wirelessChargingEnabled = true
        )

        assertTrue(fresh.hasFreshSocCalibrationInputs())
        assertFalse(fresh.copy(stale = true).hasFreshSocCalibrationInputs())
        assertFalse(fresh.copy(batteryPercentStale = true).hasFreshSocCalibrationInputs())
        assertFalse(fresh.copy(wirelessChargingEnabledStale = true).hasFreshSocCalibrationInputs())
        assertFalse(fresh.copy(connected = false).hasFreshSocCalibrationInputs())
        assertFalse(fresh.copy(ready = false).hasFreshSocCalibrationInputs())
        assertFalse(fresh.copy(sessionValidated = false).hasFreshSocCalibrationInputs())
    }

    @Test
    fun acceptsOnlyMonotonePhoneChargingFromKeyboard() {
        val tracker = tracker()

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.BASELINE,
            tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.ACCEPTED,
            tracker.observe(snapshot(time = 2_000, phone = 52, keyboard = 76))
        )

        assertNull(tracker.estimateOrNull())
    }

    @Test
    fun waitsForRoundedSocToMoveWithoutDiscardingTheLastReliableBaseline() {
        val tracker = tracker()
        tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80))

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.WAITING_FOR_MONOTONE_DELTA,
            tracker.observe(snapshot(time = 2_000, phone = 50, keyboard = 79))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.ACCEPTED,
            tracker.observe(snapshot(time = 3_000, phone = 51, keyboard = 78))
        )
    }

    @Test
    fun rejectsNonMonotoneTransferAndUsesTheNextSnapshotAsNewBaseline() {
        val tracker = tracker()
        tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80))

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.REJECTED_NON_MONOTONIC,
            tracker.observe(snapshot(time = 2_000, phone = 49, keyboard = 78))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.ACCEPTED,
            tracker.observe(snapshot(time = 3_000, phone = 50, keyboard = 76))
        )
    }

    @Test
    fun ignoresDisabledWirelessChargingInsteadOfInventingAConversionFromDischarge() {
        val tracker = tracker()

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.IGNORED_NO_TRANSFER,
            tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80, charging = false))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.IGNORED_NO_TRANSFER,
            tracker.observe(snapshot(time = 2_000, phone = 48, keyboard = 78, charging = false))
        )

        assertNull(tracker.estimateOrNull())
    }

    @Test
    fun separatesPhasesWhenWirelessChargingDirectionChanges() {
        val tracker = tracker()
        tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80, charging = false))

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.SEGMENT_RESET_PHASE_CHANGE,
            tracker.observe(snapshot(time = 2_000, phone = 50, keyboard = 80, charging = true))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.ACCEPTED,
            tracker.observe(snapshot(time = 3_000, phone = 51, keyboard = 78, charging = true))
        )
    }

    @Test
    fun wiredPhonePowerResetsTheSegmentAndDoesNotUseThatReadingAsABaseline() {
        val tracker = tracker()
        tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80))

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.SEGMENT_RESET_EXTERNAL_PHONE_POWER,
            tracker.observe(snapshot(time = 2_000, phone = 52, keyboard = 78, plugged = ClicksPhonePluggedType.AC))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.BASELINE,
            tracker.observe(snapshot(time = 3_000, phone = 52, keyboard = 78))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.ACCEPTED,
            tracker.observe(snapshot(time = 4_000, phone = 53, keyboard = 76))
        )
    }

    @Test
    fun acUsbAndDockAlwaysResetTheSegment() {
        listOf(
            ClicksPhonePluggedType.AC,
            ClicksPhonePluggedType.USB,
            ClicksPhonePluggedType.DOCK
        ).forEach { pluggedType ->
            val tracker = tracker()
            tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80))

            assertEquals(
                "$pluggedType must reset calibration",
                ClicksPowerSocCalibrationSampleDisposition.SEGMENT_RESET_EXTERNAL_PHONE_POWER,
                tracker.observe(snapshot(time = 2_000, phone = 51, keyboard = 78, plugged = pluggedType))
            )
        }
    }

    @Test
    fun wirelessPluggedTypeIsAllowedForConfirmedClicksTransfer() {
        val tracker = tracker()
        tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80, plugged = ClicksPhonePluggedType.WIRELESS))

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.ACCEPTED,
            tracker.observe(snapshot(time = 2_000, phone = 51, keyboard = 78, plugged = ClicksPhonePluggedType.WIRELESS))
        )
    }

    @Test
    fun wirelessPluggedTypeResetsWhenClicksTransferIsDisabled() {
        val tracker = tracker()

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.SEGMENT_RESET_EXTERNAL_PHONE_POWER,
            tracker.observe(
                snapshot(
                    time = 1_000,
                    phone = 50,
                    keyboard = 80,
                    charging = false,
                    plugged = ClicksPhonePluggedType.WIRELESS
                )
            )
        )
    }

    @Test
    fun timeGapAndOutOfOrderSnapshotsResetTheSegment() {
        val tracker = tracker()
        tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80))

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.SEGMENT_RESET_TIME_GAP,
            tracker.observe(snapshot(time = 6 * 60 * 60 * 1_000L + 1_001, phone = 52, keyboard = 76))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.REJECTED_OUT_OF_ORDER,
            tracker.observe(snapshot(time = 2_000, phone = 51, keyboard = 77))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.BASELINE,
            tracker.observe(snapshot(time = 3_000, phone = 51, keyboard = 77))
        )
    }

    @Test
    fun rejectsInvalidAndOutlierDeltas() {
        val tracker = tracker()

        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.REJECTED_INVALID_SNAPSHOT,
            tracker.observe(snapshot(time = 1_000, phone = 101, keyboard = 80))
        )
        tracker.observe(snapshot(time = 2_000, phone = 50, keyboard = 80))
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.REJECTED_OUTLIER,
            tracker.observe(snapshot(time = 3_000, phone = 66, keyboard = 64))
        )
        assertEquals(
            ClicksPowerSocCalibrationSampleDisposition.REJECTED_OUTLIER,
            tracker.observe(snapshot(time = 4_000, phone = 80, keyboard = 63))
        )
    }

    @Test
    fun estimateRequiresEnoughIndependentSamplesAndObservedKeyboardSpend() {
        val tracker = tracker()
        tracker.observe(snapshot(time = 1_000, phone = 50, keyboard = 80))
        repeat(3) { index ->
            tracker.observe(snapshot(time = 2_000L + index, phone = 51 + index, keyboard = 78 - index * 2))
        }

        assertNull(tracker.estimateOrNull())

        val fourth = tracker.observe(snapshot(time = 5_000, phone = 54, keyboard = 72))
        assertEquals(ClicksPowerSocCalibrationSampleDisposition.ACCEPTED, fourth)
        val estimate = tracker.estimateOrNull()
        assertNotNull(estimate)
        assertEquals(0.5, estimate!!.phonePercentPerKeyboardPercent, 0.0001)
        assertEquals(4, estimate.acceptedSampleCount)
        assertEquals(8, estimate.keyboardPercentObserved)
    }

    @Test
    fun estimateAlsoRequiresSufficientKeyboardMovement() {
        val aggregate = ClicksPowerSocCalibrationAggregate(
            List(4) { ClicksPowerSocCalibrationSample(phoneGainPercent = 1, keyboardSpentPercent = 1) }
        )

        assertNull(aggregate.estimateOrNull())
    }

    @Test
    fun statusExposesCalibrationProgressBeforeAnEstimateExists() {
        val aggregate = ClicksPowerSocCalibrationAggregate(
            listOf(
                ClicksPowerSocCalibrationSample(1, 2),
                ClicksPowerSocCalibrationSample(1, 2),
                ClicksPowerSocCalibrationSample(1, 2)
            )
        )

        val status = aggregate.status()

        assertNull(status.estimate)
        assertEquals(3, status.acceptedSampleCount)
        assertEquals(6, status.keyboardPercentObserved)
    }

    @Test
    fun chargeProjectionUsesAvailableKeyboardChargeAndPhoneHeadroom() {
        val estimate = ClicksPowerSocCalibrationEstimate(
            phonePercentPerKeyboardPercent = 0.5,
            acceptedSampleCount = 4,
            keyboardPercentObserved = 8
        )

        assertEquals(
            ClicksPowerChargeProjection(
                availableKeyboardPercent = 60,
                estimatedPhoneGainPercent = 30
            ),
            estimate.projectChargeUntilReserve(
                keyboardBatteryPercent = 80,
                reservePercent = 20,
                phoneBatteryPercent = 40
            )
        )
        assertEquals(
            5,
            estimate.projectChargeUntilReserve(
                keyboardBatteryPercent = 80,
                reservePercent = 20,
                phoneBatteryPercent = 95
            )!!.estimatedPhoneGainPercent
        )
        assertEquals(
            0,
            estimate.projectChargeUntilReserve(
                keyboardBatteryPercent = 10,
                reservePercent = 20,
                phoneBatteryPercent = 40
            )!!.availableKeyboardPercent
        )
    }

    @Test
    fun robustMedianIsNotPulledByAnOtherwiseValidExtremeSample() {
        val aggregate = ClicksPowerSocCalibrationAggregate(
            listOf(
                ClicksPowerSocCalibrationSample(1, 2),
                ClicksPowerSocCalibrationSample(2, 4),
                ClicksPowerSocCalibrationSample(3, 6),
                ClicksPowerSocCalibrationSample(2, 2),
                ClicksPowerSocCalibrationSample(1, 10)
            )
        )

        assertEquals(0.5, aggregate.estimateOrNull()!!.phonePercentPerKeyboardPercent, 0.0001)
    }

    @Test
    fun aggregatePersistsAcrossTrackerRecreationForTheSameKeyboardOnly() {
        val store = MemoryStore()
        val firstProcess = ClicksPowerSocCalibrationTracker("serial-a", store)
        firstProcess.observe(snapshot(time = 1_000, phone = 50, keyboard = 90))
        repeat(4) { index ->
            firstProcess.observe(
                snapshot(
                    time = 2_000L + index,
                    phone = 51 + index,
                    keyboard = 88 - index * 2
                )
            )
        }

        val restarted = ClicksPowerSocCalibrationTracker("serial-a", store)
        assertEquals(0.5, restarted.estimateOrNull()!!.phonePercentPerKeyboardPercent, 0.0001)
        assertNull(ClicksPowerSocCalibrationTracker("serial-b", store).estimateOrNull())
        assertTrue(store.savedKeys.contains("serial-a"))
        assertFalse(store.savedKeys.contains("serial-b"))
    }

    private fun tracker() = ClicksPowerSocCalibrationTracker("serial-a", MemoryStore())

    private fun snapshot(
        time: Long,
        phone: Int,
        keyboard: Int,
        charging: Boolean = true,
        plugged: ClicksPhonePluggedType = ClicksPhonePluggedType.NONE
    ) = ClicksPowerSocSnapshot(
        timestampMillis = time,
        phoneBatteryPercent = phone,
        keyboardBatteryPercent = keyboard,
        wirelessChargingEnabled = charging,
        phonePluggedType = plugged
    )

    private class MemoryStore : ClicksPowerSocCalibrationStore {
        private val values = mutableMapOf<String, ClicksPowerSocCalibrationAggregate>()
        val savedKeys = mutableSetOf<String>()

        override fun load(keyboardId: String): ClicksPowerSocCalibrationAggregate? = values[keyboardId]

        override fun save(keyboardId: String, aggregate: ClicksPowerSocCalibrationAggregate) {
            values[keyboardId] = aggregate
            savedKeys += keyboardId
        }
    }
}
