package it.palsoftware.pastiera

import kotlin.math.roundToInt

/**
 * A single, time-ordered pair of battery state-of-charge readings.
 *
 * [phonePluggedType] retains Android's concrete power-source category instead of collapsing it
 * to a boolean. Android does not guarantee that every battery broadcast contains it, so
 * [ClicksPhonePluggedType.UNKNOWN] is not treated as evidence either way.
 */
data class ClicksPowerSocSnapshot(
    val timestampMillis: Long,
    val phoneBatteryPercent: Int,
    val keyboardBatteryPercent: Int,
    val wirelessChargingEnabled: Boolean,
    val phonePluggedType: ClicksPhonePluggedType
)

enum class ClicksPhonePluggedType {
    NONE,
    AC,
    USB,
    WIRELESS,
    DOCK,
    UNKNOWN
}

/** The current segment, not a claim that a usable conversion has been measured. */
enum class ClicksPowerSocCalibrationPhase {
    IDLE,
    PHONE_CHARGING_FROM_KEYBOARD,
    NOT_CHARGING_FROM_KEYBOARD
}

enum class ClicksPowerSocCalibrationSampleDisposition {
    BASELINE,
    ACCEPTED,
    IGNORED_NO_TRANSFER,
    REJECTED_INVALID_SNAPSHOT,
    REJECTED_OUT_OF_ORDER,
    SEGMENT_RESET_EXTERNAL_PHONE_POWER,
    SEGMENT_RESET_PHASE_CHANGE,
    SEGMENT_RESET_TIME_GAP,
    WAITING_FOR_MONOTONE_DELTA,
    REJECTED_NON_MONOTONIC,
    REJECTED_OUTLIER
}

/** A measured phone gain and keyboard spend, both expressed in percentage points. */
data class ClicksPowerSocCalibrationSample(
    val phoneGainPercent: Int,
    val keyboardSpentPercent: Int
) {
    val phonePercentPerKeyboardPercent: Double
        get() = phoneGainPercent.toDouble() / keyboardSpentPercent

    fun isValid(): Boolean =
        phoneGainPercent in 1..MAX_SINGLE_DELTA_PERCENT &&
            keyboardSpentPercent in 1..MAX_SINGLE_DELTA_PERCENT &&
            phonePercentPerKeyboardPercent in MIN_RATIO..MAX_RATIO

    private companion object {
        const val MAX_SINGLE_DELTA_PERCENT = 15
        const val MIN_RATIO = 0.1
        const val MAX_RATIO = 2.0
    }
}

/** Persisted input for the robust estimate. Keeping individual samples makes the median restart-safe. */
data class ClicksPowerSocCalibrationAggregate(
    val samples: List<ClicksPowerSocCalibrationSample> = emptyList()
) {
    fun sanitized(): ClicksPowerSocCalibrationAggregate =
        copy(samples = samples.filter(ClicksPowerSocCalibrationSample::isValid).takeLast(MAX_STORED_SAMPLES))

    fun withSample(sample: ClicksPowerSocCalibrationSample): ClicksPowerSocCalibrationAggregate =
        ClicksPowerSocCalibrationAggregate((samples + sample).takeLast(MAX_STORED_SAMPLES)).sanitized()

    fun estimateOrNull(): ClicksPowerSocCalibrationEstimate? {
        val usableSamples = sanitized().samples
        val keyboardSpent = usableSamples.sumOf(ClicksPowerSocCalibrationSample::keyboardSpentPercent)
        if (usableSamples.size < MINIMUM_SAMPLE_COUNT || keyboardSpent < MINIMUM_KEYBOARD_SPENT_PERCENT) {
            return null
        }
        val ratios = usableSamples.map(ClicksPowerSocCalibrationSample::phonePercentPerKeyboardPercent).sorted()
        val middle = ratios.size / 2
        val median = if (ratios.size % 2 == 0) {
            (ratios[middle - 1] + ratios[middle]) / 2.0
        } else {
            ratios[middle]
        }
        return ClicksPowerSocCalibrationEstimate(
            phonePercentPerKeyboardPercent = median,
            acceptedSampleCount = usableSamples.size,
            keyboardPercentObserved = keyboardSpent
        )
    }

    fun status(): ClicksPowerSocCalibrationStatus {
        val usableSamples = sanitized().samples
        return ClicksPowerSocCalibrationStatus(
            estimate = estimateOrNull(),
            acceptedSampleCount = usableSamples.size,
            keyboardPercentObserved = usableSamples.sumOf(
                ClicksPowerSocCalibrationSample::keyboardSpentPercent
            )
        )
    }

    companion object {
        const val MAX_STORED_SAMPLES = 40
        const val MINIMUM_SAMPLE_COUNT = 4
        const val MINIMUM_KEYBOARD_SPENT_PERCENT = 8
    }
}

