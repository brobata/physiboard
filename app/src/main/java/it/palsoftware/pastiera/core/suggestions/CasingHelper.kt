package it.palsoftware.pastiera.core.suggestions

import java.util.Locale

/**
 * Helper that applies the right casing to suggestions,
 * based on how the user typed the word.
 */
object CasingHelper {

    private fun capitalizeFirstLetter(candidate: String): String {
        val idx = candidate.indexOfFirst { it.isLetter() }
        if (idx < 0) return candidate
        val first = candidate[idx]
        val cap = if (first.isLowerCase()) first.titlecase(Locale.getDefault()) else first.toString()
        return candidate.substring(0, idx) + cap + candidate.substring(idx + 1)
    }

    /**
     * Cases the suggestion to match the pattern of the original word.
     * 
     * @param candidate The suggested word (e.g. "Parenzo")
     * @param original The word the user typed (e.g. "parenz", "Parenz", "PARENZ")
     * @param forceLeadingCapital When true, force an uppercase first letter (for auto-capitalize)
     * @return The word with the correct casing
     */
    fun applyCasing(
        candidate: String,
        original: String,
        forceLeadingCapital: Boolean = false
    ): String {
        if (candidate.isEmpty()) return candidate
        
        // Field demands forced capitalization: apply titlecase
        if (forceLeadingCapital) {
            return capitalizeFirstLetter(candidate)
        }
        
        if (original.isEmpty()) return candidate
        
        // Work out the casing pattern from letters alone (ignoring apostrophes and punctuation)
        val letters = original.filter { it.isLetter() }
        if (letters.isEmpty()) return candidate

        val allUpper = letters.length > 1 && letters.all { it.isUpperCase() }
        val allLower = letters.all { it.isLowerCase() }
        val firstLetter = letters.first()
        val restLetters = letters.drop(1)
        val firstUpper = firstLetter.isUpperCase()
        val restLower = restLetters.all { it.isLowerCase() }

        // Candidate has uppercase and we're not in the "allUpper" case (>=2 uppercase letters):
        // respect the dictionary's casing as-is.
        val candidateHasUpper = candidate.any { it.isUpperCase() }
        val candidateLettersUpperCount = candidate.count { it.isUpperCase() }
        if (!forceLeadingCapital && candidateHasUpper && candidateLettersUpperCount < 2) {
            return candidate
        }
        // Original all lowercase but the candidate has uppercase (e.g. "mccartney" -> "McCartney"):
        // keep the candidate's casing.
        if (allLower && candidateHasUpper) {
            return candidate
        }
        
        return when {
            // Caso: PARENZ -> PARENZO (tutto maiuscolo)
            allUpper -> candidate.uppercase(Locale.getDefault())
            // Case: Parenz -> Parenzo (leading capital, rest lowercase)
            firstUpper && restLower -> capitalizeFirstLetter(candidate)
            // Caso: parenz -> parenzo (tutto minuscolo)
            allLower -> candidate.lowercase(Locale.getDefault())
            // Everything else: use the suggestion as-is
            else -> candidate
        }
    }
}

