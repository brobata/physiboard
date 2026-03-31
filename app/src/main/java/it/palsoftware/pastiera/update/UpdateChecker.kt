package it.palsoftware.pastiera.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val GITHUB_RELEASES_URL =
    "https://api.github.com/repos/palsoftware/pastiera/releases?per_page=20"
const val GITHUB_RELEASES_PAGE =
    "https://github.com/palsoftware/pastiera/releases"

private val client = OkHttpClient()
private val mainHandler = Handler(Looper.getMainLooper())

fun checkForUpdate(
    context: Context,
    currentVersion: String,
    releaseChannel: String,
    ignoreDismissedReleases: Boolean = true,
    callback: (hasUpdate: Boolean, latestVersion: String?, downloadUrl: String?, releasePageUrl: String?) -> Unit
) {
    if (!shouldUseGithubUpdateChecks(context)) {
        postResult(callback, false, null, null, null)
        return
    }

    val request = Request.Builder()
        .url(GITHUB_RELEASES_URL)
        .header("Accept", "application/vnd.github+json")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            postResult(callback, false, null, null, null)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { res ->
                if (!res.isSuccessful) {
                    postResult(callback, false, null, null, null)
                    return
                }

                val body = res.body?.string().orEmpty()
                if (body.isBlank()) {
                    postResult(callback, false, null, null, null)
                    return
                }

                val latestRelease = findLatestRelease(parseGitHubReleases(JSONArray(body)), releaseChannel)
                if (latestRelease == null) {
                    postResult(callback, false, null, null, null)
                    return
                }

                val latestVersion = latestRelease.tagName
                val normalizedLatest = normalizeReleaseVersion(latestVersion)
                val normalizedCurrent = normalizeReleaseVersion(currentVersion)
                
                val hasUpdate = normalizedLatest != normalizedCurrent
                
                // If ignoring dismissed releases, check if this release was dismissed
                if (hasUpdate && ignoreDismissedReleases) {
                    val isDismissed = SettingsManager.isReleaseDismissed(context, latestVersion)
                    if (isDismissed) {
                        // Release was dismissed, don't show update
                        postResult(callback, false, null, null, null)
                        return
                    }
                }
                
                postResult(
                    callback,
                    hasUpdate,
                    latestVersion,
                    latestRelease.downloadUrl,
                    latestRelease.releasePageUrl
                )
            }
        }
    })
}

private fun parseGitHubReleases(releases: JSONArray): List<GitHubRelease> =
    buildList {
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            val tagName = release.optString("tag_name").takeIf(String::isNotBlank) ?: continue

            add(
                GitHubRelease(
                    tagName = tagName,
                    prerelease = release.optBoolean("prerelease"),
                    draft = release.optBoolean("draft"),
                    htmlUrl = release.optString("html_url").takeIf(String::isNotBlank),
                    assets = parseReleaseAssets(release.optJSONArray("assets"))
                )
            )
        }
    }

private fun parseReleaseAssets(assets: JSONArray?): List<ReleaseAsset> {
    if (assets == null) return emptyList()

    return buildList {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            add(parseReleaseAsset(asset))
        }
    }
}

private fun parseReleaseAsset(asset: JSONObject): ReleaseAsset =
    ReleaseAsset(
        name = asset.optString("name", ""),
        browserDownloadUrl = asset.optString("browser_download_url").takeIf(String::isNotBlank)
    )

private fun postResult(
    callback: (Boolean, String?, String?, String?) -> Unit,
    hasUpdate: Boolean,
    latestVersion: String?,
    downloadUrl: String?,
    releasePageUrl: String?
) {
    mainHandler.post {
        callback(hasUpdate, latestVersion, downloadUrl, releasePageUrl)
    }
}

fun showUpdateDialog(
    context: Context,
    latestVersion: String,
    downloadUrl: String?,
    releasePageUrl: String?
) {
    val builder = AlertDialog.Builder(context)
        .setTitle(R.string.update_dialog_title)
        .setMessage(context.getString(R.string.update_dialog_message, latestVersion))
        .setPositiveButton(R.string.update_dialog_open_github) { _, _ ->
            openUrl(context, releasePageUrl ?: GITHUB_RELEASES_PAGE)
        }
        .setNeutralButton(R.string.update_dialog_later) { _, _ ->
            // Save dismissed release when user clicks "Later"
            SettingsManager.addDismissedRelease(context, latestVersion)
        }

    if (downloadUrl != null) {
        builder.setNegativeButton(R.string.update_dialog_download_apk) { _, _ ->
            openUrl(context, downloadUrl)
        }
    }

    builder.create().show()
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
