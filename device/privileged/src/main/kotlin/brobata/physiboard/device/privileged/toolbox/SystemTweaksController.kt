package brobata.physiboard.device.privileged.toolbox

import brobata.physiboard.core.toolbox.AnimationSpeed
import brobata.physiboard.core.toolbox.SystemTweaks
import brobata.physiboard.core.toolbox.TweaksReading
import brobata.physiboard.device.privileged.broker.BrokerBlocker
import brobata.physiboard.device.privileged.broker.ShellResult
import brobata.physiboard.device.privileged.broker.ShellRunner

/** spec: broker-privileged-toolbox.md SS14 ("Read"). */
sealed class TweaksReadOutcome {
    object NotPaired : TweaksReadOutcome()
    object Unreadable : TweaksReadOutcome()
    data class Loaded(val reading: TweaksReading) : TweaksReadOutcome()
}

/**
 * System tweaks' device layer: a stateless read and four one-shot writes, none captured for
 * reset-to-stock (SS11, "each screen offers its own reset"). No countdown, no persisted record:
 * "none of these can make the phone unusable" (SS14).
 *
 * spec: broker-privileged-toolbox.md SS14; T26 to T30.
 */
class SystemTweaksController(private val shell: ShellRunner) {
    fun read(): TweaksReadOutcome {
        if (shell.blocker() == BrokerBlocker.NOT_PAIRED) return TweaksReadOutcome.NotPaired
        val result = shell.run(SystemTweaks.READ_LINE)
        val output = (result as? ShellResult.Ok)?.output ?: return TweaksReadOutcome.Unreadable
        val reading = SystemTweaks.parse(output) ?: return TweaksReadOutcome.Unreadable
        return TweaksReadOutcome.Loaded(reading)
    }

    fun setAnimationSpeed(speed: AnimationSpeed): Boolean = shell.run(SystemTweaks.animationLine(speed)).isOk

    fun setNotificationHistory(on: Boolean): Boolean = shell.run(SystemTweaks.toggleLine(SystemTweaks.NOTIFICATION_HISTORY_KEY, on)).isOk

    fun setOneHanded(on: Boolean): Boolean = shell.run(SystemTweaks.toggleLine(SystemTweaks.ONE_HANDED_KEY, on)).isOk

    /** spec: SS14 ("'Put all of these back to stock'"); T30. */
    fun resetAll(): Boolean = shell.run(SystemTweaks.resetAllLine).isOk
}
