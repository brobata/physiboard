package it.palsoftware.pastiera

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object AppLocaleManager {
    fun wrapContext(base: Context): Context {
        val tag = SettingsManager.getAppLanguageTag(base)
        if (tag.isNullOrBlank()) {
            return base
        }

        val locale = Locale.forLanguageTag(tag)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(android.os.LocaleList(locale))
        }
        return base.createConfigurationContext(config)
    }
}
