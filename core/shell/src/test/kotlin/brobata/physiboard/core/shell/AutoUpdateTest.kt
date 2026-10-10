package brobata.physiboard.core.shell

import brobata.physiboard.core.settings.UpdateMode
import brobata.physiboard.core.shell.AutoUpdatePolicy.AfterSession
import brobata.physiboard.core.shell.AutoUpdatePolicy.SessionOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** spec: app-shell.md SS32. */
class AutoUpdateTest {

    private val dl = "https://github.com/brobata/physiboard/releases/download"

    private fun release(tag: String = "v3.3.0", assets: List<ReleaseAsset>? = null): ResolvedRelease {
        val v = tag.removePrefix("v")
        return ResolvedRelease(
            tag = tag,
            pageUrl = "https://github.com/brobata/physiboard/releases/tag/$tag",
            apkDownloadUrl = "$dl/$tag/physiboard-$v.apk",
            assets = assets ?: listOf(
                ReleaseAsset("physiboard-$v.apk", "$dl/$tag/physiboard-$v.apk"),
                ReleaseAsset("physiboard-$v.apk.sha256", "$dl/$tag/physiboard-$v.apk.sha256"),
            ),
        )
    }

    // --- hosts ---------------------------------------------------------------------------------

    @Test
    fun `only GitHub's own hosts over https are allowed`() {
        assertTrue(UpdateHosts.isAllowed("https://github.com/brobata/physiboard/releases/download/v3.3.0/physiboard-3.3.0.apk"))
        assertTrue(UpdateHosts.isAllowed("https://objects.githubusercontent.com/github-production-release-asset/1/2?x=y"))
        assertTrue(UpdateHosts.isAllowed("https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sp=r&sig=a%3D"))
        assertTrue(UpdateHosts.isAllowed("https://GitHub.com:443/x"))
        assertFalse(UpdateHosts.isAllowed("http://github.com/x"), "plain http")
        assertFalse(UpdateHosts.isAllowed("https://github.com.evil.example/x"))
        assertFalse(UpdateHosts.isAllowed("https://evilgithub.com/x"))
        assertFalse(UpdateHosts.isAllowed("https://raw.githubusercontent.com/x"))
        assertFalse(UpdateHosts.isAllowed("https://user@github.com/x"), "user info")
        assertFalse(UpdateHosts.isAllowed("https://github.com:8443/x"), "odd port")
        assertFalse(UpdateHosts.isAllowed("file:///data/x.apk"))
        assertFalse(UpdateHosts.isAllowed("/relative"))
        assertFalse(UpdateHosts.isAllowed("not a url at all"))
    }

    @Test
    fun `a redirect is followed only to an allowed host`() {
        val from = "https://github.com/brobata/physiboard/releases/download/v3.3.0/physiboard-3.3.0.apk"
        assertEquals(
            "https://release-assets.githubusercontent.com/a/b?sig=x",
            UpdateHosts.redirectTarget(from, "https://release-assets.githubusercontent.com/a/b?sig=x"),
        )
        assertEquals("https://github.com/elsewhere", UpdateHosts.redirectTarget(from, "/elsewhere"), "relative locations resolve against the hop")
        assertNull(UpdateHosts.redirectTarget(from, "https://example.com/physiboard.apk"))
        assertNull(UpdateHosts.redirectTarget(from, "http://objects.githubusercontent.com/a"))
        assertNull(UpdateHosts.redirectTarget(from, null))
        assertNull(UpdateHosts.redirectTarget(from, "   "))
    }

    // --- assets --------------------------------------------------------------------------------

    @Test
    fun `the APK and its checksum are picked by exact name`() {
        val assets = UpdateAssetSelection.select(release())!!
        assertEquals("3.3.0", assets.versionName)
        assertEquals("physiboard-3.3.0.apk", assets.apkName)
        assertEquals("$dl/v3.3.0/physiboard-3.3.0.apk", assets.apkUrl)
        assertEquals("$dl/v3.3.0/physiboard-3.3.0.apk.sha256", assets.checksumUrl)
    }

