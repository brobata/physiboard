package brobata.physiboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Looper
import android.view.inputmethod.InputMethodSubtype
import brobata.physiboard.core.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * dictionaries-languages.md SS9.4, from a user report (2026-11-02): Polish picked in Android's own
 * language settings is a base subtype with no input-style row, so the keyboard went straight back
 * to English and a downloaded Polish dictionary was never used. These drive the real
 * [KeyboardSession] with Android's current subtype set to `pl_PL`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SubtypeLanguageStartupTest {

    private lateinit var keyboard: KeyboardSession

    private val polishSubtype: InputMethodSubtype = InputMethodSubtype.InputMethodSubtypeBuilder()
        .setSubtypeLocale("pl_PL")
        .setSubtypeMode("keyboard")
        .build()

    private fun downloadPolish() {
        val dir = File(RuntimeEnvironment.getApplication().filesDir, "dictionaries/downloaded").apply { mkdirs() }
        File(dir, "pl.pbd").writeBytes(byteArrayOf(1))
    }

    private fun start(current: InputMethodSubtype?) {
        // The process-wide dictionaries would otherwise keep an earlier test's application and files.
        SharedDictionaries.resetForTest()
        val service = Robolectric.buildService(PlainService::class.java).create().get()
        keyboard = KeyboardSession(
            service,
            settingsSource = object : SettingsSource {
                override val settings: Flow<Settings> = flowOf(Settings())
            },
            readCurrentSubtype = { current },
        )
        idle()
    }

    @After
    fun tearDown() {
        if (::keyboard.isInitialized) keyboard.onServiceDestroyed()
        File(RuntimeEnvironment.getApplication().filesDir, "dictionaries").deleteRecursively()
        SharedDictionaries.resetForTest()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `starts in Polish when Android's current subtype is Polish and its dictionary is installed`() {
        downloadPolish()
        start(polishSubtype)
        assertEquals("pl_PL:qwerty", keyboard.activeStyleKey)
    }

    @Test
    fun `starts in English when the Polish dictionary is not installed, so suggestions are not lost`() {
        start(polishSubtype)
        assertEquals("en_US:qwerty", keyboard.activeStyleKey)
    }

    @Test
    fun `switches to Polish the moment its dictionary is downloaded`() {
        start(polishSubtype)
        downloadPolish()
        SharedDictionaries.get(RuntimeEnvironment.getApplication()).reloadAll()
        idle()
        assertEquals("pl_PL:qwerty", keyboard.activeStyleKey)
    }

    @Test
    fun `picking Polish in Android's settings keeps Polish through the next settings emission`() {
        downloadPolish()
        start(null)
        assertEquals("en_US:qwerty", keyboard.activeStyleKey)
        keyboard.onCurrentInputMethodSubtypeChanged(polishSubtype)
        idle()
        assertEquals("pl_PL:qwerty", keyboard.activeStyleKey)
    }

    class PlainService : InputMethodService()
}
