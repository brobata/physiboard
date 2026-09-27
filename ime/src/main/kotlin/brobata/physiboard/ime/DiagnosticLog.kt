package brobata.physiboard.ime

import android.util.Log

/**
 * The one gate for the phone-testing diagnostic trail: [KeyboardSession] and
 * [DictionaryAssetLoader] write keystrokes, field changes and selection reports at info, because
 * the phone drops verbose logs during testing (docs/release.md, "Logging").
 *
 * It is OFF unless someone turns it on, even in a debuggable build. This trail sits on the path
 * every keystroke takes, and the keyboard has to keep up with a fast typist: on the maintainer's
 * Titan it could not, and one line of it was recomputing the whole suggestion ranking to print
 * the words it would have offered (2026-09-27). Building strings for every key of every sentence
 * is not free either, so the daily build pays nothing until [enabled] is set. A release build
 * compiles [i]'s body away regardless. The crash guards at [Log.e] never come through here and
 * stay unconditional in every build.
 */
internal object DiagnosticLog {
    /** Set from `ime_overlay_debug_logging`; false until a settings emission says otherwise. */
    @Volatile
    var enabled: Boolean = false

    /** Logs [message] at info, built lazily so nothing is constructed while the trail is off. */
    inline fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG && enabled) Log.i(tag, message())
    }
}
