package brobata.physiboard.app.shell

import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import brobata.physiboard.core.shell.AutoUpdatePolicy.SessionOutcome
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Android's own installer, used by PhysiBoard to update itself (app-shell.md SS32.3, route 2).
 *
 * The session asks for no user action. Android grants that to an app updating itself when it
 * holds UPDATE_PACKAGES_WITHOUT_USER_ACTION and REQUEST_INSTALL_PACKAGES, and the update targets a
 * recent Android; otherwise it answers "pending user action" and the caller decides whether to
 * show Android's confirmation now (the user is in PhysiBoard) or post "ready, tap to install".
 *
 * The APK is copied into the session from the private update folder, and hashed on the way: the
 * bytes Android installs are the bytes that were checked, or the session is abandoned.
 */
object SessionInstaller {

    /** Writes [apk] into a new session and commits it; Android's answer goes to [statusTarget]. Throws on any failure, leaving no session behind. */
    fun commit(context: Context, apk: File, expectedSha256: String, statusTarget: IntentSender): Int {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                val digest = MessageDigest.getInstance("SHA-256")
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().buffered().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            digest.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                        }
                    }
                    session.fsync(out)
                }
                val written = digest.digest().joinToString("") { "%02x".format(it) }
                if (!written.equals(expectedSha256, ignoreCase = true)) throw IOException("the update file changed after it was checked")
                session.commit(statusTarget)
            }
            return sessionId
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    /** Android's status code as the updater's outcome. A full disk is worth another try; every other failure is a refusal. */
    fun outcome(status: Int): SessionOutcome = when (status) {
        PackageInstaller.STATUS_SUCCESS -> SessionOutcome.SUCCESS
        PackageInstaller.STATUS_PENDING_USER_ACTION -> SessionOutcome.NEEDS_USER
        PackageInstaller.STATUS_FAILURE_ABORTED, PackageInstaller.STATUS_FAILURE_STORAGE -> SessionOutcome.CANCELLED
        else -> SessionOutcome.FAILED
    }

    /** Drops a session Android left waiting for a confirmation nobody will see. */
    fun abandon(context: Context, sessionId: Int) {
        if (sessionId < 0) return
        runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
    }
}
