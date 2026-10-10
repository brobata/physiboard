package brobata.physiboard.core.keys

/**
 * Decides what a non-modifier key produces, given the current [ModifierState] and a
 * [LayoutDescription].
 *
 * spec: layers-sym-alt.md SS2 ("the layers in one picture"), SS5 (the Sym key session), SS6 (the
 * Alt layer); keys-and-modifiers.md SS7 (what modifiers do to a key). Modifier keys themselves
 * (Shift, Ctrl, Alt, Sym, Fn) never reach this object; a caller dispatches them to
 * [ModifierMachine] instead. Nav-mode behaviour with no editable field focused
 * (keys-and-modifiers.md SS15) is out of this module's scope: it belongs with the trackpad
 * surface (`trackpad-caret-nav.md`, not read for this module), so a stroke with no editable field
 * simply passes through here.
 */
object LayerResolver {

    /**
     * Facts about the focused field and the session the caller already knows and this module
     * cannot infer on its own.
     *
     * [canSwitchLayout]: whether the caller has a "next subtype" to switch to (keys-and-modifiers.md
     * SS7.5, the Ctrl+Space chord). SPEC AMENDMENT (review A8): SS7.5 makes Ctrl+Space a consumed
     * chord unconditionally, but with a single layout there is nothing to switch and a Fn+Space
     * then simply vanishes. The chord fires only when the caller says a switch is possible;
     * otherwise Space resolves like any other key under Ctrl (SS7.3), so a physical Fn+Space is
     * forwarded to the app as the Ctrl combo it is and a logical Ctrl passes Space through.
     */
    data class Context(
        val hasEditableField: Boolean = true,
        val isNumericField: Boolean = false,
        val hasSelection: Boolean = false,
        val hasTextBeforeCaret: Boolean = true,
        val canSwitchLayout: Boolean = false,
        /**
         * The field belongs to an app on the Terminal mode list (per-app-behavior.md SS4.6):
         * Ctrl in any form reaches the app as a real Ctrl combo, never as an editor command.
         */
        val terminalMode: Boolean = false,
        /**
         * The field takes accents (text-input.md SS3, the "Variations" column: off in an email
         * field). False keeps a long press in Accent mode from arming at all there.
         */
        val variationsAllowed: Boolean = true,
    )

    /** spec: keys-and-modifiers.md SS7.7 (the Backspace alternatives) and SS7.1 (swipe-to-delete). */
    data class LayerResolverSettings(
        val shiftBackspaceDelete: Boolean = false,
        val altBackspace: AltBackspaceAction = AltBackspaceAction.DELETE_CHARACTER,
        val backspaceAtStartDelete: Boolean = false,
        val swipeToDeleteEnabled: Boolean = false,
    )

    data class Resolution(val state: ModifierState, val typing: TypingSessionState, val action: Action)

    /**
     * Resolves one non-modifier key-down. Follows the in-scope subset of keys-and-modifiers.md
     * SS1.3's processing order: swipe-to-delete (SS7.1), the Ctrl+Space layout-switch chord and
     * Space/Enter clearing Alt (SS6.4, SS7.5), Enter's Shift one-shot consumption (SS7.6),
     * forward-delete alternatives (SS7.7), the Sym key session (layers-sym-alt.md SS5.3-5.4),
     * then Alt, Ctrl or plain-key resolution. Whatever falls out of that chain still owing an
     * ordinary Space, Enter or Backspace a real answer gets one from [withBaselineControlAction]
     * (text-input.md SS5-SS8) before this returns.
     */
    fun resolveKeyDown(
        state: ModifierState,
        typing: TypingSessionState,
        stroke: KeyStroke,
        layout: LayoutDescription,
        modifierSettings: ModifierSettings,
        resolverSettings: LayerResolverSettings,
        context: Context,
    ): Resolution {
        require(stroke.edge == KeyEdge.DOWN) { "not a key-down: $stroke" }
        require(stroke.key !is KeyId.Modifier) { "modifier strokes go through ModifierMachine, not LayerResolver: ${stroke.key}" }

        if (!context.hasEditableField) return Resolution(state, typing, Action.PassThrough)

        val resolution = resolveKeyDownOnEditableField(state, typing, stroke, layout, modifierSettings, resolverSettings, context)
        return withBaselineControlAction(resolution, stroke, state, context)
    }

