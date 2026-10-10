package brobata.physiboard.app.shell

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import brobata.physiboard.core.shell.ArchiveFacts
import brobata.physiboard.core.shell.InstalledApp
import java.io.File
import java.security.MessageDigest

/**
 * What Android says about a downloaded APK and about PhysiBoard as installed, reduced to the facts
 * [brobata.physiboard.core.shell.ApkVerification] decides on (app-shell.md SS32.3). Reading an
 * archive's signing certificates makes Android check the APK's signature against its contents,
 * so a tampered file reads as null or as unsigned rather than as PhysiBoard.
 */
object ApkInspector {

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    /** Null when Android cannot read [file] as an app at all. */
    fun archiveFacts(context: Context, file: File): ArchiveFacts? {
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            }
        }.getOrNull() ?: return null
        return ArchiveFacts(info.packageName ?: return null, info.longVersionCode, signerDigests(info))
    }

    /** PhysiBoard as Android has it installed right now. */
    fun installedApp(context: Context): InstalledApp? {
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
        }.getOrNull() ?: return null
        return InstalledApp(info.packageName, info.longVersionCode, signerDigests(info))
    }

    /**
     * The certificates that signed the APK's contents now, not the older ones a rotated key may
     * vouch for: the update must be signed exactly as the running app is (SS32.3).
     */
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signers: Array<Signature> = info.signingInfo?.apkContentsSigners ?: return emptySet()
        return signers.map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
