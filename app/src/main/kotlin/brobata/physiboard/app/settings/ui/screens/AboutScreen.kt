package brobata.physiboard.app.settings.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Gif
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.app.BuildConfig
import brobata.physiboard.app.settings.ui.ExpandableSection
import brobata.physiboard.app.settings.ui.NavigateRow
import brobata.physiboard.app.settings.ui.PhysiBoardType
import brobata.physiboard.app.settings.ui.SectionHeader
import brobata.physiboard.app.settings.ui.SettingsCard
import brobata.physiboard.app.settings.ui.SettingsScreenScaffold
import brobata.physiboard.app.settings.ui.Spacing
import brobata.physiboard.app.shell.DeviceDetectionAndroid
import brobata.physiboard.app.shell.openInBrowser
import brobata.physiboard.core.shell.BugReportContext
import brobata.physiboard.core.shell.BugReportUrl

/**
 * The About screen (app-shell.md SS15), rewritten for 3.0: no upstream credits markdown renderer
 * (SS30 Keep/Drop drops it; "3.0 has no upstream credits to ship"), so this is a fixed screen
 * instead of a rendered document. It credits Pastiera as the project PhysiBoard succeeds, in the
 * one sentence the task's licensing note allows, and nothing else here mentions it.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val model = remember { DeviceDetectionAndroid.classify() }

    SettingsScreenScaffold(title = "About", onBack = onBack) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            SettingsCard {
                Column(modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                    // The screen's own prompt is the title now (app-shell.md SS22.1); the facts read as its output.
                    Text("PhysiBoard", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text("version  ${BuildConfig.VERSION_NAME}", style = PhysiBoardType.value, modifier = Modifier.padding(top = Spacing.xs))
                    Text("device   ${Build.BRAND} ${Build.MODEL}", style = PhysiBoardType.value)
                    Text(
                        "PhysiBoard succeeds Pastiera, the maintainer's earlier keyboard for the Titan 2 Elite, as a clean-room rewrite.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            }

            SectionHeader("Get in touch", inset = false)
            SettingsCard {

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
                    icon = Icons.Outlined.Feedback,
                )

                NavigateRow(
                    label = "Support PhysiBoard",
                    description = "GitHub Sponsors",
                    onClick = { openInBrowser(context, "https://github.com/sponsors/brobata") },
                    icon = Icons.Outlined.FavoriteBorder,
                )

            }

            SettingsCard {
                ExpandableSection("Licences and credits") {
                NavigateRow(
                    label = "Licence",
                    description = "GPL-3.0, with a commercial licence available",
                    onClick = { openInBrowser(context, "https://github.com/brobata/physiboard/blob/main/LICENSING.md") },
                    icon = Icons.Outlined.Gavel,
                )

                NavigateRow(
                    label = "Third-party notices",
                    description = "The vendored ADB client (Apache 2.0) and everything else this build bundles",
                    onClick = { openInBrowser(context, "https://github.com/brobata/physiboard/blob/main/broker/NOTICE") },
                    icon = Icons.AutoMirrored.Outlined.Article,
                )

                NavigateRow(
                    label = "Fonts",
                    description = "Inter and JetBrains Mono, SIL Open Font License 1.1",
                    onClick = { openInBrowser(context, "https://github.com/brobata/physiboard/tree/main/third_party/licenses") },
                    icon = Icons.Outlined.TextFields,
                )

                NavigateRow(
                    label = "Emoji data",
                    description = "Emoji lists and skin tones come from Unicode (unicode.org), Unicode License v3",
                    onClick = { openInBrowser(context, "https://www.unicode.org/license.txt") },
                    icon = Icons.Outlined.EmojiEmotions,
                )

                NavigateRow(
                    label = "GIFs",
                    description = "The GIF Sym page searches KLIPY (klipy.com) when you turn it on; GIFs come straight from KLIPY under its terms",
                    onClick = { openInBrowser(context, "https://klipy.com") },
                    icon = Icons.Outlined.Gif,
                )

                NavigateRow(
                    label = "Sentence data",
                    description = "Autocorrect's word-pair counts come from Tatoeba (tatoeba.org), CC BY 2.0 FR",
                    onClick = { openInBrowser(context, "https://tatoeba.org") },
                    icon = Icons.Outlined.FormatQuote,
                )
                }

            }

        }
    }
}
