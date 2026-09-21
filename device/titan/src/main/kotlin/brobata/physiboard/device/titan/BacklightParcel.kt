package brobata.physiboard.device.titan

/**
 * Parses the vendor's `agui_functional_service` GET(key) parcel dump into the string it carries.
 *
 * spec: device-backlight-ring.md SS3.2 ("Reading the parcel"), D16. The dump looks like
 * `Result: Parcel(00000000 00000002 0031002d 00000000 '....-.1....')`: word 0 is the exception
 * code, word 1 the UTF-16 character count, and every word after that packs two little-endian
 * UTF-16 code units, low half first. The hex is parsed; the quoted ASCII rendering at the end is
 * never trusted, since it substitutes dots for non-printable characters and cannot be told apart
 * from real content.
 */
object BacklightParcel {

    private const val MAX_CHAR_COUNT = 64
    private val WORD = Regex("[0-9a-fA-F]{8}")

    /**
     * Returns the parsed string, or null ("unreadable") when the dump is not in the expected
     * shape: fewer than two words, a character count of 0 or above [MAX_CHAR_COUNT], or fewer
     * payload words than the count needs.
     */
    fun parse(raw: String?): String? {
        if (raw == null) return null
        val beforeQuote = raw.substringBefore('\'')
        val words = WORD.findAll(beforeQuote).map { it.value }.toList()
        if (words.size < 2) return null

        val count = words[1].toLong(16)
        if (count <= 0 || count > MAX_CHAR_COUNT) return null

        val payloadWords = words.drop(2)
        val wordsNeeded = ((count + 1) / 2).toInt()
        if (payloadWords.size < wordsNeeded) return null

        val chars = StringBuilder()
        var remaining = count
        for (word in payloadWords) {
            if (remaining <= 0) break
            val value = word.toLong(16)
            chars.append((value and 0xFFFF).toInt().toChar())
            remaining--
            if (remaining <= 0) break
            chars.append(((value shr 16) and 0xFFFF).toInt().toChar())
            remaining--
        }
        return chars.toString()
    }
}
