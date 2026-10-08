package brobata.physiboard.core.actions.fill

/**
 * Finds the one-time code in a notification's text. spec: layers-sym-alt.md SS4.7.
 *
 * A code is only ever looked for next to a word that says it is one ("code", "OTP",
 * "verification", "PIN", "TAN", "código", "код", "验证码" and the like, [Keywords]); a message
 * without such a word gives nothing. Around that word the extractor takes 4 to 10 digits (a
 * 9 or 10 digit run only right beside the word), two groups of 3 or 4 digits written apart
 * ("482 913", "482-913"), a letter prefix and digits ("G-482913" gives "482913"), or 4 to 10
 * capitals and digits mixing both ("F7K2QX"). Never a phone number, a date or time, an amount, a
 * year (unless right beside the word), an IP address, a masked card or account number, or a
 * number named as an order, reference, tracking, booking or similar number; never anything in a
 * link or an e-mail address. "Promo code", "zip code" and their kind do not count as the word.
 *
 * Pure: no I/O, no logging. The text it is handed is never kept.
 */
object OneTimeCodeExtractor {

    /** Shortest digit code taken. Three-digit "codes" are card security codes far more often than sign-in codes. */
    const val MIN_DIGITS: Int = 4

    /** Longest code taken, digits or letters and digits. */
    const val MAX_LENGTH: Int = 10

    /** A candidate further than this many characters from the word is not taken. */
    const val MAX_DISTANCE: Int = 60

    /**
     * The best code in [text], or null. [context] is the notification's title (the sender or the
     * subject): a keyword there counts for a short body, but a code is never taken from it,
     * since a title is so often a phone number or an SMS short code.
     */
    fun extract(text: String, context: String = ""): String? {
        if (text.isBlank()) return null
        val body = asciiDigits(text)
        val keywords = Keywords.find(body)
        val contextKeyword = context.isNotBlank() && Keywords.find(asciiDigits(context)).isNotEmpty()
        if (keywords.isEmpty() && !contextKeyword) return null
        val blocked = blockedSpans(body)
        val candidates = candidates(body).filter { c -> (c.start until c.end).none { blocked[it] } }
        var best: Scored? = null
        for (candidate in candidates) {
            val scored = score(body, candidate, keywords, contextKeyword) ?: continue
            if (best == null || scored.score > best.score) best = scored
        }
        return best?.candidate?.value
    }

    private class Candidate(val start: Int, val end: Int, val value: String, val kind: Kind)

    private enum class Kind { DIGITS, GROUPED_DIGITS, PREFIXED_DIGITS, ALPHANUMERIC }

    private class Scored(val candidate: Candidate, val score: Int)

    /**
     * Any decimal digit (Arabic-Indic, Devanagari, full-width...) as its ASCII digit, so one
     * pattern reads them all; every character keeps its place, so positions still line up.
     */
    private fun asciiDigits(text: String): String {
        if (text.none { it.isDigit() && it !in '0'..'9' }) return text
        val out = StringBuilder(text.length)
        for (c in text) {
            val digit = if (c.isDigit()) Character.digit(c, 10) else -1
            out.append(if (digit >= 0 && c !in '0'..'9') ('0' + digit) else c)
        }
        return out.toString()
    }

    /**
     * A letter of an alphabetic script, or a digit: what a code may not run into. Chinese,
     * Japanese and Korean are left out on purpose; they write "验证码是482913" with no space.
     */
    private const val WORDISH = """[\p{IsLatin}\p{IsCyrillic}\p{IsGreek}\p{N}+]"""

    private val DIGITS = Regex("""(?<!$WORDISH)\d{4,10}(?!$WORDISH)""")
    private val GROUPED = Regex("""(?<![\p{IsLatin}\p{IsCyrillic}\p{IsGreek}\p{N}+\-.,/:])(\d{3,4})[ \-](\d{3,4})(?!$WORDISH|[\-.,/:]\d)""")
    private val PREFIXED = Regex("""(?<!$WORDISH)[A-Z]{1,3}-(\d{4,8})(?!$WORDISH)""")
    private val ALPHANUMERIC = Regex("""(?<!$WORDISH)(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{4,10}(?!$WORDISH)""")

