package brobata.physiboard.core.actions.feedback

import brobata.physiboard.core.keys.ControlKey
import brobata.physiboard.core.keys.KeyId
import brobata.physiboard.core.keys.ModifierKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: keys-and-modifiers.md SS13.5 and its test rows T-H1 to T-H8. */
class HapticLanguageTest {

    private val everything = HapticCapabilities(
        hasVibrator = true,
        primitives = HapticPrimitive.entries.toSet(),
        predefined = PredefinedHaptic.entries.toSet(),
        amplitudeControl = true,
    )

    /** The Titan 2 Elite as `dumpsys vibrator_manager` reports it: tuned effects, no primitives, no amplitude control. */
    private val titan = HapticCapabilities(
        hasVibrator = true,
        primitives = emptySet(),
        predefined = setOf(PredefinedHaptic.TICK, PredefinedHaptic.CLICK, PredefinedHaptic.HEAVY_CLICK, PredefinedHaptic.DOUBLE_CLICK),
        amplitudeControl = false,
    )

    private val bare = HapticCapabilities(hasVibrator = true, primitives = emptySet(), predefined = emptySet(), amplitudeControl = false)

    @Test
    fun `T-H1 the ladder picks the composition when every primitive is there`() {
        val plan = HapticLanguage.plan(HapticEvent.MODIFIER_LOCK, HapticIntensity.LIGHT, everything)
        assertEquals(HapticPlan.Composition(HapticLanguage.recipe(HapticEvent.MODIFIER_LOCK).composition), plan)
    }

    @Test
    fun `T-H1 a composition with one missing primitive falls to the predefined effect`() {
        val missingRise = everything.copy(primitives = everything.primitives - HapticPrimitive.QUICK_RISE)
        assertEquals(HapticPlan.Predefined(PredefinedHaptic.CLICK), HapticLanguage.plan(HapticEvent.SYM_OPEN, HapticIntensity.LIGHT, missingRise))
    }

    @Test
    fun `T-H2 on the Titan every event lands on a tuned effect except nav mode`() {
        for (event in HapticEvent.entries) {
            val plan = HapticLanguage.plan(event, HapticIntensity.LIGHT, titan)
            if (event == HapticEvent.NAV_MODE) {
                assertEquals(HapticPlan.OneShot(70, HapticPlan.DEFAULT_AMPLITUDE), plan, "nav mode keeps its 70 ms pulse")
            } else {
                assertTrue(plan is HapticPlan.Predefined, "$event on the Titan was $plan")
            }
        }
    }

    @Test
    fun `T-H2 the events people tell apart stay apart on the Titan`() {
        fun on(event: HapticEvent) = HapticLanguage.plan(event, HapticIntensity.LIGHT, titan)
        // A one-shot and a lock must not feel the same; nor a correction and its undo; nor a refusal and anything else.
        assertTrue(on(HapticEvent.MODIFIER_ONE_SHOT) != on(HapticEvent.MODIFIER_LOCK))
        assertTrue(on(HapticEvent.CORRECTION) != on(HapticEvent.CORRECTION_UNDONE))
        assertTrue(on(HapticEvent.SYM_OPEN) != on(HapticEvent.SYM_CLOSE))
        assertTrue(on(HapticEvent.TOGGLE_ON) != on(HapticEvent.TOGGLE_OFF))
        val refusal = on(HapticEvent.REFUSAL)
        assertEquals(HapticPlan.Predefined(PredefinedHaptic.DOUBLE_CLICK), refusal)
        assertEquals(1, HapticEvent.entries.count { on(it) == refusal }, "only a refusal is a double")
    }

    @Test
    fun `T-H3 key strength scales the tick at every rung`() {
        val tuned = HapticIntensity.entries.map { HapticLanguage.plan(HapticEvent.KEY, it, titan) }
        assertEquals(
            listOf(PredefinedHaptic.TICK, PredefinedHaptic.CLICK, PredefinedHaptic.HEAVY_CLICK).map { HapticPlan.Predefined(it) },
            tuned,
        )
        val scales = HapticIntensity.entries.map { (HapticLanguage.plan(HapticEvent.KEY, it, everything) as HapticPlan.Composition).steps.single().scale }
        assertEquals(scales.sorted(), scales)
        assertTrue(scales.first() < 0.5f, "the default tick is very light")
        // The strength is the key tick's alone.
        assertEquals(HapticLanguage.plan(HapticEvent.PICK, HapticIntensity.LIGHT, titan), HapticLanguage.plan(HapticEvent.PICK, HapticIntensity.STRONG, titan))
    }

    @Test
    fun `T-H4 a bare vibrator gets plain pulses at its own level, and no vibrator gets nothing`() {
        val tick = HapticLanguage.plan(HapticEvent.KEY, HapticIntensity.LIGHT, bare)
        assertEquals(HapticPlan.OneShot(8, HapticPlan.DEFAULT_AMPLITUDE), tick)
        val refusal = HapticLanguage.plan(HapticEvent.REFUSAL, HapticIntensity.LIGHT, bare) as HapticPlan.Waveform
        assertEquals(listOf(0, 255, 0, 255), refusal.amplitudes, "on/off only without amplitude control")
        assertEquals(HapticPlan.OneShot(8, 60), HapticLanguage.plan(HapticEvent.KEY, HapticIntensity.LIGHT, bare.copy(amplitudeControl = true)))
        for (event in HapticEvent.entries) assertEquals(HapticPlan.Silent, HapticLanguage.plan(event, HapticIntensity.STRONG, HapticCapabilities.NONE))
    }