    private fun resolveKeyDownOnEditableField(
        state: ModifierState,
        previousTyping: TypingSessionState,
        stroke: KeyStroke,
        layout: LayoutDescription,
        modifierSettings: ModifierSettings,
        resolverSettings: LayerResolverSettings,
        context: Context,
    ): Resolution {
        if (stroke.isRepeat && repeatIsConsumedByItsOwnPress(previousTyping, stroke)) return Resolution(state, previousTyping, Action.Ignored)
        // spec SS7.5: Enter repeats after a consumed Alt+Enter are swallowed until the key-up, and
        // "a fresh Enter down with repeat 0 always clears the 'consume Enter repeats' flag first".
        if (stroke.key == ENTER && stroke.isRepeat && previousTyping.consumeEnterRepeatsUntilUp) return Resolution(state, previousTyping, Action.Ignored)
        val typing = if (stroke.isInitialPress) forgetOnNewPress(previousTyping, stroke.key) else previousTyping

        if (stroke.key == SWIPE_TO_DELETE) {
            val action = if (resolverSettings.swipeToDeleteEnabled) Action.Edit(EditEffect.DELETE_WORD_BACKWARD) else Action.Ignored
            return Resolution(state, typing, action)
        }

        if (stroke.key == SPACE && state.isCtrlActive(stroke.meta.ctrl) && context.canSwitchLayout && modifierSettings.ctrlSpaceLayoutSwitch) {
            // A Space repeat while Fn stays held must not switch again on every repeat; it is
            // consumed like the Enter repeats after an Alt+Enter switch (SS7.5).
            if (stroke.isRepeat) return Resolution(state, typing, Action.Ignored)
            return Resolution(applyCtrlSpaceLayoutSwitch(state), typing, ctrlSpaceLayoutSwitchAction(state))
        }

        // spec SS7.5, the Alt+Enter row: "Alt cleared; next subtype; toast; consumed, and every
        // Enter repeat until the Enter key-up is consumed too". The repeats are swallowed above
        // through [TypingSessionState.consumeEnterRepeatsUntilUp]; the key-up in [resolveKeyUp].
        if (stroke.key == ENTER && stroke.isInitialPress && state.isAltActive(stroke.meta.alt) && context.canSwitchLayout && modifierSettings.altEnterLayoutSwitch) {
            return Resolution(state.copy(alt = AltState()), typing.copy(consumeEnterRepeatsUntilUp = true), Action.RunCommand(KeyCommands.SWITCH_LAYOUT))
        }

        if ((stroke.key == SPACE || stroke.key == ENTER) && modifierSettings.clearAltOnSpace &&
            (state.alt.oneShot || state.alt.latched)
        ) {
            val altCleared = state.copy(alt = AltState())
            return if (stroke.key == SPACE) {
                Resolution(altCleared, typing, Action.Commit(" "))
            } else {
                Resolution(consumeShiftOneShot(altCleared), typing, Action.Edit(EditEffect.NEWLINE))
            }
        }

        var working = if (stroke.key == ENTER) consumeShiftOneShot(state) else state

        if (stroke.key == BACKSPACE && !context.hasSelection) {
            backspaceAlternative(stroke, working, resolverSettings, context)?.let { effect ->
                return Resolution(consumeAltOneShotIfUsed(working, stroke, effect, resolverSettings), typing, Action.Edit(effect))
            }
        }

        val ctrlActiveNow = working.isCtrlActive(stroke.meta.ctrl)
        val symHeld = working.sym.togglePending && !working.sym.chordUsed
        if (symHeld && !ctrlActiveNow) {
            val (afterChord, action) = trySymChord(working, stroke, layout, modifierSettings)
            working = afterChord
            if (action != null) return Resolution(working, typing, action)
        }
        if (working.sym.currentPageNumber != 0 && !working.isCtrlActive(stroke.meta.ctrl)) {
            val (afterPage, action) = trySymPageKey(working, stroke, layout, modifierSettings)
            working = afterPage
            if (action != null) return Resolution(working, typing, action)
        }

        val ctrlActiveAnyForm = working.isCtrlActive(stroke.meta.ctrl) ||
            (context.isNumericField && working.isCtrlPhysicalCombo(stroke.meta.ctrl))

        return when {
            ctrlActiveAnyForm -> resolveCtrlActive(working, typing, stroke, layout, modifierSettings, context)
            context.isNumericField -> resolveAltActive(working, typing, stroke, layout)
            working.isAltActive(stroke.meta.alt) -> resolveAltActive(working, typing, stroke, layout)
            else -> resolvePlainKey(working, typing, stroke, layout, context.variationsAllowed)
        }
    }

