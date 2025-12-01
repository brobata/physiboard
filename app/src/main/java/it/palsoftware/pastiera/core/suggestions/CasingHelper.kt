package it.palsoftware.pastiera.core.suggestions

import java.util.Locale

/**
 * Helper per applicare la capitalizzazione corretta ai suggerimenti
 * in base al pattern della parola digitata dall'utente.
 */
object CasingHelper {
    /**
     * Applica la capitalizzazione del suggerimento in base al pattern della parola originale.
     * 
     * @param candidate La parola suggerita (es. "Parenzo")
     * @param original La parola digitata dall'utente (es. "parenz", "Parenz", "PARENZ")
     * @param forceLeadingCapital Se true, forza la prima lettera maiuscola (per auto-capitalize)
     * @return La parola con la capitalizzazione corretta
     */
    fun applyCasing(
        candidate: String,
        original: String,
        forceLeadingCapital: Boolean = false
    ): String {
        if (candidate.isEmpty()) return candidate
        
        // Se il campo richiede capitalizzazione forzata, applica titlecase
        if (forceLeadingCapital) {
            return candidate.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }
        }
        
        if (original.isEmpty()) return candidate
        
        // Determina il pattern di capitalizzazione della parola originale
        val firstUpper = original.first().isUpperCase()
        val restLower = original.drop(1).all { it.isLowerCase() }
        val allUpper = original.all { it.isUpperCase() }
        val allLower = original.all { it.isLowerCase() }
        
        return when {
            // Caso: PARENZ -> PARENZO (tutto maiuscolo)
            allUpper -> candidate.uppercase(Locale.getDefault())
            // Caso: Parenz -> Parenzo (prima maiuscola, resto minuscolo)
            firstUpper && restLower -> 
                candidate.replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
                }
            // Caso: parenz -> parenzo (tutto minuscolo)
            allLower -> candidate.lowercase(Locale.getDefault())
            // Altri casi: usa il suggerimento così com'è
            else -> candidate
        }
    }
}

