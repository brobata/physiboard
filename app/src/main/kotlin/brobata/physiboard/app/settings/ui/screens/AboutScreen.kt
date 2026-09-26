package brobata.physiboard.app.settings.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.R
import brobata.physiboard.app.settings.ui.LocalSettingsController
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.Routes
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.shell.DeviceDetectionAndroid
import brobata.physiboard.app.shell.openInBrowser
import brobata.physiboard.core.shell.AppLocale
import brobata.physiboard.core.shell.BugReportContext
import brobata.physiboard.core.shell.BugReportUrl
import android.os.Build

/**
 * The About screen (app-shell.md SS15), rewritten for 3.0: no upstream credits markdown renderer
 * (SS30 Keep/Drop drops it; "3.0 has no upstream credits to ship"), so this is a fixed screen
 * instead of a rendered document. It credits Pastiera as the project PhysiBoard succeeds, in the
 * one sentence the task's licensing note allows, and nothing else here mentions it.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onShowTutorial: () -> Unit, onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val model = remember { DeviceDetectionAndroid.classify() }
    val appLanguageTag = LocalSettingsController.current.current.value.languages.appLanguageTag
    val appLanguageDescription = AppLocale.resolve(appLanguageTag)?.let { AppLocale.nativeName(it) }
        ?: stringResource(R.string.app_language_system_default)

    SettingsScreenScaffold(title = "About", onBack = onBack) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("PhysiBoard", style = MaterialTheme.typography.titleMedium)
                    Text("Ver. ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text("Device: ${Build.BRAND} ${Build.MODEL}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "PhysiBoard succeeds Pastiera, the maintainer's earlier keyboard for the Titan 2 Elite, as a clean-room rewrite.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            NavigateRow(
                label = "Report a problem",
                description = "Opens a bug report with your version, phone and firmware already filled in. You write what happened.",
                onClick = {
                    val url = BugReportUrl.build(
                        BugReportContext(
                            versionName = BuildConfig.VERSION_NAME,
                            model = model,
                            androidRelease = Build.VERSION.RELEASE,
                            androidSdkInt = Build.VERSION.SDK_INT,
                            buildDisplay = Build.DISPLAY,
                            manufacturer = Build.MANUFACTURER,
                            deviceModel = Build.MODEL,
                        ),
                    )
                    openInBrowser(context, url)
                },
            )

            NavigateRow(
                label = "Support PhysiBoard",
                description = "GitHub Sponsors",
                onClick = { openInBrowser(context, "https://github.com/sponsors/brobata") },
            )

            NavigateRow(
                label = "Licence",
                description = "GPL-3.0, with a commercial licence available",
                onClick = { openInBrowser(context, "https://github.com/brobata/physiboard/blob/main/LICENSING.md") },
            )

            NavigateRow(
                label = "Third-party notices",
                description = "The vendored ADB client (Apache 2.0) and everything else this build bundles",
                onClick = { openInBrowser(context, "https://github.com/brobata/physiboard/blob/main/broker/NOTICE") },
            )

            NavigateRow(
                label = stringResource(R.string.app_language_title),
                description = appLanguageDescription,
                onClick = { onNavigate(Routes.APP_LANGUAGE) },
            )

            NavigateRow(
                label = "Show Tutorial",
                description = "Review the introductory tutorial",
                onClick = onShowTutorial,
            )
        }
    }
}
