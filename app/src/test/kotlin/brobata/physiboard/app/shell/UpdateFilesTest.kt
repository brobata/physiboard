package brobata.physiboard.app.shell

import android.app.Application
import brobata.physiboard.core.shell.InstalledApp
import brobata.physiboard.core.shell.PendingUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * app-shell.md SS32.3, SS32.6: the private update folder, and the last check before an install,
 * on Robolectric. A real APK's signing certificates can only be read on a device; the emulator run
 * in the change record covers that half.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UpdateFilesTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val store = UpdateStore(context)
    private val updates = File(context.noBackupFilesDir, "updates")
    private val ours = InstalledApp(context.packageName, 30200, setOf("a".repeat(64)))

    private fun downloaded(bytes: ByteArray): Pair<File, PendingUpdate> {
        val partial = store.partialFile("v3.3.0")
        partial.writeBytes(bytes)
        val ready = PendingUpdate("v3.3.0", 30300, ApkInspector.sha256(partial), "physiboard-3.3.0.apk")
        assertTrue(store.keep(partial, ready))
        return File(updates, ready.fileName) to ready
    }

    @Test
    fun `the update lives in the app's no-backup folder and its record survives a re-read`() {
        val (apk, ready) = downloaded(ByteArray(32) { 7 })
        assertTrue(apk.isFile)
        assertTrue(apk.path.startsWith(context.noBackupFilesDir.path))
        assertFalse(File(updates, "physiboard-3.3.0.apk.part").exists())
        assertEquals(ready, UpdateStore(context).read().ready)
        assertEquals(apk, store.apkFor(ready))
    }

    @Test
    fun `discarding deletes the file and forgets it`() {
        val (apk, _) = downloaded(ByteArray(16))
        store.discardReady()
        assertFalse(apk.exists())
        assertNull(store.read().ready)
    }

    @Test
    fun `a file Android cannot read as an app is deleted, refused, and not downloaded again`() {
        val (apk, ready) = downloaded("not an apk".toByteArray())
        assertNull(ApkInspector.archiveFacts(context, apk))
        assertNull(AutoUpdater.checkedApk(context, store, ready, ours))
        assertFalse(apk.exists())
        assertNull(store.read().ready)
        assertEquals("v3.3.0", store.read().refusedTag)
    }

    @Test
    fun `a file changed after its download fails the checksum and is deleted`() {
        val (apk, ready) = downloaded(ByteArray(64) { 1 })
        apk.writeBytes(ByteArray(64) { 2 })
        assertNull(AutoUpdater.checkedApk(context, store, ready, ours))
        assertFalse(apk.exists())
        assertEquals("v3.3.0", store.read().refusedTag)
    }

    @Test
    fun `refusing a newer release keeps the update already waiting`() {
        val (apk, ready) = downloaded(ByteArray(16) { 3 })
        store.partialFile("v3.3.1").writeBytes(ByteArray(4))
        store.refuse("v3.3.1")
        assertTrue(apk.isFile)
        assertEquals(ready, store.read().ready)
        assertEquals("v3.3.1", store.read().refusedTag)
        assertFalse(File(updates, "physiboard-3.3.1.apk.part").exists())
    }

    @Test
    fun `refusing one release never deletes another release's download in progress`() {
        val (apk, ready) = downloaded(ByteArray(16) { 4 })
        val downloading = store.partialFile("v3.3.2").apply { writeBytes(ByteArray(8)) }
        store.refuse("v3.3.0")
        assertFalse(apk.exists())
        assertNull(store.read().ready)
        assertTrue("v3.3.2's download is still being written", downloading.isFile)
        store.refuse("v3.3.1")
        assertTrue(downloading.isFile)
        store.discardReady()
        assertTrue("discarding never touches a download in progress", downloading.isFile)
        store.refuse("v3.3.2")
        assertFalse(downloading.exists())
        assertEquals("v3.3.0", ready.tag)
    }

    @Test
    fun `a missing file is forgotten rather than installed`() {
        val (apk, ready) = downloaded(ByteArray(8))
        apk.delete()
        assertNull(store.apkFor(ready))
        assertNull(AutoUpdater.checkedApk(context, store, ready, ours))
        assertNull(store.read().ready)
    }

    @Test
    fun `the checksum of a file is its SHA-256 in lower-case hex`() {
        val file = File(context.cacheDir, "abc").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ApkInspector.sha256(file))
        assertNotNull(ApkInspector.sha256(file))
    }
}
