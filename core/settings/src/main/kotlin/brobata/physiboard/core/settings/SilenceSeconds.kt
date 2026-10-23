package brobata.physiboard.core.settings

/**
 * Voice > "Seconds of silence": the dictation silence limit typed as seconds, 1 to 60, decimals
 * allowed (dictation.md SS12.1). Stored as `dictation_stop_after_silence_ms`.
 */
object SilenceSeconds {
    /**
     * 10 s after the last word (dictation.md SS6.4, 2026-10-22). The 2.5 s of 3.1 to 3.4 was set
     * on 2026-10-07 while the engine itself still ended every request at a breath; once the
     * engine held its session, that timer was the one thing still cutting a pause short.
     */
    const val DEFAULT_MS: Int = 10_000

    /** Typed seconds as milliseconds; null while the text is not yet a number from 1 to 60. */
    fun parse(typed: String): Int? {
        val seconds = typed.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (seconds.isNaN() || seconds < 1.0 || seconds > 60.0) return null
        return Math.round(seconds * 1000).toInt()
    }

    /** 2500 -> "2.5", 5000 -> "5". */
    fun format(ms: Int): String {
        val s = ms / 1000.0
        return if (s == Math.floor(s)) s.toInt().toString() else s.toString().trimEnd('0').trimEnd('.')
    }
}
