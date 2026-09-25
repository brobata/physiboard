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

    private fun shippedAssetBody(): String? = repoRoot()
        ?.resolve("ime/src/main/assets/common/autocorrect/auto_corrections_en.json")
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
}
