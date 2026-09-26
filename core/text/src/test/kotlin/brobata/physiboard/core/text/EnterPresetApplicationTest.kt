package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: per-app-behavior.md SS3.11 step 3. */
class EnterPresetApplicationTest {

    @Test
    fun `custom leaves every row alone`() {
        val current = listOf(EnterOverride("com.slack", EnterBehavior.NEWLINE))
        assertEquals(current, EnterPresetApplication.apply(MessagingPreset.CUSTOM, current, installed = { true }))
    }

    @Test
    fun `send-on-enter writes a row for every installed favourite, discord excluded, and keeps non-favourites`() {
        val current = listOf(EnterOverride("com.slack", EnterBehavior.NEWLINE))
        val result = EnterPresetApplication.apply(MessagingPreset.SEND_SHIFT_NEWLINE, current, installed = { true })

        assertEquals(EnterOverride("com.slack", EnterBehavior.NEWLINE), result.first { it.packageName == "com.slack" })
        assertEquals(EnterBehavior.SEND_SHIFT_NEWLINE, result.first { it.packageName == "com.whatsapp" }.behavior)
        assertEquals(EnterBehavior.APP_DEFAULT, result.first { it.packageName == "com.discord" }.behavior)
        assertEquals(EnterSendMethod.AUTO, result.first { it.packageName == "com.whatsapp" }.sendMethod)
        assertEquals(ExtraSendShortcut.NONE, result.first { it.packageName == "com.whatsapp" }.extraSendShortcut)
        assertEquals(FavouriteApp.ORDERED.size + 1, result.size)
    }

    @Test
    fun `an uninstalled favourite gets no row`() {
        val result = EnterPresetApplication.apply(MessagingPreset.SEND_SHIFT_NEWLINE, emptyList(), installed = { it != "com.whatsapp" })
        assertEquals(null, result.firstOrNull { it.packageName == "com.whatsapp" })
    }

    @Test
    fun `choosing a preset replaces an existing manual override on a favourite`() {
        val current = listOf(EnterOverride("com.whatsapp", EnterBehavior.NEWLINE, EnterSendMethod.PLAIN_ENTER))
        val result = EnterPresetApplication.apply(MessagingPreset.NEWLINE_CTRL_SEND, current, installed = { true })
        val whatsapp = result.single { it.packageName == "com.whatsapp" }
        assertEquals(EnterBehavior.NEWLINE_CTRL_SEND, whatsapp.behavior)
        assertEquals(EnterSendMethod.AUTO, whatsapp.sendMethod)
    }

    @Test
    fun `app default preset also rewrites favourite rows back to app default`() {
        val current = listOf(EnterOverride("com.whatsapp", EnterBehavior.NEWLINE))
        val result = EnterPresetApplication.apply(MessagingPreset.APP_DEFAULT, current, installed = { true })
        assertEquals(EnterBehavior.APP_DEFAULT, result.single { it.packageName == "com.whatsapp" }.behavior)
    }
}
