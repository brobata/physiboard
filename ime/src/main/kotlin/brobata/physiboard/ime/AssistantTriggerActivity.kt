package brobata.physiboard.ime

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import brobata.physiboard.core.actions.commands.CommandFailure
import brobata.physiboard.core.speech.AssistantLaunch
import brobata.physiboard.core.speech.AssistantRequest
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * spec: dictation.md SS11.3. The Titan's orange side key never reaches an input method; the
 * vendor layer reads a package and activity out of `Settings.System` and launches it directly.
 * `SideKeyAssistantRemap.bind` (`:device:privileged`) points that slot's long press at this
 * activity, which launches the assistant per SS11.2 and finishes at once: run in its own task and
 * drawing no preview window (see the manifest entry) is what stops PhysiBoard's own screens
 * flashing on the way through (D12).
 *
 * The vendor starts this directly, with no [PhysiBoardInputMethodService] session necessarily
 * running, so `assistant_action` is read through the same [SettingsSourceOwner] seam the IME
 * service uses rather than through a live [KeyboardSession].
 */
class AssistantTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsSource = (applicationContext as? SettingsSourceOwner)?.settingsSource
        if (settingsSource == null) {
            launchAssistant(preferred = null)
            finish()
            return
        }
        MainScope().launch {
            val preferred = runCatching { settingsSource.settings.first() }.getOrNull()?.let(ImeSettings::assistantRequest)
            launchAssistant(preferred)
            finish()
        }
    }

    /** spec SS11.2: the requests in [preferred]'s order, targeted at the assistant package first, then untargeted; "the first that starts wins". */
    private fun launchAssistant(preferred: AssistantRequest?) {
        val order = AssistantLaunch.order(preferred)
        val assistantPackage = assistantPackageName()
        if (assistantPackage != null && order.any { startAssistantIntent(it, assistantPackage) }) return
        if (order.any { startAssistantIntent(it, targetPackage = null) }) return
        runCatching { Toast.makeText(this, CommandFailure.NO_VOICE_ASSISTANT, Toast.LENGTH_SHORT).show() }
    }

    private fun startAssistantIntent(request: AssistantRequest, targetPackage: String?): Boolean = runCatching {
        val intent = Intent(request.intentAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        targetPackage?.let(intent::setPackage)
        if (intent.resolveActivity(packageManager) == null) return false
        startActivity(intent)
        true
    }.getOrDefault(false)

    /** spec SS11.2 step 2: "the first non-empty of `Settings.Secure` keys `assistant` and `voice_interaction_service`". */
    private fun assistantPackageName(): String? = runCatching {
        listOf("assistant", "voice_interaction_service")
            .firstNotNullOfOrNull { key -> AndroidSettings.Secure.getString(contentResolver, key)?.takeIf { it.isNotBlank() } }
            ?.let { raw -> ComponentName.unflattenFromString(raw)?.packageName ?: raw }
    }.getOrNull()
}
