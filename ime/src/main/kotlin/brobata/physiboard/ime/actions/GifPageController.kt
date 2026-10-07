package brobata.physiboard.ime.actions

import android.graphics.Color
import android.graphics.drawable.AnimatedImageDrawable
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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import brobata.physiboard.core.actions.emoji.CaptureResult
import brobata.physiboard.core.actions.emoji.SearchCapture
import brobata.physiboard.core.actions.emoji.SearchFieldState
import brobata.physiboard.core.actions.gif.GifItem
import brobata.physiboard.core.actions.gif.GifPage
import brobata.physiboard.core.actions.gif.GifShelf
import brobata.physiboard.core.actions.gif.Klipy
import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.shell.FetchResult
import brobata.physiboard.core.shell.GatedFetcher
import brobata.physiboard.core.shell.NetworkPurpose
import brobata.physiboard.core.strip.StripTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The GIF page, Sym page 6. spec: layers-sym-alt.md SS4.5. A search field typed from the hardware
 * keys (the picker's capture model, `:core:actions`' [SearchCapture]), quick searches, a grid of
 * animated previews: ★ favourites and recently sent first, then KLIPY's trending list when the
 * query is empty, or KLIPY's results for it, in KLIPY's order. Every request goes through the
 * network gate; nothing here runs on the key path except the search field's own text edit.
 */
