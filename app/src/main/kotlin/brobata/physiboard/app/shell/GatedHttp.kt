package brobata.physiboard.app.shell

import brobata.physiboard.app.PhysiBoardApplication
import brobata.physiboard.core.shell.NetworkDecision
import brobata.physiboard.core.shell.NetworkGate
import brobata.physiboard.core.shell.NetworkPurpose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Thrown instead of opening a connection the gate refused; its message is the user-facing reason. */
class NetworkBlockedException(val purpose: NetworkPurpose, reason: String) : IOException(reason)

/**
 * The only place in PhysiBoard that opens a network connection (app-shell.md SS31.2). It reads
 * `private_mode` from the store as it is right now, asks [NetworkGate], and either opens the
 * connection or throws [NetworkBlockedException]. NetworkGateTest fails the build if any other
 * file opens one, so a new feature cannot go online without passing through here.
 *
 * [install] is called once from [PhysiBoardApplication.onCreate]. Until then (and if the store
 * cannot be read) every request is refused: the gate fails closed.
 */
object GatedHttp {
    @Volatile
    private var application: PhysiBoardApplication? = null

    fun install(application: PhysiBoardApplication) {
        this.application = application
    }

    /** The gate's answer for [purpose] right now, without opening anything. */
    suspend fun decide(purpose: NetworkPurpose): NetworkDecision {
        val privateMode = try {
            application?.settingsSource?.settings?.first()?.privacy?.privateMode
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return NetworkGate.decide(privateMode, purpose)
    }

    /** Opens [url] for [purpose], or throws [NetworkBlockedException] when the gate says no. */
    suspend fun open(purpose: NetworkPurpose, url: String): HttpURLConnection = when (val decision = decide(purpose)) {
        NetworkDecision.Allowed -> URL(url).openConnection() as HttpURLConnection
        is NetworkDecision.Blocked -> throw NetworkBlockedException(decision.purpose, decision.reason)
    }
}
