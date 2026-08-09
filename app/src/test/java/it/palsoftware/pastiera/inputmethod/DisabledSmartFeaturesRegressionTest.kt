package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.core.AutoCorrectionManager
import it.palsoftware.pastiera.core.InputContextState
import it.palsoftware.pastiera.core.ModifierStateController
import it.palsoftware.pastiera.core.NavModeController
import it.palsoftware.pastiera.core.TextInputController
import it.palsoftware.pastiera.core.suggestions.FakeDictionaryRepository
import it.palsoftware.pastiera.core.suggestions.SuggestionController
import it.palsoftware.pastiera.core.suggestions.SuggestionSettings
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DisabledSmartFeaturesRegressionTest {

    private lateinit var context: Context
    private val controllers = mutableListOf<SuggestionController>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        SettingsManager.setAutoCorrectEnabled(context, false)
        SettingsManager.setExperimentalSuggestionsEnabled(context, false)
        SettingsManager.setSuggestionsEnabled(context, false)
        SettingsManager.setAutoReplaceOnSpaceEnter(context, false)
        AutoCorrector.loadCorrections(context.assets, context)
    }

    @After
    fun tearDown() {
        controllers.forEach { it.destroy() }
    }

    @Test
    fun physicalKeyboardSpaceDoesNotReplaceIdWhenAllSmartFeaturesAreDisabled() {
        val inputConnection = FakeInputConnection(context, "id")
        val modifierStateController = ModifierStateController(500L)
        val router = InputEventRouter(
            context,
            NavModeController(context, modifierStateController)
        ).apply {
            suggestionController = newDisabledSuggestionController()
        }
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_NONE
        }

        val handled = router.handleTextInputPipeline(
            context = context,
            keyCode = KeyEvent.KEYCODE_SPACE,
            event = null,
            inputConnection = inputConnection,
            shouldDisableSuggestions = false,
            shouldDisableAutoCorrect = false,
            shouldDisableAutoCapitalize = true,
            shouldDisableDoubleSpaceToPeriod = true,
            isAutoCorrectEnabled = false,
            textInputController = TextInputController(context, modifierStateController, 500L),
            autoCorrectionManager = AutoCorrectionManager(context),
            inputContextState = InputContextState.fromEditorInfo(editorInfo),
            enableShiftOneShot = null,
            editorInfo = editorInfo,
            updateStatusBar = {}
        )

        assertTrue(handled)
        assertEquals("id ", inputConnection.text)
    }

    @Test
    fun onScreenKeyboardSpaceDoesNotReplaceIdWhenAllSmartFeaturesAreDisabled() {
        val inputConnection = FakeInputConnection(context, "id")
        val suggestionController = newDisabledSuggestionController()
        val modifierStateController = ModifierStateController(500L)

        val handled = SoftwareKeyboardTextInputHandler.handleSpaceInput(
            textInputController = TextInputController(context, modifierStateController, 500L),
            inputConnection = inputConnection,
            shouldDisableDoubleSpaceToPeriod = true,
            shouldDisableAutoCapitalize = true,
            shouldDisableSuggestions = false,
            onDoubleSpaceHandled = {},
            onNormalBoundary = {
                suggestionController.onBoundaryKey(KeyEvent.KEYCODE_SPACE, null, inputConnection).committed
            },
            onCommitSpace = { inputConnection.commitText(" ", 1) },
            onStatusBarUpdate = {}
        )

        assertTrue(handled)
        assertEquals("id ", inputConnection.text)
    }

    private fun newDisabledSuggestionController(): SuggestionController {
        val repository = FakeDictionaryRepository().apply { isReady = true }
        return SuggestionController(
            context = context,
            assets = context.assets,
            settingsProvider = {
                SuggestionSettings(
                    textReplacementsEnabled = false,
                    suggestionsEnabled = false,
                    autoReplaceOnSpaceEnter = false
                )
            },
            isEnabled = { false },
            onSuggestionsUpdated = {},
            currentLocale = Locale.ENGLISH,
            dictionaryRepositoryFactory = { _, _, _, _, _ -> repository }
        ).also { controllers.add(it) }
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)

        val text: String
            get() = buffer.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = buffer.takeLast(n)

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(deleteStart, buffer.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text)
            return true
        }

        override fun beginBatchEdit(): Boolean = true

        override fun endBatchEdit(): Boolean = true

        override fun finishComposingText(): Boolean = true
    }
}
