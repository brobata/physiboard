package it.palsoftware.pastiera.inputmethod.telex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VietnameseTelexProcessorTest {

    @Test
    fun `shape keys convert base vowels`() {
        assertRewrite("ca", 'a', "câ")
        assertRewrite("trang", 'w', "trăng")
        assertRewrite("de", 'e', "dê")
        assertRewrite("mo", 'w', "mơ")
        assertRewrite("tu", 'w', "tư")
        assertRewrite("d", 'd', "đ")
        assertRewrite("đ", 'd', "đd")
    }

    @Test
    fun `uow creates uo horn cluster`() {
        assertRewrite("tuo", 'w', "tươ")
    }

    @Test
    fun `dau plus a becomes dau with circumflex`() {
        assertRewrite("đau", 'a', "đâu")
    }

    @Test
    fun `tone keys apply and replace last tone`() {
        assertRewrite("ta", 's', "tá")
        assertRewrite("tá", 'f', "tà")
    }

    @Test
    fun `z clears diacritics in syllable`() {
        assertRewrite("tưở", 'z', "tuo")
    }

    @Test
    fun `repeating tone key emits literal key`() {
        assertRewrite("hẻ", 'r', "her")
    }

    @Test
    fun `repeating shape key can escape transformed vowel`() {
        assertRewrite("xô", 'o', "xoo")
        assertRewrite("mơ", 'w', "mow")
    }

    @Test
    fun `non telex key returns null`() {
        assertNull(VietnameseTelexProcessor.rewrite("ta", 'k'))
    }

    @Test
    fun `layout activation is tied to layout id`() {
        assertEquals(true, VietnameseTelexProcessor.isActiveForLayout("vietnamese_telex_qwerty"))
        assertEquals(false, VietnameseTelexProcessor.isActiveForLayout("qwerty"))
    }

    private fun assertRewrite(textBeforeCursor: String, keyChar: Char, expected: String) {
        val rewrite = VietnameseTelexProcessor.rewrite(textBeforeCursor, keyChar)
        requireNotNull(rewrite) { "Expected rewrite for '$textBeforeCursor' + '$keyChar'" }
        assertEquals(textBeforeCursor.length, rewrite.replaceCount)
        assertEquals(expected, rewrite.replacement)
    }
}
