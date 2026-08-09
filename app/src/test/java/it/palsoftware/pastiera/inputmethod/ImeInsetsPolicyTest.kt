package it.palsoftware.pastiera.inputmethod

import android.inputmethodservice.InputMethodService
import org.junit.Assert.assertEquals
import org.junit.Test

class ImeInsetsPolicyTest {
    @Test
    fun candidatesOnlyTreatsVisibleStatusBarAsImeContent() {
        val insets = InputMethodService.Insets().apply {
            contentTopInsets = 122
            visibleTopInsets = 0
        }

        ImeInsetsPolicy.applyCandidatesOnlyContentInsets(insets, candidatesOnly = true)

        assertEquals(0, insets.contentTopInsets)
    }

    @Test
    fun inputViewKeepsFrameworkContentInsets() {
        val insets = InputMethodService.Insets().apply {
            contentTopInsets = 122
            visibleTopInsets = 0
        }

        ImeInsetsPolicy.applyCandidatesOnlyContentInsets(insets, candidatesOnly = false)

        assertEquals(122, insets.contentTopInsets)
    }
}
