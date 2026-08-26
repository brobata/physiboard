package it.palsoftware.pastiera.ring

import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import it.palsoftware.pastiera.SettingsManager

/**
 * The ring itself: a black, lock-screen-topping activity that turns the screen on and keeps it
 * on for the configured time.
 *
 * It ends on the first thing that means the user has noticed — a touch, a key, unlocking —
 * and on the screen going dark. When the time runs out it does not switch the screen off
 * (an app cannot); it stops holding the screen on and lets the phone's own timeout do that,
 * black to black, so nothing flashes.
 */
class NotificationRingActivity : Activity() {

    private lateinit var view: RingView
    private val handler = Handler(Looper.getMainLooper())
    private val sources = LinkedHashMap<String, RingSource>()
    private var demo = false
    private var breathing: ValueAnimator? = null

    private val expire = Runnable {
        // Let go of the screen; the system's own timeout takes it from here.
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (demo) finish()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT -> finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        NotificationRingLauncher.dismissAnnouncement(this)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                screenBrightness = SettingsManager.getNotificationRingBrightness(this@NotificationRingActivity)
                    .screenBrightness
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        view = RingView(this)
        view.setOnApplyWindowInsetsListener { v, insets ->
            placeRing(insets.displayCutout?.boundingRects?.firstOrNull()?.let {
                val dpi = resources.displayMetrics.densityDpi
                if (RingGeometry.isTitanCutout(it.left, it.top, it.right, it.bottom, dpi)) {
                    RingGeometry.titanFitted(dpi)
                } else {
                    RingGeometry.aroundCutout(it.left, it.top, it.right, it.bottom, gapPx(), strokePx())
                }
            })
            insets
        }
        setContentView(view)

        demo = intent.getBooleanExtra(EXTRA_DEMO, false)
        intent.toSource()?.let { addSource(it) }
        if (sources.isEmpty() && !demo) {
            finish()
            return
        }

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        startBreathing()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toSource()?.let { addSource(it) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Some launches deliver no insets callback; make sure the ring has a home either way.
        if (view.ring == null) placeRing(null)
    }

    private fun placeRing(fromCutout: RingGeometry.Ring?) {
        view.ring = fromCutout
            ?: RingGeometry.titanFallback(resources.displayMetrics.densityDpi, gapPx(), strokePx())
    }

    private fun gapPx() = 3f * resources.displayMetrics.density
    private fun strokePx() = 3f * resources.displayMetrics.density

    /** A new notification while the ring is up: recolour, add its icon, and restart the clock. */
    fun addSource(source: RingSource) {
        sources[source.key] = source
        view.color = source.color
        view.icons = sources.values.map { it.packageName }.distinct().takeLast(MAX_ICONS)
            .mapNotNull { appIcon(it) }
        armExpiry()
    }

    /** The notification was dismissed elsewhere; when nothing is left, so is the ring. */
    fun removeSource(key: String) {
        if (sources.remove(key) == null) return
        if (sources.isEmpty() && !demo) {
            finish()
            return
        }
        sources.values.lastOrNull()?.let { view.color = it.color }
        view.icons = sources.values.map { it.packageName }.distinct().takeLast(MAX_ICONS)
            .mapNotNull { appIcon(it) }
    }

    private fun appIcon(packageName: String): Drawable? {
        if (!SettingsManager.isNotificationRingIconsEnabled(this)) return null
        return runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
    }

    private fun armExpiry() {
        handler.removeCallbacks(expire)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val minutes = if (demo) 0 else SettingsManager.getNotificationRingMinutes(this)
        val delay = if (demo) DEMO_MS else minutes * 60_000L
        handler.postDelayed(expire, delay)
    }

    private fun startBreathing() {
        breathing?.cancel()
        breathing = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BREATH_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { view.breath = it.animatedValue as Float }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) finish()
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Any key on the physical keyboard means the user is here.
        if (event.action == KeyEvent.ACTION_DOWN) finish()
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(expire)
        breathing?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        if (current === this) current = null
    }

    private fun Intent.toSource(): RingSource? {
        val key = getStringExtra(EXTRA_KEY) ?: return null
        return RingSource(
            key = key,
            packageName = getStringExtra(EXTRA_PACKAGE) ?: packageName,
            color = getIntExtra(EXTRA_COLOR, NotificationRingPolicy.DEFAULT_COLOR)
        )
    }

    companion object {
        private const val TAG = "NotificationRing"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_COLOR = "color"
        private const val EXTRA_DEMO = "demo"
        private const val MAX_ICONS = 3
        private const val BREATH_MS = 1_800L
        private const val DEMO_MS = 8_000L

        /** The ring on screen right now, if any. Main-thread reads only. */
        @Volatile
        var current: NotificationRingActivity? = null
            private set

        fun intent(context: Context, source: RingSource): Intent =
            Intent(context, NotificationRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_KEY, source.key)
                .putExtra(EXTRA_PACKAGE, source.packageName)
                .putExtra(EXTRA_COLOR, source.color)

        /** A ring for the settings screen's "Try it": PhysiBoard's own icon, a few seconds. */
        fun demoIntent(context: Context): Intent =
            intent(context, RingSource("demo", context.packageName, NotificationRingPolicy.DEFAULT_COLOR))
                .putExtra(EXTRA_DEMO, true)
    }
}
