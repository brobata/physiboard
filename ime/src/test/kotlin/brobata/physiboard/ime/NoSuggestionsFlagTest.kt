package brobata.physiboard.ime

import android.text.InputType
import brobata.physiboard.core.text.FieldContext
import brobata.physiboard.core.text.FieldKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * spec: text-input.md SS3, "the keyboard sets this flag itself on every editable field it
 * starts, so the app's own spell-check underline is suppressed." Before this test [KeyboardSession
 * .onStartInput] never called [inputTypeWithNoSuggestionsFlag]/[applyNoSuggestionsFlag] at all, so
 * the flag was never set on the running keyboard despite the tested classifier already reading the
 * app's own value of it ([FieldContext.appDisablesSuggestions]).
 *
 * Only [inputTypeWithNoSuggestionsFlag] is exercised here: it is the pure `Int -> Int` half of the
 * rule, with no live `EditorInfo` involved, matching this module's "no android import in a test
 * unless the class under test needs one" boundary (only [InputType]'s compile-time-constant flag
 * bits are referenced here, never an `EditorInfo` instance).
 */
class NoSuggestionsFlagTest {

    @Test
    fun `an editable field gets the no-suggestions bit set`() {
        val plain = InputType.TYPE_CLASS_TEXT
        val result = inputTypeWithNoSuggestionsFlag(plain, FieldContext(FieldKind.NORMAL))

        assertEquals(plain or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, result)
    }

    @Test
    fun `a field that already had the bit is left alone, not double-set`() {
        val alreadyFlagged = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val result = inputTypeWithNoSuggestionsFlag(alreadyFlagged, FieldContext(FieldKind.NORMAL))

        assertEquals(alreadyFlagged, result)
    }

    @Test
    fun `a not-editable field is left untouched`() {
        val plain = 0
        val result = inputTypeWithNoSuggestionsFlag(plain, FieldContext(FieldKind.NOT_EDITABLE))

        assertEquals(plain, result)
    }

    @Test
    fun `every other field kind still gets the bit, including password and URL`() {
        for (kind in listOf(FieldKind.PASSWORD, FieldKind.URL, FieldKind.EMAIL, FieldKind.FILTER, FieldKind.NUMBER_OR_PHONE)) {
            val result = inputTypeWithNoSuggestionsFlag(0, FieldContext(kind))
            assertEquals(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, result, "expected the flag set for $kind")
        }
    }
}
