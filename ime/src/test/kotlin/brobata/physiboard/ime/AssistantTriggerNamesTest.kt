package brobata.physiboard.ime

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * spec: dictation.md SS11.3, "The trampoline's name is a contract". The vendor row
 * `func1_long_press_activity` outlives every update of the app, so a name once written must stay
 * declared, exported, for as long as a phone may carry it. 3.0.0 to 3.4.0 dropped the 2.x name and
 * the orange key gave a haptic and then nothing (2026-10-22). This test reads the module's own
 * manifest so a rename, or a dropped alias, fails here instead of on the phone.
 */
class AssistantTriggerNamesTest {
    private val manifest = File("src/main/AndroidManifest.xml")

    private fun components(): Map<String, Element> {
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val found = LinkedHashMap<String, Element>()
        for (tag in listOf("activity", "activity-alias")) {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                found[element.getAttributeNS(ANDROID, "name")] = element
            }
        }
        return found
    }

    @Test
    fun `the current name is the activity's own class name`() {
        assertEquals(AssistantTriggerActivity::class.java.name, AssistantTriggerNames.CURRENT)
    }

    @Test
    fun `every served name is declared and exported in the manifest`() {
        val declared = components()
        for (name in AssistantTriggerNames.SERVED) {
            val element = declared[name]
            assertTrue(element != null, "the manifest declares no activity or alias named $name")
            assertEquals("true", element.getAttributeNS(ANDROID, "exported"), "$name must be exported: the vendor starts it from system_server")
        }
    }

    @Test
    fun `the legacy name is an alias of the current activity`() {
        val alias = components().getValue(AssistantTriggerNames.LEGACY)
        assertEquals("activity-alias", alias.tagName)
        assertEquals(AssistantTriggerNames.CURRENT, alias.getAttributeNS(ANDROID, "targetActivity"))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