    private fun candidates(text: String): List<Candidate> {
        val out = ArrayList<Candidate>()
        GROUPED.findAll(text).forEach { m ->
            out.add(Candidate(m.range.first, m.range.last + 1, m.groupValues[1] + m.groupValues[2], Kind.GROUPED_DIGITS))
        }
        PREFIXED.findAll(text).forEach { m ->
            val digits = m.groups[1]!!
            out.add(Candidate(m.range.first, m.range.last + 1, digits.value, Kind.PREFIXED_DIGITS))
        }
        DIGITS.findAll(text).forEach { m ->
            // A digit run straight after a letter prefix and a hyphen is that prefixed code.
            if (m.range.first >= 2 && text[m.range.first - 1] == '-' && text[m.range.first - 2].isLetter()) return@forEach
            out.add(Candidate(m.range.first, m.range.last + 1, m.value, Kind.DIGITS))
        }
        ALPHANUMERIC.findAll(text).forEach { m ->
            if (m.value.all { it.isDigit() }) return@forEach
            out.add(Candidate(m.range.first, m.range.last + 1, m.value, Kind.ALPHANUMERIC))
        }
        return out
    }

    private fun score(text: String, candidate: Candidate, keywords: List<IntRange>, contextKeyword: Boolean): Scored? {
        val value = candidate.value
        if (value.length > MAX_LENGTH) return null
        var nearest = Int.MAX_VALUE
        var strong = false
        for (keyword in keywords) {
            if (candidate.start >= keyword.last + 1) {
                val gap = text.substring(keyword.last + 1, candidate.start)
                val distance = gap.length
                if (distance < nearest) nearest = distance
                if (distance <= MAX_DISTANCE && isStrongGapBefore(gap)) strong = true
            } else if (candidate.end <= keyword.first) {
                val gap = text.substring(candidate.end, keyword.first)
                val distance = gap.length
                if (distance < nearest) nearest = distance
                if (distance <= MAX_DISTANCE && isStrongGapAfter(gap)) strong = true
            }
        }
        if (nearest == Int.MAX_VALUE) {
            // Only the title said "code": a short body is the code's own message.
            if (!contextKeyword || text.length > MAX_DISTANCE) return null
            nearest = MAX_DISTANCE / 2
        }
        if (nearest > MAX_DISTANCE) return null
        val digitsOnly = value.all { it.isDigit() }
        if (digitsOnly && value.length < MIN_DIGITS) return null
        // A 9 or 10 digit run is a phone number written without spaces as often as it is a code.
        if (digitsOnly && value.length >= 9 && !strong) return null
        if (digitsOnly && value.length == 4 && looksLikeYear(value) && !strong) return null
        var score = 100 - nearest
        if (strong) score += 40
        score += when {
            !digitsOnly -> -5
            value.length == 6 -> 12
            value.length in 4..8 -> 6
            else -> 0
        }
        if (candidate.kind == Kind.ALPHANUMERIC && !strong) score -= 15
        return Scored(candidate, score)
    }

    private fun looksLikeYear(value: String): Boolean = value.toInt() in 1900..2099

    /** Words that may sit between the word and the code and still say "this is it": "code is 123456", "código es 1234". */
    private val COPULAS = setOf(
        "is", "was", "are", "est", "es", "ist", "lautet", "é", "è", "är", "er", "je", "jest", "là", "adalah", "ialah", "是", "为", "は", "는", "은",
        "de",
    )

    /** Between the word and a code after it: nothing but punctuation, or one linking word. */
    private fun isStrongGapBefore(gap: String): Boolean {
        val words = gap.split(NON_WORD).filter { it.isNotEmpty() }
        return words.isEmpty() || (words.size == 1 && words[0].lowercase() in COPULAS)
    }

    /** Between a code and the word after it: "123456 is your code", "123456 — your code", "123456 ist dein Code". */
    private fun isStrongGapAfter(gap: String): Boolean {
        val words = gap.split(NON_WORD).filter { it.isNotEmpty() }
        return words.isEmpty() || (words.size <= 4 && words[0].lowercase() in COPULAS)
    }

    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")

