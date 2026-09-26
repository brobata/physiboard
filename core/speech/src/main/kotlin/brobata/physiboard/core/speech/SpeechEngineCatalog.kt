package brobata.physiboard.core.speech

/**
 * Friendly names and detail copy for the "Speech engine" picker (spec: dictation.md SS4.1). Kept
 * pure and out of `:app` so the exact copy for each known package is a JVM-testable fact, not
 * something re-typed beside a `Composable`. `:app`'s `SpeechEngines` does the Android-side lookups
 * (`PackageManager`, `Settings.Secure`) and calls into this object for the strings.
 */
object SpeechEngineCatalog {
    /** `dictation_engine`'s empty-string value: the platform's own default recognizer. */
    const val SYSTEM_DEFAULT: String = ""

    /** `dictation_engine`'s `ondevice` value: the platform's on-device recognizer, Android 12+ only. */
    const val ON_DEVICE: String = "ondevice"

    private const val GOOGLE_PACKAGE = "com.google.android.tts"
    private const val ANDROID_SYSTEM_INTELLIGENCE_PACKAGE = "com.google.android.as"
    private const val HOME_ASSISTANT_PACKAGE = "io.homeassistant.companion.android"
    private const val CLAUDE_PACKAGE = "com.anthropic.claude"

    /** spec SS4.1: the intro copy the picker dialog opens with. */
    const val INTRO: String = "If dictation cuts off too early, mishears you or won't punctuate, try a different engine, " +
        "that is the whole reason to change this. Engines that run on the phone start faster and work with no signal; " +
        "ones that use a server usually understand more."

    /** spec SS4.1: the tag on the row whose package equals the system default's package. */
    const val SYSTEM_DEFAULT_TAG: String = "Currently the system default"

    /** spec SS4.1 row 2's detail text. */
    const val ON_DEVICE_DETAIL: String = "Android's own built-in recognizer, always on the phone. Starts fastest and " +
        "needs no signal, but knows fewer words and often skips punctuation."

    /** spec SS4.1: "package `com.google.android.tts` is shown as \"Google\"; ... anything else by its app label, or its package name if the label cannot be read." */
    fun friendlyName(packageName: String, appLabel: String?): String = when (packageName) {
        GOOGLE_PACKAGE -> "Google"
        ANDROID_SYSTEM_INTELLIGENCE_PACKAGE -> "Android System Intelligence"
        else -> appLabel?.takeIf { it.isNotBlank() } ?: packageName
    }

    /** spec SS4.1: the per-engine detail text keyed by package, including the "any other" fallback. */
    fun detailFor(packageName: String): String = when (packageName) {
        GOOGLE_PACKAGE -> "The recognizer behind Google voice typing. The usual best all-rounder for accuracy and " +
            "punctuation; downloads a language pack so it still works offline."
        ANDROID_SYSTEM_INTELLIGENCE_PACKAGE -> "Google's on-device engine, the one behind Live Caption. Runs on the " +
            "phone, so it is quick and needs no signal; weaker on unusual words and names."
        HOME_ASSISTANT_PACKAGE -> "Sends what you say to your Home Assistant server and uses the speech-to-text set " +
            "up there. Only works while that server is reachable."
        CLAUDE_PACKAGE -> "Transcribes through the Claude app. Needs a connection and your Claude account."
        else -> "A speech engine added by this app. How well it hears you, and whether it needs a connection, is up to that app."
    }

    /** spec SS4.1 row 1's detail text; [systemDefaultFriendlyName] is null when the secure key is empty or unreadable. */
    fun systemDefaultDetail(systemDefaultFriendlyName: String?): String =
        if (systemDefaultFriendlyName.isNullOrBlank()) {
            "Follows Android's voice input setting"
        } else {
            "Follows Android's voice input setting, which is $systemDefaultFriendlyName today. Pick this to keep matching the rest of the phone."
        }
}
