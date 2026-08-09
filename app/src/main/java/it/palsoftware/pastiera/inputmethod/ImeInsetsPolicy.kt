package it.palsoftware.pastiera.inputmethod

import android.inputmethodservice.InputMethodService

internal object ImeInsetsPolicy {
    fun applyCandidatesOnlyContentInsets(
        insets: InputMethodService.Insets,
        candidatesOnly: Boolean
    ) {
        if (candidatesOnly) {
            insets.contentTopInsets = insets.visibleTopInsets
        }
    }
}
