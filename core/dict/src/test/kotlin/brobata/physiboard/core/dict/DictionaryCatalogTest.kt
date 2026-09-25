package brobata.physiboard.core.dict

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** spec: dictionaries-languages.md SS6 (the merge and dedup rule), SS5.5 (updatable). */
class DictionaryCatalogTest {

    private fun item(filename: String, updatedAt: String = "2026-01-01T00:00:00Z") = DictionaryManifestItem(
        id = filename, filename = filename, url = "u", bytes = 1, sha256 = "s",
        updatedAt = updatedAt, name = filename, shortDescription = "", languageTag = "",
    )

    @Test
    fun `a bundled file dedups ahead of a writable-tier file with the same name, per SS6`() {
        val local = listOf(
            LocalDictionaryFile("en_base.dict", "en", DictionaryOrigin.BUNDLED),
            LocalDictionaryFile("en_base.dict", "en", DictionaryOrigin.DOWNLOADED),
        )
        val rows = DictionaryCatalog.merge(local, emptyList()) { it }
        val row = rows.single()
        assertEquals(DictionaryOrigin.BUNDLED, row.installedOrigin)
        assertFalse("Imported" in row.badges)
    }

    @Test
    fun `an installed language merges with its manifest entry by file name`() {
        val local = listOf(LocalDictionaryFile("it_base.dict", "it", DictionaryOrigin.IMPORTED))
        val rows = DictionaryCatalog.merge(local, listOf(item("it_base.dict"))) { it }
        val row = rows.single()
        assertTrue(row.installed)
        assertEquals(setOf("Installed", "Imported"), row.badges)
        assertFalse(row.canDownload)
    }

    @Test
    fun `an online only dictionary is available for download and not installed`() {
        val rows = DictionaryCatalog.merge(emptyList(), listOf(item("fr_base.dict"))) { it }
        val row = rows.single()
        assertFalse(row.installed)
        assertTrue(row.canDownload)
        assertEquals(setOf("Available online"), row.badges)
    }

    @Test
    fun `a downloaded file is updatable only when the manifest is newer than its sidecar stamp`() {
        val local = LocalDictionaryFile("da_base.dict", "da", DictionaryOrigin.DOWNLOADED, manifestUpdatedAt = "2026-01-01T00:00:00Z")
        assertTrue(DictionaryCatalog.isUpdatable(local, item("da_base.dict", updatedAt = "2026-06-01T00:00:00Z")))
        assertFalse(DictionaryCatalog.isUpdatable(local, item("da_base.dict", updatedAt = "2025-01-01T00:00:00Z")))
    }

    @Test
    fun `a downloaded file with no sidecar stamp is unknown, not stale, per SS5-5`() {
        val local = LocalDictionaryFile("da_base.dict", "da", DictionaryOrigin.DOWNLOADED, manifestUpdatedAt = null)
        assertFalse(DictionaryCatalog.isUpdatable(local, item("da_base.dict", updatedAt = "2099-01-01T00:00:00Z")))
    }

    @Test
    fun `an imported file is never updatable, per SS5-5`() {
        val local = LocalDictionaryFile("da_base.dict", "da", DictionaryOrigin.IMPORTED, manifestUpdatedAt = "2020-01-01T00:00:00Z")
        assertFalse(DictionaryCatalog.isUpdatable(local, item("da_base.dict", updatedAt = "2099-01-01T00:00:00Z")))
    }

    @Test
    fun `rows are sorted by display name case insensitively, per SS6`() {
        val rows = DictionaryCatalog.merge(emptyList(), listOf(item("z_base.dict"), item("a_base.dict")).map { it.copy(name = it.filename) }) { it }
        assertEquals(listOf("a_base.dict", "z_base.dict"), rows.map { it.displayName })
    }
}
