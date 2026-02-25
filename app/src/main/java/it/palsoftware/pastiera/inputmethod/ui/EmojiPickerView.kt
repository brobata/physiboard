package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.TextView
import android.widget.PopupWindow
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.content.res.Configuration
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.data.emoji.EmojiRepository
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
import it.palsoftware.pastiera.data.emoji.EmojiSearchRepository
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Emoji picker view: single vertical list with section headers and bottom tabs.
 */
class EmojiPickerView(
    context: Context
) : FrameLayout(context) {

    private var currentInputConnection: InputConnection? = null
    private val recyclerView: RecyclerView
    private val searchField: EditText
    private val loadingView: ProgressBar
    private val emptyView: TextView
    private val tabScrollView: HorizontalScrollView
    private val tabRow: LinearLayout

    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadingJob: Job? = null

    private val fixedHeight = dpToPx(177f)
    private val emojiSize = dpToPx(48f)
    private val spacing = dpToPx(4f)
    private val smallPadding = dpToPx(8f)
    private val recentsApplyTopThreshold = 0

    // Data for sections
    private var sectionItems: List<SectionItem> = emptyList()
    private var itemCategoryIds: List<String> = emptyList()
    private var headerPositions: Map<String, Int> = emptyMap()
    private var selectedCategoryId: String? = null
    private var isTabClickScroll = false
    private var pendingRecentsRefresh = false
    private var pendingRecentsRefreshRequiresTop = false
    private var pendingRecentsRefreshRequiresNotRecents = false
    private var scrollState = RecyclerView.SCROLL_STATE_IDLE

    // Adapter
    private val sectionAdapter: SectionAdapter
    private val searchAdapter: SearchAdapter
    private val columns: Int
    private var regularCategories: List<EmojiRepository.EmojiCategory> = emptyList()
    private var searchIndex: EmojiSearchRepository.EmojiSearchIndex? = null
    private var searchQuery: String = ""
    private var searchJob: Job? = null
    private var isSearchMode: Boolean = false
    private var searchInputCaptureEnabled: Boolean = true
    private val selectedTabBackground = createTabBackground(true)
    private val unselectedTabBackground = createTabBackground(false)
    private var tabCategoryIds: List<String> = emptyList()

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(0, 0, 0, 0)

        // Calculate columns based on screen width
        val screenWidth = context.resources.displayMetrics.widthPixels
        val availableWidth = screenWidth - smallPadding * 2
        columns = ((availableWidth + spacing) / (emojiSize + spacing)).coerceAtLeast(4).coerceAtMost(10)

        // Layout container: vertical stack (recycler + bottom tabs)
        val vertical = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, fixedHeight)
        }

        searchField = EditText(context).apply {
            hint = context.getString(R.string.emoji_picker_search_placeholder)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(160, 255, 255, 255))
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundColor(Color.argb(30, 255, 255, 255))
            val padH = dpToPx(10f)
            val padV = dpToPx(6f)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(smallPadding, smallPadding, smallPadding, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                showSoftInputOnFocus = false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val newQuery = s?.toString().orEmpty()
                    if (newQuery == searchQuery) return
                    searchQuery = newQuery
                    scheduleSearch()
                }
            })
            setOnClickListener {
                setSearchInputCaptureEnabled(!searchInputCaptureEnabled)
            }
        }
        setSearchInputCaptureEnabled(true)

        // RecyclerView with headers and emoji grid
        recyclerView = RecyclerView(context).apply {
            overScrollMode = View.OVER_SCROLL_ALWAYS
            setHasFixedSize(false)
            clipToPadding = false
            setPadding(smallPadding, smallPadding, smallPadding, smallPadding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val gridLayoutManager = GridLayoutManager(context, columns, RecyclerView.VERTICAL, false)
        sectionAdapter = SectionAdapter(columns)
        searchAdapter = SearchAdapter()
        gridLayoutManager.spanSizeLookup = sectionAdapter.spanSizeLookup
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = sectionAdapter

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos == RecyclerView.NO_POSITION) return
                when (parent.adapter?.getItemViewType(pos) ?: return) {
                    VIEW_TYPE_HEADER -> {
                        outRect.set(0, spacing, 0, spacing)
                    }
                    VIEW_TYPE_EMOJI -> {
                        val layoutParams = view.layoutParams as? GridLayoutManager.LayoutParams
                        val column = layoutParams?.spanIndex ?: 0
                        outRect.left = if (column == 0) 0 else spacing / 2
                        outRect.right = if (column == columns - 1) 0 else spacing / 2
                        outRect.top = spacing / 2
                        outRect.bottom = spacing / 2
                    }
                }
            }
        })

        // Scroll listener to sync tabs and apply pending recents updates
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                scrollState = newState
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isTabClickScroll = false
                    maybeApplyPendingRecentsRefresh()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isSearchMode) return
                if (isTabClickScroll) return
                val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible == RecyclerView.NO_POSITION) return
                val categoryId = itemCategoryIds.getOrNull(firstVisible) ?: return
                if (categoryId != selectedCategoryId) {
                    selectedCategoryId = categoryId
                    updateTabsSelection()
                }
            }
        })

        // Loading and empty views (overlay)
        loadingView = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            visibility = View.VISIBLE
        }
        emptyView = TextView(context).apply {
            text = context.getString(R.string.emoji_picker_error)
            textSize = 14f
            setTextColor(Color.argb(128, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        // Tabs at bottom (above LEDs) - full width, no scroll
        val tabHeight = dpToPx(32f) // Height cap
        tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tabHeight
            )
            setPadding(smallPadding / 2, 0, smallPadding / 2, 0)
        }
        // Keep tabScrollView reference for compatibility but use it as a simple wrapper
        tabScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tabHeight
            )
        }
        tabScrollView.addView(tabRow)

        vertical.addView(searchField)
        vertical.addView(recyclerView)
        vertical.addView(tabScrollView)

        addView(vertical)
        addView(loadingView)
        addView(emptyView)

        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            fixedHeight
        )

        loadCategories()
    }

    fun setInputConnection(connection: InputConnection?) {
        currentInputConnection = connection
    }

    fun refresh() {
        loadCategories()
    }

    /**
     * IME hardware keys do not automatically target this EditText.
     * Handle printable keys manually while emoji picker page is open.
     */
    fun handleSearchKeyDown(event: KeyEvent): Boolean {
        if (!searchInputCaptureEnabled) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                val text = searchField.text ?: return true
                if (text.isEmpty()) return true
                text.delete(text.length - 1, text.length)
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                appendSearchText(" ")
                true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> true
            else -> {
                val unicode = event.unicodeChar
                if (unicode <= 0) return false
                val ch = unicode.toChar()
                if (Character.isISOControl(ch)) return false
                appendSearchText(ch.toString())
                true
            }
        }
    }

    fun shouldConsumeSearchKeyUp(event: KeyEvent): Boolean {
        if (!searchInputCaptureEnabled) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> true
            else -> {
                val unicode = event.unicodeChar
                unicode > 0 && !Character.isISOControl(unicode.toChar())
            }
        }
    }
    
    /**
     * Scrolls to the top of the emoji picker.
     * Recents updates are applied only when safe for UX.
     */
    fun scrollToTop() {
        recyclerView.post {
            recyclerView.scrollToPosition(0)
            // Don't force a refresh here to avoid UI jumps while scrolling.
        }
    }

    private fun loadCategories() {
        // Cancel any previous loading job to avoid race conditions
        loadingJob?.cancel()

        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        loadingJob = coroutineScope.launch {
            try {
                val recentCategory = withContext(Dispatchers.IO) { RecentEmojiManager.getRecentEmojiCategory(context) }
                val regularCategories = withContext(Dispatchers.IO) { EmojiRepository.getEmojiCategories(context) }
                val loadedSearchIndex = withContext(Dispatchers.IO) { EmojiSearchRepository.getSearchIndex(context) }
                this@EmojiPickerView.regularCategories = regularCategories
                this@EmojiPickerView.searchIndex = loadedSearchIndex

                val allCategories = mutableListOf<EmojiRepository.EmojiCategory>()
                if (recentCategory != null) allCategories.add(recentCategory)
                allCategories.addAll(regularCategories)

                // Always reset to first category when loading
                selectedCategoryId = allCategories.firstOrNull()?.id

                buildSections(allCategories)
                updateTabs(allCategories)

                loadingView.visibility = View.GONE
                if (allCategories.isEmpty()) {
                    emptyView.text = context.getString(R.string.emoji_picker_error)
                    emptyView.visibility = View.VISIBLE
                } else {
                    if (searchQuery.isNotBlank()) {
                        applySearchNow()
                    } else {
                        setSearchMode(false)
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        // Always start from top when opening emoji picker
                        recyclerView.scrollToPosition(0)
                    }
                }
            } catch (e: CancellationException) {
                throw e // Re-throw cancellation to properly cancel coroutine
            } catch (e: Exception) {
                loadingView.visibility = View.GONE
                emptyView.text = context.getString(R.string.emoji_picker_error)
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            kotlinx.coroutines.delay(120)
            applySearchNow()
        }
    }

    private fun appendSearchText(text: String) {
        if (text.isEmpty()) return
        val editable = searchField.text ?: return
        editable.append(text)
        searchField.setSelection(editable.length)
    }

    fun disableSearchInputCapture() {
        setSearchInputCaptureEnabled(false)
    }

    private fun setSearchInputCaptureEnabled(enabled: Boolean) {
        searchInputCaptureEnabled = enabled
        searchField.isCursorVisible = enabled
        searchField.alpha = if (enabled) 1f else 0.75f
        if (enabled) {
            val editable = searchField.text
            if (editable != null) {
                searchField.setSelection(editable.length)
            }
        } else {
            searchField.clearFocus()
        }
    }

    private fun applySearchNow() {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            setSearchMode(false)
            emptyView.text = context.getString(R.string.emoji_picker_error)
            emptyView.visibility = View.GONE
            recyclerView.visibility = if (sectionAdapter.itemCount > 0) View.VISIBLE else View.GONE
            return
        }

        val index = searchIndex
        if (index == null) {
            setSearchMode(true)
            emptyView.text = context.getString(R.string.emoji_picker_error)
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        val results = EmojiSearchRepository.search(index, query)
        setSearchMode(true)
        searchAdapter.submitList(results)
        if (results.isEmpty()) {
            emptyView.text = context.getString(R.string.emoji_picker_no_results)
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.scrollToPosition(0)
        }
    }

    private fun setSearchMode(enabled: Boolean) {
        if (isSearchMode == enabled) {
            // Ensure adapter is set correctly if external code changed it during refresh.
            val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
            if (enabled && recyclerView.adapter !== searchAdapter) {
                recyclerView.adapter = searchAdapter
                lm.spanSizeLookup = searchAdapter.spanSizeLookup
            } else if (!enabled && recyclerView.adapter !== sectionAdapter) {
                recyclerView.adapter = sectionAdapter
                lm.spanSizeLookup = sectionAdapter.spanSizeLookup
            }
            tabRow.alpha = if (enabled) 0.55f else 1f
            return
        }

        isSearchMode = enabled
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        if (enabled) {
            recyclerView.adapter = searchAdapter
            lm.spanSizeLookup = searchAdapter.spanSizeLookup
        } else {
            recyclerView.adapter = sectionAdapter
            lm.spanSizeLookup = sectionAdapter.spanSizeLookup
            updateTabsSelection()
        }
        tabRow.alpha = if (enabled) 0.55f else 1f
    }

    private fun buildSections(categories: List<EmojiRepository.EmojiCategory>) {
        val items = mutableListOf<SectionItem>()
        val categoryIds = ArrayList<String>()

        categories.forEach { category ->
            val title = category.displayNameRes?.let { context.getString(it) } ?: category.id
            items.add(SectionItem.Header(category.id, title))
            categoryIds.add(category.id)
            category.emojis.forEach { emojiEntry ->
                items.add(SectionItem.Emoji(category.id, emojiEntry))
                categoryIds.add(category.id)
            }
        }

        rebuildIndexCaches(items, categoryIds)
        sectionAdapter.submitList(items)
    }

    private fun updateTabs(categories: List<EmojiRepository.EmojiCategory>) {
        tabRow.removeAllViews()
        tabCategoryIds = categories.map { it.id }
        if (selectedCategoryId !in tabCategoryIds) {
            selectedCategoryId = tabCategoryIds.firstOrNull()
        }
        val tabHeight = dpToPx(32f)
        categories.forEach { category ->
            val iconRes = EmojiRepository.getCategoryIconRes(category.id)
            val label = category.displayNameRes?.let { context.getString(it) } ?: category.id
            val isSelected = category.id == selectedCategoryId
            val btn = ImageView(context).apply {
                setImageResource(iconRes)
                contentDescription = label
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(Color.WHITE)
                background = if (isSelected) selectedTabBackground else unselectedTabBackground
                // Icon always visible (alpha 1), background changes
                val pad = dpToPx(4f) // Minimal padding
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    0, // Use weight
                    tabHeight,
                    1f // Equal weight for all tabs
                )
                setOnClickListener {
                    if (isSearchMode) return@setOnClickListener
                    selectedCategoryId = category.id
                    updateTabsSelection()
                    isTabClickScroll = true
                    
                    // Recents is always at position 0 when present
                    if (category.id == EmojiRepository.RECENTS_CATEGORY_ID) {
                        (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
                        recyclerView.post {
                            requestRecentsRefresh(requireTop = true, requireNotRecents = false)
                        }
                    } else {
                        val headerPos = headerPositions[category.id] ?: return@setOnClickListener
                        (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(headerPos, 0)
                    }
                }
            }
            tabRow.addView(btn)
        }
        updateTabsSelection()
    }

    private fun updateTabsSelection() {
        for (i in 0 until tabRow.childCount) {
            val view = tabRow.getChildAt(i) as? ImageView ?: continue
            val categoryId = tabCategoryIds.getOrNull(i)
            val isSelected = categoryId == selectedCategoryId
            // Icon always visible, only background changes
            view.background = if (isSelected) selectedTabBackground else unselectedTabBackground
        }
    }

    private fun onEmojiSelected(emoji: String, categoryId: String) {
        currentInputConnection?.commitText(emoji, 1)
        // Save to storage and refresh recents when safe for UX.
        val requiresNotRecents = categoryId == EmojiRepository.RECENTS_CATEGORY_ID
        coroutineScope.launch(Dispatchers.IO) {
            val changed = RecentEmojiManager.addRecentEmoji(
                context,
                emoji,
                moveToTopWhenExists = true
            )
            if (changed) {
                withContext(Dispatchers.Main) {
                    requestRecentsRefresh(requireTop = !requiresNotRecents, requireNotRecents = requiresNotRecents)
                }
            }
        }
    }

    /**
     * Simple refresh of recents from storage.
     * Applies updates only when safe for UX.
     * Compares stored vs displayed recents and updates only if different.
     */
    private fun refreshRecentsFromStorage(allowInsertOrRemove: Boolean) {
        coroutineScope.launch {
            val recentCategory = withContext(Dispatchers.IO) {
                RecentEmojiManager.getRecentEmojiCategory(context)
            }

            val recentsHeaderIndex = headerPositions[EmojiRepository.RECENTS_CATEGORY_ID]

            // Case 1: Recents in storage but not displayed -> full reload
            if (recentsHeaderIndex == null && recentCategory != null) {
                if (!allowInsertOrRemove) {
                    markRecentsRefreshPending(requireTop = true, requireNotRecents = false)
                    return@launch
                }
                val anchor = captureScrollAnchor()
                val newRecentsItems = buildRecentsItems(recentCategory)
                val newItems = newRecentsItems + sectionItems
                rebuildIndexCaches(newItems)
                sectionAdapter.submitList(newItems) {
                    anchor?.let { restoreScrollAnchor(it, newRecentsItems.size) }
                }
                updateTabs(buildAllCategories(recentCategory))
                return@launch
            }

            // Case 2: No recents in storage but displayed -> full reload
            if (recentsHeaderIndex != null && recentCategory == null) {
                if (!allowInsertOrRemove) {
                    markRecentsRefreshPending(requireTop = true, requireNotRecents = false)
                    return@launch
                }
                val anchor = captureScrollAnchor()
                val nextHeaderIndex = sectionItems.withIndex()
                    .drop(recentsHeaderIndex + 1)
                    .firstOrNull { (_, item) -> item is SectionItem.Header }?.index
                    ?: sectionItems.size
                val removedCount = nextHeaderIndex - recentsHeaderIndex
                val newItems = sectionItems.toMutableList()
                repeat(removedCount) {
                    newItems.removeAt(recentsHeaderIndex)
                }
                rebuildIndexCaches(newItems)
                if (selectedCategoryId == EmojiRepository.RECENTS_CATEGORY_ID) {
                    selectedCategoryId = itemCategoryIds.firstOrNull()
                }
                sectionAdapter.submitList(newItems) {
                    anchor?.let { restoreScrollAnchor(it, -removedCount) }
                }
                updateTabs(buildAllCategories(null))
                return@launch
            }

            // Case 3: Both exist -> compare and update if different
            if (recentsHeaderIndex != null && recentCategory != null) {
                val nextHeaderIndex = sectionItems.withIndex()
                    .drop(recentsHeaderIndex + 1)
                    .firstOrNull { (_, item) -> item is SectionItem.Header }?.index
                    ?: sectionItems.size

                val displayedRecents = sectionItems
                    .subList(recentsHeaderIndex + 1, nextHeaderIndex)
                    .filterIsInstance<SectionItem.Emoji>()
                    .map { it.entry.base }

                val storedRecents = recentCategory.emojis.map { it.base }

                // Only update if different
                if (displayedRecents != storedRecents) {
                    val newRecentsItems = buildRecentsItems(recentCategory)

                    val newItems = sectionItems.toMutableList()
                    for (i in recentsHeaderIndex until nextHeaderIndex) {
                        newItems.removeAt(recentsHeaderIndex)
                    }
                    newItems.addAll(recentsHeaderIndex, newRecentsItems)

                    rebuildIndexCaches(newItems)
                    val anchor = if (isAtAbsoluteTop()) null else captureScrollAnchor()
                    sectionAdapter.submitList(newItems) {
                        anchor?.let { restoreScrollAnchor(it, 0) }
                    }
                }
            }
        }
    }

    /**
     * Updates tabs asynchronously when recents section is added/removed.
     */
    private fun updateTabsAsync() {
        coroutineScope.launch {
            val recentCategory = withContext(Dispatchers.IO) {
                RecentEmojiManager.getRecentEmojiCategory(context)
            }
            val regularCategories = withContext(Dispatchers.IO) {
                EmojiRepository.getEmojiCategories(context)
            }

            val allCategories = mutableListOf<EmojiRepository.EmojiCategory>()
            if (recentCategory != null) allCategories.add(recentCategory)
            allCategories.addAll(regularCategories)

            updateTabs(allCategories)
        }
    }

    private fun createTabBackground(isSelected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            // Selected: visible background, not selected: transparent
            val color = if (isSelected) Color.argb(100, 255, 255, 255) else Color.argb(0, 255, 255, 255)
            setColor(color)
            cornerRadius = dpToPx(6f).toFloat()
        }
    }

    private fun showVariantsPopup(anchor: View, entry: EmojiRepository.EmojiEntry, categoryId: String) {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val horizontalPadding = (16 * density).toInt()
        val verticalPadding = (12 * density).toInt()
        val itemHorizontalPadding = (12 * density).toInt()
        val itemVerticalPadding = (8 * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            gravity = Gravity.CENTER
        }

        var popup: PopupWindow? = null
        val options = listOf(entry.base) + entry.variants
        options.forEach { emoji ->
            val textView = TextView(context).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(itemHorizontalPadding, itemVerticalPadding, itemHorizontalPadding, itemVerticalPadding)
                val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                val isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
                setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
            }
            textView.setOnClickListener {
                onEmojiSelected(emoji, categoryId)
                popup?.dismiss()
            }
            container.addView(textView)
        }

        container.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        popup = PopupWindow(
            container,
            WRAP_CONTENT,
            WRAP_CONTENT,
            false // Don't take focus to avoid closing emoji picker
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.parseColor("#EEFFFFFF")))
            isOutsideTouchable = true
            isFocusable = false
            elevation = 12f
        }

        // Position popup above the anchor
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val windowWidth = context.resources.displayMetrics.widthPixels
        val popupWidth = container.measuredWidth
        val popupHeight = container.measuredHeight
        val anchorX = location[0]
        val anchorY = location[1]
        val desiredX = anchorX + (anchor.width - popupWidth) / 2
        val clampedX = desiredX.coerceIn(0, windowWidth - popupWidth)
        val xOffset = clampedX - anchorX
        val desiredYOffset = -(popupHeight + anchor.height)
        val minYOffset = -(anchorY + anchor.height)
        val yOffset = maxOf(desiredYOffset, minYOffset)
        popup.showAsDropDown(anchor, xOffset, yOffset)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Recreate coroutine scope if it was cancelled
        if (!coroutineScope.isActive) {
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }

    private inner class SectionAdapter(private val columns: Int) :
        ListAdapter<SectionItem, RecyclerView.ViewHolder>(SectionItemDiffCallback()) {
        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (getItemViewType(position)) {
                    VIEW_TYPE_HEADER -> columns
                    else -> 1
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is SectionItem.Header -> VIEW_TYPE_HEADER
                is SectionItem.Emoji -> VIEW_TYPE_EMOJI
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                // Minimal spacer between categories (no text, just 1dp height)
                val spacer = View(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(1f)
                    )
                }
                HeaderViewHolder(spacer)
            } else {
                val tv = TextView(parent.context).apply {
                    gravity = Gravity.CENTER
                    textSize = 28.8f
                    minHeight = emojiSize
                    minWidth = emojiSize
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                EmojiViewHolder(tv)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is SectionItem.Header -> {
                    // Nothing to bind - it's just a spacer
                }
                is SectionItem.Emoji -> {
                    (holder as EmojiViewHolder).textView.text = item.entry.base
                    val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    val isDarkTheme = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    holder.textView.setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
                    holder.textView.setOnClickListener {
                        onEmojiSelected(item.entry.base, item.categoryId)
                    }
                    holder.textView.setOnLongClickListener {
                        if (item.entry.variants.isEmpty()) return@setOnLongClickListener false
                        showVariantsPopup(holder.textView, item.entry, item.categoryId)
                        true
                    }
                }
            }
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)
    private class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    private class SearchEmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private inner class SearchAdapter :
        ListAdapter<EmojiSearchRepository.EmojiSearchResult, SearchEmojiViewHolder>(SearchResultDiffCallback()) {
        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchEmojiViewHolder {
            val tv = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                textSize = 28.8f
                minHeight = emojiSize
                minWidth = emojiSize
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return SearchEmojiViewHolder(tv)
        }

        override fun onBindViewHolder(holder: SearchEmojiViewHolder, position: Int) {
            val item = getItem(position)
            holder.textView.text = item.entry.base
            val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val isDarkTheme = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
            holder.textView.setTextColor(if (isDarkTheme) Color.WHITE else Color.BLACK)
            holder.textView.setOnClickListener {
                onEmojiSelected(item.entry.base, item.categoryId)
            }
            holder.textView.setOnLongClickListener {
                if (item.entry.variants.isEmpty()) return@setOnLongClickListener false
                showVariantsPopup(holder.textView, item.entry, item.categoryId)
                true
            }
        }

        override fun getItemViewType(position: Int): Int = VIEW_TYPE_EMOJI
    }

    private sealed class SectionItem {
        data class Header(val categoryId: String, val title: String) : SectionItem()
        data class Emoji(val categoryId: String, val entry: EmojiRepository.EmojiEntry) : SectionItem()
    }

    private class SectionItemDiffCallback : DiffUtil.ItemCallback<SectionItem>() {
        override fun areItemsTheSame(oldItem: SectionItem, newItem: SectionItem): Boolean {
            return when {
                oldItem is SectionItem.Header && newItem is SectionItem.Header ->
                    oldItem.categoryId == newItem.categoryId
                oldItem is SectionItem.Emoji && newItem is SectionItem.Emoji ->
                    oldItem.categoryId == newItem.categoryId && oldItem.entry.base == newItem.entry.base
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SectionItem, newItem: SectionItem): Boolean {
            return oldItem == newItem
        }
    }

    private class SearchResultDiffCallback :
        DiffUtil.ItemCallback<EmojiSearchRepository.EmojiSearchResult>() {
        override fun areItemsTheSame(
            oldItem: EmojiSearchRepository.EmojiSearchResult,
            newItem: EmojiSearchRepository.EmojiSearchResult
        ): Boolean {
            return oldItem.entry.base == newItem.entry.base && oldItem.categoryId == newItem.categoryId
        }

        override fun areContentsTheSame(
            oldItem: EmojiSearchRepository.EmojiSearchResult,
            newItem: EmojiSearchRepository.EmojiSearchResult
        ): Boolean {
            return oldItem == newItem
        }
    }

    private data class ScrollAnchor(val position: Int, val offset: Int)

    private fun rebuildIndexCaches(items: List<SectionItem>, categoryIds: List<String>? = null) {
        val headers = mutableMapOf<String, Int>()
        val ids = categoryIds?.toMutableList() ?: ArrayList(items.size)
        if (categoryIds == null) {
            items.forEach { item ->
                ids.add(item.categoryId())
            }
        }
        items.forEachIndexed { index, item ->
            if (item is SectionItem.Header) {
                headers[item.categoryId] = index
            }
        }
        sectionItems = items
        headerPositions = headers
        itemCategoryIds = ids
    }

    private fun buildRecentsItems(recentCategory: EmojiRepository.EmojiCategory): List<SectionItem> {
        val recentsTitle = recentCategory.displayNameRes?.let { context.getString(it) }
            ?: EmojiRepository.RECENTS_CATEGORY_ID
        val items = ArrayList<SectionItem>(recentCategory.emojis.size + 1)
        items.add(SectionItem.Header(EmojiRepository.RECENTS_CATEGORY_ID, recentsTitle))
        recentCategory.emojis.forEach { entry ->
            items.add(SectionItem.Emoji(EmojiRepository.RECENTS_CATEGORY_ID, entry))
        }
        return items
    }

    private fun buildAllCategories(recentCategory: EmojiRepository.EmojiCategory?): List<EmojiRepository.EmojiCategory> {
        return if (recentCategory == null) {
            regularCategories
        } else {
            listOf(recentCategory) + regularCategories
        }
    }

    private fun captureScrollAnchor(): ScrollAnchor? {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return null
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return null
        val topView = recyclerView.getChildAt(0)
        val offset = topView?.top ?: 0
        return ScrollAnchor(firstVisible, offset)
    }

    private fun restoreScrollAnchor(anchor: ScrollAnchor, positionDelta: Int) {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        val targetPosition = (anchor.position + positionDelta).coerceAtLeast(0)
        if (targetPosition >= sectionAdapter.itemCount) return
        lm.scrollToPositionWithOffset(targetPosition, anchor.offset)
    }

    private fun requestRecentsRefresh(requireTop: Boolean, requireNotRecents: Boolean) {
        markRecentsRefreshPending(requireTop, requireNotRecents)
        maybeApplyPendingRecentsRefresh()
    }

    private fun markRecentsRefreshPending(requireTop: Boolean, requireNotRecents: Boolean) {
        pendingRecentsRefresh = true
        pendingRecentsRefreshRequiresTop = pendingRecentsRefreshRequiresTop || requireTop
        pendingRecentsRefreshRequiresNotRecents = pendingRecentsRefreshRequiresNotRecents || requireNotRecents
    }

    private fun maybeApplyPendingRecentsRefresh() {
        if (!pendingRecentsRefresh) return
        if (scrollState != RecyclerView.SCROLL_STATE_IDLE) return
        val requiresNotRecents = pendingRecentsRefreshRequiresNotRecents
        val requiresTop = pendingRecentsRefreshRequiresTop && !requiresNotRecents
        if (requiresTop && !isNearTop()) return
        if (requiresNotRecents &&
            selectedCategoryId == EmojiRepository.RECENTS_CATEGORY_ID) {
            return
        }
        pendingRecentsRefresh = false
        pendingRecentsRefreshRequiresTop = false
        pendingRecentsRefreshRequiresNotRecents = false
        refreshRecentsFromStorage(allowInsertOrRemove = isNearTop())
    }

    private fun isNearTop(): Boolean {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = lm.findFirstVisibleItemPosition()
        return firstVisible != RecyclerView.NO_POSITION && firstVisible <= recentsApplyTopThreshold
    }

    private fun isAtAbsoluteTop(): Boolean {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible != 0) return false
        val firstView = lm.findViewByPosition(0) ?: return false
        return firstView.top >= recyclerView.paddingTop
    }

    private fun SectionItem.categoryId(): String {
        return when (this) {
            is SectionItem.Header -> categoryId
            is SectionItem.Emoji -> categoryId
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_EMOJI = 1
    }
}
