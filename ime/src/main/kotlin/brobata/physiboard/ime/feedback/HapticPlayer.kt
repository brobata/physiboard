package brobata.physiboard.ime.feedback

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import brobata.physiboard.core.actions.feedback.HapticCapabilities
import brobata.physiboard.core.actions.feedback.HapticEvent
import brobata.physiboard.core.actions.feedback.HapticIntensity
import brobata.physiboard.core.actions.feedback.HapticLanguage
import brobata.physiboard.core.actions.feedback.HapticPlan
import brobata.physiboard.core.actions.feedback.HapticPrimitive
import brobata.physiboard.core.actions.feedback.PredefinedHaptic

/**
 * Plays [HapticLanguage]'s events on this phone's actuator, for the keyboard and the settings app
 * alike (keys-and-modifiers.md SS13.5).
 *
 * Everything that costs anything happens once, here in the constructor: the actuator is asked
 * which primitives and tuned effects it has, every event's `VibrationEffect` is built for it, and
 * the system's touch-feedback switch is read and then followed through a content observer. A key
 * press then costs two array reads, a clock read and a `Handler.post` of a runnable that already
 * exists; the binder call to the vibrator service runs on this player's own thread, never on the
 * keystroke path.
 *
 * Every effect goes through plain `Vibrator.vibrate`, with no audio attributes: notification-class
 * vibration is muted whenever the phone's notification vibration is off (dictation.md SS8.1, D5),
 * and that switch must never silence typing feedback.
 */
class HapticPlayer(context: Context) {

    private val appContext = context.applicationContext

    private val vibrator: Vibrator? = runCatching {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    }.getOrNull()?.takeIf { runCatching { it.hasVibrator() }.getOrDefault(false) }

    /** What the actuator reported; also shown in the debug trail. */
    val capabilities: HapticCapabilities = queryCapabilities(vibrator)

    /** `key_haptics`, `key_haptic_strength` and `event_haptics`, set from each settings emission. */
    @Volatile var keyHaptics: Boolean = false

    @Volatile var keyStrength: HapticIntensity = HapticIntensity.LIGHT

    @Volatile var eventHaptics: Boolean = true

    @Volatile private var systemHapticsOn: Boolean = readSystemHaptics()

    private val thread: HandlerThread = HandlerThread("PhysiBoardHaptics").apply { start() }
    private val handler = Handler(thread.looper)

    /** One prebuilt runnable per (event, intensity); null where the plan is silent. */
    private val runnables: Array<Runnable?> = Array(HapticEvent.entries.size * INTENSITIES) { index ->
        val event = HapticEvent.entries[index / INTENSITIES]
        val intensity = HapticIntensity.entries[index % INTENSITIES]
        val effect = runCatching { effectFor(HapticLanguage.plan(event, intensity, capabilities)) }.getOrNull()
        val v = vibrator
        if (effect == null || v == null) null else Runnable { runCatching { v.vibrate(effect) } }
    }

    private val lastPlayedAt = LongArray(HapticEvent.entries.size) { Long.MIN_VALUE / 2 }

