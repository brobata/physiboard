package it.palsoftware.pastiera.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper
import it.palsoftware.pastiera.AppBroadcastActions
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RestoreManagerIntegrationTest {

    private lateinit var context: Context
    private lateinit var userDefaultsFile: File
    private var originalUserDefaultsContent: String? = null
    private var originalUserDefaultsExisted: Boolean = false

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        userDefaultsFile = File(context.filesDir, "user_defaults.json")
        originalUserDefaultsExisted = userDefaultsFile.exists()
        originalUserDefaultsContent = userDefaultsFile.takeIf { it.exists() }?.readText()

        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("user_dictionary_entries")
            .remove("keyboard_layout")
            .commit()
        if (userDefaultsFile.exists()) {
            userDefaultsFile.delete()
        }
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
            .edit()
            .remove("user_dictionary_entries")
            .remove("keyboard_layout")
            .commit()

        if (originalUserDefaultsExisted) {
            userDefaultsFile.parentFile?.mkdirs()
            userDefaultsFile.writeText(originalUserDefaultsContent ?: "{}")
        } else if (userDefaultsFile.exists()) {
            userDefaultsFile.delete()
        }
    }

    @Test
    fun restore_withUserDictionaryPref_appliesPrefAndSendsRefreshBroadcast() = runBlocking {
        val backupZip = createBackupZip(
            includeMetadata = true,
            prefsFiles = mapOf(
                "pastiera_prefs.json" to prefsBackupJson(
                    prefName = "pastiera_prefs",
                    entries = mapOf(
                        "user_dictionary_entries" to PreferenceValue(
                            PreferenceValueType.STRING,
                            """["alpha","beta"]"""
                        )
                    )
                )
            ),
            fileEntries = emptyMap()
        )

        val broadcastCount = countUserDictionaryBroadcastsDuring {
            val result = RestoreManager.restore(context, Uri.fromFile(backupZip))
            val success = result as RestoreResult.Success

            assertTrue(success.preferencesSummary.appliedKeys.contains("pastiera_prefs:user_dictionary_entries"))
            assertEquals(
                setOf(RestoreManager.PostRestoreAction.REFRESH_USER_DICTIONARY),
                success.postActionsTriggered
            )
            val restoredValue = context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                .getString("user_dictionary_entries", null)
            assertEquals("""["alpha","beta"]""", restoredValue)
        }

        assertEquals(1, broadcastCount)
    }

    @Test
    fun restore_oldStyleBackupWithUserDefaultsFile_stillSendsRefreshBroadcast() = runBlocking {
        val backupZip = createBackupZip(
            includeMetadata = true,
            prefsFiles = mapOf(
                "pastiera_prefs.json" to prefsBackupJson(
                    prefName = "pastiera_prefs",
                    entries = mapOf(
                        "keyboard_layout" to PreferenceValue(PreferenceValueType.STRING, "qwerty")
                    )
                )
            ),
            fileEntries = mapOf(
                "user_defaults.json" to JSONObject().put("hello", true).toString()
            )
        )

        val broadcastCount = countUserDictionaryBroadcastsDuring {
            val result = RestoreManager.restore(context, Uri.fromFile(backupZip))
            val success = result as RestoreResult.Success

            assertTrue(success.fileSummary.restoredFiles.contains("user_defaults.json"))
            assertEquals(
                setOf(RestoreManager.PostRestoreAction.REFRESH_USER_DICTIONARY),
                success.postActionsTriggered
            )
            assertTrue(File(context.filesDir, "user_defaults.json").exists())
            assertEquals(
                "qwerty",
                context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                    .getString("keyboard_layout", null)
            )
        }

        assertEquals(1, broadcastCount)
    }

    @Test
    fun restore_oldStyleBackupWithoutUserDictionaryArtifacts_restoresWithoutRefreshBroadcast() = runBlocking {
        val backupZip = createBackupZip(
            includeMetadata = true,
            prefsFiles = mapOf(
                "pastiera_prefs.json" to prefsBackupJson(
                    prefName = "pastiera_prefs",
                    entries = mapOf(
                        "keyboard_layout" to PreferenceValue(PreferenceValueType.STRING, "colemak")
                    )
                )
            ),
            fileEntries = emptyMap()
        )

        val broadcastCount = countUserDictionaryBroadcastsDuring {
            val result = RestoreManager.restore(context, Uri.fromFile(backupZip))
            val success = result as RestoreResult.Success
            assertEquals(
                "colemak",
                context.getSharedPreferences("pastiera_prefs", Context.MODE_PRIVATE)
                    .getString("keyboard_layout", null)
            )
            assertTrue(success.postActionsTriggered.isEmpty())
            assertTrue(success.fileSummary.restoredFiles.none { it.endsWith("user_defaults.json") })
        }

        assertEquals(0, broadcastCount)
    }

    private suspend fun countUserDictionaryBroadcastsDuring(block: suspend () -> Unit): Int {
        var count = 0
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AppBroadcastActions.USER_DICTIONARY_UPDATED) {
                    count++
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(AppBroadcastActions.USER_DICTIONARY_UPDATED))
        try {
            block()
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            context.unregisterReceiver(receiver)
        }
        return count
    }

    private fun createBackupZip(
        includeMetadata: Boolean,
        prefsFiles: Map<String, String>,
        fileEntries: Map<String, String>
    ): File {
        val zipFile = File.createTempFile("restore_test_", ".zip", context.cacheDir)
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            if (includeMetadata) {
                val metadata = BackupMetadata(
                    versionCode = 1,
                    versionName = "legacy-test",
                    timestamp = "2026-02-25T00:00:00Z",
                    components = (prefsFiles.keys.map { "prefs/$it" } + fileEntries.keys.map { "files/$it" }).sorted()
                )
                addZipEntry(zipOut, "backup_meta.json", metadata.toJsonString())
            }
            prefsFiles.forEach { (name, content) ->
                addZipEntry(zipOut, "prefs/$name", content)
            }
            fileEntries.forEach { (relativePath, content) ->
                addZipEntry(zipOut, "files/$relativePath", content)
            }
        }
        return zipFile
    }

    private fun prefsBackupJson(
        prefName: String,
        entries: Map<String, PreferenceValue>
    ): String {
        val json = JSONObject()
        json.put("name", prefName)
        val entriesJson = JSONObject()
        entries.forEach { (key, value) ->
            entriesJson.put(key, value.toJson())
        }
        json.put("entries", entriesJson)
        return json.toString(2)
    }

    private fun addZipEntry(zipOut: ZipOutputStream, path: String, content: String) {
        zipOut.putNextEntry(ZipEntry(path))
        zipOut.write(content.toByteArray())
        zipOut.closeEntry()
    }
}
