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
import brobata.physiboard.core.actions.emoji.CaptureResult
import brobata.physiboard.core.actions.emoji.EmojiCategories
import brobata.physiboard.core.actions.emoji.EmojiCategory
import brobata.physiboard.core.actions.emoji.EmojiEntry
import brobata.physiboard.core.actions.emoji.EmojiPickerGeometry as G
import brobata.physiboard.core.actions.emoji.EmojiTabIcon
import brobata.physiboard.core.actions.emoji.RecentEmojis
import brobata.physiboard.core.actions.emoji.SearchCapture
import brobata.physiboard.core.actions.emoji.SearchFieldState
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.strip.StripTheme
import org.json.JSONArray

/**
 * The emoji picker, Sym page 4. spec: expansion-clipboard-pickers-launcher.md SS4.3 (layout),
 * SS4.4 (choosing, the skin-tone popup, recents), SS4.5 (search and the hardware-key capture).
 * The catalogue, availability, search scoring, recents rule and capture model are all
 * `:core:actions`'; this class draws the grid, owns the `recent_emojis_prefs` file, and turns the
 * capture model's answers into the search field's text.
 *
 * Dropped with the soft keyboard (SS13): the keyboard-switcher button and the software-keyboard
 * height. The picker is a [BottomOverlay] of [G.heightDp] like the clipboard panel.
 */
