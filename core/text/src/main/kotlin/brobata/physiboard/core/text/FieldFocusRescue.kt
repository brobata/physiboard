package brobata.physiboard.core.text

/**
 * "Focus the text box when I start typing" (`accessibility_focus_field`): the decisions, with no
 * Android in them.
 *
 * A messaging app that opens straight into its compose box connects the box to the keyboard
 * before any view in the app has focus (per-app-behavior.md D9). Letters, committed through the
 * connection, land; but no cursor shows, and a key the app dispatches through view focus reaches
 * nothing until the box is tapped. With the accessibility service on, the first key typed into
 * such a field asks the service to do what the tap would have done: focus the box, then put the
 * cursor back where the keyboard left it.
 *
 * The rules are deliberately narrow. It acts at most once per field, never on a modifier, never
 * when any text box in the app already has focus (that is the box the user chose, even if it is
 * not the one the keyboard expected), and never on a guess: when the window has several boxes
 * and the text cannot tell them apart, it does nothing. A password box may be focused, but its
 * text is never read or compared and its cursor is never moved.
 *
 * spec: per-app-behavior.md SS16.2; text-input.md SS8.1.
 */
object FieldFocusRescue {

    /**
     * Whether this key-down should ask for a rescue. [featureOn] is the setting, [serviceConnected]
     * whether the accessibility service is running, [fieldReallyEditable] the keyboard's own
     * verdict on the field, [alreadyAskedThisField] whether this field (a restart of the same
     * field included) has had its one try.
     */
    fun shouldAsk(
        featureOn: Boolean,
        serviceConnected: Boolean,
        fieldReallyEditable: Boolean,
        alreadyAskedThisField: Boolean,
        isInitialKeyDown: Boolean,
        isModifier: Boolean,
    ): Boolean = featureOn && serviceConnected && fieldReallyEditable && !alreadyAskedThisField && isInitialKeyDown && !isModifier

    /** One editable node in the app's window, as the service read it. [text] is null for a password box or when unknown. */
    data class Box(
        val focused: Boolean,
        val visible: Boolean,
        val enabled: Boolean,
        val packageName: String?,
        val password: Boolean,
        val text: String?,
        val hint: String?,
    )

    /**
     * What the keyboard knows about the field it is typing into. [text] is the field's text as
     * the connection reported it (null for a password field, or when it could not be read);
     * [hint] the field's hint.
     */
    data class Expected(val packageName: String, val password: Boolean, val text: String?, val hint: String?)

    sealed interface Target {
        /** A text box in the app already has focus: leave it. */
        data object AlreadyFocused : Target

        /** Focus the box at [index] in the list the service read. */
        data class Focus(val index: Int) : Target

        /** Nothing to do, or nothing safe to do. [reason] is for the log, never the field's text. */
        data class Nothing(val reason: String) : Target
    }

    const val REASON_NO_BOX = "no_box"
    const val REASON_AMBIGUOUS = "ambiguous"

    /**
     * Which box to focus. Only boxes of the field's own app that are on screen and enabled count.
     * One such box is the one; with several, the one whose text (or, when the field is empty, whose
     * hint) matches what the keyboard knows; still several, or none, and nothing is focused.
     */
    fun choose(boxes: List<Box>, expected: Expected, someViewHasInputFocus: Boolean = false): Target {
        // D9's box is the case where NO view has focus. A view that has it without being editable
        // in accessibility terms (a terminal, a code or canvas editor) is the user's box all the same.
        if (someViewHasInputFocus) return Target.AlreadyFocused
        val ours = boxes.withIndex().filter { (_, box) -> box.packageName == expected.packageName && box.visible && box.enabled }
        if (ours.any { (_, box) -> box.focused }) return Target.AlreadyFocused
        if (ours.isEmpty()) return Target.Nothing(REASON_NO_BOX)
        if (ours.size == 1) return Target.Focus(ours.single().index)
        val byText = if (expected.password || expected.text == null) emptyList() else ours.filter { (_, box) -> textMatches(box, expected.text, expected.hint) }
        if (byText.size == 1) return Target.Focus(byText.single().index)
        val pool = byText.ifEmpty { ours }
        val byHint = if (expected.hint.isNullOrEmpty()) emptyList() else pool.filter { (_, box) -> box.hint == expected.hint }
        if (byHint.size == 1) return Target.Focus(byHint.single().index)
        return Target.Nothing(REASON_AMBIGUOUS)
    }

    /** An empty field's node may report nothing, an empty string or its hint as its text. */
    private fun textMatches(box: Box, text: String, hint: String?): Boolean {
        if (box.password) return false
        val shown = box.text
        if (text.isEmpty()) return shown.isNullOrEmpty() || (hint != null && shown == hint) || (box.hint != null && shown == box.hint)
        return shown == text
    }

    /** A selection as a start and an end; either below 0 means unknown. */
    data class Selection(val start: Int, val end: Int) {
        val known: Boolean get() = start >= 0 && end >= 0
    }

    /**
     * Where the cursor goes once the box has focus, or null to leave it. Focusing a box must not
     * move the cursor, but some boxes put it at the start or select everything when they take
     * focus, and the next letter would then land in the wrong place. Only that is undone: the
     * cursor goes back only when focusing visibly reset it (to the start, or to a whole-text
     * selection) and the text did not change in between ([textLengthBefore] and [textLengthAfter]
     * differ when the user typed while the box was being focused; the cursor they typed with is
     * then the right one). The target is the box's own selection from just before, or, when it
     * reported none, where the keyboard last saw it. Never in a password box.
     */
    fun selectionToRestore(
        beforeFocus: Selection?,
        afterFocus: Selection?,
        keyboardExpects: Selection?,
        password: Boolean,
        textLengthBefore: Int,
        textLengthAfter: Int,
    ): Selection? {
        if (password || afterFocus == null || !afterFocus.known) return null
        if (textLengthBefore != textLengthAfter) return null
        val reset = (afterFocus.start == 0 && afterFocus.end == 0) ||
            (textLengthAfter > 0 && minOf(afterFocus.start, afterFocus.end) == 0 && maxOf(afterFocus.start, afterFocus.end) == textLengthAfter)
        if (!reset) return null
        val target = beforeFocus?.takeIf { it.known } ?: keyboardExpects?.takeIf { it.known } ?: return null
        if (target == afterFocus) return null
        return target
    }
}
