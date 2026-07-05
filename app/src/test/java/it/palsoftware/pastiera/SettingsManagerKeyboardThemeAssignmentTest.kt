package it.palsoftware.pastiera

import it.palsoftware.pastiera.backup.PreferenceSchemas
import it.palsoftware.pastiera.backup.PreferenceValueType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsManagerKeyboardThemeAssignmentTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.setQualifiers("+notnight")
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("pastiera_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun fixedMode_returnsFixedTargetTheme_evenWhenSystemSlotsExist() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF101010.toInt())
        val light = theme(0xFFFFFFFF.toInt())
        val dark = theme(0xFF000000.toInt())

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.HARDWARE, dark = false, light)
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.HARDWARE, dark = true, dark)

        assertEquals(fixed.background, SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE).background)
    }

    @Test
    fun followSystem_usesLightAndDarkSlotsForTarget() {
        val context = RuntimeEnvironment.getApplication()
        val light = theme(0xFFEFEFEF.toInt())
        val dark = theme(0xFF111111.toInt())

        SettingsManager.setKeyboardThemeAssignmentMode(
            context,
            SettingsManager.KeyboardThemeTarget.SOFTWARE,
            SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
        )
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, dark = false, light)
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, dark = true, dark)

        RuntimeEnvironment.setQualifiers("+notnight")
        assertEquals(light.background, SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE).background)

        RuntimeEnvironment.setQualifiers("+night")
        assertEquals(dark.background, SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE).background)
    }

    @Test
    fun overrideBeatsFollowSystem() {
        val context = RuntimeEnvironment.getApplication()
        val dark = theme(0xFF111111.toInt())
        val override = theme(0xFFABCDEF.toInt())

        SettingsManager.setKeyboardThemeAssignmentMode(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            SettingsManager.KEYBOARD_THEME_ASSIGNMENT_MODE_FOLLOW_SYSTEM
        )
        SettingsManager.setKeyboardThemeSystemSlot(context, SettingsManager.KeyboardThemeTarget.HARDWARE, dark = true, dark)
        SettingsManager.upsertKeyboardThemeLayoutOverride(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale = "de-DE",
            layout = "qwertz",
            theme = override
        )

        RuntimeEnvironment.setQualifiers("+night")

        assertEquals(
            override.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-DE", "qwertz").background
        )
    }

    @Test
    fun overrideBeatsFixedMode() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF111111.toInt())
        val override = theme(0xFF222222.toInt())

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        SettingsManager.upsertKeyboardThemeLayoutOverride(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale = null,
            layout = "qwerty",
            theme = override
        )

        assertEquals(
            override.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwerty").background
        )
    }

    @Test
    fun mostSpecificOverrideWinsOverLanguageAndLayoutOnlyMatches() {
        val context = RuntimeEnvironment.getApplication()
        val layoutOnly = theme(0xFF111111.toInt())
        val languageAndLayout = theme(0xFF222222.toInt())
        val exactLocaleAndLayout = theme(0xFF333333.toInt())

        SettingsManager.upsertKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, null, "qwertz", layoutOnly)
        SettingsManager.upsertKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de", "qwertz", languageAndLayout)
        SettingsManager.upsertKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-DE", "qwertz", exactLocaleAndLayout)

        assertEquals(
            exactLocaleAndLayout.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-DE", "qwertz").background
        )
        assertEquals(
            languageAndLayout.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-AT", "qwertz").background
        )
        assertEquals(
            layoutOnly.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwertz").background
        )
    }

    @Test
    fun localeUnderscoresAreNormalizedForOverrides() {
        val context = RuntimeEnvironment.getApplication()
        val override = theme(0xFFABCDEF.toInt())

        SettingsManager.upsertKeyboardThemeLayoutOverride(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale = "de_DE",
            layout = "qwertz",
            theme = override
        )

        assertEquals(
            override.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "de-DE", "qwertz").background
        )
    }

    @Test
    fun overridesAreIsolatedByHardwareAndSoftwareTarget() {
        val context = RuntimeEnvironment.getApplication()
        val hardwareOverride = theme(0xFF123456.toInt())
        val softwareFixed = theme(0xFF654321.toInt())

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, softwareFixed)
        SettingsManager.upsertKeyboardThemeLayoutOverride(
            context,
            SettingsManager.KeyboardThemeTarget.HARDWARE,
            locale = "en-US",
            layout = "qwerty",
            theme = hardwareOverride
        )

        assertEquals(
            softwareFixed.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.SOFTWARE, "en-US", "qwerty").background
        )
    }

    @Test
    fun removingOverrideRevealsUnderlyingMode() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF111111.toInt())
        val override = theme(0xFF222222.toInt())

        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        SettingsManager.upsertKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwerty", override)
        SettingsManager.removeKeyboardThemeLayoutOverride(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en_US", "qwerty")

        assertEquals(
            fixed.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwerty").background
        )
    }

    @Test
    fun malformedOverrideJsonFallsBackWithoutCrashing() {
        val context = RuntimeEnvironment.getApplication()
        val fixed = theme(0xFF111111.toInt())
        SettingsManager.setKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, fixed)
        SettingsManager.getPreferences(context).edit()
            .putString("keyboard_theme_layout_overrides_hardware", "{broken")
            .commit()

        assertEquals(
            fixed.background,
            SettingsManager.getEffectiveKeyboardTheme(context, SettingsManager.KeyboardThemeTarget.HARDWARE, "en-US", "qwerty").background
        )
    }

    @Test
    fun keyboardThemeAssignmentPreferences_areRecognizedForRestore() {
        listOf(
            "keyboard_theme_assignment_mode_hardware",
            "keyboard_theme_assignment_mode_software",
            "keyboard_theme_light_hardware",
            "keyboard_theme_light_software",
            "keyboard_theme_dark_hardware",
            "keyboard_theme_dark_software",
            "keyboard_theme_layout_overrides_hardware",
            "keyboard_theme_layout_overrides_software",
            "modifier_indicator_mode"
        ).forEach { key ->
            assertEquals(
                PreferenceValueType.STRING,
                PreferenceSchemas.expectedType("pastiera_prefs", key)
            )
        }
    }

    private fun theme(background: Int): SettingsManager.KeyboardThemeSettings =
        SettingsManager.defaultKeyboardTheme().copy(
            background = background,
            accent = background xor 0x00FFFFFF
        )
}
