package brobata.physiboard.ime.actions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import brobata.physiboard.core.actions.emoji.CaptureResult
import brobata.physiboard.core.actions.emoji.EmojiCategories
import brobata.physiboard.core.actions.emoji.EmojiEntry
import brobata.physiboard.core.actions.emoji.EmojiPickerGeometry as G
import brobata.physiboard.core.actions.emoji.EmojiTabIcon
import brobata.physiboard.core.actions.emoji.PickerMode
import brobata.physiboard.core.actions.emoji.PickerModeGeometry as M
import brobata.physiboard.core.actions.emoji.PickerModes
import brobata.physiboard.core.actions.emoji.RecentEmojis
import brobata.physiboard.core.actions.emoji.SearchCapture
import brobata.physiboard.core.actions.emoji.SearchFieldState
import brobata.physiboard.core.actions.emoji.SkinTone
import brobata.physiboard.core.actions.emoji.SkinTones
import brobata.physiboard.core.actions.kaomoji.KaomojiCatalog
import brobata.physiboard.core.actions.picker.UnicodeSymbols
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.strip.StripTheme
import org.json.JSONArray

/**
 * The picker page, Sym page 4: emoji, kaomoji and Unicode symbols. spec:
 * expansion-clipboard-pickers-launcher.md SS4.3 (layout and the mode button), SS4.4 (choosing,
 * the skin-tone popup, recents), SS4.5 (search and the hardware-key capture), SS4.7 (the default
 * skin tone), SS4.8 (symbols), SS4.9 (kaomoji). The catalogues, availability, search scoring,
 * recents rule, tone rules and capture model are all `:core:actions`'; this class draws the grid,
 * owns the `recent_emojis_prefs` file, and turns the capture model's answers into the search
 * field's text. Every search runs on [EmojiAssets]' loader thread.
 *
 * Dropped with the soft keyboard (SS13): the keyboard-switcher button and the software-keyboard
 * height. The picker is a [BottomOverlay] of [G.heightDp] like the clipboard panel.
 */
