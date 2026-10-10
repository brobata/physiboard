package brobata.physiboard.ime.pointer

import android.graphics.Matrix
import brobata.physiboard.core.pointer.caret.CaretGeometry
import brobata.physiboard.core.pointer.caret.CursorAnchorReport
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The caret badge sits beside the caret on the screen, not at the editor's own coordinates (trackpad-caret-nav.md SS4.6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CaretOnScreenTest {

    private val report = CursorAnchorReport(horizontalPx = 40f, topPx = 10f, bottomPx = 50f)

    @Test
    fun `a text box at the bottom of the screen puts the caret at the bottom, not near the top`() {
        val toScreen = Matrix().apply { setTranslate(30f, 900f) }
        assertEquals(CaretGeometry(leftPx = 70f, topPx = 910f, bottomPx = 950f), CaretOnScreen.map(report, toScreen))
    }

    @Test
    fun `a scaled editor scales the line height too`() {
        val toScreen = Matrix().apply { setScale(2f, 2f); postTranslate(0f, 100f) }
        val caret = CaretOnScreen.map(report, toScreen)
        assertEquals(CaretGeometry(leftPx = 80f, topPx = 120f, bottomPx = 200f), caret)
        assertEquals(80f, caret.lineHeightPx, 0f)
    }

    @Test
    fun `no matrix keeps the reported numbers`() {
        assertEquals(CaretGeometry(leftPx = 40f, topPx = 10f, bottomPx = 50f), CaretOnScreen.map(report, null))
    }
}
