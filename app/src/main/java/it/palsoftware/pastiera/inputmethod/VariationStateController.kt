package it.palsoftware.pastiera.inputmethod

import android.view.inputmethod.InputConnection

/**
 * Tracks character variation availability for the current cursor position
 * and exposes snapshots for the status / variation bars.
 */
class VariationStateController(
    private val variationsMap: Map<Char, List<String>>
) {

    data class Snapshot(
        val isActive: Boolean,
        val lastInsertedChar: Char?,
        val variations: List<String>
    )

    private var lastInsertedChar: Char? = null
    private var availableVariations: List<String> = emptyList()
    private var variationsActive: Boolean = false

    fun refreshFromCursor(
        inputConnection: InputConnection?,
        shouldDisableVariations: Boolean,
        hasActiveSelection: Boolean = false
    ): Snapshot {
        if (shouldDisableVariations || inputConnection == null) {
            clear()
            return snapshot()
        }

        if (hasActiveSelection) {
            clear()
            return snapshot()
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(1, 0)
        if (!textBeforeCursor.isNullOrEmpty()) {
            val charBeforeCursor = textBeforeCursor.last()
            val variations = variationsMap[charBeforeCursor]
            if (!variations.isNullOrEmpty()) {
                lastInsertedChar = charBeforeCursor
                availableVariations = variations
                variationsActive = true
            } else {
                clear()
            }
        } else {
            clear()
        }

        return snapshot()
    }

    fun hasVariationsFor(char: Char): Boolean = variationsMap.containsKey(char)

    fun clear() {
        variationsActive = false
        lastInsertedChar = null
        availableVariations = emptyList()
    }

    fun snapshot(): Snapshot {
        return Snapshot(
            isActive = variationsActive,
            lastInsertedChar = if (variationsActive) lastInsertedChar else null,
            variations = if (variationsActive) availableVariations else emptyList()
        )
    }
}
