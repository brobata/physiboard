package brobata.physiboard.ime.skin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import brobata.physiboard.core.actions.clipboard.Clip
import brobata.physiboard.core.actions.clipboard.ClipboardHistory
import brobata.physiboard.core.actions.fill.OneTimeCode
import brobata.physiboard.core.actions.snippets.Snippet
import brobata.physiboard.core.actions.snippets.SnippetMatch
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.SymChooserEntry
import brobata.physiboard.core.keys.SymChooserTarget
import brobata.physiboard.core.pointer.caret.BadgeItem
import brobata.physiboard.core.pointer.caret.GlyphStyle
import brobata.physiboard.core.pointer.caret.ModifierGlyph
import brobata.physiboard.core.pointer.trackpad.TrackpadGestureSettings
import brobata.physiboard.core.strip.StripTheme
import brobata.physiboard.core.strip.SymGridPage
import brobata.physiboard.ime.actions.ClipboardPanelController
import brobata.physiboard.ime.actions.EmojiAssets
import brobata.physiboard.ime.actions.EmojiPickerController
import brobata.physiboard.ime.actions.ExpansionPopupController
import brobata.physiboard.ime.actions.GifPageController
import brobata.physiboard.ime.actions.GifPreviews
import brobata.physiboard.ime.actions.GifShelfStore
import brobata.physiboard.ime.actions.SkinTonePanelController
import brobata.physiboard.ime.actions.SymGridPanelController
import brobata.physiboard.ime.actions.SymPageChooserController
import brobata.physiboard.ime.fill.FillPageController
import brobata.physiboard.ime.fill.InlineFill
import brobata.physiboard.ime.pointer.CaretBadgeOverlayView
import brobata.physiboard.ime.pointer.TrackpadOverlayView
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.File
import java.util.concurrent.TimeUnit
import brobata.physiboard.design.R.drawable as DesignDrawables

