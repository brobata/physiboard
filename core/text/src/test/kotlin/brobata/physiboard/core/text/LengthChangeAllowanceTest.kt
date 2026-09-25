package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: autocorrect-suggestions.md SS9 step 8. */
class LengthChangeAllowanceTest {
    @Test
    fun `English allows two, any other language none`() {
        assertEquals(2, LengthChangeAllowance.forLanguage("en"))
        assertEquals(2, LengthChangeAllowance.forLanguage("en-US"))
        assertEquals(2, LengthChangeAllowance.forLanguage("en_GB"))
        assertEquals(0, LengthChangeAllowance.forLanguage("fr"))
        assertEquals(0, LengthChangeAllowance.forLanguage(""))
    }
}
