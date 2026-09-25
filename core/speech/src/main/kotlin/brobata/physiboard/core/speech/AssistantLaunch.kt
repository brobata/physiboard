package brobata.physiboard.core.speech

/** The three requests that can open the assistant, in the automatic order. spec: dictation.md SS11.2 step 1. */
enum class AssistantRequest(val intentAction: String) {
    VOICE_COMMAND("android.intent.action.VOICE_COMMAND"),
    HANDS_FREE("android.speech.action.VOICE_SEARCH_HANDS_FREE"),
    ASSIST("android.intent.action.ASSIST"),
}

/**
 * Which requests to try, and in what order, when opening the assistant. spec: dictation.md
 * SS11.2 step 1: "the action chosen in `assistant_action` first (if not `auto`), then the
 * automatic order... duplicates removed". Steps 2 to 5 (finding the assistant package, targeted
 * then untargeted attempts) are platform work the caller does with this list.
 */
object AssistantLaunch {
    /** [preferred] is null for `auto`. */
    fun order(preferred: AssistantRequest?): List<AssistantRequest> =
        (listOfNotNull(preferred) + AssistantRequest.entries).distinct()
}