    /**
     * spec: keys-and-modifiers.md SS8.3 ("if the key is released first, the timer is cancelled")
     * and SS1.4 step 13 ("consumed if a press was being tracked"): a press is tracked from its
     * arming until this up, whether or not the timer fired in between.
     */
    fun resolveKeyUp(state: ModifierState, typing: TypingSessionState, stroke: KeyStroke): Resolution {
        require(stroke.edge == KeyEdge.UP) { "not a key-up: $stroke" }
        // spec SS7.5, Alt+Enter: "the Enter up itself is consumed" and ends the repeat swallow.
        if (stroke.key == ENTER && typing.consumeEnterRepeatsUntilUp) {
            return Resolution(state, typing.copy(consumeEnterRepeatsUntilUp = false), Action.Ignored)
        }
        val pendingForThisKey = typing.pendingLongPress?.key == stroke.key
        val firedForThisKey = typing.longPressFiredKey == stroke.key
        if (!pendingForThisKey && !firedForThisKey) return Resolution(state, typing, Action.PassThrough)
        val released = typing.copy(
            pendingLongPress = if (pendingForThisKey) null else typing.pendingLongPress,
            longPressFiredKey = if (firedForThisKey) null else typing.longPressFiredKey,
        )
        return Resolution(state, released, Action.Ignored)
    }

    /**
     * Checks whether an armed long press has fired, and if so what it replaces the committed
     * text with. spec: keys-and-modifiers.md SS8.3. The replacement changes text only; SS8.3
     * names no modifier change, so [state] comes back exactly as given (review A2: a fresh
     * state here wiped caps lock, latches, the open Sym page and the held flags on every long
     * press). The key stays tracked as [TypingSessionState.longPressFiredKey] until its key-up.
     * [textBeforeCaret] is [LongPress.replacement]'s caret check (Accent mode only).
     */
    fun resolveLongPressTick(state: ModifierState, typing: TypingSessionState, nowMs: Long, layout: LayoutDescription, textBeforeCaret: String? = null): Resolution? {
        val pending = typing.pendingLongPress ?: return null
        if (!LongPress.hasFired(pending, nowMs)) return null
        val action = LongPress.replacement(pending, layout, textBeforeCaret)
        return Resolution(state, typing.copy(pendingLongPress = null, longPressFiredKey = pending.key), action)
    }

    // -----------------------------------------------------------------
    // What a press does to the previous press's bookkeeping. spec: keys-and-modifiers.md SS8.1, SS8.3, SS9.
    // -----------------------------------------------------------------

    /**
     * spec: keys-and-modifiers.md SS8.1: "a key with a pending long press consumes them (the timer
     * decides the outcome, not the repeats)" and "multi-tap keys consume them"; a press whose
     * timer already fired is still that press. Every other repeat re-enters the normal path
     * below, so a held key under Alt, Ctrl or Sym repeats through that layer, not the base one.
     */
    private fun repeatIsConsumedByItsOwnPress(typing: TypingSessionState, stroke: KeyStroke): Boolean =
        typing.pendingLongPress?.key == stroke.key ||
            typing.longPressFiredKey == stroke.key ||
            typing.multiTapCycle?.key == stroke.key

    /**
     * A fresh press (repeat 0) of any key ends whatever the previous press left armed: the
     * pending long press (review A5: otherwise a long press firing after a Space deletes the
     * space, not the letter; SS8.3's replacement is only ever of the character that press
     * committed), the multi-tap cycle of a different key (SS1.3 step 16, SS9), and a stale
     * fired-press marker for this same key (its up was lost). A different key's fired marker
     * stays: that key is still held and its repeats must still be consumed.
     */
    private fun forgetOnNewPress(typing: TypingSessionState, key: KeyId): TypingSessionState = TypingSessionState(
        multiTapCycle = typing.multiTapCycle?.takeIf { it.key == key },
        pendingLongPress = null,
        longPressFiredKey = typing.longPressFiredKey?.takeIf { it != key },
        // spec SS7.5: only "a fresh Enter down with repeat 0" clears the swallow; another key
        // chorded while Enter is still held must not let the next Enter repeat through.
        consumeEnterRepeatsUntilUp = typing.consumeEnterRepeatsUntilUp && key != ENTER,
    )

    // -----------------------------------------------------------------
    // Ctrl+Space and Space/Enter clearing Alt. spec: keys-and-modifiers.md SS6.4, SS7.5.
    // -----------------------------------------------------------------

