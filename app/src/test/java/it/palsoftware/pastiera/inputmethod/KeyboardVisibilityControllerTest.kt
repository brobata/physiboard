package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.inputmethod.InputConnection
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.SymLayoutController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardVisibilityControllerTest {

    @After
    fun tearDown() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.AUTO)
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
        assertEquals(listOf(false), harness.candidatesSurfaceActiveChanges)
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
        assertEquals(listOf(true), harness.candidatesSurfaceActiveChanges)
        assertEquals(2, harness.statusBarRefreshes)
        assertEquals(0, harness.candidatesContainerRefreshes)

        harness.runPostedActions()

        assertEquals(1, harness.candidatesContainerRefreshes)
        assertEquals(3, harness.statusBarRefreshes)
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

    @Test
    fun switchingBackBeforeContainerRefreshDoesNotReshowCandidates() {
        val harness = createHarness()

        harness.controller.onEvaluateInputViewShown(shouldShowInputView = false)
        harness.runPostedActions()
        harness.controller.onEvaluateInputViewShown(shouldShowInputView = true)
        harness.runPostedActions()

        assertFalse(harness.candidatesViewShown)
        assertEquals(0, harness.candidatesContainerRefreshes)
    }

    @Test
    fun runtimeVirtualOverrideShowsInputViewDespiteSystemHardwareDecision() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.AUTO)
        SettingsManager.setSoftwareKeyboardModeRuntimeOverride(
            context,
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        )
        val harness = createHarness()

        assertTrue(harness.controller.onEvaluateInputViewShown(shouldShowInputView = false))
        harness.runPostedActions()

        assertFalse(harness.candidatesViewShown)
        assertEquals(1, harness.candidatesVisibilityChanges)
        assertEquals(listOf(false), harness.candidatesSurfaceActiveChanges)
        assertEquals(2, harness.statusBarRefreshes)
    }

    @Test
    fun runtimeHardwareOverrideHidesInputViewDespiteSystemVirtualDecision() {
        val context = RuntimeEnvironment.getApplication()
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.AUTO)
        SettingsManager.setSoftwareKeyboardModeRuntimeOverride(
            context,
            SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE
        )
        val harness = createHarness()

        assertFalse(harness.controller.onEvaluateInputViewShown(shouldShowInputView = true))
        harness.runPostedActions()

        assertTrue(harness.candidatesViewShown)
        assertEquals(1, harness.candidatesVisibilityChanges)
        assertEquals(2, harness.statusBarRefreshes)
    }

    @Test
    fun explicitVirtualSurfaceChangeForcesInputViewFromCandidatesOnlyMode() {
        val harness = createHarness(currentInputConnection = mock(InputConnection::class.java))

        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = true)

        assertFalse(harness.candidatesViewShown)
        assertEquals(1, harness.candidatesVisibilityChanges)
        assertEquals(listOf(true), harness.inputWindowShowRequests)
    }

    @Test
    fun repeatedVirtualSurfaceRequestRetriesWhenFirstRequestDidNotRender() {
        val harness = createHarness(currentInputConnection = mock(InputConnection::class.java))

        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = true)
        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = true)

        assertEquals(listOf(true, true), harness.inputWindowShowRequests)
    }

    @Test
    fun renderedVirtualSurfaceDoesNotReopenWindow() {
        val harness = createHarness(
            currentInputConnection = mock(InputConnection::class.java),
            inputViewShown = true
        )

        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = true)

        assertTrue(harness.inputWindowShowRequests.isEmpty())
    }

    @Test
    fun virtualSurfaceRequestRetriesAfterFrameworkDidNotRenderIt() {
        val harness = createHarness(currentInputConnection = mock(InputConnection::class.java))

        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = true)
        harness.runPostedActions()

        assertEquals(listOf(true, true), harness.inputWindowShowRequests)

        harness.renderSurface(KeyboardVisibilityController.RenderedSurface.FULL_INPUT_VIEW)
        harness.runPostedActions()

        assertEquals(listOf(true, true), harness.inputWindowShowRequests)
    }

    @Test
    fun failedSurfaceTransitionStopsAfterBoundedRetries() {
        val harness = createHarness(currentInputConnection = mock(InputConnection::class.java))

        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = true)
        repeat(8) { harness.runPostedActions() }

        assertEquals(6, harness.inputWindowShowRequests.size)
        assertEquals(listOf(false), harness.requestedInputViewShownChanges)
        assertTrue(harness.controller.isCandidatesOnlySurface())
    }

    @Test
    fun cancelledSurfaceTransitionDoesNotRetry() {
        val harness = createHarness(currentInputConnection = mock(InputConnection::class.java))

        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = true)
        harness.controller.cancelPendingSurfaceTransition()
        harness.runPostedActions()

        assertEquals(listOf(true), harness.inputWindowShowRequests)
    }

    @Test
    fun frameworkEvaluationKeepsResolvedSurfaceStateInSync() {
        val harness = createHarness(currentInputConnection = mock(InputConnection::class.java))

        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = true)
        harness.controller.onEvaluateInputViewShown(shouldShowInputView = false)

        assertEquals(listOf(true), harness.inputWindowShowRequests)
    }

    @Test
    fun explicitHardwareSurfaceChangeRestartsAsCandidatesOnlyMode() {
        val harness = createHarness(
            currentInputConnection = mock(InputConnection::class.java),
            inputViewShown = true
        )

        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = false)

        assertTrue(harness.candidatesViewShown)
        assertEquals(listOf(false), harness.inputWindowShowRequests)
        assertEquals(listOf(true), harness.candidatesSurfaceActiveChanges)

        harness.runPostedActions()
        assertEquals(1, harness.candidatesContainerRefreshes)
    }

    @Test
    fun completedHardwareTransitionCanImmediatelyToggleBackToVirtual() {
        val harness = createHarness(
            currentInputConnection = mock(InputConnection::class.java),
            inputViewShown = true
        )

        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = false)
        harness.renderSurface(KeyboardVisibilityController.RenderedSurface.CANDIDATES_VIEW)
        harness.controller.onKeyboardSurfaceChanged(ensureInputViewShown = true)

        assertEquals(listOf(false, true), harness.inputWindowShowRequests)
    }

    private fun createHarness(
        currentInputConnection: InputConnection? = null,
        inputViewShown: Boolean = false
    ): VisibilityHarness {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("keyboard_visibility_controller_test", Context.MODE_PRIVATE)
        val altSymManager = AltSymManager(context.assets, prefs, context)
        val symLayoutController = SymLayoutController(context, prefs, altSymManager)
        val candidatesBarController = CandidatesBarController(context)
        var candidatesViewShown = false
        var candidatesVisibilityChanges = 0
        val candidatesSurfaceActiveChanges = mutableListOf<Boolean>()
        var candidatesContainerRefreshes = 0
        var statusBarRefreshes = 0
        val requestedInputViewShownChanges = mutableListOf<Boolean>()
        val inputViewShowRequests = mutableListOf<Boolean>()
        val postedActions = mutableListOf<() -> Unit>()
        val inputWindowShowRequests = mutableListOf<Boolean>()
        var renderedSurface = if (inputViewShown) {
            KeyboardVisibilityController.RenderedSurface.FULL_INPUT_VIEW
        } else {
            KeyboardVisibilityController.RenderedSurface.CANDIDATES_VIEW
        }

        val controller = KeyboardVisibilityController(
            context = context,
            candidatesBarController = candidatesBarController,
            symLayoutController = symLayoutController,
            isInputViewActive = { false },
            hasActiveTextField = { false },
            isNavModeLatched = { false },
            currentInputConnection = { currentInputConnection },
            isInputViewShown = { inputViewShown },
            renderedSurface = { renderedSurface },
            setRequestedInputViewShown = { requestedInputViewShownChanges += it },
            attachInputView = {},
            setCandidatesSurfaceActive = { candidatesSurfaceActiveChanges += it },
            setCandidatesViewShown = {
                candidatesViewShown = it
                candidatesVisibilityChanges += 1
            },
            synchronizeCandidatesContainerVisibility = {
                candidatesContainerRefreshes += 1
            },
            postToUi = { postedActions += it },
            postToUiDelayed = { _, action -> postedActions += action },
            showInputWindow = { shown -> inputWindowShowRequests += shown },
            requestShowInputView = { inputViewShowRequests += false },
            refreshStatusBar = { statusBarRefreshes += 1 }
        )

        return VisibilityHarness(
            controller = controller,
            onCandidatesViewShown = { candidatesViewShown },
            onCandidatesVisibilityChanges = { candidatesVisibilityChanges },
            onCandidatesContainerRefreshes = { candidatesContainerRefreshes },
            runPostedActions = {
                postedActions.toList().also { postedActions.clear() }.forEach { it() }
            },
            onInputWindowShowRequests = { inputWindowShowRequests.toList() },
            onInputViewShowRequests = { inputViewShowRequests.toList() },
            renderSurface = { renderedSurface = it },
            onRequestedInputViewShownChanges = { requestedInputViewShownChanges.toList() },
            onCandidatesSurfaceActiveChanges = { candidatesSurfaceActiveChanges.toList() },
            onStatusBarRefreshes = { statusBarRefreshes }
        )
    }

    private class VisibilityHarness(
        val controller: KeyboardVisibilityController,
        private val onCandidatesViewShown: () -> Boolean,
        private val onCandidatesVisibilityChanges: () -> Int,
        private val onCandidatesContainerRefreshes: () -> Int,
        val runPostedActions: () -> Unit,
        private val onInputWindowShowRequests: () -> List<Boolean>,
        private val onInputViewShowRequests: () -> List<Boolean>,
        val renderSurface: (KeyboardVisibilityController.RenderedSurface) -> Unit,
        private val onRequestedInputViewShownChanges: () -> List<Boolean>,
        private val onCandidatesSurfaceActiveChanges: () -> List<Boolean>,
        private val onStatusBarRefreshes: () -> Int
    ) {
        val candidatesViewShown: Boolean
            get() = onCandidatesViewShown()
        val candidatesVisibilityChanges: Int
            get() = onCandidatesVisibilityChanges()
        val candidatesContainerRefreshes: Int
            get() = onCandidatesContainerRefreshes()
        val inputWindowShowRequests: List<Boolean>
            get() = onInputWindowShowRequests()
        val inputViewShowRequests: List<Boolean>
            get() = onInputViewShowRequests()
        val statusBarRefreshes: Int
            get() = onStatusBarRefreshes()
        val requestedInputViewShownChanges: List<Boolean>
            get() = onRequestedInputViewShownChanges()
        val candidatesSurfaceActiveChanges: List<Boolean>
            get() = onCandidatesSurfaceActiveChanges()
    }
}
