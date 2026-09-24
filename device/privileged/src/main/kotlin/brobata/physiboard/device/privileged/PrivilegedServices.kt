package brobata.physiboard.device.privileged

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import brobata.physiboard.device.privileged.backlight.GlobalMasterSwitchAccess
import brobata.physiboard.device.privileged.backlight.KeyboardBacklightController
import brobata.physiboard.device.privileged.backlight.MasterSwitchAccess
import brobata.physiboard.device.privileged.broker.AndroidAdbTransport
import brobata.physiboard.device.privileged.broker.PairingCoordinator
import brobata.physiboard.device.privileged.broker.PrivilegedBroker
import brobata.physiboard.device.privileged.ring.AndroidRingLauncher
import brobata.physiboard.device.privileged.ring.DelayedRunner
import brobata.physiboard.device.privileged.ring.NotificationRingListener
import brobata.physiboard.device.privileged.ring.ProximityPocketCheck
import brobata.physiboard.device.privileged.ring.RingBacklight
import brobata.physiboard.device.privileged.ring.RingCoordinator
import brobata.physiboard.device.privileged.ring.ScreenProbe
import brobata.physiboard.device.privileged.setup.AndroidPermissionProbe
import brobata.physiboard.device.privileged.setup.AppIdentity
import brobata.physiboard.device.privileged.setup.PermissionProbe
import brobata.physiboard.device.privileged.setup.PrivilegedSetup
import brobata.physiboard.device.privileged.setup.ResetToStock
import brobata.physiboard.device.privileged.setup.SetupReasons
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Implemented by the `Application` so every component this module declares (the pairing
 * service, the tile, the listener, the ring activity) and the keyboard service can find the
 * process's one [PrivilegedServices] through the application context, the only object they
 * all share. A host that does not implement it (a JVM test, an instrumentation harness) gets
 * null from [PrivilegedServices.from] and every component no-ops.
 */
interface PrivilegedServicesOwner {
    val privileged: PrivilegedServices
}

/**
 * The process's one wiring of the privileged side: one broker (one lock, one verdict), one
 * pairing flow, one setup pass, one ring session. Constructed once by the application; the
 * settings screens, the keyboard service and the components declared in this module all reach
 * the same instance.
 *
 * spec: broker-privileged-toolbox.md SS5.2 ("There is exactly one verdict in the process"),
 * SS6 ("All discovery-plus-shell work in the process is serialized"), SS7 (where the pass runs
 * from); device-backlight-ring.md SS5.8 (restore at every process start).
 */
class PrivilegedServices(
    context: Context,
    val store: DeviceStateStore,
    val diagnostics: DiagnosticsStore = PreferencesDiagnosticsStore(context),
) {
    private val appContext: Context = context.applicationContext

    /** The running app's own identity: never a literal, the sideload build has another applicationId. */
    val identity: AppIdentity = ComponentName(appContext, NotificationRingListener::class.java).let {
        AppIdentity(packageName = appContext.packageName, ringListenerComponent = it.flattenToString())
    }

    val permissions: PermissionProbe = AndroidPermissionProbe(appContext, ComponentName(appContext, NotificationRingListener::class.java))
    val masterSwitch: MasterSwitchAccess = GlobalMasterSwitchAccess(appContext)

    /** The one background worker for the setup pass, resets, pairing and tile taps; blocking broker work never runs on the main thread. */
    val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "physiboard-privileged") }

    private val timers: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "physiboard-privileged-timer") }
    private val mainExecutor: Executor = Handler(Looper.getMainLooper()).let { handler -> Executor { runnable -> handler.post(runnable) } }

    val broker: PrivilegedBroker = PrivilegedBroker(AndroidAdbTransport(appContext), diagnostics)
    val backlight: KeyboardBacklightController = KeyboardBacklightController(broker, store, diagnostics)
    val setup: PrivilegedSetup = PrivilegedSetup(broker, permissions, store, diagnostics, backlight, identity)
    val reset: ResetToStock = ResetToStock(broker, permissions, masterSwitch, store, backlight, identity)
    val ringBacklight: RingBacklight = RingBacklight(
        store = store,
        masterSwitch = masterSwitch,
        permissions = permissions,
        timer = DelayedRunner { delayMs, action ->
            val future = timers.schedule({ runCatching(action).onFailure { Log.e(TAG, "orphan timer crashed", it) } }, delayMs, TimeUnit.MILLISECONDS)
            val cancel: () -> Unit = { future.cancel(false) }
            cancel
        },
    )
    val ringLauncher: AndroidRingLauncher = AndroidRingLauncher(appContext, permissions)
    val ring: RingCoordinator = RingCoordinator(
        store = store,
        backlight = ringBacklight,
        launcher = ringLauncher,
        screen = ScreenProbe { appContext.getSystemService(PowerManager::class.java)?.isInteractive ?: true },
        pocket = ProximityPocketCheck(appContext),
        worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "physiboard-ring") },
        surfaceExecutor = mainExecutor,
    )
    val pairing: PairingCoordinator = PairingCoordinator(AndroidAdbTransport(appContext), broker, worker) { runSetupAsync(SetupReasons.PAIRING_SUCCEEDED) }

    /** The setup pass on the worker: at pairing success, at IME start, and from the backlight screen. spec: SS7. */
    fun runSetupAsync(reason: String) {
        worker.execute {
            runCatching { setup.run(reason) }.onFailure { Log.e(TAG, "setup pass crashed", it) }
        }
    }

    /** spec: ring SS5.8: restore "at every process start of the app (off-thread)", healing a ring that darkened the keyboard and then died. */
    fun onProcessStart() {
        worker.execute {
            runCatching { ringBacklight.restore() }.onFailure { Log.e(TAG, "orphan restore crashed", it) }
        }
    }

    companion object {
        private const val TAG = "PrivilegedServices"

        /** The process's instance, or null on a host that wires none. */
        fun from(context: Context): PrivilegedServices? = (context.applicationContext as? PrivilegedServicesOwner)?.privileged
    }
}
