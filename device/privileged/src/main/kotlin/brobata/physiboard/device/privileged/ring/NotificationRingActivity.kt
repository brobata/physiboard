package brobata.physiboard.device.privileged.ring

import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
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
import brobata.physiboard.device.titan.Nightstand
import brobata.physiboard.device.titan.NotificationRingBrightness
import brobata.physiboard.device.titan.NotificationRingDuration
import brobata.physiboard.device.titan.NotificationRingGeometry
import brobata.physiboard.device.titan.NotificationRingLifecycle
import brobata.physiboard.device.titan.RingClockBurnIn
import brobata.physiboard.device.titan.RingClockFormat
import brobata.physiboard.device.titan.RingClockIcons
import brobata.physiboard.device.titan.RingEndTrigger
import brobata.physiboard.device.titan.RingOverride
import brobata.physiboard.device.titan.RingSessionState
import brobata.physiboard.device.titan.RingSource
import java.util.Calendar
import java.util.Locale

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
 * The same screen is the clock (SS5.11.1) when `notification_ring_clock` is on, and the charging
 * clock (SS5.11.2) when it was launched for a dark, locked phone on the charger: then it may
 * hold the screen with nothing waiting, and the charger, not the ring's timer, decides how long.
 *
 * spec: device-backlight-ring.md SS5.6 (the window), SS5.7 (creation, ending), SS5.7.1 to SS5.7.3, SS5.11.
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
    private var clockReceiver: BroadcastReceiver? = null
    private var clockOn = false

    /** The three switches of SS5.11.2 are all on (never on a demo). */
    private var nightstandOn = false

    /** SS5.11.2: the charger holds the screen; the ring's timer no longer decides. */
    private var chargingHold = false

    /** The charger has held this screen at some point; unplugging then never brightens it (SS5.11.4). */
    private var heldByCharger = false

    /** The ring's own timer (SS5.7.3) is counting; false on a charging clock nothing new has arrived on. */
    private var timerRunning = false

    /** The window holds keep-screen-on right now, so a screen-off can only be the power key (SS5.11.2). */
    private var holding = false
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
            screenBrightness = ringBrightness()
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        clockOn = settings.device.ringClock
        nightstandOn = !demo && Nightstand.isEnabled(settings.device.ringEnabled, settings.device.ringClock, settings.device.ringStayOnCharging)
        // SS5.11.1: with the clock on, the icons are the clock's grey row, never the coloured one.
        ringView = RingView(this).apply { showIcons = settings.device.ringShowIcons && !clockOn }
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
            val nightstandLaunch = intent.getBooleanExtra(EXTRA_NIGHTSTAND, false)
            if (ring == null || (!nightstandLaunch && intent.getStringExtra(EXTRA_KEY).isNullOrEmpty())) {
                finish()
                return
            }
            services?.ringLauncher?.cancelAnnouncement()
            // SS5.11.2: the charger may hold the screen only over a locked phone, and not after
            // the user put the clock away with the power key.
            chargingHold = Nightstand.holdsScreen(
                featureOn = nightstandOn,
                pluggedIn = AndroidNightstandProbe.isPluggedIn(this),
                keyguardLocked = AndroidNightstandProbe.isKeyguardLocked(this),
                putAway = ring.isPutAway(),
                demo = false,
            )
            sources = ring.onRingCreated(this)
            // A charging-clock launch the charger cannot hold (unplugged or unlocked since it was
            // sent) never shows, waiting notifications or not: it must not sit over an open phone.
            if (!chargingHold && (nightstandLaunch || sources.isEmpty())) {
                finish()
                return
            }
            // A notification starts the ring's timer; the charging clock going up does not.
            timerRunning = !nightstandLaunch
        }
        if (demo) timerRunning = true

        // Step 5: screen off and unlock both end the ring; the charger comes and goes (SS5.11.2).
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, received: Intent?) {
                runCatching {
                    when (received?.action) {
                        Intent.ACTION_SCREEN_OFF -> onEndTrigger(RingEndTrigger.SCREEN_OFF)
                        Intent.ACTION_USER_PRESENT -> onEndTrigger(RingEndTrigger.UNLOCK)
                        Intent.ACTION_POWER_CONNECTED -> onPowerChanged(pluggedIn = true)
                        Intent.ACTION_POWER_DISCONNECTED -> onPowerChanged(pluggedIn = false)
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
                    if (nightstandOn) {
                        addAction(Intent.ACTION_POWER_CONNECTED)
                        addAction(Intent.ACTION_POWER_DISCONNECTED)
                    }
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
        }

        // SS5.11.1: the clock moves on the minute (the system's minute tick), never every second.
        if (clockOn) {
            clockReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, received: Intent?) {
                    runCatching { updateClock() }.onFailure { Log.e(TAG, "clock tick crashed", it) }
                }
            }.also { listener ->
                ContextCompat.registerReceiver(
                    this,
                    listener,
                    IntentFilter().apply {
                        addAction(Intent.ACTION_TIME_TICK)
                        addAction(Intent.ACTION_TIME_CHANGED)
                        addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    },
                    ContextCompat.RECEIVER_EXPORTED,
                )
            }
            updateClock()
        }

        // Step 6 (the breathing animation) is started and stopped by updateWindow.
        applySources(restartTimer = timerRunning)
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

    /**
     * spec: SS5.2 point 4 and SS5.7.3: recolour, redraw the icons and, when a notification
     * arrived ([restartTimer]), re-add keep-screen-on and restart the timer. A notification going
     * away changes what is drawn but not the timer.
     */
    private fun applySources(restartTimer: Boolean) {
        val state = RingSessionState(sources)
        ringView.showRing = sources.isNotEmpty()
        NotificationRingLifecycle.currentColorArgb(state)?.let { ringView.colorArgb = it }
        val showIcons = settings.device.ringShowIcons
        ringView.icons = if (showIcons && !clockOn) {
            NotificationRingLifecycle.icons(state).mapNotNull { pkg -> runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull() }
        } else {
            emptyList()
        }
        // SS5.11.1: the clock's row, one grey icon per waiting app, only the apps the ring holds.
        ringView.clockIcons = if (showIcons && clockOn) {
            val latestKeyByPackage = sources.associate { it.packageName to it.notificationKey }
            RingClockIcons.packages(state).mapNotNull { pkg -> monochromeIcon(pkg, latestKeyByPackage[pkg]) }
        } else {
            emptyList()
        }
        if (restartTimer) {
            timerRunning = true
            handler.removeCallbacks(expiry)
            val durationMs = if (demo) NotificationRingDuration.DEMO_DURATION_MS else NotificationRingDuration.durationMs(settings.device.ringMinutes)
            handler.postDelayed(expiry, durationMs)
        }
        updateWindow()
    }

    /** SS5.11.4: keep-screen-on, brightness and breathing follow the charger and the ring's timer. */
    private fun updateWindow() {
        holding = Nightstand.keepsScreenOn(chargingHold, timerRunning)
        if (holding) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        if (!demo && !tornDown) services?.ring?.onSurfaceHolding(this, holding)
        if (chargingHold) heldByCharger = true
        val brightness = Nightstand.windowBrightness(heldByCharger, timerRunning, ringBrightness())
        if (window.attributes.screenBrightness != brightness) {
            window.attributes = window.attributes.apply { screenBrightness = brightness }
        }
        val breathe = Nightstand.breathes(sources.isNotEmpty(), chargingHold, timerRunning)
        if (breathe && animator == null && !tornDown) {
            // SS5.7 step 6: 1800 ms per leg, linear, forever.
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = BREATH_LEG_MS
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animation -> ringView.breath = animation.animatedValue as Float }
                start()
            }
        } else if (!breathe && animator != null) {
            animator?.cancel()
            animator = null
            ringView.breath = STILL_BREATH
        }
    }

    private fun ringBrightness(): Float = NotificationRingBrightness.fractionFor(settings.device.ringBrightness.storedValue)

    /** SS5.11.1: the time in the phone's 12/24-hour style, the date, and the minute's burn-in position. */
    private fun updateClock() {
        if (!clockOn) return
        val now = Calendar.getInstance()
        val time = RingClockFormat.time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(this))
        val date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), DATE_SKELETON), now).toString()
        val offset = RingClockBurnIn.offsetFor(now.timeInMillis / 60_000L)
        ringView.clock = RingView.ClockFace(time, date, offset.dxDp, offset.dyDp)
    }

    /**
     * SS5.11.1: the notification's own small icon (a one-colour silhouette by Android's rules),
     * tinted grey; if that cannot be had, the app's icon with its colour taken out. Never any of
     * the notification's text.
     */
    private fun monochromeIcon(packageName: String, notificationKey: String?): Drawable? {
        val small = notificationKey
            ?.let { key -> services?.shade?.smallIcon(key) }
            ?.let { icon -> runCatching { icon.loadDrawable(this) }.getOrNull() }
        if (small != null) return small.mutate().apply { setTint(CLOCK_ICON_TINT) }
        val appIcon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull() ?: return null
        return appIcon.mutate().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
    }

    /** SS5.11.2 "Letting go": unplugged, the charger stops holding; plugged back in, it holds again while the phone is locked. */
    private fun onPowerChanged(pluggedIn: Boolean) {
        chargingHold = Nightstand.holdsScreen(
            featureOn = nightstandOn,
            pluggedIn = pluggedIn,
            keyguardLocked = AndroidNightstandProbe.isKeyguardLocked(this),
            putAway = false,
            demo = demo,
        )
        updateWindow()
    }

    override fun onSourcesChanged(sources: List<RingSource>) {
        runCatching {
            val known = this.sources.map { it.notificationKey }.toSet()
            val arrived = sources.any { it.notificationKey !in known }
            this.sources = sources
            applySources(restartTimer = arrived)
        }.onFailure { Log.e(TAG, "onSourcesChanged crashed", it) }
    }

    override fun finishRing() {
        runCatching {
            sources = emptyList()
            if (!onEndTrigger(RingEndTrigger.LAST_SOURCE_REMOVED)) applySources(restartTimer = false)
        }.onFailure { Log.e(TAG, "finishRing crashed", it) }
    }

    /** spec: SS5.7 "Ending": the decision is [NotificationRingLifecycle.onEndTrigger]'s. Returns true when the ring is finishing. */
    private fun onEndTrigger(trigger: RingEndTrigger): Boolean {
        if (trigger == RingEndTrigger.TIMER_EXPIRY) timerRunning = false
        // SS5.11.2 "The power key": reported before finishing, whichever receiver runs first.
        if (trigger == RingEndTrigger.SCREEN_OFF && holding && !demo) services?.ring?.onHeldScreenOff(this)
        val decision = NotificationRingLifecycle.onEndTrigger(trigger, demo, chargingHold)
        if (decision.finishes) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!isFinishing) finish()
            return true
        }
        if (decision.releasesKeepScreenOn) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updateWindow()
        return false
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
        clockReceiver?.let { runCatching { unregisterReceiver(it) } }
        clockReceiver = null
        if (!demo) services?.ring?.onRingDestroyed(this)
    }

    companion object {
        private const val TAG = "NotificationRing"
        const val EXTRA_KEY = "notification_key"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_COLOR = "color"
        const val EXTRA_DEMO = "demo"
        const val DEMO_KEY = "demo"
        const val EXTRA_NIGHTSTAND = "nightstand"

        /** SS5.11.4: the glow's level when it holds still, as a breath value (alpha factor 0.35 + 0.65 x 0.3 = 0.545). */
        const val STILL_BREATH = 0.3f

        /** SS5.11.1: weekday, month, day in the locale's own order ("Sat, Oct 10"). */
        const val DATE_SKELETON = "EEEMMMd"

        /** SS5.11.1: the icons' grey; the view also lowers their alpha. */
        private const val CLOCK_ICON_TINT = 0xFFC8C8C8.toInt()

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

        /** spec: SS5.11.2: the charging clock: no notification key, the ring's own flags. */
        fun nightstandIntent(context: Context): Intent =
            Intent(context, NotificationRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_NIGHTSTAND, true)
    }
}
