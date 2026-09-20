package brobata.physiboard.inputmethod

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.inputmethod.BaseInputConnection
import brobata.physiboard.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Where dictated words land relative to what was already in the field.
 *
 * Partials are composed with the cursor after them, so that a session ending on a partial
 * leaves the cursor behind the words. That means the text "before the cursor" while composing
 * is the utterance itself. The spacing and capitalisation of an utterance must be decided from
 * the text ahead of it, once, or the final prepends a space to its own words and a later partial
 * loses its capital because it "follows a letter".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SpeechRecognitionUtteranceContextTest {

    private lateinit var context: Context
    private lateinit var editor: BaseInputConnection
    private lateinit var manager: SpeechRecognitionManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SettingsManager.setAutoCapitalizeFirstLetter(context, true)
        editor = BaseInputConnection(View(context), true)
        manager = SpeechRecognitionManager(context, { editor })
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun text() = editor.editable.toString()

    @Test
    fun `a dictation into an empty field does not start with a space`() {
        manager.updatePartialSpeechText("hello")
        idle()
        manager.updatePartialSpeechText("hello there")
        idle()
        assertEquals("Hello there", text())

        manager.finishUtterance("hello there")
        idle()
        assertEquals("Hello there ", text())
    }

    @Test
    fun `a later partial keeps the capital the first one got`() {
        manager.updatePartialSpeechText("hello")
        idle()
        manager.updatePartialSpeechText("hello there my friend")
        idle()
        assertEquals("Hello there my friend", text())
    }

    @Test
    fun `words spoken after a word get one space ahead of them`() {
        editor.commitText("Note", 1)
        manager.updatePartialSpeechText("to self")
        idle()
        manager.finishUtterance("to self")
        idle()
        assertEquals("Note to self ", text())
    }

    @Test
    fun `words spoken after a sentence are capitalised and spaced once`() {
        editor.commitText("Done. ", 1)
        manager.updatePartialSpeechText("next")
        idle()
        manager.updatePartialSpeechText("next thing")
        idle()
        manager.finishUtterance("next thing")
        idle()
        assertEquals("Done. Next thing ", text())
    }

    @Test
    fun `a final without partials still reads the field`() {
        editor.commitText("Note", 1)
        manager.finishUtterance("to self")
        idle()
        assertEquals("Note to self ", text())
    }

    @Test
    fun `a second utterance in the same session goes after the first`() {
        manager.updatePartialSpeechText("first sentence")
        idle()
        manager.finishUtterance("first sentence")
        idle()
        manager.updatePartialSpeechText("second")
        idle()
        manager.finishUtterance("second one")
        idle()
        assertEquals("First sentence second one ", text())
    }
}
