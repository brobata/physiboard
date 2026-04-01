package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.update.checkForUpdate
import it.palsoftware.pastiera.update.showUpdateDialog
import it.palsoftware.pastiera.update.shouldUseGithubUpdateChecks
import java.util.Locale

class TutorialActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PastieraTheme {
                TutorialScreen(
                    onComplete = {
                        SettingsManager.setTutorialCompleted(this@TutorialActivity)
                        val intent = Intent(this@TutorialActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

sealed class TutorialPageType {
    data class Welcome(
        val title: String,
        val description: String,
        @DrawableRes val imageRes: Int
    ) : TutorialPageType()
    
    data class Standard(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val iconTint: Color
    ) : TutorialPageType()

    data class NavMode(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val iconTint: Color
    ) : TutorialPageType()

    data class Customization(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val iconTint: Color
    ) : TutorialPageType()
    
    data class EnablePastiera(
        val title: String,
        val description: String
    ) : TutorialPageType()
    
    data class SelectPastiera(
        val title: String,
        val description: String
    ) : TutorialPageType()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val pages = listOf(
        TutorialPageType.Welcome(
            title = stringResource(R.string.tutorial_page_welcome_title),
            description = stringResource(R.string.tutorial_page_welcome_description),
            imageRes = R.drawable.tutorial_welcome
        ),
        TutorialPageType.EnablePastiera(
            title = stringResource(R.string.tutorial_page_enable_title),
            description = stringResource(R.string.tutorial_page_enable_description)
        ),
        TutorialPageType.SelectPastiera(
            title = stringResource(R.string.tutorial_page_select_title),
            description = stringResource(R.string.tutorial_page_select_description)
        ),
        TutorialPageType.Standard(
            title = stringResource(R.string.tutorial_page_led_title),
            description = stringResource(R.string.tutorial_page_led_description),
            icon = Icons.Filled.Lightbulb,
            iconTint = MaterialTheme.colorScheme.secondary
        ),
        TutorialPageType.NavMode(
            title = stringResource(R.string.tutorial_page_nav_mode_title),
            description = stringResource(R.string.tutorial_page_nav_mode_description),
            icon = Icons.Filled.Navigation,
            iconTint = MaterialTheme.colorScheme.tertiary
        ),
        TutorialPageType.Customization(
            title = stringResource(R.string.tutorial_page_customization_title),
            description = stringResource(R.string.tutorial_page_customization_description),
            icon = Icons.Filled.Settings,
            iconTint = MaterialTheme.colorScheme.primary
        ),
        TutorialPageType.Standard(
            title = stringResource(R.string.tutorial_page_ready_title),
            description = stringResource(R.string.tutorial_page_ready_description),
            icon = Icons.Filled.CheckCircle,
            iconTint = MaterialTheme.colorScheme.primary
        )
    )
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    
    // Check IME status
    var isPastieraEnabled by remember { mutableStateOf(false) }
    var isPastieraSelected by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        checkImeStatus(context) { enabled, selected ->
            isPastieraEnabled = enabled
            isPastieraSelected = selected
        }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            checkImeStatus(context) { enabled, selected ->
                isPastieraEnabled = enabled
                isPastieraSelected = selected
            }
        }
    }
    
    // Automatic update check at tutorial start (only once, respecting dismissed releases)
    if (shouldUseGithubUpdateChecks(context)) {
        LaunchedEffect(Unit) {
            checkForUpdate(
                context = context,
                currentVersion = BuildConfig.VERSION_NAME,
                releaseChannel = BuildConfig.RELEASE_CHANNEL,
                ignoreDismissedReleases = true
            ) { hasUpdate, latestVersion, downloadUrl, releasePageUrl ->
                if (hasUpdate && latestVersion != null) {
                    showUpdateDialog(context, latestVersion, downloadUrl, releasePageUrl)
                }
            }
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Skip button in top left
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(
                    onClick = onComplete,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.tutorial_skip), style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (val pageType = pages[page]) {
                    is TutorialPageType.Welcome -> {
                        TutorialWelcomePageContent(
                            page = pageType,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is TutorialPageType.Standard -> {
                        TutorialStandardPageContent(
                            page = pageType,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is TutorialPageType.NavMode -> {
                        TutorialNavModePageContent(
                            page = pageType,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is TutorialPageType.Customization -> {
                        TutorialCustomizationPageContent(
                            page = pageType,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is TutorialPageType.EnablePastiera -> {
                        TutorialEnablePastieraPageContent(
                            page = pageType,
                            isPastieraEnabled = isPastieraEnabled,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is TutorialPageType.SelectPastiera -> {
                        TutorialSelectPastieraPageContent(
                            page = pageType,
                            isPastieraEnabled = isPastieraEnabled,
                            isPastieraSelected = isPastieraSelected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            // Page indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 10.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                }
                            )
                            .padding(if (isSelected) 0.dp else 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary)
                            )
                        }
                    }
                    if (index < pages.size - 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
            
            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous button
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage - 1,
                                    animationSpec = tween(300)
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.tutorial_previous),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.tutorial_previous), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                // Next/Complete button
                if (pagerState.currentPage < pages.size - 1) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = tween(300)
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.tutorial_next), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.tutorial_next),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.tutorial_finish), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.tutorial_finish),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private val TutorialIconAreaHeight = 110.dp
private val TutorialIconSurfaceSize = 96.dp
private val TutorialIconSize = 44.dp
private val TutorialWelcomeImageSize = 240.dp

@Composable
private fun TutorialPageLayout(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    centered: Boolean = true,
    descriptionLeftAligned: Boolean = false,
    iconContent: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TutorialIconAreaHeight),
            contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
        ) {
            iconContent()
        }
        
        Spacer(modifier = Modifier.height(1.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = if (descriptionLeftAligned) TextAlign.Start else if (centered) TextAlign.Center else TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
            modifier = Modifier.fillMaxWidth()
        )
        
        content()
    }
}

@Composable
private fun TutorialIconSurface(
    icon: ImageVector,
    tint: Color
) {
    Surface(
        modifier = Modifier.size(TutorialIconSurfaceSize),
        shape = CircleShape,
        color = tint.copy(alpha = 0.1f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(TutorialIconSize),
                tint = tint
            )
        }
    }
}

@Composable
fun TutorialWelcomePageContent(
    page: TutorialPageType.Welcome,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = page.imageRes),
                contentDescription = null,
                modifier = Modifier
                    .size(TutorialWelcomeImageSize)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(1.dp))
        
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f
        )
    }
}

@Composable
fun TutorialStandardPageContent(
    page: TutorialPageType.Standard,
    modifier: Modifier = Modifier
) {
    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        iconContent = {
            TutorialIconSurface(
                icon = page.icon,
                tint = page.iconTint
            )
        }
    )
}

@Composable
fun TutorialNavModePageContent(
    page: TutorialPageType.NavMode,
    modifier: Modifier = Modifier
) {
    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        centered = true,
        descriptionLeftAligned = true,
        iconContent = {
            TutorialIconSurface(
                icon = page.icon,
                tint = page.iconTint
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialCustomizationPageContent(
    page: TutorialPageType.Customization,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var appLanguageExpanded by remember { mutableStateOf(false) }
    var modifierExpanded by remember { mutableStateOf(false) }
    var selectedLanguageTag by remember { mutableStateOf(SettingsManager.getAppLanguageTag(context)) }
    var selectedLongPressModifier by remember { mutableStateOf(SettingsManager.getLongPressModifier(context)) }

    val languageOptions = remember {
        listOf(null, "en", "it", "de", "es", "fr", "pl", "ru", "uk", "vi", "hy")
    }

    val selectedLanguageLabel = if (selectedLanguageTag == null) {
        stringResource(R.string.app_language_system_default)
    } else {
        getTutorialLanguageOptionLabel(context, selectedLanguageTag!!)
    }

    val selectedLongPressModifierLabel = when (selectedLongPressModifier) {
        "shift" -> stringResource(R.string.long_press_modifier_shift)
        "variations" -> stringResource(R.string.long_press_modifier_variations)
        "sym" -> stringResource(R.string.long_press_modifier_sym)
        else -> stringResource(R.string.long_press_modifier_alt)
    }

    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        centered = true,
        descriptionLeftAligned = true,
        iconContent = {
            TutorialIconSurface(
                icon = page.icon,
                tint = page.iconTint
            )
        }
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = appLanguageExpanded,
            onExpandedChange = { appLanguageExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedLanguageLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.app_language_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appLanguageExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = appLanguageExpanded,
                onDismissRequest = { appLanguageExpanded = false }
            ) {
                languageOptions.forEach { tag ->
                    val label = if (tag == null) {
                        context.getString(R.string.app_language_system_default)
                    } else {
                        getTutorialLanguageOptionLabel(context, tag)
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            SettingsManager.setAppLanguageTag(context, tag)
                            selectedLanguageTag = tag
                            appLanguageExpanded = false
                            (context as? TutorialActivity)?.recreate()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = modifierExpanded,
            onExpandedChange = { modifierExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedLongPressModifierLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.long_press_modifier_title)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modifierExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = modifierExpanded,
                onDismissRequest = { modifierExpanded = false }
            ) {
                listOf(
                    "alt" to stringResource(R.string.long_press_modifier_alt),
                    "shift" to stringResource(R.string.long_press_modifier_shift),
                    "variations" to stringResource(R.string.long_press_modifier_variations),
                    "sym" to stringResource(R.string.long_press_modifier_sym)
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            SettingsManager.setLongPressModifier(context, value)
                            selectedLongPressModifier = value
                            modifierExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TutorialEnablePastieraPageContent(
    page: TutorialPageType.EnablePastiera,
    isPastieraEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        iconContent = {
            TutorialIconSurface(
                icon = Icons.Filled.Settings,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tutorial_enable_button), style = MaterialTheme.typography.bodyMedium)
        }
        
        if (isPastieraEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.tutorial_enabled_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun TutorialSelectPastieraPageContent(
    page: TutorialPageType.SelectPastiera,
    isPastieraEnabled: Boolean,
    isPastieraSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        iconContent = {
            TutorialIconSurface(
                icon = Icons.Filled.Keyboard,
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        Button(
            onClick = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isPastieraEnabled,
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tutorial_select_button), style = MaterialTheme.typography.bodyMedium)
        }
        
        if (!isPastieraEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.tutorial_enable_first_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        } else if (isPastieraSelected) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.tutorial_selected_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Checks if Pastiera IME is enabled and selected.
 */
private fun checkImeStatus(
    context: Context,
    callback: (enabled: Boolean, selected: Boolean) -> Unit
) {
    try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pastieraPackageName = ImeIdentity.packageName
        val pastieraImeId = ImeIdentity.imeId
        
        val enabledInputMethods = imm.enabledInputMethodList
        val isEnabled = enabledInputMethods.any { inputMethodInfo ->
            inputMethodInfo.packageName == pastieraPackageName ||
            ImeIdentity.matchesImeId(inputMethodInfo.id)
        }
        
        var isSelected = false
        if (isEnabled) {
            try {
                val defaultInputMethod = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD
                ) ?: ""
                isSelected = ImeIdentity.matchesImeId(defaultInputMethod)
            } catch (e: SecurityException) {
                try {
                    val currentSubtype = imm.currentInputMethodSubtype
                    if (currentSubtype != null) {
                        val allInputMethods = imm.inputMethodList
                        val pastieraInputMethod = allInputMethods.find { 
                            it.packageName == pastieraPackageName || ImeIdentity.matchesImeId(it.id)
                        }
                        if (pastieraInputMethod != null && enabledInputMethods.size == 1) {
                            isSelected = true
                        } else {
                            isSelected = false
                        }
                    } else {
                        isSelected = false
                    }
                } catch (e2: Exception) {
                    isSelected = false
                }
            } catch (e: Exception) {
                isSelected = false
            }
        }
        
        callback(isEnabled, isSelected)
    } catch (e: Exception) {
        android.util.Log.e("TutorialActivity", "Error checking IME status", e)
        callback(false, false)
    }
}

private fun getTutorialLanguageOptionLabel(context: Context, languageTag: String): String {
    return try {
        val languageLocale = Locale.forLanguageTag(languageTag)
        val uiLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val nativeName = languageLocale.getDisplayLanguage(languageLocale)
        val uiName = languageLocale.getDisplayLanguage(uiLocale)
        if (nativeName.equals(uiName, ignoreCase = true)) nativeName else "$nativeName - $uiName"
    } catch (_: Exception) {
        languageTag
    }
}
