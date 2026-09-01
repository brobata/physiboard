package brobata.physiboard.inputmethod

import brobata.physiboard.SettingsManager

/**
 * Precedence rules for the per-app Enter behaviour, kept free of Android types so they can be
 * unit tested.
 *
 * These lived inline in [PhysicalKeyboardInputMethodService], where nothing could reach them.
 * That is how the override trap shipped: the tested-package check ran *before* the override
 * lookup, so choosing a behaviour for any app outside [SettingsManager] presets - WhatsApp
 * Business, a Signal fork, Slack - was stored, displayed as configured, and then ignored.
 *
 * The rule the tests pin down: an explicit per-app override always wins and is never limited to
 * a known package, because it is the user naming that app themselves. The preset is a blanket
 * default, so it stays limited to apps whose send behaviour we have actually verified.
 */
internal object EnterBehaviorPolicy {

    /**
     * The Enter behaviour for [packageName], or null to leave Enter alone.
     *
     * [presetPackages] are the apps the preset is allowed to apply to. [discordPackage] is
     * excluded from the send-on-Enter preset because its compose box does not act on an editor
     * action; users who want it there set an override instead.
     */
    fun resolveBehavior(
        packageName: String?,
        enabled: Boolean,
        overrides: List<SettingsManager.AppEnterBehaviorOverride>,
        preset: String,
        presetPackages: Set<String>,
        discordPackage: String
    ): String? {
        if (packageName == null || !enabled) return null

        val override = overrides.firstOrNull { it.packageName == packageName }?.behavior
        if (override != null && override != SettingsManager.ENTER_BEHAVIOR_APP_DEFAULT) {
            return override
        }

        if (packageName !in presetPackages) return null

        return when (preset) {
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_SEND_SHIFT_NEWLINE ->
                if (packageName == discordPackage) {
                    null
                } else {
                    SettingsManager.ENTER_BEHAVIOR_ENTER_SEND_SHIFT_NEWLINE
                }
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_CTRL_SEND ->
                SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE_CTRL_SEND
            SettingsManager.ENTER_BEHAVIOR_PRESET_ENTER_NEWLINE_ONLY ->
                SettingsManager.ENTER_BEHAVIOR_ENTER_NEWLINE
            else -> null
        }
    }

    /** The send mechanism chosen for [packageName], or AUTO when none was chosen. */
    fun resolveSendStrategy(
        packageName: String?,
        enabled: Boolean,
        overrides: List<SettingsManager.AppEnterBehaviorOverride>
    ): String {
        if (packageName == null || !enabled) return SettingsManager.ENTER_SEND_STRATEGY_AUTO
        return overrides.firstOrNull { it.packageName == packageName }
            ?.sendStrategy
            ?: SettingsManager.ENTER_SEND_STRATEGY_AUTO
    }

    /** The extra send chord chosen for [packageName], or NONE. Reads overrides only. */
    fun resolveAdditionalSendShortcut(
        packageName: String?,
        enabled: Boolean,
        overrides: List<SettingsManager.AppEnterBehaviorOverride>
    ): String {
        if (packageName == null || !enabled) return SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
        return overrides.firstOrNull { it.packageName == packageName }
            ?.additionalSendShortcut
            ?: SettingsManager.ENTER_ADDITIONAL_SEND_SHORTCUT_NONE
    }

    /** Whether the user has configured [packageName] at all. */
    fun hasOverride(
        packageName: String?,
        enabled: Boolean,
        overrides: List<SettingsManager.AppEnterBehaviorOverride>
    ): Boolean {
        if (packageName == null || !enabled) return false
        return overrides.any { it.packageName == packageName }
    }

    /**
     * Whether an editor action may be used for [packageName]: either it is one of the apps known
     * to honour one, or the user configured this app deliberately.
     */
    fun allowsEditorAction(
        packageName: String?,
        enabled: Boolean,
        overrides: List<SettingsManager.AppEnterBehaviorOverride>,
        sendActionPackages: Set<String>
    ): Boolean = packageName in sendActionPackages || hasOverride(packageName, enabled, overrides)
}
