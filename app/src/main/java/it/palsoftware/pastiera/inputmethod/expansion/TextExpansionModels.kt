package it.palsoftware.pastiera.inputmethod.expansion

enum class ExpansionProvider {
    SNIPPET,
    EMOJI,
    SYMBOL
}

enum class ExpansionPresentation(val storageValue: String) {
    OFF("off"),
    FLOATING_POPUP("floating_popup"),
    SUGGESTION_BAR("suggestion_bar");

    companion object {
        fun fromStorage(value: String?): ExpansionPresentation =
            entries.firstOrNull { it.storageValue == value } ?: FLOATING_POPUP
    }
}

data class ExpansionActivationPolicy(
    val exactOnSpace: Boolean,
    val acceptWithTab: Boolean,
    val acceptWithEnter: Boolean
)

data class ExpansionMatch(
    val provider: ExpansionProvider,
    val shortcut: String,
    val replacement: String,
    val token: String,
    val tokenStart: Int,
    val displayText: String = replacement
)

data class ExpansionQuery(
    val token: String,
    val prefix: String,
    val typedShortcut: String,
    val tokenStart: Int,
    val closed: Boolean
)

interface ExpansionSource {
    val provider: ExpansionProvider
    fun matches(query: ExpansionQuery, limit: Int = 10): List<ExpansionMatch>
}

enum class ExpansionTriggerKind {
    PREFIX,
    COLON_SHORTCODE
}

data class ExpansionRuntimeConfig(
    val source: ExpansionSource,
    val triggerKind: ExpansionTriggerKind,
    val enabled: Boolean,
    val prefix: Char? = null,
    val presentation: ExpansionPresentation,
    val activationPolicy: ExpansionActivationPolicy,
    val exactOnClose: Boolean = false
)
