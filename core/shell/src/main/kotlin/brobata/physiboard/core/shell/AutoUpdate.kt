package brobata.physiboard.core.shell

import brobata.physiboard.core.settings.UpdateMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.URI

/**
 * The hosts an update file may come from, and nothing else. spec: app-shell.md SS32.5.
 *
 * A release file's address is on github.com; GitHub answers it with a redirect to its file
 * servers. Each hop is checked here before it is followed, so a redirect to any other host, to
 * plain http, or to an address carrying a user name or an unusual port is never requested.
 */
object UpdateHosts {
    /** `release-assets.githubusercontent.com` is where github.com sent release files on 2026-10-10 (D9); `objects.` is where it sent them before. */
    val ALLOWED: Set<String> = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    /** More hops than GitHub has ever used (one); a loop stops here. */
    const val MAX_REDIRECTS = 5

    fun isAllowed(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.isAbsolute || uri.isOpaque) return false
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        if (uri.rawUserInfo != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        return host in ALLOWED
    }

    /** Where a redirect from [from] to [location] leads, or null when it leads anywhere not allowed. */
    fun redirectTarget(from: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val target = runCatching { URI(from).resolve(location.trim()).toString() }.getOrNull() ?: return null
        return target.takeIf { isAllowed(it) }
    }
}

/** The two files an automatic update needs from one release. spec: SS32.2. */
data class UpdateAssets(
    val tag: String,
    val versionName: String,
    val apkName: String,
    val apkUrl: String,
    val checksumUrl: String,
)

/**
 * Picks the APK and its checksum out of a release by their exact names. spec: SS32.2.
 *
 * The release script names them `physiboard-<version>.apk` and `physiboard-<version>.apk.sha256`
 * (docs/release.md). Both must be there, both must be this repository's own release-download
 * addresses for this tag, and the tag must be a plain version number; otherwise there is nothing
 * to download and the release is only announced, as in 3.2.
 */
object UpdateAssetSelection {
    const val DOWNLOAD_PATH_PREFIX = "/brobata/physiboard/releases/download/"
    private val plainVersion = Regex("""\d{1,4}(\.\d{1,4}){1,3}""")

    fun apkName(versionName: String): String = "physiboard-$versionName.apk"

    fun select(release: ResolvedRelease): UpdateAssets? {
        val versionName = VersionComparison.normalize(release.tag)
        if (!plainVersion.matches(versionName)) return null
        val apkName = apkName(versionName)
        val checksumName = "$apkName.sha256"
        val apk = release.assets.firstOrNull { it.name == apkName } ?: return null
        val checksum = release.assets.firstOrNull { it.name == checksumName } ?: return null
        if (!isReleaseFile(apk.downloadUrl, release.tag, apkName)) return null
        if (!isReleaseFile(checksum.downloadUrl, release.tag, checksumName)) return null
        return UpdateAssets(release.tag, versionName, apkName, apk.downloadUrl, checksum.downloadUrl)
    }

    private fun isReleaseFile(url: String, tag: String, name: String): Boolean {
        if (!UpdateHosts.isAllowed(url)) return false
        val uri = URI(url)
        return uri.host.equals("github.com", ignoreCase = true) && uri.rawPath == "$DOWNLOAD_PATH_PREFIX$tag/$name" && uri.rawQuery == null
    }
}

/** The `.sha256` asset: `<64 hex digits>  <file name>`, as `sha256sum` writes it. spec: SS32.2. */
object ChecksumFile {
    /** The real file is 87 bytes; anything over this is not a checksum file. */
    const val MAX_BYTES = 4_096
    private val hex64 = Regex("[0-9a-f]{64}")

    /** The digest, lower-cased, or null when the text is not a checksum for [expectedFileName]. A bare digest with no name is accepted. */
    fun parse(text: String, expectedFileName: String): String? {
        val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val parts = line.split(Regex("""\s+"""))
        val digest = parts[0].lowercase()
        if (!hex64.matches(digest)) return null
        if (parts.size >= 2) {
            val name = parts[1].removePrefix("*").substringAfterLast('/')
            if (name != expectedFileName) return null
        }
        return digest
    }
}

