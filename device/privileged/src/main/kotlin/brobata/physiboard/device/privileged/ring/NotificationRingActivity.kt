package brobata.physiboard.device.privileged.ring

import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import brobata.physiboard.core.settings.Settings
import brobata.physiboard.device.privileged.PrivilegedServices
import brobata.physiboard.device.titan.CutoutRect
import brobata.physiboard.device.titan.NotificationRingBrightness
import brobata.physiboard.device.titan.NotificationRingDuration
import brobata.physiboard.device.titan.NotificationRingGeometry
import brobata.physiboard.device.titan.NotificationRingLifecycle
import brobata.physiboard.device.titan.RingEndTrigger
import brobata.physiboard.device.titan.RingOverride
import brobata.physiboard.device.titan.RingSessionState
import brobata.physiboard.device.titan.RingSource

/**
 * The ring on screen: an ordinary black activity shown over the lock screen (there is no AOD on
 * this ROM, D30), keeping the screen on for the configured time, ended by the triggers of
 * SS5.7, every ending path through one teardown that hands the keyboard back.
 *
 * What it draws and when it ends are `:device:titan`'s decisions ([NotificationRingGeometry],
 * [NotificationRingLifecycle]); this class owns the window, the two receivers, the breathing
 * animator and the one timer. Whether the fitted geometry lands on the real lens, and whether
 * the system shows this over the Titan's lock screen at all, need the phone.
 *
 * spec: device-backlight-ring.md SS5.6 (the window), SS5.7 (creation, ending), SS5.7.1 to SS5.7.3.
 */
class NotificationRingActivity : Activity(), RingSurface {

