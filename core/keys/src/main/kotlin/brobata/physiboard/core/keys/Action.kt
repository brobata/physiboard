package brobata.physiboard.core.keys

/**
 * What the rest of the typing pipeline should do with one resolved [KeyStroke].
 *
 * spec: keys-and-modifiers.md SS1.3 describes a pipeline of stages that each either "consumes",
 * "passes to app", or falls through; this type is the answer this module hands back once it has
 * made that decision. It stops exactly where the task boundary does: an [Action] never carries
 * text composition, autocorrect, or dictionary state (that is `:core:text`), and never carries an
 * Android type (that is `:device:titan` and `:ime`).
 */
sealed class Action {

    /** Insert literal text at the caret. spec: keys-and-modifiers.md SS7.4, SS7.2; layers-sym-alt.md SS5.3, SS6.2. */
    data class Commit(val text: String) : Action()

    /**
     * Delete [deleteCount] characters immediately before the caret, then insert [text], as one
     * batch edit. spec: keys-and-modifiers.md SS8.3 (a long-press replacement deletes the
     * committed character before typing the alternate) and SS9 (a multi-tap cycle "is done as one
     * batch edit so apps such as Messages do not flicker").
     */
    data class ReplaceRecent(val deleteCount: Int, val text: String) : Action()

    /**
     * A named editing or navigation effect with no literal text to insert.
     *
     * [extendSelection] mirrors keys-and-modifiers.md SS7.3: several Ctrl mappings (the word
     * motions, the navigation keycodes, page start/end) extend the selection instead of moving
     * the caret when Shift is active at the time.
     */
    data class Edit(val effect: EditEffect, val extendSelection: Boolean = false) : Action()

    /**
     * Forward this key to the app as a Ctrl combination: the physical-combo pass-through of
     * keys-and-modifiers.md SS7.3 step 1, and the `native_ctrl` mapping type of SS12.2.
     */
    data class ForwardAsCtrlCombo(val key: KeyId) : Action()

    /**
     * Run a named command: a Fn Layer `command` mapping (keys-and-modifiers.md SS12.2), a
     * launcher shortcut, or the assistant/dictation trigger. The command catalogue itself belongs
     * to whichever module owns that surface; this module only decides that a command fired and
     * which id names it.
     */
    data class RunCommand(val commandId: String) : Action()

    /**
     * The modifier or Sym session state changed and the status display should refresh, but there
     * is nothing to write into the document. spec: keys-and-modifiers.md SS13 ("every modifier
     * change refreshes a status snapshot").
     */
    object StateOnly : Action()

    /** Hand the stroke to the system or the focused app exactly as received. spec: keys-and-modifiers.md SS14. */
    object PassThrough : Action()

    /** The stroke is consumed and deliberately produces nothing observable. */
    object Ignored : Action()

    /** Several effects from one stroke, applied in order (for example a long-press replacement: delete then commit). */
    data class Multiple(val actions: List<Action>) : Action()
}

/**
 * The catalogue of named editing/navigation effects an [Action.Edit] can carry.
 *
 * spec: keys-and-modifiers.md SS7.3 (the Ctrl mapping table), SS7.6 (Enter/Backspace), SS7.7
 * (forward-delete alternatives), SS9 (multi-tap has no effect of its own; long press does, see
 * SS8.3).
 */
enum class EditEffect {
    DELETE_CHAR_BACKWARD,
    DELETE_CHAR_FORWARD,
    DELETE_WORD_BACKWARD,
    DELETE_SELECTION_OR_WORD_BACKWARD,
    /** Alt+Backspace with `alt_backspace_delete` = `line`: from the caret back to the start of its line. spec: keys-and-modifiers.md SS7.7. */
    DELETE_TO_LINE_START,
    NEWLINE,
    CURSOR_UP, CURSOR_DOWN, CURSOR_LEFT, CURSOR_RIGHT, CURSOR_CENTER,
    MOVE_WORD_LEFT, MOVE_WORD_RIGHT,
    EXPAND_SELECTION_LEFT, EXPAND_SELECTION_RIGHT,
    EXPAND_SELECTION_WORD_LEFT, EXPAND_SELECTION_WORD_RIGHT,
    LINE_HOME, LINE_END,
    PAGE_START, PAGE_END, PAGE_UP, PAGE_DOWN,
    TAB, ESCAPE,
    COPY, PASTE, CUT, UNDO, SELECT_ALL,
    MEDIA_PLAY_PAUSE, MEDIA_PREVIOUS, MEDIA_NEXT,
}

/**
 * What Alt+Backspace does: the `alt_backspace_delete` choice. spec: keys-and-modifiers.md SS7.7,
 * settings-catalog.md SS2.1.
 */
enum class AltBackspaceAction {
    /** The keyboard leaves it alone: a tapped or locked Alt deletes one character, a held one reaches the app as the real key. */
    DELETE_CHARACTER,

    /** Everything from the caret back to the start of its line; at a line start, the line break before it. */
    DELETE_TO_LINE_START,

    /** The character after the caret. */
    DELETE_FORWARD,
}
