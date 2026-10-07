package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: text-input.md SS9, "Test cases" T40-T56. */
class AutoCapitalizationTest {

    private val normal = FieldContext(FieldKind.NORMAL)
    private val defaultSettings = AutoCapSettings()

    private fun decide(field: FieldContext, settings: AutoCapSettings, textBeforeCursor: String?): CapDecision =
        AutoCapitalization.evaluate(AutoCapState.initial(), field, settings, textBeforeCursor).second

    @Test
    fun `T40 empty normal field arms at field start`() {
        assertEquals(CapDecision.ArmOneShot, decide(normal, defaultSettings, ""))
    }

    @Test
    fun `T41 sentence end followed by space arms`() {
        assertEquals(CapDecision.ArmOneShot, decide(normal, defaultSettings, "Hello. "))
    }

    @Test
    fun `T42 sentence end with no following space does not arm`() {
        assertEquals(CapDecision.Leave, decide(normal, defaultSettings, "Hello."))
    }

    @Test
    fun `T43 ellipsis then space does not arm`() {
        assertEquals(CapDecision.Leave, decide(normal, defaultSettings, "Wait... "))
    }

    @Test
    fun `T44 exclamation then space arms`() {
        assertEquals(CapDecision.ArmOneShot, decide(normal, defaultSettings, "Hello! "))
    }

    @Test
    fun `T45 after-period setting off does not arm`() {
        val settings = defaultSettings.copy(capitalizeAfterSentenceEnd = false)
        assertEquals(CapDecision.Leave, decide(normal, settings, "Hello. "))
    }

    @Test
    fun `T46 text-start setting off does not arm at field start`() {
        val settings = defaultSettings.copy(capitalizeAtTextStart = false)
        assertEquals(CapDecision.Leave, decide(normal, settings, ""))
    }

    @Test
    fun `T47 URL field does not arm by default`() {
        val url = FieldContext(FieldKind.URL)
        assertEquals(CapDecision.Leave, decide(url, defaultSettings, ""))
    }

    @Test
    fun `T48 URL field arms when restricted-fields setting is on`() {
        val url = FieldContext(FieldKind.URL)
        val settings = defaultSettings.copy(capitalizeRestrictedFields = true)
        assertEquals(CapDecision.ArmOneShot, decide(url, settings, ""))
    }

    @Test
    fun `T49 password field never arms even with restricted-fields setting on`() {
        val password = FieldContext(FieldKind.PASSWORD)
        val settings = defaultSettings.copy(capitalizeRestrictedFields = true)
        assertEquals(CapDecision.Leave, decide(password, settings, ""))
    }

    @Test
    fun `T50 raw-mode app never arms even with restricted-fields setting on`() {
        val rawMode = FieldContext(FieldKind.RAW_MODE_APP)
        val settings = defaultSettings.copy(capitalizeRestrictedFields = true)
        assertEquals(CapDecision.Leave, decide(rawMode, settings, ""))
    }

    @Test
    fun `T51 user disarm suppresses auto-cap at the same context`() {
        var state = AutoCapState.initial()
        val (armed, first) = AutoCapitalization.evaluate(state, normal, defaultSettings, "")
        assertEquals(CapDecision.ArmOneShot, first)
        state = armed.onUserDisarmed(context = "|")
        val (after, second) = AutoCapitalization.evaluate(state, normal, defaultSettings, "", suppressionContext = "|")
        assertEquals(CapDecision.Leave, second)
        assertEquals(null, after.armSource)
    }

    @Test
    fun `T54 field with CAP_CHARACTERS enables caps lock at field start`() {
        val field = FieldContext(FieldKind.NORMAL, FieldCapFlags(capCharacters = true))
        assertEquals(true, AutoCapitalization.evaluateFieldStartCapsLock(field))
        // No per-character auto-cap runs afterward, regardless of text.
        assertEquals(CapDecision.Leave, decide(field, defaultSettings, "hello"))
    }

    @Test
    fun `T55 CAP_WORDS arms at word start even with both user settings off`() {
        val field = FieldContext(FieldKind.NORMAL, FieldCapFlags(capWords = true))
        val settings = AutoCapSettings(capitalizeAtTextStart = false, capitalizeAfterSentenceEnd = false)
        assertEquals(CapDecision.ArmOneShot, decide(field, settings, "hello "))
    }

    @Test
    fun `T56 CAP_SENTENCES with both user settings off never arms`() {
        val field = FieldContext(FieldKind.NORMAL, FieldCapFlags(capSentences = true))
        val settings = AutoCapSettings(capitalizeAtTextStart = false, capitalizeAfterSentenceEnd = false)
        assertEquals(CapDecision.Leave, decide(field, settings, "hello. "))
    }

    @Test
    fun `null field read clears an auto-cap-armed one-shot`() {
        val (state, first) = AutoCapitalization.evaluate(AutoCapState.initial(), normal, defaultSettings, "")
        assertEquals(CapDecision.ArmOneShot, first)
        val armed = state.withArmSourceForTest(ShiftArmSource.AUTO_CAP)
        val (_, second) = AutoCapitalization.evaluate(armed, normal, defaultSettings, null)
        assertEquals(CapDecision.ClearOneShot, second)
    }

    @Test
    fun `a one-shot the user armed is never cleared as auto-cap's, in a field with suggestions or without`() {
        val stale = AutoCapState.initial().withArmSourceForTest(ShiftArmSource.AUTO_CAP)
        val users = stale.onUserArmed()
        assertEquals(CapDecision.Leave, AutoCapitalization.evaluate(users, normal, defaultSettings, "Hello").second)
        val terminal = FieldContext(FieldKind.RAW_MODE_APP, appDisablesSuggestions = true)
        assertEquals(CapDecision.Leave, AutoCapitalization.evaluate(users, terminal, defaultSettings, "").second)
        assertEquals(CapDecision.ClearOneShot, AutoCapitalization.evaluate(stale, normal, defaultSettings, "Hello").second)
    }

    /** The maintainer's terminal, a web field that asks for no suggestions, was capitalising its first word. */
    @Test
    fun `a field that wants no suggestions gets no automatic capital`() {
        val terminal = FieldContext(FieldKind.NORMAL, appDisablesSuggestions = true)
        assertEquals(CapDecision.Leave, AutoCapitalization.evaluate(AutoCapState.initial(), terminal, AutoCapSettings(), "").second)
        assertEquals(CapDecision.Leave, AutoCapitalization.evaluate(AutoCapState.initial(), terminal, AutoCapSettings(), "done. ").second)
        val ordinary = FieldContext(FieldKind.NORMAL)
        assertEquals(CapDecision.ArmOneShot, AutoCapitalization.evaluate(AutoCapState.initial(), ordinary, AutoCapSettings(), "").second)
    }
}

/** Test-only accessor mirroring what [AutoCapitalization.evaluate] already sets internally. */
private fun AutoCapState.withArmSourceForTest(source: ShiftArmSource?): AutoCapState = this.copy(armSource = source)
