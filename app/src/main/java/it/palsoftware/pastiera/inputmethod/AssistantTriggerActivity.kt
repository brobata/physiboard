package it.palsoftware.pastiera.inputmethod

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import it.palsoftware.pastiera.R

/**
 * A doorway with no room behind it: starts the voice assistant listening and finishes.
 *
 * The Titan's orange side key can only be pointed at a package/activity, so triggering an
 * *intent* from it needs something addressable to aim at. This is that address — it exists so
 * [VendorSideKeyManager] can put PhysiBoard where the vendor expects an app, then immediately
 * hand off to [AssistantLauncher].
 *
 * Deliberately themed transparent and never drawn: the assistant's own UI is the only thing the
 * user should see.
 */
class AssistantTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AssistantLauncher.launch(this)) {
            Toast.makeText(this, getString(R.string.assistant_unavailable), Toast.LENGTH_SHORT).show()
        }
        // Nothing to show: the assistant's own UI is the only thing the user should see.
        finish()
    }
}
