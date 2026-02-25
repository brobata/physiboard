package it.palsoftware.pastiera.inputmethod.telex

import java.text.Normalizer

internal object VietnameseTelexProcessor {
    const val VIETNAMESE_TELEX_LAYOUT_ID = "vietnamese_telex_qwerty"

    data class Rewrite(
        val replaceCount: Int,
        val replacement: String
    )

    private val toneByKey = mapOf(
        's' to '\u0301', // acute
        'f' to '\u0300', // grave
        'r' to '\u0309', // hook above
        'x' to '\u0303', // tilde
        'j' to '\u0323', // dot below
    )

    private val toneKeys = toneByKey.keys + 'z'
    private val shapeKeys = setOf('a', 'd', 'e', 'o', 'u', 'w')

    private const val BREVE = '\u0306'
    private const val CIRCUMFLEX = '\u0302'
    private const val HORN = '\u031B'

    private val toneMarks = setOf('\u0301', '\u0300', '\u0309', '\u0303', '\u0323')

    fun isActiveForLayout(layoutName: String?): Boolean = layoutName == VIETNAMESE_TELEX_LAYOUT_ID

    fun rewrite(textBeforeCursor: String, keyChar: Char): Rewrite? {
        val lowerKey = keyChar.lowercaseChar()
        if (lowerKey !in toneKeys && lowerKey !in shapeKeys) return null

        val syllableStart = findSyllableStart(textBeforeCursor)
        if (syllableStart == textBeforeCursor.length) return null

        val syllable = textBeforeCursor.substring(syllableStart)
        val rewritten = when {
            lowerKey == 'z' -> clearDiacritics(syllable)
            lowerKey in toneByKey -> applyToneKey(syllable, keyChar)
            else -> applyShapeKey(syllable, keyChar)
        } ?: return null

        if (rewritten == syllable) return null
        return Rewrite(replaceCount = syllable.length, replacement = rewritten)
    }

    private fun findSyllableStart(text: String): Int {
        var i = text.length
        while (i > 0 && text[i - 1].isLetter()) i--
        return i
    }

    private fun clearDiacritics(syllable: String): String? {
        var changed = false
        val out = buildString {
            for (ch in syllable) {
                val parts = Parts.fromChar(ch)
                val cleared = parts.clearDiacritics()
                if (cleared != ch) changed = true
                append(cleared)
            }
        }
        return if (changed) out else null
    }

    private fun applyShapeKey(syllable: String, keyChar: Char): String? {
        val key = keyChar.lowercaseChar()
        val chars = syllable.toMutableList()

        if (key == 'w') {
            val clusterRewrite = applyUoWCluster(chars)
            if (clusterRewrite != null) return clusterRewrite
        }
        for (idx in chars.indices.reversed()) {
            if (!chars[idx].isLetter()) continue
            val parts = Parts.fromChar(chars[idx])
            val replacement = when (key) {
                'a' -> when {
                    parts.isBase('a') && !parts.hasShape() -> parts.withShape(CIRCUMFLEX).toChar().toString()
                    parts.isBase('a') && parts.hasShape(CIRCUMFLEX) -> "${caseOf(parts.base)}${keyChar.lowercaseChar()}"
                    else -> null
                }
                'e' -> when {
                    parts.isBase('e') && !parts.hasShape() -> parts.withShape(CIRCUMFLEX).toChar().toString()
                    parts.isBase('e') && parts.hasShape(CIRCUMFLEX) -> "${caseOf(parts.base)}${keyChar.lowercaseChar()}"
                    else -> null
                }
                'o' -> when {
                    parts.isBase('o') && !parts.hasShape() -> parts.withShape(CIRCUMFLEX).toChar().toString()
                    parts.isBase('o') && parts.hasShape(CIRCUMFLEX) -> "${caseOf(parts.base)}${keyChar.lowercaseChar()}"
                    else -> null
                }
                'd' -> when (chars[idx]) {
                    'd' -> "đ"
                    'D' -> "Đ"
                    // For an already transformed đ/Đ, treat a new d/D as a literal append
                    // instead of toggling back, so users can continue typing the next letter.
                    'đ', 'Đ' -> return syllable + keyChar
                    else -> null
                }
                'w' -> when {
                    parts.isBase('a') && !parts.hasShape() -> parts.withShape(BREVE).toChar().toString()
                    parts.isBase('o') && !parts.hasShape() -> parts.withShape(HORN).toChar().toString()
                    parts.isBase('u') && !parts.hasShape() -> parts.withShape(HORN).toChar().toString()
                    parts.isBase('a') && parts.hasShape(BREVE) -> "${caseOf(parts.base)}w"
                    parts.isBase('o') && parts.hasShape(HORN) -> "${caseOf(parts.base)}w"
                    parts.isBase('u') && parts.hasShape(HORN) -> "${caseOf(parts.base)}w"
                    else -> null
                }
                else -> null
            }
            if (replacement != null) {
                return syllable.substring(0, idx) + replacement + syllable.substring(idx + 1)
            }
        }
        return null
    }

