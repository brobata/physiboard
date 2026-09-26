package brobata.physiboard.ime.actions

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import brobata.physiboard.core.actions.commands.CommandCatalog
import brobata.physiboard.core.actions.commands.SourceVisibility
import brobata.physiboard.core.actions.launcher.CommandCustomizations
import brobata.physiboard.core.actions.launcher.IconTint
import brobata.physiboard.core.actions.launcher.LauncherBehavior
import brobata.physiboard.core.actions.launcher.LauncherRow
import brobata.physiboard.core.actions.launcher.NiagaraSearch
import brobata.physiboard.core.actions.launcher.QuickLauncherKeys
import brobata.physiboard.core.actions.launcher.QuickLauncherRanking
import brobata.physiboard.core.actions.launcher.QuickLauncherRules as R
import brobata.physiboard.core.actions.launcher.QuickLauncherSettings
import brobata.physiboard.core.actions.launcher.SheetKeyEffect
import brobata.physiboard.core.actions.launcher.SheetKeyState
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.device.titan.KeyNormalizer

/**
 * PhysiBoard's own quick launcher sheet. spec: expansion-clipboard-pickers-launcher.md SS7. In
 * 2.x it was a transparent activity; here it is a focusable [BottomOverlay] (the task brief's
 * choice for every panel), which is what lets the sheet type into its own query without an
 * activity: a focusable overlay receives the hardware keys itself, and [QuickLauncherKeys]
 * decides what each one does (SS7.1, SS7.3, T40 to T44). Ranking, favourites, hidden entries
 * and aliases are [QuickLauncherRanking]'s and [CommandCustomizations]'; this class draws rows
 * and launches.
 *
 * Not built here (SS13 "undecided", and the keyboard has no write path to the store): the row
 * long-press menu; the same customisations are edited in the settings app's "Customize entries".
 */
