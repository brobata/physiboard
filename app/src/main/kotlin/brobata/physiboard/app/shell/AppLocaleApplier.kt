package brobata.physiboard.app.shell

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import brobata.physiboard.core.shell.AppLocale

/**
 * Applies `app_language_tag` (dictionaries-languages.md SS11) through
 * [AppCompatDelegate.setApplicationLocales]: on API 33+ this calls straight through to the
 * platform's own per-app language feature, which every activity's `Configuration` already
 * reflects with no wrapping needed; AppCompat backports the choice for API 31-32 by recording it
 * and driving activity recreation itself. [applyAndRecreate] additionally recreates the calling
 * [context]'s own activity: this app's screens are plain `ComponentActivity`, not
 * `AppCompatActivity`, so AppCompat's own recreate pass (which only tracks `AppCompatActivity`
 * instances) would otherwise miss them, and "Changing it recreates the current screen"
 * (app-shell.md SS22.3) needs to hold regardless.
 */
object AppLocaleApplier {

    /** Sync AppCompatDelegate's state to the stored setting; call once at process start, before the first screen draws. */
    fun applyAtStartup(storedTag: String) {
        AppCompatDelegate.setApplicationLocales(localesFor(storedTag))
    }

    /** Apply a newly chosen tag immediately and recreate [context]'s activity so the change is visible right away. */
    fun applyAndRecreate(context: Context, storedTag: String) {
        AppCompatDelegate.setApplicationLocales(localesFor(storedTag))
        context.findActivity()?.recreate()
    }

    private fun localesFor(storedTag: String): LocaleListCompat {
        val tag = AppLocale.resolve(storedTag)
        return if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
