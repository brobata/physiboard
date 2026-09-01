package brobata.physiboard

import brobata.physiboard.core.suggestions.DictionaryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The two shipped bugs these pin down:
 *
 *  - downloads and imports shared one folder, so downloading a language silently destroyed a
 *    dictionary the user had imported for it;
 *  - the manifest's `updatedAt` was parsed and compared against nothing, so once a language was
 *    installed it could never be updated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DictionaryStoreTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        File(context.filesDir, "dictionaries_serialized").deleteRecursively()
    }

    private fun sourceFile(name: String, body: String = "dictionary-bytes"): File =
        File(context.cacheDir, name).apply { parentFile?.mkdirs(); writeText(body) }

    private fun install(
        lang: String,
        origin: String,
        body: String = "dictionary-bytes",
        manifestUpdatedAt: String? = null
    ): Boolean = DictionaryStore.install(
        context = context,
        source = sourceFile("$lang-$origin.src", body),
        languageCode = lang,
        origin = origin,
        meta = DictionaryStore.Meta(origin = origin, manifestUpdatedAt = manifestUpdatedAt)
    )

    // ---- precedence -------------------------------------------------------------------------

    @Test
    fun resolvesNothingWhenNoLanguageIsInstalled() {
        assertNull(DictionaryStore.resolve(context, "de"))
    }

    @Test
    fun resolvesADownloadWhenOnlyADownloadExists() {
        assertTrue(install("de", DictionaryStore.ORIGIN_DOWNLOAD))

        assertEquals(DictionaryStore.ORIGIN_DOWNLOAD, DictionaryStore.originOf(context, "de"))
        assertNotNull(DictionaryStore.resolve(context, "de"))
    }

    @Test
    fun anImportOutranksADownloadForTheSameLanguage() {
        install("de", DictionaryStore.ORIGIN_DOWNLOAD, body = "from-the-project")
        install("de", DictionaryStore.ORIGIN_IMPORT, body = "from-the-user")

        assertEquals("from-the-user", DictionaryStore.resolve(context, "de")!!.readText())
        assertEquals(DictionaryStore.ORIGIN_IMPORT, DictionaryStore.originOf(context, "de"))
    }

    // ---- the clobber regression -------------------------------------------------------------

    @Test
    fun downloadingDoesNotDestroyAnImportOfTheSameLanguage() {
        install("de", DictionaryStore.ORIGIN_IMPORT, body = "from-the-user")

        install("de", DictionaryStore.ORIGIN_DOWNLOAD, body = "from-the-project")

        // The user's file is untouched and still the one that loads.
        assertEquals("from-the-user", DictionaryStore.resolve(context, "de")!!.readText())
        val downloaded = File(DictionaryStore.downloadedDir(context), DictionaryStore.fileNameFor("de"))
        assertEquals("from-the-project", downloaded.readText())
    }

    @Test
    fun removingAnImportRevealsTheDownloadUnderneath() {
        install("de", DictionaryStore.ORIGIN_DOWNLOAD, body = "from-the-project")
        install("de", DictionaryStore.ORIGIN_IMPORT, body = "from-the-user")

        DictionaryStore.remove(context, "de", DictionaryStore.ORIGIN_IMPORT)

        assertEquals("from-the-project", DictionaryStore.resolve(context, "de")!!.readText())
    }

    // ---- update detection -------------------------------------------------------------------

    @Test
    fun aNewerManifestBuildIsAnUpdate() {
        install("de", DictionaryStore.ORIGIN_DOWNLOAD, manifestUpdatedAt = "2026-01-15T18:20:18Z")

        assertTrue(DictionaryStore.hasUpdate(context, "de", "2026-07-05T13:52:33Z"))
    }

    @Test
    fun theSameManifestBuildIsNotAnUpdate() {
        install("de", DictionaryStore.ORIGIN_DOWNLOAD, manifestUpdatedAt = "2026-07-05T13:52:33Z")

        assertFalse(DictionaryStore.hasUpdate(context, "de", "2026-07-05T13:52:33Z"))
    }

    @Test
    fun anImportIsNeverOfferedAnUpdate() {
        install("de", DictionaryStore.ORIGIN_IMPORT, manifestUpdatedAt = "2020-01-01T00:00:00Z")

        assertFalse(DictionaryStore.hasUpdate(context, "de", "2026-07-05T13:52:33Z"))
    }

    @Test
    fun aDownloadWithoutASidecarIsNotTreatedAsStale() {
        // Pre-split installs have no metadata. Re-downloading every language on upgrade would
        // be a rude surprise on mobile data, so unknown means "leave it alone".
        DictionaryStore.downloadedDir(context)
            .resolve(DictionaryStore.fileNameFor("de"))
            .writeText("legacy")

        assertFalse(DictionaryStore.hasUpdate(context, "de", "2026-07-05T13:52:33Z"))
    }

    // ---- migration --------------------------------------------------------------------------

    @Test
    fun legacyCustomFilesMoveIntoTheDownloadedTier() {
        val legacy = File(context.filesDir, "dictionaries_serialized/custom").apply { mkdirs() }
        legacy.resolve("fr_base.dict").writeText("legacy-french")

        DictionaryStore.migrateLegacy(context)

        assertEquals("legacy-french", DictionaryStore.resolve(context, "fr")!!.readText())
        assertEquals(DictionaryStore.ORIGIN_DOWNLOAD, DictionaryStore.originOf(context, "fr"))
        assertFalse(legacy.resolve("fr_base.dict").exists())
    }

    @Test
    fun migrationNeverOverwritesSomethingAlreadyInPlace() {
        install("fr", DictionaryStore.ORIGIN_DOWNLOAD, body = "current")
        File(context.filesDir, "dictionaries_serialized/custom").apply { mkdirs() }
            .resolve("fr_base.dict").writeText("legacy")

        DictionaryStore.migrateLegacy(context)

        assertEquals("current", DictionaryStore.resolve(context, "fr")!!.readText())
    }

    // ---- enumeration ------------------------------------------------------------------------

    @Test
    fun installedLanguagesSpansBothTiersWithoutDuplicates() {
        install("de", DictionaryStore.ORIGIN_DOWNLOAD)
        install("de", DictionaryStore.ORIGIN_IMPORT)
        install("fr", DictionaryStore.ORIGIN_DOWNLOAD)
        install("gd", DictionaryStore.ORIGIN_IMPORT)

        assertEquals(setOf("de", "fr", "gd"), DictionaryStore.installedLanguages(context))
    }

    @Test
    fun sidecarSurvivesAndRecordsTheOrigin() {
        install("de", DictionaryStore.ORIGIN_DOWNLOAD, manifestUpdatedAt = "2026-07-05T13:52:33Z")

        val meta = DictionaryStore.readMeta(context, "de")
        assertNotNull(meta)
        assertEquals(DictionaryStore.ORIGIN_DOWNLOAD, meta!!.origin)
        assertEquals("2026-07-05T13:52:33Z", meta.manifestUpdatedAt)
        assertTrue(meta.installedAt > 0L)
    }

    @Test
    fun fileNamingRoundTrips() {
        assertEquals("de_base.dict", DictionaryStore.fileNameFor("DE"))
        assertEquals("de", DictionaryStore.languageOf("de_base.dict"))
        assertNull(DictionaryStore.languageOf("notadictionary.txt"))
    }
}
