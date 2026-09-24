package brobata.physiboard.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: dictation.md SS7.1, SS7.2, SS7.5, SS7.6, SS16 T24-T31, T37-T40. */
class UtteranceTextTest {

    private val settings = DictationTextSettings()

    private fun ctx(before: String?) = UtteranceContext(before)

    @Test
    fun `T24 after-sentence capitalisation`() {
        val ctxMidSentence = ctx("random words ") // not itself a capitalise-here position
        assertEquals(
            "ciao. Come va? Bene! Ok",
            DictationCapitalization.apply("ciao. come va? bene! ok", ctxMidSentence, settings),
        )
    }

    @Test
    fun `T25 accented lowercase after a sentence end is not matched`() {
        val ctxMidSentence = ctx("random words ")
        assertEquals("ciao. èra", DictationCapitalization.apply("ciao. èra", ctxMidSentence, settings))
    }

    @Test
    fun `T26 first letter capitalised at the start of an empty field`() {
        val finished = UtteranceFinisher.finish("hello", ctx(""), settings)
        assertEquals("Hello ", finished.plainText)
    }

    @Test
    fun `T27 dictating after a word with no trailing space adds a leading space`() {
        val finished = UtteranceFinisher.finish("world", ctx("Hello"), settings)
        assertEquals(" world ", finished.plainText)
    }

    @Test
    fun `T28 a trailing space already present is not doubled`() {
        val finished = UtteranceFinisher.finish("world", ctx("Hello "), settings)
        assertEquals("world ", finished.plainText)
    }

    @Test
    fun `T29 digits never trigger a leading space`() {
        val finished = UtteranceFinisher.finish("world", ctx("5"), settings)
        assertEquals("world ", finished.plainText)
    }

    @Test
    fun `T30 capitalise after a period with a trailing space`() {
        val finished = UtteranceFinisher.finish("there", ctx("Hi. "), settings)
        assertEquals("There ", finished.plainText)
    }

    @Test
    fun `T31 no capital and no leading space right after a period with no space`() {
        val finished = UtteranceFinisher.finish("there", ctx("Hi."), settings)
        assertEquals("there ", finished.plainText)
    }

    @Test
    fun `T32 raw-mode field disables capitalisation but not spacing`() {
        val rawMode = settings.copy(capitalizationAllowed = false)
        val finished = UtteranceFinisher.finish("hello", ctx(""), rawMode)
        assertEquals("hello ", finished.plainText)
    }

    @Test
    fun `T37 a prefix continuation is the same utterance`() {
        assertTrue(SameUtteranceCheck.isSameUtterance("hello world", "hello world again"))
    }

    @Test
    fun `T38 matching first words ignoring case is the same utterance`() {
        assertTrue(SameUtteranceCheck.isSameUtterance("hello world", "HELLO there"))
    }

    @Test
    fun `T39 unrelated hypotheses are a new utterance`() {
        assertFalse(SameUtteranceCheck.isSameUtterance("hello world", "goodbye now"))
    }

    @Test
    fun `T40 an empty previous hypothesis is always the same utterance`() {
        assertTrue(SameUtteranceCheck.isSameUtterance("", "x"))
    }
}
