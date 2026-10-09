package brobata.physiboard.core.actions.feedback

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId

/**
 * PhysiBoard's haptic language: every moment the keyboard or the settings app answers with a
 * vibration, what each one feels like, and how it degrades on hardware that cannot play it.
 * Pure rules; `:ime`'s `HapticPlayer` turns a [HapticPlan] into a `VibrationEffect`.
 *
 * spec: keys-and-modifiers.md SS13.5 (the language, the gates, the fallback ladder).
 */
enum class HapticEvent(val scope: HapticScope, val priority: Int) {
    /** An ordinary key going down: the lightest thing in the language. */
    KEY(HapticScope.KEY, 0),
    MODIFIER_RELEASE(HapticScope.EVENT, 10),
    SYM_STEP(HapticScope.EVENT, 20),
    MODIFIER_ONE_SHOT(HapticScope.EVENT, 30),
    SYM_CLOSE(HapticScope.EVENT, 40),
    SYM_OPEN(HapticScope.EVENT, 50),
    MODIFIER_LOCK(HapticScope.EVENT, 60),
    /** An accent, a skin tone or another chooser pick landing in the text. */
    PICK(HapticScope.EVENT, 70),
    /** Autocorrect (or a mix-up fix, a case repair, a text replacement) rewrote the word. */
    CORRECTION(HapticScope.EVENT, 80),
    /** Backspace put the word back the way it was typed. */
    CORRECTION_UNDONE(HapticScope.EVENT, 90),
    /** A long press fired: the alternate character, or the accent chooser opening. */
    LONG_PRESS(HapticScope.EVENT, 100),
    NAV_MODE(HapticScope.EVENT, 110),
    /** The keyboard declined to do what was asked (Sym with no text box, a failed save). */
    REFUSAL(HapticScope.EVENT, 120),

    // The settings app.
    TOGGLE_ON(HapticScope.EVENT, 0),
    TOGGLE_OFF(HapticScope.EVENT, 0),
    /** A chip, a dropdown entry, a radio choice. */
    SELECT(HapticScope.EVENT, 0),
    /** One detent of a slider or a stepper. */
    STEP(HapticScope.EVENT, 0),
    REORDER(HapticScope.EVENT, 0),
    /** The confirm button of one of the two device-level resets, or an action that throws something away. */
    CONFIRM_DESTRUCTIVE(HapticScope.EVENT, 0),
    /** Pulling a folded screen title back into view. */
    REVEAL(HapticScope.EVENT, 0),
    /** "Undo" on a snackbar put the prior state back. */
    UNDO(HapticScope.EVENT, 0),
}

/** Which switch gates an event: `key_haptics` for [KEY], `event_haptics` for everything else. */
enum class HapticScope { KEY, EVENT }

/** `key_haptic_strength`. Only the key tick scales; every other event has one fixed feel. */
enum class HapticIntensity(val storedValue: String) {
    LIGHT("light"), STANDARD("standard"), STRONG("strong");

    companion object {
        fun fromStored(value: String?): HapticIntensity? = entries.firstOrNull { it.storedValue == value }
    }
}

/** The `VibrationEffect.Composition` primitives the language uses (Android 11+, where the actuator supports them). */
enum class HapticPrimitive { TICK, LOW_TICK, CLICK, THUD, SPIN, QUICK_RISE, SLOW_RISE, QUICK_FALL }

/** The predefined effects (Android 10+), tuned by the phone's maker; the middle rung of the ladder. */
enum class PredefinedHaptic { TICK, CLICK, HEAVY_CLICK, DOUBLE_CLICK }

/** One primitive of a composition: [scale] 0..1, played [delayMs] after the previous one ended. */
data class PrimitiveStep(val primitive: HapticPrimitive, val scale: Float, val delayMs: Int = 0)

/** What the actuator can do, asked once per process. */
data class HapticCapabilities(
    val hasVibrator: Boolean,
    val primitives: Set<HapticPrimitive>,
    val predefined: Set<PredefinedHaptic>,
    val amplitudeControl: Boolean,
) {
    companion object {
        val NONE = HapticCapabilities(hasVibrator = false, primitives = emptySet(), predefined = emptySet(), amplitudeControl = false)
    }
}

/** The one effect to play, already chosen for the hardware. */
sealed interface HapticPlan {
    data class Composition(val steps: List<PrimitiveStep>) : HapticPlan
    data class Predefined(val effect: PredefinedHaptic) : HapticPlan

    /** [amplitude] 1..255, or [DEFAULT_AMPLITUDE] for the actuator's own level. */
    data class OneShot(val durationMs: Long, val amplitude: Int) : HapticPlan

    /** Alternating off/on segments starting with off, as `VibrationEffect.createWaveform` takes them. */
    data class Waveform(val timingsMs: List<Long>, val amplitudes: List<Int>) : HapticPlan

    data object Silent : HapticPlan

    companion object {
        const val DEFAULT_AMPLITUDE: Int = -1
    }
}

/**
 * One event's three rungs: the composition for an actuator with primitives, the predefined effect
 * for one with the maker's tuned clicks, and a plain pulse ([basic]) for anything else.
 */
