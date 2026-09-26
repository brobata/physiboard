package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * spec: per-app-behavior.md SS3.4-SS3.10, SS3.13 (test cases T18-T32, E15, E16, E19). Test names
 * carry the spec's own id where one row maps directly onto a test.
 */
class EnterDecisionTest {

    private fun profile(
        packageName: String = "com.example.app",
        behavior: EnterBehavior = EnterBehavior.APP_DEFAULT,
        sendMethod: EnterSendMethod = EnterSendMethod.AUTO,
        actionAllowed: Boolean = false,
    ) = AppProfile(packageName = packageName, enterBehavior = behavior, enterSendMethod = sendMethod, enterActionAllowed = actionAllowed)

    private fun field(action: ImeAction = ImeAction.NONE, multiLine: Boolean = false) =
        FieldContext(FieldKind.NORMAL, imeAction = action, isMultiLine = multiLine)

    private fun decide(
        profile: AppProfile,
        fieldAction: ImeAction = ImeAction.NONE,
        multiLine: Boolean = false,
        ctrlActive: Boolean = false,
        shiftActive: Boolean = false,
        navModeActive: Boolean = false,
    ) = EnterDecision.decide(profile, field(fieldAction, multiLine), ctrlActive, shiftActive, navModeActive)

    // -----------------------------------------------------------------------------------------
    // T18-T21: send-on-Enter, method auto, field declares Send (4).
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T18 plain Enter under send-on-Enter with auto requests the field's Send action`() {
        val p = profile(behavior = EnterBehavior.SEND_SHIFT_NEWLINE, actionAllowed = true)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = false), decide(p, fieldAction = ImeAction.SEND))
    }

    @Test
    fun `T19 the field's declared Go action is requested instead of Send`() {
        val p = profile(behavior = EnterBehavior.SEND_SHIFT_NEWLINE, actionAllowed = true)
        assertEquals(EnterIntent.RequestEditorAction(2, clearCtrlIfDelivered = false), decide(p, fieldAction = ImeAction.GO))
    }

    @Test
    fun `T20 Shift+Enter under send-on-Enter inserts a newline instead of sending`() {
        val p = profile(behavior = EnterBehavior.SEND_SHIFT_NEWLINE, actionAllowed = true)
        assertEquals(EnterIntent.InsertNewline, decide(p, fieldAction = ImeAction.SEND, shiftActive = true))
    }

    @Test
    fun `T21 Ctrl+Enter under send-on-Enter sends and marks Ctrl to be cleared`() {
        val p = profile(behavior = EnterBehavior.SEND_SHIFT_NEWLINE, actionAllowed = true)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = true), decide(p, fieldAction = ImeAction.SEND, ctrlActive = true))
    }

    // -----------------------------------------------------------------------------------------
    // T22-T25: the four mechanisms and Discord's auto special case.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T22 Discord under auto sends a plain Enter, never an editor action`() {
        val p = profile(packageName = "com.discord", behavior = EnterBehavior.SEND_SHIFT_NEWLINE, sendMethod = EnterSendMethod.AUTO)
        assertEquals(EnterIntent.SendPlainEnter, decide(p, fieldAction = ImeAction.SEND))
    }

    @Test
    fun `T23 method ctrl_enter always sends Ctrl+Enter regardless of what triggered the send`() {
        val p = profile(behavior = EnterBehavior.SEND_SHIFT_NEWLINE, sendMethod = EnterSendMethod.CTRL_ENTER)
        assertEquals(EnterIntent.SendCtrlEnter, decide(p))
    }

    @Test
    fun `T24 enter_newline_ctrl_send with Ctrl inactive commits a newline regardless of method`() {
        val p = profile(packageName = "com.slack", behavior = EnterBehavior.NEWLINE_CTRL_SEND, sendMethod = EnterSendMethod.EDITOR_ACTION, actionAllowed = true)
        assertEquals(EnterIntent.InsertNewline, decide(p))
    }

    @Test
    fun `T25 Ctrl+Enter under editor_action falls back to Send when the field declares nothing`() {
        val p = profile(packageName = "com.slack", behavior = EnterBehavior.NEWLINE_CTRL_SEND, sendMethod = EnterSendMethod.EDITOR_ACTION, actionAllowed = true)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = true), decide(p, fieldAction = ImeAction.NONE, ctrlActive = true))
    }

    // -----------------------------------------------------------------------------------------
    // T26-T28: no wanted behavior, the generic step-e path.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T26 no wanted behavior requests the field's own declared Send action, Ctrl untouched`() {
        val p = profile(behavior = EnterBehavior.APP_DEFAULT)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = false), decide(p, fieldAction = ImeAction.SEND, ctrlActive = true))
    }

    @Test
    fun `T27 no wanted behavior and a field declaring no action declines`() {
        val p = profile(behavior = EnterBehavior.APP_DEFAULT)
        assertEquals(EnterIntent.Decline, decide(p, fieldAction = ImeAction.NONE))
    }

    @Test
    fun `T28 a field with the no-Enter-action flag already resolved to none declines`() {
        // The no-Enter-action flag itself is resolved into ImeAction.NONE upstream (SS3.9); this
        // decision only ever sees the resolved value.
        val p = profile(behavior = EnterBehavior.APP_DEFAULT)
        assertEquals(EnterIntent.Decline, decide(p, fieldAction = ImeAction.NONE))
    }

    // -----------------------------------------------------------------------------------------
    // T30-T32, E15, E16: nav mode.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T30 enter_newline in nav mode with Ctrl active sends instead of inserting a newline`() {
        val p = profile(behavior = EnterBehavior.NEWLINE, actionAllowed = true)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = true), decide(p, fieldAction = ImeAction.SEND, ctrlActive = true, navModeActive = true))
    }

    @Test
    fun `T31 enter_newline outside nav mode inserts a newline even with Ctrl held`() {
        val p = profile(behavior = EnterBehavior.NEWLINE)
        assertEquals(EnterIntent.InsertNewline, decide(p, ctrlActive = true, navModeActive = false))
    }

    @Test
    fun `T32 nav mode with no wanted behavior declines so nav mode's own mapping runs`() {
        val p = profile(behavior = EnterBehavior.APP_DEFAULT)
        assertEquals(EnterIntent.Decline, decide(p, fieldAction = ImeAction.SEND, navModeActive = true))
    }

    @Test
    fun `E15 is the same case as T32, nav mode owns Enter with no per-app opinion`() {
        val p = profile(behavior = EnterBehavior.APP_DEFAULT)
        assertEquals(EnterIntent.Decline, decide(p, navModeActive = true))
    }

    @Test
    fun `E16 nav mode with send-on-Enter sends because the nav latch counts as Ctrl active`() {
        val p = profile(behavior = EnterBehavior.SEND_SHIFT_NEWLINE, actionAllowed = true)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = true), decide(p, fieldAction = ImeAction.SEND, ctrlActive = true, navModeActive = true))
    }

    // -----------------------------------------------------------------------------------------
    // E19: the field's own action id wins over the Send(4) fallback.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `E19 a Go action is requested, not the Send fallback, when the app has a row`() {
        val p = profile(behavior = EnterBehavior.SEND_SHIFT_NEWLINE, sendMethod = EnterSendMethod.EDITOR_ACTION, actionAllowed = true)
        assertEquals(EnterIntent.RequestEditorAction(2, clearCtrlIfDelivered = false), decide(p, fieldAction = ImeAction.GO))
    }

    // -----------------------------------------------------------------------------------------
    // Editor action not allowed: the "Unsupported send" mechanism.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `an editor_action send with no eligibility swallows the key instead of sending`() {
        val p = profile(behavior = EnterBehavior.SEND_SHIFT_NEWLINE, sendMethod = EnterSendMethod.EDITOR_ACTION, actionAllowed = false)
        assertEquals(EnterIntent.Swallow(clearCtrlNow = false), decide(p, fieldAction = ImeAction.SEND))
    }

    @Test
    fun `auto with no eligibility swallows the key for a non-Discord app`() {
        val p = profile(packageName = "com.example.unverified", behavior = EnterBehavior.SEND_SHIFT_NEWLINE, sendMethod = EnterSendMethod.AUTO, actionAllowed = false)
        assertEquals(EnterIntent.Swallow(clearCtrlNow = true), decide(p, fieldAction = ImeAction.SEND, ctrlActive = true))
    }

    // -----------------------------------------------------------------------------------------
    // A multi-line field: SS3.9 says there is no separate rule, so the outcome must match a
    // single-line field with the same declared action exactly.
    // -----------------------------------------------------------------------------------------

    @Test
    fun `a multi-line field with a declared Send action is treated exactly like a single-line one`() {
        val p = profile(behavior = EnterBehavior.APP_DEFAULT)
        val singleLine = decide(p, fieldAction = ImeAction.SEND, multiLine = false)
        val multiLine = decide(p, fieldAction = ImeAction.SEND, multiLine = true)
        assertEquals(singleLine, multiLine)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = false), multiLine)
    }

    @Test
    fun `a multi-line field does not change a per-app newline outcome either`() {
        val p = profile(behavior = EnterBehavior.NEWLINE)
        assertEquals(decide(p, multiLine = false), decide(p, multiLine = true))
    }

    // -----------------------------------------------------------------------------------------
    // T29: Sym+Enter as an extra send (SS3.5 step 4a, SS3.8).
    // -----------------------------------------------------------------------------------------

    @Test
    fun `T29 sym-enter send fires the configured method regardless of wanted behaviour`() {
        val p = AppProfile(
            packageName = "com.whatsapp",
            enterBehavior = EnterBehavior.APP_DEFAULT,
            enterSendMethod = EnterSendMethod.PLAIN_ENTER,
            extraSendShortcut = ExtraSendShortcut.SYM_ENTER,
        )
        assertEquals(EnterIntent.SendPlainEnter, EnterDecision.decideSymEnterSend(p, field(ImeAction.NONE)))
    }

    @Test
    fun `sym-enter send falls back to editor action with the field's own action id`() {
        val p = AppProfile(
            packageName = "com.whatsapp",
            enterSendMethod = EnterSendMethod.EDITOR_ACTION,
            enterActionAllowed = true,
            extraSendShortcut = ExtraSendShortcut.SYM_ENTER,
        )
        assertEquals(EnterIntent.RequestEditorAction(2, clearCtrlIfDelivered = false), EnterDecision.decideSymEnterSend(p, field(ImeAction.GO)))
    }

    @Test
    fun `sym-enter send is never ctrl-triggered so an editor action success does not clear ctrl`() {
        val p = AppProfile(packageName = "com.whatsapp", enterSendMethod = EnterSendMethod.EDITOR_ACTION, enterActionAllowed = true)
        assertEquals(EnterIntent.RequestEditorAction(4, clearCtrlIfDelivered = false), EnterDecision.decideSymEnterSend(p, field(ImeAction.NONE)))
    }
}