internal class EmojiPickerController(
    private val service: InputMethodService,
    private val handler: Handler,
    private val assets: EmojiAssets,
) {
    interface Listener {
        fun onEmojiChosen(emoji: String)
        fun onClose()
        /** The picker needs the layout's Shift-aware text for a captured key (SS4.5, T31). */
        fun layoutText(key: KeyId, uppercase: Boolean): String?
    }

    private val panel = BottomOverlay(service, TAG)
    private val prefs = service.getSharedPreferences(RECENTS_FILE, Context.MODE_PRIVATE)
    private var listener: Listener? = null
    private var theme: StripTheme = StripTheme.SLATE_DARK
    private var data: EmojiData? = null
    private var grid: GridLayout? = null
    private var scroll: ScrollView? = null
    private var tabRow: LinearLayout? = null
    private var status: TextView? = null
    private var progress: ProgressBar? = null
    private var searchPanel: View? = null
    private var searchField: EditText? = null
    private var columns = G.MIN_COLUMNS
    private var lastOpenedPage4 = false
    private var searchState = SearchFieldState.EMPTY
    private val searchRunnable = Runnable { runSearch() }

    /** spec SS4.5: whether hardware keys go into the search field instead of the app. */
    var captureOn: Boolean = false
        private set

    val isShown: Boolean get() = panel.isShown

    fun show(expanded: Boolean, theme: StripTheme, aboveBottomPx: Int, listener: Listener) {
        this.listener = listener
        this.theme = theme
        if (panel.isShown) {
            // spec SS4.3: "a reopen straight after the same page merely scrolls to the top"
            if (lastOpenedPage4) scroll?.scrollTo(0, 0)
            return
        }
        lastOpenedPage4 = true
        captureOn = false
        searchState = SearchFieldState.EMPTY
        val metrics = service.resources.displayMetrics
        columns = G.columns(metrics.widthPixels, metrics.density.toDouble())
        val root = build()
        if (!panel.show(root, heightPx = panel.dp(G.heightDp(expanded)), bottomMarginPx = aboveBottomPx)) return
        progress?.visibility = View.VISIBLE
        val locales = service.resources.configuration.locales
        val tags = (0 until locales.size()).map { locales.get(it).toLanguageTag() }
        assets.loadAsync(tags) { loaded ->
            if (!panel.isShown) return@loadAsync
            progress?.visibility = View.GONE
            data = loaded
            if (loaded == null) setStatus(G.LOAD_FAILED) else renderSections(loaded)
        }
    }

    fun hide() {
        handler.removeCallbacks(searchRunnable)
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
    // Building and rendering
    // -----------------------------------------------------------------------------------------

    private fun build(): View {
        val context = panel.overlayContext
        val root = FrameLayout(context).apply { setBackgroundColor(theme.background) }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        grid = GridLayout(context).apply {
            columnCount = columns
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

    /** spec SS4.5: a single-line field ("Search emoji..." hint) inside a 6 dp padded panel over the bottom of the grid; it never asks for a system keyboard. */
    private fun buildSearchPanel(): View {
        val context = panel.overlayContext
        val box = FrameLayout(context).apply {
            setPadding(panel.dp(6), panel.dp(6), panel.dp(6), panel.dp(6))
            setBackgroundColor(theme.background)
        }
        searchField = EditText(context).apply {
            hint = G.SEARCH_HINT
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

    private var sections: List<Pair<EmojiCategory, Int>> = emptyList()
    private var selectedTab: String? = null
    private var searching = false

    private fun renderSections(loaded: EmojiData) {
        val recents = RecentEmojis.category(loadRecents(), loaded.categories)
        val all = listOfNotNull(recents) + loaded.categories
        val grid = grid ?: return
        grid.removeAllViews()
        status?.visibility = View.GONE
        sections = ArrayList()
        var index = 0
        val out = ArrayList<Pair<EmojiCategory, Int>>()
        for (category in all) {
            out.add(category to index)
            if (index > 0) {
                // spec SS4.3: "Category headers are 1 dp spacers, not titles"
                val remainder = index % columns
                if (remainder != 0) index += columns - remainder
            }
            for (entry in category.entries) {
                addCell(grid, entry, index)
                index++
            }
        }
        sections = out
        renderTabs(all)
        if (selectedTab == null) selectedTab = all.firstOrNull()?.id
        highlightTab()
    }

    private fun addCell(grid: GridLayout, entry: EmojiEntry, index: Int) {
        val cell = TextView(panel.overlayContext).apply {
            text = entry.base
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, G.GLYPH_SP.toFloat())
            setOnClickListener { choose(entry.base) }
            if (entry.hasVariants) setOnLongClickListener { showVariants(this, entry); true }
        }
        val gap = panel.dp(G.CELL_GAP_DP)
        val params = GridLayout.LayoutParams(GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f)).apply {
            width = 0
            height = panel.dp(G.CELL_DP)
            setMargins(if (index % columns == 0) 0 else gap, if (index < columns) 0 else gap, 0, 0)
        }
        grid.addView(cell, params)
    }

    /** spec SS4.3: search toggle, then one tab per category present (recents first), then the close button. */
    private fun renderTabs(categories: List<EmojiCategory>) {
        val row = tabRow ?: return
        row.removeAllViews()
        row.addView(tabButton("🔍") { toggleSearch() }, LinearLayout.LayoutParams(panel.dp(G.SEARCH_TOGGLE_DP), LinearLayout.LayoutParams.MATCH_PARENT))
        for (category in categories) {
            val button = tabButton(glyphFor(category.icon)) { jumpTo(category) }
            button.tag = category.id
            row.addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        row.addView(tabButton("✕") { listener?.onClose() }.apply { background = closeBackground() }, LinearLayout.LayoutParams(panel.dp(36), panel.dp(32)))
    }

    private fun tabButton(glyph: String, onClick: () -> Unit): TextView = TextView(panel.overlayContext).apply {
        text = glyph
        gravity = Gravity.CENTER
        setTextColor(theme.textAndIcons)
        setPadding(panel.dp(G.TAB_PADDING_DP), panel.dp(G.TAB_PADDING_DP), panel.dp(G.TAB_PADDING_DP), panel.dp(G.TAB_PADDING_DP))
        setOnClickListener { if (!searching || glyph == "🔍" || glyph == "✕") onClick() }
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
            child.background = if (id == selectedTab) {
                GradientDrawable().apply {
                    setColor(withAlpha(theme.accent, 100))
                    cornerRadius = panel.dp(6).toFloat()
                    setStroke(panel.dp(1), theme.divider)
                }
            } else {
                null
            }
        }
        row.alpha = if (searching) 0.55f else 1f
    }

    private var tabScrollInFlight = false

    /** spec SS4.3: "tapping a tab jumps to that category's first row"; the Recents tab also refreshes the recents. */
    private fun jumpTo(category: EmojiCategory) {
        if (category.id == EmojiCategories.RECENTS_ID) {
            data?.let { renderSections(it) }
            pendingRecentsRedraw = false
        } else if (pendingRecentsRedraw && deferRedrawUntilTabChange) {
            // spec SS4.4: "when the choice was made from the Recents section itself, only once
            // the user has moved to another tab" -- that move just happened.
            pendingRecentsRedraw = false
            deferRedrawUntilTabChange = false
            data?.let { renderSections(it) }
        }
        val start = sections.firstOrNull { it.first.id == category.id }?.second ?: return
        val grid = grid ?: return
        val cell = grid.getChildAt(start) ?: return
        selectedTab = category.id
        highlightTab()
        tabScrollInFlight = true
        scroll?.smoothScrollTo(0, cell.top)
        handler.postDelayed({ tabScrollInFlight = false }, 300)
    }

    /** spec SS4.3: "The selected tab follows the first visible item while the user scrolls (not while a tab-tap scroll is in flight)". */
    private fun followScroll() {
        if (tabScrollInFlight || searching) return
        val grid = grid ?: return
        val y = scroll?.scrollY ?: return
        var visibleIndex = 0
        for (i in 0 until grid.childCount) {
            if (grid.getChildAt(i).bottom > y) {
                visibleIndex = i
                break
            }
        }
        val section = sections.lastOrNull { it.second <= visibleIndex }?.first ?: return
        if (section.id != selectedTab) {
            selectedTab = section.id
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
        EmojiTabIcon.CLOCK -> "🕓"
        EmojiTabIcon.FILE -> "📄"
    }

    // -----------------------------------------------------------------------------------------
    // Choosing. spec SS4.4.
    // -----------------------------------------------------------------------------------------

    /** spec SS4.4: whether a Recents rebuild is owed once the deferred-redraw condition is met. */
    private var pendingRecentsRedraw = false

    /** spec SS4.4: true when the pending rebuild above is the "choice came from Recents" case. */
    private var deferRedrawUntilTabChange = false

    private fun choose(emoji: String) {
        saveRecents(RecentEmojis.add(loadRecents(), emoji))
        listener?.onEmojiChosen(emoji)
        scheduleRecentsRedraw()
    }

    /**
     * spec SS4.4: "The redraw is deferred: it is applied only when the grid is idle, and, when
     * the choice was made from the Recents section itself, only once the user has moved to
     * another tab, so the row being tapped does not shuffle under the finger; when made from
     * another section it waits until the grid is near the top." A choice from Recents waits for
     * [jumpTo] to see the user leave that tab; any other choice waits here for the grid to scroll
     * back near the top.
     */
    private fun scheduleRecentsRedraw() {
        pendingRecentsRedraw = true
        deferRedrawUntilTabChange = selectedTab == EmojiCategories.RECENTS_ID
        if (!deferRedrawUntilTabChange) applyPendingRecentsRedrawIfNearTop()
    }

    private fun applyPendingRecentsRedrawIfNearTop() {
        if (!pendingRecentsRedraw || deferRedrawUntilTabChange || searching) return
        val y = scroll?.scrollY ?: return
        if (y <= panel.dp(G.CELL_DP)) {
            pendingRecentsRedraw = false
            data?.let { renderSections(it) }
        }
    }

    /** spec SS4.4: the skin-tone chooser, "a light popup above the cell... listing the base then each variant at 24 sp with 12 dp by 8 dp padding". */
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
        lateinit var popup: PopupWindow
        for (emoji in listOf(entry.base) + entry.variants) {
            row.addView(
                TextView(context).apply {
                    text = emoji
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, G.VARIANT_POPUP_GLYPH_SP.toFloat())
                    setPadding(panel.dp(12), panel.dp(8), panel.dp(12), panel.dp(8))
                    setOnClickListener { popup.dismiss(); choose(emoji) }
                },
            )
        }
        popup = PopupWindow(row, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        row.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val x = (anchor.width - row.measuredWidth) / 2
        popup.showAsDropDown(anchor, x, -anchor.height - row.measuredHeight)
    }

    private fun loadRecents(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(RECENTS_KEY, "[]"))
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    private fun saveRecents(recents: List<String>) {
        prefs.edit().putString(RECENTS_KEY, JSONArray(recents).toString()).apply()
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

    /** spec SS4.5: a non-empty query switches the grid to a flat result list, dims the tabs; an empty query restores the sections. */
    private fun runSearch() {
        val loaded = data ?: return
        val query = searchState.text.trim()
        if (query.isEmpty()) {
            searching = false
            renderSections(loaded)
            return
        }
        searching = true
        val grid = grid ?: return
        grid.removeAllViews()
        val hits = loaded.index.search(query)
        status?.visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
        if (hits.isEmpty()) status?.text = G.NO_RESULTS
        hits.forEachIndexed { index, hit -> addCell(grid, hit.entry, index) }
        highlightTab()
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
        const val RECENTS_KEY = "recent_emojis"
    }
}
