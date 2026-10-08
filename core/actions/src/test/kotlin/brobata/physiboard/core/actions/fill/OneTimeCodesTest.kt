package brobata.physiboard.core.actions.fill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: layers-sym-alt.md SS4.7, the codes held for the Fill page, and text-input.md SS3.1, the fields they go into. */
class OneTimeCodesTest {

    private val minute = 60_000L

    private fun code(value: String, at: Long, source: String = "Messages", key: String = "com.example.sms") = OneTimeCode(value, source, key, at)

    @Test
    fun `codes are listed newest first, at most three`() {
        val store = OneTimeCodeStore()
        store.add(code("111111", 0), 0)
        store.add(code("222222", minute), minute)
        store.add(code("333333", 2 * minute), 2 * minute)
        store.add(code("444444", 3 * minute), 3 * minute)
        assertEquals(listOf("444444", "333333", "222222"), store.current(3 * minute).map { it.code })
    }

    @Test
    fun `a code expires ten minutes after it arrived`() {
        val store = OneTimeCodeStore()
        store.add(code("111111", 0), 0)
        assertEquals(1, store.current(10 * minute - 1).size)
        assertTrue(store.current(10 * minute).isEmpty())
        assertNull(store.nextExpiryAtMs())
    }

    @Test
    fun `an already expired code is not added, and the same code from the same app is listed once`() {
        val store = OneTimeCodeStore()
        assertFalse(store.add(code("111111", 0), 11 * minute))
        store.add(code("222222", 0), 0)
        store.add(code("333333", minute), minute)
        store.add(code("222222", 2 * minute), 2 * minute)
        assertEquals(listOf("222222", "333333"), store.current(2 * minute).map { it.code })
        store.add(code("222222", 2 * minute, source = "Gmail", key = "com.example.mail"), 2 * minute)
        assertEquals(3, store.current(2 * minute).size)
    }

    @Test
    fun `a backlog code goes behind newer ones`() {
        val store = OneTimeCodeStore()
        store.add(code("222222", 5 * minute), 5 * minute)
        store.add(code("111111", minute), 5 * minute)
        assertEquals(listOf("222222", "111111"), store.current(5 * minute).map { it.code })
    }

    @Test
    fun `typing a code removes it, and clear forgets them all`() {
        val store = OneTimeCodeStore()
        val first = code("111111", 0)
        store.add(first, 0)
        store.add(code("222222", 0, key = "other"), 0)
        assertTrue(store.remove(first))
        assertEquals(listOf("222222"), store.current(0).map { it.code })
        // Its notification posted again (a conversation re-posted) does not bring it back...
        assertFalse(store.add(code("111111", minute), minute))
        // ...until it would have expired anyway.
        assertTrue(store.add(code("111111", 11 * minute), 11 * minute))
        assertTrue(store.clear(11 * minute))
        assertFalse(store.clear(11 * minute))
        // A code received before the clear, read again from a re-posted notification, stays gone; a newer one is taken.
        assertFalse(store.add(code("333333", 10 * minute), 11 * minute))
        assertTrue(store.add(code("444444", 11 * minute + 1), 11 * minute + 1))
    }

    @Test
    fun `the code line names the app and the age`() {
        assertEquals("Code from Messages · just now", FillLabels.codeLine(code("1", 0), 59_000))
        assertEquals("Code from Messages · 2 min ago", FillLabels.codeLine(code("1", 0), 2 * minute + 5_000))
        assertEquals("just now", FillLabels.age(-5_000))
        assertEquals("Messaging", FillLabels.fallbackSourceName("com.google.android.apps.messaging"))
        assertEquals("Mail", FillLabels.fallbackSourceName("org.example.mail"))
    }

    private fun facts(
        inputClass: FieldFacts.InputClass,
        hints: List<String> = emptyList(),
        password: Boolean = false,
        signed: Boolean = false,
        suggestionHints: List<String> = emptyList(),
    ) = FieldFacts(inputClass, password, signed, hints, suggestionHints)

    @Test
    fun `a field named as a code is one, in any input type`() {
        val named = listOf(
            "smsOTPCode", "one-time-code", "otp_input", "Enter the 6-digit code", "verificationCode", "2FA code", "Security code",
            "Código de verificación", "Bestätigungscode", "验证码", "Passcode", "TAN",
        )
        for (hint in named) {
            assertTrue(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.TEXT, listOf(hint))), hint)
        }
        assertTrue(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.TEXT, suggestionHints = listOf("smsOTPCode"))))
    }

    @Test
    fun `other codes and look-alike words are not`() {
        val other = listOf("Promo code", "Zip code", "Country code", "Gift card code", "Card security code (CVV)", "Message", "Instant reply", "Username", "Confirm password")
        for (hint in other) {
            assertFalse(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.TEXT, listOf(hint))), hint)
        }
        assertFalse(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.TEXT, password = true)), "a text password field")
    }

    @Test
    fun `a plain number field is one unless it is named as something else`() {
        assertTrue(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.NUMBER)))
        assertTrue(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.NUMBER, password = true)))
        assertTrue(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.NUMBER, listOf("Code from the message"))))
        assertFalse(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.NUMBER, signed = true)))
        for (hint in listOf("Amount", "Quantity", "Your age", "Zip", "Card number", "Number of guests")) {
            assertFalse(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.NUMBER, listOf(hint))), hint)
        }
        assertFalse(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.PHONE)))
        assertFalse(OneTimeCodeField.isOneTimeCodeField(facts(FieldFacts.InputClass.DATETIME)))
    }
}
