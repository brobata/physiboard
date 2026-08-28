package brobata.physiboard.inputmethod.expansion

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import brobata.physiboard.core.InputContextState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TextExpansionControllerTest {
    private val editableState = InputContextState(
        isEditable = true,
        isReallyEditable = true,
        inputClass = InputType.TYPE_CLASS_TEXT,
        inputVariation = InputType.TYPE_TEXT_VARIATION_NORMAL,
        inputType = InputType.TYPE_CLASS_TEXT,
        restrictedReason = null
    )

    @Test
    fun disabledProvidersDoNotQueryTheEditor() {
        // Was written against EmojiShortcodeSource, which also let the test assert that a disabled
        // provider never loaded its asset file. Emoji and symbol shortcodes were removed in 2.0, so
        // the surviving snippet source carries the same contract: disabled means untouched.
        var sourceQueries = 0
        val source = SnippetExpansionSource {
            sourceQueries++
            mapOf("sig" to "regards")
        }
        var editorQueries = 0
        val controller = controller(
            state = editableState,
            configs = listOf(config(source, enabled = false)),
            onEditorQuery = { editorQueries++ }
        )

        controller.refresh()

        assertEquals(0, editorQueries)
        assertEquals(0, sourceQueries)
    }

    @Test
    fun restrictedFieldsDoNotQueryTheEditor() {
        var editorQueries = 0
        val restricted = editableState.copy(restrictedReason = InputContextState.RestrictedReason.EMAIL)
        val controller = controller(
            state = restricted,
            configs = listOf(config(SnippetExpansionSource { mapOf("id" to "value") }, enabled = true)),
            onEditorQuery = { editorQueries++ }
        )

        controller.refresh()

        assertEquals(0, editorQueries)
    }

    @Test
    fun activeSelectionDoesNotQueryOrReplaceEditorText() {
        var editorQueries = 0
        val controller = TextExpansionController(
            context = RuntimeEnvironment.getApplication(),
            handler = Handler(Looper.getMainLooper()),
            inputConnectionProvider = {
                editorQueries++
                null
            },
            inputContextProvider = { editableState },
            isSelectionCollapsedProvider = { false },
            anchorProvider = { null },
            configsProvider = {
                listOf(config(SnippetExpansionSource { mapOf("id" to "value") }, enabled = true))
            },
            showSuggestionBar = { _, _ -> },
            clearSuggestionBar = {},
            requestSurfaceUpdate = {},
            onCommitted = {}
        )

        controller.refresh()

        assertEquals(0, editorQueries)
    }

    @Test
    fun activationKeyRefreshesImmediatelyBeforeAQueuedRefreshCanRun() {
        val inputConnection = mock(InputConnection::class.java)
        `when`(inputConnection.getTextBeforeCursor(TextExpansionEngine.MAX_CONTEXT_CHARS, 0))
            .thenReturn("!sig")
        val source = SnippetExpansionSource { mapOf("sig" to "Regards") }
        val controller = TextExpansionController(
            context = RuntimeEnvironment.getApplication(),
            handler = Handler(Looper.getMainLooper()),
            inputConnectionProvider = { inputConnection },
            inputContextProvider = { editableState },
            anchorProvider = { null },
            configsProvider = {
                listOf(
                    ExpansionRuntimeConfig(
                        source = source,
                        triggerKind = ExpansionTriggerKind.PREFIX,
                        enabled = true,
                        prefix = '!',
                        presentation = ExpansionPresentation.OFF,
                        activationPolicy = ExpansionActivationPolicy(
                            exactOnSpace = true,
                            acceptPrefixWithSpace = false,
                            acceptWithTab = true,
                            acceptWithEnter = false
                        )
                    )
                )
            },
            showSuggestionBar = { _, _ -> },
            clearSuggestionBar = {},
            requestSurfaceUpdate = {},
            onCommitted = {}
        )
        controller.scheduleRefresh()

        assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_SPACE))
        verify(inputConnection).deleteSurroundingText(4, 0)
        verify(inputConnection).commitText("Regards ", 1)
    }

    @Test
    fun prefixSpaceOptionAcceptsHighlightedNonExactMatchAndKeepsSpace() {
        val inputConnection = mock(InputConnection::class.java)
        `when`(inputConnection.getTextBeforeCursor(TextExpansionEngine.MAX_CONTEXT_CHARS, 0))
            .thenReturn("!si")
        val controller = TextExpansionController(
            context = RuntimeEnvironment.getApplication(),
            handler = Handler(Looper.getMainLooper()),
            inputConnectionProvider = { inputConnection },
            inputContextProvider = { editableState },
            anchorProvider = { null },
            configsProvider = {
                listOf(
                    ExpansionRuntimeConfig(
                        source = SnippetExpansionSource { mapOf("sig" to "Regards") },
                        triggerKind = ExpansionTriggerKind.PREFIX,
                        enabled = true,
                        prefix = '!',
                        presentation = ExpansionPresentation.SUGGESTION_BAR,
                        activationPolicy = ExpansionActivationPolicy(
                            exactOnSpace = false,
                            acceptPrefixWithSpace = true,
                            acceptWithTab = true,
                            acceptWithEnter = false
                        )
                    )
                )
            },
            showSuggestionBar = { _, _ -> },
            clearSuggestionBar = {},
            requestSurfaceUpdate = {},
            onCommitted = {}
        )

        assertTrue(controller.handleKeyDown(KeyEvent.KEYCODE_SPACE))
        verify(inputConnection).deleteSurroundingText(3, 0)
        verify(inputConnection).commitText("Regards ", 1)
    }

    private fun config(source: ExpansionSource, enabled: Boolean) = ExpansionRuntimeConfig(
        source = source,
        triggerKind = ExpansionTriggerKind.COLON_SHORTCODE,
        enabled = enabled,
        presentation = ExpansionPresentation.FLOATING_POPUP,
        activationPolicy = ExpansionActivationPolicy(
            exactOnSpace = false,
            acceptPrefixWithSpace = false,
            acceptWithTab = true,
            acceptWithEnter = false
        )
    )

    private fun controller(
        state: InputContextState,
        configs: List<ExpansionRuntimeConfig>,
        onEditorQuery: () -> Unit
    ) = TextExpansionController(
        context = RuntimeEnvironment.getApplication(),
        handler = Handler(Looper.getMainLooper()),
        inputConnectionProvider = {
            onEditorQuery()
            null
        },
        inputContextProvider = { state },
        anchorProvider = { null },
        configsProvider = { configs },
        showSuggestionBar = { _, _ -> },
        clearSuggestionBar = {},
        requestSurfaceUpdate = {},
        onCommitted = {}
    )
}