internal class GifPageController(
    private val service: InputMethodService,
    private val handler: Handler,
    private val apiKey: String,
    private val fetcher: () -> GatedFetcher?,
    private val previews: GifPreviews,
    private val store: GifShelfStore,
    /** app-shell.md SS31.1: false in private mode or a field that asks for no learning. */
    private val learningAllowed: () -> Boolean,
    /** app-shell.md SS31.2: the gate's sentence when the keyboard already knows nothing may go out (private mode, or the setting not read yet); null otherwise. */
    private val offlineReason: () -> String?,
) {
    interface Listener {
        fun onSend(item: GifItem)
        fun onClose()
        fun layoutText(key: KeyId, uppercase: Boolean): String?
    }

    private val panel = BottomOverlay(service, TAG)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listJob: Job? = null
    private var listener: Listener? = null
    private var theme: StripTheme = StripTheme.SLATE_DARK
    private var grid: GridLayout? = null
    private var scroll: ScrollView? = null
    private var status: TextView? = null
    private var progress: ProgressBar? = null
    private var searchField: EditText? = null
    private var columns = 3
    private var cellWidthPx = 0
    private var searchState = SearchFieldState.EMPTY
    private val searchRunnable = Runnable { startList(fetch = true) }
    private val consumedKeys = HashSet<KeyId>()

    /** What the grid is showing from KLIPY: the query it was for, the next page to ask for, and the grid position to append at. */
    private var listQuery = ""
    private var nextPage = 1
    private var hasNext = false
    private var loading = false
    private var gridPosition = 0

    /** spec SS4.5: whether hardware keys go into the search field instead of the app; on when the page opens. */
    var captureOn: Boolean = false
        private set

    val isShown: Boolean get() = panel.isShown

    /**
     * [loadAtOnce] false (the page is off for the cycle and was opened from the chooser): nothing
     * is asked of KLIPY until the user types, presses Enter or taps a quick search. True: trending
     * is asked for once the Sym double-tap window has passed, so a page a double tap only flashes
     * past makes no request.
     */
    fun show(theme: StripTheme, aboveBottomPx: Int, listener: Listener, loadAtOnce: Boolean) {
        this.listener = listener
        if (panel.isShown) return
        this.theme = theme
        searchState = SearchFieldState.EMPTY
        val metrics = service.resources.displayMetrics
        columns = GifPage.columns(metrics.widthPixels, metrics.density.toDouble())
        cellWidthPx = (metrics.widthPixels - panel.dp(16)) / columns
        if (!panel.show(build(), heightPx = panel.dp(GifPage.HEIGHT_DP), bottomMarginPx = aboveBottomPx)) return
        setCapture(true)
        if (loadAtOnce) {
            handler.postDelayed(searchRunnable, GifPage.FIRST_LOAD_DELAY_MS)
        } else {
            startList(fetch = false)
        }
    }

    fun hide() {
        if (!panel.isShown) {
            // Called after every keystroke while another page is open: nothing to tear down.
            listener = null
            return
        }
        handler.removeCallbacks(searchRunnable)
        listJob?.cancel()
        previews.cancelAll()
        panel.hide()
        captureOn = false
        consumedKeys.clear()
        grid = null
        scroll = null
        status = null
        progress = null
        searchField = null
        listener = null
    }

    /** spec SS4.5 (as the picker's SS4.5): the app's own caret moved, so capture drops. */
    fun onAppSelectionChanged() {
        if (captureOn) setCapture(false)
    }

    /**
     * One hardware key while the page is open and capture is on. Returns true when the key (and
     * its release) is consumed. Enter searches at once instead of waiting for the pause.
     */
    fun onHardwareKey(event: KeyEvent, key: KeyId?): Boolean {
        if (!captureOn || key == null) return false
        if (event.action == KeyEvent.ACTION_UP) return consumedKeys.remove(key)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val ctrl = event.isCtrlPressed
        val layoutText = if (ctrl) null else listener?.layoutText(key, event.isShiftPressed)
        val eventChar = event.unicodeChar.takeIf { it != 0 }?.toChar()
        val result = SearchCapture.onKeyDown(searchState, key, ctrl, event.isAltPressed || event.isMetaPressed, layoutText, eventChar)
        searchState = result.state
        val consumed = when (result) {
            is CaptureResult.NotCaptured -> false
            is CaptureResult.Consumed -> {
                applySearchState()
                if (key == KeyId.Control(ControlKey.ENTER) && event.repeatCount == 0) searchNow()
                else if (result.queryChanged) scheduleSearch()
                true
            }
            is CaptureResult.HandToField -> {
                searchField?.dispatchKeyEvent(event)
                searchField?.let { searchState = SearchFieldState(it.text.toString(), it.selectionStart, it.selectionEnd) }
                scheduleSearch()
                true
            }
            is CaptureResult.CopyToClipboard -> {
                (service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                    ?.setPrimaryClip(android.content.ClipData.newPlainText("search", result.text))
                applySearchState()
                scheduleSearch()
                true
            }
            is CaptureResult.RequestPaste -> {
                val manager = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                manager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(service)?.toString()?.let { text ->
                    searchState = SearchCapture.insert(searchState, text)
                    applySearchState()
                    scheduleSearch()
                }
                true
            }
        }
        if (consumed) consumedKeys.add(key)
        return consumed
    }

    // -----------------------------------------------------------------------------------------
    // Building
    // -----------------------------------------------------------------------------------------

    private fun build(): View {
        val context = panel.overlayContext
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(panel.dp(6), panel.dp(6), panel.dp(6), panel.dp(2))
        }
        searchField = EditText(context).apply {
            hint = GifPage.SEARCH_HINT
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(theme.textAndIcons)
            setHintTextColor(withAlpha(theme.textAndIcons, 128))
            setPadding(panel.dp(8), panel.dp(5), panel.dp(8), panel.dp(5))
            background = GradientDrawable().apply { setColor(theme.suggestion); cornerRadius = panel.dp(7).toFloat() }
            showSoftInputOnFocus = false
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { setCapture(!captureOn) }
        }
        top.addView(searchField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(
            button("✕") { listener?.onClose() },
            LinearLayout.LayoutParams(panel.dp(36), panel.dp(32)).apply { marginStart = panel.dp(6) },
        )
        column.addView(top)

        val chips = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(panel.dp(4), 0, panel.dp(4), 0) }
        for (quick in GifPage.QUICK_SEARCHES) {
            chips.addView(
                button(quick) { runQuickSearch(quick) }.apply { setPadding(panel.dp(12), 0, panel.dp(12), 0) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, panel.dp(28)).apply { setMargins(panel.dp(2), panel.dp(2), panel.dp(2), panel.dp(2)) },
            )
        }
        column.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        val frame = FrameLayout(context)
        grid = GridLayout(context).apply {
            columnCount = columns
            setPadding(panel.dp(8), panel.dp(4), panel.dp(8), panel.dp(8))
        }
        scroll = ScrollView(context).apply {
            addView(grid)
            viewTreeObserver.addOnScrollChangedListener { maybeLoadMore() }
        }
        frame.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        status = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(theme.textAndIcons)
            setPadding(panel.dp(16), 0, panel.dp(16), 0)
            visibility = View.GONE
        }
        frame.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        progress = ProgressBar(context).apply { visibility = View.GONE }
        frame.addView(progress, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        column.addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // KLIPY asks for its branding in the interface (docs.klipy.com, "Add Attribution").
        column.addView(
            TextView(context).apply {
                text = GifPage.ATTRIBUTION
                gravity = Gravity.END
                setTextColor(withAlpha(theme.textAndIcons, 170))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setPadding(panel.dp(8), 0, panel.dp(8), panel.dp(2))
            },
        )
        return column
    }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(panel.overlayContext).apply {
        text = label
        gravity = Gravity.CENTER
        maxLines = 1
        setTextColor(theme.textAndIcons)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        background = GradientDrawable().apply { setColor(theme.button); cornerRadius = panel.dp(6).toFloat() }
        setOnClickListener { onClick() }
    }

    // -----------------------------------------------------------------------------------------
    // Search field
    // -----------------------------------------------------------------------------------------

    private fun setCapture(on: Boolean) {
        captureOn = on
        searchField?.alpha = if (on) 1f else 0.75f
        searchField?.isCursorVisible = on
        if (on) searchField?.requestFocus()
    }

    private fun applySearchState() {
        val field = searchField ?: return
        if (field.text.toString() != searchState.text) field.setText(searchState.text)
        runCatching { field.setSelection(searchState.selectionLow.coerceIn(0, field.text.length), searchState.selectionHigh.coerceIn(0, field.text.length)) }
    }

    private fun scheduleSearch() {
        handler.removeCallbacks(searchRunnable)
        handler.postDelayed(searchRunnable, GifPage.SEARCH_DEBOUNCE_MS)
    }

    private fun searchNow() {
        handler.removeCallbacks(searchRunnable)
        startList(fetch = true)
    }

    private fun runQuickSearch(query: String) {
        searchState = SearchCapture.insert(SearchFieldState(searchState.text, 0, searchState.text.length), query)
        applySearchState()
        searchNow()
    }

    // -----------------------------------------------------------------------------------------
    // The grid
    // -----------------------------------------------------------------------------------------

    /** Starts the grid over for the query on screen: the local lists (when the query is empty), then page 1 from KLIPY when [fetch]. */
    private fun startList(fetch: Boolean) {
        val grid = grid ?: return
        listJob?.cancel()
        previews.cancelAll()
        grid.removeAllViews()
        scroll?.scrollTo(0, 0)
        gridPosition = 0
        listQuery = searchState.text.trim()
        nextPage = 1
        hasNext = false
        loading = false
        setStatus(null)
        if (listQuery.isEmpty()) {
            val favourites = store.favourites()
            val favouriteSlugs = favourites.mapTo(HashSet()) { it.slug }
            if (favourites.isNotEmpty()) addSection("★ Favourites", favourites, favouriteSlugs)
            val recents = store.recents()
            if (recents.isNotEmpty()) addSection("Recently sent", recents, favouriteSlugs)
        }
        if (fetch) loadPage() else setStatus(offlineReason() ?: if (apiKey.isBlank()) GifPage.NOT_SET_UP else GifPage.IDLE)
    }

    private fun loadPage() {
        val country = service.resources.configuration.locales.get(0)?.country
        val offline = offlineReason()
        val request = GifPage.request(apiKey, listQuery, nextPage, country, privateMode = offline != null, blockedReason = offline.orEmpty())
        if (request is GifPage.Request.Refused) {
            setStatus(request.message)
            return
        }
        val fetch = request as GifPage.Request.Fetch
        val gate = fetcher() ?: run { setStatus(GifPage.FAILED); return }
        loading = true
        progress?.visibility = View.VISIBLE
        val forQuery = listQuery
        val requestedPage = nextPage
        val firstPage = requestedPage == 1
        listJob = scope.launch {
            val result = gate.get(NetworkPurpose.GIF_SEARCH, fetch.url, Klipy.MAX_LIST_BYTES)
            if (!panel.isShown || forQuery != listQuery) return@launch
            loading = false
            progress?.visibility = View.GONE
            val outcome = when (result) {
                is FetchResult.Ok -> GifPage.outcome(result.body.decodeToString(), null, requestedPage)
                is FetchResult.Blocked -> GifPage.outcome(null, result.reason)
                is FetchResult.Failed -> GifPage.outcome(null, null)
            }
            when (outcome) {
                // A later page that fails ends "load more" for this list, rather than retrying on every scroll.
                is GifPage.Outcome.Message -> if (firstPage) setStatus(outcome.text) else hasNext = false
                is GifPage.Outcome.Results -> {
                    val favouriteSlugs = store.favourites().mapTo(HashSet()) { it.slug }
                    if (firstPage && listQuery.isEmpty()) addHeader("Trending")
                    outcome.page.items.forEach { addCell(it, favouriteSlugs) }
                    hasNext = outcome.page.hasNext
                    nextPage = outcome.page.page + 1
                }
            }
        }
    }

    /** spec SS4.5: the next page of the same list once the grid is scrolled near its end. */
    private fun maybeLoadMore() {
        if (loading || !hasNext) return
        val scroll = scroll ?: return
        val content = scroll.getChildAt(0) ?: return
        if (scroll.scrollY + scroll.height >= content.height - panel.dp(GifPage.CELL_DP) * 2) loadPage()
    }

    private fun addSection(title: String, items: List<GifItem>, favouriteSlugs: Set<String>) {
        addHeader(title)
        items.forEach { addCell(it, favouriteSlugs) }
    }

    private fun addHeader(title: String) {
        val grid = grid ?: return
        if (gridPosition % columns != 0) gridPosition += columns - gridPosition % columns
        val header = TextView(panel.overlayContext).apply {
            text = title
            setTextColor(theme.textAndIcons)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(panel.dp(2), panel.dp(6), 0, panel.dp(2))
        }
        val params = GridLayout.LayoutParams(GridLayout.spec(gridPosition / columns), GridLayout.spec(0, columns)).apply { width = GridLayout.LayoutParams.MATCH_PARENT }
        grid.addView(header, params)
        gridPosition += columns
    }

    private fun addCell(item: GifItem, favouriteSlugs: Set<String>) {
        val grid = grid ?: return
        val context = panel.overlayContext
        val cell = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(theme.suggestion); cornerRadius = panel.dp(6).toFloat() }
            clipToOutline = true
            contentDescription = item.title.ifBlank { "GIF" }
            setOnClickListener { listener?.onSend(item) }
        }
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        cell.addView(image, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val star = TextView(context).apply {
            text = "★"
            setTextColor(Color.rgb(0xFF, 0xC1, 0x07))
            setShadowLayer(3f, 0f, 0f, Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            visibility = if (item.slug in favouriteSlugs) View.VISIBLE else View.GONE
        }
        cell.addView(star, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, panel.dp(2), panel.dp(4), 0) })
        cell.setOnLongClickListener { toggleFavourite(item, star); true }
        val gap = panel.dp(3)
        val params = GridLayout.LayoutParams(GridLayout.spec(gridPosition / columns), GridLayout.spec(gridPosition % columns, 1f)).apply {
            width = 0
            height = panel.dp(GifPage.CELL_DP)
            setMargins(gap, gap, gap, gap)
        }
        grid.addView(cell, params)
        gridPosition++
        image.tag = item.previewUrl
        previews.load(item.previewUrl, cellWidthPx) { drawable ->
            if (image.tag != item.previewUrl || drawable == null) return@load
            image.setImageDrawable(drawable)
            (drawable as? AnimatedImageDrawable)?.start()
        }
    }

    /**
     * spec SS4.5: a long press stars or unstars a GIF; starring is refused while learning is off.
     * Only this cell's ★ changes now (redrawing the grid would ask KLIPY for the list again); the
     * Favourites section follows on the next open or search.
     */
    private fun toggleFavourite(item: GifItem, star: View) {
        val message = when (val change = GifShelf.toggleFavourite(store.favourites(), item, learningAllowed())) {
            is GifShelf.FavouriteChange.Added -> {
                store.saveFavourites(change.list)
                star.visibility = View.VISIBLE
                "Added to favourites"
            }
            is GifShelf.FavouriteChange.Removed -> {
                store.saveFavourites(change.list)
                star.visibility = View.GONE
                "Removed from favourites"
            }
            GifShelf.FavouriteChange.RefusedPrivate -> "Private: favourites are not changed"
        }
        runCatching { Toast.makeText(service, message, Toast.LENGTH_SHORT).show() }
    }

    /** Records a sent GIF in recents (not while learning is off). Called by the session once it went in. */
    fun onSent(item: GifItem) {
        val before = store.recents()
        val after = GifShelf.afterSend(before, item, learningAllowed())
        if (after !== before) store.saveRecents(after)
    }

    private fun setStatus(text: String?) {
        status?.text = text.orEmpty()
        status?.visibility = if (text == null) View.GONE else View.VISIBLE
        if (text != null) progress?.visibility = View.GONE
    }

    fun onServiceDestroyed() {
        hide()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val TAG = "PhysiBoardGifPage"
    }
}