    @Test
    fun `T-H5 the gates`() {
        // The system's touch feedback switch silences everything.
        for (event in HapticEvent.entries) assertFalse(HapticLanguage.allows(event, systemHapticsOn = false, keyHaptics = true, eventHaptics = true))
        assertFalse(HapticLanguage.allows(HapticEvent.KEY, systemHapticsOn = true, keyHaptics = false, eventHaptics = true))
        assertTrue(HapticLanguage.allows(HapticEvent.KEY, systemHapticsOn = true, keyHaptics = true, eventHaptics = false))
        assertFalse(HapticLanguage.allows(HapticEvent.CORRECTION, systemHapticsOn = true, keyHaptics = true, eventHaptics = false))
        assertTrue(HapticLanguage.allows(HapticEvent.TOGGLE_ON, systemHapticsOn = true, keyHaptics = false, eventHaptics = true))
    }

    @Test
    fun `T-H6 the key tick plays on a first down only, never on Back or a modifier`() {
        assertTrue(HapticLanguage.keyTickApplies(KeyId.Letter('A'), 0))
        assertTrue(HapticLanguage.keyTickApplies(KeyId.Control(ControlKey.SPACE), 0))
        assertFalse(HapticLanguage.keyTickApplies(KeyId.Letter('A'), 1), "auto-repeat is silent")
        assertFalse(HapticLanguage.keyTickApplies(KeyId.Control(ControlKey.BACK), 0))
        for (m in ModifierKey.entries) assertFalse(HapticLanguage.keyTickApplies(KeyId.Modifier(m), 0))
    }

    @Test
    fun `T-H7 modifier changes`() {
        val off = ModifierLevels.NONE
        val shiftOnce = off.copy(shift = ModifierLevel.ONE_SHOT)
        val capsLock = off.copy(shift = ModifierLevel.LOCKED)
        assertEquals(HapticEvent.MODIFIER_ONE_SHOT, HapticLanguage.modifierChange(off, shiftOnce))
        assertEquals(HapticEvent.MODIFIER_LOCK, HapticLanguage.modifierChange(shiftOnce, capsLock))
        assertEquals(HapticEvent.MODIFIER_LOCK, HapticLanguage.modifierChange(off, capsLock))
        assertEquals(HapticEvent.MODIFIER_RELEASE, HapticLanguage.modifierChange(capsLock, off))
        assertNull(HapticLanguage.modifierChange(shiftOnce, shiftOnce))
        // One rising while another falls (Alt armed as Shift's one-shot lapses): the rise is the news.
        assertEquals(HapticEvent.MODIFIER_ONE_SHOT, HapticLanguage.modifierChange(shiftOnce, off.copy(alt = ModifierLevel.ONE_SHOT)))
        assertEquals(ModifierLevel.LOCKED, ModifierLevels.of(locked = true, oneShot = true))
    }

    @Test
    fun `T-H8 Sym pages and one event per keystroke`() {
        assertEquals(HapticEvent.SYM_OPEN, HapticLanguage.symChange(0, 1))
        assertEquals(HapticEvent.SYM_STEP, HapticLanguage.symChange(1, 2))
        assertEquals(HapticEvent.SYM_CLOSE, HapticLanguage.symChange(3, 0))
        assertNull(HapticLanguage.symChange(2, 2))
        assertEquals(HapticEvent.CORRECTION, HapticLanguage.stronger(HapticEvent.KEY, HapticEvent.CORRECTION))
        assertEquals(HapticEvent.REFUSAL, HapticLanguage.stronger(HapticEvent.REFUSAL, HapticEvent.SYM_OPEN))
        assertEquals(HapticEvent.CORRECTION_UNDONE, HapticLanguage.stronger(HapticEvent.CORRECTION_UNDONE, HapticEvent.CORRECTION))
        assertNull(HapticLanguage.stronger(null, null))
        assertEquals(HapticEvent.KEY, HapticLanguage.stronger(null, HapticEvent.KEY))
    }

    @Test
    fun `every recipe is playable and every composition stays in range`() {
        for (event in HapticEvent.entries) for (intensity in HapticIntensity.entries) {
            val recipe = HapticLanguage.recipe(event, intensity)
            assertTrue(recipe.composition.isNotEmpty(), "$event has a composition")
            for (step in recipe.composition) {
                assertTrue(step.scale in 0f..1f, "$event scale ${step.scale}")
                assertTrue(step.delayMs in 0..1000, "$event delay ${step.delayMs}")
            }
            when (val basic = recipe.basic) {
                is HapticPlan.OneShot -> assertTrue(basic.durationMs in 1..100 && (basic.amplitude == HapticPlan.DEFAULT_AMPLITUDE || basic.amplitude in 1..255))
                is HapticPlan.Waveform -> assertEquals(basic.timingsMs.size, basic.amplitudes.size)
                else -> error("$event's basic rung is $basic")
            }
        }
    }
}
