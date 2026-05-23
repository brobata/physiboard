package it.palsoftware.pastiera.inputmethod

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import it.palsoftware.pastiera.AppListHelper
import it.palsoftware.pastiera.InstalledApp
import it.palsoftware.pastiera.LocalizedComponentActivity
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.layout.JsonLayoutLoader
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.layout.LayoutMappingRepository
import it.palsoftware.pastiera.inputmethod.subtype.AdditionalSubtypeUtils
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickLauncherActivity : LocalizedComponentActivity() {
    private var apps: List<InstalledApp> = emptyList()
    private var query by mutableStateOf("")
    private var autoStartSingle by mutableStateOf(false)
    private var limitResults by mutableStateOf(false)
    private var widthPercent by mutableStateOf(100)
    private var pillMode by mutableStateOf(false)
    private var respectKeyboardLayout by mutableStateOf(true)
    private var typoTolerantRanking by mutableStateOf(false)
    private var filteredApps by mutableStateOf(emptyList<InstalledApp>())
    private var loadingApps by mutableStateOf(false)
    private var launchedAutomatically = false
    private var keyboardLayout: Map<Int, LayoutMapping> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableActivityAnimations()
        window.requestFeature(android.view.Window.FEATURE_NO_TITLE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        autoStartSingle = SettingsManager.getQuickLauncherAutoStartSingle(this)
        limitResults = SettingsManager.getQuickLauncherLimitResults(this)
        widthPercent = SettingsManager.getQuickLauncherWidthPercent(this)
        pillMode = SettingsManager.getQuickLauncherPillMode(this)
        respectKeyboardLayout = SettingsManager.getQuickLauncherRespectKeyboardLayout(this)
        typoTolerantRanking = SettingsManager.getQuickLauncherTypoTolerantRanking(this)
        keyboardLayout = if (respectKeyboardLayout) loadActiveKeyboardLayout() else emptyMap()
        apps = AppListHelper.getCachedInstalledApps().orEmpty()
        loadingApps = apps.isEmpty()
        refreshFilteredApps()

        setContent {
            PastieraTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    QuickLauncherSheet(
                        query = query,
                        apps = filteredApps,
                        loadingApps = loadingApps,
                        limitResults = limitResults,
                        widthPercent = widthPercent,
                        pillMode = pillMode,
                        onAppSelected = { launchApp(it.packageName) },
                        onDismiss = { finish() }
                    )
                }
            }
        }

        if (loadingApps) {
            lifecycleScope.launch {
                val loadedApps = withContext(Dispatchers.IO) {
                    AppListHelper.getInstalledApps(this@QuickLauncherActivity)
                }
                apps = loadedApps
                loadingApps = false
                refreshFilteredApps()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                launchTopMatch()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (query.isNotEmpty()) {
                    query = query.dropLast(1)
                    launchedAutomatically = false
                    refreshFilteredApps()
                }
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                finish()
                return true
            }
        }

        if (event?.isCtrlPressed != true && event?.isAltPressed != true) {
            val text = resolveTypedText(keyCode, event)
            if (!text.isNullOrEmpty()) {
                query += text
                launchedAutomatically = false
                refreshFilteredApps()
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun finish() {
        super.finish()
        disableActivityAnimations()
    }

    private fun refreshFilteredApps() {
        filteredApps = fuzzyFilterApps(apps, query, limitResults, typoTolerantRanking)
        maybeAutoLaunch()
    }

    private fun maybeAutoLaunch() {
        if (autoStartSingle && query.isNotBlank() && filteredApps.size == 1 && !launchedAutomatically) {
            launchedAutomatically = true
            launchApp(filteredApps.first().packageName)
        }
    }

    private fun launchTopMatch() {
        filteredApps.firstOrNull()?.let { launchApp(it.packageName) }
    }

    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                finish()
            } else {
                Log.w(TAG, "No launch intent for $packageName")
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error launching $packageName", error)
        }
    }

    private fun loadActiveKeyboardLayout(): Map<Int, LayoutMapping> {
        val layoutName = try {
            val imm = getSystemService(InputMethodManager::class.java)
            AdditionalSubtypeUtils.resolveActiveLayout(
                assets,
                this,
                imm.currentInputMethodSubtype
            )
        } catch (error: Exception) {
            SettingsManager.getKeyboardLayout(this)
        }
        return JsonLayoutLoader.loadLayout(assets, layoutName, this).orEmpty()
    }

    private fun resolveTypedText(keyCode: Int, event: KeyEvent?): String? {
        if (respectKeyboardLayout) {
            keyboardLayout[keyCode]
                ?.let { mapping ->
                    val mappedText = LayoutMappingRepository.resolveText(mapping, event?.isShiftPressed == true)
                    if (!mappedText.isNullOrEmpty()) {
                        return mappedText
                    }
                }
        }

        val unicode = event?.unicodeChar ?: 0
        if (unicode <= 0) {
            return null
        }
        val char = unicode.toChar()
        return if (char.isISOControl()) null else char.toString()
    }

    private fun disableActivityAnimations() {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val TAG = "QuickLauncher"
    }
}