/** What Android reads out of a downloaded APK. Null facts mean Android could not read it as an APK at all. */
data class ArchiveFacts(val packageName: String, val versionCode: Long, val signerDigests: Set<String>)

/** The running app as Android has it installed. [signerDigests] are SHA-256 digests of the signing certificates, lower-case hex. */
data class InstalledApp(val packageName: String, val versionCode: Long, val signerDigests: Set<String>)

/** Why a downloaded APK is thrown away instead of installed. spec: SS32.3. */
enum class ApkRejection(val reason: String) {
    CHECKSUM("its checksum does not match the release's"),
    NOT_AN_APK("Android cannot read it as an app"),
    OTHER_PACKAGE("it is a different app"),
    NOT_NEWER("it is not newer than the installed version"),
    UNSIGNED("it carries no signature"),
    OTHER_SIGNER("it is not signed by the key this copy of PhysiBoard was signed with"),
}

/**
 * Every check a downloaded APK passes before anything installs it. spec: SS32.3. Pure: the caller
 * hashes the file and asks Android for [ArchiveFacts]; this decides. Null means it passed.
 */
object ApkVerification {
    fun verify(expectedSha256: String, actualSha256: String, archive: ArchiveFacts?, installed: InstalledApp): ApkRejection? {
        if (expectedSha256.length != 64 || !expectedSha256.equals(actualSha256, ignoreCase = true)) return ApkRejection.CHECKSUM
        if (archive == null) return ApkRejection.NOT_AN_APK
        if (archive.packageName != installed.packageName) return ApkRejection.OTHER_PACKAGE
        if (archive.versionCode <= installed.versionCode) return ApkRejection.NOT_NEWER
        if (archive.signerDigests.isEmpty()) return ApkRejection.UNSIGNED
        val ours = installed.signerDigests.map { it.lowercase() }.toSet()
        val theirs = archive.signerDigests.map { it.lowercase() }.toSet()
        if (ours.isEmpty() || ours != theirs) return ApkRejection.OTHER_SIGNER
        return null
    }
}

/** A downloaded APK that passed every check and waits to be installed. */
data class PendingUpdate(val tag: String, val versionCode: Long, val sha256: String, val fileName: String)

/**
 * What the updater remembers between runs, kept in the app's no-backup files (SS32.6). [refusedTag]
 * is the last release whose APK failed a check or an install, so it is announced instead of
 * downloaded again every six hours.
 */
data class UpdateRecord(val ready: PendingUpdate? = null, val refusedTag: String? = null) {
    fun encode(): String = buildJsonObject {
        ready?.let {
            put("tag", it.tag)
            put("versionCode", it.versionCode)
            put("sha256", it.sha256)
            put("fileName", it.fileName)
        }
        refusedTag?.let { put("refusedTag", it) }
    }.toString()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val safeFileName = Regex("""physiboard-[0-9.]{3,20}\.apk""")

        /** Anything unreadable reads as an empty record: the worst case is one more download. */
        fun decode(text: String?): UpdateRecord {
            if (text.isNullOrBlank()) return UpdateRecord()
            val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return UpdateRecord()
            fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val tag = str("tag")
            val versionCode = (obj["versionCode"] as? JsonPrimitive)?.longOrNull
            val sha = str("sha256")
            val fileName = str("fileName")
            val ready = if (tag != null && versionCode != null && sha != null && fileName != null && safeFileName.matches(fileName)) {
                PendingUpdate(tag, versionCode, sha, fileName)
            } else {
                null
            }
            return UpdateRecord(ready, str("refusedTag"))
        }
    }
}

/**
 * The automatic updater's decisions, each a pure function of facts the Android side reads.
 * spec: app-shell.md SS32.
 */
object AutoUpdatePolicy {
    /** The only application id that installs a release APK; `.dev3` and any other id never do (SS32.1). */
    const val RELEASE_APPLICATION_ID = "brobata.physiboard"

