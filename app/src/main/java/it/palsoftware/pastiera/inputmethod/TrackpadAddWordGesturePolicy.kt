package it.palsoftware.pastiera.inputmethod

object TrackpadAddWordGesturePolicy {
    fun canAddWordByGesture(
        third: Int,
        addWordGestureEnabled: Boolean,
        fullWidthWhenAddOnlyEnabled: Boolean,
        addWordCandidate: String?,
        visibleSuggestions: List<String>
    ): Boolean {
        if (!addWordGestureEnabled || addWordCandidate == null) return false
        if (third == 0) return true
        return fullWidthWhenAddOnlyEnabled && visibleSuggestions.isEmpty()
    }
}