@Composable
private fun QuickLauncherSheet(
    query: String,
    apps: List<InstalledApp>,
    loadingApps: Boolean,
    limitResults: Boolean,
    widthPercent: Int,
    pillMode: Boolean,
    onAppSelected: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f
    val visible = remember { mutableStateOf(true) }
    val isCollapsedPill = pillMode && query.isBlank()
    val widthFraction = if (isCollapsedPill) 0.72f else widthPercent.coerceIn(50, 100) / 100f

    AnimatedVisibility(
        visible = visible.value,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 120)
        ) + fadeIn(animationSpec = tween(durationMillis = 60))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(widthFraction)
                .heightIn(max = maxSheetHeight),
            shape = if (isCollapsedPill) RoundedCornerShape(28.dp) else MaterialTheme.shapes.large,
            color = if (isCollapsedPill) Color.Transparent else MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isCollapsedPill) 0.dp else 14.dp,
                        vertical = if (isCollapsedPill) 0.dp else 8.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isCollapsedPill) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.quick_launcher_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 14.dp,
                                end = if (isCollapsedPill) 14.dp else 4.dp,
                                top = if (isCollapsedPill) 9.dp else 6.dp,
                                bottom = if (isCollapsedPill) 9.dp else 6.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isCollapsedPill) {
                            Arrangement.Center
                        } else {
                            Arrangement.Start
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        if (isCollapsedPill) {
                            Text(
                                text = stringResource(R.string.quick_launcher_search_placeholder),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = query.ifBlank { stringResource(R.string.quick_launcher_search_placeholder) },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (query.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.quick_launcher_close)
                                )
                            }
                        }
                    }
                }

                if (!isCollapsedPill) {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!isCollapsedPill && (apps.isEmpty() || loadingApps)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (query.isBlank()) {
                                if (loadingApps) {
                                    stringResource(R.string.quick_launcher_loading_apps)
                                } else if (limitResults) {
                                    stringResource(R.string.quick_launcher_type_to_search)
                                } else {
                                    stringResource(R.string.quick_launcher_no_apps)
                                }
                            } else {
                                stringResource(R.string.quick_launcher_no_results, query)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!isCollapsedPill) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                            QuickLauncherAppRow(
                                app = app,
                                isTopMatch = index == 0,
                                onClick = { onAppSelected(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickLauncherAppRow(
    app: InstalledApp,
    isTopMatch: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isTopMatch) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (isTopMatch) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = ImageView.ScaleType.FIT_CENTER
                    }
                },
                update = { imageView ->
                    imageView.setImageDrawable(app.icon?.constantState?.newDrawable() ?: app.icon)
                },
                modifier = Modifier.size(40.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isTopMatch) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isTopMatch) {
                Text(
                    text = stringResource(R.string.quick_launcher_enter_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun fuzzyFilterApps(
    apps: List<InstalledApp>,
    query: String,
    limitResults: Boolean,
    typoTolerantRanking: Boolean
): List<InstalledApp> {
    val normalizedQuery = normalizeForQuickLauncher(query)
    if (normalizedQuery.isBlank()) {
        return if (limitResults) {
            emptyList()
        } else {
            apps.sortedBy { it.appName.lowercase() }
        }
    }

    val matches = apps
        .mapNotNull { app ->
            val score = fuzzyScore(app, normalizedQuery, typoTolerantRanking) ?: return@mapNotNull null
            app to score
        }
        .sortedWith(compareBy<Pair<InstalledApp, Int>> { it.second }.thenBy { it.first.appName.lowercase() })
        .map { it.first }
    return if (limitResults) matches.take(3) else matches
}

private fun fuzzyScore(app: InstalledApp, normalizedQuery: String, typoTolerantRanking: Boolean): Int? {
    val name = normalizeForQuickLauncher(app.appName)
    val packageName = normalizeForQuickLauncher(app.packageName)
    if (name == normalizedQuery) return 0
    if (name.startsWith(normalizedQuery)) return 10 + name.length
    if (name.split(' ').any { it.startsWith(normalizedQuery) }) return 40 + name.length
    if (packageName.contains(normalizedQuery)) return 200 + packageName.indexOf(normalizedQuery)

    val fuzzyNameScore = subsequenceScore(name, normalizedQuery)
    val typoScore = if (typoTolerantRanking) typoTolerantScore(name, normalizedQuery) else null
    val fuzzyPackageScore = subsequenceScore(packageName, normalizedQuery)?.plus(240)
    return listOfNotNull(fuzzyNameScore, typoScore, fuzzyPackageScore).minOrNull()
}

private fun subsequenceScore(candidate: String, query: String): Int? {
    var lastIndex = -1
    var score = 80
    for (char in query) {
        val nextIndex = candidate.indexOf(char, startIndex = lastIndex + 1)
        if (nextIndex == -1) return null
        score += (nextIndex - lastIndex - 1)
        lastIndex = nextIndex
    }
    return score + candidate.length
}

private fun typoTolerantScore(name: String, query: String): Int? {
    val maxDistance = when (query.length) {
        0, 1, 2 -> return null
        in 3..5 -> 1
        else -> 2
    }
    val candidates = name.split(' ')
        .filter { it.isNotBlank() } +
        listOf(name.filterNot { it.isWhitespace() })

    return candidates
        .flatMap { candidate -> typoCandidateWindows(candidate, query, maxDistance) }
        .mapNotNull { window ->
            val distance = boundedDamerauLevenshtein(window, query, maxDistance) ?: return@mapNotNull null
            180 + distance * 25 + kotlin.math.abs(window.length - query.length)
        }
        .minOrNull()
}

private fun typoCandidateWindows(candidate: String, query: String, maxDistance: Int): List<String> {
    if (candidate.isBlank()) return emptyList()
    val windows = linkedSetOf<String>()
    val minLength = (query.length - maxDistance).coerceAtLeast(1)
    val maxLength = (query.length + maxDistance).coerceAtMost(candidate.length)
    for (length in minLength..maxLength) {
        windows.add(candidate.take(length))
    }
    if (candidate.length <= query.length + maxDistance) {
        windows.add(candidate)
    }
    return windows.toList()
}

private fun boundedDamerauLevenshtein(source: String, target: String, maxDistance: Int): Int? {
    if (kotlin.math.abs(source.length - target.length) > maxDistance) {
        return null
    }

    val matrix = Array(source.length + 1) { IntArray(target.length + 1) }
    for (i in 0..source.length) matrix[i][0] = i
    for (j in 0..target.length) matrix[0][j] = j

    for (i in 1..source.length) {
        var rowMin = Int.MAX_VALUE
        for (j in 1..target.length) {
            val substitutionCost = if (source[i - 1] == target[j - 1]) 0 else 1
            var value = minOf(
                matrix[i - 1][j] + 1,
                matrix[i][j - 1] + 1,
                matrix[i - 1][j - 1] + substitutionCost
            )
            if (
                i > 1 &&
                j > 1 &&
                source[i - 1] == target[j - 2] &&
                source[i - 2] == target[j - 1]
            ) {
                value = minOf(value, matrix[i - 2][j - 2] + 1)
            }
            matrix[i][j] = value
            rowMin = minOf(rowMin, value)
        }
        if (rowMin > maxDistance) return null
    }

    return matrix[source.length][target.length].takeIf { it <= maxDistance }
}

private fun normalizeForQuickLauncher(value: String): String {
    return value.lowercase().trim()
}