/**
 * A conservative conversion estimate. It is present only after independent, confirmed transfer
 * deltas were collected. It must not be interpreted as a battery capacity measurement.
 */
data class ClicksPowerSocCalibrationEstimate(
    val phonePercentPerKeyboardPercent: Double,
    val acceptedSampleCount: Int,
    val keyboardPercentObserved: Int
)

data class ClicksPowerSocCalibrationStatus(
    val estimate: ClicksPowerSocCalibrationEstimate?,
    val acceptedSampleCount: Int,
    val keyboardPercentObserved: Int
)

data class ClicksPowerChargeProjection(
    val availableKeyboardPercent: Int,
    val estimatedPhoneGainPercent: Int
)

/** Converts the currently usable keyboard charge into a bounded phone-SOC estimate. */
fun ClicksPowerSocCalibrationEstimate.projectChargeUntilReserve(
    keyboardBatteryPercent: Int,
    reservePercent: Int,
    phoneBatteryPercent: Int? = null
): ClicksPowerChargeProjection? {
    if (keyboardBatteryPercent !in 0..100 || reservePercent !in 0..100) return null
    if (phoneBatteryPercent != null && phoneBatteryPercent !in 0..100) return null
    val availableKeyboardPercent = (keyboardBatteryPercent - reservePercent).coerceAtLeast(0)
    val phoneHeadroom = phoneBatteryPercent?.let { 100 - it } ?: 100
    val estimatedPhoneGainPercent =
        (availableKeyboardPercent * phonePercentPerKeyboardPercent)
            .roundToInt()
            .coerceIn(0, phoneHeadroom)
    return ClicksPowerChargeProjection(
        availableKeyboardPercent = availableKeyboardPercent,
        estimatedPhoneGainPercent = estimatedPhoneGainPercent
    )
}

interface ClicksPowerSocCalibrationStore {
    fun load(keyboardId: String): ClicksPowerSocCalibrationAggregate?
    fun save(keyboardId: String, aggregate: ClicksPowerSocCalibrationAggregate)
}

/** True only when every keyboard value used for calibration belongs to this GATT session. */
internal fun ClicksPowerKeyboardState.hasFreshSocCalibrationInputs(): Boolean =
    connected && ready && sessionValidated && !stale &&
        serialNumber?.isNotBlank() == true &&
        batteryPercent != null && !batteryPercentStale &&
        wirelessChargingEnabled != null && !wirelessChargingEnabledStale

/**
 * Collects only observed phone charging from the keyboard.
 *
 * A disabled wireless-charging flag starts a separate segment but intentionally contributes no
 * samples: simultaneous discharge of phone and keyboard does not demonstrate an SOC conversion.
 * Likewise, the enabled flag is not enough by itself; phone SOC must increase while keyboard SOC
 * decreases. A wireless plugged type is allowed only in this enabled phase, since it is the
 * expected Clicks transfer path. Android cannot distinguish the Clicks output from a foreign
 * Qi pad, so a foreign pad remains an unavoidable source-attribution limitation.
 */
