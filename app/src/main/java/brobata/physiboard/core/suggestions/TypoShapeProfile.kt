package brobata.physiboard.core.suggestions

/**
 * How much a correction is allowed to change a word's length, per language.
 *
 * The shape gate used to accept only same-length substitutions and doubled-letter insertions.
 * That refused the most common typo there is — a dropped or added letter — so `definetly`
 * stayed wrong while `Oliva` → `Olive` was fixed, because the latter happens to be the same
 * length. From the user's chair those two outcomes are indistinguishable from random. It also
 * made *Maximum correction distance* a false promise: a distance-2 edit almost always changes
 * length, and length changes were refused whatever that setting said.
 *
 * The right answer differs by language, and in opposite directions:
 *
 *  - English and the Romance languages: a dropped or added letter is the dominant typo, so
 *    refusing every length change is what makes autocorrect feel broken.
 *  - Turkish, Hungarian, Finnish: suffix growth is ordinary morphology. Allowing length changes
 *    there would make the keyboard aggressively wrong rather than merely unhelpful.
 *  - German: `ue` → `ü` and `ss` → `ß` are length-changing orthographic fixes.
 *
 * So this is a per-language dial, defaulting to the old conservative behaviour. A language is
 * only opened up once its output has actually been judged — loosening data nobody on the
 * project can read is how a keyboard gets quietly worse.
 */
internal object TypoShapeProfile {

    data class Profile(
        /** Largest absolute length difference a non-orthographic correction may have. */
        val maxLengthDelta: Int
    )

    /** Today's shipped behaviour: same-length edits and doubled letters only. */
    private val CONSERVATIVE = Profile(maxLengthDelta = 0)

    /** Dropped/added letters permitted, still bounded by the user's distance setting. */
    private val LETTER_SLIPS = Profile(maxLengthDelta = 2)

    /**
     * Languages verified against real typing before being opened up.
     *
     * Everything absent from this map keeps [CONSERVATIVE]. Adding a language here is a data
     * decision, not a code one: it needs a corpus of that language's real typos and someone who
     * can tell a good correction from a bad one.
     */
    private val byLanguage: Map<String, Profile> = mapOf(
        "en" to LETTER_SLIPS
    )

    fun forLanguage(languageCode: String?): Profile =
        byLanguage[languageCode?.lowercase()?.take(2)] ?: CONSERVATIVE

    /**
     * True when one word is simply the other with something added to the front or back.
     *
     * This is the discriminator that makes length changes safe to allow at all. `work` → `works`
     * and `behavio` → `behavior` are morphology or completion — the user is mid-word or the
     * dictionary is offering a longer form, and replacing is wrong. `definetly` → `definitely`
     * changes a letter inside the word, which no amount of typing more would have produced.
     */
    fun isPureAffixChange(input: String, candidate: String): Boolean {
        val a = input.lowercase()
        val b = candidate.lowercase()
        if (a == b) return false
        return a.startsWith(b) || b.startsWith(a) || a.endsWith(b) || b.endsWith(a)
    }
}
