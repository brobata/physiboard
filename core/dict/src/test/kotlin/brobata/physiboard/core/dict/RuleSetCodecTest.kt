package brobata.physiboard.core.dict

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * [RuleSetCodec] parsing (autocorrect-suggestions.md SS8.1's JSON shape), plus an exercise of the
 * real shipped `auto_corrections_en.json` (docs/spec/autocorrect-suggestions.md SS8.1's English
 * row) the same way [EnglishWordListAssetTest] exercises the bundled dictionary: against the
 * actual asset file, not a hand-picked fixture, so a change to the shipped file that breaks
 * parsing or drops a rule shows up here.
 */
class RuleSetCodecTest {

    @Test
    fun `parses triggers and replacements, and pulls __name out as the display name`() {
        val parsed = assertNotNull(RuleSetCodec.parse("x-test", """{"__name":"Test set","teh":"the","adn":"and"}"""))
        assertEquals("x-test", parsed.code)
        assertEquals("Test set", parsed.displayName)
        assertEquals(mapOf("teh" to "the", "adn" to "and"), parsed.rules)
    }

    @Test
    fun `a blank __name is treated as no display name`() {
        val parsed = assertNotNull(RuleSetCodec.parse("x-test", """{"__name":"","teh":"the"}"""))
        assertEquals(null, parsed.displayName)
    }

    @Test
    fun `a set with no __name at all has a null display name`() {
        val parsed = assertNotNull(RuleSetCodec.parse("en", """{"teh":"the"}"""))
        assertEquals(null, parsed.displayName)
    }

    @Test
    fun `a non-string value under an ordinary key is skipped, not a parse failure`() {
        val parsed = assertNotNull(RuleSetCodec.parse("x-test", """{"teh":"the","bad":42,"good":"fine"}"""))
        assertEquals(mapOf("teh" to "the", "good" to "fine"), parsed.rules)
    }

    @Test
    fun `text that is not a JSON object fails to parse`() {
        assertNull(RuleSetCodec.parse("en", "not json"))
        assertNull(RuleSetCodec.parse("en", "[]"))
        assertNull(RuleSetCodec.parse("en", ""))
    }

    // --- the shipped en.json ---

    private val shippedEnglish: RuleSet? by lazy {
        shippedAssetBody()?.let { RuleSetCodec.parse("en", it) }
    }

    @Test
    fun `T-the shipped English rule set parses and has the pronoun and apostrophe-drop rules`() {
        val ruleSet = assertNotNull(
            shippedEnglish,
            "ime/src/main/assets/common/autocorrect/auto_corrections_en.json is missing or failed to parse",
        )
        assertEquals("en", ruleSet.code)
        // spec: autocorrect-suggestions.md SS8.1's `en` row, the safe subset this clean-room
        // author chose: `i`/`im`/`ive` but not `ill` or `id` (both collide with real words).
        assertEquals("I", ruleSet.rules["i"])
        assertEquals("I'm", ruleSet.rules["im"])
        assertEquals("I've", ruleSet.rules["ive"])
        assertEquals(null, ruleSet.rules["ill"])
        assertEquals(null, ruleSet.rules["id"])
        assertEquals("it's", ruleSet.rules["its"])
        assertEquals("don't", ruleSet.rules["dont"])
        assertEquals("the", ruleSet.rules["teh"])
        assertEquals("a lot", ruleSet.rules["alot"])
    }

    @Test
    fun `T-the shipped rule set turns 'i ' into 'I ' through SubstitutionMatcher`() {
        val ruleSet = assertNotNull(shippedEnglish)
        val match = assertNotNull(SubstitutionMatcher.match("i ", listOf(ruleSet), isKnownWord = { false }))
        assertEquals("I", match.replacement)
    }

    @Test
    fun `T-the shipped rule set turns 'dont ' into 'don't ' through SubstitutionMatcher`() {
        val ruleSet = assertNotNull(shippedEnglish)
        val match = assertNotNull(SubstitutionMatcher.match("dont ", listOf(ruleSet), isKnownWord = { false }))
        assertEquals("don't", match.replacement)
    }

    @Test
    fun `T-its becomes it's even when its reads as a known word, by the accent-case-punctuation guard`() {
        val ruleSet = assertNotNull(shippedEnglish)
        // spec: SS8.3 step 4's guard: a match applies when the word is not known, OR the
        // replacement differs from it only in accent, case or punctuation. "its" -> "it's"
        // differs only by an apostrophe, so it applies even when "its" itself reads as known.
        val match = assertNotNull(SubstitutionMatcher.match("its ", listOf(ruleSet), isKnownWord = { true }))
        assertEquals("it's", match.replacement)
    }

    private fun shippedAssetBody(): String? = shippedAssetBody("en")

    private fun shippedAssetBody(code: String): String? = repoRoot()
        ?.resolve("ime/src/main/assets/common/autocorrect/auto_corrections_$code.json")
        ?.takeIf { it.isFile }
        ?.readText()

    /** Walks upward from the working directory to find the repo root (marked by settings.gradle.kts). */
    private fun repoRoot(): File? {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        return null
    }

    // --- the shipped it.json and fr.json (autocorrect-suggestions.md SS8.1, SS19 gap) ---

    private val shippedItalian: RuleSet? by lazy { shippedAssetBody("it")?.let { RuleSetCodec.parse("it", it) } }
    private val shippedFrench: RuleSet? by lazy { shippedAssetBody("fr")?.let { RuleSetCodec.parse("fr", it) } }

    @Test
    fun `T-the shipped Italian rule set parses and has the two-word elisions and accent repairs`() {
        val ruleSet = assertNotNull(
            shippedItalian,
            "ime/src/main/assets/common/autocorrect/auto_corrections_it.json is missing or failed to parse",
        )
        assertEquals("it", ruleSet.code)
        // spec: SS8.1's `it` row, minus `forza juve -> forza napoli` (SS18: "Drop, upstream joke rule").
        assertEquals("cos'è", ruleSet.rules["cos e"])
        assertEquals("dov'è", ruleSet.rules["dov e"])
        assertEquals("chi è", ruleSet.rules["chi e"])
        assertEquals("c'ho", ruleSet.rules["c ho"])
        assertEquals("qual è", ruleSet.rules["qual'è"])
        assertEquals("perché", ruleSet.rules["perche"])
        assertEquals("perché", ruleSet.rules["perchè"])
        assertEquals("così", ruleSet.rules["cosi"])
        assertEquals("già", ruleSet.rules["gia"])
        assertEquals("più", ruleSet.rules["piu"])
        assertEquals(null, ruleSet.rules["forza juve"])
    }

    @Test
    fun `T-the shipped Italian set turns 'perche ' into 'perché ' through SubstitutionMatcher`() {
        val ruleSet = assertNotNull(shippedItalian)
        val match = assertNotNull(SubstitutionMatcher.match("perche ", listOf(ruleSet), isKnownWord = { false }))
        assertEquals("perché", match.replacement)
    }

    @Test
    fun `T-the shipped Italian set turns 'cos e ' into cos'è through the two-word sequence path`() {
        val ruleSet = assertNotNull(shippedItalian)
        val match = assertNotNull(SubstitutionMatcher.match("cos e ", listOf(ruleSet), isKnownWord = { false }))
        assertEquals("cos'è", match.replacement)
    }

    @Test
    fun `T-the shipped French rule set parses and has apostrophe and accent repairs`() {
        val ruleSet = assertNotNull(
            shippedFrench,
            "ime/src/main/assets/common/autocorrect/auto_corrections_fr.json is missing or failed to parse",
        )
        assertEquals("fr", ruleSet.code)
        // spec: SS8.1's `fr` row examples, quoted verbatim: `etre -> être`, `jai -> j'ai`,
        // `coeur -> cœur` (not authored: no safe unambiguous typo owns that spelling; see the
        // ligature-free entries actually shipped), `ct -> c'était` (not shipped: too short and
        // ambiguous with the abbreviation "ct"), and SS9's T30 pairs.
        assertEquals("être", ruleSet.rules["etre"])
        assertEquals("j'ai", ruleSet.rules["jai"])
        assertEquals("j'espère", ruleSet.rules["jespere"])
        assertEquals("c'est-à-dire", ruleSet.rules["cestadire"])
        assertEquals("lui-même", ruleSet.rules["luimeme"])
        assertEquals("l'eau", ruleSet.rules["leau"])
        assertEquals("Noël", ruleSet.rules["noel"])
        assertEquals("ça", ruleSet.rules["ca"])
        // Deliberately absent: common valid words that would collide (SS10, "never overwrite a
        // correctly spelled word"). `la` ("the"/"her") is not turned into `là` ("there"), and `a`
        // (verb "has") is not turned into `à` (preposition): both pairs are genuinely ambiguous,
        // unlike `ca -> ça` which SS8.1's own T27 sanctions.
        assertEquals(null, ruleSet.rules["la"])
        assertEquals(null, ruleSet.rules["a"])
        assertEquals(null, ruleSet.rules["ou"])
    }

    @Test
    fun `T-the shipped French set turns 'Ca ' into 'Ça ' through SubstitutionMatcher, casing from the trigger`() {
        val ruleSet = assertNotNull(shippedFrench)
        val match = assertNotNull(SubstitutionMatcher.match("Ca ", listOf(ruleSet), isKnownWord = { true }))
        assertEquals("Ça", match.replacement)
    }

    @Test
    fun `T-the shipped French set turns 'jespere ' into j'espère`() {
        val ruleSet = assertNotNull(shippedFrench)
        val match = assertNotNull(SubstitutionMatcher.match("jespere ", listOf(ruleSet), isKnownWord = { false }))
        assertEquals("j'espère", match.replacement)
    }
}
