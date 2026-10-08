package brobata.physiboard.ime

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import brobata.physiboard.core.actions.fill.FieldFacts
import brobata.physiboard.core.actions.fill.OneTimeCodeField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** text-input.md SS3.1: what a real EditorInfo tells the code-field check. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FillFieldFactsTest {

    private fun info(inputType: Int, hint: String? = null, extras: Bundle? = null) = EditorInfo().apply {
        this.inputType = inputType
        hintText = hint
        this.extras = extras
    }

    private fun isCodeField(info: EditorInfo): Boolean = OneTimeCodeField.isOneTimeCodeField(fillFieldFacts(info)!!)

    @Test
    fun `a number field and a number password field are code fields`() {
        assertTrue(isCodeField(info(InputType.TYPE_CLASS_NUMBER)))
        assertTrue(isCodeField(info(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)))
        val facts = fillFieldFacts(info(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))!!
        assertEquals(FieldFacts.InputClass.NUMBER, facts.inputClass)
        assertTrue(facts.passwordVariation)
    }

    @Test
    fun `a decimal number, a phone field and a plain text field are not`() {
        assertFalse(isCodeField(info(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)))
        assertFalse(isCodeField(info(InputType.TYPE_CLASS_PHONE)))
        assertFalse(isCodeField(info(InputType.TYPE_CLASS_TEXT)))
        assertFalse(isCodeField(info(InputType.TYPE_CLASS_NUMBER, hint = "Amount")))
    }

    @Test
    fun `a text field is one when its hint or extras name a code`() {
        assertTrue(isCodeField(info(InputType.TYPE_CLASS_TEXT, hint = "Enter verification code")))
        val extras = Bundle().apply { putString("autocomplete", "one-time-code") }
        assertTrue(isCodeField(info(InputType.TYPE_CLASS_TEXT, extras = extras)))
        assertFalse(isCodeField(info(InputType.TYPE_CLASS_TEXT, hint = "Promo code")))
    }
}