internal class QuickLauncherController(
    private val service: InputMethodService,
    private val handler: Handler,
    private val catalogSource: AndroidCommandCatalog,
    private val layoutText: (KeyId, Boolean) -> String?,
) {
    var settings: QuickLauncherSettings = QuickLauncherSettings()
    var customizations: CommandCustomizations = CommandCustomizations()
    var visibility: SourceVisibility = SourceVisibility()
    var quickLauncherKey: KeyId? = null
    var executor: CommandExecutor? = null

    /**
     * Called right after the sheet actually opens, so the owner of the other three bottom
     * overlays (Sym grid, clipboard, emoji picker -- exclusive among themselves by construction)
     * can close whichever of those is open, keeping at most one overlay on screen at once.
     */
    var onOpened: () -> Unit = {}

    private val panel = BottomOverlay(service, TAG)
    private var query = ""
    private var rows: List<LauncherRow> = emptyList()
    private var results: List<LauncherRow> = emptyList()
    private var keyState = SheetKeyState()
    private var list: LinearLayout? = null
    private var queryView: TextView? = null
    private var dismissing = false
    private var autoLaunched = false

    init {
        // spec SS7.2: "the full catalog is reloaded in the background" -- while the sheet is open
        // on a stale or empty (still "Loading apps...") list, a reload that actually changes the
        // apps redraws it in place instead of waiting for the next open.
        catalogSource.onAppsReloaded = { if (isOpen) refreshRows() }
    }

    val isOpen: Boolean get() = panel.isShown

    private fun refreshRows() {
        rows = buildRows(catalogSource.build())
        refilter()
    }

    /** spec SS7.1: the key press toggles ("the same key opens and closes"); the command path opens. */
    fun toggle(): Boolean = if (isOpen) { dismiss(); true } else openPhysiBoardSheet()

    /**
     * spec SS7.1: `quick_launcher_behavior` decides what "open" means: PhysiBoard's sheet, or
     * Niagara's search with a fallback to the sheet when it fails to start.
     */
    fun open(): Boolean {
        if (settings.behavior == LauncherBehavior.NIAGARA && openNiagara()) return true
        return openPhysiBoardSheet()
    }

    private fun openNiagara(): Boolean = runCatching {
        val intent = Intent(NiagaraSearch.ACTION, android.net.Uri.parse(NiagaraSearch.URI)).apply {
            setPackage(NiagaraSearch.PACKAGE)
            addCategory(NiagaraSearch.CATEGORY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        service.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openPhysiBoardSheet(): Boolean {
        if (isOpen) return true
        query = ""
        keyState = SheetKeyState()
        dismissing = false
        autoLaunched = false
        val catalog = catalogSource.build()
        rows = buildRows(catalog)
        val root = build()
        val height = (service.resources.displayMetrics.heightPixels * R.MAX_HEIGHT_PERCENT / 100)
        if (!panel.show(root, heightPx = height, focusable = true)) return false
        onOpened()
        root.requestFocus()
        refilter()
        root.animate().translationY(0f).alpha(1f).setDuration(R.ANIMATION_MS).start()
        return true
    }

    fun dismiss() {
        if (!isOpen || dismissing) return
        dismissing = true
        val view = panel.view
        if (view == null) {
            panel.hide()
            return
        }
        view.animate().translationY(view.height.toFloat()).alpha(0f).setDuration(R.ANIMATION_MS).withEndAction { panel.hide() }.start()
    }

    fun onServiceDestroyed() = panel.hide()

    private fun buildRows(catalog: CommandCatalog): List<LauncherRow> =
        catalog.forQuickLauncher(visibility).map { LauncherRow(it, customizations[it.id]) }

    // -----------------------------------------------------------------------------------------
    // Keys. spec SS7.1, SS7.3.
    // -----------------------------------------------------------------------------------------

    private fun onKey(event: KeyEvent): Boolean {
        val key = KeyNormalizer.normalize(event.keyCode, event.scanCode, event.action, event.repeatCount, event.metaState, event.deviceId, event.eventTime)?.key
            ?: return false
        val symHeld = event.metaState and KeyEvent.META_SYM_ON != 0
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return key in keyState.consumedDowns
                val uppercase = event.isShiftPressed
                val text = if (settings.respectKeyboardLayout && !event.isCtrlPressed) layoutText(key, uppercase) else null
                val eventChar = event.unicodeChar.takeIf { it != 0 && !event.isCtrlPressed }?.toChar()
                val (next, effect) = QuickLauncherKeys.onKeyDown(keyState, key, symHeld, event.isCtrlPressed, quickLauncherKey, text, eventChar)
                keyState = next
                apply(effect)
            }
            KeyEvent.ACTION_UP -> {
                val (next, effect) = QuickLauncherKeys.onKeyUp(keyState, key, event.isCanceled)
                keyState = next
                apply(effect)
            }
            else -> false
        }
    }

    private fun apply(effect: SheetKeyEffect): Boolean = when (effect) {
        SheetKeyEffect.Dismiss -> { dismiss(); true }
        SheetKeyEffect.LaunchTop -> { results.firstOrNull()?.let(::launch); true }
        is SheetKeyEffect.AppendText -> { query += effect.text; refilter(); true }
        SheetKeyEffect.DeleteLast -> { query = query.dropLast(1); refilter(); true }
        SheetKeyEffect.ConsumedOnly -> true
        SheetKeyEffect.NotConsumed -> false
    }

    private fun launch(row: LauncherRow) {
        dismiss()
        val executor = executor ?: return
        handler.post { runCatching { executor.run(row.command) }.onFailure { Log.e(TAG, "launch crashed", it) } }
    }

    // -----------------------------------------------------------------------------------------
    // Drawing. spec SS7.1 (appearance), SS7.2 (what it lists), SS7.5 (rows).
    // -----------------------------------------------------------------------------------------

    private fun build(): View {
        val context = panel.overlayContext
        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(panel.dp(R.PADDING_H_DP), panel.dp(R.PADDING_V_DP), panel.dp(R.PADDING_H_DP), panel.dp(R.PADDING_V_DP))
            background = GradientDrawable().apply {
                setColor(SHEET_BACKGROUND)
                cornerRadii = floatArrayOf(panel.dp(16).toFloat(), panel.dp(16).toFloat(), panel.dp(16).toFloat(), panel.dp(16).toFloat(), 0f, 0f, 0f, 0f)
            }
            alpha = 0f
            translationY = panel.dp(48).toFloat()
            setOnKeyListener { _, _, event -> runCatching { onKey(event) }.getOrElse { error -> Log.e(TAG, "sheet key crashed", error); false } }
        }
        sheet.addView(
            TextView(context).apply {
                text = R.TITLE
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, 0, 0, panel.dp(6))
            },
        )
        val searchBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(panel.dp(12), panel.dp(8), panel.dp(12), panel.dp(8))
            background = GradientDrawable().apply {
                setColor(SEARCH_BACKGROUND)
                cornerRadius = panel.dp(20).toFloat()
            }
        }
        searchBox.addView(TextView(context).apply { text = "🔍"; setPadding(0, 0, panel.dp(8), 0) })
        queryView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            maxLines = 1
        }
        searchBox.addView(queryView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        searchBox.addView(TextView(context).apply { text = "⌄"; setTextColor(Color.WHITE); setOnClickListener { dismiss() } })
        sheet.addView(searchBox)
        list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        sheet.addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val dim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            setOnClickListener { dismiss() }
        }
        dim.addView(sheet, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        dim.isFocusable = true
        dim.isFocusableInTouchMode = true
        dim.setOnKeyListener { _, _, event -> runCatching { onKey(event) }.getOrElse { false } }
        return dim
    }

    private fun refilter() {
        val queryView = queryView ?: return
        queryView.text = query.ifEmpty { R.HINT }
        queryView.setTextColor(if (query.isEmpty()) MUTED else Color.WHITE)
        results = QuickLauncherRanking.rank(rows, query, settings)
        autoLaunched = false
        renderRows()
        QuickLauncherRanking.autoLaunches(query, results, settings)?.let { row ->
            if (!autoLaunched) {
                autoLaunched = true
                launch(row)
            }
        }
    }

    private fun renderRows() {
        val list = list ?: return
        list.removeAllViews()
        val context = panel.overlayContext
        if (results.isEmpty()) {
            val message = when {
                // spec SS7.2: "'Loading apps...' shows while the list is empty during that reload".
                rows.isEmpty() && catalogSource.isLoadingApps -> R.LOADING
                rows.isEmpty() -> R.EMPTY_NO_ENTRIES
                query.isBlank() && settings.limitResults -> R.EMPTY_LIMITED
                query.isBlank() -> R.EMPTY_NO_ENTRIES
                else -> R.emptyNoResults(query)
            }
            list.addView(TextView(context).apply { text = message; setTextColor(MUTED); setPadding(0, panel.dp(16), 0, panel.dp(16)); gravity = Gravity.CENTER })
            return
        }
        val headers = QuickLauncherRanking.showsSourceHeaders(query, results.size)
        var lastSource: String? = null
        results.forEachIndexed { index, row ->
            if (headers && row.command.source.label != lastSource) {
                lastSource = row.command.source.label
                list.addView(TextView(context).apply { text = lastSource; setTextColor(MUTED); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); setPadding(0, panel.dp(8), 0, panel.dp(4)) })
            }
            list.addView(rowView(row, index == 0))
        }
    }

    /** spec SS7.5: icon, label (alias first when set), subtitle or source label, "Enter" on the top match, the favourite's 2 dp border. */
    private fun rowView(row: LauncherRow, top: Boolean): View {
        val context = panel.overlayContext
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(panel.dp(10), panel.dp(8), panel.dp(10), panel.dp(8))
            background = GradientDrawable().apply {
                cornerRadius = panel.dp(10).toFloat()
                rowTintColor(row, top)?.let { setColor(it) }
                if (row.isFavorite && settings.highlightFavorites) setStroke(panel.dp(R.FAVORITE_BORDER_DP), FAVORITE_BORDER)
            }
            setOnClickListener { launch(row) }
        }
        val icon = ImageView(context)
        row.command.iconPackage?.let { pkg -> icon.setImageDrawable(catalogSource.appIcon(pkg)) }
        view.addView(icon, LinearLayout.LayoutParams(panel.dp(32), panel.dp(32)).apply { marginEnd = panel.dp(12) })
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply { text = row.displayLabel(settings.showAliasFirst); setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        texts.addView(TextView(context).apply { text = row.displaySubtitle; setTextColor(MUTED); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f); maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        view.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (top) view.addView(TextView(context).apply { text = R.ENTER_HINT; setTextColor(MUTED); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f) })
        return view
    }

    /**
     * spec SS7.5: "the top match is tinted with `quick_launcher_static_top_highlight_color` ...
     * when `quick_launcher_static_top_highlight` is on ..., otherwise with the entry's chosen
     * color or a color derived from its icon at alpha 0.58." A non-top row with no chosen color
     * keeps no fill: `quick_launcher_icon_colors` (tinting every row) is not built yet.
     */
    private fun rowTintColor(row: LauncherRow, top: Boolean): Int? {
        row.customization.color?.let { return it }
        if (!top) return null
        if (settings.staticTopHighlight) return settings.staticTopHighlightColor
        return iconTintFor(row, TOP_TINT_ALPHA)
    }

    /** spec SS7.5: "icons that yield nothing use a hue per source" -- also the only path for a command with no app icon at all. */
    private fun iconTintFor(row: LauncherRow, alpha: Int): Int {
        val fromIcon = row.command.iconPackage?.let { pkg -> catalogSource.appIcon(pkg) }?.let { averagePixelColor(it, alpha) }
        return fromIcon ?: IconTint.colorForHue(row.command.source.hue, alpha)
    }

    /** spec SS7.5: "the alpha- and saturation-weighted average of a 32 by 32 rendering of the icon". */
    private fun averagePixelColor(drawable: Drawable, alpha: Int): Int? = runCatching {
        val bitmap = Bitmap.createBitmap(ICON_SAMPLE_SIZE, ICON_SAMPLE_SIZE, Bitmap.Config.ARGB_8888)
        val previousBounds = drawable.bounds
        drawable.setBounds(0, 0, ICON_SAMPLE_SIZE, ICON_SAMPLE_SIZE)
        drawable.draw(Canvas(bitmap))
        drawable.bounds = previousBounds
        val pixels = IntArray(ICON_SAMPLE_SIZE * ICON_SAMPLE_SIZE)
        bitmap.getPixels(pixels, 0, ICON_SAMPLE_SIZE, 0, 0, ICON_SAMPLE_SIZE, ICON_SAMPLE_SIZE)
        bitmap.recycle()
        IconTint.averageColor(pixels, alpha)
    }.getOrNull()

    private companion object {
        /** spec SS7.5: "alpha 0.58" (0.58 * 255, rounded). */
        const val TOP_TINT_ALPHA = 148
        const val ICON_SAMPLE_SIZE = 32
        const val TAG = "PhysiBoardQuickLauncher"
        val SHEET_BACKGROUND: Int = Color.rgb(28, 28, 30)
        val SEARCH_BACKGROUND: Int = Color.rgb(44, 44, 48)
        val MUTED: Int = Color.argb(160, 255, 255, 255)
        val FAVORITE_BORDER: Int = Color.rgb(64, 156, 255)
    }
}
