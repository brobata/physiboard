package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.PopupWindow
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.res.Configuration
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.data.emoji.EmojiRepository
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
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

    // Data for sections
    private var sectionItems: List<SectionItem> = emptyList()
    private var headerPositions: Map<String, Int> = emptyMap()
    private var selectedCategoryId: String? = null
    private var isTabClickScroll = false

    // Adapter
    private val sectionAdapter: SectionAdapter
    private val columns: Int

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
        gridLayoutManager.spanSizeLookup = sectionAdapter.spanSizeLookup
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = sectionAdapter

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos == RecyclerView.NO_POSITION) return
                when (sectionAdapter.getItemViewType(pos)) {
                    VIEW_TYPE_HEADER -> {
                        outRect.set(0, spacing, 0, spacing)
                    }
                    VIEW_TYPE_EMOJI -> {
                        val column = (pos - sectionAdapter.firstEmojiPositionBefore(pos)) % columns
                        outRect.left = if (column == 0) 0 else spacing / 2
                        outRect.right = if (column == columns - 1) 0 else spacing / 2
                        outRect.top = spacing / 2
                        outRect.bottom = spacing / 2
                    }
                }
            }
        })

        // Scroll listener to sync tabs and refresh recents at top
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isTabClickScroll = false
                    // Refresh recents when user stops scrolling at the top
                    val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val firstVisible = lm.findFirstVisibleItemPosition()
                    if (firstVisible <= 5) { // Near top = recents area
                        refreshRecentsFromStorage()
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isTabClickScroll) return
                val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible == RecyclerView.NO_POSITION) return
                val header = sectionItems.getOrNull(firstVisible) as? SectionItem.Header
                    ?: run {
                        val headerPos = (firstVisible downTo 0).firstOrNull { sectionItems[it] is SectionItem.Header }
                        if (headerPos != null) sectionItems[headerPos] as? SectionItem.Header else null
                    } ?: return
                if (header.categoryId != selectedCategoryId) {
                    selectedCategoryId = header.categoryId
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

        // Tabs at bottom (above LEDs)
        tabScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setPadding(smallPadding, smallPadding / 2, smallPadding, smallPadding / 2)
            }
        }
        tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        tabScrollView.addView(tabRow)

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

                val allCategories = mutableListOf<EmojiRepository.EmojiCategory>()
                if (recentCategory != null) allCategories.add(recentCategory)
                allCategories.addAll(regularCategories)

                // Always reset to first category when loading
                selectedCategoryId = allCategories.firstOrNull()?.id

                buildSections(allCategories)
                updateTabs(allCategories)

                loadingView.visibility = View.GONE
                if (allCategories.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    // Always start from top when opening emoji picker
                    recyclerView.scrollToPosition(0)
                }
            } catch (e: CancellationException) {
                throw e // Re-throw cancellation to properly cancel coroutine
            } catch (e: Exception) {
                loadingView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        }
    }

    private fun buildSections(categories: List<EmojiRepository.EmojiCategory>) {
        val items = mutableListOf<SectionItem>()
        val headers = mutableMapOf<String, Int>()

        categories.forEach { category ->
            headers[category.id] = items.size
            val title = category.displayNameRes?.let { context.getString(it) } ?: category.id
            items.add(SectionItem.Header(category.id, title))
            category.emojis.forEach { emojiEntry ->
                items.add(SectionItem.Emoji(category.id, emojiEntry))
            }
        }

        sectionItems = items
        headerPositions = headers
        sectionAdapter.submitList(items)
    }

    private fun updateTabs(categories: List<EmojiRepository.EmojiCategory>) {
        tabRow.removeAllViews()
        categories.forEachIndexed { index, category ->
            val icon = EmojiRepository.getCategoryIcon(category.id)
            val label = category.displayNameRes?.let { context.getString(it) } ?: category.id
            val isSelected = category.id == selectedCategoryId
            val btn = TextView(context).apply {
                text = icon
                contentDescription = label
                textSize = 20f
                gravity = Gravity.CENTER
                background = createTabBackground(isSelected)
                alpha = if (isSelected) 1.0f else 0.6f
                val pad = dpToPx(10f)
                setPadding(pad, pad / 2, pad, pad / 2)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        marginStart = dpToPx(2f)
                    }
                }
                setOnClickListener {
                    val headerPos = headerPositions[category.id] ?: return@setOnClickListener
                    selectedCategoryId = category.id
                    updateTabsSelection()
                    isTabClickScroll = true
                    (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(headerPos, 0)
                    // Refresh recents when clicking on recents tab
                    if (category.id == EmojiRepository.RECENTS_CATEGORY_ID) {
                        refreshRecentsFromStorage()
                    }
                }
            }
            tabRow.addView(btn)
        }
        updateTabsSelection()
    }

    private fun updateTabsSelection() {
        for (i in 0 until tabRow.childCount) {
            val view = tabRow.getChildAt(i) as? TextView ?: continue
            val header = sectionItems.asSequence().filterIsInstance<SectionItem.Header>().elementAtOrNull(i)
            val isSelected = header?.categoryId == selectedCategoryId
            view.alpha = if (isSelected) 1.0f else 0.6f
            view.background = createTabBackground(isSelected)
        }
        val selectedIndex = (0 until tabRow.childCount).firstOrNull { idx ->
            val header = sectionItems.asSequence().filterIsInstance<SectionItem.Header>().elementAtOrNull(idx)
            header?.categoryId == selectedCategoryId
        } ?: return
        tabScrollView.post {
            val view = tabRow.getChildAt(selectedIndex)
            val scrollX = view.left - tabScrollView.width / 2 + view.width / 2
            tabScrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }

    private fun onEmojiSelected(emoji: String, categoryId: String) {
        currentInputConnection?.commitText(emoji, 1)
        // Just save to storage - recents will be refreshed when user scrolls to top
        RecentEmojiManager.addRecentEmoji(context, emoji)
    }

    /**
     * Simple refresh of recents from storage.
     * Called when user scrolls to top of the list.
     * Compares stored vs displayed recents and updates only if different.
     */
    private fun refreshRecentsFromStorage() {
        coroutineScope.launch {
            val recentCategory = withContext(Dispatchers.IO) {
                RecentEmojiManager.getRecentEmojiCategory(context)
            }

            val recentsHeaderIndex = headerPositions[EmojiRepository.RECENTS_CATEGORY_ID]

            // Case 1: Recents in storage but not displayed -> full reload
            if (recentsHeaderIndex == null && recentCategory != null) {
                loadCategories()
                return@launch
            }

            // Case 2: No recents in storage but displayed -> full reload
            if (recentsHeaderIndex != null && recentCategory == null) {
                loadCategories()
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
                    val recentsTitle = recentCategory.displayNameRes?.let { context.getString(it) }
                        ?: EmojiRepository.RECENTS_CATEGORY_ID
                    val newRecentsItems = mutableListOf<SectionItem>()
                    newRecentsItems.add(SectionItem.Header(EmojiRepository.RECENTS_CATEGORY_ID, recentsTitle))
                    recentCategory.emojis.forEach { entry ->
                        newRecentsItems.add(SectionItem.Emoji(EmojiRepository.RECENTS_CATEGORY_ID, entry))
                    }

                    val newItems = sectionItems.toMutableList()
                    for (i in recentsHeaderIndex until nextHeaderIndex) {
                        newItems.removeAt(recentsHeaderIndex)
                    }
                    newItems.addAll(recentsHeaderIndex, newRecentsItems)

                    val newHeaders = mutableMapOf<String, Int>()
                    newItems.forEachIndexed { index, item ->
                        if (item is SectionItem.Header) {
                            newHeaders[item.categoryId] = index
                        }
                    }

                    sectionItems = newItems
                    headerPositions = newHeaders
                    sectionAdapter.submitList(newItems)
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
            val color = if (isSelected) Color.argb(80, 255, 255, 255) else Color.argb(40, 255, 255, 255)
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
        val xOffset = -container.width / 2 + anchor.width / 2
        popup.showAsDropDown(anchor, xOffset, -anchor.height * 2)
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

    private inner class SectionAdapter(private val columns: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var items: List<SectionItem> = emptyList()

        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (getItemViewType(position)) {
                    VIEW_TYPE_HEADER -> columns
                    else -> 1
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
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

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
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

        fun submitList(newItems: List<SectionItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        fun firstEmojiPositionBefore(position: Int): Int {
            for (i in position downTo 0) {
                if (items[i] is SectionItem.Header) return i + 1
            }
            return 0
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)
    private class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private sealed class SectionItem {
        data class Header(val categoryId: String, val title: String) : SectionItem()
        data class Emoji(val categoryId: String, val entry: EmojiRepository.EmojiEntry) : SectionItem()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_EMOJI = 1
    }
}

