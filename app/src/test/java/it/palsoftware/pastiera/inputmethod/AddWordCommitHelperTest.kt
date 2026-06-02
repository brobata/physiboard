package it.palsoftware.pastiera.inputmethod

import android.content.Context
import android.view.View
import android.view.inputmethod.BaseInputConnection
import it.palsoftware.pastiera.core.AutoSpaceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AddWordCommitHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AutoSpaceTracker.clear()
    }

    @Test
    fun commitAutoSpaceAfterAddWordAppendsSpaceAndMarksItPending() {
        val inputConnection = FakeInputConnection(context, "Wiederspruch")

        AddWordCommitHelper.commitAutoSpaceAfterAddWord(inputConnection)

        assertEquals("Wiederspruch ", inputConnection.text)
        assertTrue(AutoSpaceTracker.isPending())
    }

    @Test
    fun committedAddWordAutoSpaceIsReplacedByFollowingPunctuation() {
        val inputConnection = FakeInputConnection(context, "Wiederspruch")

        AddWordCommitHelper.commitAutoSpaceAfterAddWord(inputConnection)
        val replaced = AutoSpaceTracker.replaceAutoSpaceWithPunctuation(inputConnection, ".")

        assertTrue(replaced)
        assertEquals("Wiederspruch. ", inputConnection.text)
        assertFalse(AutoSpaceTracker.isPending())
    }

    @Test
    fun commitAutoSpaceAfterAddWordDoesNotAppendBeforeExistingBoundary() {
        val inputConnection = FakeInputConnection(context, "Wiederspruch", ".")

        AddWordCommitHelper.commitAutoSpaceAfterAddWord(inputConnection)

        assertEquals("Wiederspruch", inputConnection.text)
        assertFalse(AutoSpaceTracker.isPending())
    }

    private class FakeInputConnection(
        context: Context,
        initialText: String,
        private val textAfterCursor: String = ""
    ) : BaseInputConnection(View(context), true) {
        private val buffer = StringBuilder(initialText)

        val text: String
            get() = buffer.toString()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            return buffer.takeLast(n)
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
            return textAfterCursor.take(n)
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val deleteStart = (buffer.length - beforeLength).coerceAtLeast(0)
            buffer.delete(deleteStart, buffer.length)
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            buffer.append(text ?: "")
            return true
        }
    }
}