    private fun applyCtrlSpaceLayoutSwitch(state: ModifierState): ModifierState =
        state.copy(alt = AltState(), ctrl = CtrlState())

    private fun ctrlSpaceLayoutSwitchAction(state: ModifierState): Action =
        if (state.ctrl.latchFromNavMode) {
            Action.Multiple(listOf(Action.RunCommand(KeyCommands.EXIT_NAV_MODE), Action.RunCommand(KeyCommands.SWITCH_LAYOUT)))
        } else {
            Action.RunCommand(KeyCommands.SWITCH_LAYOUT)
        }

    private fun consumeShiftOneShot(state: ModifierState): ModifierState =
        if (state.shift.value == ShiftValue.ONE_SHOT) state.copy(shift = state.shift.copy(value = ShiftValue.OFF)) else state

    // -----------------------------------------------------------------
    // The baseline text-input action for an ordinary Space, Enter or Backspace.
    // spec: text-input.md SS5.5 ("every commit of a non-Alt character"), SS6.1/SS6.2 (Space),
    // SS7 (Enter), SS8 (Backspace).
    // -----------------------------------------------------------------

    /**
     * [CharacterResolution.layoutOrDefaultCharacter] only ever resolves a letter, a digit or a
     * punctuation mark; Space, Enter and Backspace are [KeyId.Control] keys with no base-layout
     * entry of their own, so every branch above ([resolvePlainKey], [resolveAltActive] for a key
     * other than Space, and [resolveCtrlActive]'s own "no mapping, Enter or Back" case) answers
     * an ordinary press of one of them with [Action.PassThrough]. Read on its own that is a
     * defensible "the layout does not map it" (keys-and-modifiers.md SS7.4 step 11's bare
     * fallback, "Enter, Space, Backspace ... reach the app as ordinary key events"), but
     * text-input.md SS5 to SS8 expects the double-space period, the deferred-space debt, the
     * legacy-autocorrect boundary hand-off and the Backspace undo table to run on every ordinary
     * press of these three keys, not just the ones a layout happens to map. This substitutes the
     * real action `:core:text` needs once every branch above has already had its say and still
     * came back with [Action.PassThrough], for exactly the three control keys text-input.md
     * names, and leaves every other [Action.PassThrough] alone: a Ctrl-active one (Ctrl is
     * checked against the state [resolveKeyDown] was called with, since none of the branches
     * above change whether Ctrl was active) is keys-and-modifiers.md SS7.3's own distinct
     * "no mapping, Enter or Back: pass to app", not this gap, and a key that is neither Space,
     * Enter nor Backspace was never in scope here.
     */
    private fun withBaselineControlAction(resolution: Resolution, stroke: KeyStroke, state: ModifierState, context: Context): Resolution {
        if (resolution.action != Action.PassThrough) return resolution
        val ctrlActive = state.isCtrlActive(stroke.meta.ctrl) || (context.isNumericField && state.isCtrlPhysicalCombo(stroke.meta.ctrl))
        if (ctrlActive) return resolution
        val action = when (stroke.key) {
            SPACE -> Action.Commit(" ")
            ENTER -> Action.Edit(EditEffect.NEWLINE)
            BACKSPACE -> Action.Edit(EditEffect.DELETE_CHAR_BACKWARD)
            else -> return resolution
        }
        return resolution.copy(action = action)
    }

    // -----------------------------------------------------------------
    // Backspace alternatives. spec: keys-and-modifiers.md SS7.7.
    // -----------------------------------------------------------------

    /**
     * What Backspace does instead of deleting one character before the caret, or null for the
     * ordinary path. The caller has already ruled out a selection. Shift's forward delete is
     * checked first, so Alt+Shift+Backspace with both rows on deletes forward once. The
     * line delete gives way to Ctrl in any form: Ctrl+Alt+Backspace stays Ctrl's word delete.
     */
    private fun backspaceAlternative(
        stroke: KeyStroke,
        state: ModifierState,
        settings: LayerResolverSettings,
        context: Context,
    ): EditEffect? {
        val altActive = state.isAltActive(stroke.meta.alt)
        val ctrlActive = state.isCtrlActive(stroke.meta.ctrl) || (context.isNumericField && state.isCtrlPhysicalCombo(stroke.meta.ctrl))
        return when {
            settings.shiftBackspaceDelete && stroke.meta.shift -> EditEffect.DELETE_CHAR_FORWARD
            altActive && settings.altBackspace == AltBackspaceAction.DELETE_FORWARD -> EditEffect.DELETE_CHAR_FORWARD
            altActive && !ctrlActive && settings.altBackspace == AltBackspaceAction.DELETE_TO_LINE_START -> EditEffect.DELETE_TO_LINE_START
            settings.backspaceAtStartDelete && !stroke.meta.shift && !altActive && !context.hasTextBeforeCaret -> EditEffect.DELETE_CHAR_FORWARD
            else -> null
        }
    }

