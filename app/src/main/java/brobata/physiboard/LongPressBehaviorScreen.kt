package brobata.physiboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import brobata.physiboard.ui.SettingsTopBar

/**
 * Long Press behavior screen: the hold delay and what a long press does.
 */
@Composable
fun LongPressBehaviorScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.long_press_behavior_title),
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            LongPressSettingsRows()
        }
    }
}