    private fun applyUoWCluster(chars: List<Char>): String? {
        if (chars.size < 2) return null
        val last = Parts.fromChar(chars[chars.lastIndex])
        val prev = Parts.fromChar(chars[chars.lastIndex - 1])
        if (!last.isBase('o') || last.hasShape()) return null
        if (!prev.isBase('u') || prev.hasShape()) return null

        val newPrev = prev.withShape(HORN).toChar()
        val newLast = last.withShape(HORN).toChar()
        return buildString(chars.size) {
            append(chars.subList(0, chars.lastIndex - 1).joinToString(""))
            append(newPrev)
            append(newLast)
        }
    }

    private fun applyToneKey(syllable: String, keyChar: Char): String? {
        val key = keyChar.lowercaseChar()
        val toneMark = toneByKey[key] ?: return null
        val chars = syllable.toMutableList()
        val targetIndex = findToneTargetIndex(chars) ?: return null
        val parts = Parts.fromChar(chars[targetIndex])

        return if (parts.tone == toneMark) {
            val cleared = parts.withTone(null).toChar()
            syllable.substring(0, targetIndex) + cleared + syllable.substring(targetIndex + 1) + keyChar
        } else {
            val toned = parts.withTone(toneMark).toChar()
            syllable.substring(0, targetIndex) + toned + syllable.substring(targetIndex + 1)
        }
    }

    private fun findToneTargetIndex(chars: List<Char>): Int? {
        val vowelIndices = chars.indices.filter { Parts.fromChar(chars[it]).isVietnameseVowel() }
        if (vowelIndices.isEmpty()) return null
        if (vowelIndices.size == 1) return vowelIndices.first()

        // Common-case heuristics for Vietnamese TELEX.
        val lastIdx = vowelIndices.last()
        val last = Parts.fromChar(chars[lastIdx])
        val prevIdx = vowelIndices.getOrNull(vowelIndices.size - 2)
        val prev = prevIdx?.let { Parts.fromChar(chars[it]) }

        // Common "ươ" cluster -> tone on ơ.
        if (prev != null && prev.isBase('u') && prev.hasShape(HORN) && last.isBase('o') && last.hasShape(HORN)) {
            return lastIdx
        }

        // If the final vowel is a semivowel, prefer the previous vowel.
        if (prevIdx != null && last.base.lowercaseChar() in setOf('i', 'y', 'u')) {
            // Exception: "uy" usually carries tone on y.
            if (!(prev!!.base.lowercaseChar() == 'u' && last.base.lowercaseChar() == 'y')) {
                return prevIdx
            }
        }

        // "oa"/"oe" commonly take tone on 'o'.
        if (prevIdx != null && prev!!.base.lowercaseChar() == 'o' && last.base.lowercaseChar() in setOf('a', 'e')) {
            return prevIdx
        }

        return lastIdx
    }

    private fun caseOf(base: Char): String = if (base.isUpperCase()) base.toString() else base.lowercaseChar().toString()

    private data class Parts(
        val base: Char,
        val shape: Char? = null,
        val tone: Char? = null,
        val dStroke: Boolean = false
    ) {
        fun isBase(ch: Char): Boolean = !dStroke && base.lowercaseChar() == ch
        fun hasShape(): Boolean = shape != null
        fun hasShape(mark: Char): Boolean = shape == mark

        fun isVietnameseVowel(): Boolean {
            if (dStroke) return false
            return base.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u', 'y')
        }

        fun withShape(newShape: Char?): Parts = copy(shape = newShape)
        fun withTone(newTone: Char?): Parts = copy(tone = newTone)

        fun clearDiacritics(): Char {
            return when {
                dStroke && base == 'd' -> 'd'
                dStroke && base == 'D' -> 'D'
                else -> copy(shape = null, tone = null).toChar()
            }
        }

        fun toChar(): Char {
            if (dStroke) return if (base.isUpperCase()) 'Đ' else 'đ'
            val sb = StringBuilder().append(base)
            shape?.let { sb.append(it) }
            tone?.let { sb.append(it) }
            return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC).first()
        }

        companion object {
            fun fromChar(ch: Char): Parts {
                if (ch == 'đ' || ch == 'Đ') {
                    return Parts(base = if (ch == 'Đ') 'D' else 'd', dStroke = true)
                }
                val nfd = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
                val base = nfd.firstOrNull() ?: ch
                var shape: Char? = null
                var tone: Char? = null
                for (m in nfd.drop(1)) {
                    when {
                        m in toneMarks -> tone = m
                        m == BREVE || m == CIRCUMFLEX || m == HORN -> shape = m
                    }
                }
                return Parts(base = base, shape = shape, tone = tone)
            }
        }
    }
}