    /**
     * A tapped (one-shot) Alt is spent on the Backspace it changed, exactly as it is on a letter
     * it changes (SS7.2); a held or locked Alt stays. When Shift's own row decided the press,
     * Alt played no part and an armed one-shot waits for the next key.
     */
    private fun consumeAltOneShotIfUsed(state: ModifierState, stroke: KeyStroke, effect: EditEffect, settings: LayerResolverSettings): ModifierState {
        if (!state.alt.oneShot) return state
        val shiftDecided = settings.shiftBackspaceDelete && stroke.meta.shift
        val altDecided = !shiftDecided && when (effect) {
            EditEffect.DELETE_TO_LINE_START -> true
            EditEffect.DELETE_CHAR_FORWARD -> settings.altBackspace == AltBackspaceAction.DELETE_FORWARD
            else -> false
        }
        return if (altDecided) state.copy(alt = state.alt.copy(oneShot = false)) else state
    }

    // -----------------------------------------------------------------
    // The Sym key session. spec: layers-sym-alt.md SS5.3 (chords), SS5.4 (a page open).
    // -----------------------------------------------------------------

    private val EDIT_SHORTCUT_KEYS: Map<KeyId, EditEffect> = mapOf(
        KeyId.Letter('C') to EditEffect.COPY,
        KeyId.Letter('V') to EditEffect.PASTE,
        KeyId.Letter('X') to EditEffect.CUT,
        KeyId.Letter('A') to EditEffect.SELECT_ALL,
    )

    private fun trySymChord(
        state: ModifierState,
        stroke: KeyStroke,
        layout: LayoutDescription,
        settings: ModifierSettings,
    ): Pair<ModifierState, Action?> {
        if (settings.symEditShortcutsEnabled && !stroke.meta.alt) {
            val effect = EDIT_SHORTCUT_KEYS[stroke.key]
            if (effect != null) return ModifierMachine.symChordUsed(state) to Action.Edit(effect)
        }

        // Launcher shortcuts (layers-sym-alt.md SS5.3 step 2) belong to
        // expansion-clipboard-pickers-launcher.md, not read for this module; a caller that owns
        // that catalogue can intercept before calling this resolver.

        val shiftEffective = state.shiftForcesUppercase(stroke.meta.shift)
        val preferredPage = preferredSymTextPage(state.sym.currentPageNumber, layout.symPagesConfig)
        val text = preferredPage?.let { CharacterResolution.symPageEntryText(pageMap(it, layout)[stroke.key], shiftEffective) }

        val markedState = ModifierMachine.symChordUsed(state)
        if (text == null) return markedState to null

        // spec layers-sym-alt.md SS5.5: `sym_auto_close` makes using a key layer one-shot, and a
        // chord that draws from the currently open key layer counts as using it.
        val closed = if (settings.symAutoCloseEnabled) markedState.copy(sym = markedState.sym.copy(currentPageNumber = 0)) else markedState
        return closed to Action.Commit(text)
    }

    private fun preferredSymTextPage(currentPageNumber: Int, pages: SymPagesConfig): SymPageId? {
        val openPage = SymPageId.entries.firstOrNull { it.pageNumber == currentPageNumber && it.isKeyLayer }
        if (openPage != null) return openPage
        return pages.normalizedOrder.firstOrNull { it.isKeyLayer && pages.isEnabled(it) }
    }

    private fun pageMap(page: SymPageId, layout: LayoutDescription): SymPageMap = when (page) {
        SymPageId.EMOJI -> layout.emojiPage
        SymPageId.SYMBOLS -> layout.symbolsPage
        else -> layout.customPages[page] ?: SymPageMap()
    }