/**
 * Draws every keyboard panel the way it reaches the screen, in real pixels (Robolectric's native
 * graphics), at the Titan 2 Elite's 1080 px width and 300 dpi. Nothing here can type: it builds
 * each panel through its own controller, takes the view the controller handed the window manager,
 * and checks that the panel drew something. The PNGs land in `ime/build/panel-renders/` for the
 * design review (docs/design/design-system.md); no debug screen ships for this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w576dp-h640dp-300dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelRenderTest {

    class HarnessIme : InputMethodService()

    private lateinit var service: InputMethodService
    private val handler = Handler(Looper.getMainLooper())
    private val out: File by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        File(checkNotNull(dir), "ime/build/panel-renders").apply { mkdirs() }
    }

    private val dark = StripTheme.SLATE_DARK
    private val terminal = StripTheme(
        background = 0xFF0F172A.toInt(), suggestion = 0xFF1E293B.toInt(), button = 0xFF334155.toInt(),
        accent = 0xFFF59E0B.toInt(), textAndIcons = 0xFFF1F5F9.toInt(), divider = 0xFF334155.toInt(),
        keyCornerRatio = 0.08, chromeCornerRatio = 0.08, ledInactive = 0xFF475569.toInt(), ledActive = 0xFFF59E0B.toInt(),
        ledLocked = 0xFFB45309.toInt(), suggestionsHeightScale = 1.4, showLeds = false,
    )
    private val light = StripTheme(
        background = 0xFFF8FAFC.toInt(), suggestion = 0xFFFFFFFF.toInt(), button = 0xFFE0E6EE.toInt(),
        accent = 0xFF276EF1.toInt(), textAndIcons = 0xFF171A1F.toInt(), divider = 0xFFC7CDD4.toInt(),
        keyCornerRatio = 0.10, chromeCornerRatio = 0.10, ledInactive = 0xFFD1D5DB.toInt(), ledActive = 0xFF276EF1.toInt(),
        ledLocked = 0xFFD65A00.toInt(), suggestionsHeightScale = 1.4, showLeds = false,
    )
    private val themes = listOf("terminal" to terminal, "slate-dark" to dark, "slate-light" to light)

    private val symbols = mapOf(
        'Q' to "1", 'W' to "2", 'E' to "3", 'R' to "4", 'T' to "5", 'Y' to "6", 'U' to "7", 'I' to "8", 'O' to "9", 'P' to "0",
        'A' to "@", 'S' to "#", 'D' to "€", 'F' to "_", 'G' to "&", 'H' to "-", 'J' to "+", 'K' to "(", 'L' to ")",
        'Z' to "*", 'X' to "\"", 'C' to "'", 'V' to ":", 'B' to ";", 'N' to "!", 'M' to "?",
    )
    private val emoji = mapOf(
        'Q' to "😀", 'W' to "😂", 'E' to "😍", 'R' to "😭", 'T' to "😊", 'Y' to "👍", 'U' to "🙏", 'I' to "❤", 'O' to "🔥", 'P' to "🎉",
        'A' to "😅", 'S' to "😎", 'D' to "🤔", 'F' to "😢", 'G' to "😡", 'H' to "👋", 'J' to "👏", 'K' to "💯", 'L' to "✨",
        'Z' to "😴", 'X' to "🤷", 'C' to "🙄", 'V' to "😘", 'B' to "🥳", 'N' to "🤝", 'M' to "👀",
    )

    @Before
    fun setUp() {
        ShadowSettings.setCanDrawOverlays(true)
        service = Robolectric.buildService(HarnessIme::class.java).create().get()
        // Reduced motion: the panels land in their final place with no spring to wait for.
        Settings.Global.putFloat(service.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun windowManager(): ShadowWindowManagerImpl =
        Shadow.extract(service.getSystemService(Context.WINDOW_SERVICE) as WindowManager)

    /** The last window a controller added, drawn at the screen's width and its own height. */
    private fun captureLast(name: String, fallbackHeightDp: Int = 420) {
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        val views = windowManager().views
        assertTrue("$name added no window", views.isNotEmpty())
        val view = views.last()
        val params = view.layoutParams as? WindowManager.LayoutParams
        val width = service.resources.displayMetrics.widthPixels
        val requested = params?.width?.takeIf { it > 0 }
        render(name, view, requested ?: width, params?.height?.takeIf { it > 0 }, fallbackHeightDp)
        // Close every window before the next panel: a fresh window manager list per capture.
        for (v in views.toList()) (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v)
    }

    private fun render(name: String, view: View, widthPx: Int, heightPx: Int?, fallbackHeightDp: Int = 300) {
        val density = service.resources.displayMetrics.density
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            if (heightPx != null) View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            else View.MeasureSpec.makeMeasureSpec((fallbackHeightDp * density).toInt(), View.MeasureSpec.AT_MOST),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            if (heightPx != null) View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
            else View.MeasureSpec.makeMeasureSpec((fallbackHeightDp * density).toInt(), View.MeasureSpec.AT_MOST),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val bitmap = Bitmap.createBitmap(view.measuredWidth.coerceAtLeast(1), view.measuredHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name drew nothing", view.measuredHeight > 0)
    }

    private val gridListener = object : SymGridPanelController.Listener {
        override fun onKeyTapped(letter: Char) = Unit
        override fun onKeyLongPressed(letter: Char) = Unit
        override fun onPencil() = Unit
        override fun onGlobe() = Unit
        override fun onSearch() = Unit
        override fun onClose() = Unit
    }

    @Test
    fun symPages() {
        for ((label, theme) in themes) {
            SymGridPanelController(service).apply { show(SymGridPage.SYMBOLS, symbols, theme, 0, gridListener) }
            captureLast("sym-symbols-$label")
            SymGridPanelController(service).apply { show(SymGridPage.EMOJI, emoji, theme, 0, gridListener) }
            captureLast("sym-emoji-$label")
        }
        SymGridPanelController(service).apply { show(SymGridPage.CUSTOM_1, mapOf('Q' to "hello", 'W' to "→", 'A' to "brb", 'S' to "omw"), terminal, 0, gridListener) }
        captureLast("sym-mypage-terminal")
    }

    @Test
    fun clipboard() {
        var history = ClipboardHistory()
        history = history.capture("Meet at the loading dock at 6:30, bring the hotel pans", 1_000)
        history = history.capture("https://physiboard.app/docs/sym-pages", 2_000)
        history = history.capture("415-555-0134", 3_000)
        history = history.capture("Thanks, see you tomorrow!", 4_000)
        val pinned = history.entries.first()
        history = history.copy(entries = history.entries.map { if (it.id == pinned.id) it.copy(pinned = true) else it })
        val listener = object : ClipboardPanelController.Listener {
            override fun onClipTapped(clip: Clip) = Unit
            override fun onTogglePinned(clip: Clip) = Unit
            override fun onDelete(clip: Clip) = Unit
            override fun onClearAll() = Unit
            override fun onClose() = Unit
        }
        for ((label, theme) in themes) {
            ClipboardPanelController(service).show(history, theme, 0, listener)
            captureLast("clipboard-$label")
        }
        ClipboardPanelController(service).show(ClipboardHistory(), terminal, 0, listener)
        captureLast("clipboard-empty-terminal")
    }

    @Test
    fun emojiPicker() {
        val assets = EmojiAssets(service.assets, handler, 33)
        val listener = object : EmojiPickerController.Listener {
            override fun onChosen(text: String) = Unit
            override fun onClose() = Unit
            override fun layoutText(key: KeyId, uppercase: Boolean): String? = null
        }
        for ((label, theme) in themes) {
            val picker = EmojiPickerController(service, handler, assets) { true }
            picker.show(false, theme, 0, brobata.physiboard.core.actions.emoji.SkinTone.NONE, listener, kaomojiEnabled = true)
            repeat(40) {
                ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
                Thread.sleep(50)
            }
            captureLast("emoji-picker-$label")
        }
        assets.shutdown()
    }

    @Test
    fun gifPage() {
        val listener = object : GifPageController.Listener {
            override fun onSend(item: brobata.physiboard.core.actions.gif.GifItem) = Unit
            override fun onClose() = Unit
            override fun layoutText(key: KeyId, uppercase: Boolean): String? = null
        }
        for ((label, theme) in themes) {
            GifPageController(service, handler, "", { null }, GifPreviews { null }, GifShelfStore(service), { true }, { null })
                .show(theme, 0, listener, loadAtOnce = false)
            captureLast("gif-$label")
        }
    }

    @Test
    fun fillPage() {
        val now = 10_000_000L
        val content = object : FillPageController.Content {
            override val codes = listOf(
                OneTimeCode("482913", "Messages", "sms", now - 60_000),
                OneTimeCode("771204", "Gmail", "mail", now - 240_000),
            )
            override val inline = InlineFill()
            override val nowMs = now
            override fun keyLabel(digit: Int): String? = when (digit) { 1 -> "W"; 2 -> "E"; else -> null }
            override val emptyNote = "Nothing to fill yet"
        }
        val listener = object : FillPageController.Listener {
            override fun onCode(code: OneTimeCode) = Unit
            override fun onClose() = Unit
        }
        for ((label, theme) in themes) {
            FillPageController(service, handler).show(theme, 0, content, listener)
            captureLast("fill-$label")
        }
    }

    @Test
    fun choosersAndBars() {
        val entries = SymChooserTarget.entries.map { SymChooserEntry(it, inCycle = it != SymChooserTarget.GIF) }
        for ((label, theme) in themes) {
            SymPageChooserController(service, handler).show(entries, theme, 0) { }
            captureLast("sym-chooser-$label")
            SkinTonePanelController(service).show(
                listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), listOf("P", "Q", "W", "E", "R", "T"), theme, 0, {}, {},
            )
            captureLast("skin-tones-$label")
            SkinTonePanelController(service).show(
                listOf("é", "è", "ê", "ë", "ē", "ė", "ę"), listOf("W", "E", "R", "S", "D", "F", "Z"), theme, 0, {}, {},
                digitOf = { (it + 1) % 10 }, glyphSp = 22f,
            )
            captureLast("accents-$label")
        }
        ExpansionPopupController(service).render(
            listOf(SnippetMatch(Snippet("addr", "1200 Barton Springs Rd")), SnippetMatch(Snippet("adm", "Admin office: ext 204"))), 0, 0,
        ) { }
        captureLast("expansion-popup")
    }

    /** The launcher icon (both layers under a round and a squircle mask), the dev icon, the themed icon and the small icons. */
    @Test
    fun identity() {
        val size = 432
        val sheet = Bitmap.createBitmap(size * 4 + 60, size * 2 + 20, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(0xFF808890.toInt())
        fun drawable(id: Int) = checkNotNull(service.getDrawable(id))
        fun layer(id: Int, x: Int, y: Int, tint: Int? = null, bg: Int? = null, round: Boolean = true) {
            val tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(tile)
            if (bg != null) c.drawColor(bg)
            if (id == 0) {
                drawable(brobata.physiboard.design.R.drawable.pb_launcher_background).apply { setBounds(0, 0, size, size); draw(c) }
                drawable(brobata.physiboard.design.R.drawable.pb_launcher_foreground).apply { setBounds(0, 0, size, size); draw(c) }
            } else {
                drawable(id).apply { setBounds(0, 0, size, size); tint?.let { setTint(it) }; draw(c) }
            }
            // The launcher's mask: a circle, or a squircle-ish rounded square, over the 108 dp canvas
            // cut to its visible 72 dp (inset 18/108 each side).
            val inset = size * 18f / 108f
            val path = android.graphics.Path()
            val r = android.graphics.RectF(inset, inset, size - inset, size - inset)
            if (round) path.addOval(r, android.graphics.Path.Direction.CW) else path.addRoundRect(r, size * 0.16f, size * 0.16f, android.graphics.Path.Direction.CW)
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.clipPath(path)
            canvas.drawBitmap(tile, 0f, 0f, null)
            canvas.restore()
        }
        layer(0, 0, 0)
        layer(0, size + 20, 0, round = false)
        layer(DesignDrawables.pb_launcher_foreground_dev, (size + 20) * 2, 0, bg = 0xFF0F172A.toInt())
        layer(DesignDrawables.pb_launcher_monochrome, (size + 20) * 3, 0, tint = 0xFF3A2F1E.toInt(), bg = 0xFFF2E3C6.toInt())
        val small = listOf(DesignDrawables.pb_ic_mark, DesignDrawables.pb_ic_backlight, DesignDrawables.pb_ic_ring, DesignDrawables.pb_ic_close, DesignDrawables.pb_ic_search, DesignDrawables.pb_ic_edit, DesignDrawables.pb_ic_globe, DesignDrawables.pb_ic_chevron_down)
        small.forEachIndexed { i, id ->
            drawable(id).apply {
                setTint(0xFFFFFFFF.toInt())
                val s = 120
                val x = 20 + i * (s + 50)
                setBounds(x, size + 80, x + s, size + 80 + s)
                draw(canvas)
            }
        }
        File(out, "identity.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun trackpadAndBadge() {
        val density = service.resources.displayMetrics.density
        val pad = TrackpadOverlayView(service, TrackpadGestureSettings(), { false }, {}, {}).apply { hintText = "Slide to move the cursor · tap here to close" }
        val frame = android.widget.FrameLayout(service).apply { setBackgroundColor(0xFF263238.toInt()) }
        frame.addView(pad, android.widget.FrameLayout.LayoutParams(-1, -1))
        render("trackpad-hint", frame, service.resources.displayMetrics.widthPixels, (160 * density).toInt())
        val badgeFrame = android.widget.FrameLayout(service).apply {
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding((16 * density).toInt(), (16 * density).toInt(), 0, 0)
        }
        val badge = CaretBadgeOverlayView(service).apply {
            items = listOf(BadgeItem(ModifierGlyph.SHIFT, GlyphStyle.LOCKED_FULL), BadgeItem(ModifierGlyph.CTRL, GlyphStyle.ARMED_FULL), BadgeItem(ModifierGlyph.FILL, GlyphStyle.ARMED_FAINT))
        }
        badgeFrame.addView(badge)
        render("caret-badge", badgeFrame, (240 * density).toInt(), (56 * density).toInt())
    }
}
