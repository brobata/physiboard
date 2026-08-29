package brobata.physiboard

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.inputmethod.InputMethodManager

/**
 * Whether PhysiBoard is enabled as an input method and whether it is the selected one.
 */
data class ImeStatus(val enabled: Boolean, val selected: Boolean) {
    companion object {
        private const val TAG = "ImeStatus"

        fun check(context: Context): ImeStatus {
            return try {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                val packageName = ImeIdentity.packageName

                val enabledInputMethods = imm.enabledInputMethodList
                val enabled = enabledInputMethods.any { info ->
                    info.packageName == packageName || ImeIdentity.matchesImeId(info.id)
                }
                if (!enabled) return ImeStatus(enabled = false, selected = false)

                val selected = try {
                    val defaultInputMethod = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.DEFAULT_INPUT_METHOD
                    ) ?: ""
                    ImeIdentity.matchesImeId(defaultInputMethod)
                } catch (e: SecurityException) {
                    // Android 14+ can refuse the secure setting; infer from the current subtype.
                    try {
                        val currentSubtype = imm.currentInputMethodSubtype
                        val physiBoard = imm.inputMethodList.find {
                            it.packageName == packageName || ImeIdentity.matchesImeId(it.id)
                        }
                        currentSubtype != null && physiBoard != null && enabledInputMethods.size == 1
                    } catch (e2: Exception) {
                        Log.e(TAG, "Could not determine the selected input method", e2)
                        false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read the default input method", e)
                    false
                }
                ImeStatus(enabled = enabled, selected = selected)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking IME status", e)
                ImeStatus(enabled = false, selected = false)
            }
        }
    }
}