    private val systemObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            systemHapticsOn = readSystemHaptics()
        }
    }

    init {
        runCatching {
            appContext.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED), false, systemObserver)
        }.onFailure { error -> Log.w(TAG, "cannot follow the touch feedback switch; reading it once", error) }
    }

    /** Plays [event] if its gate allows it; the same event inside [HapticLanguage.REPEAT_WINDOW_MS] plays once. Safe on any thread that the caller already owns (the main thread here). */
    fun play(event: HapticEvent) {
        if (!HapticLanguage.allows(event, systemHapticsOn, keyHaptics, eventHaptics)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPlayedAt[event.ordinal] < HapticLanguage.REPEAT_WINDOW_MS) return
        lastPlayedAt[event.ordinal] = now
        val intensity = if (event == HapticEvent.KEY) keyStrength else HapticIntensity.LIGHT
        val runnable = runnables[event.ordinal * INTENSITIES + intensity.ordinal] ?: return
        handler.post(runnable)
    }

    /** Plays the key tick at [intensity] right now regardless of `key_haptics`, as the strength chips' preview. Still silent with the system switch off. */
    fun preview(event: HapticEvent, intensity: HapticIntensity) {
        if (!systemHapticsOn) return
        val runnable = runnables[event.ordinal * INTENSITIES + intensity.ordinal] ?: return
        handler.post(runnable)
    }

    fun release() {
        runCatching { appContext.contentResolver.unregisterContentObserver(systemObserver) }
        thread.quitSafely()
    }

    /** One line for the debug trail: which rung each kind of event lands on here. */
    fun describe(): String =
        "vibrator=${capabilities.hasVibrator} primitives=${capabilities.primitives.joinToString(",")} " +
            "predefined=${capabilities.predefined.joinToString(",")} amplitude=${capabilities.amplitudeControl}"

    private fun readSystemHaptics(): Boolean = runCatching {
        Settings.System.getInt(appContext.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    }.getOrDefault(true)

    private companion object {
        const val TAG = "PhysiBoardHaptics"
        val INTENSITIES = HapticIntensity.entries.size

        val PRIMITIVE_IDS: Map<HapticPrimitive, Int> = mapOf(
            HapticPrimitive.TICK to VibrationEffect.Composition.PRIMITIVE_TICK,
            HapticPrimitive.LOW_TICK to VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            HapticPrimitive.CLICK to VibrationEffect.Composition.PRIMITIVE_CLICK,
            HapticPrimitive.THUD to VibrationEffect.Composition.PRIMITIVE_THUD,
            HapticPrimitive.SPIN to VibrationEffect.Composition.PRIMITIVE_SPIN,
            HapticPrimitive.QUICK_RISE to VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            HapticPrimitive.SLOW_RISE to VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            HapticPrimitive.QUICK_FALL to VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
        )

        val EFFECT_IDS: Map<PredefinedHaptic, Int> = mapOf(
            PredefinedHaptic.TICK to VibrationEffect.EFFECT_TICK,
            PredefinedHaptic.CLICK to VibrationEffect.EFFECT_CLICK,
            PredefinedHaptic.HEAVY_CLICK to VibrationEffect.EFFECT_HEAVY_CLICK,
            PredefinedHaptic.DOUBLE_CLICK to VibrationEffect.EFFECT_DOUBLE_CLICK,
        )

        /**
         * A primitive counts only when the actuator says yes. A tuned effect counts unless it says
         * no: "unknown" is what a HAL that never reports answers, and the platform then plays its
         * own fallback for the effect, which is still the right shape.
         */
        fun queryCapabilities(vibrator: Vibrator?): HapticCapabilities {
            if (vibrator == null) return HapticCapabilities.NONE
            return runCatching {
                val primitiveKeys = PRIMITIVE_IDS.keys.toList()
                val primitiveAnswers = vibrator.arePrimitivesSupported(*primitiveKeys.map { PRIMITIVE_IDS.getValue(it) }.toIntArray())
                val effectKeys = EFFECT_IDS.keys.toList()
                val effectAnswers = vibrator.areEffectsSupported(*effectKeys.map { EFFECT_IDS.getValue(it) }.toIntArray())
                HapticCapabilities(
                    hasVibrator = true,
                    primitives = primitiveKeys.filterIndexed { i, _ -> primitiveAnswers.getOrElse(i) { false } }.toSet(),
                    predefined = effectKeys.filterIndexed { i, _ -> effectAnswers.getOrElse(i) { Vibrator.VIBRATION_EFFECT_SUPPORT_NO } != Vibrator.VIBRATION_EFFECT_SUPPORT_NO }.toSet(),
                    amplitudeControl = vibrator.hasAmplitudeControl(),
                )
            }.getOrElse { error ->
                Log.w(TAG, "the vibrator would not describe itself; plain pulses only", error)
                HapticCapabilities(hasVibrator = true, primitives = emptySet(), predefined = emptySet(), amplitudeControl = false)
            }
        }

        fun effectFor(plan: HapticPlan): VibrationEffect? = when (plan) {
            is HapticPlan.Composition -> {
                val composition = VibrationEffect.startComposition()
                for (step in plan.steps) composition.addPrimitive(PRIMITIVE_IDS.getValue(step.primitive), step.scale, step.delayMs)
                composition.compose()
            }
            is HapticPlan.Predefined -> VibrationEffect.createPredefined(EFFECT_IDS.getValue(plan.effect))
            is HapticPlan.OneShot -> VibrationEffect.createOneShot(
                plan.durationMs,
                if (plan.amplitude == HapticPlan.DEFAULT_AMPLITUDE) VibrationEffect.DEFAULT_AMPLITUDE else plan.amplitude,
            )
            is HapticPlan.Waveform -> VibrationEffect.createWaveform(plan.timingsMs.toLongArray(), plan.amplitudes.toIntArray(), -1)
            HapticPlan.Silent -> null
        }
    }
}