    private fun trySymPageKey(
        state: ModifierState,
        stroke: KeyStroke,
        layout: LayoutDescription,
        settings: ModifierSettings,
    ): Pair<ModifierState, Action?> {
        if (stroke.key == BACK) {
            return state.copy(sym = state.sym.copy(currentPageNumber = 0)) to Action.Ignored
        }
        if (stroke.key == ENTER) {
            return if (settings.symAutoCloseEnabled) {
                state.copy(sym = state.sym.copy(currentPageNumber = 0)) to null
            } else {
                state to null
            }
        }

        val pageId = SymPageId.entries.firstOrNull { it.pageNumber == state.sym.currentPageNumber }
        if (pageId == null || !pageId.isKeyLayer) return state to null // panels (3, 4): not handled here

        val shiftEffective = state.shiftForcesUppercase(stroke.meta.shift)
        val text = CharacterResolution.symPageEntryText(pageMap(pageId, layout)[stroke.key], shiftEffective) ?: return state to null

        val newState = if (settings.symAutoCloseEnabled) state.copy(sym = state.sym.copy(currentPageNumber = 0)) else state
        return newState to Action.Commit(text)
    }

    // -----------------------------------------------------------------
    // Alt active. spec: keys-and-modifiers.md SS7.2; layers-sym-alt.md SS6.2.
    // -----------------------------------------------------------------

    private fun resolveAltActive(state: ModifierState, typing: TypingSessionState, stroke: KeyStroke, layout: LayoutDescription): Resolution {
        val consumed = if (state.alt.oneShot) state.copy(alt = state.alt.copy(oneShot = false)) else state
        val clearedTyping = typing.copy(multiTapCycle = null, pendingLongPress = null)

        if (stroke.key == BACK) return Resolution(consumed, clearedTyping, Action.PassThrough)
        if (stroke.key == SPACE) return Resolution(consumed, clearedTyping, Action.Commit(" "))

        val text = layout.deviceLayer[stroke.key]
        val action = if (text != null) Action.Commit(text) else Action.PassThrough
        return Resolution(consumed, clearedTyping, action)
    }

    // -----------------------------------------------------------------
    // Ctrl active. spec: keys-and-modifiers.md SS7.3.
    // -----------------------------------------------------------------

    private val BASIC_EDIT_ACTIONS = setOf("copy", "cut", "paste", "select_all")

    private val NAMED_CTRL_ACTIONS: Map<String, EditEffect> = mapOf(
        "select_all" to EditEffect.SELECT_ALL,
        "copy" to EditEffect.COPY,
        "paste" to EditEffect.PASTE,
        "cut" to EditEffect.CUT,
        "undo" to EditEffect.UNDO,
        "expand_selection_left" to EditEffect.EXPAND_SELECTION_LEFT,
        "expand_selection_right" to EditEffect.EXPAND_SELECTION_RIGHT,
        "expand_selection_word_left" to EditEffect.EXPAND_SELECTION_WORD_LEFT,
        "expand_selection_word_right" to EditEffect.EXPAND_SELECTION_WORD_RIGHT,
        "move_word_left" to EditEffect.MOVE_WORD_LEFT,
        "move_word_right" to EditEffect.MOVE_WORD_RIGHT,
        "page_start" to EditEffect.PAGE_START,
        "page_end" to EditEffect.PAGE_END,
        "media_play_pause" to EditEffect.MEDIA_PLAY_PAUSE,
        "media_previous" to EditEffect.MEDIA_PREVIOUS,
        "media_next" to EditEffect.MEDIA_NEXT,
    )

    private val SELECTION_EXTENDABLE_ACTIONS = setOf("move_word_left", "move_word_right", "page_start", "page_end")

    private val SELECTION_EXTENDABLE_KEYCODES = setOf(
        ControlKey.DPAD_UP, ControlKey.DPAD_DOWN, ControlKey.DPAD_LEFT, ControlKey.DPAD_RIGHT,
        ControlKey.MOVE_HOME, ControlKey.MOVE_END, ControlKey.PAGE_UP, ControlKey.PAGE_DOWN,
    )

