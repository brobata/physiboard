package brobata.physiboard.app.shell

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import brobata.physiboard.core.shell.ImeIdentity
import brobata.physiboard.core.shell.ImeProbe
import brobata.physiboard.core.shell.ImeProbeResult
import brobata.physiboard.core.shell.SecureSettingOutcome

/**
 * The Android half of the enabled/selected probe (app-shell.md SS8.2): reads the input-method
 * list and the secure setting, then hands the plain facts to [ImeProbe] for the decision. This is
 * the "shared" (non-Status-screen) variant everywhere it is used in `:app` (home, setup); nothing
 * in this milestone builds the Status-screen's stricter variant, which never applies the Android
 * 14+ fallback (app-shell.md SS8.2 step 3).
 *
 * [serviceClassName] is this build's own input-method service ([ImeComponent.SERVICE_CLASS_NAME]);
 * it is a parameter rather than a hard-coded read so a JVM test of the pure [ImeProbe] never needs
 * this class at all.
 */
object ImeProbeAndroid {
    fun evaluate(context: Context, serviceClassName: String): ImeProbeResult {
        val packageName = context.packageName
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledList = imm?.enabledInputMethodList.orEmpty()
        val enabledInList = enabledList.any { it.packageName == packageName || ImeIdentity.matches(it.id, packageName, serviceClassName) }

        val outcome = try {
            SecureSettingOutcome.Read(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
        } catch (_: SecurityException) {
            SecureSettingOutcome.SecurityException
        }

        val currentSubtypeExists = try {
            imm?.currentInputMethodSubtype != null
        } catch (_: Exception) {
            false
        }

        return try {
            ImeProbe.evaluate(
                enabledInList = enabledInList,
                packageName = packageName,
                serviceClassName = serviceClassName,
                outcome = outcome,
                currentSubtypeExists = currentSubtypeExists,
                enabledInputMethodCount = enabledList.size,
            )
        } catch (_: Exception) {
            // spec: SS8.2 step 4, "any other failure anywhere yields not enabled, not selected".
            ImeProbeResult(enabled = false, selected = false)
        }
    }
}
