package brobata.physiboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import brobata.physiboard.toolbox.DisplayDensity
import brobata.physiboard.toolbox.RevertibleChange
import brobata.physiboard.ui.SettingsTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Screen density, with the undo armed before the change lands.
 *
 * The countdown is the whole safety model: after applying, the user has a fixed window to say
 * the screen is still readable, and anything else — expiry, a crash, putting the phone down —
 * puts the panel back where it started.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayDensityScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenPairing: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<DisplayDensity.Info?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableStateOf<Int?>(null) }

    fun load() {
        loading = true
        scope.launch {
            val read = withContext(Dispatchers.IO) { DisplayDensity.read(context) }
            info = read
            if (read != null && selected == 0f) selected = read.current.toFloat()
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // The countdown IS the confirmation. Reaching zero reverts; only an explicit Keep stops it.
    LaunchedEffect(secondsLeft != null) {
        if (secondsLeft == null) return@LaunchedEffect
        var remaining = (RevertibleChange.CONFIRM_WINDOW_MS / 1000).toInt()
        while (remaining > 0 && secondsLeft != null) {
            secondsLeft = remaining
            delay(1000)
            remaining--
        }
        if (secondsLeft != null) {
            secondsLeft = null
            withContext(Dispatchers.IO) { RevertibleChange.revertNow(context) }
            load()
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = stringResource(R.string.density_title),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.density_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                return@Column
            }

            val current = info
            if (current == null) {
                Text(
                    text = stringResource(R.string.bloat_not_paired),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Button(
                    onClick = onOpenPairing,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(stringResource(R.string.bloat_set_up_pairing)) }
                return@Column
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val range = DisplayDensity.range(current.physical)
            val chosen = selected.roundToInt()

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.density_value, chosen, current.physical),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (chosen < current.physical) {
                        stringResource(R.string.density_smaller)
                    } else if (chosen > current.physical) {
                        stringResource(R.string.density_larger)
                    } else {
                        stringResource(R.string.density_stock)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = selected,
                    onValueChange = { selected = (it / 5f).roundToInt() * 5f },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    enabled = !busy && secondsLeft == null,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && secondsLeft == null && chosen != current.current,
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                val outcome = withContext(Dispatchers.IO) {
                                    DisplayDensity.apply(context, current, chosen)
                                }
                                busy = false
                                when (outcome) {
                                    is RevertibleChange.Outcome.Applied -> {
                                        secondsLeft = (RevertibleChange.CONFIRM_WINDOW_MS / 1000).toInt()
                                    }
                                    is RevertibleChange.Outcome.NotPaired ->
                                        error = context.getString(R.string.bloat_not_paired)
                                    is RevertibleChange.Outcome.Failed -> error = outcome.reason
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.density_apply)) }

                    if (current.isOverridden) {
                        TextButton(
                            enabled = !busy && secondsLeft == null,
                            onClick = {
                                busy = true
                                scope.launch {
                                    withContext(Dispatchers.IO) { DisplayDensity.reset(context) }
                                    busy = false
                                    selected = current.physical.toFloat()
                                    load()
                                }
                            }
                        ) { Text(stringResource(R.string.density_reset)) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        secondsLeft?.let { remaining ->
            AlertDialog(
                onDismissRequest = { /* only Keep or the countdown may close this */ },
                title = { Text(stringResource(R.string.density_confirm_title)) },
                text = { Text(stringResource(R.string.density_confirm_message, remaining)) },
                confirmButton = {
                    TextButton(onClick = {
                        secondsLeft = null
                        RevertibleChange.keep(context)
                        load()
                    }) { Text(stringResource(R.string.density_keep)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        secondsLeft = null
                        scope.launch {
                            withContext(Dispatchers.IO) { RevertibleChange.revertNow(context) }
                            load()
                        }
                    }) { Text(stringResource(R.string.density_revert)) }
                }
            )
        }
    }
}
