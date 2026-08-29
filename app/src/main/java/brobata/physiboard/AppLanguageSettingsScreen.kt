package brobata.physiboard

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import brobata.physiboard.ui.SettingsTopBar

@Composable
fun AppLanguageSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.app_language_title),
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AppLanguageSelectorCard()
        }
    }
}

fun currentAppLanguageLabel(context: Context): String {
    val tag = getCurrentAppLanguageTag(context)
    return tag?.let { getLanguageOptionLabel(context, it) }
        ?: context.getString(R.string.app_language_system_default)
}