    @Test
    fun `a release without its checksum, or with files elsewhere, is not downloaded`() {
        val apkOnly = release(assets = listOf(ReleaseAsset("physiboard-3.3.0.apk", "$dl/v3.3.0/physiboard-3.3.0.apk")))
        assertNull(UpdateAssetSelection.select(apkOnly))
        val otherRepo = release(
            assets = listOf(
                ReleaseAsset("physiboard-3.3.0.apk", "https://github.com/someone/fork/releases/download/v3.3.0/physiboard-3.3.0.apk"),
                ReleaseAsset("physiboard-3.3.0.apk.sha256", "$dl/v3.3.0/physiboard-3.3.0.apk.sha256"),
            ),
        )
        assertNull(UpdateAssetSelection.select(otherRepo))
        val otherTag = release(
            assets = listOf(
                ReleaseAsset("physiboard-3.3.0.apk", "$dl/v3.2.0/physiboard-3.3.0.apk"),
                ReleaseAsset("physiboard-3.3.0.apk.sha256", "$dl/v3.3.0/physiboard-3.3.0.apk.sha256"),
            ),
        )
        assertNull(UpdateAssetSelection.select(otherTag))
        val plainHttp = release(
            assets = listOf(
                ReleaseAsset("physiboard-3.3.0.apk", "http://github.com/brobata/physiboard/releases/download/v3.3.0/physiboard-3.3.0.apk"),
                ReleaseAsset("physiboard-3.3.0.apk.sha256", "$dl/v3.3.0/physiboard-3.3.0.apk.sha256"),
            ),
        )
        assertNull(UpdateAssetSelection.select(plainHttp))
        assertNull(UpdateAssetSelection.select(release(tag = "nightly")), "not a version number")
        assertNull(UpdateAssetSelection.select(release(tag = "v3.3.0-rc1")), "not a plain version number")
    }

    // --- checksum file -------------------------------------------------------------------------

    @Test
    fun `the checksum file is read the way sha256sum writes it`() {
        val hex = "555259646006e8933919ee1bb7743ec1ff62ffc6790104e427c9cdcd30608d81"
        assertEquals(hex, ChecksumFile.parse("$hex  physiboard-3.2.0.apk\n", "physiboard-3.2.0.apk"))
        assertEquals(hex, ChecksumFile.parse("$hex *physiboard-3.2.0.apk", "physiboard-3.2.0.apk"), "binary-mode marker")
        assertEquals(hex, ChecksumFile.parse("$hex  release/dist/physiboard-3.2.0.apk", "physiboard-3.2.0.apk"), "a path is reduced to its name")
        assertEquals(hex, ChecksumFile.parse("\n${hex.uppercase()}\n", "physiboard-3.2.0.apk"), "a bare digest, any case")
        assertNull(ChecksumFile.parse("$hex  physiboard-3.1.0.apk", "physiboard-3.2.0.apk"), "another file's checksum")
        assertNull(ChecksumFile.parse(hex.dropLast(1), "physiboard-3.2.0.apk"))
        assertNull(ChecksumFile.parse("<html>not found</html>", "physiboard-3.2.0.apk"))
        assertNull(ChecksumFile.parse("", "physiboard-3.2.0.apk"))
    }

    // --- verification --------------------------------------------------------------------------

    private val sha = "a".repeat(64)
    private val ours = InstalledApp("brobata.physiboard", 30200, setOf("89a050fcb37aa14a16d77c737c70caaebdc4c7f10156f9dc8933adb3499c3261"))
    private fun archive(
        pkg: String = "brobata.physiboard",
        code: Long = 30300,
        signers: Set<String> = setOf("89A050FCB37AA14A16D77C737C70CAAEBDC4C7F10156F9DC8933ADB3499C3261"),
    ) = ArchiveFacts(pkg, code, signers)

    @Test
    fun `an APK that matches on every count passes`() {
        assertNull(ApkVerification.verify(sha, sha.uppercase(), archive(), ours))
    }

