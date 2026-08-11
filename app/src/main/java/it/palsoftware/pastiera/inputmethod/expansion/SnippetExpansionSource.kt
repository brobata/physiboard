package it.palsoftware.pastiera.inputmethod.expansion

import java.util.Locale

class SnippetExpansionSource(
    private val entriesProvider: () -> Map<String, String>
) : ExpansionSource {
    override val provider: ExpansionProvider = ExpansionProvider.SNIPPET

    override fun matches(query: ExpansionQuery, limit: Int): List<ExpansionMatch> {
        val prefix = query.typedShortcut.lowercase(Locale.ROOT)
        return entriesProvider().asSequence()
            .filter { (shortcut, _) -> shortcut.startsWith(prefix) }
            .sortedBy { (shortcut, _) -> shortcut.length }
            .take(limit)
            .map { (shortcut, replacement) ->
                ExpansionMatch(
                    provider = provider,
                    shortcut = shortcut,
                    replacement = replacement,
                    token = query.token,
                    tokenStart = query.tokenStart,
                    displayText = "$shortcut → ${replacement.lineSequence().firstOrNull().orEmpty()}"
                )
            }
            .toList()
    }
}
