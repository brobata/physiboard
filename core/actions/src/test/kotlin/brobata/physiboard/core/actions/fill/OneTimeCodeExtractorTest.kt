package brobata.physiboard.core.actions.fill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * spec: layers-sym-alt.md SS4.7, the code a notification carries. Every message here is written
 * for this test in the shape real ones take (SMS from services and banks, sign-in mails, an
 * authenticator's reminder); none is copied from a real one.
 */
class OneTimeCodeExtractorTest {

    /** text, title, expected code (null: nothing to take). */
    private data class Case(val text: String, val expected: String?, val title: String = "")

    private val codes = listOf(
        // The everyday English shapes.
        Case("Your verification code is 482913", "482913"),
        Case("482913 is your Instagram code. Don't share it.", "482913"),
        Case("Use 731904 to verify your Acme account.", "731904"),
        Case("Your Uber code: 5521. Never share this code.", "5521"),
        Case("Your one-time password is 90817265. It expires in 10 minutes.", "90817265"),
        Case("G-602144 is your Google verification code.", "602144"),
        Case("Your code is 482 913", "482913"),
        Case("Your security code: 482-913", "482913"),
        Case("Your Microsoft account security code is 6614", "6614"),
        Case("OTP for login: 773201. Valid for 5 mins. Do not share.", "773201"),
        Case("Your PIN is 4821", "4821"),
        Case("Your TAN: 389021", "389021"),
        Case("Your 2FA code is F7K2QX", "F7K2QX"),
        Case("Sign-in code AB12CD for your account", "AB12CD"),
        Case("[Acme] Your login code is 120943. If this wasn't you, reset your password.", "120943"),
        Case("Passcode: 0042", "0042"),
        Case("Here's your code: 918273\n\nThis code expires in 15 minutes.", "918273"),
        Case("Enter 554433 on the sign in page to continue", "554433"),
        Case("Your Acme verification code is: 300114. Reply STOP to 55123 to opt out.", "300114"),
        // A code next to things that are not codes.
        Case("Rs. 5,000 debited from a/c XX1234 on 12-05-24. OTP 778899 for txn. Call 1800-123-4567 if not you.", "778899"),
        Case("Your Amazon OTP is 448812. Do not share it. Order 402-1234567-1234567", "448812"),
        Case("Use code 663300 to confirm your payment of $49.99 at Shop.", "663300"),
        Case("Your code is 2024", "2024"),
        Case("Verification code 482913 sent at 10:42 on 05/06/2025", "482913"),
        Case("Code 991827 for card ending 4432. Questions? +1 (555) 012-3456", "991827"),
        Case("Your Acme Bank sign-in code is 51236. Balance: 1,204.55 EUR", "51236"),
        Case("To finish signing in, enter this code: 274615 at https://acme.example.com/verify?id=88123", "274615"),
        Case("Your verification code is 4821 and your booking ref is 99812", "4821"),
        Case("Your WhatsApp code: 123-456. Don't share this code with others", "123456"),
        Case("Your Apple ID Code is: 551204. Don't share it with anyone.", "551204"),
        Case("Your temporary password: 77221188", "77221188"),
        Case("123456 is your verification code. Order 789012 ships tomorrow.", "123456"),
        Case("Kaufbetrag 23,50 EUR bei Shop. Ihre mTAN lautet 448812.", "448812"),
        Case("Acme: 2-step verification code 093311 (valid 3 minutes)", "093311"),
        // Other languages and scripts.
        Case("Tu código de verificación es 381920", "381920"),
        Case("Seu código de verificação é 220819", "220819"),
        Case("Votre code de vérification est 650212", "650212"),
        Case("Ihr Bestätigungscode lautet 845521", "845521"),
        Case("Il tuo codice di verifica è 712093", "712093"),
        Case("Uw verificatiecode is 304491", "304491"),
        Case("Twój kod weryfikacyjny to 552910", "552910"),
        Case("Ваш код подтверждения: 4417", "4417"),
        Case("Doğrulama kodunuz: 918274", "918274"),
        Case("Kode verifikasi Anda adalah 662010", "662010"),
        Case("Mã xác thực của bạn là 123987", "123987"),
        Case("【Acme】您的验证码是482913，5分钟内有效。", "482913"),
        Case("認証コードは738201です。", "738201"),
        Case("[Web발신] 인증번호 [482913]를 입력해주세요.", "482913"),
        Case("رمز التحقق الخاص بك هو 482913", "482913"),
        Case("आपका ओटीपी 553201 है", "553201"),
        Case("Ваш код: ٤٨٢٩١٣", "482913"),
        Case("認証番号：１２３４５６", "123456"),
        // A short body under a title that names it.
        Case("448291", "448291", title = "Your verification code"),
    )

    private val nothing = listOf(
        // No word that says "code".
        Case("Your package 4482913 is out for delivery", null),
        Case("Meeting moved to 3:30, room 4412", null),
        Case("Call me at 555-123-4567 when you land", null),
        Case("Happy birthday! Born 1990, still going strong", null),
        Case("You paid $1,250.00 to Acme Corp", null),
        Case("Flight AA1234 departs at 14:05 from gate B12", null),
        // A code word, but no code.
        Case("Your verification code has expired. Request a new one.", null),
        Case("Your password was changed on 12/05/2025 at 10:42", null),
        Case("Use promo code SAVE2025 for 20% off", null),
        Case("Your bank code is 51236", null),
        Case("Enter zip code 94103 for local results", null),
        Case("New login from 192.168.10.20 on 2025-05-06", null),
        Case("Your code expires at 12:45", null),
        Case("Verification for order #448291 complete", null),
        Case("Code review: 3 comments on PR 4821 by alex", null),
        Case("Confirm your payment of 2500 to Acme", null),
        Case("Call 0800 123 4567 for your access code", null),
        Case("Your verification code will be sent to +44 7700 900123", null),
        Case("Security code changed for card ending in 4432", null),
        Case("Your code is in the app. Version 4.2.1 is out.", null),
        Case("", null),
        Case("Your order 123456 has shipped", null),
        Case("Two-factor authentication was turned on 06/05/2025", null),
        Case("Your code: we sent it to j***@example.com", null),
        Case("Enter the code we sent to your phone ending in 0199", null),
        // A title naming a code does not make a long unrelated body one.
        Case("Hi! Long time no see. Dinner on the 14th? Table for 4 at 7:30, my treat, bring 2 friends.", null, title = "Code club"),
    )

    @Test
    fun `codes are found in the shapes they come in`() {
        val failures = codes.mapNotNull { case ->
            val got = OneTimeCodeExtractor.extract(case.text, case.title)
            if (got == case.expected) null else "\"${case.text}\" gave $got, expected ${case.expected}"
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test
    fun `nothing is taken from messages without a code`() {
        val failures = nothing.mapNotNull { case ->
            val got = OneTimeCodeExtractor.extract(case.text, case.title)
            if (got == null) null else "\"${case.text}\" gave $got, expected nothing"
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }

    @Test
    fun `a code is never taken from the title`() {
        assertEquals(null, OneTimeCodeExtractor.extract("Your verification code is below", context = "482913"))
        assertEquals(null, OneTimeCodeExtractor.extract("Hello there", context = "Code 482913"))
    }

    @Test
    fun `a nine or ten digit run needs to sit right by the word`() {
        assertEquals("1234567890", OneTimeCodeExtractor.extract("Your code is 1234567890"))
        assertEquals(null, OneTimeCodeExtractor.extract("Your code was sent. Questions: 8005550199"))
    }
}