    private val handler = Handler(Looper.getMainLooper())
    private val expiry = Runnable { onEndTrigger(RingEndTrigger.TIMER_EXPIRY) }
    private var services: PrivilegedServices? = null
    private var settings: Settings = Settings()
    private lateinit var ringView: RingView
    private var demo = false
    private var sources: List<RingSource> = emptyList()
    private var animator: ValueAnimator? = null
    private var receiver: BroadcastReceiver? = null
    private var placed = false
    private var tornDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { create() }.onFailure { error ->
            Log.e(TAG, "ring creation crashed", error)
            finish()
        }
    }

    /** spec: SS5.7 "On creation the activity", steps 1 to 6. */
    private fun create() {
        services = PrivilegedServices.from(this)
        settings = services?.store?.snapshot() ?: Settings()
        demo = intent.getBooleanExtra(EXTRA_DEMO, false)

        // Step 2: the window.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            screenBrightness = NotificationRingBrightness.fractionFor(settings.device.ringBrightness.storedValue)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        ringView = RingView(this).apply { showIcons = settings.device.ringShowIcons }
        setContentView(ringView)
        // Step 3: place when insets arrive; onAttachedToWindow covers a launch that never delivers them.
        ringView.setOnApplyWindowInsetsListener { _, insets ->
            runCatching { place(insets.displayCutout?.boundingRects?.firstOrNull()) }.onFailure { Log.e(TAG, "placing crashed", it) }
            insets
        }

        // Step 4: the source. Step 1's ownership and announcement cancel happen inside onRingCreated.
        if (demo) {
            sources = listOf(RingSource(DEMO_KEY, packageName, intent.getIntExtra(EXTRA_COLOR, AndroidRingLauncher.DEMO_COLOR_ARGB), System.currentTimeMillis()))
        } else {
            val ring = services?.ring
            if (ring == null || intent.getStringExtra(EXTRA_KEY).isNullOrEmpty()) {
                finish()
                return
            }
            services?.ringLauncher?.cancelAnnouncement()
            sources = ring.onRingCreated(this)
            if (sources.isEmpty()) {
                finish()
                return
            }
        }

        // Step 5: screen off and unlock both end the ring.
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, received: Intent?) {
                runCatching {
                    when (received?.action) {
                        Intent.ACTION_SCREEN_OFF -> onEndTrigger(RingEndTrigger.SCREEN_OFF)
                        Intent.ACTION_USER_PRESENT -> onEndTrigger(RingEndTrigger.UNLOCK)
                    }
                }.onFailure { Log.e(TAG, "receiver crashed", it) }
            }
        }.also { listener ->
            ContextCompat.registerReceiver(
                this,
                listener,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_USER_PRESENT)
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
        }

        // Step 6: the breathing animation, 1800 ms per leg, linear, forever.
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BREATH_LEG_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation -> ringView.breath = animation.animatedValue as Float }
            start()
        }
        applySources()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        runCatching { if (!placed) place(null) }.onFailure { Log.e(TAG, "fallback placing crashed", it) }
    }

    /** spec: SS5.7.1's priority order, resolved by `:device:titan`. */
    private fun place(cutoutRect: Rect?) {
        val densityDpi = resources.displayMetrics.densityDpi.toFloat()
        val override = settings.device.ringFit?.let { RingOverride(it.cx, it.cy, it.radius, it.stroke) }
        val cutout = cutoutRect?.let { CutoutRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
        ringView.geometry = NotificationRingGeometry.resolve(override, cutout, densityDpi)
        placed = true
    }

    /** spec: SS5.2 point 4 and SS5.7.3: recolour, redraw the icons, re-add keep-screen-on, restart the timer. */
    private fun applySources() {
        val state = RingSessionState(sources)
        NotificationRingLifecycle.currentColorArgb(state)?.let { ringView.colorArgb = it }
        ringView.icons = if (settings.device.ringShowIcons) {
            NotificationRingLifecycle.icons(state).mapNotNull { pkg -> runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull() }
        } else {
            emptyList()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.removeCallbacks(expiry)
        val durationMs = if (demo) NotificationRingDuration.DEMO_DURATION_MS else NotificationRingDuration.durationMs(settings.device.ringMinutes)
        handler.postDelayed(expiry, durationMs)
    }

    override fun onSourcesChanged(sources: List<RingSource>) {
        runCatching {
            this.sources = sources
            applySources()
        }.onFailure { Log.e(TAG, "onSourcesChanged crashed", it) }
    }

    override fun finishRing() {
        runCatching { onEndTrigger(RingEndTrigger.LAST_SOURCE_REMOVED) }.onFailure { Log.e(TAG, "finishRing crashed", it) }
    }

    /** spec: SS5.7 "Ending": the decision is [NotificationRingLifecycle.onEndTrigger]'s. */
    private fun onEndTrigger(trigger: RingEndTrigger) {
        val decision = NotificationRingLifecycle.onEndTrigger(trigger, demo)
        if (decision.releasesKeepScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (decision.finishes && !isFinishing) finish()
    }

    /** spec: SS5.7 ("touch (finger up) finishes"). */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        runCatching { if (event.action == MotionEvent.ACTION_UP) onEndTrigger(RingEndTrigger.TOUCH) }.onFailure { Log.e(TAG, "touch crashed", it) }
        return true
    }

    /** spec: SS5.7 ("any key down finishes (and the key is still passed on)"). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        runCatching { if (event.action == KeyEvent.ACTION_DOWN) onEndTrigger(RingEndTrigger.KEY_DOWN) }.onFailure { Log.e(TAG, "key crashed", it) }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        runCatching { teardown() }.onFailure { Log.e(TAG, "teardown crashed", it) }
        super.onDestroy()
    }

    /** spec: SS5.7 ("Every ending path passes through one teardown that restores the keyboard, cancels the timer and animation, unregisters the receiver, and clears 'current ring'"). */
    private fun teardown() {
        if (tornDown) return
        tornDown = true
        handler.removeCallbacksAndMessages(null)
        animator?.cancel()
        animator = null
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        if (!demo) services?.ring?.onRingDestroyed(this)
    }

    companion object {
        private const val TAG = "NotificationRing"
        const val EXTRA_KEY = "notification_key"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_COLOR = "color"
        const val EXTRA_DEMO = "demo"
        const val DEMO_KEY = "demo"

        /** spec: SS5.7 step 6 ("1800 ms per leg"). */
        const val BREATH_LEG_MS = 1_800L

        /** spec: SS5.6 ("new-task and single-top flags ... with the notification key, package and colour as extras"). */
        fun intent(context: Context, notificationKey: String, packageName: String, colorArgb: Int, demo: Boolean): Intent =
            Intent(context, NotificationRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_KEY, notificationKey)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_COLOR, colorArgb)
                .putExtra(EXTRA_DEMO, demo)
    }
}