    @Test
    fun `each failed check is reported, checksum first`() {
        assertEquals(ApkRejection.CHECKSUM, ApkVerification.verify(sha, "b".repeat(64), archive(), ours))
        assertEquals(ApkRejection.CHECKSUM, ApkVerification.verify("", "", archive(), ours), "no expected digest is never a match")
        assertEquals(ApkRejection.CHECKSUM, ApkVerification.verify(sha, "b".repeat(64), null, ours))
        assertEquals(ApkRejection.NOT_AN_APK, ApkVerification.verify(sha, sha, null, ours))
        assertEquals(ApkRejection.OTHER_PACKAGE, ApkVerification.verify(sha, sha, archive(pkg = "brobata.physiboard.dev3"), ours))
        assertEquals(ApkRejection.NOT_NEWER, ApkVerification.verify(sha, sha, archive(code = 30200), ours), "same version")
        assertEquals(ApkRejection.NOT_NEWER, ApkVerification.verify(sha, sha, archive(code = 30100), ours), "a downgrade")
        assertEquals(ApkRejection.UNSIGNED, ApkVerification.verify(sha, sha, archive(signers = emptySet()), ours))
        assertEquals(ApkRejection.OTHER_SIGNER, ApkVerification.verify(sha, sha, archive(signers = setOf("c".repeat(64))), ours))
        assertEquals(
            ApkRejection.OTHER_SIGNER,
            ApkVerification.verify(sha, sha, archive(signers = ours.signerDigests + "c".repeat(64)), ours),
            "an extra signer is a different signer set",
        )
        assertEquals(ApkRejection.OTHER_SIGNER, ApkVerification.verify(sha, sha, archive(), ours.copy(signerDigests = emptySet())), "unknown own signer never matches")
    }

    // --- the record ----------------------------------------------------------------------------

    @Test
    fun `the record round-trips and anything unreadable is empty`() {
        val record = UpdateRecord(PendingUpdate("v3.3.0", 30300, sha, "physiboard-3.3.0.apk"), refusedTag = "v3.2.9")
        assertEquals(record, UpdateRecord.decode(record.encode()))
        assertEquals(UpdateRecord(), UpdateRecord.decode(UpdateRecord().encode()))
        assertEquals(UpdateRecord(), UpdateRecord.decode(null))
        assertEquals(UpdateRecord(), UpdateRecord.decode("{not json"))
        assertEquals(UpdateRecord(), UpdateRecord.decode("[]"))
    }

    @Test
    fun `a record naming a file outside the update folder's pattern has nothing ready`() {
        val evil = """{"tag":"v3.3.0","versionCode":30300,"sha256":"$sha","fileName":"../shared_prefs/x.xml"}"""
        assertNull(UpdateRecord.decode(evil).ready)
    }

    // --- decisions -----------------------------------------------------------------------------

    @Test
    fun `only the release application id with its build flag installs anything`() {
        assertTrue(AutoUpdatePolicy.buildMayInstall("brobata.physiboard", buildFlag = true))
        assertFalse(AutoUpdatePolicy.buildMayInstall("brobata.physiboard", buildFlag = false), "debug build")
        assertFalse(AutoUpdatePolicy.buildMayInstall("brobata.physiboard.dev3", buildFlag = true), "sideload build")
        assertFalse(AutoUpdatePolicy.buildMayInstall("brobata.physiboard.sideload", buildFlag = true))
    }

    @Test
    fun `a found release is downloaded, announced, or already ready`() {
        val r = release()
        assertIs<AutoUpdatePolicy.Found.Download>(AutoUpdatePolicy.onFound(UpdateMode.INSTALL_AUTOMATICALLY, true, r, UpdateRecord()))
        assertIs<AutoUpdatePolicy.Found.Download>(AutoUpdatePolicy.onFound(UpdateMode.DOWNLOAD_AND_ASK, true, r, UpdateRecord()))
        assertEquals(AutoUpdatePolicy.Found.Announce, AutoUpdatePolicy.onFound(UpdateMode.OFF, true, r, UpdateRecord()), "Off is the 3.2 behaviour")
        assertEquals(AutoUpdatePolicy.Found.Announce, AutoUpdatePolicy.onFound(UpdateMode.INSTALL_AUTOMATICALLY, false, r, UpdateRecord()), "dev or debug build")
        assertEquals(
            AutoUpdatePolicy.Found.Announce,
            AutoUpdatePolicy.onFound(UpdateMode.INSTALL_AUTOMATICALLY, true, r, UpdateRecord(refusedTag = "v3.3.0")),
            "a refused release is not downloaded again",
        )
        assertIs<AutoUpdatePolicy.Found.Download>(
            AutoUpdatePolicy.onFound(UpdateMode.INSTALL_AUTOMATICALLY, true, release("v3.3.1"), UpdateRecord(refusedTag = "v3.3.0")),
            "the next release is tried",
        )
        assertEquals(
            AutoUpdatePolicy.Found.AlreadyReady,
            AutoUpdatePolicy.onFound(UpdateMode.INSTALL_AUTOMATICALLY, true, r, UpdateRecord(PendingUpdate("v3.3.0", 30300, sha, "physiboard-3.3.0.apk"))),
        )
        assertEquals(
            AutoUpdatePolicy.Found.Announce,
            AutoUpdatePolicy.onFound(UpdateMode.INSTALL_AUTOMATICALLY, true, release(assets = emptyList()), UpdateRecord()),
            "nothing to download",
        )
    }

