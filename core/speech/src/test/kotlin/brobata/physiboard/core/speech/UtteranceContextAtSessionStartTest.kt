package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec SS7.7: when the previous session's words still count as this session's own at its start. */
class UtteranceContextAtSessionStartTest {

    private val left = UtteranceContext("Hi.\nThat is great. ", writtenBySession = "That is great. ")

    @Test
    fun `the field ending as the last session left it carries that session's words`() {
        assertEquals(UtteranceContext("Hi.\nThat is great. ", "That is great. "), UtteranceContext.atSessionStart("Hi.\nThat is great. ", left))
    }

    @Test
    fun `a shorter read than the context still matches on what both hold`() {
        assertEquals(UtteranceContext("That is great. ", "That is great. "), UtteranceContext.atSessionStart("That is great. ", left))
        assertEquals(UtteranceContext("is great. "), UtteranceContext.atSessionStart("is great. ", left), "a read too short to hold the session's words carries nothing")
    }

    @Test
    fun `a field that changed, a web field that answers empty, or nothing left behind carries nothing`() {
        assertEquals(UtteranceContext("That is great. Ok. "), UtteranceContext.atSessionStart("That is great. Ok. ", left))
        assertEquals(UtteranceContext(""), UtteranceContext.atSessionStart("", left))
        assertEquals(UtteranceContext(null), UtteranceContext.atSessionStart(null, left))
        assertEquals(UtteranceContext("That is great. "), UtteranceContext.atSessionStart("That is great. ", null))
        assertEquals(UtteranceContext("That is great. "), UtteranceContext.atSessionStart("That is great. ", UtteranceContext("That is great. ")))
    }

    @Test
    fun `the spoken-mark flag travels with the words`() {
        val wow = UtteranceContext("Wow! ", writtenBySession = "Wow! ", sessionTailIsSpokenMark = true)
        assertEquals(wow, UtteranceContext.atSessionStart("Wow! ", wow))
    }
}
