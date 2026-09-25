package brobata.physiboard.core.settings

/**
 * Resolves which [StripTheme] the strip actually draws for a given locale and layout, once the
 * per-layout override list ([ThemeLayoutOverride]) is in play. spec: status-bar.md SS9.2 ("the
 * most specific matching override... beats the chosen theme") and settings-catalog.md SS2.6's
 * scoring table ("exact locale 16 points, language 8, layout 4; the best match wins"). Locales are
 * normalized so underscores and hyphens compare equal, per status-bar.md SS9.2.
 */
object StripThemeResolution {

    /**
     * [chosen] is the target's own theme (`keyboard_theme_hardware`); [overrides] is searched for
     * the highest-scoring match and its theme wins ties by list order (first entry kept). No
     * override scores above zero, or [overrides] is empty, returns [chosen] unchanged.
     */
    fun resolve(chosen: StripTheme, overrides: List<ThemeLayoutOverride>, locale: String, layout: String): StripTheme {
        val normalizedLocale = normalize(locale)
        val language = normalizedLocale.substringBefore('-')
        var best: ThemeLayoutOverride? = null
        var bestScore = 0
        for (override in overrides) {
            val score = score(override, normalizedLocale, language, layout)
            if (score > bestScore) {
                bestScore = score
                best = override
            }
        }
        return best?.theme ?: chosen
    }

    private fun score(override: ThemeLayoutOverride, normalizedLocale: String, language: String, layout: String): Int {
        var score = 0
        override.locale?.let { raw ->
            val overrideLocale = normalize(raw)
            score += when {
                overrideLocale == normalizedLocale -> 16
                overrideLocale.substringBefore('-') == language -> 8
                else -> 0
            }
        }
        if (override.layout != null && override.layout == layout) score += 4
        return score
    }

    private fun normalize(locale: String): String = locale.replace('_', '-').lowercase()
}