    @Test
    fun `a checked APK waits for quiet, asks, or is discarded`() {
        assertEquals(AutoUpdatePolicy.Ready.INSTALL_WHEN_QUIET, AutoUpdatePolicy.whenReady(UpdateMode.INSTALL_AUTOMATICALLY, true))
        assertEquals(AutoUpdatePolicy.Ready.ASK, AutoUpdatePolicy.whenReady(UpdateMode.DOWNLOAD_AND_ASK, true))
        assertEquals(AutoUpdatePolicy.Ready.DISCARD, AutoUpdatePolicy.whenReady(UpdateMode.OFF, true))
        assertEquals(AutoUpdatePolicy.Ready.DISCARD, AutoUpdatePolicy.whenReady(UpdateMode.INSTALL_AUTOMATICALLY, false))
    }

    @Test
    fun `an install waits for the screen to be off and dictation to end`() {
        assertEquals(AutoUpdatePolicy.Timing.WAIT_FOR_SCREEN_OFF, AutoUpdatePolicy.timing(screenInteractive = true, dictationActive = false))
        assertEquals(AutoUpdatePolicy.Timing.WAIT_FOR_SCREEN_OFF, AutoUpdatePolicy.timing(screenInteractive = true, dictationActive = true))
        assertEquals(AutoUpdatePolicy.Timing.WAIT_FOR_DICTATION, AutoUpdatePolicy.timing(screenInteractive = false, dictationActive = true))
        assertEquals(AutoUpdatePolicy.Timing.NOW, AutoUpdatePolicy.timing(screenInteractive = false, dictationActive = false))
    }

    @Test
    fun `Titan tools go first when they can reach the shell, the notification is always last`() {
        assertEquals(listOf(AutoUpdatePolicy.Route.BROKER, AutoUpdatePolicy.Route.SESSION, AutoUpdatePolicy.Route.NOTIFY), AutoUpdatePolicy.routes(true))
        assertEquals(listOf(AutoUpdatePolicy.Route.SESSION, AutoUpdatePolicy.Route.NOTIFY), AutoUpdatePolicy.routes(false))
    }

    @Test
    fun `what follows Android's answer to an install`() {
        val p = AutoUpdatePolicy
        assertEquals(AfterSession.DONE, p.afterSession(SessionOutcome.SUCCESS, interactive = false))
        assertEquals(AfterSession.NOTIFY_READY, p.afterSession(SessionOutcome.NEEDS_USER, interactive = false))
        assertEquals(AfterSession.CONFIRM, p.afterSession(SessionOutcome.NEEDS_USER, interactive = true))
        assertEquals(AfterSession.KEEP, p.afterSession(SessionOutcome.CANCELLED, interactive = true))
        assertEquals(AfterSession.REFUSE, p.afterSession(SessionOutcome.FAILED, interactive = false))
    }

    @Test
    fun `a downloaded update is stale once that version or a newer one is installed`() {
        val ready = PendingUpdate("v3.3.0", 30300, sha, "physiboard-3.3.0.apk")
        assertFalse(AutoUpdatePolicy.isStale(ready, 30200))
        assertTrue(AutoUpdatePolicy.isStale(ready, 30300))
        assertTrue(AutoUpdatePolicy.isStale(ready, 30400))
    }

    @Test
    fun `a release is noticed within six hours`() {
        assertEquals(6L, AutoUpdatePolicy.CHECK_PERIOD_HOURS)
    }
}
