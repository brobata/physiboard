package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubstitutionMatcherTest {

    private val english = RuleSet(
        code = "en",
        displayName = "English",
        rules = mapOf(
            "dont" to "don't",
            "ive" to "I've",
            "i" to "I",
        ),
    )
    private val italian = RuleSet(
        code = "it",
        displayName = "Italiano",
        rules = mapOf(
            "cos e" to "cos'è",
            "perche" to "perché",
        ),
    )
    private val recipes = RuleSet(
        code = "x-pastiera",
        displayName = null,
        // "bb" and "ppp" contain no non-alphanumeric character, so per §8.3 step 1's own test
        // they are last-word triggers (step 4), not symbol triggers: they need a boundary key
        // after them, same as any other single-word rule.
        rules = mapOf("bb" to "BlackBerry", "ppp" to "%"),
    )

    private val alwaysUnknown: (String) -> Boolean = { false }
    private val alwaysKnown: (String) -> Boolean = { true }

    @Test
    fun `T-a last-word rule fires on a boundary and is cased as stored`() {
        val match = SubstitutionMatcher.match("I dont ", listOf(english), alwaysUnknown)

        assertEquals("don't", match?.replacement)
        assertEquals("dont", match?.matchedText)
    }

    @Test
    fun `T-a two-word sequence matches unconditionally, before the last-word step`() {
        val match = SubstitutionMatcher.match("cos e ", listOf(italian), alwaysUnknown)

        assertEquals("cos'è", match?.replacement)
        assertEquals("it", match?.ruleSetCode)
    }

    @Test
    fun `T-a purely alphabetic trigger needs a following boundary, unlike a symbol trigger`() {
        assertNull(SubstitutionMatcher.match("bb", listOf(recipes), alwaysUnknown))
        assertEquals("BlackBerry", SubstitutionMatcher.match("bb ", listOf(recipes), alwaysUnknown)?.replacement)
    }

    @Test
    fun `T-a symbol trigger matches at the text's own end without needing a boundary after it`() {
        val emoticon = RuleSet(code = "x-pastiera", displayName = null, rules = mapOf(":e1" to "😀"))
        val match = SubstitutionMatcher.match(":e1", listOf(emoticon), alwaysUnknown)

        assertEquals("😀", match?.replacement)
    }

    @Test
    fun `T-the longest matching symbol trigger wins`() {
        val rules = RuleSet(code = "x-pastiera", displayName = null, rules = mapOf("@@" to "short", "@@@" to "long"))
        val match = SubstitutionMatcher.match("@@@", listOf(rules), alwaysUnknown)

        assertEquals("@@@", match?.trigger)
        assertEquals("long", match?.replacement)
    }

    @Test
    fun `T-an all-uppercase trigger uppercases the whole replacement`() {
        val match = SubstitutionMatcher.match("DONT ", listOf(english), alwaysUnknown)
        assertEquals("DON'T", match?.replacement)
    }

    @Test
    fun `T-a leading capital capitalizes the replacement`() {
        val match = SubstitutionMatcher.match("Dont ", listOf(english), alwaysUnknown)
        assertEquals("Don't", match?.replacement)
    }

    @Test
    fun `T-the known-word guard blocks a last-word rule for a known word with an unrelated replacement`() {
        val unrelated = RuleSet(code = "custom", displayName = null, rules = mapOf("cat" to "dog"))
        val match = SubstitutionMatcher.match("cat ", listOf(unrelated), alwaysKnown)
        assertNull(match)
    }

    @Test
    fun `T-the known-word guard allows a rule whose replacement only differs by accent`() {
        val match = SubstitutionMatcher.match("perche ", listOf(italian), alwaysKnown)
        assertEquals("perché", match?.replacement)
    }

    @Test
    fun `T-the known-word guard allows an apostrophe-only difference such as dont to don't`() {
        // "dont" and "don't" normalize to the same key (punctuation is stripped), so this
        // class of rule still fires even for a caller whose dictionary happens to know "dont".
        val match = SubstitutionMatcher.match("dont ", listOf(english), alwaysKnown)
        assertEquals("don't", match?.replacement)
    }

    @Test
    fun `T-text with no trailing boundary character yet does not match a last-word rule`() {
        // "dont" alone, mid-word, has not reached a boundary key yet: the last-word and
        // two-word steps both require one. A symbol trigger is the one kind of rule that can
        // fire without it, since its own presence at the text's end is what fires it (see the
        // symbol-trigger tests above).
        val match = SubstitutionMatcher.match("dont", listOf(english), alwaysUnknown)
        assertNull(match)
    }

    @Test
    fun `T-a non-boundary terminator such as an emoji aborts the match entirely`() {
        val match = SubstitutionMatcher.match("dont😀", listOf(english), alwaysUnknown)
        assertNull(match)
    }

    @Test
    fun `T-rule sets are searched in the order given, first match wins`() {
        val overriding = RuleSet(code = "custom", displayName = null, rules = mapOf("dont" to "OVERRIDE"))
        val match = SubstitutionMatcher.match("dont ", listOf(overriding, english), alwaysUnknown)
        assertEquals("OVERRIDE", match?.replacement)
    }

    @Test
    fun `T-no rule set matches means no match`() {
        val match = SubstitutionMatcher.match("hello ", listOf(english, italian), alwaysUnknown)
        assertNull(match)
    }
}
