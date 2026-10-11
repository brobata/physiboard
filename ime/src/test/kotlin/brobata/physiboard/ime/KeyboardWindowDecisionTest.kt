package brobata.physiboard.ime

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * status-bar.md SS3.2 (T39), SS3.4 and SS13 (T44): how KeyboardSession drives the window decision
 * from a field's start and finish. `request(shown, fieldOpen)` is what onStartInput posts
 * (shown false since the bar went, fieldOpen = the field is really editable, restarts included)
 * and onFinishInput posts (false, false).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyboardWindowDecisionTest {

    private val applied = mutableListOf<Boolean>()
    private var leaves = 0
    private val decision = KeyboardWindowDecision(Handler(Looper.getMainLooper()), Any(), apply = { applied += it }, leftField = { leaves++; onHidden() })

    /** What KeyboardSession.onKeyboardWindowHidden does first, for a hide real or stood in for. */
    private fun onHidden(): Unit = decision.onWindowHidden()

    private fun turn() = shadowOf(Looper.getMainLooper()).idle()

    private fun start(editable: Boolean) = decision.request(shown = false, fieldOpen = editable)

    private fun finish() = decision.request(shown = false, fieldOpen = false)

    @Test
    fun `T44 field A finishing and field B starting in one turn cancels the posted leave`() {
        start(editable = true); turn()
        finish(); start(editable = true); turn()
        assertEquals(0, leaves)
        assertEquals("the finish's decision never ran", listOf(false, false), applied)
    }

    @Test
    fun `T44 leaving A for a field that is not editable runs the leave exactly once`() {
        start(editable = true); turn()
        finish(); start(editable = false); turn()
        assertEquals(1, leaves)
        finish(); turn()
        assertEquals("the later finish is no second leave", 1, leaves)
    }

    @Test
    fun `T44 a restart of the same field never runs the leave`() {
        start(editable = true); turn()
        repeat(5) { start(editable = true); turn() }
        assertEquals(0, leaves)
        finish(); turn()
        assertEquals(1, leaves)
    }

    @Test
    fun `T44 a window that is up reports its own hide, so no leave is stood in for`() {
        decision.onWindowShown()
        start(editable = true); turn()
        finish(); turn()
        assertEquals(0, leaves)
    }

    @Test
    fun `T44 after a real hide the next finish is no second leave`() {
        decision.onWindowShown()
        start(editable = true); turn()
        decision.onWindowHidden()
        finish(); turn()
        assertEquals(0, leaves)
    }
}
