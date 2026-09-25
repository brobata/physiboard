package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** spec: dictionaries-languages.md SS5.1 (the manifest contract), SS5.2 (fetch outcomes), SS6, SS5.5. */
class DictionaryManifestTest {

    private val sample = """
        {
          "schemaVersion": 1,
          "generatedAt": "2026-08-31T22:32:15.661255Z",
          "releaseTag": "1",
          "items": [
            {
              "id": "it_base",
              "filename": "it_base.dict",
              "url": "https://example.com/it_base.dict",
              "bytes": 13938536,
              "sha256": "7776eb23",
              "updatedAt": "2026-08-01T00:00:00Z",
              "name": "Italian (Basic)",
              "shortDescription": "Common words",
              "languageTag": "it-IT"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `a well formed manifest parses every required field`() {
        val manifest = DictionaryManifestCodec.parse(sample)
        assertEquals(1, manifest?.schemaVersion)
        assertEquals("1", manifest?.releaseTag)
        val item = manifest?.items?.single()
        assertEquals("it", item?.languageCode)
        assertEquals("Italian (Basic)", item?.name)
    }

    @Test
    fun `a missing required field is a parse error, per SS5-1`() {
        val missingSha = sample.replace("\"sha256\": \"7776eb23\",", "")
        assertNull(DictionaryManifestCodec.parse(missingSha))
    }

    @Test
    fun `an empty or blank or unparsable body does not parse, per SS5-2`() {
        assertNull(DictionaryManifestCodec.parse(null))
        assertNull(DictionaryManifestCodec.parse(""))
        assertNull(DictionaryManifestCodec.parse("   "))
        assertNull(DictionaryManifestCodec.parse("not json"))
    }

    @Test
    fun `an unknown field is ignored, per SS5-1`() {
        val withExtra = sample.replace("\"releaseTag\": \"1\",", "\"releaseTag\": \"1\", \"somethingNew\": 42,")
        assertEquals("1", DictionaryManifestCodec.parse(withExtra)?.releaseTag)
    }

    @Test
    fun `the language code strips _base-dict, falling back to just -dict`() {
        val item = DictionaryManifestItem("id", "vi_base.dict", "u", 1, "s", "t", "n", "", "")
        assertEquals("vi", item.languageCode)
        val bare = item.copy(filename = "custom.dict")
        assertEquals("custom", bare.languageCode)
    }
}
