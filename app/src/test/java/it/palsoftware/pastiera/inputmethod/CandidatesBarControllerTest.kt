package it.palsoftware.pastiera.inputmethod

import android.view.View
import android.view.ViewGroup
import it.palsoftware.pastiera.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CandidatesBarControllerTest {
    private val context = RuntimeEnvironment.getApplication()

    @After
    fun tearDown() {
        SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.AUTO)
        SoftwareKeyboardAutoDetector.onInputDevicesChanged()
    }

    @Test
    fun candidatesViewIsNotCollapsedByConfiguredSoftwareKeyboardMode() {
        SettingsManager.setSoftwareKeyboardMode(
            context,
            SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL
        )
        val controller = CandidatesBarController(context)

        val candidatesView = controller.getCandidatesView()

        assertEquals(View.VISIBLE, candidatesView.visibility)
        assertNotEquals(0, candidatesView.layoutParams.height)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, candidatesView.layoutParams.height)
    }

    @Test
    fun candidatesViewRendersHardwareStatusRow() {
        val controller = CandidatesBarController(context)
        val candidatesView = controller.getCandidatesView()

        controller.updateStatusBars(
            snapshot = emptyStatusSnapshot(),
            emojiMapText = "",
            inputConnection = null,
            symMappings = null
        )
        candidatesView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        assertNotEquals(0, candidatesView.measuredHeight)
    }

    private fun emptyStatusSnapshot() = StatusBarController.StatusSnapshot(
        capsLockEnabled = false,
        shiftPhysicallyPressed = false,
        shiftOneShot = false,
        ctrlLatchActive = false,
        ctrlPhysicallyPressed = false,
        ctrlOneShot = false,
        ctrlLatchFromNavMode = false,
        altLatchActive = false,
        altPhysicallyPressed = false,
        altOneShot = false,
        symPage = 0
    )
}
