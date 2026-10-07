package brobata.physiboard.core.shell

/**
 * Why PhysiBoard wants the network. Every request names one, so a refusal can say what it
 * stopped. A feature that adds a request (a GIF search, say) adds its purpose here and goes
 * through [NetworkGate] like the rest. spec: app-shell.md SS31.2.
 */
enum class NetworkPurpose(val label: String) {
    UPDATE_CHECK("update check"),
    DICTIONARY_MANIFEST("dictionary list"),
    DICTIONARY_DOWNLOAD("dictionary download"),

    /** The GIF page's search and trending lists (layers-sym-alt.md SS4.5). */
    GIF_SEARCH("GIF search"),

    /** A GIF preview or the GIF being sent, from the provider's media servers. */
    GIF_MEDIA("GIF download"),
}

/** The gate's answer for one request. */
sealed class NetworkDecision {
    data object Allowed : NetworkDecision()

    data class Blocked(val purpose: NetworkPurpose, val reason: String) : NetworkDecision()
}

/**
 * The one door every PhysiBoard network request goes through. spec: app-shell.md SS31.2.
 *
 * Private mode closes it for every purpose. So does not knowing: when the setting cannot be read
 * ([privateMode] null), the request does not go out, because a keyboard that cannot tell whether
 * the user asked it to stay offline must assume they did.
 *
 * The decision is pure; `:app`'s one HTTP opener asks it before every connection, and a test
 * (NetworkGateTest) fails the build if any other file opens one.
 */
object NetworkGate {
    const val BLOCKED_PRIVATE = "Private mode is on, so PhysiBoard makes no network requests."
    const val BLOCKED_UNKNOWN = "PhysiBoard could not read its settings, so it makes no network requests."

    fun decide(privateMode: Boolean?, purpose: NetworkPurpose): NetworkDecision = when (privateMode) {
        false -> NetworkDecision.Allowed
        true -> NetworkDecision.Blocked(purpose, BLOCKED_PRIVATE)
        null -> NetworkDecision.Blocked(purpose, BLOCKED_UNKNOWN)
    }
}