    private fun resolveCtrlActive(
        state: ModifierState,
        typing: TypingSessionState,
        stroke: KeyStroke,
        layout: LayoutDescription,
        settings: ModifierSettings,
        context: Context,
    ): Resolution {
        val clearedTyping = typing.copy(multiTapCycle = null, pendingLongPress = null)
        val physicalCombo = state.isCtrlPhysicalCombo(stroke.meta.ctrl)
        val navGrid = state.ctrl.latchFromNavMode || (physicalCombo && settings.navModeCtrlHoldEnabled)
        val shortcutKeycode = shortcutKeycodeFor(stroke.key, layout, settings)
        val mappingKeycode = if (navGrid) stroke.key else shortcutKeycode

        val mapping = layout.ctrlMappings.mappingFor(mappingKeycode)
        val isBasicEdit = mapping is CtrlMapping.NamedAction && mapping.actionId in BASIC_EDIT_ACTIONS
        val numericForcesBasicEdit = context.isNumericField && isBasicEdit

        if (physicalCombo && !navGrid && !numericForcesBasicEdit) {
            return Resolution(state, clearedTyping, Action.ForwardAsCtrlCombo(shortcutKeycode))
        }

        val consumedOneShot = if (state.ctrl.oneShot && !state.ctrl.latchFromNavMode) {
            state.copy(ctrl = state.ctrl.copy(oneShot = false))
        } else {
            state
        }

        // per-app-behavior.md SS4.6: in a terminal, Ctrl+A is ^A, not "select all", and a tapped
        // or latched Ctrl is as much Ctrl as a held one. Only a held Fn's opt-in nav grid keeps
        // the mappings that send real keys (arrows, Tab, Esc) or run a command.
        if (context.terminalMode && !state.ctrl.latchFromNavMode && isTerminalCtrlKey(stroke.key)) {
            val navGridKeepsMapping = navGrid && (mapping is CtrlMapping.Keycode || mapping is CtrlMapping.Command)
            if (!navGridKeepsMapping) {
                return Resolution(if (physicalCombo) state else consumedOneShot, clearedTyping, Action.ForwardAsCtrlCombo(shortcutKeycode))
            }
        }

        val shiftActive = state.shift.pressed || stroke.meta.shift
        val action = when (mapping) {
            is CtrlMapping.Command -> Action.RunCommand(mapping.commandId)
            is CtrlMapping.NamedAction -> resolveNamedCtrlAction(mapping.actionId, shiftActive)
            CtrlMapping.NativeCtrl -> Action.ForwardAsCtrlCombo(shortcutKeycode)
            is CtrlMapping.Keycode -> resolveCtrlKeycode(mapping.key, shiftActive)
            CtrlMapping.None -> resolveNoCtrlMapping(stroke, physicalCombo, navGrid)
        }
        return Resolution(consumedOneShot, clearedTyping, action)
    }

    /**
     * The keys a terminal reads with Ctrl: everything that types or edits. Back, volume, Home,
     * app switch, the media keys and swipe-to-delete keep their own handling under Ctrl, so a
     * latched Ctrl never takes Back or the volume keys from the system.
     */
    private fun isTerminalCtrlKey(key: KeyId): Boolean = when (key) {
        is KeyId.Letter, is KeyId.Digit, is KeyId.Punctuation -> true
        is KeyId.Control -> key.key in TERMINAL_CTRL_CONTROLS
        is KeyId.Modifier -> false
    }

    private val TERMINAL_CTRL_CONTROLS = setOf(
        ControlKey.SPACE, ControlKey.ENTER, ControlKey.BACKSPACE, ControlKey.TAB, ControlKey.ESCAPE,
        ControlKey.DPAD_UP, ControlKey.DPAD_DOWN, ControlKey.DPAD_LEFT, ControlKey.DPAD_RIGHT,
        ControlKey.MOVE_HOME, ControlKey.MOVE_END, ControlKey.PAGE_UP, ControlKey.PAGE_DOWN, ControlKey.FORWARD_DELETE,
    )

    private fun shortcutKeycodeFor(key: KeyId, layout: LayoutDescription, settings: ModifierSettings): KeyId {
        if (!settings.layoutAwareCtrlShortcuts) return key
        val printed = CharacterResolution.baseCharacter(key, uppercase = false, tapIndex = 0, layout.baseLayout)
        val letter = printed?.singleOrNull()?.takeIf { it.isLetter() } ?: return key
        return KeyId.Letter(letter.uppercaseChar())
    }

    private fun resolveNamedCtrlAction(actionId: String, shiftActive: Boolean): Action {
        val effect = NAMED_CTRL_ACTIONS[actionId] ?: return Action.PassThrough
        return Action.Edit(effect, extendSelection = actionId in SELECTION_EXTENDABLE_ACTIONS && shiftActive)
    }