internal class EmojiPickerController(
    private val service: InputMethodService,
    private val handler: Handler,
    private val assets: EmojiAssets,
    /** app-shell.md SS31: false in private mode or a field that asks for no learning; recents are then left as they are. */
    private val learningAllowed: () -> Boolean,
) {
    interface Listener {
        /** Commit [text] (an emoji, a kaomoji or a symbol) as finished text. */
        fun onChosen(text: String)
        fun onClose()
        /** The picker needs the layout's Shift-aware text for a captured key (SS4.5, T31). */
        fun layoutText(key: KeyId, uppercase: Boolean): String?
    }

    /** One grid cell: what it shows and inserts, and what a long press does (null: nothing). */
    private class Cell(val text: String, val onTap: () -> Unit, val onLongPress: ((View) -> Unit)?)

    /** One tab and the cells it jumps to. */
    private class Section(val id: String, val tabLabel: String, val cells: List<Cell>)

    private val panel = BottomOverlay(service, TAG)
    private val prefs = service.getSharedPreferences(RECENTS_FILE, Context.MODE_PRIVATE)
    private var listener: Listener? = null
    private var theme: StripTheme = StripTheme.SLATE_DARK
    private var skinTone: SkinTone = SkinTone.NONE
    private var data: EmojiData? = null
    private var emojiLoadFailed = false
    private var symbols: UnicodeSymbols.Catalog? = null
    private var symbolsLoading = false
    private var symbolsFailed = false
    private var symbolsTriedThisOpen = false
    private var grid: GridLayout? = null
    private var scroll: ScrollView? = null
    private var tabRow: LinearLayout? = null
    private var status: TextView? = null
    private var progress: ProgressBar? = null
    private var searchPanel: View? = null
    private var searchField: EditText? = null
    private var emojiColumns = G.MIN_COLUMNS
    private var lastOpenedPage4 = false
    private var searchState = SearchFieldState.EMPTY
    private val searchRunnable = Runnable { runSearch() }
    private var searchGeneration = 0

    /** spec SS4.3: what the page shows. Every open starts on emoji ([PickerModes.openingMode]); only the open itself may ask for another mode. */
    private var mode: PickerMode = PickerMode.EMOJI

    /** The mode the next open was asked for (the chooser's K or U, the Symbols page's search); used once. */
    private var requestedMode: PickerMode? = null

    /** Open the next show with the search field up and typing into it. */
    private var requestedSearch = false

    /** `emoji_picker_kaomoji`: whether kaomoji is a mode at all. */
    private var kaomojiEnabled = false

    /** spec SS4.8: the symbol group on screen (Symbols mode draws one group at a time). */
    private var symbolGroup: String? = null

    /** spec SS4.5: whether hardware keys go into the search field instead of the app. */
    var captureOn: Boolean = false
        private set

    val isShown: Boolean get() = panel.isShown

    fun show(expanded: Boolean, theme: StripTheme, aboveBottomPx: Int, skinTone: SkinTone, listener: Listener, kaomojiEnabled: Boolean = false) {
        this.listener = listener
        this.theme = theme
        this.kaomojiEnabled = kaomojiEnabled
        if (panel.isShown) {
            // spec SS4.3: "a reopen straight after the same page merely scrolls to the top"
            if (lastOpenedPage4) scroll?.scrollTo(0, 0)
            return
        }
        this.skinTone = skinTone
        // "it got rid of real emojis for kaomoji" (2026-10-07): the page used to reopen in whatever
        // mode the last visit ended in. It is the Emoji page: emoji, unless this open asked otherwise.
        mode = PickerModes.openingMode(requestedMode, kaomojiEnabled)
        requestedMode = null
        selectedTab = null
        symbolGroup = null
        val openSearch = requestedSearch
        requestedSearch = false
        symbolsTriedThisOpen = false
        lastOpenedPage4 = true
        captureOn = false
        searchState = SearchFieldState.EMPTY
        searching = false
        val metrics = service.resources.displayMetrics
        emojiColumns = G.columns(metrics.widthPixels, metrics.density.toDouble())
        val root = build()
        if (!panel.show(root, heightPx = panel.dp(G.heightDp(expanded)), bottomMarginPx = aboveBottomPx)) return
        progress?.visibility = View.VISIBLE
        val locales = service.resources.configuration.locales
        val tags = (0 until locales.size()).map { locales.get(it).toLanguageTag() }
        assets.loadAsync(tags) { loaded ->
            if (!panel.isShown) return@loadAsync
            data = loaded
            emojiLoadFailed = loaded == null
            if (mode != PickerMode.EMOJI) return@loadAsync
            // A query typed while the emoji were loading had nothing to search yet: run it now.
            if (searching) runSearch() else renderMode()
        }
        if (mode != PickerMode.EMOJI) renderMode()
        if (openSearch) toggleSearch()
    }

    fun hide() {
        handler.removeCallbacks(searchRunnable)
        searchGeneration++
        panel.hide()
        captureOn = false
        grid = null
        scroll = null
        tabRow = null
        status = null
        progress = null
        searchPanel = null
        searchField = null
        listener = null
        variantPopup?.dismiss()
        variantPopup = null
    }

    /** spec SS4.5: capture is dropped when the app's own caret moved between two captured keys, or when the session ends. */
    fun onAppSelectionChanged() {
        if (captureOn) setCapture(false)
    }

    /**
     * spec SS4.5's table: one hardware key while page 4 is open and capture is on. Returns true
     * when the key (and its matching release, which the caller must also swallow) is consumed.
     */
    fun onHardwareKey(event: KeyEvent, key: KeyId?): Boolean {
        if (!captureOn || key == null) return false
        if (event.action == KeyEvent.ACTION_UP) return key in consumedKeys.also { consumedKeys.remove(key) }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val shift = event.isShiftPressed
        val ctrl = event.isCtrlPressed
        val altOrMeta = event.isAltPressed || event.isMetaPressed
        val layoutText = if (ctrl) null else listener?.layoutText(key, shift)
        val eventChar = event.unicodeChar.takeIf { it != 0 }?.toChar()
        val result = SearchCapture.onKeyDown(searchState, key, ctrl, altOrMeta, layoutText, eventChar)
        searchState = result.state
        val consumed = when (result) {
            is CaptureResult.NotCaptured -> false
            is CaptureResult.Consumed -> {
                applySearchState()
                if (result.queryChanged) scheduleSearch()
                true
            }
            is CaptureResult.HandToField -> {
                searchField?.dispatchKeyEvent(event)
                syncStateFromField()
                true
            }
            is CaptureResult.CopyToClipboard -> {
                copyToClipboard(result.text)
                applySearchState()
                scheduleSearch()
                true
            }
            is CaptureResult.RequestPaste -> {
                pasteFromClipboard()
                true
            }
        }
        if (consumed) consumedKeys.add(key)
        return consumed
    }

    private val consumedKeys = HashSet<KeyId>()

    // -----------------------------------------------------------------------------------------
    // Building
    // -----------------------------------------------------------------------------------------

    private fun build(): View {
        val context = panel.overlayContext
        val root = FrameLayout(context).apply { setBackgroundColor(theme.background) }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        grid = GridLayout(context).apply {
            val pad = panel.dp(G.GRID_PADDING_DP)
            setPadding(pad, pad, pad, pad + panel.dp(G.GRID_EXTRA_BOTTOM_DP))
        }
        scroll = ScrollView(context).apply {
            addView(grid)
            viewTreeObserver.addOnScrollChangedListener {
                followScroll()
                applyPendingRecentsRedrawIfNearTop()
            }
        }
        val gridFrame = FrameLayout(context)
        gridFrame.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        status = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.textAndIcons)
            visibility = View.GONE
        }
        gridFrame.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        progress = ProgressBar(context).apply { visibility = View.GONE }
        gridFrame.addView(progress, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        searchPanel = buildSearchPanel().apply { visibility = View.GONE }
        gridFrame.addView(searchPanel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        column.addView(gridFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        tabRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        column.addView(tabRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panel.dp(G.TAB_ROW_HEIGHT_DP)))
        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    /** spec SS4.5: a single-line field (the mode's hint) inside a 6 dp padded panel over the bottom of the grid; it never asks for a system keyboard. */
    private fun buildSearchPanel(): View {
        val context = panel.overlayContext
        val box = FrameLayout(context).apply {
            setPadding(panel.dp(6), panel.dp(6), panel.dp(6), panel.dp(6))
            setBackgroundColor(theme.background)
        }
        searchField = EditText(context).apply {
            hint = mode.searchHint
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.textAndIcons)
            setHintTextColor(withAlpha(theme.textAndIcons, 128))
            setPadding(panel.dp(8), panel.dp(5), panel.dp(8), panel.dp(5))
            background = GradientDrawable().apply {
                setColor(theme.suggestion)
                cornerRadius = panel.dp(7).toFloat()
            }
            showSoftInputOnFocus = false
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { setCapture(!captureOn) }
        }
        box.addView(searchField, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        return box
    }

    // -----------------------------------------------------------------------------------------
    // Rendering, per mode
    // -----------------------------------------------------------------------------------------

    /** Each drawn section's id and the grid child index of its first cell. */
    private var sectionStarts: List<Pair<String, Int>> = emptyList()
    private var selectedTab: String? = null
    private var searching = false

    /** Draws the current mode's sections (not search results), or its loading or failed state. */
    private fun renderMode() {
        if (grid == null) return
        searchField?.hint = mode.searchHint
        progress?.visibility = View.GONE
        when (mode) {
            PickerMode.EMOJI -> {
                val loaded = data
                when {
                    loaded != null -> renderEmoji(loaded)
                    emojiLoadFailed -> renderFailed(G.LOAD_FAILED)
                    else -> renderLoading()
                }
            }
            PickerMode.KAOMOJI -> renderKaomoji()
            PickerMode.SYMBOLS -> {
                val catalog = symbols
                when {
                    catalog != null -> renderSymbols(catalog)
                    symbolsFailed && symbolsTriedThisOpen -> renderFailed(M.SYMBOLS_FAILED)
                    else -> {
                        // A failure is retried on the next open of the page, not on every redraw.
                        symbolsTriedThisOpen = true
                        renderLoading()
                        loadSymbols()
                    }
                }
            }
        }
    }

    private fun renderLoading() {
        clearGrid()
        renderTabs(emptyList())
        progress?.visibility = View.VISIBLE
    }

    private fun renderFailed(message: String) {
        clearGrid()
        renderTabs(emptyList())
        setStatus(message)
    }

    private fun loadSymbols() {
        if (symbolsLoading) return
        symbolsLoading = true
        assets.loadSymbolsAsync { catalog ->
            symbolsLoading = false
            symbols = catalog
            symbolsFailed = catalog == null
            if (panel.isShown && mode == PickerMode.SYMBOLS && !searching) renderMode()
        }
    }

    /** spec SS4.3, SS4.4, SS4.7: recents first, then the nine categories; each emoji shown in the default tone. */
    private fun renderEmoji(loaded: EmojiData) {
        val recents = RecentEmojis.category(loadRecents(PickerMode.EMOJI), loaded.categories)
        val all = listOfNotNull(recents) + loaded.categories
        renderSections(all.map { category -> Section(category.id, glyphFor(category.icon), category.entries.map(::emojiCell)) }, emojiColumns)
    }

    /** spec SS4.9: recents first, then the twelve mood groups, every kaomoji drawn at once. */
    private fun renderKaomoji() {
        val recents = loadRecents(PickerMode.KAOMOJI)
        val sections = ArrayList<Section>()
        if (recents.isNotEmpty()) sections.add(Section(KaomojiCatalog.RECENTS_ID, RECENTS_GLYPH, recents.map { textCell(PickerMode.KAOMOJI, it) }))
        for (group in KaomojiCatalog.groups) sections.add(Section(group.id, group.tabLabel, group.entries.map { textCell(PickerMode.KAOMOJI, it.text) }))
        renderSections(sections, M.KAOMOJI_COLUMNS)
    }

    /**
     * spec SS4.8: one group at a time (the largest run to hundreds of marks, too many to draw
     * all at once); a tab swaps the group. Recents, when any, are the first tab and the first
     * group shown.
     */
    private fun renderSymbols(catalog: UnicodeSymbols.Catalog) {
        val recents = loadRecents(PickerMode.SYMBOLS)
        val tabs = ArrayList<Pair<String, String>>()
        if (recents.isNotEmpty()) tabs.add(SYMBOL_RECENTS_ID to RECENTS_GLYPH)
        for (group in catalog.tabs) tabs.add(group.id to group.tabLabel)
        val current = symbolGroup?.takeIf { id -> tabs.any { it.first == id } } ?: tabs.firstOrNull()?.first
        symbolGroup = current
        val cells = if (current == SYMBOL_RECENTS_ID) {
            recents.map { textCell(PickerMode.SYMBOLS, it) }
        } else {
            catalog.symbolsOf(current.orEmpty()).map { textCell(PickerMode.SYMBOLS, it.text) }
        }
        clearGrid()
        val grid = grid ?: return
        cells.forEachIndexed { index, cell -> addCell(grid, cell, index, emojiColumns) }
        sectionStarts = listOfNotNull(current?.let { it to 0 })
        selectedTab = current
        renderTabRow(tabs)
        highlightTab()
    }

    private fun renderSections(sections: List<Section>, columns: Int) {
        clearGrid()
        val grid = grid ?: return
        var index = 0
        var children = 0
        // Each section's first cell as a child index of the grid (the spacer cells that end a
        // short row are grid positions, not children), which is what a tab scrolls to and what
        // the scroll follower reads back.
        val starts = ArrayList<Pair<String, Int>>()
        for (section in sections) {
            if (index > 0) {
                // spec SS4.3: "Category headers are 1 dp spacers, not titles"
                val remainder = index % columns
                if (remainder != 0) index += columns - remainder
            }
            starts.add(section.id to children)
            for (cell in section.cells) {
                addCell(grid, cell, index, columns)
                index++
                children++
            }
        }
        sectionStarts = starts
        renderTabs(sections)
        if (selectedTab == null || sections.none { it.id == selectedTab }) selectedTab = sections.firstOrNull()?.id
        highlightTab()
    }

    private fun clearGrid() {
        grid?.removeAllViews()
        status?.visibility = View.GONE
        progress?.visibility = View.GONE
    }

    private fun addCell(grid: GridLayout, cell: Cell, index: Int, columns: Int) {
        if (grid.columnCount != columns) grid.columnCount = columns
        val kaomoji = mode == PickerMode.KAOMOJI
        val view = TextView(panel.overlayContext).apply {
            text = cell.text
            gravity = Gravity.CENTER
            when {
                kaomoji -> {
                    setTextColor(theme.textAndIcons)
                    maxLines = 1
                    setAutoSizeTextTypeUniformWithConfiguration(M.KAOMOJI_MIN_SP, M.KAOMOJI_MAX_SP, 1, TypedValue.COMPLEX_UNIT_SP)
                }
                mode == PickerMode.SYMBOLS -> {
                    setTextColor(theme.textAndIcons)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, M.SYMBOL_GLYPH_SP.toFloat())
                }
                else -> setTextSize(TypedValue.COMPLEX_UNIT_SP, G.GLYPH_SP.toFloat())
            }
            setOnClickListener { cell.onTap() }
            cell.onLongPress?.let { action -> setOnLongClickListener { action(this); true } }
        }
        val gap = panel.dp(G.CELL_GAP_DP)
        val params = GridLayout.LayoutParams(GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f)).apply {
            width = 0
            height = panel.dp(if (kaomoji) M.KAOMOJI_CELL_DP else G.CELL_DP)
            setMargins(if (index % columns == 0) 0 else gap, if (index < columns) 0 else gap, 0, 0)
        }
        grid.addView(view, params)
    }

    /**
     * spec SS4.4, SS4.7: an emoji cell shows and inserts the entry in the default tone (when the
     * device has that toned form); a tap remembers the untoned base in recents, so a recent
     * follows a later change of the default; a long press lists the base and every variant.
     */
    private fun emojiCell(entry: EmojiEntry): Cell {
        val shown = displayForm(entry)
        return Cell(
            text = shown,
            onTap = { chooseEmoji(shown, recentAs = entry.base) },
            onLongPress = if (entry.hasVariants) ({ anchor -> showVariants(anchor, entry) }) else null,
        )
    }

    private fun displayForm(entry: EmojiEntry): String {
        val toned = SkinTones.withDefault(entry.base, skinTone)
        return if (toned == entry.base || toned in entry.variants) toned else entry.base
    }

    /** spec SS4.8, SS4.9: a kaomoji or a symbol: inserted as plain text; a long press names it. */
    private fun textCell(mode: PickerMode, text: String): Cell = Cell(
        text = text,
        onTap = { chooseText(mode, text) },
        onLongPress = { showName(mode, text) },
    )

    // -----------------------------------------------------------------------------------------
    // Tabs
    // -----------------------------------------------------------------------------------------

    private fun renderTabs(sections: List<Section>) = renderTabRow(sections.map { it.id to it.tabLabel })

    /** spec SS4.3: the search toggle, the mode button, one tab per section (recents first), then the close button. */
    private fun renderTabRow(tabs: List<Pair<String, String>>) {
        val row = tabRow ?: return
        row.removeAllViews()
        row.addView(tabButton("🔍", alwaysEnabled = true) { toggleSearch() }, LinearLayout.LayoutParams(panel.dp(G.SEARCH_TOGGLE_DP), LinearLayout.LayoutParams.MATCH_PARENT))
        // spec SS4.3: no mode button while emoji is the only mode (kaomoji off, not in symbols).
        if (PickerModes.showsModeButton(mode, kaomojiEnabled)) {
            row.addView(
                tabButton(mode.buttonLabel, alwaysEnabled = true) { switchMode() }.apply {
                    background = GradientDrawable().apply {
                        setColor(theme.button)
                        cornerRadius = panel.dp(6).toFloat()
                    }
                },
                LinearLayout.LayoutParams(panel.dp(M.MODE_BUTTON_DP), LinearLayout.LayoutParams.MATCH_PARENT),
            )
        }
        for ((id, label) in tabs) {
            val button = tabButton(label, alwaysEnabled = false) { onTab(id) }
            button.tag = id
            row.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        if (tabs.isEmpty()) row.addView(View(panel.overlayContext), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        row.addView(tabButton("✕", alwaysEnabled = true) { listener?.onClose() }.apply { background = closeBackground() }, LinearLayout.LayoutParams(panel.dp(36), panel.dp(32)))
    }

    private fun tabButton(label: String, alwaysEnabled: Boolean, onClick: () -> Unit): TextView = TextView(panel.overlayContext).apply {
        text = label
        gravity = Gravity.CENTER
        maxLines = 1
        setTextColor(theme.textAndIcons)
        setPadding(panel.dp(G.TAB_PADDING_DP), panel.dp(G.TAB_PADDING_DP), panel.dp(G.TAB_PADDING_DP), panel.dp(G.TAB_PADDING_DP))
        setAutoSizeTextTypeUniformWithConfiguration(TAB_MIN_SP, TAB_MAX_SP, 1, TypedValue.COMPLEX_UNIT_SP)
        setOnClickListener { if (!searching || alwaysEnabled) onClick() }
    }

    private fun closeBackground() = GradientDrawable().apply {
        setColor(theme.button)
        cornerRadius = panel.dp(6).toFloat()
    }

    private fun highlightTab() {
        val row = tabRow ?: return
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i) as? TextView ?: continue
            val id = child.tag as? String ?: continue
            child.background = if (id == selectedTab && !searching) {
                GradientDrawable().apply {
                    setColor(withAlpha(theme.accent, 100))
                    cornerRadius = panel.dp(6).toFloat()
                    setStroke(panel.dp(1), theme.divider)
                }
            } else {
                null
            }
            child.alpha = if (searching) 0.55f else 1f
        }
    }

    /**
     * layers-sym-alt.md SS5.10: the Sym page chooser opens the picker straight in [target] mode
     * (P Emoji, K Kaomoji, U Unicode symbols). Before [show] it only sets the mode the page opens
     * in; on an open page it switches like the mode button.
     */
    fun presetMode(target: PickerMode, withSearch: Boolean = false) {
        if (!panel.isShown) {
            requestedMode = target
            requestedSearch = withSearch
            return
        }
        val resolved = PickerModes.openingMode(target, kaomojiEnabled)
        if (mode != resolved) switchMode(resolved)
        if (withSearch && searchPanel?.visibility != View.VISIBLE) toggleSearch()
    }

    /** spec SS4.3: the mode button cycles Emoji, Kaomoji, Symbols; a query being typed is re-run in the new mode. */
    private fun switchMode(target: PickerMode = PickerModes.next(mode, kaomojiEnabled)) {
        mode = target
        selectedTab = null
        pendingRecentsRedraw = false
        deferRedrawUntilTabChange = false
        variantPopup?.dismiss()
        if (searchState.text.isNotBlank()) {
            searchField?.hint = mode.searchHint
            runSearch()
        } else {
            renderMode()
        }
        scroll?.scrollTo(0, 0)
    }

    private var tabScrollInFlight = false

    /**
     * spec SS4.3: "tapping a tab jumps to that category's first row"; the Recents tab also
     * refreshes the recents. In Symbols mode a tab swaps the group on screen instead (SS4.8).
     */
    private fun onTab(id: String) {
        if (mode == PickerMode.SYMBOLS) {
            symbolGroup = id
            pendingRecentsRedraw = false
            symbols?.let(::renderSymbols)
            scroll?.scrollTo(0, 0)
            return
        }
        val recentsId = if (mode == PickerMode.EMOJI) EmojiCategories.RECENTS_ID else KaomojiCatalog.RECENTS_ID
        if (id == recentsId) {
            pendingRecentsRedraw = false
            renderMode()
        } else if (pendingRecentsRedraw && deferRedrawUntilTabChange) {
            // spec SS4.4: "when the choice was made from the Recents section itself, only once
            // the user has moved to another tab" -- that move just happened.
            pendingRecentsRedraw = false
            deferRedrawUntilTabChange = false
            renderMode()
        }
        val start = sectionStarts.firstOrNull { it.first == id }?.second ?: return
        val grid = grid ?: return
        val cell = grid.getChildAt(start) ?: return
        selectedTab = id
        highlightTab()
        tabScrollInFlight = true
        scroll?.smoothScrollTo(0, cell.top)
        handler.postDelayed({ tabScrollInFlight = false }, 300)
    }

    /** spec SS4.3: "The selected tab follows the first visible item while the user scrolls (not while a tab-tap scroll is in flight)". */
    private fun followScroll() {
        if (tabScrollInFlight || searching || mode == PickerMode.SYMBOLS) return
        val grid = grid ?: return
        val y = scroll?.scrollY ?: return
        var visibleIndex = 0
        for (i in 0 until grid.childCount) {
            if (grid.getChildAt(i).bottom > y) {
                visibleIndex = i
                break
            }
        }
        val section = sectionStarts.lastOrNull { it.second <= visibleIndex }?.first ?: return
        if (section != selectedTab) {
            selectedTab = section
            highlightTab()
        }
    }

    private fun glyphFor(icon: EmojiTabIcon): String = when (icon) {
        EmojiTabIcon.SATISFIED_FACE -> "😀"
        EmojiTabIcon.PERSON -> "👤"
        EmojiTabIcon.PAW -> "🐾"
        EmojiTabIcon.FORK_AND_KNIFE -> "🍴"
        EmojiTabIcon.AIRPLANE -> "✈"
        EmojiTabIcon.FOOTBALL -> "⚽"
        EmojiTabIcon.LIGHT_BULB -> "💡"
        EmojiTabIcon.SYMBOLS -> "⁉"
        EmojiTabIcon.FLAG -> "🏳"
        EmojiTabIcon.CLOCK -> RECENTS_GLYPH
        EmojiTabIcon.FILE -> "📄"
    }

    // -----------------------------------------------------------------------------------------
    // Choosing. spec SS4.4.
    // -----------------------------------------------------------------------------------------

    /** spec SS4.4: whether a Recents rebuild is owed once the deferred-redraw condition is met. */
    private var pendingRecentsRedraw = false

    /** spec SS4.4: true when the pending rebuild above is the "choice came from Recents" case. */
    private var deferRedrawUntilTabChange = false

    /**
     * Inserts [inserted] and, unless private mode or the field forbids learning, remembers
     * [recentAs] in the Emoji recents.
     */
    private fun chooseEmoji(inserted: String, recentAs: String) {
        val before = loadRecents(PickerMode.EMOJI)
        val after = RecentEmojis.afterChoice(before, recentAs, learningAllowed())
        if (after !== before) saveRecents(PickerMode.EMOJI, after)
        listener?.onChosen(inserted)
        if (after !== before) scheduleRecentsRedraw(EmojiCategories.RECENTS_ID)
    }

    /** The kaomoji and symbol counterpart of [chooseEmoji], under the same private-mode gate. */
    private fun chooseText(mode: PickerMode, text: String) {
        val before = loadRecents(mode)
        val after = RecentEmojis.afterChoice(before, text, learningAllowed())
        if (after !== before) saveRecents(mode, after)
        listener?.onChosen(text)
        if (after !== before) scheduleRecentsRedraw(if (mode == PickerMode.KAOMOJI) KaomojiCatalog.RECENTS_ID else SYMBOL_RECENTS_ID)
    }

    /**
     * spec SS4.4: "The redraw is deferred: it is applied only when the grid is idle, and, when
     * the choice was made from the Recents section itself, only once the user has moved to
     * another tab, so the row being tapped does not shuffle under the finger; when made from
     * another section it waits until the grid is near the top." A choice from Recents waits for
     * [onTab] to see the user leave that tab; any other choice waits here for the grid to scroll
     * back near the top. In Symbols mode, where one group is drawn at a time, the next tab tap
     * redraws.
     */
    private fun scheduleRecentsRedraw(recentsId: String) {
        if (searching) return
        pendingRecentsRedraw = true
        deferRedrawUntilTabChange = selectedTab == recentsId || mode == PickerMode.SYMBOLS
        if (!deferRedrawUntilTabChange) applyPendingRecentsRedrawIfNearTop()
    }

    private fun applyPendingRecentsRedrawIfNearTop() {
        if (!pendingRecentsRedraw || deferRedrawUntilTabChange || searching) return
        val y = scroll?.scrollY ?: return
        if (y <= panel.dp(G.CELL_DP)) {
            pendingRecentsRedraw = false
            renderMode()
        }
    }

    private var variantPopup: PopupWindow? = null

    /**
     * spec SS4.4, SS4.7: the skin-tone chooser, "a light popup above the cell... listing the base
     * then each variant at 24 sp with 12 dp by 8 dp padding". What is picked here is inserted
     * and remembered as picked, whatever the default tone.
     */
    private fun showVariants(anchor: View, entry: EmojiEntry) {
        val context = panel.overlayContext
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.argb(0xEE, 255, 255, 255))
                cornerRadius = panel.dp(6).toFloat()
            }
            elevation = panel.dp(12).toFloat()
        }
        val untoned = SkinTones.apply(entry.base, SkinTone.NONE)
        val forms = (listOf(untoned, entry.base) + entry.variants).distinct()
        lateinit var popup: PopupWindow
        for (emoji in forms) {
            row.addView(
                TextView(context).apply {
                    text = emoji
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, G.VARIANT_POPUP_GLYPH_SP.toFloat())
                    setPadding(panel.dp(12), panel.dp(8), panel.dp(12), panel.dp(8))
                    setOnClickListener { popup.dismiss(); chooseEmoji(emoji, recentAs = emoji) }
                },
            )
        }
        val content = android.widget.HorizontalScrollView(context).apply { addView(row) }
        popup = PopupWindow(content, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val x = (anchor.width - row.measuredWidth) / 2
        variantPopup?.dismiss()
        variantPopup = popup
        popup.showAsDropDown(anchor, x, -anchor.height - row.measuredHeight)
    }

    /** spec SS4.8, SS4.9: a long press on a kaomoji or a symbol says what it is. */
    private fun showName(mode: PickerMode, text: String) {
        val name = when (mode) {
            PickerMode.KAOMOJI -> KaomojiCatalog.groups.firstNotNullOfOrNull { g -> g.entries.firstOrNull { it.text == text }?.name }
            else -> {
                val cp = text.codePointAt(0)
                val unicodeName = runCatching { Character.getName(cp) }.getOrNull()
                listOfNotNull(unicodeName?.lowercase(), "U+%04X".format(cp)).joinToString(" · ")
            }
        } ?: return
        runCatching { Toast.makeText(service, name, Toast.LENGTH_SHORT).show() }
    }

    private fun loadRecents(mode: PickerMode): List<String> = runCatching {
        val array = JSONArray(prefs.getString(mode.recentsKey, "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun saveRecents(mode: PickerMode, recents: List<String>) {
        prefs.edit().putString(mode.recentsKey, JSONArray(recents).toString()).apply()
    }

    // -----------------------------------------------------------------------------------------
    // Search. spec SS4.5.
    // -----------------------------------------------------------------------------------------

    private fun toggleSearch() {
        val panel = searchPanel ?: return
        val showing = panel.visibility == View.VISIBLE
        panel.visibility = if (showing) View.GONE else View.VISIBLE
        setCapture(!showing)
        if (showing) {
            searchState = SearchFieldState.EMPTY
            applySearchState()
            runSearch()
        }
    }

    /** spec SS4.5: "the field dims to alpha 0.75 when off and its caret hides". */
    private fun setCapture(on: Boolean) {
        captureOn = on
        searchField?.alpha = if (on) 1f else 0.75f
        searchField?.isCursorVisible = on
        if (on) searchField?.requestFocus()
    }

    private fun applySearchState() {
        val field = searchField ?: return
        if (field.text.toString() != searchState.text) field.setText(searchState.text)
        val low = searchState.selectionLow.coerceIn(0, field.text.length)
        val high = searchState.selectionHigh.coerceIn(0, field.text.length)
        runCatching { field.setSelection(low, high) }
    }

    private fun syncStateFromField() {
        val field = searchField ?: return
        searchState = SearchFieldState(field.text.toString(), field.selectionStart, field.selectionEnd)
        scheduleSearch()
    }

    private fun scheduleSearch() {
        handler.removeCallbacks(searchRunnable)
        handler.postDelayed(searchRunnable, G.SEARCH_DEBOUNCE_MS)
    }

    /**
     * spec SS4.5: a non-empty query switches the grid to a flat result list, dims the tabs; an
     * empty query restores the sections. The scoring runs on the loader thread; an answer to a
     * query that has since changed (or a page since closed) is dropped.
     */
    private fun runSearch() {
        val query = searchState.text.trim()
        val generation = ++searchGeneration
        if (query.isEmpty()) {
            searching = false
            renderMode()
            return
        }
        searching = true
        highlightTab()
        when (mode) {
            PickerMode.EMOJI -> {
                val index = data?.index
                if (index == null) {
                    if (emojiLoadFailed) showResults(generation, PickerMode.EMOJI, emptyList(), G.LOAD_FAILED)
                    return
                }
                assets.searchAsync({ index.search(query) }) { hits ->
                    showResults(generation, PickerMode.EMOJI, hits?.map { emojiCell(it.entry) }, G.LOAD_FAILED)
                }
            }
            PickerMode.KAOMOJI -> assets.searchAsync({ KaomojiCatalog.search(query) }) { hits ->
                showResults(generation, PickerMode.KAOMOJI, hits?.map { textCell(PickerMode.KAOMOJI, it.kaomoji.text) }, PickerMode.KAOMOJI.noResults)
            }
            PickerMode.SYMBOLS -> assets.searchAsync({ assets.symbolIndex().search(query) }) { hits ->
                showResults(generation, PickerMode.SYMBOLS, hits?.map { textCell(PickerMode.SYMBOLS, it.symbol.text) }, M.SYMBOLS_FAILED)
            }
        }
    }

    /** [cells] null means the search could not run (its index failed to build): [failure] is shown. */
    private fun showResults(generation: Int, forMode: PickerMode, cells: List<Cell>?, failure: String) {
        if (generation != searchGeneration || forMode != mode || !panel.isShown || !searching) return
        clearGrid()
        val grid = grid ?: return
        val columns = if (forMode == PickerMode.KAOMOJI) M.KAOMOJI_COLUMNS else emojiColumns
        when {
            cells == null -> setStatus(failure)
            cells.isEmpty() -> setStatus(forMode.noResults)
            else -> cells.forEachIndexed { index, cell -> addCell(grid, cell, index, columns) }
        }
        sectionStarts = emptyList()
        highlightTab()
        scroll?.scrollTo(0, 0)
    }

    private fun setStatus(text: String) {
        status?.text = text
        status?.visibility = View.VISIBLE
    }

    private fun copyToClipboard(text: String) {
        val manager = service.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        manager.setPrimaryClip(android.content.ClipData.newPlainText("search", text))
    }

    private fun pasteFromClipboard() {
        val manager = service.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        val text = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(service)?.toString() ?: return
        searchState = SearchCapture.insert(searchState, text)
        applySearchState()
        scheduleSearch()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val TAG = "PhysiBoardEmojiPicker"
        const val RECENTS_FILE = "recent_emojis_prefs"
        const val RECENTS_GLYPH = "🕓"
        const val SYMBOL_RECENTS_ID = "SYMBOL_RECENTS"
        const val TAB_MIN_SP = 8
        const val TAB_MAX_SP = 14
    }
}
