package brobata.physiboard.core.shell

/**
 * PhysiBoard's own IME id in both forms Android accepts (app-shell.md SS1): the long form
 * `package/fully.qualified.Service` and the short form `package/.relative.Service`. Every id
 * comparison in the shell (the enabled/selected probe, the package-replaced predicate) goes
 * through [ImeIdentity.matches] so long and short forms are never compared against each other by
 * accident.
 */
object ImeIdentity {

    private fun fullyQualify(packageName: String, serviceClassName: String): String =
        if (serviceClassName.startsWith(".")) packageName + serviceClassName else serviceClassName

    /** `package/fully.qualified.Service`. */
    fun longId(packageName: String, serviceClassName: String): String = "$packageName/${fullyQualify(packageName, serviceClassName)}"

    /** `package/.relative.Service`, the short form 2.x also accepts everywhere it compares an id (SS1). */
    fun shortId(packageName: String, serviceClassName: String): String {
        val fq = fullyQualify(packageName, serviceClassName)
        val relative = if (fq.startsWith(packageName)) fq.removePrefix(packageName) else ".$fq"
        return "$packageName/$relative"
    }

    /** spec: SS1, T20. A blank id or one for another package/service never matches either form. */
    fun matches(candidateId: String?, packageName: String, serviceClassName: String): Boolean {
        if (candidateId.isNullOrBlank()) return false
        return candidateId == longId(packageName, serviceClassName) || candidateId == shortId(packageName, serviceClassName)
    }
}

/** spec: app-shell.md SS8.2. Whether PhysiBoard is enabled and whether it is the active input method. */
data class ImeProbeResult(val enabled: Boolean, val selected: Boolean)

/**
 * The one enabled/selected probe every 2.x screen re-implemented slightly differently
 * (app-shell.md SS8.2): reading the input-method list and the secure setting is Android glue, but
 * the three-branch decision the spec pins down is not. [SecureSettingOutcome] models step 3, the
 * Android 14+ security-exception fallback.
 */
sealed interface SecureSettingOutcome {
    data class Read(val defaultInputMethodId: String?) : SecureSettingOutcome
    data object SecurityException : SecureSettingOutcome
}

object ImeProbe {
    /**
     * spec: SS8.2. Step 1: not enabled ends the probe at (false, false). Step 2: selected is
     * whether the secure setting equals PhysiBoard's id in either form. Step 3: when reading the
     * setting throws, [sharedVariantSecurityFallback] decides selected instead of failing outright
     * (used by home/setup; the Status screen variant instead always reports not selected, which
     * callers get by never invoking this fallback). Step 4: any other failure is the caller's job
     * (a caught exception around the whole probe reporting not enabled, not selected).
     */
    fun evaluate(
        enabledInList: Boolean,
        packageName: String,
        serviceClassName: String,
        outcome: SecureSettingOutcome,
        currentSubtypeExists: Boolean,
        enabledInputMethodCount: Int,
    ): ImeProbeResult {
        if (!enabledInList) return ImeProbeResult(enabled = false, selected = false)
        val selected = when (outcome) {
            is SecureSettingOutcome.Read -> ImeIdentity.matches(outcome.defaultInputMethodId, packageName, serviceClassName)
            SecureSettingOutcome.SecurityException -> sharedVariantSecurityFallback(currentSubtypeExists, enabledInList, enabledInputMethodCount)
        }
        return ImeProbeResult(enabled = true, selected = selected)
    }

    /** spec: SS8.2 step 3, the shared (non-Status-screen) variant: selected only if PhysiBoard is the phone's sole enabled IME with a current subtype. */
    private fun sharedVariantSecurityFallback(currentSubtypeExists: Boolean, imeInFullList: Boolean, enabledInputMethodCount: Int): Boolean =
        currentSubtypeExists && imeInFullList && enabledInputMethodCount == 1
}