class ClicksPowerSocCalibrationTracker(
    private val keyboardId: String,
    private val store: ClicksPowerSocCalibrationStore
) {
    private var aggregate = store.load(keyboardId)?.sanitized() ?: ClicksPowerSocCalibrationAggregate()
    private var baseline: ClicksPowerSocSnapshot? = null

    var phase: ClicksPowerSocCalibrationPhase = ClicksPowerSocCalibrationPhase.IDLE
        private set

    fun estimateOrNull(): ClicksPowerSocCalibrationEstimate? = aggregate.estimateOrNull()

    fun status(): ClicksPowerSocCalibrationStatus = aggregate.status()

    fun disconnect() {
        baseline = null
        phase = ClicksPowerSocCalibrationPhase.IDLE
    }

    fun observe(snapshot: ClicksPowerSocSnapshot): ClicksPowerSocCalibrationSampleDisposition {
        if (!snapshot.isValid()) {
            disconnect()
            return ClicksPowerSocCalibrationSampleDisposition.REJECTED_INVALID_SNAPSHOT
        }
        if (snapshot.requiresSegmentReset()) {
            disconnect()
            return ClicksPowerSocCalibrationSampleDisposition.SEGMENT_RESET_EXTERNAL_PHONE_POWER
        }

        val snapshotPhase = snapshot.phase()
        val previous = baseline
        if (previous == null) {
            baseline = snapshot
            phase = snapshotPhase
            return if (snapshotPhase == ClicksPowerSocCalibrationPhase.PHONE_CHARGING_FROM_KEYBOARD) {
                ClicksPowerSocCalibrationSampleDisposition.BASELINE
            } else {
                ClicksPowerSocCalibrationSampleDisposition.IGNORED_NO_TRANSFER
            }
        }
        if (snapshot.timestampMillis <= previous.timestampMillis) {
            disconnect()
            return ClicksPowerSocCalibrationSampleDisposition.REJECTED_OUT_OF_ORDER
        }
        if (snapshot.timestampMillis - previous.timestampMillis > MAX_SEGMENT_GAP_MILLIS) {
            baseline = snapshot
            phase = snapshotPhase
            return ClicksPowerSocCalibrationSampleDisposition.SEGMENT_RESET_TIME_GAP
        }
        if (snapshotPhase != phase) {
            baseline = snapshot
            phase = snapshotPhase
            return ClicksPowerSocCalibrationSampleDisposition.SEGMENT_RESET_PHASE_CHANGE
        }
        if (phase != ClicksPowerSocCalibrationPhase.PHONE_CHARGING_FROM_KEYBOARD) {
            baseline = snapshot
            return ClicksPowerSocCalibrationSampleDisposition.IGNORED_NO_TRANSFER
        }

        val phoneDelta = snapshot.phoneBatteryPercent - previous.phoneBatteryPercent
        val keyboardDelta = snapshot.keyboardBatteryPercent - previous.keyboardBatteryPercent
        if (phoneDelta == 0 || keyboardDelta == 0) {
            return ClicksPowerSocCalibrationSampleDisposition.WAITING_FOR_MONOTONE_DELTA
        }
        if (phoneDelta < 0 || keyboardDelta > 0) {
            baseline = snapshot
            return ClicksPowerSocCalibrationSampleDisposition.REJECTED_NON_MONOTONIC
        }

        val sample = ClicksPowerSocCalibrationSample(
            phoneGainPercent = phoneDelta,
            keyboardSpentPercent = -keyboardDelta
        )
        if (!sample.isValid()) {
            baseline = snapshot
            return ClicksPowerSocCalibrationSampleDisposition.REJECTED_OUTLIER
        }
        aggregate = aggregate.withSample(sample)
        store.save(keyboardId, aggregate)
        baseline = snapshot
        return ClicksPowerSocCalibrationSampleDisposition.ACCEPTED
    }

    private fun ClicksPowerSocSnapshot.isValid(): Boolean =
        timestampMillis >= 0L && phoneBatteryPercent in 0..100 && keyboardBatteryPercent in 0..100

    private fun ClicksPowerSocSnapshot.phase(): ClicksPowerSocCalibrationPhase =
        if (wirelessChargingEnabled) {
            ClicksPowerSocCalibrationPhase.PHONE_CHARGING_FROM_KEYBOARD
        } else {
            ClicksPowerSocCalibrationPhase.NOT_CHARGING_FROM_KEYBOARD
        }

    private fun ClicksPowerSocSnapshot.requiresSegmentReset(): Boolean = when (phonePluggedType) {
        ClicksPhonePluggedType.AC,
        ClicksPhonePluggedType.USB,
        ClicksPhonePluggedType.DOCK -> true
        ClicksPhonePluggedType.WIRELESS -> !wirelessChargingEnabled
        ClicksPhonePluggedType.NONE,
        ClicksPhonePluggedType.UNKNOWN -> false
    }

    private companion object {
        const val MAX_SEGMENT_GAP_MILLIS = 6 * 60 * 60 * 1_000L
    }
}
