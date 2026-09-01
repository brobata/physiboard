package brobata.physiboard.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import brobata.physiboard.inputmethod.EmbeddedAdbShell
import brobata.physiboard.inputmethod.PrivilegedDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one place the app decides whether the ADB broker actually works.
 *
 * Every screen used to answer this for itself, and the answers disagreed: the home tile could read
 * "needs pairing" while the toolbox on the very next screen said the tools could reach the system.
 * Two independent checks against a device whose state moves between them will always be able to
 * drift, so there is one verdict here and every surface shows it.
 *
 * Checking costs an mDNS discovery plus a connect, so a verdict is reused for [FRESH_FOR_MS] and
 * concurrent callers await the in-flight check instead of starting their own. That sharing is what
 * keeps two screens consistent — being cheaper is the side benefit.
 */
object BrokerStatusMonitor {

    /** How long a verdict is treated as current before another request re-checks. */
    private const val FRESH_FOR_MS = 10_000L

    private val _status = MutableStateFlow<EmbeddedAdbShell.BrokerStatus?>(null)

    /** The current verdict, or null before the first check has landed. */
    val status: StateFlow<EmbeddedAdbShell.BrokerStatus?> = _status.asStateFlow()

    private val refreshMutex = Mutex()

    @Volatile
    private var lastCheckedAt = 0L

    @Volatile
    private var seeded = false

    /** Show the last persisted verdict immediately, so a screen never opens blank. */
    @Synchronized
    fun seed(context: Context) {
        if (seeded) return
        seeded = true
        if (_status.value == null) {
            _status.value = PrivilegedDiagnostics.lastBrokerStatus(context)?.first
        }
    }

    /**
     * Re-check unless a recent verdict can be reused. Suspends while a check is in flight, so a
     * second screen asking at the same moment receives that same answer rather than racing it.
     */
    suspend fun refresh(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        refreshMutex.withLock {
            val age = System.currentTimeMillis() - lastCheckedAt
            if (!force && lastCheckedAt != 0L && age < FRESH_FOR_MS) return
            val result = withContext(Dispatchers.IO) { EmbeddedAdbShell.verify(appContext) }
            lastCheckedAt = System.currentTimeMillis()
            PrivilegedDiagnostics.recordBrokerStatus(appContext, result)
            _status.value = result
        }
    }

    /**
     * Drop the cached verdict after something that invalidates it — forgetting a pairing, or
     * pairing again. Without this the stale answer would stand for up to [FRESH_FOR_MS].
     */
    fun invalidate() {
        lastCheckedAt = 0L
        _status.value = null
    }
}

/**
 * The verified broker status for any screen that shows it, shared through [BrokerStatusMonitor].
 *
 * Never read [EmbeddedAdbShell.isPaired] to decide what to tell the user: it answers "is a key
 * stored", which is written the moment a pairing is ATTEMPTED and survives Wireless debugging
 * being turned off, so it reports a working setup in two cases where nothing can connect at all.
 *
 * @param refreshKey change it to force a fresh check (e.g. after a re-pair).
 */
@Composable
fun rememberVerifiedBrokerStatus(refreshKey: Any = Unit): State<EmbeddedAdbShell.BrokerStatus?> {
    val context = LocalContext.current
    remember { BrokerStatusMonitor.seed(context) }
    // Wireless debugging is the setting most likely to change while a screen is open, and the one
    // that silently breaks an otherwise good pairing, so it is polled and drives a re-check.
    var wirelessOn by remember { mutableStateOf(EmbeddedAdbShell.isWirelessDebuggingEnabled(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_500)
            wirelessOn = EmbeddedAdbShell.isWirelessDebuggingEnabled(context)
        }
    }
    LaunchedEffect(refreshKey, wirelessOn) {
        BrokerStatusMonitor.refresh(context, force = true)
    }
    return BrokerStatusMonitor.status.collectAsState()
}
