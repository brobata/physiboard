package brobata.physiboard.ime

import android.util.Log

/**
 * The one gate for the phone-testing diagnostic trail: [KeyboardSession] and
 * [DictionaryAssetLoader] write every keystroke, field change and selection report at info
 * because the phone drops verbose logs during testing (docs/release.md, "Logging"). [BuildConfig]
 * is generated per build type, so `sideload` (which `initWith(debug)`) keeps the trail while
 * `release` compiles [i]'s body away entirely; the crash guards at [Log.e] never go through this
 * object and stay unconditional in every build.
 */
internal object DiagnosticLog {
    /** Logs [message] at info, built lazily so a release build pays nothing to construct it. */
    inline fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.i(tag, message())
    }
}