    /**
     * Everything a code is never taken from, as a mask over [text]. Ordered loosely from the
     * most to the least certain; overlapping spans simply both block.
     */
    private fun blockedSpans(text: String): BooleanArray {
        val blocked = BooleanArray(text.length)
        for (pattern in BLOCKS) {
            pattern.regex.findAll(text).forEach { m ->
                val range = if (pattern.group > 0) m.groups[pattern.group]?.range else m.range
                range?.forEach { blocked[it] = true }
            }
        }
        return blocked
    }

    private class Block(val regex: Regex, val group: Int = 0)

    private val CURRENCY_SIGNS = """[$€£¥₹₩₽₺₫₱฿₪]"""
    /** Currency codes and words. Codes that are also everyday words ("try", "pen", "cop") are left out. */
    private const val CURRENCY_WORDS =
        "usd|eur|gbp|inr|jpy|cny|rmb|chf|cad|aud|nzd|mxn|brl|ars|clp|zar|sek|nok|dkk|pln|czk|huf|rub|uah|aed|sar|sgd|hkd|myr|idr|thb|vnd|krw|ngn|kes|egp|rs|rp|kr|zł|zl|dollars?|euros?|pounds?|rupees?|reais|pesos?"

    /** An amount's number: thousands kept together by a comma, a dot or a no-break space, then an optional decimal part. */
    private const val AMOUNT = """\d{1,3}(?:[,.\u00A0\u202F]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?"""

