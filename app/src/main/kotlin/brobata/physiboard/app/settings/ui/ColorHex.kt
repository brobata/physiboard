package brobata.physiboard.app.settings.ui

/** Every colour in [brobata.physiboard.core.settings.StripTheme] is a signed 32-bit ARGB int; the "text" row type edits it as `#AARRGGBB`. */
object ColorHex {
    fun toHex(argb: Int): String = "#%08X".format(argb)

    /** Accepts `#AARRGGBB` or `#RRGGBB` (opaque); returns null for anything else so the row can show a validation error instead of silently keeping the old colour. */
    fun parse(text: String): Int? {
        val trimmed = text.trim().removePrefix("#")
        return when (trimmed.length) {
            6 -> trimmed.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> trimmed.toLongOrNull(16)?.toInt()
            else -> null
        }
    }

    fun validate(text: String): String? = if (parse(text) == null) "Use #AARRGGBB or #RRGGBB" else null
}
