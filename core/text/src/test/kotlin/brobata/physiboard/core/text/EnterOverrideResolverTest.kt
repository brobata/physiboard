package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * spec: per-app-behavior.md SS3.3 ("Resolving what applies to the current app"), SS3.13 (test
 * cases T1-T17). Test names carry the spec's own id where one row maps directly onto a test.
 */
class EnterOverrideResolverTest {

    private val whatsapp = "com.whatsapp"
    private val whatsappBusiness = "com.whatsapp.w4b"
    private val discord = "com.discord"
    private val slack = "com.slack"

    // -----------------------------------------------------------------------------------------
    // Wanted behavior. T1-T9.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T1 an override on an untested package wins outright under the send-on-Enter preset`() {
        val rows = listOf(EnterOverride(whatsappBusiness, behavior = EnterBehavior.SEND_SHIFT_NEWLINE))
        val result = EnterOverrideResolver.resolveBehavior(whatsappBusiness, rows, MessagingPreset.SEND_SHIFT_NEWLINE, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.SEND_SHIFT_NEWLINE, result)
    }

    @Test
    fun `T2 no row and a package outside the preset list gets nothing`() {
        val result = EnterOverrideResolver.resolveBehavior(whatsappBusiness, emptyList(), MessagingPreset.SEND_SHIFT_NEWLINE, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.APP_DEFAULT, result)
    }

    @Test
    fun `T3 an explicit newline override beats the send-on-Enter preset`() {
        val rows = listOf(EnterOverride(whatsapp, behavior = EnterBehavior.NEWLINE))
        val result = EnterOverrideResolver.resolveBehavior(whatsapp, rows, MessagingPreset.SEND_SHIFT_NEWLINE, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.NEWLINE, result)
    }

    @Test
    fun `T4 no row on a preset package falls through to the preset`() {
        val result = EnterOverrideResolver.resolveBehavior(whatsapp, emptyList(), MessagingPreset.SEND_SHIFT_NEWLINE, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.SEND_SHIFT_NEWLINE, result)
    }

    @Test
    fun `T5 Discord is excluded from the send-on-Enter preset with no row`() {
        val result = EnterOverrideResolver.resolveBehavior(discord, emptyList(), MessagingPreset.SEND_SHIFT_NEWLINE, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.APP_DEFAULT, result)
    }

    @Test
    fun `T6 an explicit Discord override bypasses the send-on-Enter exclusion`() {
        val rows = listOf(EnterOverride(discord, behavior = EnterBehavior.SEND_SHIFT_NEWLINE))
        val result = EnterOverrideResolver.resolveBehavior(discord, rows, MessagingPreset.APP_DEFAULT, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.SEND_SHIFT_NEWLINE, result)
    }

    @Test
    fun `T7 an app-default override on a preset package still falls through to the preset`() {
        val rows = listOf(EnterOverride(whatsapp, behavior = EnterBehavior.APP_DEFAULT))
        val result = EnterOverrideResolver.resolveBehavior(whatsapp, rows, MessagingPreset.SEND_SHIFT_NEWLINE, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.SEND_SHIFT_NEWLINE, result)
    }

    @Test
    fun `T8 the master switch off gives nothing even with a matching row`() {
        val rows = listOf(EnterOverride(whatsapp, behavior = EnterBehavior.SEND_SHIFT_NEWLINE))
        val result = EnterOverrideResolver.resolveBehavior(whatsapp, rows, MessagingPreset.SEND_SHIFT_NEWLINE, masterSwitchEnabled = false)
        assertEquals(EnterBehavior.APP_DEFAULT, result)
    }

    @Test
    fun `T9 a null current package gives nothing even with a matching row`() {
        val rows = listOf(EnterOverride(whatsapp, behavior = EnterBehavior.SEND_SHIFT_NEWLINE))
        val result = EnterOverrideResolver.resolveBehavior(null, rows, MessagingPreset.SEND_SHIFT_NEWLINE, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.APP_DEFAULT, result)
    }

    // -----------------------------------------------------------------------------------------
    // Send method. T10-T12.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T10 no row resolves the send method to auto`() {
        assertEquals(EnterSendMethod.AUTO, EnterOverrideResolver.resolveSendMethod(whatsapp, emptyList(), masterSwitchEnabled = true))
    }

    @Test
    fun `T11 a row's send method is returned as stored`() {
        val rows = listOf(EnterOverride(whatsapp, sendMethod = EnterSendMethod.PLAIN_ENTER))
        assertEquals(EnterSendMethod.PLAIN_ENTER, EnterOverrideResolver.resolveSendMethod(whatsapp, rows, masterSwitchEnabled = true))
    }

    @Test
    fun `T12 the master switch off reads the send method as auto regardless of the row`() {
        val rows = listOf(EnterOverride(whatsapp, sendMethod = EnterSendMethod.PLAIN_ENTER))
        assertEquals(EnterSendMethod.AUTO, EnterOverrideResolver.resolveSendMethod(whatsapp, rows, masterSwitchEnabled = false))
    }

    // -----------------------------------------------------------------------------------------
    // Editor action allowed. T13-T15, E18.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T13 a send-action package is allowed an editor action with no row`() {
        assertTrue(EnterOverrideResolver.isEditorActionAllowed(whatsapp, emptyList(), masterSwitchEnabled = true))
    }

    @Test
    fun `T14 a package outside the send-action list is not allowed with no row`() {
        assertFalse(EnterOverrideResolver.isEditorActionAllowed(whatsappBusiness, emptyList(), masterSwitchEnabled = true))
    }

    @Test
    fun `T15 any row at all allows the editor action even outside the send-action list`() {
        val rows = listOf(EnterOverride(whatsappBusiness))
        assertTrue(EnterOverrideResolver.isEditorActionAllowed(whatsappBusiness, rows, masterSwitchEnabled = true))
    }

    @Test
    fun `E18 an empty package is treated as not listed for editor action eligibility`() {
        assertFalse(EnterOverrideResolver.isEditorActionAllowed("", listOf(EnterOverride("")), masterSwitchEnabled = true))
    }

    // -----------------------------------------------------------------------------------------
    // Extra send shortcut. T16-T17.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T16 a row's extra shortcut is returned as stored`() {
        val rows = listOf(EnterOverride(whatsappBusiness, extraSendShortcut = ExtraSendShortcut.SYM_ENTER))
        assertEquals(ExtraSendShortcut.SYM_ENTER, EnterOverrideResolver.resolveExtraShortcut(whatsappBusiness, rows, masterSwitchEnabled = true))
    }

    @Test
    fun `T17 no row resolves the extra shortcut to none`() {
        assertEquals(ExtraSendShortcut.NONE, EnterOverrideResolver.resolveExtraShortcut(whatsapp, emptyList(), masterSwitchEnabled = true))
    }

    // -----------------------------------------------------------------------------------------
    // Edge cases.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `E3 the preset never applies to a package outside the 9 preset packages`() {
        val result = EnterOverrideResolver.resolveBehavior(slack, emptyList(), MessagingPreset.NEWLINE_CTRL_SEND, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.APP_DEFAULT, result)
    }

    @Test
    fun `E4 app default on a preset package still gets the preset's behavior`() {
        val rows = listOf(EnterOverride(whatsapp, behavior = EnterBehavior.APP_DEFAULT))
        assertEquals(
            EnterBehavior.NEWLINE_CTRL_SEND,
            EnterOverrideResolver.resolveBehavior(whatsapp, rows, MessagingPreset.NEWLINE_CTRL_SEND, masterSwitchEnabled = true),
        )
    }

    @Test
    fun `E5 Discord under the newline-ctrl-send preset with no row still gets that preset`() {
        // Discord is excluded only from the send-on-Enter preset (SS3.2), never from this one.
        val result = EnterOverrideResolver.resolveBehavior(discord, emptyList(), MessagingPreset.NEWLINE_CTRL_SEND, masterSwitchEnabled = true)
        assertEquals(EnterBehavior.NEWLINE_CTRL_SEND, result)
    }

    @Test
    fun `E14 editor action eligibility is also off when the master switch is off`() {
        val rows = listOf(EnterOverride(slack, behavior = EnterBehavior.SEND_SHIFT_NEWLINE))
        assertFalse(EnterOverrideResolver.isEditorActionAllowed(slack, rows, masterSwitchEnabled = false))
    }

    @Test
    fun `Discord is not a send-action package by itself`() {
        assertFalse(discord in EnterPresetPackages.SEND_ACTION)
        assertTrue(discord in EnterPresetPackages.PRESET)
    }
}
