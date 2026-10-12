package brobata.physiboard.ime.actions

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import brobata.physiboard.core.actions.emoji.CaptureResult
import brobata.physiboard.core.actions.emoji.EmojiCategories
import brobata.physiboard.core.actions.emoji.EmojiEntry
import brobata.physiboard.core.actions.emoji.EmojiSearchLocales
import brobata.physiboard.core.actions.emoji.EmojiPickerGeometry as G
import brobata.physiboard.core.actions.emoji.EmojiTabIcon
import brobata.physiboard.core.actions.emoji.PickerMode
import brobata.physiboard.core.actions.emoji.PickerModeGeometry as M
import brobata.physiboard.core.actions.emoji.PickerModes
import brobata.physiboard.core.actions.emoji.RecentEmojis
import brobata.physiboard.core.actions.emoji.RememberedSkinTones
import brobata.physiboard.core.actions.emoji.SearchCapture
import brobata.physiboard.core.actions.emoji.SearchFieldState
import brobata.physiboard.core.actions.emoji.SkinTone
import brobata.physiboard.core.actions.emoji.SkinTones
import brobata.physiboard.core.actions.kaomoji.KaomojiCatalog
import brobata.physiboard.core.actions.picker.UnicodeSymbols
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.design.DesignMotion
import brobata.physiboard.design.DesignTokens
import brobata.physiboard.design.PhysiFonts
import brobata.physiboard.design.R as DesignR
import brobata.physiboard.ime.skin.PanelSkin
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
 *
 * Speed (layers-sym-alt.md SS5.12): the view tree and its window are built once and kept, hidden,
 * between opens ([prepare] builds them ahead of the first one); only a theme, screen or height
 * change rebuilds them. The grid is a recycling list that makes views for the rows on screen
 * only: drawing all 1,900 emoji as views took 0.8 to 1.4 s of main thread after every open on the
 * emulator. A reopen with nothing changed (same recents, tones and default) draws nothing at all.
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

    /** One grid cell: what it shows and inserts, and what a long press does (null: nothing). [kind] picks its view type. */
    private class Cell(val text: String, val kind: Int, val onTap: () -> Unit, val onLongPress: ((View) -> Unit)?)

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
    private var root: View? = null
    private var grid: RecyclerView? = null
    private var gridLayout: GridLayoutManager? = null
    private var cells = CellAdapter()
    private var tabRow: LinearLayout? = null
    private var status: TextView? = null
    private var progress: ProgressBar? = null
    private var searchPanel: View? = null
    private var searchField: EditText? = null
    private var searchRow: View? = null
    private var skin: PanelSkin = PanelSkin(service, StripTheme.SLATE_DARK)
    private var emojiColumns = G.MIN_COLUMNS

    /** What the kept view tree was built for; anything else rebuilds it. */
    private data class BuildKey(val theme: StripTheme, val widthPx: Int, val density: Float, val expanded: Boolean, val roundedCorners: Boolean)
    private var builtFor: BuildKey? = null

    /** What the grid last drew in Emoji mode; a reopen that would draw the same draws nothing. */
    private var drawnSignature: List<Any?>? = null
    private var emojiLoading = false
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

    fun show(expanded: Boolean, theme: StripTheme, aboveBottomPx: Int, skinTone: SkinTone, listener: Listener, kaomojiEnabled: Boolean = false, enter: DesignMotion.Enter = DesignMotion.Enter.QUICK) {
        this.listener = listener
        this.theme = theme
        this.kaomojiEnabled = kaomojiEnabled
        if (panel.isShown) {
            // spec SS4.3: "a reopen straight after the same page merely scrolls to the top"
            if (lastOpenedPage4) scrollToTop()
            return
        }
        this.skinTone = skinTone
        val build = ensureBuilt(expanded)
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
        resetForOpen()
        // An emoji load that failed is tried again on the next open.
        if (data == null && emojiLoadFailed) emojiLoadFailed = false
        if (mode == PickerMode.EMOJI) renderEmojiIfChanged() else renderMode()
        scrollToTop()
        if (!panel.show(build, heightPx = panel.dp(G.heightDp(expanded)), bottomMarginPx = aboveBottomPx, enter = enter)) return
        loadEmoji()
        if (openSearch) toggleSearch()
    }

    /**
     * layers-sym-alt.md SS5.12: builds the page and adds its window, invisible, ahead of the first
     * open, with the emoji drawn into it, so that open is only a reveal. Quiet without the overlay
     * permission. Does nothing while the page is up.
     */
    fun prepare(expanded: Boolean, theme: StripTheme, skinTone: SkinTone, kaomojiEnabled: Boolean) {
        if (panel.isShown) return
        this.theme = theme
        this.skinTone = skinTone
        this.kaomojiEnabled = kaomojiEnabled
        val build = ensureBuilt(expanded)
        mode = PickerMode.EMOJI
        resetForOpen()
        renderEmojiIfChanged()
        // The window is added once the emoji are drawn into it, so its warm-up draw has them.
        if (data != null) panel.prepare(build, heightPx = panel.dp(G.heightDp(expanded))) else prepareWhenLoaded = true
        loadEmoji()
    }

    /** [prepare] ran before the emoji were loaded: add the window when they are. */
    private var prepareWhenLoaded = false

    /**
     * Takes the page off screen. The view tree and its window stay for the next open; [animate]
     * false (a page a chord took back, layers-sym-alt.md SS5.11) goes at once.
     */
    fun hide(animate: Boolean = true) {
        handler.removeCallbacks(searchRunnable)
        searchGeneration++
        variantPopup?.dismiss()
        variantPopup = null
        if (!panel.isShown) return
        panel.hide(animate, keep = true)
        setCapture(false)
        listener = null
    }

    /** Drops the kept page and its window: the keyboard service is going, or the page must be rebuilt. */
    fun release() {
        hide(animate = false)
        panel.release()
        root = null
        grid = null
        gridLayout = null
        tabRow = null
        status = null
        progress = null
        searchPanel = null
        searchField = null
        searchRow = null
        builtFor = null
        drawnSignature = null
    }

    /** Everything an open starts from (SS4.3, SS4.5): no search, no capture, nothing pending. */
    private fun resetForOpen() {
        captureOn = false
        searchState = SearchFieldState.EMPTY
        searching = false
        pendingRecentsRedraw = false
        deferRedrawUntilTabChange = false
        tabScrollInFlight = false
        searchPanel?.visibility = View.GONE
        searchField?.let { field -> if (field.text.isNotEmpty()) field.setText("") }
        setCapture(false)
    }

    private fun ensureBuilt(expanded: Boolean): View {
        val metrics = service.resources.displayMetrics
        val key = BuildKey(theme, metrics.widthPixels, metrics.density, expanded, BottomOverlay.roundedCornersEnabled)
        root?.let { built -> if (builtFor == key) return built }
        release()
        emojiColumns = G.columns(metrics.widthPixels, metrics.density.toDouble())
        val built = build()
        root = built
        builtFor = key
        return built
    }

    /** spec SS4.1: the categories are read once per process; the first open (or [prepare]) asks for them. */
    private fun loadEmoji() {
        if (emojiLoading) return
        val locales = service.resources.configuration.locales
        val tags = (0 until locales.size()).map { locales.get(it).toLanguageTag() }
        // spec SS4.2: the search index follows the system's languages; a change (the keyboard
        // service lives through it) loads the index for the new chain, from the assets' cache.
        if (data?.chain == EmojiSearchLocales.chain(tags)) return
        emojiLoading = true
        assets.loadAsync(tags) { loaded ->
            emojiLoading = false
            data = loaded
            baseByKey = loaded?.let { RememberedSkinTones.baseByKey(it.categories) } ?: emptyMap()
            emojiLoadFailed = loaded == null
            if (root == null || mode != PickerMode.EMOJI) return@loadAsync
            // A query typed while the emoji were loading had nothing to search yet: run it now.
            if (searching) runSearch() else renderEmojiIfChanged()
            if (prepareWhenLoaded && !panel.isShown) {
                val built = root
                val expanded = builtFor?.expanded
                if (built != null && expanded != null) panel.prepare(built, heightPx = panel.dp(G.heightDp(expanded)))
            }
            prepareWhenLoaded = false
        }
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
        val skin = PanelSkin(context, theme).also { skin = it }
        val root = FrameLayout(context).apply { background = skin.panelBackground() }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val adapter = CellAdapter().also { cells = it }
        val layout = GridLayoutManager(context, emojiColumns).also { gridLayout = it }
        layout.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (adapter.items.getOrNull(position)?.kind == KIND_BREAK) layout.spanCount else 1
        }.apply {
            isSpanIndexCacheEnabled = true
            isSpanGroupIndexCacheEnabled = true
        }
        grid = RecyclerView(context).apply {
            val pad = panel.dp(G.GRID_PADDING_DP)
            setPadding(pad, pad, pad, pad + panel.dp(G.GRID_EXTRA_BOTTOM_DP))
            clipToPadding = false
            layoutManager = layout
            this.adapter = adapter
            // Rows are redrawn in place; nothing slides around when the recents change.
            itemAnimator = null
            setHasFixedSize(true)
            recycledViewPool.setMaxRecycledViews(KIND_EMOJI, RECYCLED_CELLS)
            addItemDecoration(CellGaps(layout, panel.dp(G.CELL_GAP_DP)))
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    followScroll()
                    applyPendingRecentsRedrawIfNearTop()
                }
            })
        }
        val gridFrame = FrameLayout(context)
        gridFrame.addView(grid, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        status = skin.label("", DesignTokens.Type.BODY_SP, color = skin.mutedText).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        gridFrame.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        progress = ProgressBar(context).apply {
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(theme.accent)
        }
        gridFrame.addView(progress, FrameLayout.LayoutParams(panel.dp(32), panel.dp(32), Gravity.CENTER))
        searchPanel = buildSearchPanel().apply { visibility = View.GONE }
        gridFrame.addView(searchPanel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        column.addView(gridFrame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // The tab bar is its own strip of the pane: a hairline above it.
            background = skin.panelBackground()
            setPadding(panel.dp(2), panel.dp(1), panel.dp(2), 0)
        }
        column.addView(tabRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panel.dp(G.TAB_ROW_HEIGHT_DP)))
        root.addView(column, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return root
    }

    /** spec SS4.5: a single-line field (the mode's hint) inside a 6 dp padded panel over the bottom of the grid; it never asks for a system keyboard. Drawn as the skin's prompt, `$ search emoji_`. */
    private fun buildSearchPanel(): View {
        val context = panel.overlayContext
        val box = FrameLayout(context).apply {
            setPadding(panel.dp(6), panel.dp(6), panel.dp(6), panel.dp(6))
            background = skin.panelBackground()
        }
        val field = EditText(context).apply {
            setSingleLine()
            showSoftInputOnFocus = false
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { setCapture(!captureOn) }
        }
        searchField = field
        val row = skin.promptField(field, mode.searchHint)
        searchRow = row
        box.addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
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
        searchField?.hint = PanelSkin.promptHint(mode.searchHint)
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

    /** The recents list the grid last drew, so a tone change redraws the cells without reshuffling the recents (SS4.4). */
    private var drawnRecents: List<String> = emptyList()

    /** spec SS4.3, SS4.4, SS4.7: recents first, then the nine categories; each emoji in its remembered or default tone. */
    private fun renderEmoji(loaded: EmojiData, recentsList: List<String> = RememberedSkinTones.collapseRecents(loadRecents(PickerMode.EMOJI), baseByKey)) {
        drawnRecents = recentsList
        drawnSignature = emojiSignature()
        val recents = RecentEmojis.category(recentsList, loaded.categories)
        val all = listOfNotNull(recents) + loaded.categories
        renderSections(all.map { category -> Section(category.id, glyphFor(category.icon), category.entries.map(::emojiCell)) }, emojiColumns)
    }

    /** What an Emoji-mode drawing depends on. */
    private fun emojiSignature(): List<Any?> =
        listOf(data?.let { System.identityHashCode(it) }, prefs.getString(PickerMode.EMOJI.recentsKey, null), prefs.getString(RememberedSkinTones.PREF_KEY, null), skinTone, emojiColumns)

    /** spec SS4.3: a reopen straight after the same page draws nothing new when nothing changed. */
    private fun renderEmojiIfChanged() {
        if (mode != PickerMode.EMOJI) return
        if (data != null && !searching && drawnSignature == emojiSignature()) {
            selectedTab = sectionStarts.firstOrNull()?.first
            highlightTab()
            return
        }
        renderMode()
    }

    /** spec SS4.9: recents first, then the twelve mood groups. */
    private fun renderKaomoji() {
        drawnSignature = null
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
        drawnSignature = null
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
        setCells(cells, emojiColumns)
        sectionStarts = listOfNotNull(current?.let { it to 0 })
        selectedTab = current
        renderTabRow(tabs)
        highlightTab()
    }

    private fun renderSections(sections: List<Section>, columns: Int) {
        // Each section's first cell as a list position, which is what a tab scrolls to and what
        // the scroll follower reads back. spec SS4.3: "Category headers are 1 dp spacers, not
        // titles": a section starts on a row of its own after an empty full-width break.
        val items = ArrayList<Cell>(sections.sumOf { it.cells.size + 1 })
        val starts = ArrayList<Pair<String, Int>>()
        for (section in sections) {
            if (items.isNotEmpty()) items.add(BREAK)
            starts.add(section.id to items.size)
            items.addAll(section.cells)
        }
        setCells(items, columns)
        sectionStarts = starts
        renderTabs(sections)
        if (selectedTab == null || sections.none { it.id == selectedTab }) selectedTab = sections.firstOrNull()?.id
        highlightTab()
    }

    private fun clearGrid() {
        setCells(emptyList(), gridLayout?.spanCount ?: emojiColumns)
    }

    /** Hands the list its cells; only the rows on screen get views (SS4.3's grid, recycled). */
    private fun setCells(items: List<Cell>, columns: Int) {
        status?.visibility = View.GONE
        progress?.visibility = View.GONE
        val layout = gridLayout ?: return
        if (layout.spanCount != columns) layout.spanCount = columns
        layout.spanSizeLookup.invalidateSpanIndexCache()
        layout.spanSizeLookup.invalidateSpanGroupIndexCache()
        cells.items = items
        @Suppress("NotifyDataSetChanged") // the whole list is new: a different mode, section set or result list
        cells.notifyDataSetChanged()
    }

    private fun scrollToTop() {
        grid?.stopScroll()
        gridLayout?.scrollToPositionWithOffset(0, 0)
    }

    /** The cells' views, made for the rows on screen and reused as the grid scrolls. */
    private inner class CellAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<Cell> = emptyList()

        override fun getItemCount(): Int = items.size
        override fun getItemViewType(position: Int): Int = items[position].kind

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val context = parent.context
            if (viewType == KIND_BREAK) {
                return object : RecyclerView.ViewHolder(View(context).apply { layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, 0) }) {}
            }
            val view = TextView(context).apply {
                gravity = Gravity.CENTER
                background = skin.cellDrawable()
                when (viewType) {
                    KIND_KAOMOJI -> {
                        setTextColor(theme.textAndIcons)
                        typeface = skin.face(PhysiFonts.Face.SANS)
                        maxLines = 1
                        setAutoSizeTextTypeUniformWithConfiguration(M.KAOMOJI_MIN_SP, M.KAOMOJI_MAX_SP, 1, TypedValue.COMPLEX_UNIT_SP)
                    }
                    KIND_SYMBOL -> {
                        setTextColor(theme.textAndIcons)
                        typeface = skin.face(PhysiFonts.Face.SANS)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, M.SYMBOL_GLYPH_SP.toFloat())
                    }
                    else -> setTextSize(TypedValue.COMPLEX_UNIT_SP, G.GLYPH_SP.toFloat())
                }
                DesignMotion.pressable(this)
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, panel.dp(if (viewType == KIND_KAOMOJI) M.KAOMOJI_CELL_DP else G.CELL_DP))
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val view = holder.itemView as? TextView ?: return
            val cell = items[position]
            view.text = cell.text
            view.scaleX = 1f
            view.scaleY = 1f
            view.setOnClickListener { cell.onTap() }
            val longPress = cell.onLongPress
            if (longPress != null) {
                view.setOnLongClickListener { longPress(it); true }
            } else {
                view.setOnLongClickListener(null)
                view.isLongClickable = false
            }
        }
    }

    /**
     * spec SS4.3: 4 dp between cells and rows, none at the outer edges; each column gives up the
     * same share of the gaps, so every cell is the same width.
     */
    private class CellGaps(private val layout: GridLayoutManager, private val gap: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val params = view.layoutParams as? GridLayoutManager.LayoutParams
            val position = parent.getChildAdapterPosition(view)
            if (params == null || position == RecyclerView.NO_POSITION || params.spanSize != 1) {
                outRect.set(0, 0, 0, 0)
                return
            }
            val columns = layout.spanCount
            val column = params.spanIndex
            outRect.left = column * gap / columns
            outRect.right = gap - (column + 1) * gap / columns
            outRect.top = if (layout.spanSizeLookup.getSpanGroupIndex(position, columns) == 0) 0 else gap
            outRect.bottom = 0
        }
    }

    /**
     * spec SS4.4, SS4.7: an emoji cell shows and inserts the entry in the default tone (when the
     * device has that toned form); a tap remembers the untoned base in recents, so a recent
     * follows a later change of the default; a long press lists the base and every variant.
     */
    private fun emojiCell(entry: EmojiEntry): Cell {
        val shown = RememberedSkinTones.displayForm(entry.base, entry.variants, rememberedTones(), skinTone)
        return Cell(
            text = shown,
            kind = KIND_EMOJI,
            onTap = { chooseEmoji(shown, recentAs = entry.base) },
            onLongPress = if (entry.hasVariants) ({ anchor -> showVariants(anchor, entry) }) else null,
        )
    }

    /** spec SS4.8, SS4.9: a kaomoji or a symbol: inserted as plain text; a long press names it. */
    private fun textCell(mode: PickerMode, text: String): Cell = Cell(
        text = text,
        kind = if (mode == PickerMode.KAOMOJI) KIND_KAOMOJI else KIND_SYMBOL,
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
        row.addView(
            skin.iconButton(DesignR.drawable.pb_ic_search, "Search") { toggleSearch() }.apply { background = skin.cellDrawable() },
            LinearLayout.LayoutParams(panel.dp(G.SEARCH_TOGGLE_DP), LinearLayout.LayoutParams.MATCH_PARENT),
        )
        // spec SS4.3: no mode button while emoji is the only mode (kaomoji off, not in symbols).
        if (PickerModes.showsModeButton(mode, kaomojiEnabled)) {
            row.addView(
                tabButton(mode.buttonLabel, alwaysEnabled = true) { switchMode() }.apply {
                    background = skin.buttonDrawable()
                    typeface = skin.face(PhysiFonts.Face.MONO_MEDIUM)
                },
                LinearLayout.LayoutParams(panel.dp(M.MODE_BUTTON_DP), panel.dp(MODE_BUTTON_HEIGHT_DP)).apply { marginEnd = panel.dp(2) },
            )
        }
        for ((id, label) in tabs) {
            val button = tabButton(label, alwaysEnabled = false) { onTab(id) }
            button.tag = id
            row.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        if (tabs.isEmpty()) row.addView(View(panel.overlayContext), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        row.addView(skin.closeButton { listener?.onClose() }, LinearLayout.LayoutParams(panel.dp(PanelSkin.CLOSE_WIDTH_DP), panel.dp(PanelSkin.CLOSE_HEIGHT_DP)))
    }

    private fun tabButton(label: String, alwaysEnabled: Boolean, onClick: () -> Unit): TextView = TextView(panel.overlayContext).apply {
        text = label
        gravity = Gravity.CENTER
        maxLines = 1
        typeface = skin.face(PhysiFonts.Face.MONO_MEDIUM)
        setTextColor(theme.textAndIcons)
        setPadding(panel.dp(G.TAB_PADDING_DP), panel.dp(G.TAB_PADDING_DP), panel.dp(G.TAB_PADDING_DP), panel.dp(G.TAB_PADDING_DP))
        setAutoSizeTextTypeUniformWithConfiguration(TAB_MIN_SP, TAB_MAX_SP, 1, TypedValue.COMPLEX_UNIT_SP)
        background = skin.cellDrawable()
        setOnClickListener { if (!searching || alwaysEnabled) onClick() }
    }

    private fun highlightTab() {
        val row = tabRow ?: return
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i) as? TextView ?: continue
            val id = child.tag as? String ?: continue
            child.background = if (id == selectedTab && !searching) skin.selectedDrawable() else skin.cellDrawable()
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
            searchField?.hint = PanelSkin.promptHint(mode.searchHint)
            runSearch()
        } else {
            renderMode()
        }
        scrollToTop()
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
            scrollToTop()
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
        val layout = gridLayout ?: return
        val grid = grid ?: return
        selectedTab = id
        highlightTab()
        tabScrollInFlight = true
        val scroller = object : LinearSmoothScroller(grid.context) {
            override fun getVerticalSnapPreference(): Int = SNAP_TO_START
        }
        scroller.targetPosition = start
        layout.startSmoothScroll(scroller)
        handler.postDelayed({ tabScrollInFlight = false }, 300)
    }

    /** spec SS4.3: "The selected tab follows the first visible item while the user scrolls (not while a tab-tap scroll is in flight)". */
    private fun followScroll() {
        if (tabScrollInFlight || searching || mode == PickerMode.SYMBOLS) return
        val visibleIndex = gridLayout?.findFirstVisibleItemPosition()?.takeIf { it != RecyclerView.NO_POSITION } ?: return
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
    private fun chooseEmoji(inserted: String, recentAs: String, fromPopup: Boolean = false) {
        val learning = learningAllowed()
        val before = loadRecents(PickerMode.EMOJI)
        val after = RecentEmojis.afterChoice(before, recentAs, learning)
        if (after !== before) saveRecents(PickerMode.EMOJI, after)
        // spec SS4.7, "Remembered tones": a pick from the popup is this emoji's tone from now on;
        // the plain form forgets it. Nothing is written in private mode.
        val tonesBefore = rememberedTones()
        val tonesAfter = if (fromPopup) RememberedSkinTones.afterPick(tonesBefore, inserted, learning) else tonesBefore
        if (tonesAfter !== tonesBefore) saveRememberedTones(tonesAfter)
        listener?.onChosen(inserted)
        if (tonesAfter !== tonesBefore) redrawTones()
        if (after !== before) scheduleRecentsRedraw(EmojiCategories.RECENTS_ID)
    }

    /** The tones in `recent_emojis_prefs`, parsed again only when the stored row changed. */
    private var tonesRaw: String? = null
    private var tones: Map<String, String> = emptyMap()
    private var baseByKey: Map<String, String> = emptyMap()

    private fun rememberedTones(): Map<String, String> {
        val raw = prefs.getString(RememberedSkinTones.PREF_KEY, null)
        if (raw != tonesRaw) {
            tonesRaw = raw
            tones = RememberedSkinTones.decode(raw)
        }
        return tones
    }

    private fun saveRememberedTones(next: Map<String, String>) {
        val raw = RememberedSkinTones.encode(next)
        prefs.edit().putString(RememberedSkinTones.PREF_KEY, raw).apply()
        tonesRaw = raw
        tones = next
    }

    /**
     * A tone was just remembered or forgotten: the cells on screen show it at once. The recents
     * are drawn as they were, so nothing moves under the finger (their own redraw stays deferred).
     */
    private fun redrawTones() {
        val loaded = data ?: return
        if (mode != PickerMode.EMOJI || searching || root == null) return
        renderEmoji(loaded, drawnRecents)
        // Drawn with the old recents: the next open must not take this drawing as current.
        drawnSignature = null
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
        val y = grid?.computeVerticalScrollOffset() ?: return
        if (y <= panel.dp(G.CELL_DP)) {
            pendingRecentsRedraw = false
            renderMode()
        }
    }

    private var variantPopup: PopupWindow? = null

    /**
     * spec SS4.4, SS4.7: the skin-tone chooser, "a light popup above the cell... listing the base
     * then each variant at 24 sp with 12 dp by 8 dp padding". What is picked here is inserted as
     * picked, whatever the default tone, and becomes this emoji's remembered tone.
     */
    private fun showVariants(anchor: View, entry: EmojiEntry) {
        val context = panel.overlayContext
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            // A pane of its own over the grid: Keys fill, a Key-outline hairline, 6 dp corners.
            background = skin.rounded(theme.suggestion, theme.divider, DesignTokens.Radius.PANE)
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
                    background = skin.cellDrawable()
                    setOnClickListener {
                        popup.dismiss()
                        // The recent is the emoji itself, so it follows the tone remembered for it;
                        // with nothing remembered (private mode) it stays as picked.
                        val recentAs = if (SkinTones.toneKeyOf(emoji) != null && learningAllowed()) entry.base else emoji
                        chooseEmoji(emoji, recentAs = recentAs, fromPopup = true)
                    }
                },
            )
        }
        val content = android.widget.HorizontalScrollView(context).apply { addView(row) }
        // Not focusable: a popup that takes window focus took it from the app, and Android then
        // finished the app's text field ("gained focus without editor"), which closed the page and
        // the popup with it before a tone could be picked (2026-10-11, emulator). A touch outside
        // still dismisses it.
        popup = PopupWindow(content, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, false)
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
        skin.setPromptActive(searchRow, on)
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
        drawnSignature = null
        val columns = if (forMode == PickerMode.KAOMOJI) M.KAOMOJI_COLUMNS else emojiColumns
        setCells(cells.orEmpty(), columns)
        when {
            cells == null -> setStatus(failure)
            cells.isEmpty() -> setStatus(forMode.noResults)
        }
        sectionStarts = emptyList()
        highlightTab()
        scrollToTop()
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

    private companion object {
        const val TAG = "PhysiBoardEmojiPicker"
        const val RECENTS_FILE = "recent_emojis_prefs"
        const val RECENTS_GLYPH = "🕓"
        const val SYMBOL_RECENTS_ID = "SYMBOL_RECENTS"
        const val TAB_MIN_SP = 8
        const val TAB_MAX_SP = 14

        /** The mode button sits in the tab bar as a key, not a full-height block. */
        const val MODE_BUTTON_HEIGHT_DP = 32

        const val KIND_EMOJI = 0
        const val KIND_KAOMOJI = 1
        const val KIND_SYMBOL = 2
        const val KIND_BREAK = 3

        /** Emoji cells kept for reuse: a few screens' worth, so a tab jump makes almost no new views. */
        const val RECYCLED_CELLS = 120

        /** The row break that starts a section on a row of its own. */
        val BREAK = Cell("", KIND_BREAK, {}, null)
    }
}