    /** SS32.7: how often the background check runs. */
    const val CHECK_PERIOD_HOURS = 6L

    /** SS32.4: the screen must stay off this long before an install. */
    const val QUIET_DELAY_MS = 120_000L

    /** SS32.4: a dictation session running with the screen off is looked at again after this. */
    const val BUSY_RETRY_MS = 300_000L

    /** SS32.1: a build may download and install a release only when it is the release app and its build flag allows it. */
    fun buildMayInstall(applicationId: String, buildFlag: Boolean): Boolean = buildFlag && applicationId == RELEASE_APPLICATION_ID

    /** What a check that found [release] leads to. */
    sealed interface Found {
        /** The 3.2 behaviour: say a release exists, nothing else. */
        data object Announce : Found

        data class Download(val assets: UpdateAssets) : Found

        /** This release is already downloaded and checked. */
        data object AlreadyReady : Found
    }

    fun onFound(mode: UpdateMode, buildMayInstall: Boolean, release: ResolvedRelease, record: UpdateRecord): Found {
        if (mode == UpdateMode.OFF || !buildMayInstall) return Found.Announce
        if (record.refusedTag == release.tag) return Found.Announce
        if (record.ready?.tag == release.tag) return Found.AlreadyReady
        val assets = UpdateAssetSelection.select(release) ?: return Found.Announce
        return Found.Download(assets)
    }

    /** What happens to a checked APK under the current setting. */
    enum class Ready { INSTALL_WHEN_QUIET, ASK, DISCARD }

    fun whenReady(mode: UpdateMode, buildMayInstall: Boolean): Ready = when {
        !buildMayInstall || mode == UpdateMode.OFF -> Ready.DISCARD
        mode == UpdateMode.DOWNLOAD_AND_ASK -> Ready.ASK
        else -> Ready.INSTALL_WHEN_QUIET
    }

    /** A downloaded update the installed app has caught up with (it was installed, or a newer one was). */
    fun isStale(ready: PendingUpdate, installedVersionCode: Long): Boolean = installedVersionCode >= ready.versionCode

    enum class Timing { NOW, WAIT_FOR_SCREEN_OFF, WAIT_FOR_DICTATION }

    /**
     * SS32.4. Installing replaces PhysiBoard, which ends its process and with it the keyboard, so it
     * happens only while nobody can be typing: the screen is off. A dictation session can outlive
     * the screen, and is waited out.
     */
    fun timing(screenInteractive: Boolean, dictationActive: Boolean): Timing = when {
        screenInteractive -> Timing.WAIT_FOR_SCREEN_OFF
        dictationActive -> Timing.WAIT_FOR_DICTATION
        else -> Timing.NOW
    }

    enum class Route { BROKER, SESSION, NOTIFY }

    /** SS32.3: the install routes, best first. Titan tools first when they can reach the phone's shell. */
    fun routes(brokerReachable: Boolean): List<Route> =
        if (brokerReachable) listOf(Route.BROKER, Route.SESSION, Route.NOTIFY) else listOf(Route.SESSION, Route.NOTIFY)

    enum class SessionOutcome { SUCCESS, NEEDS_USER, CANCELLED, FAILED }

    enum class AfterSession {
        /** Installed; the new version's process takes over. */
        DONE,

        /** The user is in PhysiBoard: show Android's confirmation. */
        CONFIRM,

        /** Nobody is watching: post "ready, tap to install". */
        NOTIFY_READY,

        /** The user said no: keep the file, do nothing now. */
        KEEP,

        /** Android refused the APK: delete it, remember the tag, announce the release the 3.2 way. */
        REFUSE,
    }

    fun afterSession(outcome: SessionOutcome, interactive: Boolean): AfterSession = when (outcome) {
        SessionOutcome.SUCCESS -> AfterSession.DONE
        SessionOutcome.NEEDS_USER -> if (interactive) AfterSession.CONFIRM else AfterSession.NOTIFY_READY
        SessionOutcome.CANCELLED -> AfterSession.KEEP
        SessionOutcome.FAILED -> AfterSession.REFUSE
    }
}
