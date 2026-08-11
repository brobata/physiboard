package it.palsoftware.pastiera.inputmethod.expansion

import android.content.Context
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.core.InputContextState

class TextExpansionController(
    private val context: Context,
    private val handler: Handler,
    private val inputConnectionProvider: () -> InputConnection?,
    private val inputContextProvider: () -> InputContextState,
    private val isSelectionCollapsedProvider: () -> Boolean = { true },
    private val anchorProvider: () -> View?,
    private val configsProvider: () -> List<ExpansionRuntimeConfig>,
    private val showSuggestionBar: (List<String>, (String) -> Unit) -> Unit,
    private val clearSuggestionBar: () -> Unit,
    private val requestSurfaceUpdate: () -> Unit,
    private val onCommitted: (String) -> Unit
) {
    companion object {
        private const val REFRESH_DELAY_MS = 24L
    }

    private val engine = TextExpansionEngine()
    private var refreshRunnable: Runnable? = null
    private var popup: TextExpansionPopup? = null
    private var activeConfig: ExpansionRuntimeConfig? = null
    private var activeQuery: ExpansionQuery? = null
    private var activeMatches: List<ExpansionMatch> = emptyList()
    private var selectedIndex = 0
    private var suggestionBarVisible = false

    fun scheduleRefresh() {
        refreshRunnable?.let(handler::removeCallbacks)
        refreshRunnable = Runnable { refresh() }.also { handler.postDelayed(it, REFRESH_DELAY_MS) }
    }

    fun refresh() {
        refreshRunnable?.let(handler::removeCallbacks)
        refreshRunnable = null
        val state = inputContextProvider()
        if (!state.isReallyEditable || state.restrictedReason != null || !isSelectionCollapsedProvider()) {
            clear()
            return
        }
        val configs = configsProvider().filter { it.enabled }
        if (configs.isEmpty()) {
            clear()
            return
        }
        val inputConnection = inputConnectionProvider() ?: run {
            clear()
            return
        }
        val text = inputConnection.getTextBeforeCursor(TextExpansionEngine.MAX_CONTEXT_CHARS, 0)?.toString().orEmpty()
        val candidates = configs.mapNotNull { config ->
            val query = when (config.triggerKind) {
                ExpansionTriggerKind.PREFIX -> config.prefix?.let { engine.snippetQuery(text, it) }
                ExpansionTriggerKind.COLON_SHORTCODE -> engine.shortcodeQuery(text)
            } ?: return@mapNotNull null
            config to query
        }
        val latest = candidates.maxByOrNull { (_, query) -> query.tokenStart } ?: run {
            clear()
            return
        }
        val matching = candidates.filter { (_, query) ->
            query.tokenStart == latest.second.tokenStart && query.token == latest.second.token
        }
        val matches = engine.collect(latest.second, matching.map { it.first.source })
        if (matches.isEmpty()) {
            clear()
            return
        }
        val queryChanged = activeQuery?.token != latest.second.token ||
            activeConfig?.source !== latest.first.source
        activeConfig = latest.first
        activeQuery = latest.second
        activeMatches = matches
        if (queryChanged) selectedIndex = 0
        selectedIndex = selectedIndex.coerceAtMost(activeMatches.lastIndex)

        val exact = engine.uniqueExact(latest.second, matches)
        if (latest.second.closed && latest.first.exactOnClose && exact != null) {
            commit(exact, suffix = "")
            return
        }
        render(latest.first.presentation)
    }

    fun handleKeyDown(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE ||
            keyCode == KeyEvent.KEYCODE_TAB ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER
        ) {
            // A fast typist can press the activation key before the coalesced refresh runs.
            // Validate the current editor snapshot synchronously rather than missing the match.
            refresh()
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && hasVisibleMatches()) {
            clear()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && hasVisibleMatches()) return moveSelection(-1)
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && hasVisibleMatches()) return moveSelection(1)

        val config = activeConfig ?: return false
        val query = activeQuery ?: return false
        val exact = engine.uniqueExact(query, activeMatches)
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                if (config.activationPolicy.exactOnSpace && exact != null) {
                    commit(exact, " ")
                    true
                } else if (
                    exact == null &&
                    config.activationPolicy.acceptPrefixWithSpace &&
                    hasVisibleMatches()
                ) {
                    activeMatches.getOrNull(selectedIndex)?.let {
                        commit(it, " ")
                        true
                    } ?: false
                } else false
            }
            KeyEvent.KEYCODE_TAB -> acceptIfEnabled(config.activationPolicy.acceptWithTab, exact)
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER ->
                acceptIfEnabled(config.activationPolicy.acceptWithEnter, exact)
            else -> false
        }
    }

    fun clear() {
        val surfaceChanged = popup != null || suggestionBarVisible
        refreshRunnable?.let(handler::removeCallbacks)
        refreshRunnable = null
        popup?.dismiss()
        popup = null
        activeConfig = null
        activeQuery = null
        activeMatches = emptyList()
        selectedIndex = 0
        if (suggestionBarVisible) {
            clearSuggestionBar()
            suggestionBarVisible = false
        }
        if (surfaceChanged) requestSurfaceUpdate()
    }

    private fun acceptIfEnabled(enabled: Boolean, exact: ExpansionMatch?): Boolean {
        if (!enabled) return false
        val selected = if (hasVisibleMatches()) activeMatches.getOrNull(selectedIndex) else exact
        selected ?: return false
        commit(selected, "")
        return true
    }

    private fun commit(match: ExpansionMatch, suffix: String) {
        val inputConnection = inputConnectionProvider() ?: return
        val query = activeQuery ?: return
        val current = inputConnection.getTextBeforeCursor(TextExpansionEngine.MAX_CONTEXT_CHARS, 0)?.toString().orEmpty()
        if (!current.endsWith(query.token)) {
            clear()
            return
        }
        inputConnection.beginBatchEdit()
        inputConnection.finishComposingText()
        inputConnection.deleteSurroundingText(query.token.length, 0)
        inputConnection.commitText(match.replacement + suffix, 1)
        inputConnection.endBatchEdit()
        clear()
        onCommitted(match.replacement + suffix)
    }

    private fun render(presentation: ExpansionPresentation) {
        when (presentation) {
            ExpansionPresentation.OFF -> {
                val surfaceChanged = popup != null || suggestionBarVisible
                popup?.dismiss()
                popup = null
                if (suggestionBarVisible) {
                    clearSuggestionBar()
                    suggestionBarVisible = false
                }
                if (surfaceChanged) requestSurfaceUpdate()
            }
            ExpansionPresentation.FLOATING_POPUP -> {
                if (suggestionBarVisible) {
                    clearSuggestionBar()
                    suggestionBarVisible = false
                    requestSurfaceUpdate()
                }
                val anchor = anchorProvider() ?: return
                val currentPopup = popup ?: TextExpansionPopup(
                    context = context,
                    onSelected = { commit(it, "") },
                    onDismissed = {
                        popup = null
                        activeConfig = null
                        activeQuery = null
                        activeMatches = emptyList()
                        selectedIndex = 0
                    }
                ).also { popup = it }
                if (currentPopup.isShowing()) currentPopup.update(anchor, activeMatches)
                else currentPopup.show(anchor, activeMatches)
            }
            ExpansionPresentation.SUGGESTION_BAR -> {
                popup?.dismiss()
                popup = null
                val labels = activeMatches.take(3).map { it.displayText }
                showSuggestionBar(labels) { label ->
                    activeMatches.firstOrNull { it.displayText == label }?.let { commit(it, "") }
                }
                suggestionBarVisible = true
                requestSurfaceUpdate()
            }
        }
    }

    private fun hasVisibleMatches(): Boolean =
        activeConfig?.presentation != ExpansionPresentation.OFF && activeMatches.isNotEmpty()

    private fun moveSelection(delta: Int): Boolean {
        if (activeMatches.isEmpty()) return false
        val lastVisibleIndex = when (activeConfig?.presentation) {
            ExpansionPresentation.SUGGESTION_BAR -> minOf(2, activeMatches.lastIndex)
            else -> activeMatches.lastIndex
        }
        selectedIndex = (selectedIndex + delta).coerceIn(0, lastVisibleIndex)
        popup?.moveSelection(delta)
        return true
    }
}