    private val BLOCKS: List<Block> = listOf(
        // Links and e-mail addresses.
        Block(Regex("""(?i)\b(?:https?://|www\.)\S+""")),
        Block(Regex("""(?i)\b[\w-]+(?:\.[\w-]+)*\.(?:com|net|org|io|co|me|ly|gl|app|dev|info|biz|uk|de|fr|es|it|nl|br|in|ru|cn|jp)(?:/\S*)?""")),
        Block(Regex("""\S+@\S+\.\S+""")),
        // IP addresses.
        Block(Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")),
        // Phone numbers: an international number, a North American one, four or more groups.
        Block(Regex("""\+\s?\d[\d\s().\-]{5,}\d""")),
        Block(Regex("""(?<!\d)(?:1[\s.\-])?\(?\d{3}\)?[\s.\-]\d{3}[\s.\-]\d{4}(?!\d)""")),
        Block(Regex("""(?<!\d)\(\d{2,5}\)\s?\d[\d\s\-]{4,}\d""")),
        Block(Regex("""(?<![\d\-])\d{2,5}(?:[\s.\-]\d{2,5}){3,}(?![\d\-])""")),
        // A number someone is told to call, text or reply to.
        Block(Regex("""(?i)\b(?:call|phone|tel|telephone|mobile|dial|text|reply|sms to|contact|fax|hotline|llama|llame|appelez|anrufen|ligue)\b\D{0,20}?(\+?\d[\d\s().\-]*\d)"""), group = 1),
        // Dates and times.
        Block(Regex("""(?<!\d)\d{1,4}[/.\-]\d{1,2}[/.\-]\d{1,4}(?!\d)""")),
        Block(Regex("""(?<![\d/])\d{1,2}/\d{1,4}(?![\d/])""")),
        Block(Regex("""(?<!\d)\d{1,2}[:h]\d{2}(?::\d{2})?(?!\d)""")),
        Block(Regex("""(?i)(?<!\d)\d{1,2}(?:st|nd|rd|th)?\s(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?(?:\s\d{2,4})?""")),
        Block(Regex("""(?i)\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?\s\d{1,2}(?:st|nd|rd|th)?(?:,?\s\d{4})?""")),
        // Amounts: a currency before or after, a decimal part, thousands separators, a percentage.
        Block(Regex("""(?i)(?:$CURRENCY_SIGNS|\b(?:$CURRENCY_WORDS)\b\.?)\s?(?:$AMOUNT)""")),
        Block(Regex("""(?i)(?:$AMOUNT)\s?(?:$CURRENCY_SIGNS|\b(?:$CURRENCY_WORDS)\b)""")),
        Block(
            Regex(
                """(?i)\b(?:payment|paid|pay|debited|credited|debit|credit|balance|bal|amount|amt|total|transfer|transferred|spent|charged|purchase|withdrawal|withdrawn|deposit|deposited|bill|refund|price|cost|fee|limit|importe|montant|betrag|valor|saldo)\b(?:\s+(?:of|for|de|von|di|du|da|is|was))?[\s:=]*($AMOUNT)""",
            ),
            group = 1,
        ),
        Block(Regex("""(?<![\d.,])\d{1,3}(?:[,.]\d{3})+(?:[.,]\d{1,2})?(?![\d.,]*\d)""")),
        Block(Regex("""(?<![\d.,])\d+[.,]\d{1,2}(?![\d.,])""")),
        Block(Regex("""\d+(?:[.,]\d+)?\s?%""")),
        // Masked card and account numbers: "****1234", "XX1234", "ending in 1234".
        Block(Regex("""[*•xX]{2,}[\s\-]?\d{2,6}""")),
        Block(Regex("""(?i)\b(?:ending(?:\s+(?:in|with))?|ends\s+(?:in|with))\s*[:#]?\s*\d+""")),
        // Numbers named as something other than a code.
        Block(
            Regex(
                """(?i)\b(?:order|orders|invoice|tracking|track|booking|reservation|ticket|ref|reference|account|acct|a/c|card|transaction|txn|receipt|case|customer|member|membership|policy|shipment|parcel|awb|flight|seat|gate|room|table|pedido|commande|facture|bestellung|rechnung|kundennummer|sendung|zamówienie|заказ|zip|postal|postcode|plz|cep|pincode|serial|model|version|ver|build|imei|iban|swift|sort code|routing|batch|lot|item|sku|vin)\b\.?\s*(?:no\.?|nr\.?|number|num|id|#)?\s*[:#]?\s*([A-Za-z0-9][A-Za-z0-9\-/]{2,})""",
            ),
            group = 1,
        ),
        Block(Regex("""#\s?[A-Za-z0-9\-]+""")),
        // A street address: "1600 Amphitheatre Parkway", "221B Baker Street".
        Block(Regex("""(?i)\b\d{1,5}[A-Z]?\s+(?:[A-Z][a-z]+\s+){0,3}(?:street|st\.|avenue|ave\.?|road|rd\.|boulevard|blvd|lane|ln\.|drive|dr\.|way|parkway|pkwy|suite|ste\.?)(?![a-z])""")),
        // Quantities with a unit.
        Block(Regex("""(?i)(?<![\p{L}\p{N}])\d+\s?(?:gb|mb|kb|tb|mins?|minutes?|hours?|hrs?|days?|seconds?|secs?|km|kg|mg|ml|mi|miles?|points?|pts|steps|calories|kcal|items?|units?|pcs|x)(?![\p{L}])""")),
    )

    /** The words that mark a code, and the words that make one of them mean something else. */
    internal object Keywords {

        /**
         * Matched case-insensitively. Most must start and end on a word boundary; one ending in
         * `*` is a stem and may run on ("verif*" is verification, verifica, verifizierung...).
         * Words in scripts written without spaces (Chinese, Japanese, Korean) match anywhere.
         */
        private val WORDS: List<String> = listOf(
            // English
            "code", "codes", "otp", "otps", "one-time", "one time", "onetime", "passcode", "passcodes", "password", "pass code",
            "verif*", "authenticat*", "auth", "2fa", "mfa", "two-factor", "two factor", "2-step", "two-step", "2-factor",
            "pin", "confirm*", "login", "log in", "log-in", "sign in", "sign-in", "signin", "security key",
            // Spanish, Portuguese, Catalan, Galician
            "código", "codigo", "códigos", "clave", "contraseña", "senha", "verificación", "verificação", "acceso",
            // French
            "mot de passe", "usage unique", "vérif*",
            // German, Dutch, Scandinavian
            "bestätigung*", "sicherheitscode", "einmal*", "anmeldecode", "zugangscode", "freigabecode", "aktivierungscode", "registrierungscode", "tan", "mtan", "smstan", "pushtan", "passwort", "inlogcode",
            "verificatiecode", "beveiligingscode", "engångskod", "engangskode", "engangskod", "kod*", "bekræft*",
            // Italian
            "codice", "codici",
            // Polish, Czech, Slovak, Hungarian, Romanian
            "hasło", "haslo", "kód", "kódu", "ověřovací", "jelszó", "parola",
            // Turkish, Indonesian, Malay, Vietnamese, Tagalog
            "doğrulama", "şifre", "sifre", "mã", "mật khẩu", "xác thực", "xác minh",
            // Russian, Ukrainian, Bulgarian, Serbian, Greek, Hebrew, Arabic, Persian, Hindi
            "код*", "пароль", "підтвердж*", "подтвержд*", "κωδικ*", "קוד", "סיסמה", "رمز", "الرمز", "كود", "كلمة المرور", "کد",
            "कोड", "ओटीपी", "सत्यापन",
            // Chinese, Japanese, Korean
            "验证码", "驗證碼", "校验码", "校驗碼", "动态码", "動態碼", "动态密码", "動態密碼", "确认码", "確認碼", "认证码", "認證碼", "密码", "密碼",
            "認証コード", "確認コード", "認証番号", "確認番号", "ワンタイムパスワード", "パスコード", "セキュリティコード", "コード",
            "인증번호", "인증 번호", "인증코드", "인증 코드", "확인코드", "확인 코드", "보안코드", "승인번호", "비밀번호",
        )

        /** The word right before "code" that makes it some other code: "promo code", "zip code", "country code". */
        private val NOT_A_CODE_BEFORE: Set<String> = setOf(
            "promo", "promotion", "promotional", "coupon", "discount", "voucher", "gift", "referral", "invite", "invitation", "redeem",
            "zip", "postal", "post", "area", "country", "dial", "dialing", "dialling", "sort", "swift", "bic", "iban", "bank", "branch",
            "tracking", "product", "item", "error", "status", "reason", "qr", "bar", "barcode", "source", "colour", "color", "dress",
            "tax", "vat", "airport", "flight", "booking", "reservation", "offer", "sale", "savings", "access-point", "wifi", "wi-fi",
            "descuento", "cupón", "cupon", "promocional", "réduction", "promotionnel", "rabatt", "gutschein", "rabattcode",
            "sconto", "zniżkowy", "rabatowy", "промокод",
        )

        /** "Promo code" said after the word, as Spanish and French do ("código promocional", "code promo"), and "code review" and its kind. */
        private val NOT_A_CODE_AFTER: Set<String> = setOf(
            "promo", "promocional", "promotionnel", "de réduction", "de descuento", "postal", "sconto", "rabatt", "regalo", "cadeau",
            "review", "reviews", "of conduct", "snippet", "block", "editor", "base", "freeze", "name", "names",
        )

        private class Pattern(val text: String, val stem: Boolean, val bounded: Boolean)

        private val PATTERNS: List<Pattern> = WORDS.map { word ->
            val stem = word.endsWith("*")
            val text = word.removeSuffix("*").lowercase()
            Pattern(text, stem, bounded = text.all { it.code < 0x1100 })
        }

        /** Where the marking words are in [text]; empty when there are none. */
        fun find(text: String): List<IntRange> {
            val lower = text.lowercase()
            // lowercase() can change length for a handful of characters; fall back to the
            // original when it does, so positions still line up (only case-folding is lost).
            val haystack = if (lower.length == text.length) lower else text
            val found = ArrayList<IntRange>()
            for (pattern in PATTERNS) {
                var from = 0
                while (true) {
                    val at = haystack.indexOf(pattern.text, from)
                    if (at < 0) break
                    from = at + 1
                    var end = at + pattern.text.length
                    if (pattern.bounded) {
                        if (at > 0 && haystack[at - 1].isLetterOrDigit()) continue
                        if (pattern.stem) {
                            while (end < haystack.length && haystack[end].isLetter()) end++
                        } else if (end < haystack.length && haystack[end].isLetterOrDigit()) {
                            continue
                        }
                    }
                    if (isOtherKindOfCode(haystack, at, end)) continue
                    found.add(at until end)
                }
            }
            return found
        }

        private fun isOtherKindOfCode(text: String, start: Int, end: Int): Boolean {
            val before = text.substring(0, start).trimEnd().split(NON_WORD_KEEP_HYPHEN).lastOrNull { it.isNotEmpty() }
            if (before != null && before in NOT_A_CODE_BEFORE) return true
            val after = text.substring(end).trimStart()
            return NOT_A_CODE_AFTER.any { after.startsWith(it) && (after.length == it.length || !after[it.length].isLetter()) }
        }

        private val NON_WORD_KEEP_HYPHEN = Regex("""[^\p{L}\p{N}\-]+""")
    }
}
