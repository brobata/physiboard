package it.palsoftware.pastiera

/** Pure policy boundary for promoting a cached snapshot into the current GATT session. */
internal object ClicksGattReadSessionPolicy {
    fun shouldDiscardCachedState(
        hadCachedDeviceData: Boolean,
        cachedSerialNumber: String?,
        freshSerialNumber: String?
    ): Boolean = hadCachedDeviceData && freshSerialNumber != null && cachedSerialNumber != freshSerialNumber

    fun isSessionValidated(readFailed: Boolean, freshSerialNumber: String?): Boolean =
        !readFailed && !freshSerialNumber.isNullOrBlank()
}

/**
 * Prevents an operation response received after a timeout from satisfying a later operation that
 * happens to use the same response group. Clicks responses do not carry a transaction identifier,
 * so a timed-out GATT session cannot safely process any further control responses.
 */
internal class ClicksGattResponseSessionGate {
    var timedOut: Boolean = false
        private set

    fun onOperationTimeout() {
        timedOut = true
    }

    fun acceptsResponse(expectedGroup: Int, receivedGroup: Int): Boolean =
        !timedOut && expectedGroup == receivedGroup
}
