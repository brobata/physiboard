package brobata.physiboard.ime.pointer

import android.graphics.Matrix
import brobata.physiboard.core.pointer.caret.CaretGeometry
import brobata.physiboard.core.pointer.caret.CursorAnchorReport

/**
 * The caret in screen pixels, which is the space the badge window is placed in. spec:
 * trackpad-caret-nav.md SS4.6. An editor reports its insertion marker in its own coordinates;
 * `CursorAnchorInfo.getMatrix()` is what maps them onto the screen. Without it a text box at the
 * bottom of the screen put the badge near the top, and a web page's caret landed one toolbar
 * too high.
 */
internal object CaretOnScreen {
    /** [report] must already be usable (`CaretUsability.isUsable`); a null [matrix] is taken as no transform. */
    fun map(report: CursorAnchorReport, matrix: Matrix?): CaretGeometry {
        val x = report.horizontalPx!!
        val points = floatArrayOf(x, report.topPx!!, x, report.bottomPx!!)
        matrix?.mapPoints(points)
        return CaretGeometry(leftPx = points[0], topPx = points[1], bottomPx = points[3])
    }
}
