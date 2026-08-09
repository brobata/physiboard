package it.palsoftware.pastiera.inputmethod

import android.inputmethodservice.InputMethodService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImeInsetsPolicyTest {
    @Test
    fun candidatesOnlyTreatsVisibleStatusBarAsImeContent() {
        val insets = InputMethodService.Insets().apply {
            contentTopInsets = 122
            visibleTopInsets = 0
            touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE
            touchableRegion = android.graphics.Region()
        }

        ImeInsetsPolicy.applyCandidatesOnlyContentInsets(
            insets,
            candidatesOnly = true,
            touchableWidth = 1080,
            touchableHeight = 229
        )

        assertEquals(0, insets.contentTopInsets)
        assertEquals(
            InputMethodService.Insets.TOUCHABLE_INSETS_REGION,
            insets.touchableInsets
        )
        assertEquals(
            android.graphics.Rect(0, 0, 1080, 229),
            ImeInsetsPolicy.candidatesTouchableBounds(0, 1080, 229)
        )
    }

    @Test
    fun inputViewKeepsFrameworkContentInsets() {
        val insets = InputMethodService.Insets().apply {
            contentTopInsets = 122
            visibleTopInsets = 0
            touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE
        }

        ImeInsetsPolicy.applyCandidatesOnlyContentInsets(insets, candidatesOnly = false)

        assertEquals(122, insets.contentTopInsets)
        assertEquals(
            InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE,
            insets.touchableInsets
        )
    }
}