data class HapticRecipe(val composition: List<PrimitiveStep>, val predefined: PredefinedHaptic?, val basic: HapticPlan)

object HapticLanguage {

    /** The same event inside this window plays once: a slider dragged across twenty detents is a texture, not twenty knocks. */
    const val REPEAT_WINDOW_MS: Long = 35

    /** spec SS13.5's table. [intensity] matters only for [HapticEvent.KEY]. */
    fun recipe(event: HapticEvent, intensity: HapticIntensity = HapticIntensity.LIGHT): HapticRecipe = when (event) {
        HapticEvent.KEY -> when (intensity) {
            HapticIntensity.LIGHT -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.TICK, 0.35f)), PredefinedHaptic.TICK, oneShot(8, 60))
            HapticIntensity.STANDARD -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.TICK, 0.6f)), PredefinedHaptic.CLICK, oneShot(12, 120))
            HapticIntensity.STRONG -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.CLICK, 0.8f)), PredefinedHaptic.HEAVY_CLICK, oneShot(18, 200))
        }
        HapticEvent.MODIFIER_ONE_SHOT -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.CLICK, 0.5f)), PredefinedHaptic.CLICK, oneShot(15, 140))
        HapticEvent.MODIFIER_LOCK -> HapticRecipe(
            listOf(PrimitiveStep(HapticPrimitive.CLICK, 0.9f), PrimitiveStep(HapticPrimitive.TICK, 0.5f, delayMs = 40)),
            PredefinedHaptic.HEAVY_CLICK,
            oneShot(30, 220),
        )
        HapticEvent.MODIFIER_RELEASE -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.LOW_TICK, 0.6f)), PredefinedHaptic.TICK, oneShot(8, 80))
        HapticEvent.SYM_OPEN -> HapticRecipe(
            listOf(PrimitiveStep(HapticPrimitive.QUICK_RISE, 0.4f), PrimitiveStep(HapticPrimitive.CLICK, 0.6f)),
            PredefinedHaptic.CLICK,
            oneShot(15, 150),
        )
        HapticEvent.SYM_CLOSE -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.QUICK_FALL, 0.4f)), PredefinedHaptic.TICK, oneShot(10, 100))
        HapticEvent.SYM_STEP -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.TICK, 0.6f)), PredefinedHaptic.TICK, oneShot(10, 110))
        HapticEvent.PICK -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.CLICK, 0.7f)), PredefinedHaptic.CLICK, oneShot(15, 170))
        // Subtle: the word changing is the news, the vibration only says it was the keyboard.
        HapticEvent.CORRECTION -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.TICK, 0.4f)), PredefinedHaptic.TICK, oneShot(10, HapticPlan.DEFAULT_AMPLITUDE))
        HapticEvent.CORRECTION_UNDONE -> HapticRecipe(
            listOf(PrimitiveStep(HapticPrimitive.QUICK_FALL, 0.5f), PrimitiveStep(HapticPrimitive.TICK, 0.5f)),
            PredefinedHaptic.CLICK,
            oneShot(15, 150),
        )
        HapticEvent.LONG_PRESS -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.THUD, 0.6f)), PredefinedHaptic.HEAVY_CLICK, oneShot(30, 220))
        // trackpad-caret-nav.md SS5.7's 70 ms pulse stays the fallback: there is no tuned effect that long.
        HapticEvent.NAV_MODE -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.THUD, 0.8f)), null, oneShot(70, HapticPlan.DEFAULT_AMPLITUDE))
        // A soft double: two low ticks, never a buzz.
        HapticEvent.REFUSAL -> HapticRecipe(
            listOf(PrimitiveStep(HapticPrimitive.LOW_TICK, 0.7f), PrimitiveStep(HapticPrimitive.LOW_TICK, 0.7f, delayMs = 70)),
            PredefinedHaptic.DOUBLE_CLICK,
            HapticPlan.Waveform(listOf(0L, 12L, 70L, 12L), listOf(0, 140, 0, 140)),
        )
        HapticEvent.TOGGLE_ON -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.CLICK, 0.6f)), PredefinedHaptic.CLICK, oneShot(15, 150))
        HapticEvent.TOGGLE_OFF -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.LOW_TICK, 0.7f)), PredefinedHaptic.TICK, oneShot(10, 100))
        HapticEvent.SELECT -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.TICK, 0.7f)), PredefinedHaptic.TICK, oneShot(10, 120))
        HapticEvent.STEP -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.LOW_TICK, 0.5f)), PredefinedHaptic.TICK, oneShot(6, 80))
        HapticEvent.REORDER -> HapticRecipe(
            listOf(PrimitiveStep(HapticPrimitive.TICK, 0.5f), PrimitiveStep(HapticPrimitive.CLICK, 0.5f, delayMs = 30)),
            PredefinedHaptic.CLICK,
            oneShot(15, 150),
        )
        HapticEvent.CONFIRM_DESTRUCTIVE -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.THUD, 0.7f)), PredefinedHaptic.HEAVY_CLICK, oneShot(35, 230))
        HapticEvent.REVEAL -> HapticRecipe(listOf(PrimitiveStep(HapticPrimitive.QUICK_RISE, 0.3f)), PredefinedHaptic.TICK, oneShot(8, 80))
        HapticEvent.UNDO -> HapticRecipe(
            listOf(PrimitiveStep(HapticPrimitive.QUICK_FALL, 0.5f), PrimitiveStep(HapticPrimitive.CLICK, 0.5f)),
            PredefinedHaptic.CLICK,
            oneShot(15, 150),
        )
    }

    /**
     * The fallback ladder: the composition when the actuator plays every primitive in it, else
     * the predefined effect when it reports that one, else the plain pulse (at the actuator's own
     * level when it has no amplitude control). Nothing at all without a vibrator.
     */
    fun plan(event: HapticEvent, intensity: HapticIntensity, capabilities: HapticCapabilities): HapticPlan {
        if (!capabilities.hasVibrator) return HapticPlan.Silent
        val recipe = recipe(event, intensity)
        if (recipe.composition.isNotEmpty() && recipe.composition.all { it.primitive in capabilities.primitives }) {
            return HapticPlan.Composition(recipe.composition)
        }
        val predefined = recipe.predefined
        if (predefined != null && predefined in capabilities.predefined) return HapticPlan.Predefined(predefined)
        return when (val basic = recipe.basic) {
            is HapticPlan.OneShot -> if (capabilities.amplitudeControl) basic else basic.copy(amplitude = HapticPlan.DEFAULT_AMPLITUDE)
            is HapticPlan.Waveform -> if (capabilities.amplitudeControl) basic else basic.copy(amplitudes = basic.amplitudes.map { if (it == 0) 0 else 255 })
            else -> basic
        }
    }

    /**
     * Whether [event] may play. The phone's own "touch feedback" switch silences all of it, as it
     * does dictation's cues (dictation.md SS8.1); then the key tick follows `key_haptics` and the
     * rest follow `event_haptics`.
     */
    fun allows(event: HapticEvent, systemHapticsOn: Boolean, keyHaptics: Boolean, eventHaptics: Boolean): Boolean {
        if (!systemHapticsOn) return false
        return when (event.scope) {
            HapticScope.KEY -> keyHaptics
            HapticScope.EVENT -> eventHaptics
        }
    }

    /** The key tick plays on a key's first down, as typing sounds do (SS9.1), except Back and the modifiers, which have events of their own. */
    fun keyTickApplies(key: KeyId, repeatCount: Int): Boolean =
        repeatCount == 0 && key !is KeyId.Modifier && key != KeyId.Control(ControlKey.BACK)

    /** Of two events in the same keystroke, the one that says more; the other is dropped, never played as well. */
    fun stronger(a: HapticEvent?, b: HapticEvent?): HapticEvent? = when {
        a == null -> b
        b == null -> a
        b.priority > a.priority -> b
        else -> a
    }

    /** What a modifier key press or release did to the modifier state, as an event; null when nothing changed. */
    fun modifierChange(before: ModifierLevels, after: ModifierLevels): HapticEvent? {
        var rose: ModifierLevel? = null
        var fell = false
        for (i in 0 until ModifierLevels.COUNT) {
            val b = before[i]
            val a = after[i]
            if (a.ordinal > b.ordinal) {
                if (rose == null || a.ordinal > rose.ordinal) rose = a
            } else if (a.ordinal < b.ordinal) {
                fell = true
            }
        }
        return when (rose) {
            ModifierLevel.LOCKED -> HapticEvent.MODIFIER_LOCK
            ModifierLevel.ONE_SHOT -> HapticEvent.MODIFIER_ONE_SHOT
            else -> if (fell) HapticEvent.MODIFIER_RELEASE else null
        }
    }

    /** A Sym page change: 0 is closed. */
    fun symChange(pageBefore: Int, pageAfter: Int): HapticEvent? = when {
        pageBefore == pageAfter -> null
        pageBefore == 0 -> HapticEvent.SYM_OPEN
        pageAfter == 0 -> HapticEvent.SYM_CLOSE
        else -> HapticEvent.SYM_STEP
    }

    private fun oneShot(ms: Long, amplitude: Int) = HapticPlan.OneShot(ms, amplitude)
}

/** How far one latching modifier is engaged. A physical hold is not a level: holding Shift to type a capital says nothing. */
enum class ModifierLevel { OFF, ONE_SHOT, LOCKED }

/** Shift, Alt and Ctrl's levels at one moment. */
data class ModifierLevels(val shift: ModifierLevel, val alt: ModifierLevel, val ctrl: ModifierLevel) {
    operator fun get(index: Int): ModifierLevel = when (index) {
        0 -> shift
        1 -> alt
        else -> ctrl
    }

    companion object {
        const val COUNT = 3
        val NONE = ModifierLevels(ModifierLevel.OFF, ModifierLevel.OFF, ModifierLevel.OFF)

        fun of(locked: Boolean, oneShot: Boolean): ModifierLevel = when {
            locked -> ModifierLevel.LOCKED
            oneShot -> ModifierLevel.ONE_SHOT
            else -> ModifierLevel.OFF
        }
    }
}
