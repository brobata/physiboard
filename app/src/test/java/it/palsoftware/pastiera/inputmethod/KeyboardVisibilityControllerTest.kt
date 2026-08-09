package it.palsoftware.pastiera.inputmethod

import android.content.Context
import it.palsoftware.pastiera.core.SymLayoutController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardVisibilityControllerTest {

    @After
    fun tearDown() {
        SoftwareKeyboardAutoDetector.onInputDevicesChanged()
    }

    @Test
    fun systemShowingInputView_hidesCandidatesViewAndKeepsInputViewShown() {
        val harness = createHarness()

        assertTrue(harness.controller.onEvaluateInputViewShown(shouldShowInputView = true))
        assertEquals(0, harness.candidatesVisibilityChanges)

        harness.runPostedActions()

        assertFalse(harness.candidatesViewShown)
        assertEquals(1, harness.candidatesVisibilityChanges)
        assertEquals(2, harness.statusBarRefreshes)
    }

    @Test
    fun systemHidingInputView_showsCandidatesViewWithoutForcingInputView() {
        val harness = createHarness()

        assertFalse(harness.controller.onEvaluateInputViewShown(shouldShowInputView = false))
        assertEquals(0, harness.candidatesVisibilityChanges)

        harness.runPostedActions()

        assertTrue(harness.candidatesViewShown)
        assertEquals(1, harness.candidatesVisibilityChanges)
        assertEquals(2, harness.statusBarRefreshes)
    }

    @Test
    fun stalePostedVisibilityDoesNotOverrideLatestSystemDecision() {
        val harness = createHarness()

        harness.controller.onEvaluateInputViewShown(shouldShowInputView = true)
        harness.controller.onEvaluateInputViewShown(shouldShowInputView = false)
        harness.runPostedActions()

        assertTrue(harness.candidatesViewShown)
        assertEquals(1, harness.candidatesVisibilityChanges)
    }

    private fun createHarness(): VisibilityHarness {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("keyboard_visibility_controller_test", Context.MODE_PRIVATE)
        val altSymManager = AltSymManager(context.assets, prefs, context)
        val symLayoutController = SymLayoutController(context, prefs, altSymManager)
        val candidatesBarController = CandidatesBarController(context)
        var candidatesViewShown = false
        var candidatesVisibilityChanges = 0
        var statusBarRefreshes = 0
        val postedActions = mutableListOf<() -> Unit>()

        val controller = KeyboardVisibilityController(
            context = context,
            candidatesBarController = candidatesBarController,
            symLayoutController = symLayoutController,
            isInputViewActive = { false },
            hasActiveTextField = { false },
            isNavModeLatched = { false },
            currentInputConnection = { null },
            isInputViewShown = { false },
            attachInputView = {},
            setCandidatesViewShown = {
                candidatesViewShown = it
                candidatesVisibilityChanges += 1
            },
            postToUi = { postedActions += it },
            requestShowInputView = {},
            refreshStatusBar = { statusBarRefreshes += 1 }
        )

        return VisibilityHarness(
            controller = controller,
            onCandidatesViewShown = { candidatesViewShown },
            onCandidatesVisibilityChanges = { candidatesVisibilityChanges },
            runPostedActions = {
                postedActions.toList().also { postedActions.clear() }.forEach { it() }
            },
            onStatusBarRefreshes = { statusBarRefreshes }
        )
    }

    private class VisibilityHarness(
        val controller: KeyboardVisibilityController,
        private val onCandidatesViewShown: () -> Boolean,
        private val onCandidatesVisibilityChanges: () -> Int,
        val runPostedActions: () -> Unit,
        private val onStatusBarRefreshes: () -> Int
    ) {
        val candidatesViewShown: Boolean
            get() = onCandidatesViewShown()
        val candidatesVisibilityChanges: Int
            get() = onCandidatesVisibilityChanges()
        val statusBarRefreshes: Int
            get() = onStatusBarRefreshes()
    }
}
