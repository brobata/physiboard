package brobata.physiboard.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** spec: per-app-behavior.md SS3.11's status-block table, SS3.2 tested/experimental, E13. */
class FavouriteStatusCardTest {

    @Test
    fun `E13 discord under send-on-enter shows no override even though it applies`() {
        val status = FavouriteStatusCard.forRow(FavouriteApp.DISCORD, EnterBehavior.SEND_SHIFT_NEWLINE)
        assertEquals("No override", status.badge)
        assertEquals("App default: PhysiBoard does not intervene.", status.line1)
    }

    @Test
    fun `discord under newline-ctrl-send shows plain enter`() {
        val status = FavouriteStatusCard.forRow(FavouriteApp.DISCORD, EnterBehavior.NEWLINE_CTRL_SEND)
        assertEquals("Plain Enter", status.badge)
        assertEquals("Active: Enter newline, Ctrl+Enter sends.", status.line1)
    }

    @Test
    fun `app default gives no override for any favourite`() {
        val status = FavouriteStatusCard.forRow(FavouriteApp.WHATSAPP, EnterBehavior.APP_DEFAULT)
        assertEquals("No override", status.badge)
        assertEquals("The app decides whether Enter sends or inserts a newline.", status.line2)
    }

    @Test
    fun `a tested favourite shows active with the app-action badge`() {
        val status = FavouriteStatusCard.forRow(FavouriteApp.WHATSAPP, EnterBehavior.SEND_SHIFT_NEWLINE)
        assertEquals("App action", status.badge)
        assertEquals("Active: Enter sends, Shift+Enter newline.", status.line1)
    }

    @Test
    fun `an untested favourite is worded experimentally active`() {
        val status = FavouriteStatusCard.forRow(FavouriteApp.SIGNAL, EnterBehavior.SEND_SHIFT_NEWLINE)
        assertEquals("App action", status.badge)
        assertEquals("Experimentally active: Enter sends, Shift+Enter newline.", status.line1)
        assertEquals("Not confirmed: PhysiBoard is trying the app's send action and direct newline insertion.", status.line2)
    }

    @Test
    fun `whatsapp and telegram and element and google messages and threema and threema libre and instagram are tested`() {
        val tested = setOf(
            FavouriteApp.WHATSAPP, FavouriteApp.TELEGRAM, FavouriteApp.ELEMENT,
            FavouriteApp.GOOGLE_MESSAGES, FavouriteApp.THREEMA, FavouriteApp.THREEMA_LIBRE, FavouriteApp.INSTAGRAM,
        )
        assertEquals(tested, FavouriteApp.entries.filter { it.tested }.toSet())
    }

    @Test
    fun `signal and discord and messenger are not tested`() {
        val untested = setOf(FavouriteApp.SIGNAL, FavouriteApp.DISCORD, FavouriteApp.MESSENGER)
        assertEquals(untested, FavouriteApp.entries.filterNot { it.tested }.toSet())
    }

    @Test
    fun `favourite order matches the spec exactly`() {
        assertEquals(
            listOf(
                "com.whatsapp", "org.telegram.messenger", "org.thoughtcrime.securesms", "com.discord",
                "im.vector.app", "com.google.android.apps.messaging", "ch.threema.app", "ch.threema.app.libre",
                "com.instagram.android", "com.facebook.orca",
            ),
            FavouriteApp.ORDERED.map { it.packageName },
        )
    }
}
