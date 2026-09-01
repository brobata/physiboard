package brobata.physiboard

import brobata.physiboard.inputmethod.EnterBehaviorPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Precedence rules for per-app Enter behaviour.
 *
 * The regression these exist for: the tested-package check used to run before the override
 * lookup, so an override on an app outside the shipped list was saved, shown as configured, and
 * silently ignored. Reported by a user who set every control correctly and saw nothing happen.
 */
class EnterBehaviorPolicyTest {

    private val whatsApp = "com.whatsapp"
    private val whatsAppBusiness = "com.whatsapp.w4b"
    private val discord = "com.discord"
    private val presetPackages = setOf(whatsApp, discord, "org.telegram.messenger")

    private fun override(
        pkg: String,
        behavior: String = SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE,
        sendStrategy: String = SettingsManager.ENTER_SEND_STRATEGY_AUTO,
        additionalSendShortcut: String = SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
    ) = SettingsManager.AppEnterBehaviorOverride(pkg, behavior, sendStrategy, additionalSendShortcut)

    private fun behaviorFor(
        pkg: String?,
        overrides: List<SettingsManager.AppEnterBehaviorOverride> = emptyList(),
        enabled: Boolean = true,
        preset: String = SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE
    ) = EnterBehaviorPolicy.resolveBehavior(
        packageName = pkg,
        enabled = enabled,
        overrides = overrides,
        preset = preset,
        presetPackages = presetPackages,
        discordPackage = discord
    )

    // ---- the regression -------------------------------------------------------------------

    @Test
    fun overrideAppliesToAnAppOutsideTheShippedPresetList() {
        val resolved = behaviorFor(whatsAppBusiness, listOf(override(whatsAppBusiness)))

        assertEquals(SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE, resolved)
    }

    @Test
    fun presetDoesNotLeakToAppsOutsideTheShippedList() {
        assertNull(behaviorFor(whatsAppBusiness))
    }

    @Test
    fun overrideBeatsThePresetForTheSameApp() {
        val resolved = behaviorFor(
            whatsApp,
            listOf(override(whatsApp, behavior = SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE))
        )

        assertEquals(SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE, resolved)
    }

    // ---- preset behaviour, unchanged --------------------------------------------------------

    @Test
    fun presetAppliesToAShippedApp() {
        assertEquals(SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE, behaviorFor(whatsApp))
    }

    @Test
    fun discordIsExcludedFromTheSendOnEnterPreset() {
        assertNull(behaviorFor(discord))
    }

    @Test
    fun discordStillAcceptsAnExplicitOverride() {
        val resolved = behaviorFor(discord, listOf(override(discord)))

        assertEquals(SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE, resolved)
    }

    @Test
    fun appDefaultOverrideFallsThroughToThePreset() {
        val resolved = behaviorFor(
            whatsApp,
            listOf(override(whatsApp, behavior = SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT))
        )

        assertEquals(SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE, resolved)
    }

    @Test
    fun everythingIsOffWhenTheFeatureIsDisabled() {
        assertNull(behaviorFor(whatsApp, listOf(override(whatsApp)), enabled = false))
    }

    @Test
    fun aNullPackageResolvesToNothing() {
        assertNull(behaviorFor(null, listOf(override(whatsApp))))
    }

    // ---- send strategy --------------------------------------------------------------------

    @Test
    fun sendStrategyDefaultsToAutoWithoutAnOverride() {
        assertEquals(
            SettingsManager.ENTER_SEND_STRATEGY_AUTO,
            EnterBehaviorPolicy.resolveSendStrategy(whatsApp, true, emptyList())
        )
    }

    @Test
    fun sendStrategyIsReadFromTheOverride() {
        val overrides = listOf(
            override(whatsApp, sendStrategy = SettingsManager.ENTER_SEND_STRATEGY_PLAIN_ENTER)
        )

        assertEquals(
            SettingsManager.ENTER_SEND_STRATEGY_PLAIN_ENTER,
            EnterBehaviorPolicy.resolveSendStrategy(whatsApp, true, overrides)
        )
    }

    @Test
    fun sendStrategyFallsBackToAutoWhenTheFeatureIsDisabled() {
        val overrides = listOf(
            override(whatsApp, sendStrategy = SettingsManager.ENTER_SEND_STRATEGY_PLAIN_ENTER)
        )

        assertEquals(
            SettingsManager.ENTER_SEND_STRATEGY_AUTO,
            EnterBehaviorPolicy.resolveSendStrategy(whatsApp, false, overrides)
        )
    }

    // ---- editor-action eligibility ----------------------------------------------------------

    @Test
    fun editorActionIsAllowedForAShippedApp() {
        assertTrue(
            EnterBehaviorPolicy.allowsEditorAction(whatsApp, true, emptyList(), presetPackages)
        )
    }

    @Test
    fun editorActionIsRefusedForAnUnknownUnconfiguredApp() {
        assertFalse(
            EnterBehaviorPolicy.allowsEditorAction(whatsAppBusiness, true, emptyList(), presetPackages)
        )
    }

    @Test
    fun configuringAnUnknownAppMakesTheEditorActionAvailable() {
        assertTrue(
            EnterBehaviorPolicy.allowsEditorAction(
                whatsAppBusiness,
                true,
                listOf(override(whatsAppBusiness)),
                presetPackages
            )
        )
    }

    // ---- additional send shortcut -----------------------------------------------------------

    @Test
    fun additionalSendShortcutReadsAnOverrideOnAnyApp() {
        val overrides = listOf(
            override(
                whatsAppBusiness,
                additionalSendShortcut = SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER
            )
        )

        assertEquals(
            SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_SYM_ENTER,
            EnterBehaviorPolicy.resolveAdditionalSendShortcut(whatsAppBusiness, true, overrides)
        )
    }

    @Test
    fun additionalSendShortcutIsNoneWithoutAnOverride() {
        assertEquals(
            SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE,
            EnterBehaviorPolicy.resolveAdditionalSendShortcut(whatsApp, true, emptyList())
        )
    }
}