    private fun resolveCtrlKeycode(key: KeyId, shiftActive: Boolean): Action {
        val control = (key as? KeyId.Control)?.key ?: return Action.PassThrough
        val effect = when (control) {
            ControlKey.DPAD_UP -> EditEffect.CURSOR_UP
            ControlKey.DPAD_DOWN -> EditEffect.CURSOR_DOWN
            ControlKey.DPAD_LEFT -> EditEffect.CURSOR_LEFT
            ControlKey.DPAD_RIGHT -> EditEffect.CURSOR_RIGHT
            ControlKey.DPAD_CENTER -> EditEffect.CURSOR_CENTER
            ControlKey.TAB -> EditEffect.TAB
            ControlKey.MOVE_HOME -> EditEffect.LINE_HOME
            ControlKey.MOVE_END -> EditEffect.LINE_END
            ControlKey.PAGE_UP -> EditEffect.PAGE_UP
            ControlKey.PAGE_DOWN -> EditEffect.PAGE_DOWN
            ControlKey.ESCAPE -> EditEffect.ESCAPE
            ControlKey.FORWARD_DELETE -> EditEffect.DELETE_CHAR_FORWARD
            else -> return Action.PassThrough
        }
        return Action.Edit(effect, extendSelection = control in SELECTION_EXTENDABLE_KEYCODES && shiftActive)
    }

    private fun resolveNoCtrlMapping(stroke: KeyStroke, physicalCombo: Boolean, navGrid: Boolean): Action = when {
        stroke.key == BACKSPACE -> Action.Edit(EditEffect.DELETE_SELECTION_OR_WORD_BACKWARD)
        stroke.key == ENTER || stroke.key == BACK -> Action.PassThrough
        physicalCombo && !navGrid -> Action.ForwardAsCtrlCombo(stroke.key)
        else -> Action.PassThrough
    }

    // -----------------------------------------------------------------
    // Neither Alt nor Ctrl. spec: keys-and-modifiers.md SS7.4; SS9 (multi-tap).
    // -----------------------------------------------------------------

    private fun resolvePlainKey(state: ModifierState, typing: TypingSessionState, stroke: KeyStroke, layout: LayoutDescription, variationsAllowed: Boolean): Resolution {
        val uppercase = state.shiftForcesUppercase(stroke.meta.shift)

        val isRealMultiTap = MultiTap.isMultiTapKey(stroke.key, layout.baseLayout) &&
            !MultiTap.isSharpSException(stroke.key, uppercase, layout.baseLayout)

        if (isRealMultiTap) {
            // spec SS8.1: holding a multi-tap key must not churn through its variants.
            if (stroke.isRepeat) return Resolution(state, typing, Action.Ignored)
            val active = typing.multiTapCycle
            if (active != null && active.key == stroke.key && MultiTap.isWithinWindow(active, stroke.timeMs)) {
                val (newCycle, action) = MultiTap.advance(active, stroke.timeMs, layout.baseLayout)
                val newTyping = typing.copy(multiTapCycle = newCycle, pendingLongPress = armLongPress(stroke, uppercase, newCycle.committedText, layout, variationsAllowed))
                return Resolution(state, newTyping, action)
            }
            val (newCycle, text) = MultiTap.begin(stroke.key, uppercase, stroke.timeMs, layout.baseLayout)
            val newTyping = typing.copy(multiTapCycle = newCycle, pendingLongPress = armLongPress(stroke, uppercase, text, layout, variationsAllowed))
            return Resolution(consumeShiftOneShot(state), newTyping, Action.Commit(text))
        }

        val text = CharacterResolution.layoutOrDefaultCharacter(stroke.key, uppercase, tapIndex = 0, layout.baseLayout)
            ?: return Resolution(state, typing.copy(multiTapCycle = null), Action.PassThrough)

        val newTyping = typing.copy(multiTapCycle = null, pendingLongPress = armLongPress(stroke, uppercase, text, layout, variationsAllowed))
        return Resolution(consumeShiftOneShot(state), newTyping, Action.Commit(text))
    }

    /** spec: keys-and-modifiers.md SS8.2 ("eligibility, computed on key-down"): a repeat of a still-held key never arms a new long press. */
    private fun armLongPress(stroke: KeyStroke, shiftEffective: Boolean, committedText: String, layout: LayoutDescription, variationsAllowed: Boolean): LongPress.Pending? =
        if (stroke.isInitialPress && LongPress.isEligible(stroke.key, shiftEffective, layout, variationsAllowed)) {
            LongPress.arm(stroke.key, shiftEffective, committedText, stroke.timeMs, layout)
        } else {
            null
        }

    private val SPACE = KeyId.Control(ControlKey.SPACE)
    private val ENTER = KeyId.Control(ControlKey.ENTER)
    private val BACK = KeyId.Control(ControlKey.BACK)
    private val BACKSPACE = KeyId.Control(ControlKey.BACKSPACE)
    private val SWIPE_TO_DELETE = KeyId.Control(ControlKey.SWIPE_TO_DELETE)
}
