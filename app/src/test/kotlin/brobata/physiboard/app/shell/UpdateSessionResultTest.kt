package brobata.physiboard.app.shell

import android.app.Application
import android.content.pm.PackageInstaller
import brobata.physiboard.core.shell.AutoUpdatePolicy.SessionOutcome
import brobata.physiboard.core.shell.PendingUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Modifier

/** app-shell.md SS32.3: what Android's answer to an install session may do to the waiting update. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpdateSessionResultTest {

    private val context: Application = RuntimeEnvironment.getApplication()

    /** Every STATUS_ code PackageInstaller has on the device, by name. */
    private val expected = mapOf(
        "STATUS_SUCCESS" to SessionOutcome.SUCCESS,
        "STATUS_PENDING_USER_ACTION" to SessionOutcome.NEEDS_USER,
        "STATUS_FAILURE_INVALID" to SessionOutcome.REFUSED,
        "STATUS_FAILURE_INCOMPATIBLE" to SessionOutcome.REFUSED,
        "STATUS_FAILURE_CONFLICT" to SessionOutcome.REFUSED,
        "STATUS_FAILURE" to SessionOutcome.RETRY_LATER,
        "STATUS_FAILURE_BLOCKED" to SessionOutcome.RETRY_LATER,
        "STATUS_FAILURE_ABORTED" to SessionOutcome.RETRY_LATER,
        "STATUS_FAILURE_STORAGE" to SessionOutcome.RETRY_LATER,
        "STATUS_FAILURE_TIMEOUT" to SessionOutcome.RETRY_LATER,
        // Hidden in the SDK, visible on the device: an incremental install still streaming.
        "STATUS_PENDING_STREAMING" to SessionOutcome.RETRY_LATER,
    )

    @Test
    fun `only a verdict on the APK itself refuses the release, for every status Android defines`() {
        val constants = PackageInstaller::class.java.fields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && it.name.startsWith("STATUS_") }
            .associate { it.name to it.getInt(null) }
        assertEquals("every STATUS_ constant is classified here", constants.keys, expected.keys)
        for ((name, value) in constants) assertEquals(name, expected.getValue(name), SessionInstaller.outcome(value))
        assertEquals("a code this build does not know keeps the file", SessionOutcome.RETRY_LATER, SessionInstaller.outcome(12345))
    }

    private fun waiting(tag: String): PendingUpdate {
        val store = UpdateStore(context)
        val partial = store.partialFile(tag).apply { writeBytes(ByteArray(16) { 9 }) }
        val ready = PendingUpdate(tag, 30300, ApkInspector.sha256(partial), "physiboard-${tag.removePrefix("v")}.apk")
        assertTrue(store.keep(partial, ready))
        return ready
    }

    @Test
    fun `a refusal about another release leaves the waiting update alone`() {
        val ready = waiting("v3.3.1")
        AutoUpdater.onBackgroundSessionResult(context, PackageInstaller.STATUS_FAILURE_INVALID, 1, "bad", committedTag = "v3.3.0")
        assertEquals(ready, UpdateStore(context).read().ready)
        assertTrue(File(context.noBackupFilesDir, "updates/physiboard-3.3.1.apk").isFile)
        AutoUpdater.onBackgroundSessionResult(context, PackageInstaller.STATUS_FAILURE_INVALID, 1, "bad", committedTag = null)
        assertEquals(ready, UpdateStore(context).read().ready)
    }

    @Test
    fun `a refusal about the waiting release refuses it, a timeout does not`() {
        val ready = waiting("v3.3.1")
        AutoUpdater.onBackgroundSessionResult(context, PackageInstaller.STATUS_FAILURE_TIMEOUT, 1, "slow", committedTag = "v3.3.1")
        assertEquals(ready, UpdateStore(context).read().ready)
        AutoUpdater.onBackgroundSessionResult(context, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, 1, "bad", committedTag = "v3.3.1")
        assertNull(UpdateStore(context).read().ready)
        assertEquals("v3.3.1", UpdateStore(context).read().refusedTag)
    }
}
