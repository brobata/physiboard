package brobata.physiboard.core.shell

import java.net.URLEncoder

/** The plain facts "Report a problem" fills the GitHub issue template with. spec: app-shell.md SS15.1. */
data class BugReportContext(
    val versionName: String,
    val model: TitanModel,
    val androidRelease: String,
    val androidSdkInt: Int,
    val buildDisplay: String,
    val manufacturer: String,
    val deviceModel: String,
)

/**
 * Builds the GitHub "new issue" URL "Report a problem" opens (app-shell.md SS15.1, T26). Pure
 * string building: the browser intent itself is Android glue.
 */
object BugReportUrl {
    private const val BASE = "https://github.com/brobata/physiboard/issues/new"

    fun build(context: BugReportContext): String {
        val diagnostics = listOf(
            "android=${context.androidRelease} (sdk ${context.androidSdkInt})",
            "build=${context.buildDisplay}",
            "model=${context.manufacturer} ${context.deviceModel}",
        ).joinToString("\n")

        val params = listOf(
            "template" to "bug.yml",
            "app_version" to context.versionName,
            "device" to DeviceDetection.bugReportLabel(context.model),
            "diagnostics" to diagnostics,
        )
        val query = params.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        return "$BASE?$query"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
