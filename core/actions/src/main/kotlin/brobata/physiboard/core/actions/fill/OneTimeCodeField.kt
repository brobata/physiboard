package brobata.physiboard.core.actions.fill

/**
 * What a field tells an input method about itself, in plain values. spec: text-input.md SS3.1.
 *
 * Android hands an input method no autofill hints and no maximum length; what it does hand
 * over is the input type and a few free texts the app or the browser fills in: the hint
 * ("Enter the 6-digit code"), the label, the field's name or id, the private options string,
 * and any text extras. [hints] is all of those, unprocessed. [suggestionHints] is the autofill
 * hints of the inline suggestions a password manager offered for this field (an SMS
 * verification service marks its own with `smsOTPCode`).
 */
data class FieldFacts(
    val inputClass: InputClass,
    /** A password variation: text, visible, web or number password. */
    val passwordVariation: Boolean = false,
    /** A number field that takes a sign or a decimal point, which a code never needs. */
    val signedOrDecimal: Boolean = false,
    val hints: List<String> = emptyList(),
    val suggestionHints: List<String> = emptyList(),
) {
    enum class InputClass { TEXT, NUMBER, PHONE, DATETIME, OTHER }
}

/**
 * Whether a field is one a one-time code goes into. spec: text-input.md SS3.1,
 * layers-sym-alt.md SS4.7. When it is and a code is waiting, the Fill page is the first page Sym
 * opens.
 *
 * - **Named as one**: any hint text naming a one-time code (Android's `smsOTPCode`, the web's
 *   `one-time-code`, "otp", "2fa", "verification code", "código", "验证码"...) or an inline
 *   suggestion marked so. A "promo code", "zip code" and the like is not one.
 * - **A plain number field** (or a number password) whose hints do not name it as something
 *   else (an amount, a quantity, a phone number, a card, a date, a postal code...). Android does
 *   not say how long a field is, so the short maximum length a code field usually has cannot be
 *   checked; a plain number field with a code waiting is taken as the code's field.
 * - Never a phone, date or time field, a signed or decimal number, or a text password field
 *   (a password manager's own suggestions cover that one) unless its hints name a code.
 */
object OneTimeCodeField {

    fun isOneTimeCodeField(facts: FieldFacts): Boolean {
        val hints = Hints(facts.hints)
        if (Hints(facts.suggestionHints).matches(OTP_SUGGESTION_WORDS, OTP_SUGGESTION_COMPOUNDS)) return true
        val otherKind = hints.matches(NOT_A_CODE_WORDS, emptyList())
        if (!otherKind && hints.matches(CODE_WORDS, CODE_COMPOUNDS)) return true
        return when (facts.inputClass) {
            FieldFacts.InputClass.NUMBER -> !facts.signedOrDecimal && !otherKind && !hints.matches(NUMBER_IS_SOMETHING_ELSE, emptyList())
            else -> false
        }
    }

    /**
     * Hint texts read two ways: as words ("smsOTPCode" is sms, otp, code; "one-time-code" is one,
     * time, code), so a short word never matches inside a longer one ("tan" in "instant", "age"
     * in "message"); and run together with nothing but letters and digits ("onetimecode"), for
     * the longer names and for scripts written without spaces.
     */
    private class Hints(texts: List<String>) {
        val words: Set<String> = texts.flatMap { text -> text.split(WORD_BREAK).map { it.lowercase() }.filter { it.isNotEmpty() } }.toSet()
        val joined: String = texts.joinToString(" ") { text -> text.lowercase().filter { it.isLetterOrDigit() } }

        fun matches(wordList: Collection<String>, compounds: Collection<String>): Boolean =
            wordList.any { it in words } || compounds.any { it in joined }
    }

    /** Between words, and inside camelCase ("smsOTPCode", "verificationCode"). */
    private val WORD_BREAK = Regex("""[^\p{L}\p{N}]+|(?<=\p{Ll})(?=\p{Lu})|(?<=\p{Lu})(?=\p{Lu}\p{Ll})""")

    /** `smsOTPCode` (Android's AUTOFILL_HINT_SMS_OTP) and the 2FA hints password managers and the web use. */
    private val OTP_SUGGESTION_WORDS = setOf("otp", "2fa", "totp", "mfa")
    private val OTP_SUGGESTION_COMPOUNDS = listOf("smsotpcode", "onetimecode", "onetimepassword", "twofactor")

    private val CODE_WORDS = setOf(
        "code", "codes", "otp", "2fa", "mfa", "totp", "tan", "passcode", "pincode",
        "codigo", "código", "codice", "kod", "kód", "kode", "код", "kodu",
        "verification", "verificacion", "verificación", "verificação", "verifica", "vérification", "verifizierung",
    )

    private val CODE_COMPOUNDS = listOf(
        "smsotpcode", "onetimecode", "onetimepassword", "onetimepasscode", "onetimepin", "twofactor", "2step", "twostep",
        "verificationcode", "verifycode", "verifcode", "securitycode", "authcode", "authenticationcode", "authenticatorcode",
        "logincode", "signincode", "smscode", "confirmationcode", "accesscode", "digitcode",
        "bestätigungscode", "sicherheitscode", "verificatiecode", "codedevérification", "codedeverification", "codigodeverificacion",
        "códigodeverificación", "códigodeverificação", "codicediverifica",
        "验证码", "驗證碼", "校验码", "认证码", "認證碼", "認証コード", "確認コード", "認証番号", "인증번호", "인증코드",
    )

    /** A field named as some other code or number: never a one-time code's place, even when it says "code". */
    private val NOT_A_CODE_WORDS = setOf(
        "promo", "promotional", "coupon", "discount", "voucher", "gift", "giftcard", "referral", "invite", "invitation", "zip", "postal",
        "postcode", "plz", "country", "area", "dial", "phone", "mobile", "tel", "telephone", "cvv", "cvc", "csc", "cvv2", "card", "iban",
        "routing", "sort", "swift", "bic", "amount", "price", "quantity", "qty", "tracking", "product", "barcode", "qr",
    )

    /** A number field named as something a code never is. */
    private val NUMBER_IS_SOMETHING_ELSE = setOf(
        "amount", "price", "quantity", "qty", "count", "age", "year", "years", "month", "day", "days", "date", "time", "hour", "hours",
        "minute", "minutes", "weight", "height", "size", "guests", "people", "persons", "adults", "children", "tip", "total", "percent",
        "percentage", "rate", "page", "zoom", "volume", "temperature", "card", "expiry", "expiration", "exp", "ssn", "tax", "zip",
        "postal", "phone", "mobile", "street", "house", "floor", "unit", "apt", "score", "rating", "level", "duration", "distance",
        "speed", "number", "id", "account", "member", "invoice", "order",
    )
}
